# Phase 06: Layout & Polish

## Context Links
- [Angular Patterns Research](./research/researcher-01-angular-patterns.md)
- [Cinema UI Patterns](./research/researcher-02-cinema-ui-patterns.md)
- All previous phases

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** App shell (toolbar, sidenav, footer), user profile, admin pages, global error handling, loading indicators, 404 page.

## Key Insights
- Material Toolbar + Sidenav for responsive navigation
- Sidenav toggles on mobile; static on desktop
- Global loading bar for HTTP requests
- Admin pages: basic CRUD for theaters and showtimes

## Requirements
### Functional
- App toolbar: logo, navigation links, user menu (login/logout/profile)
- Responsive sidenav (mobile burger menu)
- Footer with basic info
- User profile page (view profile from `/api/users/me`)
- Admin dashboard: theater CRUD, showtime CRUD
- 404 Not Found page
- Global loading indicator (top progress bar)

### Non-functional
- Consistent layout across all pages
- Smooth transitions between routes
- Accessible navigation (keyboard, screen reader)

## Architecture
```
shared/components/
├── toolbar/
│   └── toolbar.component.ts
├── sidenav-layout/
│   └── sidenav-layout.component.ts
├── footer/
│   └── footer.component.ts
├── loading-bar/
│   └── loading-bar.component.ts
└── not-found/
    └── not-found.component.ts

features/profile/
└── profile-page/
    └── profile-page.component.ts

features/admin/
├── admin.routes.ts
├── theater-management/
│   └── theater-management.component.ts
└── showtime-management/
    └── showtime-management.component.ts
```

## Related Code Files
- **Create:** `shared/components/toolbar/toolbar.component.ts`
- **Create:** `shared/components/sidenav-layout/sidenav-layout.component.ts`
- **Create:** `shared/components/footer/footer.component.ts`
- **Create:** `shared/components/loading-bar/loading-bar.component.ts`
- **Create:** `shared/components/not-found/not-found.component.ts`
- **Create:** `features/profile/profile-page/profile-page.component.ts`
- **Create:** `features/admin/admin.routes.ts`
- **Create:** `features/admin/theater-management/theater-management.component.ts`
- **Create:** `features/admin/showtime-management/showtime-management.component.ts`
- **Create:** `core/services/loading.service.ts`
- **Create:** `core/interceptors/loading.interceptor.ts`
- **Create:** `core/guards/admin.guard.ts`
- **Modify:** `app.component.ts` — wrap with sidenav layout
- **Modify:** `app.routes.ts` — add profile, admin, 404 routes
- **Modify:** `app.config.ts` — register loading interceptor

## Implementation Steps
1. Create `LoadingService`:
   - `loading = signal<boolean>(false)`
   - `show()` / `hide()` methods
   - Track concurrent requests with counter
2. Create `loadingInterceptor: HttpInterceptorFn`:
   - Call `loadingService.show()` on request start
   - Call `loadingService.hide()` on response/error (finalize)
3. Create `ToolbarComponent`:
   - Material Toolbar with app logo/title
   - Navigation links: Movies, My Bookings, My Payments
   - Right side: Login button (unauthenticated) or user menu (MatMenu: Profile, Logout)
   - Admin link visible only for ROLE_ADMIN
   - `@if (authService.isAuthenticated())` conditional rendering
4. Create `SidenavLayoutComponent`:
   - MatSidenav + MatSidenavContainer
   - Sidenav mode: `over` on mobile, `side` on desktop
   - BreakpointObserver for responsive toggle
   - Content area with `<router-outlet>`
5. Create `FooterComponent`:
   - Simple footer with copyright, links
6. Create `LoadingBarComponent`:
   - MatProgressBar (indeterminate mode)
   - `@if (loadingService.loading())` show/hide
   - Position: fixed top of viewport
7. Update `AppComponent`:
   - Wrap template with SidenavLayoutComponent
   - Include ToolbarComponent and FooterComponent
   - Include LoadingBarComponent
8. Create `ProfilePageComponent`:
   - Fetch `GET /api/users/me`
   - Display: username, email, fullName, roles
   - Material Card layout
9. Create `AdminGuard`:
   - Check user has ROLE_ADMIN
   - Redirect to `/movies` if not admin
10. Create `TheaterManagementComponent`:
    - List theaters (Material Table)
    - Create/edit theater dialog (name, capacity)
    - Calls `GET/POST/PUT /api/theaters`
11. Create `ShowtimeManagementComponent`:
    - List showtimes (Material Table)
    - Create/edit showtime dialog (movieId, theaterId, startTime, price)
    - Calls `GET/POST/PUT /api/showtimes`
12. Create `NotFoundComponent`:
    - 404 page with "Go Home" button
13. Add routes:
    - `profile` → ProfilePageComponent (auth guard)
    - `admin` → lazy load admin routes (admin guard)
    - `**` → NotFoundComponent (wildcard, must be last)
14. Register `loadingInterceptor` in `app.config.ts`

## Todo List
- [ ] LoadingService + loading interceptor
- [ ] ToolbarComponent with nav + user menu
- [ ] SidenavLayoutComponent (responsive)
- [ ] FooterComponent
- [ ] LoadingBarComponent
- [ ] Update AppComponent with layout shell
- [ ] ProfilePageComponent
- [ ] AdminGuard
- [ ] TheaterManagementComponent (CRUD)
- [ ] ShowtimeManagementComponent (CRUD)
- [ ] NotFoundComponent (404)
- [ ] Wire all routes
- [ ] Final responsive testing

## Success Criteria
- App has consistent toolbar/sidenav/footer on all pages
- Navigation works on mobile (burger menu) and desktop (sidenav)
- Loading bar appears during API calls
- Profile page displays user info
- Admin pages accessible only to ROLE_ADMIN
- 404 page catches unknown routes
- No console errors; clean navigation

## Risk Assessment
- Sidenav breakpoint mismatch — test on multiple screen sizes
- Admin guard bypass — server-side auth is primary defense; client guard is UX only

## Security Considerations
- Admin guard is UX-only; backend enforces role-based access
- Profile page only shows current user's data
- Logout clears all tokens and resets auth state

## Next Steps
- Integration testing across all features
- E2E tests with Cypress/Playwright (future phase)
- Production build optimization (tree-shaking, lazy loading verification)
