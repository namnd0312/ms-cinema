import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      let message = 'An unexpected error occurred';

      switch (error.status) {
        case 0:
          message = 'Unable to connect to server';
          break;
        case 400:
          message = extractMessage(error) || 'Invalid request';
          break;
        case 401:
          // Handled by auth interceptor; don't show snackbar
          return throwError(() => error);
        case 403:
          message = 'Access denied';
          break;
        case 404:
          message = 'Resource not found';
          break;
        case 409:
          message = extractMessage(error) || 'Conflict';
          break;
        case 423:
          message = extractMessage(error) || 'Account is locked. Try again later.';
          break;
        case 500:
          message = 'Server error. Please try again later.';
          break;
      }

      snackBar.open(message, 'Close', { duration: 5000, panelClass: 'error-snackbar' });
      return throwError(() => error);
    })
  );
};

function extractMessage(error: HttpErrorResponse): string | null {
  if (typeof error.error === 'string') return error.error;
  if (error.error?.message) return error.error.message;
  return null;
}
