# 장애 격리와 Backpressure 검증

> A -> B -> C 비동기 흐름에서 장애가 나도 데이터가 유실되지 않는지 검증한다.

## 목표

- Server B가 중지되어도 Server A가 요청을 접수하고 `202 ACCEPTED`를 반환하는지 검증한다.
- Server C가 중지되어도 Server B 처리 결과가 RabbitMQ에 남아 재처리 가능한지 검증한다.
- B/C 재기동 후 backlog가 처리되고 최종 결과가 저장되는지 검증한다.
- 반복 실패 메시지가 retry wait queue를 거쳐 DLQ로 격리되는지 검증한다.
- queue backlog, consumer concurrency, rate limit을 backpressure 근거로 기록한다.

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

테스트용 값을 정한다.

```bash
export PROMOTION_ID=2026050402
export STOCK=10
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

큐 backlog는 RabbitMQ Management API로 확인한다.

```bash
curl -u promotion:promotion \
  http://localhost:15672/api/queues/%2F/issue.requested.queue \
  | jq '{name, messages, messages_ready, messages_unacknowledged}'

curl -u promotion:promotion \
  http://localhost:15672/api/queues/%2F/issue.processed.queue \
  | jq '{name, messages, messages_ready, messages_unacknowledged}'
```

## 시나리오 1. Server B 장애

Server B를 중지한다.

```bash
# server-b bootRun 프로세스 중지
```

Server A로 요청을 보낸다.

```bash
curl -i -X POST "http://localhost:8081/api/v1/promotions/${PROMOTION_ID}/coupons/issue" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 10001" \
  -H "Idempotency-Key: b-down-${PROMOTION_ID}-10001" \
  -d '{"deviceId":"ios-1","channel":"APP"}'
```

기대 결과:

- Server A 응답은 `202 ACCEPTED`다.
- `coupon_issue_request`에 요청 원장이 저장된다.
- `outbox_event`는 publish 성공 후 `PUBLISHED`가 된다.
- Server B가 중지되어 있으므로 `issue.requested.queue` backlog가 증가한다.
- Server B를 재기동하면 backlog가 줄고 Redis 처리 결과가 생성된다.
- 이후 Server C가 살아 있으면 `coupon_issue_result`에 최종 결과가 저장된다.

확인 쿼리:

```bash
docker exec promotion-mysql mysql -uroot -ppassword server_a_db \
  -e "SELECT status, COUNT(*) FROM coupon_issue_request WHERE promotion_id = ${PROMOTION_ID} GROUP BY status;"

docker exec promotion-mysql mysql -uroot -ppassword server_a_db \
  -e "SELECT status, COUNT(*) FROM outbox_event GROUP BY status;"
```

## 시나리오 2. Server C 장애

Server C를 중지하고 Server A/B는 실행 상태로 둔다.

```bash
# server-c bootRun 프로세스 중지
```

Server A로 요청을 보낸다.

```bash
curl -i -X POST "http://localhost:8081/api/v1/promotions/${PROMOTION_ID}/coupons/issue" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 10002" \
  -H "Idempotency-Key: c-down-${PROMOTION_ID}-10002" \
  -d '{"deviceId":"ios-2","channel":"APP"}'
```

기대 결과:

- Server A 응답은 `202 ACCEPTED`다.
- Server B는 `issue.requested`를 consume하고 Redis Lua script로 재고를 처리한다.
- Server B는 `issue.processed`를 publish한다.
- Server C가 중지되어 있으므로 `issue.processed.queue` backlog가 증가한다.
- Server C를 재기동하면 backlog가 줄고 `coupon_issue_result`에 최종 결과가 저장된다.

확인 쿼리:

```bash
docker exec promotion-redis redis-cli GET issue:{requestId}:result

docker exec promotion-mysql mysql -uroot -ppassword server_c_db \
  -e "SELECT result, COUNT(*) FROM coupon_issue_result WHERE promotion_id = ${PROMOTION_ID} GROUP BY result;"
```

## 시나리오 3. Retry와 DLQ

잘못된 payload를 발행해 consumer 실패를 만든다.

```bash
curl -u promotion:promotion -H "Content-Type: application/json" \
  -X POST http://localhost:15672/api/exchanges/%2F/issue.requested.exchange/publish \
  -d '{"properties":{},"routing_key":"issue.requested","payload":"invalid-json","payload_encoding":"string"}'

curl -u promotion:promotion -H "Content-Type: application/json" \
  -X POST http://localhost:15672/api/exchanges/%2F/issue.processed.exchange/publish \
  -d '{"properties":{},"routing_key":"issue.processed","payload":"invalid-json","payload_encoding":"string"}'
```

기대 결과:

- 최초 consumer 실패 후 wait queue로 이동한다.
- TTL 이후 work queue로 재전달된다.
- 최대 retry 횟수를 넘으면 DLQ로 이동한다.

확인 명령:

```bash
curl -u promotion:promotion \
  http://localhost:15672/api/queues/%2F/issue.requested.retry.wait.queue \
  | jq '{name, messages, messages_ready, messages_unacknowledged}'

curl -u promotion:promotion \
  http://localhost:15672/api/queues/%2F/issue.requested.dlq \
  | jq '{name, messages, messages_ready, messages_unacknowledged}'

curl -u promotion:promotion \
  http://localhost:15672/api/queues/%2F/issue.processed.retry.wait.queue \
  | jq '{name, messages, messages_ready, messages_unacknowledged}'

curl -u promotion:promotion \
  http://localhost:15672/api/queues/%2F/issue.processed.dlq \
  | jq '{name, messages, messages_ready, messages_unacknowledged}'
```

## Backpressure 해석

현재 구현의 backpressure는 세 단계로 나뉜다.

1. Server A는 Redis rate limit으로 사용자별 과도한 요청을 초기에 제한한다.
2. RabbitMQ는 B/C 처리량보다 많은 메시지를 queue에 buffer한다.
3. Server B/C는 `prefetch`, `concurrency`, `max-concurrency`로 한 번에 처리하는 메시지 수를 제한한다.

따라서 B/C가 느리거나 중지되어도 Server A는 자신의 DB와 outbox 저장이 가능하면 요청을 접수한다.
다만 queue backlog가 계속 증가하면 consumer 증설, prefetch 조정, rate limit 강화, DLQ 모니터링이 필요하다.

## 실측 결과

실행 기준:

- 실행 일시: 2026-05-04
- 실행 환경: 로컬 개발 머신
- 인프라: MySQL, Redis, RabbitMQ는 Docker Compose
- 애플리케이션: Server A/B/C는 로컬 `bootRun`

### Server B 장애

```text
항목                      결과
Server B 상태             중지
Server A 요청 수          3
Server A 응답             202 ACCEPTED 3건
Server A request log      3건
outbox_event              PUBLISHED 3건
issue.requested.queue     backlog 3건
Server C 최종 결과        B 중지 중 0건
Server B 재기동 후         issue.requested.queue backlog 0건
최종 저장                 SUCCESS 3건
판정                      통과
```

해석:

- Server B가 죽어 있어도 Server A는 요청을 접수하고 DB와 outbox에 기록했다.
- 메시지는 RabbitMQ `issue.requested.queue`에 남아 있었다.
- Server B 재기동 후 backlog가 처리되고 Server C 최종 저장까지 이어졌다.

### Server C 장애

```text
항목                      결과
Server C 상태             중지
Server A 요청 수          3
Server A 응답             202 ACCEPTED 3건
issue.requested.queue     backlog 0건
Redis stock               5 -> 2
issue.processed.queue     backlog 3건
Server C 최종 결과        C 중지 중 0건
Server C 재기동 후         issue.processed.queue backlog 0건
최종 저장                 SUCCESS 3건
판정                      통과
```

해석:

- Server C가 죽어 있어도 Server B는 Redis Lua script로 재고를 처리했다.
- B가 발행한 결과 메시지는 RabbitMQ `issue.processed.queue`에 남아 있었다.
- Server C 재기동 후 backlog가 처리되고 최종 결과가 저장됐다.

### Retry와 DLQ

```text
항목                                  결과
issue.requested invalid payload       routed true
issue.processed invalid payload       routed true
issue.requested.retry.wait.queue      1건
issue.processed.retry.wait.queue      1건
TTL 이후 issue.requested.dlq          1건
TTL 이후 issue.processed.dlq          1건
retry wait queue                      0건
판정                                  통과
```

해석:

- consumer 처리 실패 메시지는 즉시 유실되지 않고 retry wait queue로 이동했다.
- 최대 retry 횟수를 초과한 메시지는 DLQ에 격리됐다.
- 장애 메시지가 work queue에 계속 남아 정상 메시지 처리를 막는 구조는 아니다.

## 결과 기록 양식

```text
시나리오:
실행 일시:
PROMOTION_ID:
요청 수:
기대 결과:
실제 결과:
RabbitMQ backlog:
DB 확인:
Redis 확인:
판정:
비고:
```

실제 실행 결과는 성능 테스트 결과와 함께 제출 문서에 요약한다.
