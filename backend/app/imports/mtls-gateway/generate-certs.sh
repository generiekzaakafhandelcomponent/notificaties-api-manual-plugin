#!/usr/bin/env bash
#
# Generates throwaway certificates for the local mTLS gateway (docker-compose service 'mtls-gateway') and writes
# them, together with the plugin configurations that use them, to:
#   - certs/ca.pem, certs/ca-key.pem          : CA that signs all other certificates
#   - certs/server.pem, certs/server-key.pem  : certificate the gateway presents (localhost, 127.0.0.1,
#                                               host.docker.internal, mtls-gateway)
#   - certs/client.pem, certs/client-key.pem  : client certificate Valtimo presents to the gateway
#   - certs/open-notificaties.pem, certs/open-notificaties-key.pem
#                                             : client certificate Open Notificaties presents to the gateway when it
#                                               delivers a notificatie (through 'open-notificaties-egress')
#   - src/main/resources/config/plugin/mtls-gateway.pluginconfig.json: the certificates of every Http Client
#     Authentication configuration in it
#
# For local testing only. Run from anywhere: ./backend/app/imports/mtls-gateway/generate-certs.sh

set -euo pipefail

GATEWAY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$GATEWAY_DIR/../.." && pwd)"
CERTS_DIR="$GATEWAY_DIR/certs"
PLUGIN_CONFIG="$APP_DIR/src/main/resources/config/plugin/mtls-gateway.pluginconfig.json"
DAYS=36500
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

mkdir -p "$CERTS_DIR"
cd "$CERTS_DIR"

openssl req -x509 -newkey rsa:2048 -nodes -days "$DAYS" \
  -keyout ca-key.pem -out ca.pem \
  -subj "/CN=Notificaties API manual sandbox CA"

cat > "$WORK_DIR/server.ext" <<EOF
basicConstraints = CA:FALSE
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = DNS:localhost, DNS:host.docker.internal, DNS:mtls-gateway, IP:127.0.0.1
EOF
openssl req -newkey rsa:2048 -nodes -keyout server-key.pem -out "$WORK_DIR/server.csr" -subj "/CN=localhost"
openssl x509 -req -in "$WORK_DIR/server.csr" -CA ca.pem -CAkey ca-key.pem -CAcreateserial -CAserial "$WORK_DIR/ca.srl" \
  -days "$DAYS" -extfile "$WORK_DIR/server.ext" -out server.pem

cat > "$WORK_DIR/client.ext" <<EOF
basicConstraints = CA:FALSE
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = clientAuth
EOF
client_certificate() {
  local name="$1" cn="$2"
  openssl req -newkey rsa:2048 -nodes -keyout "$name-key.pem" -out "$WORK_DIR/$name.csr" -subj "/CN=$cn"
  openssl x509 -req -in "$WORK_DIR/$name.csr" -CA ca.pem -CAkey ca-key.pem -CAserial "$WORK_DIR/ca.srl" \
    -days "$DAYS" -extfile "$WORK_DIR/client.ext" -out "$name.pem"
}
client_certificate client valtimo-sandbox-client
client_certificate open-notificaties open-notificaties-sandbox-client

# nginx in the containers runs as another user than the owner of these files
chmod 644 ./*.pem

python3 - "$CERTS_DIR" "$PLUGIN_CONFIG" <<'EOF'
import json, pathlib, sys

certs = pathlib.Path(sys.argv[1])
pem = lambda name: (certs / name).read_text()
tls = {
    "clientCertificate": pem("client.pem"),
    "clientPrivateKey": pem("client-key.pem"),
    "serverCertificate": pem("ca.pem"),
}
config_file = pathlib.Path(sys.argv[2])
configs = json.loads(config_file.read_text())
for config in configs:
    if config["pluginDefinitionKey"] == "http-client-authentication":
        config["properties"].update(tls)
config_file.write_text(json.dumps(configs, indent=4) + "\n")
EOF

echo "Certificates written to $CERTS_DIR"
echo "Plugin configurations written to $PLUGIN_CONFIG"
