#!/bin/sh
# Writes a /config.js that exposes realm + clientId to the React app at runtime.
# Override OI_REALM and OI_CLIENT_ID in docker-compose.yml or K8s env.
cat > /usr/share/nginx/html/config.js <<EOF
window.__OI_CONFIG__ = {
  realm: "${OI_REALM}",
  clientId: "${OI_CLIENT_ID}"
};
EOF
exec nginx -g 'daemon off;'
