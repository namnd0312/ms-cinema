# Phase 04 — Consent Screen: Angular Material UI w/ Auto-Approve + Anti-Spoofing Defense

## Context Links

- Plan overview: [plan.md](./plan.md)
- Brainstorm: [../reports/brainstorm-260528-2234-sso-idp-partner-oauth2.md](../reports/brainstorm-260528-2234-sso-idp-partner-oauth2.md) — section 3.6
- Research — consent: [research/researcher-02-jwt-migration-consent-hardening.md](./research/researcher-02-jwt-migration-consent-hardening.md) — section B
- Scout: [scout/scout-01-existing-auth-patterns.md](./scout/scout-01-existing-auth-patterns.md)
- Prereq: Phase 02 complete (consent service stubbed) + Phase 03 complete (client name from DB)
- Related code files:
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/cinema-frontend/src/app/features/auth/` (component style reference)
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/cinema-frontend/src/app/core/services/` (HTTP service pattern)
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/src/main/java/com/namnd/cinema/service/oauth2/JpaOAuth2AuthorizationConsentService.java`
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/src/main/java/com/namnd/cinema/config/oauth2/AuthorizationServerConfig.java` (set `.consentPage("/oauth/consent")`)

## Overview

- Date: 2026-05-28
- Description: Custom consent screen on cinema-frontend (Angular 18 + Material 18 standalone component). Backend `OAuth2ConsentController` provides view model (client display name from DB, redirect host, scope friendly names). Auto-approve path for trusted partners skips UI; decision persisted in `oauth2_authorization_consent` so users not re-prompted on subsequent partner logins.
- Priority: P1
- Implementation status: pending
- Review status: n/a

## Key Insights

- **Anti-spoofing rule**: client display name MUST come from `oauth2_registered_clients.client_name` (DB), NEVER from request params. Otherwise partners could craft `?client_name=Netflix` to phish.
- **Redirect host display** is the second anti-phishing signal — extract host from registered redirect URI (DB), not from request.
- **Auto-approve path is server-side** — when `client.auto_approve=true`, `JpaOAuth2AuthorizationConsentService.findById` synthesizes a "yes" consent on first call so Spring AS never redirects to `/oauth/consent`.
- **Spring AS passes `client_id`, `scope`, `state` to consent page** — `state` MUST be echoed back in submit so Spring AS continues the auth flow correctly.
- **Persistence** uses Phase 02's `oauth2_authorization_consent` table — composite PK `(registered_client_id, principal_name)`.

## Requirements

### Functional

- Backend endpoint `GET /api/oauth/consent` returns view-model JSON:
  ```json
  { "clientId":"...", "clientName":"Acme Partner",
    "redirectHost":"app.acme.com", "scopes":[{"id":"openid","label":"Identity"},...],
    "state":"..." }
  ```
- Backend endpoint `POST /api/oauth/consent` accepts `{authorized: bool, scopes: [...], state: "..."}` and finalizes consent by delegating to Spring AS's consent submission (302 to AS continuation URL).
- Frontend route `/oauth/consent` -> `OauthConsentComponent` (standalone) — Material card w/ partner name (bold), redirect host (mid-tone), scope checkbox list, Allow + Deny buttons.
- Auto-approve: `JpaOAuth2AuthorizationConsentService.findById` checks `client.auto_approve`; if true returns synthesized `OAuth2AuthorizationConsent` w/ all requested scopes, bypassing UI.
- User-granted decisions persist; re-login from same `(client, user)` skips consent UI.

### Non-functional

- Consent screen must not display anything from URL query params except `client_id` (lookup key).
- Scope -> label mapping is server-side (single source of truth).
- Page renders <500ms (single DB lookup + one HTTP roundtrip).
- Backend rejects POST if `state` doesn't match Spring AS's expected state (replay defense — Spring AS handles natively if we redirect submit to its endpoint).

## Architecture

### Flow

```
[Spring AS] /oauth2/authorize -> needs consent ->
   302 /oauth/consent?client_id=X&state=Y&scope=openid+email
   |
   v
[cinema-frontend] /oauth/consent
   - GET /api/oauth/consent?client_id=X&state=Y&scope=openid+email
       backend: load RegisteredClient by id, build view-model
   - User clicks Allow
   - POST to Spring AS native consent endpoint /oauth2/authorize w/ {client_id, state, scope(s), consent=approved}
[Spring AS] persists OAuth2AuthorizationConsent, redirects partner w/ code
```

### Auto-approve branch

```
[Spring AS] -> JpaOAuth2AuthorizationConsentService.findById(clientId, principal)
   if entity present -> return existing consent
   else if client.auto_approve = true:
       lazily save new consent w/ all requested scopes -> return it
   else:
       return null -> Spring AS shows /oauth/consent
```

### Scope friendly name map (server-side, immutable)

```
openid  -> "Identity"
profile -> "Name"
email   -> "Email address"
```

## Related Code Files

### Create (backend)

- `auth-service/src/main/java/com/namnd/cinema/controller/oauth2/OAuth2ConsentController.java`.
- `auth-service/src/main/java/com/namnd/cinema/dto/oauth2/ConsentViewModelResponse.java`.
- `auth-service/src/main/java/com/namnd/cinema/service/oauth2/ConsentScopeLabelService.java` (immutable scope -> label map).

### Modify (backend)

- `auth-service/src/main/java/com/namnd/cinema/service/oauth2/JpaOAuth2AuthorizationConsentService.java` — add auto-approve branch in `findById`.
- `auth-service/src/main/java/com/namnd/cinema/config/oauth2/AuthorizationServerConfig.java` — confirm `.consentPage("/oauth/consent")` set (Phase 02 placeholder).
- `auth-service/src/main/java/com/namnd/cinema/config/security/SecurityConfig.java` — `/api/oauth/consent` and `/oauth/consent` require authenticated user (form login session); permit both for already-logged-in users.

### Create (frontend)

- `cinema-frontend/src/app/features/oauth-consent/oauth-consent.component.ts`.
- `cinema-frontend/src/app/features/oauth-consent/oauth-consent.component.html`.
- `cinema-frontend/src/app/features/oauth-consent/oauth-consent.component.scss`.
- `cinema-frontend/src/app/core/services/oauth-consent.service.ts` — HTTP client for view-model fetch + submit.
- `cinema-frontend/src/app/core/models/oauth-consent.model.ts` — `ConsentViewModel` TS interface.

### Modify (frontend)

- `cinema-frontend/src/app/app.routes.ts` — register `{path:'oauth/consent', loadComponent: () => import('./features/oauth-consent/oauth-consent.component').then(m => m.OauthConsentComponent)}`.

### Delete

- Phase 02 stub `ConsentStubController` if present.

## Implementation Steps

1. **Backend — `ConsentScopeLabelService`**:
   ```java
   @Service public class ConsentScopeLabelService {
     private static final Map<String,String> LABELS = Map.of(
         "openid","Identity","profile","Name","email","Email address");
     public List<ScopeLabel> labelize(Set<String> requested) {
       return requested.stream()
           .map(s -> new ScopeLabel(s, LABELS.getOrDefault(s, s))).toList();
     }
   }
   ```
2. **Backend — `OAuth2ConsentController`**:
   ```java
   @RestController @RequestMapping("/api/oauth")
   public class OAuth2ConsentController {
     @GetMapping("/consent")
     public ConsentViewModelResponse view(@RequestParam("client_id") String clientId,
                                          @RequestParam("state") String state,
                                          @RequestParam("scope") String scope) {
       var client = registeredClientRepo.findByClientIdAndStatus(clientId, ACTIVE)
                    .orElseThrow(() -> new ClientNotFoundException(clientId));
       String host = URI.create(client.getRedirectUris().get(0)).getHost();
       var scopes = scopeLabelService.labelize(Set.of(scope.split(" ")));
       return new ConsentViewModelResponse(clientId, client.getClientName(), host, scopes, state);
     }
   }
   ```
   Note: POST submission is NOT proxied through our controller — frontend posts directly to Spring AS's `/oauth2/authorize` w/ `consent=true` and scope params. This keeps Spring AS in charge of state validation + consent persistence.
3. **Backend — `JpaOAuth2AuthorizationConsentService.findById` auto-approve branch**:
   ```java
   @Override
   public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
     return repo.findById(new ConsentPK(registeredClientId, principalName))
         .map(this::toModel)
         .orElseGet(() -> autoApproveIfTrusted(registeredClientId, principalName));
   }
   private OAuth2AuthorizationConsent autoApproveIfTrusted(String clientId, String principalName) {
     var client = registeredClientRepo.findByClientIdAndStatus(clientId, ACTIVE).orElse(null);
     if (client == null || !client.isAutoApprove()) return null;
     var consent = OAuth2AuthorizationConsent.withId(clientId, principalName)
         .scopes(s -> s.addAll(parseCsv(client.getScopes()))).build();
     save(consent);
     return consent;
   }
   ```
4. **Backend — wire `.consentPage("/oauth/consent")` confirmed** in `AuthorizationServerConfig` (set in Phase 02; verify here). Make `/oauth/consent` (HTML route on frontend) reachable via routing — frontend Angular handles the URL.
5. **Backend — permit consent endpoints** in `SecurityConfig` for authenticated users: `/oauth/consent`, `/api/oauth/consent`. (User MUST be logged in to give consent — Spring AS enforces this naturally.)
6. **Audit events** — emit `oauth2.consent.granted` + `oauth2.consent.denied` via Spring `ApplicationEventPublisher` from `JpaOAuth2AuthorizationConsentService.save` (granted) and `remove` (denied). Bridge to existing audit pipeline by publishing `AuditSpringEvent` from these hooks. (Phase 06 wires fully if not done here.)
7. **Frontend — `oauth-consent.model.ts`**:
   ```ts
   export interface ScopeLabel { id: string; label: string; }
   export interface ConsentViewModel {
     clientId: string; clientName: string; redirectHost: string;
     scopes: ScopeLabel[]; state: string;
   }
   ```
8. **Frontend — `oauth-consent.service.ts`**:
   ```ts
   @Injectable({providedIn:'root'})
   export class OauthConsentService {
     constructor(private http: HttpClient) {}
     getViewModel(p: {client_id:string; state:string; scope:string}) {
       return this.http.get<ConsentViewModel>('/api/oauth/consent', {params: p});
     }
   }
   ```
9. **Frontend — `OauthConsentComponent`** (standalone):
   ```ts
   @Component({
     standalone: true, selector: 'app-oauth-consent',
     imports: [CommonModule, MatCardModule, MatButtonModule, MatCheckboxModule, MatListModule],
     templateUrl: './oauth-consent.component.html',
     styleUrls: ['./oauth-consent.component.scss']
   })
   export class OauthConsentComponent {
     vm = signal<ConsentViewModel | null>(null);
     private route = inject(ActivatedRoute);
     private svc = inject(OauthConsentService);
     constructor() {
       const q = this.route.snapshot.queryParamMap;
       this.svc.getViewModel({
         client_id: q.get('client_id')!, state: q.get('state')!, scope: q.get('scope')!
       }).subscribe(v => this.vm.set(v));
     }
     allow() { this.submit(true); }
     deny()  { this.submit(false); }
     private submit(authorized: boolean) {
       const form = document.createElement('form');
       form.method = 'post'; form.action = '/oauth2/authorize';
       const v = this.vm()!;
       this.append(form, 'client_id', v.clientId);
       this.append(form, 'state', v.state);
       v.scopes.forEach(s => this.append(form, 'scope', s.id));
       if (!authorized) this.append(form, 'consent_action', 'cancel');
       document.body.appendChild(form); form.submit();
     }
     private append(f: HTMLFormElement, k: string, val: string) {
       const i = document.createElement('input'); i.name = k; i.value = val; f.appendChild(i);
     }
   }
   ```
10. **Frontend — template** (`oauth-consent.component.html`):
    ```html
    <mat-card *ngIf="vm() as v" class="consent-card">
      <mat-card-header>
        <mat-card-title><strong>{{ v.clientName }}</strong> wants to access your account</mat-card-title>
        <mat-card-subtitle>Will redirect to: {{ v.redirectHost }}</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        <p>This app will be able to read:</p>
        <mat-list>
          <mat-list-item *ngFor="let s of v.scopes">{{ s.label }}</mat-list-item>
        </mat-list>
      </mat-card-content>
      <mat-card-actions align="end">
        <button mat-button (click)="deny()">Deny</button>
        <button mat-flat-button color="primary" (click)="allow()">Allow</button>
      </mat-card-actions>
    </mat-card>
    ```
11. **Frontend — styling** matches existing Angular Material card patterns under `cinema-frontend/src/app/features/auth/` — check `login.component.scss` for typography + padding tokens.
12. **Frontend — route registration** in `app.routes.ts` w/ `loadComponent` lazy load.
13. **Backend — exception handler** for `ClientNotFoundException` -> 404 w/ neutral error message ("Unknown client"); no leak of whether client_id ever existed beyond admin path.
14. **Tests**:
    - Unit: `ConsentScopeLabelServiceTest` covers known + unknown scopes.
    - Unit: `JpaOAuth2AuthorizationConsentServiceTest` covers auto-approve branch (client found + auto_approve=true; client found + auto_approve=false; client not found).
    - Integration: `OAuth2ConsentControllerTest` (`@WebMvcTest`) returns view-model w/ host extracted from registered URI.
    - Frontend: unit test for `OauthConsentComponent.allow()` builds correct form fields (Jasmine/Karma if project uses; else manual test plan documented).
    - Manual E2E test plan (no Cypress required): document curl/browser steps in `plans/260528-2240-sso-idp-partner-oauth2/manual-test-plan-consent.md`.
15. **Compile + smoke**: `./mvnw -pl auth-service test`; `cd cinema-frontend && npm run build`.
16. **Staging walkthrough**:
    - Register partner via Phase 03 API w/ `autoApprove=false`.
    - Log into cinema as test user.
    - Run auth-code+PKCE flow from a curl/postman partner sim -> redirected to `/oauth/consent` -> see partner name + host -> Allow -> back to partner w/ code.
    - Run again -> NO consent screen (persisted).
    - Repeat w/ `autoApprove=true` client -> NO consent screen ever.

## Todo List

- [ ] Implement `ConsentScopeLabelService`
- [ ] Implement `OAuth2ConsentController.view` returning view-model
- [ ] Add auto-approve branch to `JpaOAuth2AuthorizationConsentService.findById`
- [ ] Emit `oauth2.consent.granted` / `denied` audit events (via `ApplicationEventPublisher`)
- [ ] Confirm `consentPage("/oauth/consent")` set in `AuthorizationServerConfig`
- [ ] Permit `/oauth/consent` + `/api/oauth/consent` for authenticated users in `SecurityConfig`
- [ ] Frontend `ConsentViewModel` model
- [ ] Frontend `OauthConsentService` HTTP client
- [ ] Frontend `OauthConsentComponent` standalone (TS + HTML + SCSS)
- [ ] Register `/oauth/consent` route in `app.routes.ts`
- [ ] Add `ClientNotFoundException` -> 404 handler
- [ ] Backend unit + integration tests
- [ ] Document manual E2E test plan
- [ ] Staging walkthrough w/ auto_approve=false and =true clients

## Success Criteria

- User sees partner display name from DB, never from request params.
- Redirect host shown prominently on consent screen.
- "Allow" persists `oauth2_authorization_consent` row; subsequent login from same client skips UI.
- "Deny" returns user to partner w/ `error=access_denied` (Spring AS native behavior).
- `auto_approve=true` clients never trigger consent UI (verified by network trace).
- `oauth2.consent.granted` / `denied` audit events emitted to Kafka.
- Frontend build passes; component renders Material card matching existing style.

## Risk Assessment

| Risk | Severity | Mitigation |
|---|---|---|
| Partner-name spoof via crafted query param | HIGH | Always load `clientName` from DB; integration test asserts query-param `clientName` is ignored |
| Consent form CSRF (Spring AS native POST) | MED | Spring AS's session-bound state + Spring Security CSRF token (frontend includes via interceptor) |
| Persistent consent never expires (over-trust) | LOW | Out of scope; document as known limitation; admin can purge via DB if needed |
| Auto-approve mis-set on untrusted client | HIGH | Admin endpoint default `autoApprove=false`; require explicit `true` in request; audit on update |
| Frontend route bypasses auth (no login session) | MED | Spring AS redirects unauth users to `/login` before consent; route guard on Angular ensures `/oauth/consent` requires session too |

## Security Considerations

- DB-only display name (anti-phishing).
- Redirect host displayed to user (anti-phishing).
- No scope additions allowed beyond what Spring AS requested (server-side intersects submitted scope against AS-requested set).
- `oauth2_authorization_consent` table never exposes principal_name via API (only used by Spring AS internals).
- Audit `denied` events captured to detect partner enumeration / pressure tactics.
- CSRF: rely on Spring Security's existing CSRF token on form-encoded POST (frontend interceptor adds `X-XSRF-TOKEN`).
- Defense in depth: server-side check that `client_id` in submitted form matches session-stored authorization request (Spring AS native).

## Next Steps

- Phase 05 (cutover) runs independent of this phase; consent UI ready means partners can start integration tests against staging.
- Phase 06 (hardening) re-tests anti-spoofing via security review checklist.
