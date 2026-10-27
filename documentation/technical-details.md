# Installation and technical details

This page is meant for developers and operators. For configuring the plugin in the user interface, see
[Notificaties API (manual abonnement)](plugin.md).

The plugin has its own plugin key (`notificatiesapi-manual`), callback addresses and database table. Installing it
changes nothing for the standard Notificaties API plugin.

---

## Installation

**Backend** (`build.gradle.kts`):

```kotlin
dependencies {
    implementation("com.ritense.valtimoplugins:notificaties-api-manual:1.0.0-V12")
}
```

**Frontend** (`package.json`):

```json
{
  "dependencies": {
    "@valtimo-plugins/notificaties-api-manual": "1.0.0-V12"
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

The plugin creates its own table (`notificaties_api_manual_abonnement_link`) when the application starts.

---

## Configuration rules

A configuration is refused when:

- the shared secret starts with `Bearer` (in any case), starts or ends with a space, contains characters other than
  printable ASCII, or is longer than 255 characters;
- the callback URL ends in `/api/v1/notificatiesapi/callback` (the address of the standard plugin), or is the callback
  URL of a configuration of the standard plugin.

The form removes spaces around the secret and stops at 255 characters; the other rules are checked when saving.

{% hint style="warning" %}
These rules also apply to configurations in a `.pluginconfig.json` file. On Valtimo 12, a configuration that breaks
one of them is not deployed, and the configurations after it in the same file are skipped; the application log names
the error.
{% endhint %}

---

## Callback

Valtimo accepts a notificatie on the callback address of this plugin (`/api/v1/notificatiesapi-manual/callback`) when
it carries the shared secret of a **Manual** configuration, or the secret of an abonnement that Valtimo registered for
an **Automatic** configuration of this plugin. Other notificaties are answered with `401`, or `403` without a secret,
and not processed.

A notificatie that arrives through both plugins is processed once; when both deliver it at the same moment, one
delivery may first be answered with an error. Open Notificaties retries it, and it is then ignored.

---

## Automatic registration

- Each **Automatic** configuration registers its own abonnement on channel `objecten` when it is saved. When Open
  Notificaties can't be reached, the configuration is not saved.
- When Valtimo cannot remove an automatically registered abonnement, it logs its address; remove it in Open
  Notificaties.
- When a configuration is switched to **Manual** without an **Authentication header**, Valtimo doesn't show the old
  abonnement's secret, so its notificaties are refused (`401`) until the abonnement is removed.
