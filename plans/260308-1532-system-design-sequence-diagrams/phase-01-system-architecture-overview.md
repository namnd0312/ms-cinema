# Phase 1: System Architecture Overview

## Context Links
- [system-architecture.md](../../docs/system-architecture.md)
- [codebase-summary.md](../../docs/codebase-summary.md)
- [docker-compose.yml](../../docker-compose.yml)
- [api-gateway application.yml](../../api-gateway/src/main/resources/application.yml)

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Create top-level architecture diagrams: C4-style component diagram, service catalog table, infrastructure layout, and data flow overview.

## Key Insights from Code
- 9 Maven modules: auth-service, movie-service, booking-service, payment-service, api-gateway, eureka-server, config-server, jwt-auth-spring-boot-autoconfigure, jwt-auth-spring-boot-starter
- kafka-events shared library (not a runnable service)
- Ports: gateway 8080, auth 8081, movie 8082, booking 8083, payment 8084, eureka 8761, config 8888, prometheus 9090, grafana 3000
- Infra: PostgreSQL 5432 (auth-db, movie-db, booking-db, payment-db), Redis 6379, Kafka 9092
- All services register with Eureka; gateway uses `lb://` for load-balanced routing
- Config Server distributes shared JWT secret to all services
- jwt-auth-spring-boot-starter used by movie, booking, payment services for JWT filter auto-config

## Diagrams to Create

### 1. C4-Style Component Diagram (Mermaid `graph TD`)
Participants:
- Client (Web/Mobile)
- api-gateway (:8080)
- eureka-server (:8761)
- config-server (:8888)
- auth-service (:8081)
- movie-service (:8082)
- booking-service (:8083)
- payment-service (:8084)
- PostgreSQL (auth-db, movie-db, booking-db, payment-db)
- Redis (:6379)
- Kafka (:9092)
- Stripe API (external)
- SMTP/Gmail (external)
- Prometheus (:9090)
- Grafana (:3000)

### 2. Service Catalog Table
Columns: Service, Port, Database, Dependencies, Kafka Topics, Key Endpoints

### 3. Infrastructure Layout Diagram (Mermaid `graph LR`)
Docker Compose network topology showing `my-net` bridge

### 4. Data Flow Overview (Mermaid `flowchart`)
High-level request flow: Client -> Gateway -> Eureka lookup -> downstream service -> DB/Redis/Kafka

## Source Files to Reference
- `docker-compose.yml` — all container definitions and ports
- `api-gateway/src/main/resources/application.yml` — route definitions
- `eureka-server/src/main/resources/application.yml`
- `config-server/src/main/resources/application.yml`
- `monitoring/prometheus/prometheus.yml` — scrape targets

## Todo
- [ ] C4 component diagram with all services and data stores
- [ ] Service catalog table with ports, databases, topics
- [ ] Docker infrastructure layout diagram
- [ ] Data flow overview diagram
- [ ] Verify all port numbers match docker-compose.yml

## Success Criteria
- Diagrams render in GitHub Markdown
- All 9 modules represented with correct ports
- External dependencies (Stripe, SMTP) shown
- Monitoring stack (Prometheus, Grafana) included
