/**
 * View-model for the OAuth2/OIDC consent screen.
 * Mirrors auth-service ConsentViewModelResponse — server-side is single source of truth.
 */
export interface ScopeLabel {
  id: string;
  label: string;
}

export interface ConsentViewModel {
  clientId: string;
  clientName: string;
  redirectHost: string;
  scopes: ScopeLabel[];
  state: string;
}
