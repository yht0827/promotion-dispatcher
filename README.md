# Promotion Dispatcher

## 프로젝트 개요

선착순 프로모션 쿠폰 발급 요청을 비동기로 처리하는 멀티 모듈 Spring Boot 프로젝트입니다.

클라이언트는 Server A에 쿠폰 발급 요청을 보내고, Server A는 요청 원장과 Outbox를 저장한 뒤 즉시 `202 ACCEPTED`를 반환합니다. 이후 Server B가 Redis Lua script로 재고를 원자적으로 차감하고, Server C가 최종 발급 결과를 MySQL에 저장합니다.

```text
Client
  -> Server A: 요청 접수, 멱등성 검증, Rate Limit, Outbox 저장
  -> RabbitMQ: issue.requested
  -> Server B: Redis Lua script 재고 차감, 처리 결과 발행
  -> RabbitMQ: issue.processed
  -> Server C: 최종 발급 결과 저장
```

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.14 |
| Build | Gradle |
| Database | MySQL 8.4 |
| Cache / Atomic Stock | Redis 7.4, Lua Script |
| Message Broker | RabbitMQ 3.13 |
| Migration | Flyway |
| Test | JUnit 5, Testcontainers |
| Load Test | k6 |

## 서비스 구성

```text
promotion-dispatcher/
├── common      # A/B/C가 공유하는 이벤트 DTO, 결과 타입
├── server-a    # HTTP API, 요청 원장, Outbox, RabbitMQ publish
├── server-b    # issue.requested consume, Redis 재고 처리, issue.processed publish
├── server-c    # issue.processed consume, 최종 결과 저장
├── docs        # 요구사항, 시퀀스, 클래스, ERD, 성능/장애 검증 문서
├── http        # API 호출 예시
└── k6          # 부하 테스트 스크립트
```

각 서버는 독립 Spring Boot application으로 실행되며, 각 모듈 내부는 `domain`, `application`, `adapter`, `config` 기준의 Hexagonal Architecture를 따릅니다.

## 핵심 설계

### 1. 대량 요청 접수

- Server A는 최종 발급 완료를 기다리지 않고 요청 접수까지만 동기 처리합니다.
- `coupon_issue_request`와 `outbox_event`를 같은 transaction에 저장해 요청과 이벤트 발행 의도를 함께 남깁니다.
- Redis 기반 사용자별 rate limit으로 과도한 반복 요청을 초기에 제한합니다.
- MySQL hot path에서는 비관적 락을 사용하지 않고 unique constraint로 중복을 방어합니다.

### 2. 데이터 정합성

- Server A는 `idempotency_key`, `promotion_id + user_id` 기준으로 중복 접수를 막습니다.
- Server B는 Redis Lua script로 재고 확인, 사용자 중복 확인, 재고 차감을 원자적으로 처리합니다.
- Server B는 `requestId`별 처리 결과를 Redis에 저장해 메시지가 재전달되어도 같은 결과를 재사용합니다.
- Server C는 `request_id`, `promotion_id + user_id` unique constraint로 최종 저장 중복을 방어합니다.

### 3. 장애 대응

- Server A는 Transactional Outbox로 DB 저장 후 RabbitMQ 발행 전 장애 상황을 복구할 수 있습니다.
- Server B/C consumer는 manual ack를 사용해 처리 완료 전 장애 시 메시지가 재전달되도록 합니다.
- B -> C 발행은 broker confirm 이후 기존 메시지를 ack합니다.
- 실패 메시지는 retry wait queue를 거쳐 재시도되고, 최대 횟수 초과 시 DLQ에 격리됩니다.

### 4. Backpressure

- Server A: Redis rate limit으로 사용자 단위 burst를 제한합니다.
- RabbitMQ: 처리량을 초과한 메시지를 queue에 buffer합니다.
- Server B/C: `prefetch`, `concurrency`, `max-concurrency`로 한 번에 처리하는 메시지 수를 제한합니다.

## API

외부 HTTP API는 Server A만 제공합니다.

| 기능 | Method | URI |
|---|---|---|
| 쿠폰 발급 요청 | POST | `/api/v1/promotions/{promotionId}/coupons/issue` |
| Health Check | GET | `/actuator/health` |

요청 예시는 [http/promotion-api.http](http/promotion-api.http)에서 확인할 수 있습니다.

```http
POST http://localhost:8081/api/v1/promotions/1/coupons/issue
Content-Type: application/json
X-User-Id: 1001
Idempotency-Key: idem-promotion-1-user-1001

{
  "requestField1": "value-1",
  "requestField2": "value-2",
  "requestField3": "value-3",
  "requestField4": "value-4",
  "requestField5": "value-5",
  "requestField6": "value-6",
  "requestField7": "value-7",
  "requestField8": "value-8",
  "requestField9": "value-9",
  "requestField10": "value-10"
}
```

정상 접수 응답:

```json
{
  "requestId": "7f3db2fd-9c8c-4f49-85c1-f36f5c35a9c2",
  "status": "ACCEPTED"
}
```

## 실행 방법

### 1. 인프라 실행

```bash
docker compose up -d
```

실행되는 인프라:

- MySQL: `localhost:3306`
- Redis: `localhost:6379`
- RabbitMQ: `localhost:5672`
- RabbitMQ Management: `http://localhost:15672`

RabbitMQ 계정:

```text
username: promotion
password: promotion
```

### 2. 애플리케이션 실행

터미널을 3개 열고 각각 실행합니다.

```bash
./gradlew :server-a:bootRun
./gradlew :server-b:bootRun
./gradlew :server-c:bootRun
```

기본 포트:

- Server A: `8081`
- Server B: `8082`
- Server C: `8083`

### 3. 테스트 실행

```bash
./gradlew test
```

### 4. API 호출 테스트

IntelliJ HTTP Client 또는 VS Code REST Client에서 아래 파일을 실행합니다.

```text
http/promotion-api.http
```

### 5. 부하 테스트

테스트 전 Redis 재고를 설정합니다.

```bash
export PROMOTION_ID=2026050401
export STOCK=100

docker exec promotion-redis redis-cli \
  SET promotion:${PROMOTION_ID}:stock ${STOCK}
```

k6 실행:

```bash
PROMOTION_ID=${PROMOTION_ID} VUS=1000 ITERATIONS=1 MAX_DURATION=2m \
  k6 run k6/coupon-issue-1000-users.js
```

상세 초기화 명령과 결과 확인 쿼리는 [docs/05-performance-test.md](docs/05-performance-test.md)에 정리했습니다.

## 성능 테스트와 인프라 사이징

1vCPU, 2GB로 제한한 Server A 기준 안정 처리량은 보수적으로 약 `80 TPS`로 측정했습니다.

측정 조건:

- Server A: Docker container, `--cpus=1`, `--memory=2g`
- JVM: `-XX:ActiveProcessorCount=1 -Xms512m -Xmx1536m`
- MySQL, Redis, RabbitMQ: Docker Compose
- Server B/C: local `bootRun`

100 VU 반복 측정 평균:

```text
요청 수  TPS   avg(ms)  p95(ms)  p99(ms)  HTTP error  최종 결과     backlog
100      80.5  756.7    1201.4   1260.6   0%          SUCCESS 100   0
```

100,000명 동시 접속 기준 Server A 필요 인스턴스 수:

```text
목표 처리 시간 1초:
ceil(100,000 / 80) = 1,250대

목표 처리 시간 10초:
ceil(10,000 / 80) = 125대

목표 처리 시간 60초:
ceil(1,667 / 80) = 21대
```

이 계산은 Server A 요청 접수 기준입니다. 운영 환경에서는 MySQL 쓰기 처리량, RabbitMQ publish 처리량, Server B/C consumer 처리량, Redis hot key 처리량을 함께 맞춰야 합니다.

## 장애 검증 결과

장애 시나리오는 [docs/06-failure-scenarios.md](docs/06-failure-scenarios.md)에 정리했습니다.

검증한 항목:

- Server B 중지 중에도 Server A는 요청을 접수하고 RabbitMQ backlog에 메시지를 보관합니다.
- Server B 재기동 후 `issue.requested.queue` backlog가 처리되고 최종 결과가 저장됩니다.
- Server C 중지 중에도 Server B는 Redis 재고 처리를 완료하고 `issue.processed.queue`에 결과를 보관합니다.
- Server C 재기동 후 backlog가 처리되고 MySQL `server_c_db`에 최종 결과가 저장됩니다.
- invalid payload는 retry wait queue를 거쳐 DLQ로 격리됩니다.

## 문서

| 문서 | 내용 |
|---|---|
| [01 요구사항 정의](docs/01-requirements.md) | 유비쿼터스 언어, 서비스 책임, API, 정합성 요구사항 |
| [02 시퀀스 다이어그램](docs/02-sequence-diagrams.md) | A -> B -> C 주요 처리 흐름 |
| [03 클래스 다이어그램](docs/03-class-diagrams.md) | 서버별 Hexagonal Architecture 구조 |
| [04 ERD](docs/04-erd.md) | Server A/C 테이블 설계와 unique constraint |
| [05 성능 테스트](docs/05-performance-test.md) | k6 테스트, TPS 측정, 100,000명 사이징 |
| [06 장애 검증](docs/06-failure-scenarios.md) | 장애 격리, retry, DLQ, backpressure 검증 |

## 제출 관점 요약

- Source Code: 이 저장소
- Design Document: `README.md`, `docs/01` ~ `docs/06`
- Data Flow: `docs/02-sequence-diagrams.md`
- 동시성 및 정합성 해결 전략: `docs/01-requirements.md`, `docs/04-erd.md`, `docs/06-failure-scenarios.md`
- 부하 테스트 결과: `docs/05-performance-test.md`
- 인프라 확장 계획 및 계산 근거: `README.md`, `docs/05-performance-test.md`
