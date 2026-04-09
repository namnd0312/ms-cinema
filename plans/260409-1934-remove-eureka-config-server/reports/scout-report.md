# Scout Report: Eureka & Config Server References

## eureka-server/ Module (DELETE ENTIRE DIR)
- `eureka-server/pom.xml` — module pom with `spring-cloud-starter-netflix-eureka-server`
- `eureka-server/Dockerfile` — container build
- `eureka-server/src/main/java/com/namnd/eurekaserver/EurekaServerApplication.java` — `@EnableEurekaServer`
- `eureka-server/src/main/resources/application.yml` — eureka server config

## config-server/ Module (DELETE ENTIRE DIR)
- `config-server/pom.xml` — `spring-cloud-config-server`, `spring-cloud-starter-netflix-eureka-client`
- `config-server/Dockerfile` — container build
- `config-server/src/main/java/com/namnd/configserver/ConfigServerApplication.java` — main class
- `config-server/src/main/resources/application.yml` — config server settings
- `config-server/src/main/resources/config-repo/` — audit-service.yml, api-gateway.yml, auth-service.yml, notification-service.yml, application.yml

## Root pom.xml (L35-36)
- `<module>eureka-server</module>` (L35)
- `<module>config-server</module>` (L36)

## Service pom.xml — Eureka Client Dependency
| File | Line | Dependency |
|------|------|------------|
| auth-service/pom.xml | L110-113 | `spring-cloud-starter-netflix-eureka-client` |
| api-gateway/pom.xml | L26 | `spring-cloud-starter-netflix-eureka-client` |
| movie-service/pom.xml | L57-60 | `spring-cloud-starter-netflix-eureka-client` |
| booking-service/pom.xml | L66-69 | `spring-cloud-starter-netflix-eureka-client` |
| payment-service/pom.xml | L67-70 | `spring-cloud-starter-netflix-eureka-client` |
| notification-service/pom.xml | L77-80 | `spring-cloud-starter-netflix-eureka-client` |
| audit-service/pom.xml | L65-68 | `spring-cloud-starter-netflix-eureka-client` |

## Service pom.xml — Config Client Dependency
| File | Line | Dependency |
|------|------|------------|
| auth-service/pom.xml | L119 | `spring-cloud-starter-config` |
| api-gateway/pom.xml | L30 | `spring-cloud-starter-config` |
| movie-service/pom.xml | L64 | `spring-cloud-starter-config` |
| booking-service/pom.xml | L73 | `spring-cloud-starter-config` |
| payment-service/pom.xml | L74 | `spring-cloud-starter-config` |
| notification-service/pom.xml | L84 | `spring-cloud-starter-config` |
| audit-service/pom.xml | L72 | `spring-cloud-starter-config` |

## Service application.yml — Eureka Config Blocks
| File | Lines | Content |
|------|-------|---------|
| auth-service/src/main/resources/application.yml | L55-59 | `eureka:` block + `register-with-eureka: true` |
| api-gateway/src/main/resources/application.yml | L123-126 | `eureka:` block |
| movie-service/src/main/resources/application.yml | L33-36 | `eureka:` block |
| booking-service/src/main/resources/application.yml | L50-53 | `eureka:` block |
| payment-service/src/main/resources/application.yml | L58-61 | `eureka:` block |
| notification-service/src/main/resources/application.yml | L50-53 | `eureka:` block |
| audit-service/src/main/resources/application.yml | L35-38 | `eureka:` block |

## Service application.yml — Config Server Import
| File | Line | Content |
|------|------|---------|
| auth-service/src/main/resources/application.yml | L7 | `import: "optional:configserver:..."` |
| api-gateway/src/main/resources/application.yml | L8 | `import: "optional:configserver:..."` |
| movie-service/src/main/resources/application.yml | L8 | `import: "optional:configserver:..."` |
| booking-service/src/main/resources/application.yml | L8 | `import: "optional:configserver:..."` |
| payment-service/src/main/resources/application.yml | L8 | `import: "optional:configserver:..."` |
| notification-service/src/main/resources/application.yml | L8 | `import: "optional:configserver:..."` |
| audit-service/src/main/resources/application.yml | L8 | `import: "optional:configserver:..."` |

## api-gateway application-k8s.yml
- L1: comment referencing "no Eureka", "disable config-server"
- L101-103: `eureka: client: enabled: false`

## docker-compose.yml
- L68-79: `eureka-server` service definition
- L81-95: `config-server` service definition (depends_on eureka-server)
- L106-107: api-gateway depends_on eureka-server, config-server + env vars
- L126-128: auth-service depends_on eureka-server, config-server + env vars
- L153-155: movie-service depends_on eureka-server, config-server + env vars
- L179-181: booking-service depends_on eureka-server, config-server + env vars
- L205-207: payment-service depends_on eureka-server, config-server + env vars
- L231-233: notification-service depends_on eureka-server, config-server + env vars
- L261-263: audit-service depends_on eureka-server, config-server + env vars

## K8s Manifests
- `k8s/base/configmap.yml` L7-11: comments + `EUREKA_CLIENT_ENABLED`, `SPRING_CLOUD_CONFIG_ENABLED`

## Prometheus
- `monitoring/prometheus/prometheus.yml` L35-38: eureka-server scrape job
- `monitoring/prometheus/prometheus.yml` L40-43: config-server scrape job

## Documentation (update references)
- `README.md` L11, L34, L49-50, L130, L164, L168
- `docs/project-overview-pdr.md` L11, L23, L186, L189, L544, L587, L595, L609, L647
- `docs/system-architecture.md` L23-25, L53-65
- `docs/codebase-summary.md` L15-16, L32-33, L60
- `docs/project-changelog.md` L398
- `docs/project-roadmap.md` L41, L151
- `docs/system-design-mermaid-diagrams-all-services-flows.md` — multiple eureka/config-server refs in diagrams

## api-gateway pom.xml
- L16: description mentions "Eureka discovery"

## Notes
- No `@EnableDiscoveryClient` or `@EnableEurekaClient` annotations in business services (auto-configured via classpath)
- `repomix-output.xml` has many references but is generated output, not source — ignore
- Plans/ dir has historical references — ignore (read-only history)
