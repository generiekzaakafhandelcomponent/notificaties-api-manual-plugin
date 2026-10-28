# Installation and technical details

This page is meant for developers and operators. For configuring the plugin in the user interface, see
[Notificaties API (manual abonnement)](plugin.md).

The plugin has its own plugin key (`notificatiesapi-manual`), callback addresses and database tables. Installing it
changes nothing for the standard Notificaties API plugin: it only reads the `Authorization` header of notificaties on
the address of the standard plugin, to [warn about misrouted notificaties](#callback).

---

## Installation

**Backend** (`build.gradle.kts`):

```kotlin
dependencies {
    implementation("com.ritense.valtimoplugins:notificaties-api-manual:1.0.1")
}
```

**Frontend** (`package.json`):

```json
{
  "dependencies": {
    "@valtimo-plugins/notificaties-api-manual": "1.0.1"
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

The plugin creates its own tables (`notificaties_api_manual_abonnement_link` and
`notificaties_api_manual_misrouted_callback`) when the application starts.

---

## Application properties

### Standard plugin

If the standard Notificaties API plugin has a configuration whose callback address is only reachable through the
gateway, its own automatic registration fails too. Turn it off:

```yaml
valtimo:
  zgw:
    register-abonnementen: false
```

{% hint style="danger" %}
On Valtimo `13.41.0` up to `13.41.0-4` and on `13.42.x`, this setting is **required** in that situation: a failing
registration of the standard plugin stops the application from starting. From `13.41.0-5` and `13.43.0` the
application keeps running, but keeps retrying for a while and logs errors.
{% endhint %}

The setting applies to all configurations of the standard plugin. It does not affect this plugin.

### This plugin

| Property | Default | Description |
|----------|---------|-------------|
| `valtimo.notificaties-api-manual.register-abonnementen` | `true` | `false` turns off the automatic registration of this plugin's configurations. Manual configurations never register anything. |
| `valtimo.notificaties-api-manual.abonnement-registration.initial-backoff` | `2s` | First wait before retrying the registration at startup. |
| `valtimo.notificaties-api-manual.abonnement-registration.max-backoff` | `30s` | Longest wait between retries. |
| `valtimo.notificaties-api-manual.abonnement-registration.max-duration` | `15m` | After this time the startup registration stops retrying. |

---

## Configuration rules

A configuration is refused when:

- the shared secret starts with `Bearer` (in any case), starts or ends with a space, contains characters other than
  printable ASCII, or is longer than 255 characters;
- an **Automatic** configuration has no callback URL;
- the callback URL ends in `/api/v1/notificatiesapi/callback` (the address of the standard plugin), or is the callback
  URL of a configuration of the standard plugin.

The form removes spaces around the secret and stops at 255 characters; the other rules are checked when saving.

{% hint style="warning" %}
These rules also apply to configurations in a `.pluginconfig.json` file. A file that breaks one of them stops the
application from starting.
{% endhint %}

---

## Callback

Valtimo accepts a notificatie on the callback address of this plugin (`/api/v1/notificatiesapi-manual/callback`) when
it carries the shared secret of a **Manual** configuration, or the secret of an abonnement that Valtimo registered for
an **Automatic** configuration of this plugin. Other notificaties are answered with `401`, or `403` without a secret,
and not processed.

Valtimo also looks at the `Authorization` header of every notificatie on the address of the standard plugin
(`/api/v1/notificatiesapi/callback`). When it is the shared secret of a **Manual** configuration and not a secret of
the standard plugin, Valtimo logs a warning and stores the time per configuration in the table
`notificaties_api_manual_misrouted_callback`. The form of that configuration then shows a warning. The standard plugin
still answers the notificatie itself (`401`, or `204` for the test notificatie). The stored time is removed when a
notificatie with that secret arrives on the address of this plugin. Times of configurations that were deleted or are no
longer **Manual** are removed with the next notificatie that this plugin accepts.

Accepted notificaties go into the same queue as those of the standard plugin, so all existing consumers receive them. A
notificatie that arrives through both plugins is processed once; when both deliver it at the same moment, one delivery
may first be answered with an error. Open Notificaties retries it, and it is then ignored.

---

## Process variables

**Receive a notification** sets these variables on the process: `notificatieKanaal`, `notificatieResourceUrl`,
`notificatieActie`, `notificatieKenmerken`, `notificatieHoofdObject` and `notificatieAanmaakdatum`.

---

## Automatic registration

- Registration runs in the background after saving. If Open Notificaties can't reach the callback URL, the
  registration fails and the application log says so.
- One abonnement is registered per Open Notificaties URL. Only the secret of the configuration that holds it is used.
- Valtimo deletes abonnementen with its callback URL that it didn't register itself, as the standard plugin does. It
  skips this when a **Manual** configuration uses the same Open Notificaties URL or callback URL, or has no callback
  URL, because an administrator's abonnement can't be told apart from a leftover one; remove leftovers in Open
  Notificaties then.
- Start one instance of the application first when several start at the same time with a new **Automatic**
  configuration.
- Process links copied with a BPMN-only redeployment reach the abonnement at the next change or restart.
- When Valtimo cannot remove an automatically registered abonnement, it logs its address; remove it in Open
  Notificaties.
- When a configuration is switched to **Manual** without an **Authentication header**, Valtimo doesn't show the old
  abonnement's secret, so its notificaties are refused (`401`) until the abonnement is removed.
