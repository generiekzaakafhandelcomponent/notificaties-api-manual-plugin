# Installation and technical details

This page is meant for developers and operators. For configuring the plugin in the user interface, see
[Notificaties API, manual abonnement](plugin.md).

The plugin has its own plugin key (`notificatiesapi-manual`) and one property, the Open Notificaties URL. It has no
callback address, process actions or database tables: notificaties arrive on the callback address of the standard
Notificaties API plugin. What it changes is what the standard plugin does in Open Notificaties for that URL; see
[How it works](#how-it-works).

---

## Installation

**Backend** (`build.gradle.kts`):

```kotlin
dependencies {
    implementation("com.ritense.valtimoplugins:notificaties-api-manual:2.0.0")
}
```

**Frontend** (`package.json`):

```json
{
  "dependencies": {
    "@valtimo-plugins/notificaties-api-manual": "2.0.0"
  }
}
```

In `app.module.ts`:

```typescript
import {PLUGINS_TOKEN} from '@valtimo/plugin';
import {
  NotificatiesApiManualPluginModule,
  notificatiesApiManualPluginSpecification,
} from '@valtimo-plugins/notificaties-api-manual';

@NgModule({
  imports: [
    NotificatiesApiManualPluginModule,
  ],
  providers: [
    {
      provide: PLUGINS_TOKEN,
      useValue: [
        notificatiesApiManualPluginSpecification,
      ],
    },
  ],
})
```

No application property is needed. Don't set `valtimo.zgw.register-abonnementen: false` for this plugin: the standard
plugin keeps registering abonnementen in other Open Notificaties URLs.

---

## How it works

The standard plugin registers an abonnement per Open Notificaties URL of its configurations, when the application has
started and when a configuration or plugin process link changes. The plugin wraps the `NotificatiesApiClient` bean
(with a bean post-processor, so a client that the application defines itself is wrapped too). For an Open Notificaties
URL of a configuration of this plugin (compared without trailing `/`), the wrapper:

- returns no abonnementen and no kanalen, so the standard plugin deletes and reads nothing;
- creates no kanalen, and creates, changes and deletes no abonnement. A created abonnement is returned with the URL
  `<Open Notificaties URL>/abonnement/registered-manually` and the key the standard plugin chose. No such abonnement
  exists in Open Notificaties.

The standard plugin stores that URL and key in `notificaties_api_abonnement_link`. Its callback
(`/api/v1/notificatiesapi/callback`) accepts every notificatie that carries a key from that table, so it accepts the
notificaties of the abonnement the administrator created, as long as its **Auth** is the **Authentication header** of
the configuration of the standard plugin. Without an Authentication header, the standard plugin picks a random key that
the administrator can't see.

The standard plugin stores one link per Open Notificaties URL, for one of its configurations on that URL. With more
than one configuration of the standard plugin on the URL, only the Authentication header of that one is accepted; the
form of this plugin warns about it.

Publishing notificaties, and every call for other URLs, go to the client unchanged.

The form of a configuration of this plugin calls `GET /api/management/v1/notificatiesapi-manual/configurations/{id}/manual-abonnement`
(admins only). It returns the configurations of the standard plugin on the same URL (without the value of their
Authentication header), and the kanalen with filters of their **Receive a notification** process links and of other
`NotificatiesApiListener` plugins linked to them, such as Verzoek and Portaaltaak.

### Order of configuration

The standard plugin registers when one of its configurations is saved. If no configuration of this plugin has that URL
yet, the wrapper passes the registration on and it fails behind the gateway.

{% hint style="danger" %}
On Valtimo `13.41.0` up to `13.41.0-4` and on `13.42.x`, a failing registration of the standard plugin also stops the
application from starting. Deploy configurations of this plugin (`.pluginconfig.json`) no later than those of the
standard plugin, or in the same deployment.
{% endhint %}

---

## Process variables

**Receive a notification** of the standard plugin sets these variables on the process: `notificatieKanaal`,
`notificatieResourceUrl`, `notificatieActie`, `notificatieKenmerken`, `notificatieHoofdObject` and
`notificatieAanmaakdatum`.

---

## Upgrading from 1.0.x

Version 2.0.0 no longer has its own callback, shared secret, **Automatic** registration or process actions. A
**Manual** configuration keeps its URL and becomes a configuration of 2.0.0. Plan the upgrade at a quiet moment:
notificaties that arrive between the upgrade and the last step are refused, and lost when Open Notificaties stops
retrying.

{% stepper %}
{% step %}
Delete configurations of this plugin with **Automatic** registration. After the upgrade they would stop the standard
plugin from registering on their URL.
{% endstep %}
{% step %}
Upgrade. The tables `notificaties_api_manual_abonnement_link` and `notificaties_api_manual_misrouted_callback` are
dropped. Open and save each configuration of this plugin once: Valtimo logs an error for the properties it no longer
knows (`Ignoring unknown property ...`) until then.
{% endstep %}
{% step %}
For each configuration of this plugin, create a configuration of the standard plugin with the same URL, and with the
old shared secret as **Authentication header**. Not before the upgrade: 1.0.x doesn't stop the standard plugin from
registering.
{% endstep %}
{% step %}
Link the **Publish a notification** and **Receive a notification** process links of this plugin to that configuration,
and check that processes waiting for a notificatie are linked to it too
{% endstep %}
{% step %}
In Open Notificaties, change the callback URL of the abonnement to the address of the standard plugin
(`/api/v1/notificatiesapi/callback`), and route that address through the gateway
{% endstep %}
{% endstepper %}
