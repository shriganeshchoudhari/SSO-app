param(
  [string]$Realm = "demo",
  [string]$ClientId = "admin-cli",
  [string]$Username = "admin",
  [string]$Password = "Admin123!",
  [string]$AuthBaseUrl = "http://localhost:7070"
)

$body = "grant_type=password&client_id=$ClientId&username=$Username&password=$Password"
(Invoke-RestMethod `
  -Method Post `
  -Uri "$AuthBaseUrl/auth/realms/$Realm/protocol/openid-connect/token" `
  -ContentType "application/x-www-form-urlencoded" `
  -Body $body).access_token
