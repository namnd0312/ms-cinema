import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ConsentViewModel } from '../../core/models/oauth-consent.model';
import { OauthConsentService } from '../../core/services/oauth-consent.service';

/**
 * Standalone consent screen for OIDC partner login.
 *
 * Anti-spoofing: every visible value (clientName, redirectHost, scope labels) comes
 * from the server view-model — query params are used only to fetch the model.
 *
 * Submission posts a synthesized HTML form to /oauth2/authorize so Spring AS handles
 * state validation + consent persistence natively (and CSRF stays under its control).
 */
@Component({
  selector: 'app-oauth-consent',
  standalone: true,
  imports: [
    CommonModule, MatCardModule, MatButtonModule, MatListModule, MatProgressSpinnerModule,
  ],
  templateUrl: './oauth-consent.component.html',
  styleUrls: ['./oauth-consent.component.scss'],
})
export class OauthConsentComponent {
  vm = signal<ConsentViewModel | null>(null);
  error = signal<string | null>(null);

  private route = inject(ActivatedRoute);
  private svc = inject(OauthConsentService);

  constructor() {
    const q = this.route.snapshot.queryParamMap;
    const clientId = q.get('client_id');
    const state = q.get('state');
    const scope = q.get('scope');
    if (!clientId || !state || !scope) {
      this.error.set('Missing OAuth2 parameters; cannot continue.');
      return;
    }
    this.svc.getViewModel({ client_id: clientId, state, scope }).subscribe({
      next: v => this.vm.set(v),
      error: () => this.error.set('Unable to load consent details.'),
    });
  }

  allow(): void {
    this.submit(true);
  }

  deny(): void {
    this.submit(false);
  }

  /**
   * Build + auto-submit a hidden form POST to Spring AS's authorize endpoint.
   * Echoes back the original state + each requested scope so Spring AS can
   * resume the auth flow correctly. For deny, sends no scope params — Spring AS
   * interprets that as user_denied -> error=access_denied to partner.
   */
  private submit(authorized: boolean): void {
    const v = this.vm();
    if (!v) return;
    const form = document.createElement('form');
    form.method = 'post';
    form.action = '/oauth2/authorize';
    this.appendField(form, 'client_id', v.clientId);
    this.appendField(form, 'state', v.state);
    if (authorized) {
      v.scopes.forEach(s => this.appendField(form, 'scope', s.id));
    }
    document.body.appendChild(form);
    form.submit();
  }

  private appendField(form: HTMLFormElement, name: string, value: string): void {
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = name;
    input.value = value;
    form.appendChild(input);
  }
}
