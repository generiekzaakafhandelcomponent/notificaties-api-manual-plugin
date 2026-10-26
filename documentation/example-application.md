# Example Application

This project contains a sandbox application with the plugin installed. It deploys a configuration of this plugin with manual registration (`backend/app/src/main/resources/config/plugin/notificaties-api-manual.pluginconfig.json`, shared secret `local-shared-secret`).

## Running the example application

Run the Gradle commands from the **project root** directory and the npm commands from `frontend/`.

### Prerequisites

- Java 17 or later
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

1. Log in as `admin` and go to **Admin** > **Plugins**. Open **Notificaties API, handmatig abonnement (Autodeployed)**:
   the form shows **Manual** (**Handmatig**) registration and, below it, the callback URL and the channel the abonnement must contain.
2. Send a notificatie straight to the callback address, as Open Notificaties would (this skips Open Notificaties; to go
   through it, first create kanaal `objecten` and an abonnement as described in [Plugin](plugin.md)):

   ```shell
   curl -i -X POST localhost:8080/api/v1/notificatiesapi-manual/callback/manual \
     -H 'Authorization: local-shared-secret' -H 'Content-Type: application/json' \
     -d '{"kanaal":"objecten","hoofdObject":"http://x/1","resourceUrl":"http://x/'"$(date +%s)"'","actie":"create","aanmaakdatum":"'"$(date -u +%FT%T)"'","kenmerken":{}}'
   ```

   The answer is `204` and the notificatie is passed on to the plugins that process notificaties. With another secret
   the answer is `401`, without one `403`. A notificatie identical to one received before is ignored (still `204`);
   the command above makes each one unique.
