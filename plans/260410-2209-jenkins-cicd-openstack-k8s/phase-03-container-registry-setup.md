# Phase 03 — Container Registry Setup

## Context Links
- Parent plan: [plan.md](plan.md)
- Research: [research/researcher-01-jenkins-k8s-helm-setup.md](research/researcher-01-jenkins-k8s-helm-setup.md) §7
- Related manifests: `k8s/{svc}/deployment.yml`

## Overview
- Date: 2026-04-10
- Priority: P1
- Status: pending
- Review: pending
- Description: Provision a container registry, create pull/push credentials for Jenkins (Kaniko) and for K8s nodes, then update all `k8s/*/deployment.yml` to reference the registry path with `imagePullPolicy: IfNotPresent`.

## Key Insights
- **Recommended: Docker Hub** — zero infra, free private repos per account, simplest
- GHCR alternative — ties to GitHub, login via PAT with `write:packages`
- Harbor — strongest feature set but requires self-hosting (+1 namespace + PVC + DB)
- Kaniko reads `/kaniko/.docker/config.json` → mount `dockerconfigjson` secret
- Cluster nodes also need imagePullSecrets → add to each deployment's `spec.imagePullSecrets`

## Requirements
**Functional**
- Registry account with 7+ private repos (one per service)
- Secret `registry-credentials` in `jenkins` ns (Kaniko push)
- Secret `registry-credentials` in `ms-cinema` ns (kubelet pull)
- All app deployments reference `<registry>/<svc>:<tag>` images
- `imagePullPolicy: IfNotPresent` (not `Never`)

**Non-functional**
- Credentials never in git
- Rotation documented

## Architecture
```
[Jenkins agent pod]
   └── kaniko --destination=docker.io/namnd/auth-service:tag
         └── uses /kaniko/.docker/config.json (from Secret jenkins/registry-credentials)
                 |
                 v
         [Docker Hub]
                 ^
                 |
[kubelet on K8s node] --pull-- (uses ms-cinema/registry-credentials via imagePullSecrets)
```

## Related Code Files
**Modify (all deployments)**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/auth-service/deployment.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/movie-service/deployment.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/booking-service/deployment.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/payment-service/deployment.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/notification-service/deployment.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/audit-service/deployment.yml`

**Create**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/base/registry-secret.example.yml`

## Implementation Steps
1. Decide registry (Q3). Assume Docker Hub `docker.io/<org>` for steps below.

2. Create 6 private repos in registry UI:
   `<org>/auth-service`, `/movie-service`, `/booking-service`, `/payment-service`, `/notification-service`, `/audit-service`.

3. Generate an access token (not password) with push scope.

4. Create Kaniko push secret in `jenkins` ns:
   ```bash
   kubectl -n jenkins create secret docker-registry registry-credentials \
     --docker-server=https://index.docker.io/v1/ \
     --docker-username='<user>' \
     --docker-password='<token>' \
     --docker-email='<mail>'
   ```

5. Create kubelet pull secret in `ms-cinema` ns (separate, even if same creds):
   ```bash
   kubectl -n ms-cinema create secret docker-registry registry-credentials \
     --docker-server=https://index.docker.io/v1/ \
     --docker-username='<user>' \
     --docker-password='<token>' \
     --docker-email='<mail>'
   ```

6. Create example file `k8s/base/registry-secret.example.yml`:
   ```yaml
   # Create via kubectl create secret docker-registry, NOT apply.
   # Documented here only for reference.
   apiVersion: v1
   kind: Secret
   metadata: { name: registry-credentials, namespace: ms-cinema }
   type: kubernetes.io/dockerconfigjson
   data:
     .dockerconfigjson: BASE64_ENCODED_JSON
   ```

7. Update every `k8s/<svc>/deployment.yml`:
   - Change `image: ms-cinema/<svc>:latest` → `image: docker.io/<org>/<svc>:latest`
   - Change `imagePullPolicy: Never` → `imagePullPolicy: IfNotPresent`
   - Add under `spec.template.spec`:
     ```yaml
     imagePullSecrets:
       - name: registry-credentials
     ```

8. Verify the `registry-credentials` secret in `jenkins` ns matches the volume mount in `ci/agent-pod.yaml` (Phase 02).

9. Test pull manually (after first successful push in Phase 04):
   ```bash
   kubectl -n ms-cinema run probe --rm -it --image=docker.io/<org>/auth-service:latest --restart=Never -- sh
   ```

## Todo List
- [ ] Decide registry (Q3)
- [ ] Create repos
- [ ] Generate token
- [ ] Create `registry-credentials` in `jenkins` ns
- [ ] Create `registry-credentials` in `ms-cinema` ns
- [ ] Update all 6 `k8s/<svc>/deployment.yml` files (image path + pullPolicy + pullSecrets)
- [ ] Create `k8s/base/registry-secret.example.yml`
- [ ] Commit manifest changes (NOT secrets)

## Success Criteria
- Both secrets exist and are `type: kubernetes.io/dockerconfigjson`
- `grep -r "imagePullPolicy: Never" k8s/` returns nothing
- All deployments reference full registry path
- Kaniko smoke push (manual `docker login` + `docker push` test) succeeds

## Risk Assessment
| Risk | Impact | Mitigation |
|---|---|---|
| Rate limit on Docker Hub free tier | Pipeline throttle | Use authenticated pulls; consider Harbor if problematic |
| Token leaked in git | Credential theft | Never commit, use `.example.yml` only |
| Wrong registry path in manifests | ImagePullBackOff | Use sed + grep to verify replacement |
| Existing pods still reference old `ms-cinema/*:latest` | Stale workloads | `kubectl apply -f k8s/` after updating |

## Security Considerations
- Access token (not password); scope = push only
- Separate tokens for CI vs human use
- Private repos only
- Plan quarterly rotation (documented in Phase 07 deployment-guide)

## Next Steps
- Phase 04: parameterize Dockerfile + author Jenkinsfile
- Depends on: Phase 02 (`registry-credentials` referenced by agent pod)
