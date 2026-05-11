# Phase 01 — Provision Jenkins on Kubernetes

## Context Links
- Parent plan: [plan.md](plan.md)
- Research: [research/researcher-01-jenkins-k8s-helm-setup.md](research/researcher-01-jenkins-k8s-helm-setup.md)
- Related: `k8s/ingress.yml`, `k8s/infra/`, existing ingress-nginx install

## Overview
- Date: 2026-04-10
- Priority: P1
- Status: pending
- Review: pending
- Description: Install Jenkins controller via official Helm chart in dedicated `jenkins` namespace, backed by Cinder PVC, exposed through ingress-nginx with TLS, wired with a ServiceAccount that has cross-namespace deploy permissions to `ms-cinema`.

## Key Insights
- Helm chart `jenkins/jenkins` v5.4+ is the standard path (simpler than raw YAML)
- Cinder is RWO — fine for single-controller PVC (30Gi)
- Ingress-nginx needs `proxy-body-size: 0` and `proxy-read-timeout: 3600` for plugin uploads / long log streams
- Cross-namespace deploy: SA lives in `jenkins`, Role+RoleBinding in `ms-cinema`
- Persist values.yaml in repo for reproducibility — no Jenkinsfile needed yet

## Requirements
**Functional**
- Jenkins controller reachable at `https://<jenkins-host>`
- JENKINS_HOME persistent on Cinder PVC (30Gi)
- SA `jenkins` in `jenkins` ns can `patch/update deployments` in `ms-cinema` ns
- Agent pods can be scheduled in `jenkins` ns

**Non-functional**
- Single replica controller (HA out of scope)
- Restart tolerant (PVC-backed)
- Upgradable via Helm

## Architecture
```
[Internet] --> ingress-nginx --> Service jenkins --> Pod jenkins-controller
                                                            |
                                                            +-- PVC (csi-cinder, 30Gi)
                                                            +-- SA: jenkins
                                                                  |
                                                                  +-- RoleBinding in ms-cinema ns
```

## Related Code Files
**Create**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/jenkins/values.yaml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/jenkins/namespace.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/jenkins/rbac.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/jenkins/install.sh`

**Modify** — none (no app manifest changes in this phase)

## Implementation Steps
1. Verify cluster prerequisites:
   ```bash
   kubectl get sc
   kubectl get ns ms-cinema
   kubectl -n ingress-nginx get deploy
   ```
   Record storage class name (update Q1).

2. Create namespace manifest `k8s/jenkins/namespace.yml`:
   ```yaml
   apiVersion: v1
   kind: Namespace
   metadata: { name: jenkins }
   ```

3. Create RBAC `k8s/jenkins/rbac.yml` (Role + RoleBinding in `ms-cinema`):
   ```yaml
   apiVersion: rbac.authorization.k8s.io/v1
   kind: Role
   metadata: { name: jenkins-deployer, namespace: ms-cinema }
   rules:
     - apiGroups: ["apps"]
       resources: ["deployments","replicasets"]
       verbs: ["get","list","watch","patch","update"]
     - apiGroups: [""]
       resources: ["pods","pods/log","services","configmaps","events"]
       verbs: ["get","list","watch"]
   ---
   apiVersion: rbac.authorization.k8s.io/v1
   kind: RoleBinding
   metadata: { name: jenkins-deployer, namespace: ms-cinema }
   roleRef: { apiGroup: rbac.authorization.k8s.io, kind: Role, name: jenkins-deployer }
   subjects:
     - { kind: ServiceAccount, name: jenkins, namespace: jenkins }
   ```

4. Create `k8s/jenkins/values.yaml` (JCasC config moved to Phase 02; this file is infra-only):
   ```yaml
   controller:
     image:
       registry: docker.io
       repository: jenkins/jenkins
       tag: 2.462-jdk17
     resources:
       requests: { cpu: "500m", memory: "1Gi" }
       limits:   { cpu: "2",    memory: "3Gi" }
     ingress:
       enabled: true
       hostName: jenkins.example.com        # TODO Q2
       ingressClassName: nginx
       tls:
         - secretName: jenkins-tls
           hosts: [jenkins.example.com]
       annotations:
         nginx.ingress.kubernetes.io/proxy-body-size: "0"
         nginx.ingress.kubernetes.io/proxy-read-timeout: "3600"
         nginx.ingress.kubernetes.io/proxy-request-buffering: "off"
         # cert-manager.io/cluster-issuer: letsencrypt-prod  # enable if Q2=letsencrypt
     servicePort: 8080
     jenkinsUrlProtocol: https
   persistence:
     enabled: true
     storageClass: csi-cinder               # TODO Q1
     size: 30Gi
     accessMode: ReadWriteOnce
   serviceAccount:
     create: true
     name: jenkins
   agent:
     enabled: true
     namespace: jenkins
   ```

5. Install helper `k8s/jenkins/install.sh`:
   ```bash
   #!/usr/bin/env bash
   set -euo pipefail
   kubectl apply -f k8s/jenkins/namespace.yml
   kubectl apply -f k8s/jenkins/rbac.yml
   helm repo add jenkins https://charts.jenkins.io
   helm repo update
   helm upgrade --install jenkins jenkins/jenkins \
     -n jenkins \
     -f k8s/jenkins/values.yaml
   ```
   `chmod +x`.

6. Apply and verify:
   ```bash
   ./k8s/jenkins/install.sh
   kubectl -n jenkins rollout status statefulset/jenkins --timeout=10m
   kubectl -n jenkins get pvc,svc,ingress
   kubectl -n jenkins exec jenkins-0 -c jenkins -- cat /run/secrets/additional/chart-admin-password
   ```

7. Validate cross-ns RBAC:
   ```bash
   kubectl auth can-i patch deploy -n ms-cinema --as=system:serviceaccount:jenkins:jenkins
   ```
   Expect `yes`.

## Todo List
- [ ] Confirm storage class name (Q1)
- [ ] Decide TLS source (Q2)
- [ ] Create `k8s/jenkins/namespace.yml`
- [ ] Create `k8s/jenkins/rbac.yml`
- [ ] Create `k8s/jenkins/values.yaml`
- [ ] Create `k8s/jenkins/install.sh`
- [ ] Run install, verify pod Ready
- [ ] Verify ingress reachable (curl /login)
- [ ] Verify SA can patch deployments in `ms-cinema`

## Success Criteria
- `kubectl -n jenkins get pod jenkins-0` = Running 2/2
- `curl -k https://<host>/login` → 200
- `kubectl auth can-i patch deploy -n ms-cinema --as=...` → yes
- PVC Bound on Cinder
- Admin password captured securely (rotated in Phase 07)

## Risk Assessment
| Risk | Impact | Mitigation |
|---|---|---|
| Wrong storage class → PVC Pending | Blocker | Run `kubectl get sc`, update values |
| Ingress TLS misconfig → site unreachable | High | Start with self-signed, move to cert-manager |
| PVC size undersized | Medium | 30Gi for monorepo + Maven cache (enough) |
| Helm chart breaking change | Low | Pin chart version |

## Security Considerations
- Signup disabled (Phase 02 JCasC)
- TLS mandatory (no plaintext)
- SA scope: only apps+pods read in `ms-cinema`, no secrets access
- PVC encryption: delegated to Cinder backend

## Next Steps
- Phase 02: JCasC + agent pod templates
- Depends on: Jenkins controller Running
