# Research Report: Auth-Service Email Flow

**Date:** 2026-03-08
**Status:** Complete

## Current Email Implementation

### EmailService Interface & Implementation
- **Location:** `auth-service/src/main/java/com/namnd/springjwt/service/`
- **Methods:**
  - `sendActivationEmail(String to, String token)` - Account activation emails
  - `sendPasswordResetEmail(String to, String token)` - Password reset emails
- **Implementation:** `EmailServiceImpl` uses Spring's `JavaMailSender` with `SimpleMailMessage`
- **Email Format:** Plain text only (no HTML/templates)
- **Masking:** Implements email masking for security logs

### Email Triggers
1. **Account Activation** → `ActivationServiceImpl.sendActivationEmail()`
   - Triggered during user registration
   - Token expires in 24 hours
   - Activation base URL: `http://localhost:8080/api/auth/activate`

2. **Password Reset** → `PasswordResetServiceImpl.sendPasswordResetEmail()`
   - Triggered on password reset request
   - Token expires in 30 minutes
   - Reset base URL: `http://localhost:4200/auth/reset-password`

### SMTP Configuration (application.yml)
```
Host: smtp.gmail.com
Port: 587
Auth: true
STARTTLS: enabled
Credentials: App password in config
```

### Current Limitations
- **Synchronous:** Email sending blocks request
- **No Templates:** Plain text only
- **No Retry:** Failures logged but not retried
- **No Queue:** No message queue/persistence
- **Error Handling:** Silent failures with logging only
- **Single Provider:** Gmail SMTP hardcoded

## Other Services
- **Booking Service:** No email notifications currently implemented
- **Payment Service:** No email notifications visible
- **No Event Publishing:** Services don't publish events for notifications

## Integration Points for Kafka Migration
1. Replace direct `EmailServiceImpl` calls with Kafka events
2. Create dedicated `notification-service` to consume email events
3. Decouple email sending from business logic
4. Enable async email with retry logic
5. Support other notification channels (SMS, push)

## Unresolved Questions
- Should we support email templates/HTML content?
- How to handle email delivery guarantees (at-least-once)?
- Should booking/payment services also send notifications?
- What's the SLA for email delivery?
