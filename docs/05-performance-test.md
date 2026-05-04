# 성능 테스트

> k6로 Server A 요청 접수 성능과 A/B/C 비동기 처리 흐름을 확인한다.

## 목표

- 1,000명 동시 쿠폰 발급 요청 시 Server A가 빠르게 `202 ACCEPTED`를 반환하는지 확인한다.
- 재고보다 많은 요청이 들어와도 Server B Redis Lua script가 `SUCCESS`, `SOLD_OUT` 결과를 중복 없이 만든다.
- Server C 최종 결과 저장 건수와 RabbitMQ backlog를 확인한다.
- p95, p99, error rate를 측정해 인프라 사이징 근거로 사용한다.

## 사전 준비

인프라를 실행한다.

```bash
docker compose up -d
```

애플리케이션 3개를 각각 실행한다.

```bash
./gradlew :server-a:bootRun
./gradlew :server-b:bootRun
./gradlew :server-c:bootRun
```

프로모션 ID와 재고 수량을 정한다.

```bash
export PROMOTION_ID=2026050401
export STOCK=100
```

테스트 데이터를 초기화한다.

```bash
docker exec promotion-mysql mysql -uroot -ppassword server_a_db \
  -e "DELETE FROM outbox_event; DELETE FROM coupon_issue_request WHERE promotion_id = ${PROMOTION_ID};"

docker exec promotion-mysql mysql -uroot -ppassword server_c_db \
  -e "DELETE FROM coupon_issue_result WHERE promotion_id = ${PROMOTION_ID};"

docker exec promotion-redis redis-cli \
  DEL promotion:${PROMOTION_ID}:stock promotion:${PROMOTION_ID}:issued-users

docker exec promotion-redis redis-cli \
  SET promotion:${PROMOTION_ID}:stock ${STOCK}
```

큐가 이미 존재한다면 필요 시 비운다.

```bash
docker exec promotion-rabbitmq rabbitmqctl purge_queue issue.requested.queue
docker exec promotion-rabbitmq rabbitmqctl purge_queue issue.processed.queue
```

## Smoke

낮은 부하로 스크립트와 환경을 먼저 확인한다.

```bash
PROMOTION_ID=${PROMOTION_ID} VUS=10 ITERATIONS=1 \
  k6 run k6/coupon-issue-1000-users.js
```

기대값:

- HTTP status는 `202`, `409`, `429` 중 하나다.
- fresh promotion/user 조합이면 대부분 `202`다.
- `coupon_issue_unexpected_status_rate`는 `0`이어야 한다.

## 1,000명 동시 요청

재고 100개에 사용자 1,000명이 동시에 1회씩 요청하는 시나리오다.

```bash
PROMOTION_ID=${PROMOTION_ID} VUS=1000 ITERATIONS=1 MAX_DURATION=2m \
  k6 run k6/coupon-issue-1000-users.js
```

측정 항목:

- Server A HTTP p95
- Server A HTTP p99
- `coupon_issue_accepted_rate`
- `coupon_issue_rate_limited_rate`
- `coupon_issue_unexpected_status_rate`
- RabbitMQ queue backlog
- Redis `SUCCESS`, `SOLD_OUT` 결과
- Server C 최종 저장 건수

## 결과 확인

Server A 접수 건수:

```bash
docker exec promotion-mysql mysql -uroot -ppassword server_a_db \
  -e "SELECT status, COUNT(*) FROM coupon_issue_request WHERE promotion_id = ${PROMOTION_ID} GROUP BY status;"
```

Outbox 상태:

```bash
docker exec promotion-mysql mysql -uroot -ppassword server_a_db \
  -e "SELECT status, COUNT(*) FROM outbox_event GROUP BY status;"
```

Redis 재고:

```bash
docker exec promotion-redis redis-cli GET promotion:${PROMOTION_ID}:stock
docker exec promotion-redis redis-cli SCARD promotion:${PROMOTION_ID}:issued-users
```

Server C 최종 결과:

```bash
docker exec promotion-mysql mysql -uroot -ppassword server_c_db \
  -e "SELECT result, COUNT(*) FROM coupon_issue_result WHERE promotion_id = ${PROMOTION_ID} GROUP BY result;"
```

RabbitMQ backlog:

```bash
curl -u promotion:promotion \
  http://localhost:15672/api/queues/%2F/issue.requested.queue | jq '{name, messages, messages_ready, messages_unacknowledged}'

curl -u promotion:promotion \
  http://localhost:15672/api/queues/%2F/issue.processed.queue | jq '{name, messages, messages_ready, messages_unacknowledged}'
```

## 해석 기준

- Server A의 성공 기준은 최종 발급 완료가 아니라 요청 접수 `202` 응답이다.
- Server B/C는 비동기로 처리되므로 k6 종료 직후에는 RabbitMQ backlog가 잠시 남을 수 있다.
- `STOCK=100`, `VUS=1000`이면 최종적으로 `SUCCESS`는 최대 100건이어야 한다.
- 나머지는 `SOLD_OUT` 또는 이미 처리된 사용자 기준 `DUPLICATE`가 될 수 있다.
- `429`가 많으면 rate limit이 테스트 목적보다 강하게 작동한 것이므로 `BASE_USER_ID`, `VUS`, `ITERATIONS` 값을 확인한다.
- Redis hot key 병목은 `promotion:{promotionId}:stock`, `promotion:{promotionId}:issued-users`에 집중된다.
- 현재 구현은 측정 전 stock bucket/shard를 넣지 않고, 단일 Lua script 기준 한계를 먼저 확인한다.
- 병목이 확인되면 stock bucket/shard, Redis Cluster, 사전 분산 재고를 확장안으로 검토한다.

## 실측 결과

실행 기준:

- 실행 일시: 2026-05-04
- 실행 환경: 로컬 개발 머신
- 인프라: MySQL, Redis, RabbitMQ는 Docker Compose
- 애플리케이션: Server A/B/C는 로컬 `bootRun`
- 제한 사항: 1vCPU, 2GB 제한 환경은 아직 적용하지 않았다.

주의:

- 아래 수치는 현재 로컬 기준 baseline이다.
- 과제 제출용 공식 수치는 1vCPU, 2GB 제한 환경에서 한 번 더 측정해야 한다.
- 1,000 VU 테스트는 HTTP error 없이 모두 접수됐지만, 현재 threshold인 p95 500ms는 초과했다.

### Warm-up

```text
VU   요청 수  HTTP 202  최종 결과    비고
10   10       10        SUCCESS 10   공식 수치에서 제외
```

### 100 VU 반복 측정

```text
회차  요청 수  TPS     avg(ms)  p95(ms)  p99(ms)  HTTP error  최종 결과     backlog
1     100      273.0   240.4    339.2    344.4    0%          SUCCESS 100   0
2     100      465.1   122.8    186.5    191.3    0%          SUCCESS 100   0
3     100      522.9   113.1    164.0    166.6    0%          SUCCESS 100   0
평균  100      420.3   158.8    229.9    234.1    0%          SUCCESS 100   0
```

해석:

- 100 VU에서는 모든 요청이 `202 ACCEPTED`로 접수됐다.
- 최종 결과는 모두 `SUCCESS`였다.
- RabbitMQ backlog는 안정화 후 0이었다.
- 현재 threshold인 p95 500ms, p99 1000ms를 만족했다.

### 1,000 VU 반복 측정

```text
회차  요청 수  TPS     avg(ms)  p95(ms)  p99(ms)  HTTP error  최종 결과                   backlog
1     1000     816.8   641.9    1062.8   1144.1   0%          SUCCESS 100 / SOLD_OUT 900   0
2     1000     847.4   630.0    1049.9   1106.7   0%          SUCCESS 100 / SOLD_OUT 900   0
3     1000     925.8   528.5    941.5    996.8    0%          SUCCESS 100 / SOLD_OUT 900   0
평균  1000     863.3   600.1    1018.1   1082.5   0%          SUCCESS 100 / SOLD_OUT 900   0
```

해석:

- 1,000건 모두 `202 ACCEPTED`로 접수됐다.
- Server A request log는 1,000건 저장됐다.
- outbox는 최종적으로 `PUBLISHED` 상태까지 처리됐다.
- 재고 100개 기준 최종 결과는 `SUCCESS 100`, `SOLD_OUT 900`으로 정합성이 맞았다.
- RabbitMQ backlog는 안정화 후 0이었다.
- p95 평균이 약 1018ms로 현재 threshold인 500ms를 초과했다.
- k6 exit code는 threshold 실패 때문에 99였지만, HTTP error rate와 정합성은 통과했다.

### 병목 해석

현재 병목은 최종 발급 처리보다 Server A 요청 접수 구간에서 먼저 드러난다.
Server A는 요청마다 MySQL request log와 outbox를 같은 transaction에 저장하므로, 순간 1,000 VU에서는 DB insert와 connection pool 경쟁이 p95 지연으로 나타난다.

현재 구조에서 데이터 유실 방어는 정상 동작했다.
다만 지연 시간 목표를 만족하려면 다음 개선을 검토한다.

- Server A 인스턴스 수평 확장
- Hikari pool, Tomcat thread, DB connection 상한 재조정
- MySQL 쓰기 성능 확인
- 요청 접수 테이블 index와 insert 비용 확인
- Redis rate limit 정책으로 순간 burst 제한
- B/C consumer concurrency와 RabbitMQ backlog 모니터링

## 사이징 계산 방식

실측 TPS를 기준으로 필요한 인스턴스 수를 계산한다.

```text
필요 인스턴스 수 = 목표 TPS / 단일 인스턴스 실측 TPS
```

예시:

```text
목표 TPS: 10,000
Server A 단일 인스턴스 실측 TPS: 800
필요 Server A 인스턴스 수: ceil(10,000 / 800) = 13
```

최종 README에는 실제 측정값으로 계산식을 갱신한다.

## 100,000명 동시 접속 산정 예시

현재 로컬 baseline에서 1,000 VU 평균 처리량은 약 863 TPS다.
정확한 제출 수치는 1vCPU, 2GB 제한 환경에서 다시 측정한 TPS로 계산해야 한다.

동시에 100,000명이 쿠폰 발급 버튼을 누르는 상황을 단순화하면 다음처럼 계산할 수 있다.

```text
목표 처리 시간 1초:
목표 TPS = 100,000
필요 Server A 인스턴스 = ceil(100,000 / 863) = 116대

목표 처리 시간 10초:
목표 TPS = 10,000
필요 Server A 인스턴스 = ceil(10,000 / 863) = 12대
```

운영에서는 Server A만 늘리는 것으로 끝나지 않는다.
Server A 인스턴스 수에 맞춰 MySQL connection 수, RabbitMQ 처리량, Server B/C consumer 수, Redis hot key 처리량도 함께 검증해야 한다.
