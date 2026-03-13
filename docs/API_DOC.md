# API Documentation — Admin & Protocol

## Error Model
- JSON: { "code": "string", "message": "string", "details": {...} }
- HTTP: 200/201/204, 400, 401, 403, 404, 409, 422, 429, 500

## OIDC Protocol (spec-aligned)
- GET /.well-known/openid-configuration
- GET /auth/realms/{realm}/protocol/openid-connect/auth
- POST /auth/realms/{realm}/protocol/openid-connect/token
- GET /auth/realms/{realm}/protocol/openid-connect/userinfo
- GET /auth/realms/{realm}/protocol/openid-connect/certs
- POST /auth/realms/{realm}/protocol/openid-connect/logout
- POST /auth/realms/{realm}/protocol/openid-connect/token/introspect

## Admin REST (v1)
- Realms
  - GET /admin/realms
  - POST /admin/realms
  - GET /admin/realms/{realm}
  - DELETE /admin/realms/{realm}
- Users
  - GET /admin/realms/{realm}/users?first&max&search
  - POST /admin/realms/{realm}/users
  - GET /admin/realms/{realm}/users/{id}
  - PUT /admin/realms/{realm}/users/{id}
  - DELETE /admin/realms/{realm}/users/{id}
  - POST /admin/realms/{realm}/users/{id}/credentials/password
- Roles
  - GET /admin/realms/{realm}/roles
  - POST /admin/realms/{realm}/roles
  - GET /admin/realms/{realm}/roles/{id}
  - DELETE /admin/realms/{realm}/roles/{id}
  - POST /admin/realms/{realm}/users/{userId}/roles/{roleId}   (assign)
  - DELETE /admin/realms/{realm}/users/{userId}/roles/{roleId} (unassign)
- Clients
  - GET /admin/realms/{realm}/clients
  - POST /admin/realms/{realm}/clients
  - GET /admin/realms/{realm}/clients/{id}
  - PUT /admin/realms/{realm}/clients/{id}
  - DELETE /admin/realms/{realm}/clients/{id}

## Admin — Sessions
- GET /admin/realms/{realm}/sessions
- DELETE /admin/realms/{realm}/sessions/{sessionId}

## Auth — Token Endpoint (MVP)
- POST /auth/realms/{realm}/protocol/openid-connect/token
  - grant_type=password
  - client_id=app-id
  - username, password
  - Response:
    {
      "access_token": "jwt",
      "id_token": "jwt",
      "token_type": "Bearer",
      "expires_in": 900
    }
    
## Auth — Logout (MVP)
- POST /auth/realms/{realm}/protocol/openid-connect/logout
  - Form: sid={sessionId}
  - Effect: invalidates the session by ID

## Example — Create User
Request:
{
  "username": "alice",
  "email": "alice@example.com",
  "enabled": true
}
Response: 201 Created + Location: /admin/realms/{realm}/users/{id}

## Base URLs (Dev)
- Backend: http://localhost:7070
- Admin UI: http://localhost:5000 (dev proxy forwards /admin → backend)
- Account UI: http://localhost:5100 (dev proxy forwards /admin and /auth → backend)

## cURL Examples (Dev)
- List realms:
  curl http://localhost:7070/admin/realms
- Create realm:
  curl -X POST http://localhost:7070/admin/realms \
    -H "Content-Type: application/json" \
    -d '{"name":"demo","displayName":"Demo"}'
- List users in realm:
  curl http://localhost:7070/admin/realms/{realmId}/users
- Create user in realm:
  curl -X POST http://localhost:7070/admin/realms/{realmId}/users \
    -H "Content-Type: application/json" \
    -d '{"username":"alice","email":"alice@example.com","enabled":true}'
- Set user password:
  curl -X POST http://localhost:7070/admin/realms/{realmId}/users/{userId}/credentials/password \
    -H "Content-Type: application/json" \
    -d '{"password":"Secret123!"}'
- Get token (password grant):
  curl -X POST http://localhost:7070/auth/realms/{realm}/protocol/openid-connect/token \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&client_id=my-app&username=alice&password=Secret123!"
- Create client in realm:
  curl -X POST http://localhost:7070/admin/realms/{realmId}/clients \
    -H "Content-Type: application/json" \
    -d '{"clientId":"my-app","protocol":"openid-connect","publicClient":true}'
- Create role in realm:
  curl -X POST http://localhost:7070/admin/realms/{realmId}/roles \
    -H "Content-Type: application/json" \
    -d '{"name":"admin"}'
- Assign role to user:
  curl -X POST http://localhost:7070/admin/realms/{realmId}/users/{userId}/roles/{roleId}
- List sessions:
  curl http://localhost:7070/admin/realms/{realmId}/sessions
- Delete session:
  curl -X DELETE http://localhost:7070/admin/realms/{realmId}/sessions/{sessionId}
- Logout via protocol:
  curl -X POST http://localhost:7070/auth/realms/{realm}/protocol/openid-connect/logout \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "sid={sessionId}"
