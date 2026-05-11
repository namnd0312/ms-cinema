# Jenkins on Kubernetes (OpenStack Cinder) — Research Report

**Date:** 2026-04-10 | **Focus:** Helm chart v5.x, K8s plugin, Kaniko, RBAC, JCasC

## 1. Helm Chart `jenkins/jenkins`
Current: v5.4+ (2025). Stable, community-maintained.

Key values.yaml knobs:
```yaml
controller:
  image: jenkins/jenkins:2.462-jdk17   # LTS
  installPlugins:
    - kubernetes:latest
    - configuration-as-code:latest
    - workflow-aggregator:latest
    - git:latest
    - credentials-binding:latest
    - blueocean:latest
    - pipeline-utility-steps:latest
  JCasC:
    configScripts:
      basic: |
        jenkins:
          securityRealm:
            local: { allowsSignup: false }
          authorizationStrategy:
            loggedInUsersCanDoAnything: { allowAnonymousRead: false }
persistence:
  enabled: true
  storageClass: csi-cinder
  size: 30Gi
  accessMode: ReadWriteOnce
controller.ingress:
  enabled: true
  hostName: jenkins.example.com
  ingressClassName: nginx
  annotations:
    nginx.ingress.kubernetes.io/proxy-body-size: "0"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "3600"
    cert-manager.io/cluster-issuer: letsencrypt-prod
serviceAccount: { create: true, name: jenkins }
```

## 2. Kubernetes Plugin + Dynamic Agents
Plugin `kubernetes:4000+`. Uses inbound agent protocol (JNLP deprecated). Controller namespace `jenkins`, agent pods ephemeral.

Pod template (maven + kaniko + kubectl):
```yaml
unclassified:
  kubernetes:
    cloudName: kubernetes
    namespace: jenkins
    templates:
      - name: ms-cinema-agent
        containers:
          - { name: maven,   image: 'maven:3.9-eclipse-temurin-21', command: cat, ttyEnabled: true }
          - { name: kaniko,  image: 'gcr.io/kaniko-project/executor:v1.23.2-debug', command: /busybox/cat, ttyEnabled: true }
          - { name: kubectl, image: 'bitnami/kubectl:1.30', command: cat, ttyEnabled: true }
```

## 3. OpenStack Cinder Storage Class
Typical names: `csi-cinder`, `cinder-csi`, `standard`. Verify: `kubectl get sc`. RWO only (no RWX). Size 30Gi for JENKINS_HOME (monorepo + Maven cache). Snapshot/backup via Velero or Cinder snapshots — out of scope.

## 4. Ingress-nginx + TLS
Repo already runs ingress-nginx. Use host-based ingress. Annotations required:
- `proxy-body-size: "0"` (plugin/hpi upload)
- `proxy-read-timeout: "3600"` (long builds stream logs)
- `proxy-request-buffering: "off"`

TLS: cert-manager + letsencrypt if public DNS available, else self-signed Secret + `tls.hosts`. X-Forwarded headers auto-handled by ingress-nginx.

## 5. RBAC — Cross-Namespace Access
```yaml
apiVersion: v1
kind: ServiceAccount
metadata: { name: jenkins, namespace: jenkins }
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata: { name: jenkins-deployer, namespace: ms-cinema }
rules:
  - apiGroups: [apps]
    resources: [deployments, replicasets]
    verbs: [get, list, patch, update]
  - apiGroups: [""]
    resources: [pods, pods/log, services, configmaps]
    verbs: [get, list]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata: { name: jenkins-deployer, namespace: ms-cinema }
roleRef: { apiGroup: rbac.authorization.k8s.io, kind: Role, name: jenkins-deployer }
subjects:
  - { kind: ServiceAccount, name: jenkins, namespace: jenkins }
```
Agent pods run with `serviceAccountName: jenkins` and use in-cluster kubeconfig automatically.

## 6. JCasC Minimal
```yaml
jenkins:
  systemMessage: "ms-cinema CI/CD"
  securityRealm: { local: { allowsSignup: false, users: [{ id: admin, password: ${ADMIN_PASSWORD} }] } }
  authorizationStrategy: { loggedInUsersCanDoAnything: { allowAnonymousRead: false } }
  remotingSecurity: { enabled: true }
credentials:
  system:
    domainCredentials:
      - credentials:
          - usernamePassword: { scope: GLOBAL, id: docker-registry, username: ${DOCKER_USER}, password: ${DOCKER_PASS} }
          - usernamePassword: { scope: GLOBAL, id: git-credentials,  username: ${GIT_USER},    password: ${GIT_TOKEN} }
```
Inject `ADMIN_PASSWORD`, `DOCKER_*`, `GIT_*` via K8s Secret mounted to controller.

## 7. Kaniko for Rootless Image Builds
Recommend Kaniko over DinD (no privileged pod, no daemon). Mount docker config:
```yaml
- name: kaniko
  image: gcr.io/kaniko-project/executor:v1.23.2-debug
  command: [/busybox/cat]
  tty: true
  volumeMounts:
    - { name: docker-config, mountPath: /kaniko/.docker }
volumes:
  - name: docker-config
    secret:
      secretName: registry-credentials
      items: [{ key: .dockerconfigjson, path: config.json }]
```
Create secret: `kubectl create secret docker-registry registry-credentials --docker-server=... --docker-username=... --docker-password=... -n jenkins`.

## 8. Secrets Management
KISS: K8s Secrets + JCasC env substitution. Avoid sealed-secrets (extra operator) unless GitOps flow mandates checked-in secrets. Store registry/git/admin creds as K8s Secret in `jenkins` ns, reference in values.yaml `controller.existingSecret`.

## 9. Hardening Checklist
- `allowsSignup: false`
- CSRF enabled (default)
- `remotingSecurity.enabled: true`
- Script Console: admin-only
- Disable anonymous read
- NetworkPolicy: restrict jenkins ns egress to ms-cinema + registry + git
- Pod Security: restricted
- Rotate admin password post-install

## Summary Decisions
| Concern | Choice | Why |
|---|---|---|
| Storage | `csi-cinder` 30Gi RWO | OpenStack native |
| Agents | Dynamic pods | Scale, isolation |
| Image build | Kaniko | Rootless, no DinD |
| Deploy | SA in-cluster kubeconfig | No external creds |
| Secrets | K8s Secret + JCasC | Simplicity |
| TLS | cert-manager / self-signed | Env-dependent |

## Unresolved Questions
1. Which storage class is actually available on the OpenStack cluster? (`kubectl get sc`)
2. Public DNS + letsencrypt feasible, or internal-only + self-signed?
3. Backup policy for JENKINS_HOME PVC (Velero? Cinder snapshot?)?
4. Registry of choice (Docker Hub / GHCR / Harbor)?
5. Namespace for Jenkins: `jenkins` (new) confirmed?

## Citations
- https://github.com/jenkinsci/helm-charts
- https://plugins.jenkins.io/kubernetes/
- https://www.jenkins.io/doc/book/managing/configuration-as-code/
- https://github.com/GoogleContainerTools/kaniko
- https://docs.openstack.org/cinder/latest/
- https://cert-manager.io/docs/
