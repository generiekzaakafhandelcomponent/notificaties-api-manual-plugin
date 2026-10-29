# Example Application

This project contains a sandbox application with the plugin installed. It deploys a configuration of this plugin (`backend/app/src/main/resources/config/plugin/notificaties-api-manual.pluginconfig.json`), a configuration of the standard Notificaties API plugin with the same URL and Authentication header `local-shared-secret` (in `mtls-gateway.pluginconfig.json`), and a **Verzoek** configuration linked to it (see [Trying Verzoek](#trying-verzoek)).
All traffic between Valtimo and the ZGW APIs for them goes through a gateway that demands a client certificate (see
[The mTLS gateway](#the-mtls-gateway)).

The sandbox runs Valtimo `13.51.0` (`sandboxValtimoVersion` in `gradle.properties`): the Http Client Authentication
plugin it uses for the gateway needs `13.48` or later. The plugin itself is still built against `13.41.0`.

## Running the example application

Run the Gradle commands from the **project root** directory and the npm commands from `frontend/`.

### Prerequisites

- Java 21
- [Docker (Desktop)](https://www.docker.com/products/docker-desktop/)
- Node.js 20 (nvm recommended)

### Start docker

Make sure docker is running.

Start with gradle script:

```shell
./gradlew :backend:app:composeUp
```

### Start backend

By gradle script:

```shell
./gradlew :backend:app:bootRun
```

### Start frontend

```shell
cd frontend
nvm use 20
npm run clean
npm install
npm run build
npm start
```

### Keycloak users

The example application has a few test users that are preconfigured.

| Name         | Role           | Username  | Password  |
|--------------|----------------|-----------|-----------|
| James Vance  | ROLE_USER      | user      | user      |
| Asha Miller  | ROLE_ADMIN     | admin     | admin     |
| Morgan Finch | ROLE_DEVELOPER | developer | developer |

## Source code

The source code is split up into two modules:

1. [Frontend](/frontend)
2. [Backend](/backend)

## Trying the plugin

Because the configuration of this plugin uses the URL of the standard configuration **Notificaties API via mTLS
gateway (Autodeployed)**, the standard plugin registers nothing there: at startup the log shows
`Not creating an abonnement in Open Notificaties at 'https://127.0.0.1:8445/api/v1/' ...`.

1. Log in as `admin` and go to **Admin** > **Plugins**. Open **Open Notificaties via mTLS gateway, handmatig abonnement
   (Autodeployed)**: the form starts with the notice that the standard plugin is configured after this one, and below
   it shows the standard configuration and the callback URL and channels the abonnement must contain.
2. Start an **Example** case: **Cases** (**Dossiers**) > **Example** > **Create new case** (**Creëer Nieuw Dossier**),
   fill in a name and click **Start**. Its process waits in a receive task for a notificatie on channel `objecten` with action
   `create`.
3. Send a notificatie straight to the callback address of the standard plugin, as Open Notificaties would (this skips
   Open Notificaties and the gateway; to go through them, see [Trying Verzoek](#trying-verzoek)):

   ```shell
   curl -i -X POST localhost:8080/api/v1/notificatiesapi/callback \
     -H 'Authorization: local-shared-secret' -H 'Content-Type: application/json' \
     -d '{"kanaal":"objecten","hoofdObject":"http://x/1","resource":"object","resourceUrl":"http://x/'"$(date +%s)"'","actie":"create","aanmaakdatum":"'"$(date -u +%FT%T)"'","kenmerken":{}}'
   ```

   The answer is `204`. The case continues and shows the resource URL of the notificatie. With another secret the
   answer is `401`, without one `403`. A notificatie identical to one received before is ignored (still `204`);
   the command above makes each one unique per second. The secret is accepted because the standard plugin stored it
   as the key of the abonnement it "registered" at startup.

## Trying Verzoek

The sandbox deploys a **Verzoek** setup that receives its notificaties through an abonnement created by hand:

| What | Where (`backend/app/src/main/resources/config/`) |
|------|--------------------------------------------------|
| **Verzoek (Autodeployed)**: verzoek type `verzoek-test`, linked to the standard **Notificaties API via mTLS gateway (Autodeployed)** configuration, starts `verzoek-behandelen` in a new **Verzoek** case | `plugin/verzoek.pluginconfig.json` |
| Object management **Verzoek** for the objecttype *Productaanvraag*, with the Objecten API and Objecttypen API via the gateway | `global/object-management/verzoek.object-management.json` |
| Case **Verzoek**, with a zaaktype link (*Bezwaar behandelen*, via the gateway) and the process `verzoek-behandelen` with user task **Verzoek beoordelen** | `case/verzoek/1-0-0/` |

Verzoek copies `voornaam` (from the data of the verzoek), `bsn` and the URL of the verzoek object into the case.

1. Go to **Admin** > **Plugins** and open **Open Notificaties via mTLS gateway, handmatig abonnement (Autodeployed)**.
   The channels include `objecten` with the filters `objectType` and `actie = create` of Verzoek.
2. Create the channel and the abonnement in Open Notificaties, as an administrator would:

   ```shell
   ./backend/app/verzoek-scenario.sh abonnement
   ```

   This creates channel `objecten` and an abonnement on `http://localhost:8446/api/v1/notificatiesapi/callback`
   (the egress proxy of Open Notificaties, which forwards to the gateway) with secret `local-shared-secret`, and the
   filters of both the Example case and Verzoek. The `objectType` filter of Verzoek uses
   `http://host.docker.internal:8011/...` instead of the `https://127.0.0.1:8447/...` shown in the form: the Objecten
   API in Docker knows the Objecttypen API only by that address, so its notificaties carry it.
3. Submit a verzoek, as a form would:

   ```shell
   ./backend/app/verzoek-scenario.sh verzoek Jan
   ```

   The script creates the verzoek object through the gateway, with Valtimo's client certificate. The Objecten API
   notifies Open Notificaties, which delivers the notificatie through its egress proxy and the gateway to the callback
   of the standard plugin. Verzoek fetches the object through the gateway. The log shows `Received verzoek notification`, and
   within a few seconds a **Verzoek** case appears under **Cases** with the voornaam, the BSN and the URL of the verzoek
   object, and the task **Verzoek beoordelen**. The gateway log (`docker logs plugin-docker-compose-mtls-gateway`) shows
   every step with the client certificate that was presented.

   The same requests are in [verzoek.http](/backend/app/http/verzoek.http), for the HTTP Client of IntelliJ IDEA, with
   the environments `mtls` (through the gateway) and `direct`.

{% hint style="info" %}
Valtimo logs an `AccessDeniedException` from `TaskUpdateListener` after the case is created. That listener of
the standard Valtimo modules only sends the live update of the new task to open browsers; the case and the task are
created anyway. Verzoek through the standard plugin logs the same.
{% endhint %}

## The mTLS gateway

`mtls-gateway` in the docker compose setup is an nginx gateway that only lets requests through when they present a
client certificate signed by the sandbox CA, like the gateway between Valtimo and the ZGW APIs at a municipality. It
works in both directions:

| Gateway                    | Forwards to                                                        | Client certificate                    |
|----------------------------|--------------------------------------------------------------------|---------------------------------------|
| `https://127.0.0.1:8443`   | Objecten API                                                       | Valtimo (`valtimo-sandbox-client`)    |
| `https://127.0.0.1:8444`   | Open Zaak                                                          | Valtimo                               |
| `https://127.0.0.1:8445`   | Open Notificaties                                                  | Valtimo                               |
| `https://127.0.0.1:8447`   | Objecttypen API                                                    | Valtimo                               |
| `https://mtls-gateway:8446`| Valtimo, only `/api/v1/notificatiesapi/callback` (other paths `404`) | Open Notificaties (`open-notificaties-sandbox-client`) |

**Valtimo to the ZGW APIs.** The configurations in
[mtls-gateway.pluginconfig.json](/backend/app/src/main/resources/config/plugin/mtls-gateway.pluginconfig.json) use the
[Http Client Authentication](https://github.com/valtimo-platform/valtimo-plugins) plugin, which presents Valtimo's
client certificate and trusts the sandbox CA: Objecten API, Objecttypen API, Zaken API and Catalogi API, and the
standard Notificaties API configuration on `https://127.0.0.1:8445/api/v1/`. The configurations in
`_zgw.pluginconfig.json` still reach the ZGW APIs directly.

**Open Notificaties to Valtimo.** Open Notificaties can't present a client certificate itself. Its delivery worker
sends notificaties to `http://localhost:8446`, where `open-notificaties-egress` (an nginx in the network of the worker)
forwards them to the gateway with the certificate of Open Notificaties, as an outgoing gateway would at a
municipality. That is why the abonnement uses `http://localhost:8446/api/v1/notificatiesapi/callback`. Only the
delivery worker has this proxy: the test message that Open Notificaties sends from its web container when an
abonnement is registered through its API doesn't get through, as with the gateway this plugin is made for.

The certificates (`backend/app/imports/mtls-gateway/certs/`) are throwaway certificates for local testing. To renew
them, run the script below; it also writes them into the Http Client Authentication configurations. Restart the
gateway, the egress proxy and Valtimo afterwards.

```shell
./backend/app/imports/mtls-gateway/generate-certs.sh
```
