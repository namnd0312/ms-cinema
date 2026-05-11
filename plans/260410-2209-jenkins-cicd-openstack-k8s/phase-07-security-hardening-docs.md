# Phase 07 — Security Hardening & Documentation

## Context Links
- Parent plan: [plan.md](plan.md)
- Research: [research/researcher-01-jenkins-k8s-helm-setup.md](research/researcher-01-jenkins-k8s-helm-setup.md) §9
- Docs: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docs/`

## Overview
- Date: 2026-04-10
- Priority: P2
- Status: pending
- Review: pending
- Description: Lock down Jenkins (network policy, admin rotation, CSRF/CSP), update project documentation with CI/CD runbook, changelog, roadmap, and deployment guide.

## Key Insights
- Hardening must NOT break the pipeline — test after each change
- NetworkPolicy in `jenkins` ns must still allow agent ↔ controller + egress to registry + git + `ms-cinema` API
- Docs are canonical source for onboarding — team-facing, concise

## Requirements
**Functional**
- Anonymous read disabled (done in Phase 02)
- Signup disabled (done in Phase 02)
- Admin password rotated (not the helm-generated one)
- NetworkPolicy restricts egress
- Script Console restricted to admin (default)
- CI/CD runbook exists in `docs/`

**Non-functional**
- Zero security regressions
- Docs link back to phase files

## Architecture
```
NetworkPolicy (jenkins ns)
  ingress:
    - from ingress-nginx ns (:8080)
    - from within jenkins ns (:50000 agent tunnel)
  egress:
    - to kube-dns (:53)
    - to kube-api (443)
    - to ms-cinema ns (all ports)
    - to 0.0.0.0/0 (443)    # registry, git, maven central
```

## Related Code Files
**Create**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/jenkins/networkpolicy.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docs/cicd-jenkins.md`

**Modify**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docs/deployment-guide.md`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docs/project-changelog.md`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docs/development-roadmap.md`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docs/system-architecture.md`

## Implementation Steps
1. Rotate Jenkins admin password:
   ```bash
   kubectl -n jenkins patch secret jenkins-secrets --type=merge \
     -p '{"stringData":{"ADMIN_PASSWORD":"<new-strong-pw>"}}'
   kubectl -n jenkins rollout restart statefulset/jenkins
   ```
   Store new password in team secret manager (1Password/Vault) — NOT git.

2. Create `k8s/jenkins/networkpolicy.yml`:
   ```yaml
   apiVersion: networking.k8s.io/v1
   kind: NetworkPolicy
   metadata: { name: jenkins-default, namespace: jenkins }
   spec:
     podSelector: {}
     policyTypes: [Ingress, Egress]
     ingress:
       - from:
           - namespaceSelector:
               matchLabels: { kubernetes.io/metadata.name: ingress-nginx }
         ports:
           - { port: 8080, protocol: TCP }
       - from:
           - podSelector: {}
         ports:
           - { port: 50000, protocol: TCP }
     egress:
       - to:
           - namespaceSelector:
               matchLabels: { kubernetes.io/metadata.name: kube-system }
         ports:
           - { port: 53, protocol: UDP }
           - { port: 53, protocol: TCP }
       - to:
           - ipBlock: { cidr: 0.0.0.0/0, except: [169.254.0.0/16] }
         ports:
           - { port: 443, protocol: TCP }
           - { port: 6443, protocol: TCP }
       - to:
           - namespaceSelector:
               matchLabels: { kubernetes.io/metadata.name: ms-cinema }
   ```
   Apply: `kubectl apply -f k8s/jenkins/networkpolicy.yml`

3. Label namespaces so selectors work:
   ```bash
   kubectl label ns ms-cinema      kubernetes.io/metadata.name=ms-cinema --overwrite
   kubectl label ns ingress-nginx  kubernetes.io/metadata.name=ingress-nginx --overwrite
   kubectl label ns kube-system    kubernetes.io/metadata.name=kube-system --overwrite
   ```

4. Run pipeline end-to-end to confirm no hardening regressions.

5. Create `docs/cicd-jenkins.md` (runbook) with sections:
   - Overview (architecture diagram)
   - Access (URL, login, rotating password)
   - Pipeline anatomy (Jenkinsfile stages)
   - How to add a new service
   - How to rollback (`kubectl rollout undo`)
   - Common failures + fixes
   - Credential rotation (registry token, git PAT, admin)
   - Backup / restore JENKINS_HOME PVC (Q5)
   - Link: `plans/260410-2209-jenkins-cicd-openstack-k8s/`

6. Update `docs/deployment-guide.md`:
   - Add "CI/CD Pipeline" section linking to `cicd-jenkins.md`
   - Replace any references to local `imagePullPolicy: Never` flow
   - Note registry prerequisites

7. Update `docs/project-changelog.md`:
   ```
   ## 2026-04-10
   ### Added
   - Self-hosted Jenkins CI/CD on Kubernetes (OpenStack) — plan 260410-2209
   - Kaniko image builds pushed to container registry
   - Multibranch pipeline with PR checks + main deploy
   - NetworkPolicy + JCasC-driven hardening
   ### Changed
   - Root Dockerfile parameterized with ARG SERVICE
   - All deployments now use registry path + imagePullPolicy: IfNotPresent
   ```

8. Update `docs/development-roadmap.md`:
   - Mark "CI/CD pipeline" phase as In Progress → Complete when done

9. Update `docs/system-architecture.md`:
   - Add Jenkins box in architecture diagram
   - Describe build + deploy flow

10. Final review checklist:
    - `grep -r "imagePullPolicy: Never" k8s/` = empty
    - Admin password rotated
    - NetworkPolicy active, pipeline still green
    - All 4 doc files updated

## Todo List
- [ ] Rotate admin password, store in secret manager
- [ ] Create + apply NetworkPolicy
- [ ] Label namespaces
- [ ] Verify pipeline green post-hardening
- [ ] Write `docs/cicd-jenkins.md`
- [ ] Update `docs/deployment-guide.md`
- [ ] Update `docs/project-changelog.md`
- [ ] Update `docs/development-roadmap.md`
- [ ] Update `docs/system-architecture.md`
- [ ] Team walkthrough / demo

## Success Criteria
- All hardening items applied without pipeline breakage
- Docs link consistently to new runbook
- `kubectl -n jenkins get netpol` shows applied policy
- New team member can follow docs to trigger + monitor a build

## Risk Assessment
| Risk | Impact | Mitigation |
|---|---|---|
| NetworkPolicy too strict → agents can't pull Maven | Pipeline broken | Test end-to-end immediately after apply, loosen egress |
| Lost admin password | Lockout | Store in 1Password; recovery via `kubectl exec` + reset script |
| Docs drift | Onboarding friction | Link docs from phase plan; review quarterly |

## Security Considerations
- NetworkPolicy default-deny + explicit allow
- Admin password strong + rotated
- No secrets in git (`.gitignore` enforced)
- Agent pods ephemeral, no hostPath, no privileged
- Registry tokens scoped push-only
- TLS on ingress (Phase 01)

## Next Steps
- Optional follow-ups (out of scope):
  - Add cinema-frontend Angular pipeline (Q6)
  - Integration tests post-deploy
  - GitOps (ArgoCD) migration
  - Velero PVC backups (Q5)
- Depends on: Phases 01-06
