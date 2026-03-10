# Phase 1: Convert to Multi-Module Maven Project

## Context Links
- [Current pom.xml](/pom.xml)
- [Plan overview](./plan.md)

## Overview
- **Priority:** P1 (blocking all other phases)
- **Status:** completed
- **Effort:** 2h
- Convert single-module project to multi-module Maven parent POM. Move all existing source code into `auth-service` module. Add empty modules for starter library.

## Key Insights
- Current artifactId `spring-jwt` becomes parent; `auth-service` becomes the runnable Spring Boot app
- Parent POM uses `<packaging>pom</packaging>`, no Spring Boot plugin at parent level
- Spring Boot plugin only in `auth-service` module (the executable JAR)
- JJWT version (0.12.6) should be managed in parent `<dependencyManagement>` since both auth-service and autoconfigure need it

## Requirements

### Functional
- All existing functionality must work identically after restructure
- `mvn clean install` from root builds all modules
- `auth-service` produces executable JAR (same as current `spring-jwt.jar`)

### Non-functional
- No code changes in existing Java files (only POM restructure + file moves)
- Dockerfile updated to reference new JAR path

## Architecture
```
jwt-spring-security/           (parent POM)
├── pom.xml                    (packaging=pom, modules list, dependencyManagement)
├── auth-service/
│   ├── pom.xml                (inherits parent, has spring-boot-maven-plugin)
│   └── src/main/java/...     (all existing code moved here)
├── jwt-auth-spring-boot-autoconfigure/
│   └── pom.xml                (empty module, populated in Phase 3)
├── jwt-auth-spring-boot-starter/
│   └── pom.xml                (empty module, populated in Phase 3)
├── docker-compose.yml
└── Dockerfile                 (updated JAR path)
```

## Related Code Files

### Files to Modify
- `/pom.xml` -- convert to parent POM
- `/Dockerfile` -- update JAR path from `target/spring-jwt.jar` to `auth-service/target/auth-service.jar`
- `/docker-compose.yml` -- update build context if needed

### Files to Create
- `/auth-service/pom.xml`
- `/jwt-auth-spring-boot-autoconfigure/pom.xml` (skeleton)
- `/jwt-auth-spring-boot-starter/pom.xml` (skeleton)

### Files to Move
- `/src/main/` --> `/auth-service/src/main/`
- `/src/test/` --> `/auth-service/src/test/`
- `/src/main/resources/` --> `/auth-service/src/main/resources/`

## Implementation Steps

### 1. Create module directories
```bash
mkdir -p auth-service/src/main/java
mkdir -p auth-service/src/main/resources
mkdir -p auth-service/src/test
mkdir -p jwt-auth-spring-boot-autoconfigure/src/main/java
mkdir -p jwt-auth-spring-boot-starter/src/main/java
```

### 2. Move existing source code
```bash
# Move all source and test code into auth-service
mv src/main/java auth-service/src/main/
mv src/main/resources auth-service/src/main/
mv src/test auth-service/src/
# Remove empty src directory
rmdir src/main src
```

### 3. Convert root pom.xml to parent POM
Replace current `pom.xml` with parent POM:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.3</version>
        <relativePath/>
    </parent>

    <groupId>com.namnd</groupId>
    <artifactId>spring-jwt</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>spring-jwt</name>
    <description>JWT Spring Security Microservice Platform</description>

    <properties>
        <java.version>21</java.version>
        <jjwt.version>0.12.6</jjwt.version>
    </properties>

    <modules>
        <module>auth-service</module>
        <module>jwt-auth-spring-boot-autoconfigure</module>
        <module>jwt-auth-spring-boot-starter</module>
    </modules>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-api</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-impl</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-jackson</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

### 4. Create auth-service/pom.xml
- `<parent>` points to root `com.namnd:spring-jwt`
- `<artifactId>auth-service</artifactId>`
- Copy all `<dependencies>` from original pom.xml (remove JJWT versions, managed by parent)
- Include `spring-boot-maven-plugin` with Lombok exclusion
- Set `<finalName>auth-service</finalName>` in build section

### 5. Create skeleton jwt-auth-spring-boot-autoconfigure/pom.xml
```xml
<project>
    <parent>
        <groupId>com.namnd</groupId>
        <artifactId>spring-jwt</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>jwt-auth-spring-boot-autoconfigure</artifactId>
    <name>JWT Auth Spring Boot Autoconfigure</name>
    <!-- Dependencies added in Phase 3 -->
</project>
```

### 6. Create skeleton jwt-auth-spring-boot-starter/pom.xml
```xml
<project>
    <parent>
        <groupId>com.namnd</groupId>
        <artifactId>spring-jwt</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>jwt-auth-spring-boot-starter</artifactId>
    <name>JWT Auth Spring Boot Starter</name>
    <!-- Dependencies added in Phase 3 -->
</project>
```

### 7. Update Dockerfile
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY auth-service/target/auth-service.jar auth-service.jar
ENTRYPOINT ["java", "-jar", "auth-service.jar"]
```

### 8. Verify build
```bash
mvn clean install
```
All existing tests must pass. auth-service module must produce executable JAR.

## Todo List
- [x] Create module directories
- [x] Move src/ into auth-service/
- [x] Convert root pom.xml to parent (packaging=pom, modules, dependencyManagement)
- [x] Create auth-service/pom.xml with all existing dependencies
- [x] Create skeleton autoconfigure pom.xml
- [x] Create skeleton starter pom.xml
- [x] Update Dockerfile JAR path
- [x] Run `mvn clean install` -- verify all tests pass
- [ ] Run `docker-compose up --build` -- verify container starts (requires running infra)

## Success Criteria
- `mvn clean install` from root succeeds
- All existing unit tests pass in auth-service module
- Application starts and all auth endpoints work (login, register, refresh, logout)
- Docker build succeeds with updated Dockerfile

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| IDE import breaks after restructure | Low | Re-import Maven project in IDE |
| Resource files not found | Medium | Verify application.yml is in auth-service/src/main/resources/ |
| Test resource paths broken | Low | Move test resources alongside test sources |

## Security Considerations
- No security changes in this phase -- purely structural
- Ensure `.gitignore` still covers target directories in submodules

## Next Steps
- Phase 2: Add new auth endpoints (validate-token, userinfo)
- Phase 3: Populate starter library modules
