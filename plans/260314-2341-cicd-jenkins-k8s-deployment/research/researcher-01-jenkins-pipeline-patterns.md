# Jenkins CI/CD Pipeline Research: Spring Boot + Kubernetes Deployment

**Date:** 2026-03-14 | **Project:** ms-cinema | **Focus:** Jenkins pipeline patterns, Docker build strategies, K8s deployment

---

## 1. Declarative Pipeline Best Practices (Maven Multi-Module)

### Parallel Stages Pattern
```groovy
pipeline {
  agent any

  stages {
    stage('Build & Test') {
      parallel {
        stage('Module A: Build') {
          agent { docker { image 'maven:3.9-openjdk-17' } }
          steps {
            sh 'cd module-a && mvn clean package'
          }
        }
        stage('Module B: Build') {
          agent { docker { image 'maven:3.9-openjdk-17' } }
          steps {
            sh 'cd module-b && mvn clean package'
          }
        }
      }
    }

    stage('Unit Tests') {
      parallel {
        stage('Module A: Test') {
          agent { docker { image 'maven:3.9-openjdk-17' } }
          steps { sh 'cd module-a && mvn test' }
        }
        stage('Module B: Test') {
          agent { docker { image 'maven:3.9-openjdk-17' } }
          steps { sh 'cd module-b && mvn test' }
        }
      }
    }
  }
}
```

### Key Insights
- Use `failFast: true` in parallel blocks to abort all stages on first failure
- Pipeline Maven Integration plugin (`withMaven()`) simplifies multi-module builds
- Each parallel stage can use different Maven profiles/JDK versions
- Avoid executor starvation: match parallel stage count to Jenkins agent capacity

---

## 2. Docker Build Strategies for Minikube

### Strategy 1: docker-env (Fastest for Dev)
```bash
eval $(minikube docker-env)
docker build -t cinema-api:1.0 .
# Image immediately available in Minikube; no registry push needed
```
**Pros:** Fastest iteration, no registry overhead
**Cons:** docker-env state lost on cluster restart; not production-like

### Strategy 2: Local Registry (Recommended)
```groovy
stage('Build & Push Image') {
  steps {
    script {
      sh '''
        minikube addons enable registry
        docker build -t localhost:5000/cinema-api:${BUILD_NUMBER} .
        docker push localhost:5000/cinema-api:${BUILD_NUMBER}
      '''
    }
  }
}
```
**Pros:** Persistent, scalable, production pattern
**Cons:** Extra registry overhead; requires port-forward

### Strategy 3: Docker Hub (Multi-Cluster)
```groovy
stage('Push to Hub') {
  steps {
    script {
      withCredentials([usernamePassword(credentialsId: 'docker-hub-creds', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
        sh '''
          docker login -u $DOCKER_USER -p $DOCKER_PASS
          docker tag cinema-api:${BUILD_NUMBER} $DOCKER_USER/cinema-api:${BUILD_NUMBER}
          docker push $DOCKER_USER/cinema-api:${BUILD_NUMBER}
        '''
      }
    }
  }
}
```

**Recommendation:** Use local registry for Minikube dev; Docker Hub for production K8s clusters.

---

## 3. kubectl apply via Jenkins Pipeline

### Credential Management Pattern
```groovy
stage('Deploy to K8s') {
  steps {
    withKubeConfig([
      credentialsId: 'kubeconfig-minikube',
      serverUrl: 'https://127.0.0.1:8443'
    ]) {
      sh '''
        kubectl apply -f k8s/deployment.yaml
        kubectl set image deployment/cinema-api \
          cinema-api=cinema-api:${BUILD_NUMBER} \
          -n default
        kubectl rollout status deployment/cinema-api
      '''
    }
  }
}
```

### Alternative: Pod ServiceAccount (Jenkins on K8s)
```groovy
stage('Deploy') {
  steps {
    // No credentials needed; uses Pod's ServiceAccount
    sh 'kubectl apply -f k8s/'
  }
}
```

### Kubeconfig Setup
1. Export kubeconfig: `kubectl config view --raw > ~/.kube/minikube`
2. Jenkins: Manage Credentials → Add Secret file → Upload kubeconfig
3. Reference in pipeline: `credentialsId: 'kubeconfig-minikube'`

---

## 4. Essential Jenkins Plugins

| Plugin | Purpose | Pipeline Usage |
|--------|---------|-----------------|
| **Kubernetes CLI** | `withKubeConfig()` | Credential & kubeconfig mgmt |
| **Kubernetes** | Pod templating (containers) | `agent { kubernetes {} }` |
| **Docker Pipeline** | Docker build/push | `docker.build()`, `docker.push()` |
| **Pipeline Utility Steps** | Archive logs, env vars | `readJSON()`, `readYAML()` |
| **Generic Webhook Trigger** | GitHub/GitLab webhooks | Auto-trigger on push |

---

## 5. Webhook + Approval Gate Pattern

### Dev Environment (Auto-Trigger)
```groovy
pipeline {
  triggers {
    githubPush()  // or GenericTrigger with payload validation
  }

  stages {
    stage('Build & Test') { /* ... */ }
    stage('Deploy to Dev') {
      steps { sh 'kubectl apply -f k8s/dev/' }
    }
  }
}
```

### Production Environment (Manual Approval)
```groovy
pipeline {
  triggers {
    githubPush()
  }

  stages {
    stage('Build & Test') { /* ... */ }

    stage('Deploy to Staging') {
      steps { sh 'kubectl apply -f k8s/staging/' }
    }

    stage('Approval for Production') {
      steps {
        input message: 'Deploy to production?', ok: 'Deploy'
      }
    }

    stage('Deploy to Production') {
      steps {
        withKubeConfig([credentialsId: 'kubeconfig-prod']) {
          sh 'kubectl apply -f k8s/prod/'
        }
      }
    }
  }
}
```

---

## 6. Recommended Complete Jenkinsfile Skeleton

```groovy
pipeline {
  agent { kubernetes {} }

  environment {
    IMAGE_REPO = "localhost:5000/cinema-api"
    IMAGE_TAG = "${BUILD_NUMBER}"
  }

  triggers {
    githubPush()
  }

  stages {
    stage('Build Multi-Module') {
      parallel {
        stage('Build API') {
          steps {
            container('maven') {
              sh 'mvn -f api/pom.xml clean package'
            }
          }
        }
        stage('Build Auth') {
          steps {
            container('maven') {
              sh 'mvn -f auth/pom.xml clean package'
            }
          }
        }
      }
    }

    stage('Build Docker Image') {
      steps {
        container('docker') {
          sh '''
            docker build -t ${IMAGE_REPO}:${IMAGE_TAG} .
            docker push ${IMAGE_REPO}:${IMAGE_TAG}
          '''
        }
      }
    }

    stage('Deploy Dev') {
      steps {
        withKubeConfig([credentialsId: 'kubeconfig-minikube']) {
          sh 'kubectl set image deployment/cinema-api cinema-api=${IMAGE_REPO}:${IMAGE_TAG} -n dev'
        }
      }
    }

    stage('Approval for Prod') {
      when { branch 'master' }
      steps {
        input 'Deploy to production?'
      }
    }

    stage('Deploy Production') {
      when { branch 'master' }
      steps {
        withKubeConfig([credentialsId: 'kubeconfig-prod']) {
          sh 'kubectl set image deployment/cinema-api cinema-api=${IMAGE_REPO}:${IMAGE_TAG} -n prod'
        }
      }
    }
  }

  post {
    always {
      cleanWs()
    }
  }
}
```

---

## Summary & Recommendations

| Aspect | Recommendation |
|--------|-----------------|
| **Multi-module builds** | Use parallel stages with Maven containers; separate by module |
| **Docker strategy** | docker-env for Minikube dev; local registry for persistence; Docker Hub for prod |
| **kubectl auth** | Use `withKubeConfig()` with Jenkins credentials store; fallback to Pod ServiceAccount if Jenkins runs on K8s |
| **Plugins** | Kubernetes CLI + Docker Pipeline + Generic Webhook Trigger (essential) |
| **Webhook trigger** | Auto-deploy to dev; manual approval gate for prod |
| **Shared libraries** | Extract common deployment logic to `vars/kubeDeployment.groovy` for DRY |

---

## Sources

- [Jenkins Declarative Pipeline Parallelism](https://www.netdata.cloud/academy/jenkins-declarative-pipeline/)
- [Pipeline Maven Integration Plugin](https://plugins.jenkins.io/pipeline-maven/)
- [Minikube Docker Image Pushing Strategies](https://minikube.sigs.k8s.io/docs/handbook/pushing/)
- [Jenkins Minikube Dockerized Deployment](https://medium.com/@vpkhandare2000/setting-up-jenkins-for-dockerized-minikube-deployment-c28d76e779b2)
- [Kubernetes CLI Plugin](https://plugins.jenkins.io/kubernetes-cli/)
- [Kubernetes Credentials Management](https://github.com/jenkinsci/kubernetes-cli-plugin)
- [Generic Webhook Trigger Plugin](https://plugins.jenkins.io/generic-webhook-trigger/)

