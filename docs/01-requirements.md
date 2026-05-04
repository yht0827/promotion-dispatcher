# 요구사항 정의서

> Promotion Dispatcher 요구사항 정의 (현재 설계 기준)

## 목차

- [유비쿼터스 언어](#유비쿼터스-언어)
- [시스템 개요](#시스템-개요)
- [서비스 구성](#서비스-구성)
- [API 전체 요약](#api-전체-요약)
- [쿠폰 발급 요청](#쿠폰-발급-요청)
- [이벤트 처리 요구사항](#이벤트-처리-요구사항)
- [상태 및 결과 코드](#상태-및-결과-코드)
- [정합성 및 장애 대응 요구사항](#정합성-및-장애-대응-요구사항)
- [부하 테스트 및 사이징 요구사항](#부하-테스트-및-사이징-요구사항)
- [비기능 요구사항](#비기능-요구사항)
- [제외 범위](#제외-범위)

---

## 유비쿼터스 언어

| 한글 용어 | 영문 용어 | 설명 |
|---|---|---|
| 프로모션 | Promotion | 쿠폰 발급이 진행되는 이벤트 단위 |
| 쿠폰 | Coupon | 사용자에게 지급되는 제한 리소스 |
| 발급 요청 | Issue Request | 사용자가 쿠폰 발급을 요청한 기록 |
| 발급 결과 | Issue Result | 최종 발급 성공, 중복, 품절, 실패 결과 |
| 멱등성 키 | Idempotency Key | 동일 요청 재전송을 식별하는 클라이언트 제공 키 |
| 요청 ID | Request ID | Server A가 생성하는 내부 요청 식별자 |
| Outbox | Outbox Event | DB 저장과 메시지 발행 사이의 유실을 막는 이벤트 저장소 |
| 발급 요청 이벤트 | Issue Requested Event | Server A가 Server B로 전달하는 쿠폰 발급 요청 이벤트 |
| 발급 처리 이벤트 | Issue Processed Event | Server B가 Server C로 전달하는 쿠폰 처리 결과 이벤트 |
| 재고 | Stock | 프로모션 쿠폰의 남은 수량 |
| Backpressure | Backpressure | 처리량 초과 요청이 시스템 전체를 무너뜨리지 않도록 유량을 제어하는 전략 |
| DLQ | Dead Letter Queue | 반복 실패한 메시지를 격리하는 큐 |

---

## 시스템 개요

```text
Client
  -> Server A
  -> MySQL server_a_db
  -> RabbitMQ issue.requested
  -> Server B
  -> Redis Lua script
  -> RabbitMQ issue.processed
  -> Server C
  -> MySQL server_c_db
```

핵심 패턴:

| 패턴 | 설명 |
|---|---|
| Monorepo Multi-module MSA | 하나의 repository 안에 A/B/C 독립 실행 서비스를 Gradle module로 구성 |
| Hexagonal Architecture | 각 서버 내부를 domain, application, adapter, config 패키지로 분리 |
| Idempotency Key | 동일 요청 재전송과 사용자별 중복 발급 방지 |
| Transactional Outbox | Server A 요청 저장과 이벤트 저장을 같은 transaction으로 처리 |
| RabbitMQ | A -> B, B -> C 비동기 메시징, retry, DLQ, prefetch 적용 |
| Redis Lua Script | 재고 확인, 사용자 중복 확인, 재고 차감을 원자 처리 |
| MySQL Unique Constraint | 최종 발급 결과 중복 저장 방어 |
| Rate Limit | Server A에서 Redis counter 기반 사용자별 요청량 제한 |
| Publisher Confirm | A/B 발행자가 RabbitMQ broker confirm 이후 다음 상태로 진행 |
| k6 | 부하 테스트와 인프라 사이징 근거 측정 |

---

## 서비스 구성

| 모듈 | 런타임 책임 | 주요 저장소 |
|---|---|---|
| `common` | 서비스 간 이벤트 DTO, 공통 enum | 없음 |
| `server-a` | HTTP 요청 접수, rate limit, 멱등성 검증, request log/outbox 저장, RabbitMQ 발행 | MySQL `server_a_db`, Redis |
| `server-b` | RabbitMQ consume, Redis Lua 기반 재고 처리, processed event 발행 | Redis |
| `server-c` | RabbitMQ consume, 최종 쿠폰 발급 결과 저장 | MySQL `server_c_db` |

각 서버는 독립 Spring Boot application으로 실행된다.

```bash
./gradlew :server-a:bootRun
./gradlew :server-b:bootRun
./gradlew :server-c:bootRun
```

---

## API 전체 요약

| 기능 | METHOD | URI | 주요 헤더 |
|---|---|---|---|
| 쿠폰 발급 요청 | POST | `/api/v1/promotions/{promotionId}/coupons/issue` | `Idempotency-Key`, `X-User-Id` |
| Health Check | GET | `/actuator/health` | - |

외부 HTTP API는 Server A만 제공한다. Server B와 Server C는 RabbitMQ consumer로 동작한다.

---

## 쿠폰 발급 요청

| METHOD | URI | 설명 |
|---|---|---|
| POST | `/api/v1/promotions/{promotionId}/coupons/issue` | 선착순 프로모션 쿠폰 발급 요청을 접수한다 |

### 기능 요구사항

- `Idempotency-Key` 헤더는 필수다.
- `X-User-Id` 헤더는 필수다.
- 요청 body는 10개 필드를 가진 JSON 객체다.
- Server A는 최종 발급 결과를 기다리지 않고 `ACCEPTED`를 반환한다.
- Server A는 `promotion_id + user_id` 기준 중복 접수를 막는다.
- Server A는 `idempotency_key` 기준 동일 요청 재전송을 막는다.
- Server A는 `rate-limit:{userId}` Redis counter로 사용자별 과도한 요청을 제한한다.
- Server A는 request log와 outbox event를 같은 transaction에 저장한다.
- outbox relay는 RabbitMQ에 `issue.requested` 이벤트를 발행하고 broker confirm 이후 `PUBLISHED`로 갱신한다.

### Request

```http
POST /api/v1/promotions/1/coupons/issue
Idempotency-Key: issue-user-1001-001
X-User-Id: 1001
Content-Type: application/json
```

```json
{
  "requestField1": "value1",
  "requestField2": "value2",
  "requestField3": "value3",
  "requestField4": "value4",
  "requestField5": "value5",
  "requestField6": "value6",
  "requestField7": "value7",
  "requestField8": "value8",
  "requestField9": "value9",
  "requestField10": "value10"
}
```

### Response

```json
{
  "requestId": "7f3db2fd-9c8c-4f49-85c1-f36f5c35a9c2",
  "status": "ACCEPTED"
}
```

### 실패 케이스

| 케이스 | HTTP 상태 | 메시지 방향 |
|---|---:|---|
| `Idempotency-Key` 누락 | 400 | 헤더 필수 |
| `X-User-Id` 누락 | 400 | 헤더 필수 |
| 요청 본문 파싱 실패 | 400 | 요청 본문 형식 오류 |
| 필수 필드 누락 | 400 | validation 메시지 |
| 같은 `Idempotency-Key` 재요청 | 202 | 기존 접수 결과 반환 |
| 같은 `promotionId + userId`에 다른 `Idempotency-Key` | 409 | 중복 접수 거절 |
| 사용자별 rate limit 초과 | 429 | 잠시 후 재시도 |
| DB 저장 실패 | 500 | 서버 내부 오류 |

---

## 이벤트 처리 요구사항

### issue.requested

Server A가 발행하고 Server B가 consume한다.

```json
{
  "requestId": "7f3db2fd-9c8c-4f49-85c1-f36f5c35a9c2",
  "promotionId": 1,
  "userId": 1001,
  "idempotencyKey": "issue-user-1001-001",
  "requestedAt": "2026-05-04T10:00:00Z"
}
```

처리 요구사항:

- Server B는 RabbitMQ manual ack를 사용한다.
- Server B는 Redis Lua script로 중복 확인, 재고 확인, 재고 차감을 원자 처리한다.
- 처리 결과는 `SUCCESS`, `DUPLICATE`, `SOLD_OUT` 중 하나다.
- 처리 결과는 `issue:{requestId}:result`에 저장해 재전달 시 같은 결과를 반환한다.
- 처리 결과와 프로모션 재고 관련 Redis key는 TTL을 둔다.
- 처리 완료 후 `issue.processed` 이벤트를 발행하고 broker confirm 이후 `issue.requested` 메시지를 ack한다.

### issue.processed

Server B가 발행하고 Server C가 consume한다.

```json
{
  "requestId": "7f3db2fd-9c8c-4f49-85c1-f36f5c35a9c2",
  "promotionId": 1,
  "userId": 1001,
  "result": "SUCCESS",
  "reason": null,
  "processedAt": "2026-05-04T10:00:01Z"
}
```

처리 요구사항:

- Server C는 RabbitMQ manual ack를 사용한다.
- Server C는 `request_id` unique constraint로 동일 이벤트 중복 저장을 막는다.
- Server C는 `promotion_id + user_id` unique constraint로 최종 중복 발급을 막는다.
- 이미 저장된 동일 이벤트는 idempotent success로 처리한다.

---

## 상태 및 결과 코드

| 상태 | 설명 |
|---|---|
| `ACCEPTED` | Server A가 요청을 접수하고 outbox 저장까지 완료 |

| outbox 상태 | 설명 |
|---|---|
| `PENDING` | 발행 대기 |
| `PUBLISHED` | RabbitMQ broker confirm까지 완료 |
| `FAILED` | 발행 실패, 재시도 대상 |
| `DEAD` | 최대 재시도 초과로 자동 발행 중단 |

| 결과 | 설명 |
|---|---|
| `SUCCESS` | 쿠폰 발급 성공 |
| `DUPLICATE` | 동일 사용자 또는 동일 요청 중복 |
| `SOLD_OUT` | 남은 쿠폰 재고 없음 |
| `FAILED` | 처리 실패, retry 또는 DLQ 확인 필요 |

---

## 정합성 및 장애 대응 요구사항

| 시나리오 | 요구사항 |
|---|---|
| A가 DB 저장 후 RabbitMQ 발행 전에 죽음 | `outbox_event`를 통해 재발행 가능해야 함 |
| A가 같은 요청을 다시 받음 | `idempotency_key`, `promotion_id + user_id`로 중복 방어 |
| B가 메시지 처리 중 죽음 | RabbitMQ ack 전이면 재전달되어야 함 |
| B가 SUCCESS 후 발행 전에 죽음 | `issue:{requestId}:result`로 같은 SUCCESS 결과를 재사용 |
| C가 같은 이벤트를 여러 번 받음 | `request_id` unique constraint로 idempotent 처리 |
| 요청량이 처리량을 초과함 | A rate limit, RabbitMQ prefetch, consumer concurrency로 유량 제어 |
| 반복 실패 메시지 발생 | retry 후 DLQ에 격리 |

### DB Lock 최소화 전략

대량 요청 구간에서 `SELECT FOR UPDATE` 기반 비관적 락을 사용하지 않는다.
Server A와 Server C는 짧은 transaction과 unique constraint로 중복을 방어하고,
실제 재고 차감은 Server B의 Redis Lua script에서 원자적으로 처리한다.

- Server A: `idempotency_key`, `promotion_id + user_id` unique key로 중복 접수 방어
- Server B: Redis Lua script로 stock 확인, issued-users 확인, stock 차감을 단일 명령처럼 처리
- Server C: `request_id`, `promotion_id + user_id` unique key로 at-least-once 재전달 중복 저장 방어

따라서 hot path에서 MySQL row lock 경합을 만들지 않고, DB는 요청 원장과 최종 결과 원장 역할에 집중한다.

### Redis Hot Key 고려

특정 프로모션에 요청이 몰리면 `promotion:{promotionId}:stock`, `promotion:{promotionId}:issued-users`가 hot key가 된다.
현재 구현은 Lua script로 정합성을 확보하지만, 단일 key에 집중되는 처리량 한계는 남는다.

현재 과제 구현에서는 stock bucket/shard를 바로 구현하지 않는다.
측정 전 분산 재고를 도입하면 bucket 선택, 부분 소진 시 재시도, 전체 품절 판단, 사용자 중복 set 분산까지 설계 복잡도가 커진다.
따라서 단일 Redis Lua script로 정합성과 DB lock 회피를 우선하고, k6 측정 결과 hot key 병목이 확인되면 아래 확장안을 적용한다.

확장안:

- stock bucket/shard: 프로모션 재고를 여러 bucket key로 나누어 요청을 분산
- Redis Cluster: 여러 프로모션 부하를 shard로 분산
- 사전 분산 재고: 인기 프로모션 시작 전에 재고를 shard별로 적재

---

## 부하 테스트 및 사이징 요구사항

과제 시나리오:

- 총 사용자 1,000명
- 각 사용자는 10초 이내 100건 요청
- 평균 10,000 TPS 규모
- 서버 1대당 목표 처리량 500~1,000 TPS

검증 전략:

- 로컬 검증용 k6 시나리오로 낮은 부하부터 정상 흐름을 확인한다.
- ramping 시나리오로 500~1,000 TPS 근처까지 점진적으로 올린다.
- 측정값을 기준으로 10,000 TPS 및 100,000명 동시 접속 기준 필요 인스턴스 수를 계산한다.

---

## 비기능 요구사항

| 항목 | 요구사항 |
|---|---|
| 성능 | 단일 노드 500~1,000 TPS 목표를 기준으로 측정 |
| 정합성 | Outbox, RabbitMQ ack, Redis Lua, MySQL unique constraint로 중복과 유실 방어 |
| 장애 대응 | retry, DLQ, idempotent consumer 적용 |
| Backpressure | rate limit, prefetch, consumer concurrency 제한 |
| 테스트 | 단위 테스트, adapter 테스트, end-to-end smoke test, k6 부하 테스트 |
| 운영 확장 | B outbox, stock bucket/shard, Redis Cluster, observability stack은 측정 후 확장안으로 문서화 |

### Connection Pool 기준

- Server A MySQL pool: 최대 20, 최소 idle 5, connection timeout 3초
- Server C MySQL pool: 최대 10, 최소 idle 2, connection timeout 3초
- Server A는 HTTP 요청 접수와 outbox 저장을 담당하므로 C보다 높은 pool을 둔다.
- Server C는 consumer concurrency가 제한되어 있으므로 작은 pool로 시작한다.
- Redis는 현재 기본 Lettuce connection을 사용하며, k6 측정 후 병목이 확인되면 pooling을 검토한다.

### 보안 고려사항

- HTTPS/TLS는 Spring Boot 앱이 직접 처리하지 않고 Load Balancer, API Gateway, Reverse Proxy 앞단에서 종료한다.
- 현재 구현은 과제 단순화를 위해 `X-User-Id` 헤더를 신뢰한다.
- 실서비스에서는 Gateway 또는 인증 필터에서 JWT/session을 검증하고 내부 헤더로 userId를 전달해야 한다.
- Redis, RabbitMQ, MySQL은 public network에 노출하지 않고 private network와 계정 기반 접근 제어를 사용한다.
- 로그에는 인증 토큰이나 민감정보를 남기지 않는다.

---

## 제외 범위

- 관리자 API
- 여러 종류의 쿠폰
- 사용자 인증
- 애플리케이션 직접 TLS termination
- 프론트엔드 UI
- Kubernetes 배포
- 완전한 observability stack
- Server B durable outbox 구현
