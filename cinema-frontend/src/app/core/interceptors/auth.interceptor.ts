import { HttpInterceptorFn, HttpRequest, HttpErrorResponse, HttpHandlerFn, HttpEvent } from '@angular/common/http';
import { inject } from '@angular/core';
import { BehaviorSubject, Observable, catchError, filter, switchMap, take, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

const PUBLIC_URLS = [
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/forgot-password',
  '/api/auth/reset-password',
  '/api/auth/activate',
  '/api/auth/resend-activation',
  '/api/auth/refresh-token',
  '/api/bookings/showtimes/',
  '/api/movies',
  '/api/showtimes'
];

let isRefreshing = false;
const refreshTokenSubject = new BehaviorSubject<string | null>(null);

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);

  // Skip token attachment for auth endpoints (refresh-token sends expired token which causes 401)
  const isAuthEndpoint = req.url.includes('/api/auth/');
  const isPublic = PUBLIC_URLS.some(url => req.url.includes(url));

  // Attach token for non-auth requests (public endpoints still get JWT for personalized data)
  const token = !isAuthEndpoint ? authService.getToken() : null;
  const authReq = token ? addToken(req, token) : req;

  return next(authReq).pipe(
    catchError(error => {
      if (!isPublic && error instanceof HttpErrorResponse && error.status === 401) {
        return handle401Error(authReq, next, authService);
      }
      return throwError(() => error);
    })
  );
};

function addToken(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

function handle401Error(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  authService: AuthService
): Observable<HttpEvent<unknown>> {
  if (!isRefreshing) {
    isRefreshing = true;
    refreshTokenSubject.next(null);

    return authService.refreshToken().pipe(
      switchMap(response => {
        isRefreshing = false;
        refreshTokenSubject.next(response.accessToken);
        return next(addToken(req, response.accessToken));
      }),
      catchError(err => {
        isRefreshing = false;
        authService.logout();
        return throwError(() => err);
      })
    );
  } else {
    return refreshTokenSubject.pipe(
      filter((token): token is string => token !== null),
      take(1),
      switchMap(token => next(addToken(req, token)))
    );
  }
}
