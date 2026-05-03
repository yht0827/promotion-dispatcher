# ERD (Entity Relationship Diagram)

> Promotion Dispatcher 데이터 모델 (현재 설계 기준)

## 목차

- [DB 분리 기준](#db-분리-기준)
- [메인 테이블 ERD](#메인-테이블-erd)
- [핵심 관계 설명](#핵심-관계-설명)
- [테이블 상세](#테이블-상세)
  - [coupon_issue_request](#coupon_issue_request)
  - [outbox_event](#outbox_event)
  - [coupon_issue_result](#coupon_issue_result)
- [Redis 데이터 구조](#redis-데이터-구조)
- [인덱스 전략](#인덱스-전략)
- [Flyway Migration](#flyway-migration)
- [운영 관점 체크포인트](#운영-관점-체크포인트)

---

## DB 분리 기준

로컬 개발에서는 MySQL 컨테이너 1개를 사용하되 database를 분리한다.

```text
mysql:8
  ├─ server_a_db
  └─ server_c_db
```

서비스별 책임:

- Server A는 `server_a_db`만 사용한다.
- Server C는 `server_c_db`만 사용한다.
- Server B는 Redis를 사용하고 MySQL을 직접 사용하지 않는다.
- Server A DB와 Server C DB 사이에는 물리 FK를 두지 않는다.

---

## 메인 테이블 ERD

```mermaid
erDiagram
    COUPON_ISSUE_REQUEST ||--o{ OUTBOX_EVENT : "creates"

    COUPON_ISSUE_REQUEST {
      bigint id PK
      char request_id UK
      bigint promotion_id
      bigint user_id
      varchar idempotency_key UK
      varchar status
      json payload_json
      datetime created_at
      datetime updated_at
    }

    OUTBOX_EVENT {
      bigint id PK
      char event_id UK
      varchar aggregate_type
      char aggregate_id
      varchar event_type
      json payload_json
      varchar status
      int retry_count
      varchar last_error
      datetime created_at
      datetime updated_at
      datetime published_at
    }

    COUPON_ISSUE_RESULT {
      bigint id PK
      char request_id UK
      bigint promotion_id
      bigint user_id
      varchar result
      varchar reason
      datetime processed_at
      datetime created_at
    }
```

---

## 핵심 관계 설명

- `coupon_issue_request`는 Server A의 요청 접수 원장이다.
- `outbox_event`는 Server A의 메시지 발행 대기열이다.
- `outbox_event.aggregate_id`는 `coupon_issue_request.request_id`를 참조하는 논리 키다.
- `coupon_issue_result`는 Server C의 최종 발급 결과 원장이다.
- `coupon_issue_result.request_id`는 Server A에서 생성한 요청 ID를 사용한다.
- 서비스 간 DB가 분리되어 있으므로 cross-database FK는 사용하지 않는다.
- 정합성은 event contract, idempotency key, unique constraint, RabbitMQ ack/retry로 보장한다.

---

## 테이블 상세

### coupon_issue_request

Server A DB: `server_a_db`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | PK, AUTO_INCREMENT | 내부 식별자 |
| `request_id` | CHAR(36) | NOT NULL, UNIQUE | Server A가 생성한 요청 UUID |
| `promotion_id` | BIGINT UNSIGNED | NOT NULL | 프로모션 식별자 |
| `user_id` | BIGINT UNSIGNED | NOT NULL | 사용자 식별자 |
| `idempotency_key` | VARCHAR(100) | NOT NULL, UNIQUE | 클라이언트 재요청 식별자 |
| `status` | VARCHAR(30) | NOT NULL | `ACCEPTED`, `PUBLISHED`, `PUBLISH_FAILED` |
| `payload_json` | JSON | NOT NULL | 원본 10개 필드 요청 body |
| `created_at` | DATETIME(6) | NOT NULL | 생성 시각 |
| `updated_at` | DATETIME(6) | NOT NULL | 수정 시각 |

인덱스:

- `uq_coupon_issue_request_request_id`: `(request_id)` unique
- `uq_coupon_issue_request_promotion_user`: `(promotion_id, user_id)` unique
- `uq_coupon_issue_request_idempotency_key`: `(idempotency_key)` unique
- `ix_coupon_issue_request_status_created_at`: `(status, created_at)`

### outbox_event

Server A DB: `server_a_db`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | PK, AUTO_INCREMENT | 내부 식별자 |
| `event_id` | CHAR(36) | NOT NULL, UNIQUE | outbox 이벤트 UUID |
| `aggregate_type` | VARCHAR(50) | NOT NULL | 예: `COUPON_ISSUE_REQUEST` |
| `aggregate_id` | CHAR(36) | NOT NULL | `coupon_issue_request.request_id` 논리 참조 |
| `event_type` | VARCHAR(100) | NOT NULL | 예: `issue.requested` |
| `payload_json` | JSON | NOT NULL | RabbitMQ 발행 payload |
| `status` | VARCHAR(30) | NOT NULL | `PENDING`, `PUBLISHED`, `FAILED` |
| `retry_count` | INT UNSIGNED | NOT NULL | 발행 재시도 횟수 |
| `last_error` | VARCHAR(1000) | NULL | 마지막 실패 원인 |
| `created_at` | DATETIME(6) | NOT NULL | 생성 시각 |
| `updated_at` | DATETIME(6) | NOT NULL | 수정 시각 |
| `published_at` | DATETIME(6) | NULL | 발행 성공 시각 |

인덱스:

- `uq_outbox_event_event_id`: `(event_id)` unique
- `ix_outbox_event_status_created_at`: `(status, created_at)`
- `ix_outbox_event_aggregate`: `(aggregate_type, aggregate_id)`

### coupon_issue_result

Server C DB: `server_c_db`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | PK, AUTO_INCREMENT | 내부 식별자 |
| `request_id` | CHAR(36) | NOT NULL, UNIQUE | Server A 요청 ID |
| `promotion_id` | BIGINT UNSIGNED | NOT NULL | 프로모션 식별자 |
| `user_id` | BIGINT UNSIGNED | NOT NULL | 사용자 식별자 |
| `result` | VARCHAR(30) | NOT NULL | `SUCCESS`, `DUPLICATE`, `SOLD_OUT`, `FAILED` |
| `reason` | VARCHAR(255) | NULL | 실패 또는 중복 사유 |
| `processed_at` | DATETIME(6) | NOT NULL | Server B 처리 시각 |
| `created_at` | DATETIME(6) | NOT NULL | Server C 저장 시각 |

인덱스:

- `uq_coupon_issue_result_request_id`: `(request_id)` unique
- `uq_coupon_issue_result_promotion_user`: `(promotion_id, user_id)` unique
- `ix_coupon_issue_result_result_created_at`: `(result, created_at)`

---

## Redis 데이터 구조

| Key | Type | 설명 |
|---|---|---|
| `promotion:{promotionId}:stock` | String number | 남은 쿠폰 재고 |
| `promotion:{promotionId}:issued-users` | Set | 이미 발급 처리된 사용자 ID |
| `issue:{requestId}:result` | String | 특정 요청의 Redis 처리 결과 |
| `rate-limit:{userId}` | String number with TTL | 사용자별 요청량 제한 counter |

Lua script 원자 처리:

```text
1. issue:{requestId}:result가 이미 있으면 기존 결과 반환
2. issued-users에 userId가 있으면 DUPLICATE 저장 후 반환
3. stock이 0 이하면 SOLD_OUT 저장 후 반환
4. stock이 남아 있으면 DECR
5. issued-users에 userId 추가
6. issue:{requestId}:result = SUCCESS 저장
7. SUCCESS 반환
```

---

## 인덱스 전략

| 인덱스 | 목적 |
|---|---|
| `uq_coupon_issue_request_request_id` | 요청 ID 중복 방지 및 조회 |
| `uq_coupon_issue_request_promotion_user` | 사용자별 프로모션 중복 접수 방지 |
| `uq_coupon_issue_request_idempotency_key` | 같은 idempotency key 재요청 방지 |
| `ix_coupon_issue_request_status_created_at` | 상태별 요청 조회 및 운영 확인 |
| `uq_outbox_event_event_id` | outbox event 중복 방지 |
| `ix_outbox_event_status_created_at` | pending outbox relay 조회 |
| `ix_outbox_event_aggregate` | 요청 단위 outbox 추적 |
| `uq_coupon_issue_result_request_id` | 동일 이벤트 중복 저장 방지 |
| `uq_coupon_issue_result_promotion_user` | 최종 사용자별 중복 발급 방지 |
| `ix_coupon_issue_result_result_created_at` | 결과별 발급 현황 조회 |

---

## Flyway Migration

각 서버가 자기 schema만 관리한다.

```text
server-a/src/main/resources/db/migration/V1__create_coupon_issue_request_and_outbox.sql
server-c/src/main/resources/db/migration/V1__create_coupon_issue_result.sql
```

Docker Compose MySQL init script는 database 생성만 담당한다.

```sql
CREATE DATABASE IF NOT EXISTS server_a_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS server_c_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;
```

---

## 운영 관점 체크포인트

- `coupon_issue_request`는 요청 접수 원장이므로 임의 삭제하지 않는다.
- `outbox_event`는 발행 성공 후에도 추적 목적으로 보관한다.
- `outbox_event.status=PENDING`이 오래 남으면 RabbitMQ 또는 relay 장애를 의심한다.
- `coupon_issue_result`는 최종 결과 원장이며 append-only에 가깝게 운영한다.
- `promotion_id + user_id` unique constraint는 Server C의 최종 중복 발급 방어선이다.
- Redis 재고와 MySQL 최종 결과 수가 일시적으로 다를 수 있으므로 운영 문서에 보정 절차를 남긴다.
- 트래픽 증가 시 `promotion:{promotionId}:stock:{bucket}` 형태의 stock bucket/shard 확장을 고려한다.
