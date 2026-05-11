# Phase 05 — Deploy Stage, Rollout & Health Verify

## Context Links
- Parent plan: [plan.md](plan.md)
- Research: [research/researcher-02-jenkinsfile-patterns.md](research/researcher-02-jenkinsfile-patterns.md) §5-6
- Previous: [phase-04-dockerfile-and-jenkinsfile.md](phase-04-dockerfile-and-jenkinsfile.md)

## Overview
- Date: 2026-04-10
- Priority: P1
- Status: pending
- Review: pending
- Description: Extend Jenkinsfile with a `Deploy` stage (branch=main only) that patches each deployment image via `kubectl set image`, waits for rollout completion, and smoke-tests actuator health via ingress.

## Key Insights
- `kubectl set image` = minimal, atomic, no YAML mutation, no Helm migration needed
- Rollout status timeout 5m per service; fail pipeline on non-zero
- Actuator health reachable through ingress (path `/<service>/actuator/health` — verify against `k8s/ingress.yml`)
- Optional rollback via `kubectl rollout undo` on failure (Q7)

## Requirements
**Functional**
- Deploy only on `main` branch
- Sequential per-service: `set image` → `rollout status` → `health check`
- Pipeline fails fast on any rollout error or non-200 health
- Optional auto-rollback toggle

**Non-functional**
- Deploy phase < 10 min total
- Idempotent re-runs safe

## Architecture
```
Jenkinsfile (Deploy stage, container: kubectl)
  for svc in SERVICES:
     kubectl set image deploy/<svc> <svc>=<REGISTRY>/<svc>:<TAG> -n ms-cinema
     kubectl rollout status deploy/<svc> -n ms-cinema --timeout=5m
     curl -fsS https://<ingress>/<svc>/actuator/health   # smoke
```

## Related Code Files
**Modify**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/Jenkinsfile`

**Reference (no modify)**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/ingress.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/*/deployment.yml`

## Implementation Steps
1. Confirm ingress path per service by inspecting `k8s/ingress.yml`. Record the actual host+path mapping (e.g. `/api/auth/...`).

2. Add Deploy stage to `Jenkinsfile` (insert before final `post`):
   ```groovy
   stage('Deploy') {
     when { branch 'main' }
     environment {
       INGRESS_HOST = 'api.ms-cinema.example.com'   // TODO from k8s/ingress.yml
     }
     steps {
       container('kubectl') {
         script {
           def failed = []
           SERVICES.each { svc ->
             try {
               sh "kubectl set image deployment/${svc} ${svc}=${REGISTRY}/${svc}:${IMAGE_TAG} -n ${NAMESPACE} --record"
               sh "kubectl rollout status deployment/${svc} -n ${NAMESPACE} --timeout=5m"
             } catch (err) {
               failed << svc
               echo "Rollout failed for ${svc}: ${err}"
             }
           }
           if (failed) {
             if (params.AUTO_ROLLBACK == 'true') {
               failed.each { svc ->
                 sh "kubectl rollout undo deployment/${svc} -n ${NAMESPACE}"
                 sh "kubectl rollout status deployment/${svc} -n ${NAMESPACE} --timeout=5m"
               }
             }
             error "Deployment failed: ${failed.join(', ')}"
           }
         }
       }
     }
   }
   stage('Smoke Health') {
     when { branch 'main' }
     steps {
       container('kubectl') {
         script {
           SERVICES.each { svc ->
             // Actuator exposed inside cluster — use service DNS to avoid ingress auth
             sh "kubectl run curl-${svc}-${env.BUILD_NUMBER} --rm -i --restart=Never --image=curlimages/curl:8.8.0 -n ${NAMESPACE} -- -fsS http://${svc}:8080/actuator/health"
           }
         }
       }
     }
   }
   ```

3. Add pipeline parameter for rollback toggle (top of Jenkinsfile):
   ```groovy
   parameters {
     booleanParam(name: 'AUTO_ROLLBACK', defaultValue: false, description: 'Rollout undo on failed rollout')
   }
   ```

4. (Optional) Replace in-cluster `kubectl run curl` with a sidecar container in agent-pod.yaml if curl image pulls become annoying — YAGNI for now.

5. Verify service ports in each `k8s/<svc>/deployment.yml` match the smoke test port (8080 default).

6. Trigger pipeline on a test commit to `main` and observe Deploy + Smoke stages.

7. Verify rollout recorded:
   ```bash
   kubectl -n ms-cinema rollout history deployment/auth-service
   kubectl -n ms-cinema get pod -l app=auth-service -o wide
   ```

## Todo List
- [ ] Confirm ingress host/paths from `k8s/ingress.yml`
- [ ] Extend Jenkinsfile with Deploy + Smoke stages
- [ ] Add `AUTO_ROLLBACK` param
- [ ] Trigger test deploy on `main`
- [ ] Verify rollout history
- [ ] Verify health endpoints return 200
- [ ] Decide Q7 (auto-rollback default)

## Success Criteria
- Test deploy updates all 6 deployments to new tag
- `kubectl rollout history` shows CI-driven revision
- All 6 actuator `/health` return 200 `{status:UP}`
- Pipeline aborts cleanly if one service fails to start

## Risk Assessment
| Risk | Impact | Mitigation |
|---|---|---|
| Slow startup (DB migrations) exceeds 5m | False failures | Bump `--timeout=10m` or add readiness probes |
| Actuator not exposed on service port | Smoke false negative | Confirm Spring `management.server.port` |
| Partial deploy (3 of 6) on failure | Inconsistent state | Optional auto-rollback OR alert + manual fix |
| `kubectl set image` wrong container name | No change applied | Container name MUST match service name in deployment |

## Security Considerations
- SA `jenkins` has least-privilege (no secrets read)
- Smoke uses internal ClusterIP → bypasses ingress auth; fine for health only
- `--record` annotates revision with kubectl command (no secrets)

## Next Steps
- Phase 06: webhooks + multibranch trigger
- Depends on: Phase 04
