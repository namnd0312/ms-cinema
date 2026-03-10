# Research Report: Stripe PaymentIntent + Spring Cloud OpenFeign
Date: 2026-03-03

---

## Topic 1: Stripe PaymentIntent Integration (Spring Boot 3.4.3 / Java 21)

### Maven Dependency
```xml
<dependency>
    <groupId>com.stripe</groupId>
    <artifactId>stripe-java</artifactId>
    <version>27.x</version> <!-- verify latest at mvnrepository.com/artifact/com.stripe/stripe-java -->
</dependency>
```
Latest stable as of 2025: **27.x** series (check MVN repo for exact patch). No Spring Boot starter needed — plain SDK.

### PaymentIntent Flow: create → confirm → webhook

```java
// 1. CREATE (server-side, return clientSecret to frontend)
Stripe.apiKey = "sk_...";
PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
    .setAmount(10000L)          // cents
    .setCurrency("usd")
    .setAutomaticPaymentMethods(
        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
            .setEnabled(true).build())
    .setIdempotencyKey(orderId) // pass as RequestOptions, not param
    .build();
RequestOptions opts = RequestOptions.builder()
    .setIdempotencyKey("order-" + orderId).build();
PaymentIntent intent = PaymentIntent.create(params, opts);
// return intent.getClientSecret() to frontend

// 2. CONFIRM — handled client-side via Stripe.js (stripe.confirmPayment)
// Server does NOT call confirm() for modern Payment Element flow.

// 3. REFUND
RefundCreateParams refundParams = RefundCreateParams.builder()
    .setPaymentIntent(paymentIntentId)
    .setAmount(5000L) // partial; omit for full refund
    .build();
Refund refund = Refund.create(refundParams,
    RequestOptions.builder().setIdempotencyKey("refund-" + orderId).build());
```

### Webhook Signature Verification (Spring Boot)

```java
@RestController
@RequestMapping("/webhooks")
public class StripeWebhookController {

    @Value("${stripe.webhook-secret}") String webhookSecret;

    @PostMapping(value = "/stripe", consumes = "application/json")
    public ResponseEntity<String> handle(
            HttpServletRequest request,
            @RequestHeader("Stripe-Signature") String sigHeader) throws IOException {
        String payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.status(400).body("Invalid signature");
        }
        // Idempotency: check event.getId() in DB before processing
        switch (event.getType()) {
            case "payment_intent.succeeded" -> handleSuccess(event);
            case "payment_intent.payment_failed" -> handleFailed(event);
        }
        return ResponseEntity.ok("received");
    }
}
```

Key config: use `@RequestBody String` or raw `InputStream` — **do NOT** use `@RequestBody` with JSON parser (breaks signature).

### Best Practices
- **Idempotency keys**: set per-operation via `RequestOptions`, use stable IDs (e.g., `"pay-{orderId}"`).
- **Webhook idempotency**: store `event.getId()` in DB; skip if already processed.
- **Respond fast**: return 200 immediately; process in async/queue (e.g., `@Async` or Kafka).
- **Error handling**: catch `StripeException` subtypes: `CardException`, `RateLimitException`, `InvalidRequestException`, `AuthenticationException`, `ApiConnectionException`.
- **application.yml**:
  ```yaml
  stripe:
    api-key: ${STRIPE_API_KEY}
    webhook-secret: ${STRIPE_WEBHOOK_SECRET}
  ```

---

## Topic 2: Spring Cloud OpenFeign + Eureka (Spring Boot 3.4.x / Spring Cloud 2024.0.1)

### Maven Dependencies
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
<!-- LoadBalancer included transitively via openfeign starter; explicit if needed: -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```
Spring Cloud BOM `2024.0.1` manages all versions. **Ribbon is removed** — Spring Cloud LoadBalancer is the default.

### Enable Feign
```java
@SpringBootApplication
@EnableFeignClients(basePackages = "com.example.client")
public class BookingServiceApplication { ... }
```

### FeignClient Interface Pattern (Eureka service name)
```java
@FeignClient(name = "movie-service") // matches spring.application.name in Eureka
public interface MovieServiceClient {

    @GetMapping("/api/movies/{id}")
    MovieDto getMovie(@PathVariable("id") Long id);

    @GetMapping("/api/movies")
    List<MovieDto> listMovies();
}
```
`name` resolves via Eureka → Spring Cloud LoadBalancer picks an instance automatically.

### JWT Propagation via RequestInterceptor
```java
@Component
public class JwtFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            String auth = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (StringUtils.hasText(auth)) {
                template.header(HttpHeaders.AUTHORIZATION, auth);
            }
        }
    }
}
```
Register globally (Spring bean auto-detected) or per-client via `@FeignClient(configuration = MyConfig.class)`.

### Error Handling
```java
// Option A: ErrorDecoder (recommended for non-2xx mapping to exceptions)
@Component
public class FeignErrorDecoder implements ErrorDecoder {
    @Override
    public Exception decode(String methodKey, Response response) {
        return switch (response.status()) {
            case 404 -> new ResourceNotFoundException("Not found via Feign: " + methodKey);
            case 503 -> new ServiceUnavailableException();
            default -> new Default().decode(methodKey, response);
        };
    }
}

// Option B: Fallback with Resilience4j CircuitBreaker
// feign.circuitbreaker.enabled=true in application.yml
@FeignClient(name = "movie-service", fallback = MovieServiceFallback.class)
public interface MovieServiceClient { ... }

@Component
public class MovieServiceFallback implements MovieServiceClient {
    @Override
    public MovieDto getMovie(Long id) {
        return MovieDto.empty(); // safe default
    }
}
```

### application.yml snippet
```yaml
spring:
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true
      client:
        config:
          default:
            connectTimeout: 3000
            readTimeout: 5000
            loggerLevel: BASIC

eureka:
  client:
    serviceUrl:
      defaultZone: http://eureka-server:8761/eureka/
```

### Spring Cloud LoadBalancer (replacing Ribbon)
- Included automatically with `spring-cloud-starter-openfeign` in 2024.x.
- Default strategy: **Round Robin**. Switch to random:
  ```java
  @Bean
  ReactorLoadBalancer<ServiceInstance> randomLoadBalancer(
          Environment env, LoadBalancerClientFactory factory) {
      return new RandomLoadBalancer(
          factory.getLazyProvider(env.getProperty(LoadBalancerClientFactory.PROPERTY_NAME),
              ServiceInstanceListSupplier.class),
          env.getProperty(LoadBalancerClientFactory.PROPERTY_NAME));
  }
  ```

---

## Unresolved Questions
1. Exact latest `stripe-java` patch version (confirm at [MVN Repo](https://mvnrepository.com/artifact/com.stripe/stripe-java) — was 27.x range in late 2025).
2. Whether circuit-breaker fallback should use Resilience4j or native Feign fallback — depends on if `spring-cloud-circuitbreaker-resilience4j` is added to the BOM.
3. Stripe PaymentIntent flow: confirm if the project uses Payment Element (client confirms) vs. server-side confirm — affects whether `PaymentIntent.confirm()` is needed server-side.
4. Thread-local propagation: `RequestContextHolder` approach requires `RequestContextListener` registered if using async threads.

---

Sources:
- [stripe-java GitHub](https://github.com/stripe/stripe-java)
- [Stripe PaymentIntent API](https://docs.stripe.com/api/payment_intents/create?lang=java)
- [Stripe Webhooks Docs](https://docs.stripe.com/webhooks)
- [Spring Cloud OpenFeign Docs](https://docs.spring.io/spring-cloud-openfeign/docs/current/reference/html/)
- [Feign + Eureka + LoadBalancer Guide](https://www.geeksforgeeks.org/java/java-spring-boot-microservices-integration-of-eureka-feign-spring-cloud-load-balancer/)
- [Spring Boot Stripe Webhooks (Medium)](https://medium.com/@s42401191401/from-payment-intent-to-webhooks-automating-stripe-notifications-with-spring-boot-e6a72edf3cde)
