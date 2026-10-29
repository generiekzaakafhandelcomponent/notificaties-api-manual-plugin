#!/usr/bin/env bash
#
# Verzoek test scenario for the sandbox application (see documentation/example-application.md).
#
#   ./backend/app/verzoek-scenario.sh abonnement        Create kanaal 'objecten' and the manual abonnement in Open
#                                                       Notificaties, as an administrator would (idempotent).
#   ./backend/app/verzoek-scenario.sh verzoek [voornaam] Create a verzoek object in the Objecten API, through the mTLS
#                                                       gateway. The Objecten API notifies Open Notificaties, which
#                                                       delivers the notificatie through its egress proxy and the
#                                                       gateway to the callback of the standard Notificaties API
#                                                       plugin; Verzoek then fetches the object through the gateway
#                                                       and creates a Verzoek case.
#
# Needs the Docker stack (./gradlew :backend:app:composeUp) and, for 'verzoek', the running backend.

set -euo pipefail

# The egress proxy of Open Notificaties, which forwards to the gateway (https://mtls-gateway:8446) with its certificate
CALLBACK_URL="http://localhost:8446/api/v1/notificatiesapi/callback"
# The Auth header of the standard Notificaties API configuration (mtls-gateway.pluginconfig.json)
SHARED_SECRET="local-shared-secret"
# The Objecten API in Docker only accepts the objecttype under the address it knows the Objecttypen API by.
OBJECTTYPE_URL="http://host.docker.internal:8011/api/v2/objecttypes/021f685e-9482-4620-b157-34cd4003da6b"
OBJECTS_API_TOKEN="182c13e2209161852c53cef53a879f7a2f923430"
CERTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/imports/mtls-gateway/certs"

create_abonnement() {
    docker exec -i -w /app/src plugin-docker-compose-open-notificaties python manage.py shell <<EOF 2>&1 | grep '^abonnement'
from nrc.datamodel.models import Abonnement, Filter, FilterGroup, Kanaal

# Like Valtimo creates kanalen: without a list of filter names.
kanaal, _ = Kanaal.objects.get_or_create(naam="objecten", defaults={"documentatie_link": "", "filters": []})
# Also the one on /api/v1/notificatiesapi-manual/callback that versions before 2.0.0 used
Abonnement.objects.filter(callback_url__regex=r"/api/v1/notificatiesapi(-manual)?/callback$").delete()
abonnement = Abonnement.objects.create(callback_url="$CALLBACK_URL", auth="$SHARED_SECRET", client_id="valtimo")
for filters in [
    # 'Receive a notification' process link of the Example case
    {"actie": "create"},
    # Verzoek, which the form shows with https://127.0.0.1:8447 (the URL of the Objecttypen API plugin via the gateway)
    {"objectType": "$OBJECTTYPE_URL", "actie": "create"},
]:
    group = FilterGroup.objects.create(abonnement=abonnement, kanaal=kanaal)
    for key, value in filters.items():
        Filter.objects.create(filter_group=group, key=key, value=value)
print("abonnement", abonnement.uuid, "->", abonnement.callback_url)
EOF
}

create_verzoek() {
    local voornaam="${1:-Jan}"
    curl -sS -X POST https://127.0.0.1:8443/api/v2/objects \
        --cacert "$CERTS_DIR/ca.pem" --cert "$CERTS_DIR/client.pem" --key "$CERTS_DIR/client-key.pem" \
        -H "Authorization: Token $OBJECTS_API_TOKEN" \
        -H 'Content-Type: application/json' \
        -H 'Content-Crs: EPSG:4326' \
        -d '{
            "type": "'"$OBJECTTYPE_URL"'",
            "record": {
                "typeVersion": 1,
                "startAt": "'"$(date +%F)"'",
                "data": {"type": "verzoek-test", "bsn": "999990755", "data": {"voornaam": "'"$voornaam"'"}}
            }
        }'
    echo
}

case "${1:-}" in
    abonnement) create_abonnement ;;
    verzoek) create_verzoek "${2:-}" ;;
    *)
        echo "Usage: $0 abonnement | verzoek [voornaam]" >&2
        exit 1
        ;;
esac
