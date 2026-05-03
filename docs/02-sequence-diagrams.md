# 시퀀스 다이어그램

> Promotion Dispatcher 주요 런타임 흐름 (현재 설계 기준)

## 목차

- [쿠폰 발급 요청 접수](#쿠폰-발급-요청-접수)
- [Outbox 발행](#outbox-발행)
- [Server B 쿠폰 처리](#server-b-쿠폰-처리)
- [Server C 최종 저장](#server-c-최종-저장)
- [멱등성 재요청](#멱등성-재요청)
- [장애 및 재시도](#장애-및-재시도)
- [부하 테스트 흐름](#부하-테스트-흐름)

---

## 쿠폰 발급 요청 접수

```mermaid
sequenceDiagram
    participant C as Client
    participant API as Server A Web Adapter
    participant UC as IssueCouponUseCase
    participant RL as RateLimitPort
    participant RP as CouponIssueRequestPort
    participant OP as OutboxEventPort
    participant DB as MySQL server_a_db

    C->>API: POST /api/promotions/{id}/coupons/issue
    API->>UC: issue(command)
    UC->>RL: check(userId)

    alt rate limit exceeded
        RL-->>UC: rejected
        UC-->>API: TooManyRequestsException
        API-->>C: 429 TOO MANY REQUESTS
    else allowed
        UC->>RP: saveRequest(command)
        RP->>DB: INSERT coupon_issue_request
        UC->>OP: saveOutbox(issue.requested)
        OP->>DB: INSERT outbox_event
        UC-->>API: accepted(requestId)
        API-->>C: 202 ACCEPTED
    end
```

핵심 포인트

- Server A는 최종 발급 결과를 기다리지 않는다.
- request log와 outbox event는 같은 transaction에 저장한다.
- 사용자별 과도한 요청은 rate limit으로 초기에 차단한다.

---

## Outbox 발행

```mermaid
sequenceDiagram
    participant R as OutboxRelay
    participant OP as OutboxEventPort
    participant DB as MySQL server_a_db
    participant MQ as RabbitMQ

    R->>OP: findPendingEvents()
    OP->>DB: SELECT outbox_event WHERE status=PENDING
    DB-->>OP: pending events
    OP-->>R: events

    loop each event
        R->>MQ: publish issue.requested
        alt publish success
            R->>OP: markPublished(eventId)
            OP->>DB: UPDATE status=PUBLISHED, published_at=now
        else publish failed
            R->>OP: markFailed(eventId, error)
            OP->>DB: UPDATE retry_count, last_error
        end
    end
```

핵심 포인트

- DB commit 이후 RabbitMQ 발행 전에 Server A가 죽어도 outbox가 남는다.
- relay는 pending event를 재조회해 발행을 재시도한다.
- `status, created_at` index로 pending event 조회를 최적화한다.

---

## Server B 쿠폰 처리

```mermaid
sequenceDiagram
    participant MQ1 as RabbitMQ issue.requested
    participant L as Server B Listener
    participant UC as ProcessCouponIssueUseCase
    participant RS as RedisCouponStockPort
    participant Redis as Redis Lua Script
    participant PUB as IssueProcessedPublisher
    participant MQ2 as RabbitMQ issue.processed

    MQ1->>L: issue.requested
    L->>UC: process(event)
    UC->>RS: issue(requestId, promotionId, userId)
    RS->>Redis: EVAL issue coupon script

    alt existing request result
        Redis-->>RS: previous result
    else already issued user
        Redis-->>RS: DUPLICATE
    else stock <= 0
        Redis-->>RS: SOLD_OUT
    else stock available
        Redis->>Redis: DECR stock
        Redis->>Redis: SADD issued-users userId
        Redis->>Redis: SET issue:{requestId}:result SUCCESS
        Redis-->>RS: SUCCESS
    end

    RS-->>UC: result
    UC->>PUB: publish issue.processed
    PUB->>MQ2: publish result event
    L->>MQ1: ack
```

핵심 포인트

- Redis Lua script가 재고 확인, 중복 확인, 차감을 원자적으로 처리한다.
- `issue:{requestId}:result`가 있어 재전달 시 결과가 바뀌지 않는다.
- 메시지는 처리와 발행이 끝난 뒤 ack한다.

---

## Server C 최종 저장

```mermaid
sequenceDiagram
    participant MQ as RabbitMQ issue.processed
    participant L as Server C Listener
    participant UC as FinalizeCouponIssueUseCase
    participant RP as CouponIssueResultPort
    participant DB as MySQL server_c_db

    MQ->>L: issue.processed
    L->>UC: finalize(event)
    UC->>RP: saveResult(event)
    RP->>DB: INSERT coupon_issue_result

    alt saved
        DB-->>RP: success
        RP-->>UC: saved result
        L->>MQ: ack
    else duplicate request_id
        DB-->>RP: duplicate key
        RP-->>UC: idempotent success
        L->>MQ: ack
    else duplicate promotion_id + user_id
        DB-->>RP: duplicate key
        RP-->>UC: duplicate final issue
        L->>MQ: ack
    end
```

핵심 포인트

- Server C는 최종 정합성 방어선이다.
- `request_id` unique constraint는 동일 이벤트 재처리를 막는다.
- `promotion_id + user_id` unique constraint는 최종 중복 발급을 막는다.

---

## 멱등성 재요청

```mermaid
sequenceDiagram
    participant C as Client
    participant API as Server A Web Adapter
    participant UC as IssueCouponUseCase
    participant RP as CouponIssueRequestPort
    participant DB as MySQL server_a_db

    C->>API: POST issue with same Idempotency-Key
    API->>UC: issue(command)
    UC->>RP: saveRequest(command)
    RP->>DB: INSERT coupon_issue_request

    alt same idempotency_key exists
        DB-->>RP: duplicate key
        RP->>DB: SELECT existing request
        DB-->>RP: existing request
        RP-->>UC: existing accepted result
        UC-->>API: accepted(existing requestId)
        API-->>C: 200 OK or 202 ACCEPTED
    else same promotion_id + user_id exists
        DB-->>RP: duplicate key
        RP->>DB: SELECT existing request
        DB-->>RP: existing request
        UC-->>API: duplicate or accepted existing result
        API-->>C: 200 OK or 409 CONFLICT
    end
```

핵심 포인트

- 같은 idempotency key의 재요청은 새 이벤트를 만들지 않는다.
- 같은 사용자와 같은 프로모션 조합은 한 번만 접수한다.
- 최종 중복 방어는 Server C에서도 한 번 더 수행한다.

---

## 장애 및 재시도

```mermaid
sequenceDiagram
    participant A as Server A
    participant DB as MySQL server_a_db
    participant R as OutboxRelay
    participant MQ as RabbitMQ
    participant B as Server B
    participant DLQ as Dead Letter Queue

    A->>DB: INSERT request + outbox
    A--xA: crash before publish
    R->>DB: SELECT pending outbox
    R->>MQ: publish issue.requested

    MQ->>B: deliver message
    B--xB: processing failure before ack
    MQ->>B: redeliver message

    alt retry exhausted
        MQ->>DLQ: move message
    else success
        B->>MQ: ack
    end
```

핵심 포인트

- Server A 장애는 outbox relay로 복구한다.
- Server B/C 장애는 RabbitMQ ack와 재전달로 복구한다.
- 반복 실패 메시지는 DLQ로 격리해 전체 파이프라인을 막지 않는다.

---

## 부하 테스트 흐름

```mermaid
sequenceDiagram
    participant K6 as k6
    participant A as Server A
    participant MQ as RabbitMQ
    participant B as Server B
    participant Redis as Redis
    participant C as Server C
    participant DB as MySQL

    K6->>A: concurrent issue requests
    A-->>K6: 202 ACCEPTED
    A->>MQ: issue.requested
    MQ->>B: consume with prefetch
    B->>Redis: Lua issue script
    B->>MQ: issue.processed
    MQ->>C: consume with prefetch
    C->>DB: save final result
```

측정 포인트

- Server A HTTP TPS
- p95 latency
- HTTP error rate
- RabbitMQ backlog
- Redis 처리 결과 분포
- Server C 최종 저장 건수
