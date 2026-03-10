# Phase 01: Project Setup

## Context Links
- [Angular Patterns Research](./research/researcher-01-angular-patterns.md)
- [Plan Overview](./plan.md)

## Overview
- **Priority:** P1 (blocking all other phases)
- **Status:** pending
- **Description:** Scaffold Angular 17/18 project with Material, proxy config, routing shell, and shared infrastructure.

## Key Insights
- Standalone by default in Angular 17+; no NgModules
- `provideHttpClient(withInterceptors([...]))` in app.config.ts
- Material 3 theming via CSS custom properties

## Requirements
### Functional
- Angular project in `cinema-frontend/` at repo root
- Angular Material installed with dark/light cinema theme
- Proxy config forwarding `/api/*` to `localhost:8080`
- App-level route structure with lazy loading

### Non-functional
- SCSS styling
- Environment files for API base URL
- Clean folder structure following LIFT principle

## Architecture
```
cinema-frontend/src/app/
├── core/                  # Singletons (services, guards, interceptors)
│   ├── services/
│   ├── guards/
│   ├── interceptors/
│   └── models/           # Shared interfaces/types
├── shared/                # Reusable components, pipes
│   └── components/
├── features/
│   ├── auth/
│   ├── movies/
│   ├── booking/
│   ├── payment/
│   └── profile/
├── app.component.ts
├── app.config.ts
└── app.routes.ts
```

## Related Code Files
- **Create:** `cinema-frontend/` (entire directory)
- **Create:** `cinema-frontend/src/app/app.config.ts`
- **Create:** `cinema-frontend/src/app/app.routes.ts`
- **Create:** `cinema-frontend/src/app/core/models/` (interfaces)
- **Create:** `cinema-frontend/proxy.conf.json`
- **Create:** `cinema-frontend/src/environments/environment.ts`
- **Create:** `cinema-frontend/src/environments/environment.prod.ts`
- **Create:** `cinema-frontend/src/styles.scss` (Material theme)

## Implementation Steps
1. Run `ng new cinema-frontend --standalone --style=scss --routing --skip-tests` from project root
2. `cd cinema-frontend && ng add @angular/material` — choose custom theme, set up animations
3. Create `proxy.conf.json`:
   ```json
   { "/api": { "target": "http://localhost:8080", "secure": false } }
   ```
4. Update `angular.json` to add proxy config to serve options
5. Create environment files with `apiUrl: '/api'`
6. Create folder structure: `core/{services,guards,interceptors,models}`, `shared/components`, `features/{auth,movies,booking,payment,profile}`
7. Define shared TypeScript interfaces in `core/models/`:
   - `user.model.ts` — User, LoginRequest, RegisterRequest, JwtResponse
   - `movie.model.ts` — Movie, Showtime, Theater, Seat
   - `booking.model.ts` — Booking, BookingRequest
   - `payment.model.ts` — Payment, PaymentIntent
8. Set up `app.routes.ts` with lazy-loaded feature routes (empty placeholders)
9. Configure `app.config.ts` with `provideRouter`, `provideHttpClient`, `provideAnimations`
10. Create custom Material theme in `styles.scss` (dark cinema palette: deep indigo primary, amber accent)

## Todo List
- [ ] Scaffold Angular project
- [ ] Install Angular Material
- [ ] Configure proxy
- [ ] Create environment files
- [ ] Set up folder structure
- [ ] Define shared model interfaces
- [ ] Configure app routes (lazy loading)
- [ ] Configure app.config.ts providers
- [ ] Set up Material theme

## Success Criteria
- `ng serve` runs without errors
- Proxy forwards `/api/*` requests to backend gateway
- Empty feature routes resolve correctly
- Material components render with custom theme

## Risk Assessment
- Angular CLI version mismatch — pin to 17.x or 18.x
- Material breaking changes between 17 and 18 — use stable APIs only

## Security Considerations
- No secrets in environment files (proxy handles API routing)
- `.gitignore` excludes `node_modules/`, `.angular/`

## Next Steps
- Phase 02: Auth Module (depends on core models + app.config)
