# Jenkinsfile Patterns — Spring Boot Maven Monorepo → K8s

**Date:** 2026-04-10 | **Repo:** ms-cinema (6 services + 2 shared libs + Angular)

## Recommendation (TL;DR)
**Single shared declarative Jenkinsfile** at repo root, parameterized by service, with path-based change detection via `git diff --name-only`. Rationale: 6 services share root POM, shared libs (kafka-events, jwt-auth-autoconfigure) must build first, same Dockerfile pattern. Per-service Jenkinsfiles duplicate logic. Monorepo = single dependency graph → single pipeline.

## 1. Strategy Comparison
| | Shared Jenkinsfile (pick) | Per-service Multibranch |
|---|---|---|
| Maintenance | 1 file | 6 files |
| Shared lib order | Enforced centrally | Manual coordination |
| Parallel builds | Native `parallel {}` | Separate jobs |
| PR checks | One scan | Six scans |
| Independent release | Harder | Easier |

## 2. Path-Based Change Detection
Declarative `when { changeset }` is limited. Use Groovy + shell:
```groovy
def changed(svc) {
  return sh(returnStatus: true,
    script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT:-HEAD~1} HEAD | grep -qE '^(${svc}|kafka-events|jwt-auth-autoconfigure|pom.xml)'"
  ) == 0
}
```
Shared libs change → rebuild all services.

## 3. Maven Cache
Use agent pod workspace volume + `-Dmaven.repo.local=${WORKSPACE}/.m2`. Avoid RWX PVC (Cinder doesn't support natively). Kaniko caches base layers independently via `--cache=true`.

## 4. Image Tag Strategy
```
feature/*, PR: <svc>:${GIT_SHORT}-${BUILD_NUMBER}
main:          <svc>:${GIT_SHORT}-${BUILD_NUMBER} AND <svc>:latest
git tag v*:    <svc>:${TAG_NAME}
```
`GIT_SHORT = env.GIT_COMMIT.take(7)`. Avoid mutable-only `latest` in prod.

## 5. Deploy Command
**Pick: `kubectl set image`** — minimal, atomic, no YAML mutation.
```bash
kubectl set image deployment/auth-service \
  auth-service=$REGISTRY/auth-service:$TAG -n ms-cinema
```
Alternatives rejected: sed+apply (fragile), Kustomize (overkill for now), Helm (migration cost).

**Note:** existing `imagePullPolicy: Never` must be changed to `IfNotPresent` once a real registry is used.

## 6. Rollout Verification
```bash
kubectl rollout status deployment/$SVC -n ms-cinema --timeout=5m
curl -fsS http://<ingress-host>/api/<svc>/actuator/health
```
Fail pipeline on non-200. Optional: automatic rollback via `kubectl rollout undo`.

## 7. PR vs Main
- PR: checkout → shared libs → build changed services → unit tests → image build (no push). Skip deploy.
- main: + push images + deploy + rollout verify.

## 8. Webhooks
GitHub: native "GitHub plugin" + webhook on push/PR → `<jenkins>/github-webhook/`. GitLab: "GitLab plugin". Fallback: "Generic Webhook Trigger". Multibranch pipeline scan on webhook event.

## 9. Example Shared Jenkinsfile (≈80 lines)
```groovy
@Library('none') _
def SERVICES = ['auth-service','movie-service','booking-service','payment-service','notification-service','audit-service']
def REGISTRY = 'docker.io/namnd'  // parameterize
def NAMESPACE = 'ms-cinema'

pipeline {
  agent {
    kubernetes {
      yamlFile 'ci/agent-pod.yaml'   // maven + kaniko + kubectl
      defaultContainer 'maven'
    }
  }
  environment {
    MAVEN_OPTS = '-Dmaven.repo.local=${WORKSPACE}/.m2'
    GIT_SHORT  = "${env.GIT_COMMIT?.take(7)}"
    IMAGE_TAG  = "${env.GIT_SHORT}-${env.BUILD_NUMBER}"
  }
  stages {
    stage('Shared libs') {
      steps {
        sh 'mvn -B -pl kafka-events,jwt-auth-autoconfigure -am install -DskipTests $MAVEN_OPTS'
      }
    }
    stage('Build + test services') {
      steps {
        script {
          def builds = [:]
          SERVICES.each { svc ->
            builds[svc] = {
              stage("build ${svc}") {
                sh "mvn -B -pl ${svc} -am install ${MAVEN_OPTS}"
              }
            }
          }
          parallel builds
        }
      }
    }
    stage('Image build + push') {
      when { anyOf { branch 'main'; branch 'develop'; changeRequest() } }
      steps {
        container('kaniko') {
          script {
            SERVICES.each { svc ->
              sh """
                /kaniko/executor --dockerfile=Dockerfile --context=dir://\$WORKSPACE \
                  --build-arg SERVICE=${svc} \
                  --destination=${REGISTRY}/${svc}:${IMAGE_TAG} \
                  ${env.BRANCH_NAME == 'main' ? "--destination=${REGISTRY}/${svc}:latest" : ''} \
                  --cache=true
              """
            }
          }
        }
      }
    }
    stage('Deploy') {
      when { branch 'main' }
      steps {
        container('kubectl') {
          script {
            SERVICES.each { svc ->
              sh "kubectl set image deployment/${svc} ${svc}=${REGISTRY}/${svc}:${IMAGE_TAG} -n ${NAMESPACE}"
              sh "kubectl rollout status deployment/${svc} -n ${NAMESPACE} --timeout=5m"
            }
          }
        }
      }
    }
  }
  post {
    failure { echo 'Pipeline failed' }
  }
}
```

## 10. Dockerfile Note
Current root Dockerfile hardcodes `auth-service`. Must parameterize:
```dockerfile
ARG SERVICE
FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY ${SERVICE}/target/${SERVICE}.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
```
Kaniko passes `--build-arg SERVICE=<svc>`.

## Unresolved Questions
1. `imagePullPolicy: Never` — keep or switch to `IfNotPresent` once registry configured?
2. Target registry (Docker Hub vs GHCR vs Harbor)?
3. Git provider for webhooks?
4. Automatic rollback on failed rollout — enable?
5. Frontend (Angular) — include in same pipeline or separate?
6. Integration tests — run post-deploy via Testcontainers or skip for now?

## Citations
- https://www.jenkins.io/doc/book/pipeline/syntax/
- https://plugins.jenkins.io/kubernetes/
- https://github.com/GoogleContainerTools/kaniko
- https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#updating-a-deployment
