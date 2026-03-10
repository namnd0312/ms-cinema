# Angular 17/18 Cinema Frontend: Patterns & Setup Guide

**Date:** 2026-03-07 | **Research Scope:** Standalone components, control flow, Material theming, JWT interceptors, routing

---

## 1. Standalone Components & Control Flow

**Key Shift:** Angular 17+ generates standalone components by default. No NgModule imports required.

```typescript
// app.routes.ts - Define routes directly
export const appRoutes: Routes = [
  { path: 'movies', loadComponent: () => import('./features/movies/movies.component').then(m => m.MoviesComponent) },
  { path: 'booking', canActivate: [authGuard], loadComponent: () => import('./features/booking/booking.component').then(m => m.BookingComponent) }
];
```

**Control Flow Syntax** - Replaces *ngIf, *ngFor, *ngSwitch:
- `@if (condition)` - Conditional rendering
- `@for (item of items; track item.id)` - Loop with mandatory `track` (90% faster than *ngFor)
- `@switch (value)` / `@case (match)` / `@default` - Type-safe branching

**Signals** - Reactive state without heavy RxJS:
```typescript
import { signal, computed } from '@angular/core';

export class MovieComponent {
  movies = signal<Movie[]>([]);
  selectedGenre = signal<string>('all');

  filteredMovies = computed(() =>
    this.movies().filter(m =>
      this.selectedGenre() === 'all' || m.genre === this.selectedGenre()
    )
  );
}
```

---

## 2. Angular Material 17/18 Theming

**Setup (Standalone):**
```typescript
// main.ts
import { provideAnimations } from '@angular/platform-browser/animations';
import '@angular/material/prebuilt-themes/indigo-pink.css';

bootstrapApplication(AppComponent, {
  providers: [
    provideAnimations(),
    // other providers
  ]
});
```

**Material 3 Support** (Angular 18): Use CSS custom properties (--mat-sys-*) for design tokens. Define custom theme:
```scss
// theme.scss
@use '@angular/material' as mat;

$custom-theme: mat.define-theme((
  color: (
    primary: mat.$indigo-palette,
    tertiary: mat.$pink-palette,
  )
));

html { @include mat.all-component-colors($custom-theme); }
```

**Component Styling:** Import Material styles directly in component:
```typescript
@Component({
  selector: 'app-movie-card',
  standalone: true,
  imports: [MatCardModule, MatButtonModule],
  template: `<mat-card>...</mat-card>`
})
```

---

## 3. JWT Auth Interceptor (HttpInterceptorFn)

**Functional Interceptor Pattern** (Angular 17+):
```typescript
// auth.interceptor.ts
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  if (token && !isPublicUrl(req.url)) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
  }

  return next(req).pipe(
    catchError(error => {
      if (error.status === 401) {
        // Handle token expiration - refresh & retry
        return authService.refreshToken().pipe(
          switchMap(newToken => {
            const retryReq = req.clone({
              setHeaders: { Authorization: `Bearer ${newToken}` }
            });
            return next(retryReq);
          })
        );
      }
      return throwError(() => error);
    })
  );
};

// app.config.ts - Register interceptor
export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(
      withInterceptors([authInterceptor])
    )
  ]
};
```

---

## 4. Project Structure (Medium-Scale)

**Recommended Layout:**
```
src/
├── app/
│   ├── core/              # Singletons: AuthService, interceptors, guards
│   │   ├── services/
│   │   ├── guards/
│   │   └── interceptors/
│   ├── shared/            # Reusable: components, pipes, utilities
│   │   ├── components/    # e.g., NavBar, Footer
│   │   ├── pipes/
│   │   └── utils/
│   ├── features/          # Feature modules (lazy-loaded)
│   │   ├── movies/        # List, detail, search
│   │   ├── booking/       # Seat selection, payment
│   │   ├── auth/          # Login, register
│   │   └── profile/       # User profile, history
│   ├── store/             # State management (signals or NgRx)
│   ├── app.routes.ts
│   └── app.config.ts
└── assets/
```

**LIFT Principle:** Locate quickly, Identify at glance, Flat structure, T-DRY (Try to stay DRY).

---

## 5. Routing with Lazy Loading & Guards

**Route Configuration:**
```typescript
// app.routes.ts
export const appRoutes: Routes = [
  { path: '', redirectTo: 'movies', pathMatch: 'full' },
  {
    path: 'auth',
    loadComponent: () => import('./features/auth/auth.component').then(m => m.AuthComponent)
  },
  {
    path: 'movies',
    loadChildren: () => import('./features/movies/movies.routes').then(m => m.MOVIES_ROUTES)
  },
  {
    path: 'booking/:movieId',
    canActivate: [authGuard],
    loadComponent: () => import('./features/booking/booking.component').then(m => m.BookingComponent)
  }
];

// movies.routes.ts (lazy-loaded feature)
export const MOVIES_ROUTES: Routes = [
  { path: '', component: MovieListComponent },
  { path: ':id', component: MovieDetailComponent }
];
```

**Route Guards (Functional):**
```typescript
// auth.guard.ts
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  router.navigate(['/auth/login'], { queryParams: { returnUrl: state.url } });
  return false;
};
```

---

## Key Takeaways

1. **Default to standalone** - No NgModules needed; better tree-shaking
2. **Use signals + computed** - Simpler state management than RxJS for UI logic
3. **@if/@for/@switch** - Cleaner templates, better performance
4. **HttpInterceptorFn** - Functional approach, no class boilerplate
5. **Feature-based structure** - Scales well; easy navigation
6. **Lazy load routes** - Load only what's needed; improves initial load
7. **Material 3 themes** - CSS variables for dynamic theming

---

## Sources

- [Angular Control Flow Guide](https://angular.dev/guide/templates/control-flow)
- [Angular Material Theming](https://v18.material.angular.dev/guide/theming)
- [Angular 17 Interceptors Tutorial](https://dev.to/bytebantz/angulars-17-interceptors-complete-tutorial-220k)
- [Angular Project Structure Best Practices](https://medium.com/@dragos.atanasoae_62577/angular-project-structure-guide-small-medium-and-large-projects-e17c361b2029)
- [Routing with Standalone Components](https://www.angulararchitects.io/en/blog/routing-and-lazy-loading-with-standalone-components/)
- [Route Guards in Angular 18](https://medium.com/@solomongetachew112/complete-guide-to-route-guards-in-angular-18-real-world-examples-best-practices-and-tips-113d791323d2)
