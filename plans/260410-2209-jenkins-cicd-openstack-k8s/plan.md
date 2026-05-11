---
title: "Jenkins CI/CD on OpenStack Kubernetes"
description: "Self-hosted Jenkins on K8s for ms-cinema: build, image (Kaniko), push, deploy, rollout verify"
status: pending
priority: P1
effort: 22h
branch: k8s
tags: [cicd, jenkins, kubernetes, openstack, kaniko, devops]
created: 2026-04-10
---

# Jenkins CI/CD on OpenStack Kubernetes

Self-hosted Jenkins inside the existing ms-cinema K8s cluster (OpenStack/Cinder). Pipeline builds Maven multi-module, bakes images with Kaniko, pushes to registry, deploys via `kubectl set image`, verifies rollout. KISS: no Helm chart for app deploy yet — reuse existing raw manifests in `k8s/`.

## Scope
- In: 6 backend services + 2 shared libs (kafka-events, jwt-auth-autoconfigure)
- Out (first iteration): cinema-frontend (Angular), integration tests, GitOps/ArgoCD

## Phases

| # | File | Effort | Status |
|---|---|---|---|
| 1 | [phase-01-provision-jenkins-on-k8s.md](phase-01-provision-jenkins-on-k8s.md) | 3h | pending |
| 2 | [phase-02-configure-jenkins-jcasc-agents.md](phase-02-configure-jenkins-jcasc-agents.md) | 3h | pending |
| 3 | [phase-03-container-registry-setup.md](phase-03-container-registry-setup.md) | 2h | pending |
| 4 | [phase-04-dockerfile-and-jenkinsfile.md](phase-04-dockerfile-and-jenkinsfile.md) | 4h | pending |
| 5 | [phase-05-deploy-stage-rollout-verify.md](phase-05-deploy-stage-rollout-verify.md) | 3h | pending |
| 6 | [phase-06-webhooks-multibranch-trigger.md](phase-06-webhooks-multibranch-trigger.md) | 2h | pending |
| 7 | [phase-07-security-hardening-docs.md](phase-07-security-hardening-docs.md) | 5h | pending |

## Key Dependencies
- K8s cluster reachable, `kubectl` configured, OpenStack Cinder storage class available
- Ingress-nginx already installed (confirmed on `k8s` branch)
- `ms-cinema` namespace exists with current manifests applied
- Container registry account (credentials out-of-band)
- Git provider webhook reachable from Jenkins public URL

## Research Inputs
- `research/researcher-01-jenkins-k8s-helm-setup.md` — Helm, RBAC, Kaniko, JCasC, Cinder
- `research/researcher-02-jenkinsfile-patterns.md` — Monorepo pipeline, tagging, deploy

## Unresolved Questions
1. OpenStack storage class name? (`csi-cinder` vs `cinder-csi` vs `standard` — run `kubectl get sc`)
2. Jenkins hostname / DNS + TLS source? (cert-manager letsencrypt vs self-signed Secret)
3. Registry choice? (Docker Hub simplest; GHCR if GitHub; Harbor if self-hosted preferred)
4. Git provider? (GitHub / GitLab / Gitea — affects webhook + plugin)
5. Backup policy for JENKINS_HOME PVC? (Velero vs Cinder snapshot vs none)
6. Include Angular frontend in same pipeline now or defer?
7. Automatic rollback on failed rollout — enable `kubectl rollout undo` on failure?
8. Keep `imagePullPolicy: Never` anywhere or switch all to `IfNotPresent`?
