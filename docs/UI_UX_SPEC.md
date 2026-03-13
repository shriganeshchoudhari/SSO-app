# UI/UX Specification

## Principles
- PatternFly components, consistent theming.
- Accessibility AA (WCAG 2.1), keyboard navigation.
- Responsive layouts (>=320px), LTR/RTL and i18n ready.

## Information Architecture
- Admin Console
  - Dashboard
  - Realms: list/create/settings
  - Clients: list/create/settings/roles/mappers
  - Users: list/create/details/roles/groups
  - Roles, Groups
  - Identity Providers, User Federation
  - Events/Audit, Settings
- Account Console
  - Profile, Credentials, Sessions, Applications

## Key Flows
- Login: username/password → optional OTP → consent (if enabled).
- Create Realm: name → display → save.
- Create OIDC Client: client_id → redirect_uris → type (public/confidential) → save.
- Assign Role to User: select user → Roles → assign.

## Interaction & Validation
- Inline field validation; error summaries.
- Confirm dangerous actions (delete).
- Loading/skeleton states; empty states with guidance.

## Theming
- Theme tokens for colors/typography; dark/light modes.

## Dev Environment
- Admin UI runs on http://localhost:5000 with a dev proxy to backend http://localhost:7070 for /admin requests.
- Avoid CORS issues by using the dev proxy in vite.config.ts.
