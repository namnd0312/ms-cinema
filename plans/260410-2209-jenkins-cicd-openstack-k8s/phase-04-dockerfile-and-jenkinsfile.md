# Phase 04 — Parameterized Dockerfile & Jenkinsfile

## Context Links
- Parent plan: [plan.md](plan.md)
- Research: [research/researcher-02-jenkinsfile-patterns.md](research/researcher-02-jenkinsfile-patterns.md)
- Dockerfile: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/Dockerfile`

## Overview
- Date: 2026-04-10
- Priority: P1
- Status: pending
- Review: pending
- Description: Parameterize root Dockerfile with `ARG SERVICE` so one file builds all 6 services. Author a single shared declarative `Jenkinsfile` at repo root: shared libs → parallel service builds + tests → parallel Kaniko image builds/pushes.

## Key Insights
- Monorepo → one Jenkinsfile, not six (avoid duplication)
- Shared libs (`kafka-events`, `jwt-auth-autoconfigure`) MUST build+install first
- Use workspace-local `.m2` cache — Cinder has no RWX for shared cache volume
- Kaniko `--cache=true` handles base layer caching independently
- Change detection: simple `git diff` based, shared-lib change → rebuild all

## Requirements
**Functional**
- One Dockerfile builds any service via `--build-arg SERVICE=<name>`
- Jenkinsfile stages: Checkout → SharedLibs → Build+Test → Image → (Deploy in Phase 05)
- Parallel service builds where independent
- Unit tests run as part of `mvn install`
- Image tag = `${GIT_SHORT}-${BUILD_NUMBER}`; also `latest` on `main`
- PR builds: build + test + image build, no push of `latest`, no deploy

**Non-functional**
- Pipeline under 15 min for full build
- Idempotent, re-runnable

## Architecture
```
Jenkinsfile (declarative)
 ├── agent: kubernetes pod (ci/agent-pod.yaml)
 ├── stage Checkout
 ├── stage Shared Libs  (maven: mvn install kafka-events, jwt-auth-autoconfigure)
 ├── stage Build+Test   (maven: parallel per service, mvn -pl <svc> -am install)
 ├── stage Image        (kaniko: parallel per service, /kaniko/executor --build-arg SERVICE=)
 └── stage Deploy       (Phase 05, branch=main only)
```

## Related Code Files
**Modify**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/Dockerfile`

**Create**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/Jenkinsfile`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/ci/agent-pod.yaml` (from Phase 02 if not yet created)

## Implementation Steps
1. Rewrite `Dockerfile` at repo root:
   ```dockerfile
   # syntax=docker/dockerfile:1.6
   FROM eclipse-temurin:21-jre-alpine
   ARG SERVICE
   ENV SERVICE=${SERVICE}
   WORKDIR /opt/app
   COPY ${SERVICE}/target/${SERVICE}.jar app.jar
   EXPOSE 8080
   ENTRYPOINT ["sh","-c","java -jar /opt/app/app.jar"]
   ```
   Note: jar name must match Maven `<finalName>` — verify in each service `pom.xml` (add `<finalName>${project.artifactId}</finalName>` if missing).

2. Verify each service pom has `<finalName>`:
   ```bash
   grep -l finalName auth-service/pom.xml movie-service/pom.xml ...
   ```
   Add where missing under `<build>`.

3. Create `Jenkinsfile`:
   ```groovy
   def SERVICES = ['auth-service','movie-service','booking-service','payment-service','notification-service','audit-service']
   def REGISTRY = 'docker.io/<org>'     // TODO replace
   def NAMESPACE = 'ms-cinema'

   pipeline {
     agent {
       kubernetes {
         yamlFile 'ci/agent-pod.yaml'
         defaultContainer 'maven'
       }
     }
     options {
       timeout(time: 45, unit: 'MINUTES')
       buildDiscarder(logRotator(numToKeepStr: '30'))
       disableConcurrentBuilds()
     }
     environment {
       MAVEN_OPTS = '-Dmaven.repo.local=${WORKSPACE}/.m2 -Dstyle.color=always'
       GIT_SHORT  = "${env.GIT_COMMIT?.take(7)}"
       IMAGE_TAG  = "${env.GIT_COMMIT?.take(7)}-${env.BUILD_NUMBER}"
     }
     stages {
       stage('Checkout Info') {
         steps {
           sh 'git log -1 --oneline'
           sh 'mvn -v'
         }
       }
       stage('Shared Libs') {
         steps {
           sh 'mvn -B -ntp -pl kafka-events,jwt-auth-autoconfigure -am install -DskipTests'
         }
       }
       stage('Build + Test Services') {
         steps {
           script {
             def builds = [:]
             SERVICES.each { svc ->
               builds[svc] = {
                 stage("build ${svc}") {
                   sh "mvn -B -ntp -pl ${svc} -am install"
                 }
               }
             }
             parallel builds
           }
         }
         post {
           always {
             junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
           }
         }
       }
       stage('Image Build + Push') {
         when { anyOf { branch 'main'; branch 'develop'; changeRequest() } }
         steps {
           container('kaniko') {
             script {
               def isMain = env.BRANCH_NAME == 'main'
               SERVICES.each { svc ->
                 def dest1 = "--destination=${REGISTRY}/${svc}:${IMAGE_TAG}"
                 def dest2 = isMain ? "--destination=${REGISTRY}/${svc}:latest" : ''
                 sh """
                   /kaniko/executor \
                     --dockerfile=Dockerfile \
                     --context=dir://\$WORKSPACE \
                     --build-arg SERVICE=${svc} \
                     ${dest1} ${dest2} \
                     --cache=true --cache-ttl=24h
                 """
               }
             }
           }
         }
       }
       // Deploy stage in Phase 05
     }
     post {
       success { echo "Built tag ${env.IMAGE_TAG}" }
       failure { echo 'Pipeline failed' }
       always  { cleanWs(deleteDirs: true, notFailBuild: true) }
     }
   }
   ```

4. Commit `ci/agent-pod.yaml` (from Phase 02 body).

5. Smoke test locally (optional):
   ```bash
   docker build --build-arg SERVICE=auth-service -t auth-service:dev .
   docker run --rm auth-service:dev
   ```
   (Run `mvn -pl auth-service -am install` first.)

6. Trigger pipeline once in Jenkins (manual) to validate stages up to Image.

## Todo List
- [ ] Rewrite `Dockerfile` with `ARG SERVICE`
- [ ] Verify / add `<finalName>` in each service pom
- [ ] Create `Jenkinsfile`
- [ ] Confirm `ci/agent-pod.yaml` committed
- [ ] Replace `REGISTRY` placeholder
- [ ] Manual trigger — confirm build green
- [ ] Confirm images appear in registry

## Success Criteria
- `docker build --build-arg SERVICE=movie-service .` succeeds locally
- Pipeline run produces 6 image tags in registry
- Unit tests reported in Jenkins UI (JUnit)
- Total build < 15 min (with cache)

## Risk Assessment
| Risk | Impact | Mitigation |
|---|---|---|
| `finalName` mismatch breaks COPY | Image build fails | Enforce `<finalName>${project.artifactId}</finalName>` |
| Parallel `mvn install` races m2 cache | Flaky builds | Workspace-local `.m2` per agent pod |
| Kaniko OOM on 6 parallel builds | Agent crash | Keep sequential per agent (current design) |
| Long first build (no cache) | Slow feedback | Accept for first run; Kaniko cache warms subsequent |

## Security Considerations
- No secrets in Jenkinsfile (only credential IDs from Phase 02)
- Kaniko runs rootless (no privileged pod, no host Docker socket)
- Build context scoped to workspace (no parent dir access)

## Next Steps
- Phase 05: add Deploy stage with `kubectl set image` + rollout verify
- Depends on: Phases 02, 03
