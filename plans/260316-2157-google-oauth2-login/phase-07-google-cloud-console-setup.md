# Phase 7: Google Cloud Console Setup

## Context Links
- [Plan overview](./plan.md)

## Overview
- **Priority:** P1 (blocking — credentials needed for testing)
- **Status:** pending
- **Description:** Step-by-step guide to create Google OAuth2 credentials

## Setup Instructions

### 1. Go to Google Cloud Console
- Navigate to https://console.cloud.google.com
- Create a new project or select existing one

### 2. Enable APIs
- Go to "APIs & Services" → "Library"
- Search and enable "Google+ API" (or "Google Identity Services")

### 3. Configure OAuth Consent Screen
- Go to "APIs & Services" → "OAuth consent screen"
- Select "External" user type
- Fill in: App name, User support email, Developer email
- Add scopes: `email`, `profile`, `openid`
- Add test users (your Google email) if in testing mode
- Save

### 4. Create OAuth2 Credentials
- Go to "APIs & Services" → "Credentials"
- Click "Create Credentials" → "OAuth 2.0 Client IDs"
- Application type: "Web application"
- Name: "ms-cinema-dev"
- Authorized JavaScript origins: `http://localhost:8080`
- Authorized redirect URIs: `http://localhost:8080/login/oauth2/code/google`
- Click "Create"
- Copy **Client ID** and **Client Secret**

### 5. Set Environment Variables
```bash
export GOOGLE_CLIENT_ID=your-client-id-here.apps.googleusercontent.com
export GOOGLE_CLIENT_SECRET=your-client-secret-here
```

Or add to `.env` file (not committed to git):
```
GOOGLE_CLIENT_ID=your-client-id-here.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret-here
```

### 6. Production Setup
For production, add additional redirect URI:
- `https://your-domain.com/login/oauth2/code/google`

## Todo List
- [ ] Create Google Cloud project
- [ ] Configure OAuth consent screen
- [ ] Create OAuth2 credentials
- [ ] Set environment variables
- [ ] Verify credentials work with auth-service

## Success Criteria
- GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET env vars set
- OAuth2 flow redirects to Google consent screen successfully

## Security Considerations
- Never commit client-secret to git
- Use env vars or secret manager
- Restrict authorized origins/redirect URIs to known domains
