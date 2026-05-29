import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ConsentViewModel } from '../models/oauth-consent.model';

/**
 * HTTP client for /api/oauth/consent view-model.
 * Submission posts directly to /oauth2/authorize so Spring AS owns state/consent
 * persistence — there is intentionally NO submit() here.
 */
@Injectable({ providedIn: 'root' })
export class OauthConsentService {
  private http = inject(HttpClient);

  getViewModel(opts: { client_id: string; state: string; scope: string }): Observable<ConsentViewModel> {
    const params = new HttpParams()
      .set('client_id', opts.client_id)
      .set('state', opts.state)
      .set('scope', opts.scope);
    return this.http.get<ConsentViewModel>('/api/oauth/consent', { params });
  }
}
