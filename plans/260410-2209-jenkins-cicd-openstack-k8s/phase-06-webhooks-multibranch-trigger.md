# Phase 06 — Webhooks & Multibranch Pipeline Trigger

## Context Links
- Parent plan: [plan.md](plan.md)
- Research: [research/researcher-02-jenkinsfile-patterns.md](research/researcher-02-jenkinsfile-patterns.md) §8

## Overview
- Date: 2026-04-10
- Priority: P2
- Status: pending
- Review: pending
- Description: Seed a Multibranch Pipeline job via JCasC job-dsl, wire Git provider webhook to Jenkins, and differentiate PR checks (build+test+image) from `main` deployments.

## Key Insights
- Multibranch auto-discovers branches + PRs; one job for all
- Webhook URL: `https://<jenkins>/github-webhook/` (GitHub) or `/project/<job>` (generic)
- Seed job via JCasC `jobs:` block or `job-dsl` plugin — repeatable
- PR vs branch differentiation handled inside Jenkinsfile via `changeRequest()` / `branch 'main'`

## Requirements
**Functional**
- One multibranch pipeline `ms-cinema` pointing at repo
- Triggered by webhook on push and PR open/update
- PR builds: no deploy, no `latest` tag
- `main` builds: full deploy
- Branch discovery excludes nothing (all branches)

**Non-functional**
- Webhook latency < 5s
- Auto-clean stale branches after 7 days

## Architecture
```
[GitHub/GitLab] --webhook--> https://<jenkins>/github-webhook/
                                       |
                                       v
                              Jenkins Multibranch scan
                                       |
                                       v
                             Spawn build for affected branch/PR
```

## Related Code Files
**Modify**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/jenkins/values.yaml` (add JCasC `jobs:` seed)

## Implementation Steps
1. Decide Git provider (Q4). Assume GitHub below.

2. In GitHub repo settings → Webhooks → Add:
   - Payload URL: `https://<jenkins-host>/github-webhook/`
   - Content type: `application/json`
   - Events: `push`, `pull_request`
   - Active: yes

3. Append to `k8s/jenkins/values.yaml` under `controller.JCasC.configScripts`:
   ```yaml
   jobs: |
     jobs:
       - script: >
           multibranchPipelineJob('ms-cinema') {
             branchSources {
               github {
                 id('ms-cinema-gh')
                 scanCredentialsId('git-credentials')
                 repoOwner('<owner>')       // TODO
                 repository('ms-cinema')
                 buildOriginBranch(true)
                 buildOriginPRMerge(true)
                 buildForkPRMerge(false)
               }
             }
             orphanedItemStrategy {
               discardOldItems { daysToKeep(7); numToKeep(20) }
             }
             triggers {
               periodicFolderTrigger { interval('1d') }
             }
             factory {
               workflowBranchProjectFactory { scriptPath('Jenkinsfile') }
             }
           }
   ```

4. Reapply helm:
   ```bash
   helm upgrade jenkins jenkins/jenkins -n jenkins -f k8s/jenkins/values.yaml
   ```

5. Verify job seeded: UI → `ms-cinema` multibranch job visible → Scan Now → branches discovered.

6. Test webhook:
   - Push trivial commit to feature branch → Jenkins builds it
   - Open PR → Jenkins builds PR (no deploy)
   - Merge to `main` → Jenkins builds + deploys

7. Jenkins public URL check — ensure ingress reachable from GitHub IPs (no IP allowlist, or add GitHub hook IPs if restricted).

## Todo List
- [ ] Decide Git provider (Q4)
- [ ] Configure webhook in Git UI
- [ ] Append JCasC `jobs:` seed to values.yaml
- [ ] Helm upgrade
- [ ] Verify multibranch job exists
- [ ] Test push → auto-build
- [ ] Test PR → build-only (no deploy)
- [ ] Test main merge → full deploy

## Success Criteria
- Webhook delivery green in GitHub UI
- Push triggers Jenkins build within 5s
- PR build skips Deploy stage
- `main` build runs Deploy stage
- Stale branches auto-removed after 7 days

## Risk Assessment
| Risk | Impact | Mitigation |
|---|---|---|
| Jenkins not publicly reachable | Webhook fails | VPN / IP allowlist / ngrok dev tunnel |
| Webhook secret missing | Spoofing | Set shared secret in GH webhook + Jenkins plugin |
| Job-dsl syntax error | Job not created | Validate in Jenkins script console before commit |

## Security Considerations
- Add webhook shared secret (GitHub → Jenkins GitHub plugin validates HMAC)
- Scope `git-credentials` PAT to minimum (repo read for private repo)
- NetworkPolicy: allow ingress from `ingress-nginx` namespace only (Phase 07)

## Next Steps
- Phase 07: hardening + documentation
- Depends on: Phases 01-05 functional
