# Phase 5: Infrastructure Flows

## Context Links
- [api-gateway application.yml](../../api-gateway/src/main/resources/application.yml) — route definitions
- [JwtAutoConfiguration.java](../../jwt-auth-spring-boot-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAutoConfiguration.java) (62 lines)
- [JwtAuthenticationFilter.java (starter)](../../jwt-auth-spring-boot-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAuthenticationFilter.java)
- [JwtTokenValidator.java](../../jwt-auth-spring-boot-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtTokenValidator.java)
- [KafkaConsumerConfig.java](../../booking-service/src/main/java/com/namnd/bookingservice/config/KafkaConsumerConfig.java) (37 lines)
- [KafkaTopics.java](../../kafka-events/src/main/java/com/namnd/kafka/events/topic/KafkaTopics.java)
- [EventEnvelope.java](../../kafka-events/src/main/java/com/namnd/kafka/events/envelope/EventEnvelope.java)

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Mermaid sequence diagrams for cross-cutting infrastructure: API Gateway routing, Eureka service discovery, Config Server bootstrap, Kafka DLT error handling, and JWT starter filter chain.

## Key Insights from Code

### API Gateway Routing (application.yml)
7 business routes + 4 Swagger doc routes:
- /api/auth/** -> lb://auth-service
- /api/users/** -> lb://auth-service
- /api/movies/** -> lb://movie-service
- /api/showtimes/** -> lb://movie-service
- /api/theaters/** -> lb://movie-service
- /api/bookings/** -> lb://booking-service
- /api/payments/** -> lb://payment-service
- Swagger: /{service}/v3/api-docs/** with StripPrefix=1 filter

### Eureka Service Discovery
- All services register with eureka-server on startup
- Gateway uses `lb://` prefix for load-balanced routing via Eureka
- eureka.instance.prefer-ip-address=true

### Config Server Bootstrap
- All services import: `optional:configserver:http://${CONFIG_SERVER_HOST:localhost}:8888`
- Config Server serves: shared JWT secret (namnd.app.jwtSecret, jwt.auth.secret), per-service configs
- config-repo/application.yml = shared properties

### JWT Starter Filter Chain (JwtAutoConfiguration)
- @AutoConfiguration, @ConditionalOnClass(SecurityFilterChain.class)
- Beans: JwtTokenValidator, JwtAuthenticationFilter, SecurityFilterChain
- JwtAuthenticationFilter: extract Bearer -> JwtTokenValidator.validate() -> set JwtAuthenticatedUser in SecurityContext
- JwtAuthProperties: secret, publicPaths[] (e.g., /actuator/prometheus)
- Used by movie-service, booking-service, payment-service

### Kafka DLT Error Handling (KafkaConsumerConfig)
- DefaultErrorHandler with ExponentialBackOffWithMaxRetries(3)
- Back-off: 1s -> 2s -> 4s, max 10s
- DeadLetterPublishingRecoverer: forward to DLT topic after retries exhausted
- Not retryable: SerializationException, MessageConversionException

### Kafka Topic Registry (KafkaTopics.java)
- PAYMENT_EVENTS = "payment-events"
- MOVIE_EVENTS = "movie-events"

### EventEnvelope Structure
Record: eventId (UUID), eventType (discriminator), source (service name), correlationId, timestamp, payload (generic T)

## Diagrams to Create (5 total)

### 1. API Gateway Routing Flow
Participants: Client, API Gateway, Eureka, auth-service, movie-service, booking-service, payment-service
- Show route resolution: path matching -> Eureka lookup -> load-balanced forward
- Include Swagger doc routing with StripPrefix

### 2. Service Discovery & Registration Flow
Participants: auth-service, movie-service, booking-service, payment-service, api-gateway, Eureka Server
- Startup: register with Eureka
- Runtime: heartbeat, instance list refresh
- Gateway: resolve `lb://service-name` to actual host:port

### 3. Config Server Bootstrap Flow
Participants: config-server, config-repo, auth-service, movie-service, booking-service, payment-service
- Startup: services fetch config from config-server
- Config-server loads from config-repo (file system or git)
- Shared properties: JWT secret distributed to all services

### 4. Kafka DLT Error Handling Flow
Participants: Kafka (payment-events), PaymentEventListener, DefaultErrorHandler, DeadLetterPublishingRecoverer, Kafka (payment-events.DLT)
- Happy path: message consumed successfully
- Retry path: 3 retries with exponential back-off
- DLT path: after retries exhausted, forward to dead-letter topic

### 5. JWT Starter Authentication Filter Flow
Participants: Client, JwtAuthenticationFilter (starter), JwtTokenValidator, SecurityContext, Controller
- Extract Bearer token from Authorization header
- JwtTokenValidator.validate() — HS512 signature + expiry check
- Build JwtAuthenticatedUser(userId, email, roles) from claims
- Set SecurityContext -> continue to controller
- Public paths bypass filter

## Source Files to Reference
- `api-gateway/src/main/resources/application.yml`
- `jwt-auth-spring-boot-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAutoConfiguration.java`
- `jwt-auth-spring-boot-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAuthenticationFilter.java`
- `jwt-auth-spring-boot-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtTokenValidator.java`
- `jwt-auth-spring-boot-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAuthenticatedUser.java`
- `jwt-auth-spring-boot-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAuthProperties.java`
- `booking-service/src/main/java/com/namnd/bookingservice/config/KafkaConsumerConfig.java`
- `kafka-events/src/main/java/com/namnd/kafka/events/topic/KafkaTopics.java`
- `kafka-events/src/main/java/com/namnd/kafka/events/envelope/EventEnvelope.java`

## Todo
- [ ] API Gateway routing sequence diagram
- [ ] Service discovery & registration sequence diagram
- [ ] Config Server bootstrap sequence diagram
- [ ] Kafka DLT error handling sequence diagram
- [ ] JWT starter filter chain sequence diagram
- [ ] Document EventEnvelope schema as a reference table
- [ ] Document Kafka topic registry table

## Success Criteria
- All 5 infrastructure flows have sequence diagrams
- Gateway route table matches application.yml exactly
- JWT starter filter shows the difference vs auth-service's own JwtAuthenticationFilter
- DLT flow shows retry count, back-off intervals, and non-retryable exceptions
- EventEnvelope fields documented with examples
