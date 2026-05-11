# Phase 02 — Configure Jenkins via JCasC + Dynamic Agents

## Context Links
- Parent plan: [plan.md](plan.md)
- Research: [research/researcher-01-jenkins-k8s-helm-setup.md](research/researcher-01-jenkins-k8s-helm-setup.md) §2, §6
- Previous: [phase-01-provision-jenkins-on-k8s.md](phase-01-provision-jenkins-on-k8s.md)

## Overview
- Date: 2026-04-10
- Priority: P1
- Status: pending
- Review: pending
- Description: Declaratively configure Jenkins (security, credentials, kubernetes cloud, agent pod template) via Configuration-as-Code plugin. Plugin list codified in values.yaml.

## Key Insights
- JCasC = no click-ops, full reproducibility
- Kubernetes plugin v4000+ uses inbound agent protocol
- Single pod template with maven + kaniko + kubectl containers covers whole pipeline
- Admin password + registry creds injected via K8s Secret → env vars → JCasC `${VAR}` substitution

## Requirements
**Functional**
- Admin user auto-created from secret
- Signup disabled, anonymous read disabled
- Kubernetes cloud `kubernetes` auto-registered
- Pod template `ms-cinema-agent` with maven/kaniko/kubectl containers
- Credentials `docker-registry` and `git-credentials` present

**Non-functional**
- All config checked into git (`k8s/jenkins/values.yaml`)
- Secret values NEVER in git

## Architecture
```
controller (jenkins-0)
  └── JCasC plugin reads ConfigMap from values.yaml
         ├── securityRealm (local, no signup)
         ├── credentials (from env → K8s Secret)
         └── kubernetes cloud
                └── podTemplate ms-cinema-agent
                       ├── maven (maven:3.9-eclipse-temurin-21)
                       ├── kaniko (gcr.io/kaniko-project/executor:debug)
                       └── kubectl (bitnami/kubectl:1.30)
```

## Related Code Files
**Modify**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/jenkins/values.yaml` (append plugins + JCasC)

**Create**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/jenkins/secret.example.yml` (template, NOT applied)
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/ci/agent-pod.yaml` (referenced by Jenkinsfile in Phase 04)

## Implementation Steps
1. Create K8s Secret out-of-band (NOT in git):
   ```bash
   kubectl -n jenkins create secret generic jenkins-secrets \
     --from-literal=ADMIN_PASSWORD='<strong-pw>' \
     --from-literal=DOCKER_USER='<user>' \
     --from-literal=DOCKER_PASS='<token>' \
     --from-literal=GIT_USER='<user>' \
     --from-literal=GIT_TOKEN='<token>'
   ```

2. Create `k8s/jenkins/secret.example.yml` as documentation template:
   ```yaml
   # cp → jenkins-secrets.yml (gitignored) and fill
   apiVersion: v1
   kind: Secret
   metadata: { name: jenkins-secrets, namespace: jenkins }
   type: Opaque
   stringData:
     ADMIN_PASSWORD: "CHANGE_ME"
     DOCKER_USER: "CHANGE_ME"
     DOCKER_PASS: "CHANGE_ME"
     GIT_USER: "CHANGE_ME"
     GIT_TOKEN: "CHANGE_ME"
   ```

3. Add to `.gitignore`:
   ```
   k8s/jenkins/jenkins-secrets.yml
   ```

4. Append to `k8s/jenkins/values.yaml`:
   ```yaml
   controller:
     installPlugins:
       - kubernetes:4253.v7700d91739e5
       - workflow-aggregator:600.vb_57cdd26fdd7
       - git:5.2.2
       - github:1.40.0
       - github-branch-source:1797.v86fdb_4d57d43
       - configuration-as-code:1850.va_a_8c31d3158b_
       - credentials-binding:687.v619cb_15e923f
       - pipeline-utility-steps:2.19.0
       - blueocean:1.27.15
       - job-dsl:1.87
     existingSecret: jenkins-secrets
     additionalExistingSecrets:
       - name: jenkins-secrets
         keyName: ADMIN_PASSWORD
       - name: jenkins-secrets
         keyName: DOCKER_USER
       - name: jenkins-secrets
         keyName: DOCKER_PASS
       - name: jenkins-secrets
         keyName: GIT_USER
       - name: jenkins-secrets
         keyName: GIT_TOKEN
     JCasC:
       defaultConfig: true
       configScripts:
         security: |
           jenkins:
             systemMessage: "ms-cinema CI/CD"
             securityRealm:
               local:
                 allowsSignup: false
                 users:
                   - id: admin
                     password: ${ADMIN_PASSWORD}
             authorizationStrategy:
               loggedInUsersCanDoAnything:
                 allowAnonymousRead: false
             remotingSecurity:
               enabled: true
         credentials: |
           credentials:
             system:
               domainCredentials:
                 - credentials:
                     - usernamePassword:
                         scope: GLOBAL
                         id: docker-registry
                         username: ${DOCKER_USER}
                         password: ${DOCKER_PASS}
                     - usernamePassword:
                         scope: GLOBAL
                         id: git-credentials
                         username: ${GIT_USER}
                         password: ${GIT_TOKEN}
         clouds: |
           jenkins:
             clouds:
               - kubernetes:
                   name: kubernetes
                   serverUrl: https://kubernetes.default
                   namespace: jenkins
                   jenkinsUrl: http://jenkins.jenkins.svc.cluster.local:8080
                   jenkinsTunnel: jenkins-agent.jenkins.svc.cluster.local:50000
                   templates:
                     - name: ms-cinema-agent
                       label: ms-cinema-agent
                       serviceAccount: jenkins
                       yamlMergeStrategy: merge
                       yaml: |
                         apiVersion: v1
                         kind: Pod
                         spec:
                           containers:
                             - name: maven
                               image: maven:3.9-eclipse-temurin-21
                               command: [cat]
                               tty: true
                               resources:
                                 requests: { cpu: "500m", memory: "1Gi" }
                                 limits:   { cpu: "2",    memory: "3Gi" }
                             - name: kaniko
                               image: gcr.io/kaniko-project/executor:v1.23.2-debug
                               command: [/busybox/cat]
                               tty: true
                               volumeMounts:
                                 - name: docker-config
                                   mountPath: /kaniko/.docker
                             - name: kubectl
                               image: bitnami/kubectl:1.30
                               command: [cat]
                               tty: true
                           volumes:
                             - name: docker-config
                               secret:
                                 secretName: registry-credentials
                                 items:
                                   - key: .dockerconfigjson
                                     path: config.json
   ```

5. Also create `ci/agent-pod.yaml` mirror (used by Jenkinsfile `yamlFile` in Phase 04):
   ```yaml
   apiVersion: v1
   kind: Pod
   spec:
     serviceAccountName: jenkins
     containers:
       - name: maven
         image: maven:3.9-eclipse-temurin-21
         command: [cat]
         tty: true
         resources:
           requests: { cpu: "500m", memory: "1Gi" }
           limits:   { cpu: "2",    memory: "3Gi" }
       - name: kaniko
         image: gcr.io/kaniko-project/executor:v1.23.2-debug
         command: [/busybox/cat]
         tty: true
         volumeMounts:
           - name: docker-config
             mountPath: /kaniko/.docker
       - name: kubectl
         image: bitnami/kubectl:1.30
         command: [cat]
         tty: true
     volumes:
       - name: docker-config
         secret:
           secretName: registry-credentials
           items:
             - key: .dockerconfigjson
               path: config.json
   ```

6. Reapply helm:
   ```bash
   helm upgrade jenkins jenkins/jenkins -n jenkins -f k8s/jenkins/values.yaml
   kubectl -n jenkins rollout status statefulset/jenkins
   ```

7. Verify JCasC loaded:
   - UI → Manage Jenkins → Configuration as Code → Status
   - UI → Manage Jenkins → Clouds → `kubernetes` present
   - UI → Credentials → `docker-registry`, `git-credentials` present

8. Smoke test agent pod:
   - Create one-off Pipeline job:
     ```groovy
     pipeline {
       agent { kubernetes { yaml libraryResource('ci/agent-pod.yaml') } }
       stages { stage('ping') { steps {
         container('maven')  { sh 'mvn -v' }
         container('kaniko') { sh '/kaniko/executor version' }
         container('kubectl'){ sh 'kubectl version --client' }
       }}}
     }
     ```

## Todo List
- [ ] Create `jenkins-secrets` in cluster
- [ ] Update `k8s/jenkins/values.yaml` with plugins + JCasC
- [ ] Create `ci/agent-pod.yaml`
- [ ] Create `k8s/jenkins/secret.example.yml`
- [ ] Update `.gitignore`
- [ ] `helm upgrade` and verify rollout
- [ ] Verify JCasC Status = Loaded
- [ ] Smoke test agent pod (all 3 containers respond)

## Success Criteria
- JCasC Status page shows all scripts loaded
- Kubernetes cloud test connection = OK
- Smoke-test pipeline green
- Credentials listed in UI
- Admin login works

## Risk Assessment
| Risk | Impact | Mitigation |
|---|---|---|
| JCasC YAML indent error | Controller fails to start | Validate locally with `yamllint` |
| Plugin version drift | Broken restart | Pin versions, document update process |
| Agent pod stuck Pending | Jobs queue forever | Check ResourceQuota, node capacity |
| `registry-credentials` secret missing | Kaniko push fails | Created in Phase 03 |

## Security Considerations
- Secrets consumed via env vars from K8s Secret, never in values.yaml
- `remotingSecurity: true` prevents classic agent-to-controller exploits
- `agent.disableRemoteRootFs` default Jenkins hardening applies
- Credentials scoped GLOBAL (acceptable single-tenant)

## Next Steps
- Phase 03: create `registry-credentials` secret + registry account
- Depends on: Phase 01 complete
