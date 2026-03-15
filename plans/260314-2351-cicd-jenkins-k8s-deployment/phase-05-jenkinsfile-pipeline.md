# Phase 5: Jenkinsfile + Pipeline

## Context Links
- [Parent Plan](./plan.md)
- [Jenkins Research](./research/researcher-01-jenkins-pipeline-patterns.md)
- Dependencies: Phase 1 (manifests), Phase 2 (overlays), Phase 4 (k8s profiles)

## Overview
- **Date:** 2026-03-14
- **Priority:** P1
- **Status:** pending
- **Effort:** 4h
- **Review status:** not started

Create declarative Jenkinsfile at repo root. Pipeline: checkout, build shared libs, parallel build services, parallel Docker build+push, deploy to dev (auto), deploy to prod (manual approval).

## Key Insights
- Maven multi-module: shared libs (`kafka-events`, `jwt-auth-*`) must build first (other modules depend on them)
- After shared libs installed to local Maven repo, all services can build in parallel
- Docker images: use Minikube docker-env for dev (no push needed); Docker Hub for prod
- Kustomize `edit set image` updates image tags per overlay before `kubectl apply -k`
- Jenkins needs: JDK 21, Maven, Docker, kubectl, kustomize CLI
- `failFast: true` in parallel blocks to abort on first failure

## Requirements

### Functional
- Single Jenkinsfile handles both dev and prod deployments
- Shared libs built before service modules
- Service builds run in parallel (6 services + frontend)
- Docker images tagged with `${BUILD_NUMBER}`
- Dev: auto-deploy on push to master
- Prod: manual approval gate before deploy
- Post-build cleanup

### Non-Functional
- Pipeline completes in <15 min for full build
- Failed build stops pipeline (no partial deploys)
- Credentials stored in Jenkins credential store (never in Jenkinsfile)

## Architecture

### Pipeline Stages
```
1. Checkout
2. Build Shared Libs (sequential: kafka-events, jwt-auth-autoconfigure, jwt-auth-starter)
3. Build Services (parallel: auth, movie, booking, payment, notification, api-gateway)
4. Run Tests (parallel: all services)
5. Build Docker Images (parallel: all 8 services + frontend)
6. Deploy to Dev (auto: kubectl apply -k k8s/overlays/dev/)
7. Approval Gate (manual input, master branch only)
8. Deploy to Prod (kubectl apply -k k8s/overlays/prod/)
```

### Jenkins Credentials Required
| Credential ID | Type | Usage |
|---------------|------|-------|
| `kubeconfig-dev` | Secret file | Minikube kubeconfig for dev |
| `kubeconfig-prod` | Secret file | Prod cluster kubeconfig |
| `docker-hub-creds` | Username/Password | Docker Hub push (prod) |

### Image Naming Convention
- Dev: `{service-name}:{BUILD_NUMBER}` (local Minikube registry)
- Prod: `{docker-hub-user}/cinema-{service-name}:{BUILD_NUMBER}`

### Jenkinsfile Structure (Pseudocode)
```groovy
pipeline {
  agent any
  tools { jdk 'jdk-21'; maven 'maven-3.9' }

  environment {
    DOCKER_REGISTRY = 'localhost:5000'
    SERVICES = 'auth-service,movie-service,booking-service,payment-service,notification-service,api-gateway'
  }

  triggers { githubPush() }

  stages {
    stage('Checkout') { steps { checkout scm } }

    stage('Build Shared Libs') {
      steps {
        sh 'mvn clean install -pl kafka-events,jwt-auth-spring-boot-autoconfigure,jwt-auth-spring-boot-starter -DskipTests'
      }
    }

    stage('Build Services') {
      parallel {
        stage('auth-service')         { steps { sh 'mvn package -pl auth-service -DskipTests' } }
        stage('movie-service')        { steps { sh 'mvn package -pl movie-service -DskipTests' } }
        stage('booking-service')      { steps { sh 'mvn package -pl booking-service -DskipTests' } }
        stage('payment-service')      { steps { sh 'mvn package -pl payment-service -DskipTests' } }
        stage('notification-service') { steps { sh 'mvn package -pl notification-service -DskipTests' } }
        stage('api-gateway')          { steps { sh 'mvn package -pl api-gateway -DskipTests' } }
      }
      // eureka-server and config-server skipped (not deployed to K8s)
    }

    stage('Test') {
      parallel {
        // Same modules, mvn test
      }
    }

    stage('Docker Build') {
      parallel {
        // For each service: docker build -t ${DOCKER_REGISTRY}/cinema-{svc}:${BUILD_NUMBER}
        // Frontend: docker build -t ${DOCKER_REGISTRY}/cinema-frontend:${BUILD_NUMBER}
      }
    }

    stage('Deploy Dev') {
      steps {
        withKubeConfig([credentialsId: 'kubeconfig-dev']) {
          // cd k8s/overlays/dev && kustomize edit set image for each service
          sh 'kubectl apply -k k8s/overlays/dev/'
          sh 'kubectl rollout status deployment/auth-service -n cinema-dev --timeout=120s'
          // repeat for other services
        }
      }
    }

    stage('Approval') {
      when { branch 'master' }
      steps { input message: 'Deploy to production?', ok: 'Deploy' }
    }

    stage('Deploy Prod') {
      when { branch 'master' }
      steps {
        withCredentials([usernamePassword(credentialsId: 'docker-hub-creds', ...)]) {
          // Push images to Docker Hub
        }
        withKubeConfig([credentialsId: 'kubeconfig-prod']) {
          // cd k8s/overlays/prod && kustomize edit set image
          sh 'kubectl apply -k k8s/overlays/prod/'
          // rollout status checks
        }
      }
    }
  }

  post {
    always { cleanWs() }
    failure { echo 'Pipeline failed!' /* optional: Slack/email */ }
    success { echo 'Pipeline succeeded!' }
  }
}
```

## Related Code Files

### Files to Create
- `Jenkinsfile` (repo root)

### Files to Modify
- None

### Files to Delete
- None

## Implementation Steps

1. **Create Jenkinsfile** at repo root with declarative pipeline syntax
2. **Agent config**: `agent any` with `tools` block for JDK 21 + Maven 3.9
3. **Environment block**: define DOCKER_REGISTRY, image prefix, service list
4. **Stage: Checkout**: `checkout scm`
5. **Stage: Build Shared Libs**: `mvn clean install -pl kafka-events,jwt-auth-spring-boot-autoconfigure,jwt-auth-spring-boot-starter -DskipTests`
6. **Stage: Build Services (parallel)**:
   - Each service: `mvn package -pl {service} -DskipTests -am` (Note: `-am` not needed since shared libs already installed)
   - 6 parallel stages (auth, movie, booking, payment, notification, gateway)
   - `failFast true`
7. **Stage: Test (parallel)**:
   - Each service: `mvn test -pl {service}`
   - `failFast true`
8. **Stage: Docker Build (parallel)**:
   - Backend services: `docker build -f {service}/Dockerfile -t ${DOCKER_REGISTRY}/cinema-{service}:${BUILD_NUMBER} .` or `{service}/`
   - Note auth-service Dockerfile is at repo root (copies from `auth-service/target/`)
   - Frontend: `docker build -f cinema-frontend/Dockerfile -t ${DOCKER_REGISTRY}/cinema-frontend:${BUILD_NUMBER} cinema-frontend/`
   - Eureka-server, config-server: NOT built (not deployed to K8s)
9. **Stage: Deploy Dev**:
   - `withKubeConfig(credentialsId: 'kubeconfig-dev')`
   - For each service: `cd k8s/overlays/dev && kustomize edit set image cinema-{svc}=${DOCKER_REGISTRY}/cinema-{svc}:${BUILD_NUMBER}`
   - `kubectl apply -k k8s/overlays/dev/`
   - `kubectl rollout status` for each deployment with `--timeout=120s`
10. **Stage: Approval**: `input` step, `when { branch 'master' }`
11. **Stage: Deploy Prod**:
    - Push images to Docker Hub (re-tag and push)
    - `kustomize edit set image` for prod overlay
    - `kubectl apply -k k8s/overlays/prod/`
    - Rollout status checks
12. **Post block**: `cleanWs()`, failure notification

### Docker Build Context Notes
| Service | Dockerfile Location | Build Context | Image Name |
|---------|-------------------|---------------|------------|
| auth-service | `./Dockerfile` (repo root) | `.` | cinema-auth-service |
| movie-service | `./movie-service/Dockerfile` | `./movie-service` | cinema-movie-service |
| booking-service | `./booking-service/Dockerfile` | `./booking-service` | cinema-booking-service |
| payment-service | `./payment-service/Dockerfile` | `./payment-service` | cinema-payment-service |
| notification-service | `./notification-service/Dockerfile` | `./notification-service` | cinema-notification-service |
| api-gateway | `./api-gateway/Dockerfile` | `./api-gateway` | cinema-api-gateway |
| cinema-frontend | `./cinema-frontend/Dockerfile` | `./cinema-frontend` | cinema-frontend |

## Todo List

- [ ] Create Jenkinsfile with pipeline structure
- [ ] Implement Checkout stage
- [ ] Implement Build Shared Libs stage
- [ ] Implement parallel Build Services stage
- [ ] Implement parallel Test stage
- [ ] Implement parallel Docker Build stage
- [ ] Implement Deploy Dev stage with kustomize edit + kubectl apply
- [ ] Implement Approval gate
- [ ] Implement Deploy Prod stage
- [ ] Add post-build cleanup
- [ ] Test pipeline with `jenkins-cli` or manual trigger

## Success Criteria
- Jenkinsfile passes `declarative-linter` validation
- Pipeline builds all 7 deployable modules (6 backend + frontend)
- Docker images tagged with build number
- Dev deploy completes without errors
- Prod deploy blocked behind manual approval
- `kubectl rollout status` confirms successful deployment

## Risk Assessment
- **Maven dependency resolution**: Shared libs must be installed before parallel builds. Handled by sequential stage order.
- **Docker daemon access**: Jenkins agent needs Docker socket access. Solution: mount `/var/run/docker.sock` or use Docker-in-Docker.
- **Minikube docker-env**: Jenkins running outside Minikube won't have access. Solution: use local registry addon instead.
- **Parallel stage executor starvation**: 6 parallel stages need 6 executors. Solution: configure Jenkins with adequate executor count or use Kubernetes plugin for dynamic agents.
- **auth-service Dockerfile at root**: Different build context than other services. Handled in Docker Build stage with explicit `-f` flag.

## Security Considerations
- No credentials in Jenkinsfile (all via Jenkins credential store)
- `withKubeConfig` scopes kubectl access to specific stage
- Docker Hub password via `withCredentials`, not env vars
- Jenkins agents should have minimal K8s RBAC (deploy to cinema-* namespaces only)

## Next Steps
- Phase 6: Jenkins setup instructions + testing
- Future: Extract shared pipeline logic to Jenkins Shared Library for DRY
