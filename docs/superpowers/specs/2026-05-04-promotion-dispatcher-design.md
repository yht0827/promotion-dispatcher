# Promotion Dispatcher Design

## 1. Goal

`promotion-dispatcher`는 선착순 프로모션 쿠폰 발급을 안정적으로 처리하는 백엔드 과제 프로젝트다.

과제의 핵심은 단순 쿠폰 CRUD가 아니다. 1,000명의 사용자가 짧은 시간 안에 대량 요청을 보내는 상황에서 제한된 쿠폰 리소스를 중복 없이, 유실 없이, 제한된 서버 자원 안에서 처리하는 것이다.

이 설계는 다음 평가 항목을 코드와 문서로 증명하는 것을 목표로 한다.

- 대량 트래픽과 동시성 제어
- A -> B -> C 서비스 간 데이터 정합성
- 동일 요청 반복 시 idempotency 보장
- Redis 기반 캐시와 hot spot 대응
- k6 부하 테스트 결과 기반 인프라 사이징

## 2. Assumptions

- GitHub repository는 하나만 사용한다.
- repository 내부에 `server-a`, `server-b`, `server-c`를 Gradle multi-module로 둔다.
- 각 서버는 독립적인 Spring Boot application으로 실행된다.
- 프론트엔드 코드는 구현하지 않는다.
- 과제 PDF의 `submodule`은 Git submodule이 아니라 하나의 repository 안에 포함된 서비스 모듈로 해석한다.
- 로컬 개발에서는 MySQL 컨테이너 1개 안에 `server_a_db`, `server_c_db` database를 분리한다.
- 실제 운영 설명에서는 A와 C의 RDBMS를 논리적으로 분리된 저장소로 취급한다.
- Server B의 NoSQL/cache 역할은 Redis가 담당한다.

## 3. Architecture Summary

이 프로젝트는 monorepo 기반 multi-module MSA 구조다.

저장소 구조는 Gradle multi-module이고, 런타임 구조는 독립 실행 가능한 A/B/C 서비스가 RabbitMQ를 통해 비동기로 통신하는 MSA 스타일이다.

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

## 4. Technology Stack

- Java 21
- Spring Boot 3.x
- Gradle multi-module
- MySQL 8
- Redis
- RabbitMQ
- k6
- Docker Compose

## 5. Module Structure

`sample-java-assignment`의 `domain/application/infrastructure/api/app` 분리 철학은 유지하되, 이번 과제에서는 서비스가 3개이므로 Gradle module을 레이어 단위로 나누지 않는다.

Gradle module은 서비스 단위로 나누고, 각 서비스 내부 패키지를 hexagonal architecture로 구성한다.

```text
promotion-dispatcher/
  common/
  server-a/
  server-b/
  server-c/
  docker/
  k6/
  docs/
  README.md
```

### common

공통 모듈은 서비스 간 계약에 해당하는 최소 타입만 가진다.

```text
common/
  src/main/java/com/promotion/common/
    event/
    status/
```

포함 대상:

- `IssueRequestedEvent`
- `IssueProcessedEvent`
- `IssueResult`
- 공통 status enum

제외 대상:

- 비즈니스 유스케이스
- DB entity
- Spring configuration
- 서비스별 adapter

## 6. Hexagonal Package Structure

각 서버는 같은 원칙을 따른다.

- `domain`은 Spring, JPA, RabbitMQ, Redis를 모른다.
- `application`은 유스케이스와 port만 가진다.
- `adapter`는 외부 입출력을 담당하고 application port를 호출하거나 구현한다.
- `config`는 wiring과 외부 기술 설정만 가진다.

### server-a

```text
server-a/src/main/java/com/promotion/servera/
  domain/
  application/
    port/in/
    port/out/
    service/
  adapter/
    in/web/
    out/persistence/
    out/messaging/
  config/
  ServerAApplication.java
```

역할:

- HTTP 요청 접수
- 사용자별 중복 요청 방지
- idempotency key 처리
- request log 저장
- outbox 저장
- RabbitMQ 발행
- rate limit 적용

### server-b

```text
server-b/src/main/java/com/promotion/serverb/
  domain/
  application/
    port/in/
    port/out/
    service/
  adapter/
    in/messaging/
    out/redis/
    out/messaging/
  config/
  ServerBApplication.java
```

역할:

- RabbitMQ에서 `issue.requested` consume
- Redis Lua script로 재고 확인, 사용자 중복 확인, 재고 차감
- `SUCCESS`, `DUPLICATE`, `SOLD_OUT` 결과 결정
- RabbitMQ로 `issue.processed` 발행

### server-c

```text
server-c/src/main/java/com/promotion/serverc/
  domain/
  application/
    port/in/
    port/out/
    service/
  adapter/
    in/messaging/
    out/persistence/
  config/
  ServerCApplication.java
```

역할:

- RabbitMQ에서 `issue.processed` consume
- 최종 쿠폰 발급 결과 MySQL 저장
- `unique(request_id)`와 `unique(promotion_id, user_id)`로 최종 중복 방어
- 최종 상태 기록

## 7. API Design

Server A만 외부 HTTP API를 제공한다.

```http
POST /api/promotions/{promotionId}/coupons/issue
Idempotency-Key: <uuid>
X-User-Id: <user-id>
Content-Type: application/json
```

요청 body는 과제 조건의 10개 필드 JSON 객체를 만족하도록 구성한다.

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

응답은 최종 발급 결과가 아니라 접수 결과를 반환한다.

```json
{
  "requestId": "uuid",
  "status": "ACCEPTED"
}
```

비동기 응답을 선택하는 이유:

- Server A가 요청 thread를 오래 점유하지 않는다.
- RabbitMQ 기반 backpressure 설계 의도가 분명해진다.
- 대량 트래픽 상황에서 API layer의 책임이 작아진다.

## 8. Event Design

이벤트는 2개만 둔다.

```text
issue.requested
issue.processed
```

### issue.requested

Server A가 발행하고 Server B가 consume한다.

```json
{
  "requestId": "uuid",
  "promotionId": 1,
  "userId": 1001,
  "idempotencyKey": "uuid",
  "requestedAt": "2026-05-04T10:00:00Z"
}
```

### issue.processed

Server B가 발행하고 Server C가 consume한다.

```json
{
  "requestId": "uuid",
  "promotionId": 1,
  "userId": 1001,
  "result": "SUCCESS",
  "reason": null,
  "processedAt": "2026-05-04T10:00:01Z"
}
```

## 9. Data Model

### server_a_db.coupon_issue_request

```text
id
request_id
promotion_id
user_id
idempotency_key
status
payload_json
created_at
updated_at
```

Constraints:

```text
unique(request_id)
unique(promotion_id, user_id)
unique(idempotency_key)
```

목적:

- 동일 사용자의 동일 프로모션 중복 요청 방지
- 동일 idempotency key 재시도 처리
- 원본 요청 JSON 보관

### server_a_db.outbox_event

```text
id
aggregate_type
aggregate_id
event_type
payload_json
status
retry_count
last_error
created_at
published_at
```

Index:

```text
index(status, created_at)
```

목적:

- 요청 저장과 메시지 발행 사이의 유실 방지
- RabbitMQ 발행 실패 시 재시도

### server_c_db.coupon_issue_result

```text
id
request_id
promotion_id
user_id
result
reason
created_at
```

Constraints:

```text
unique(request_id)
unique(promotion_id, user_id)
```

목적:

- 최종 쿠폰 발급 결과 저장
- Server A 또는 RabbitMQ 재전달로 발생한 중복 메시지 최종 방어

## 10. Redis Design

기본 Redis key:

```text
promotion:{promotionId}:stock
promotion:{promotionId}:issued-users
issue:{requestId}:result
rate-limit:{userId}
```

Server B는 Redis Lua script로 다음 처리를 원자적으로 수행한다.

```text
1. issue:{requestId}:result가 이미 있으면 기존 결과 반환
2. issued-users에 userId가 있는지 확인
3. 이미 있으면 issue:{requestId}:result = DUPLICATE 저장 후 DUPLICATE 반환
4. stock을 확인
5. stock이 0 이하면 issue:{requestId}:result = SOLD_OUT 저장 후 SOLD_OUT 반환
6. stock이 남아 있으면 DECR
7. issued-users에 userId 추가
8. issue:{requestId}:result = SUCCESS 저장
9. SUCCESS 반환
```

Lua script를 선택한 이유:

- 재고 확인, 중복 확인, 차감을 Redis 안에서 원자적으로 처리한다.
- Java 코드에서 여러 Redis command를 나눠 호출할 때 생기는 race condition을 줄인다.
- Server B가 SUCCESS 처리 후 `issue.processed` 발행 전에 실패해도, 재전달 시 같은 `requestId`에 대해 기존 SUCCESS 결과를 재사용할 수 있다.
- 과제의 동시성 제어와 hot spot 대응 의도를 코드로 보여줄 수 있다.

Hot spot 완화는 1차 구현에서는 단일 stock key와 Lua script로 처리한다. README에서는 트래픽 증가 시 `promotion:{promotionId}:stock:{bucket}` 형태의 bucket/shard 확장 전략을 설명한다.

## 11. Consistency Strategy

### Idempotency

Server A:

- `Idempotency-Key` header를 필수로 받는다.
- `idempotency_key` unique constraint로 동일 요청 재전송을 방어한다.
- `promotion_id + user_id` unique constraint로 사용자별 중복 접수를 방어한다.

Server C:

- `request_id` unique constraint로 동일 이벤트 재처리를 방어한다.
- `promotion_id + user_id` unique constraint로 최종 중복 발급을 방어한다.

### Outbox

Server A는 request log와 outbox event를 같은 DB transaction에 저장한다.

```text
request 저장 성공 + outbox 저장 성공
-> transaction commit
-> relay가 RabbitMQ publish
```

장점:

- DB 저장 후 메시지 발행 전에 서버가 죽어도 outbox에 이벤트가 남는다.
- RabbitMQ 발행 실패 시 outbox relay가 재시도할 수 있다.

### RabbitMQ

RabbitMQ는 A -> B, B -> C 간 비동기 통신을 담당한다.

사용 전략:

- manual ack
- retry
- DLQ
- consumer prefetch
- consumer concurrency 제한

Backpressure는 A의 rate limit과 RabbitMQ consumer prefetch로 나눠 처리한다.

## 12. Failure Scenarios

### Server A가 DB 저장 후 RabbitMQ 발행 전에 죽음

`outbox_event`가 남아 있으므로 relay가 재시도한다.

### Server B가 메시지 처리 중 죽음

RabbitMQ ack 전이면 메시지가 재전달된다. Redis Lua 처리 결과와 Server C unique key가 중복 발급을 방어한다.

### Server C가 동일 이벤트를 여러 번 받음

`unique(request_id)`로 중복 저장을 막는다. 이미 성공 처리된 요청이면 idempotent success로 간주한다.

### Redis 처리 후 B가 issue.processed 발행 전에 죽음

Redis에는 `issue:{requestId}:result`가 남는다. RabbitMQ ack 전이면 메시지가 재전달되고, Lua script는 같은 `requestId`에 대해 기존 처리 결과를 반환한다. 따라서 SUCCESS 처리 후 재전달되어도 DUPLICATE로 바뀌지 않는다.

RabbitMQ 발행 실패가 반복되면 메시지는 retry 후 DLQ로 이동한다. 이 리스크는 README에 명시하고, 1차 구현에서는 DLQ에 남은 이벤트를 운영자가 보정 가능한 상태로 만든다.

확장안:

- B에도 outbox를 둔다.
- Redis 처리 결과를 별도 durable store에 남긴다.
- stock reservation과 finalization을 분리한다.

1차 과제 구현에서는 Server A outbox를 핵심 구현으로 두고, Server B outbox는 확장안으로 문서화한다.

## 13. Rate Limiting and Backpressure

Server A:

- 사용자별 rate limit
- 짧은 시간 과도한 요청은 `429 Too Many Requests`
- Redis counter 기반 구현

RabbitMQ:

- Server B/C consumer prefetch 설정
- worker concurrency 제한
- 처리 실패 메시지는 retry 후 DLQ 이동

이 설계는 Server A가 처리 능력을 초과한 요청을 무한히 받아 내부 queue와 DB를 무너뜨리는 상황을 줄인다.

## 14. Load Test Plan

k6 테스트는 두 종류로 나눈다.

### Local verification

로컬 머신에서 낮은 부하로 정상 흐름과 오류율을 확인한다.

측정 지표:

- requests per second
- HTTP error rate
- p95 latency
- accepted count
- final success count
- duplicate count
- sold out count

### Target ramping

500~1,000 TPS 근처까지 점진적으로 부하를 올린다.

목표:

- 단일 노드에서 어느 수준까지 안정적으로 받는지 확인
- 병목이 A, RabbitMQ, B Redis, C MySQL 중 어디인지 기록

### Infrastructure calculation

PDF 요구사항의 10,000 TPS와 100,000명 동시 접속 시나리오는 README에서 산식으로 제시한다.

예시:

```text
measured_tps_per_instance = 700
required_tps = 10,000
base_instances = ceil(10,000 / 700) = 15
safety_factor = 1.3
recommended_instances = ceil(15 * 1.3) = 20
```

실제 수치는 구현 후 k6 결과로 갱신한다.

## 15. Testing Strategy

테스트는 위험이 큰 경로를 중심으로 둔다.

Server A:

- idempotency key 중복 요청 테스트
- `promotion_id + user_id` 중복 요청 테스트
- outbox event 저장 테스트
- controller validation 테스트

Server B:

- Redis Lua script SUCCESS 테스트
- Redis Lua script DUPLICATE 테스트
- Redis Lua script SOLD_OUT 테스트
- `issue.requested` consume 후 `issue.processed` 발행 테스트

Server C:

- 최종 발급 결과 저장 테스트
- `request_id` 중복 이벤트 idempotent 처리 테스트
- `promotion_id + user_id` 중복 발급 방어 테스트

Integration:

- Docker Compose 기반 A -> RabbitMQ -> B -> Redis -> RabbitMQ -> C 흐름 smoke test

## 16. Implementation Scope

1차 구현 범위:

- 프로모션 1개
- 쿠폰 재고 N개
- 사용자당 1회 발급
- A/B/C 독립 실행
- A outbox 구현
- RabbitMQ 기반 A -> B -> C 흐름
- Redis Lua 기반 재고 처리
- C final result 저장
- k6 부하테스트 스크립트
- README 제출 문서

제외 범위:

- 관리자 API
- 여러 종류의 쿠폰
- 사용자 인증
- UI
- Kubernetes 배포
- 완전한 observability stack
- Server B durable outbox 구현

## 17. Implementation Order

1. Gradle multi-module skeleton 생성
2. Docker Compose로 MySQL, Redis, RabbitMQ 구성
3. `common` 이벤트 타입 작성
4. Server A request log와 outbox 구현
5. Server A HTTP API 구현
6. Server A outbox relay와 RabbitMQ publish 구현
7. Server B RabbitMQ consumer 구현
8. Server B Redis Lua script 구현
9. Server B `issue.processed` publish 구현
10. Server C RabbitMQ consumer와 final result 저장 구현
11. end-to-end smoke test
12. k6 부하테스트 작성
13. README와 부하테스트 결과 정리

## 18. README Delivery Content

README에는 다음을 포함한다.

- 과제 해석
- 전체 아키텍처 다이어그램
- monorepo multi-module MSA 설명
- A/B/C 서버 역할
- 실행 방법
- API 사용 예시
- 데이터 흐름
- idempotency 전략
- outbox 전략
- RabbitMQ retry/DLQ/backpressure 전략
- Redis Lua script와 hot spot 대응
- MySQL unique key 기반 최종 정합성 방어
- k6 부하테스트 방법과 결과
- 100,000명 동시 접속 기준 인프라 계산
- 구현하지 않은 확장안과 이유

## 19. Decision Log and Deferred Measurements

현재 설계에서 확정된 내용:

- 도메인은 선착순 프로모션 쿠폰 발급이다.
- Gradle module은 `common`, `server-a`, `server-b`, `server-c`로 둔다.
- 각 서버 내부는 package 기반 hexagonal architecture로 구성한다.
- MySQL 컨테이너는 로컬에서 1개만 사용하고 database를 2개로 나눈다.
- Redis 처리는 Lua script로 구현한다.
- RabbitMQ는 A -> B, B -> C 비동기 메시징에 사용한다.
- Server A에는 outbox를 구현한다.
- Server B outbox는 1차 구현에서 제외하고 확장안으로 문서화한다.

구현 후 측정으로 확정할 내용:

- k6 측정 TPS
- p95 latency
- error rate
- 100,000명 기준 권장 instance 수
