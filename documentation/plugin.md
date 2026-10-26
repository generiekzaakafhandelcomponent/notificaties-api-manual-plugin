# Notificaties API (manual abonnement)

{% hint style="success" %}
For Valtimo `12.45.0` and later 12.x versions, GZAC edition (version `1.0.0-V12`). On Valtimo 13, use version `1.0.0`.
{% endhint %}

The Notificaties API (manual abonnement) plugin connects Valtimo to Open Notificaties, like the standard Notificaties
API plugin, and adds one option: an administrator can create the abonnement (subscription) in Open Notificaties
instead of Valtimo. Use it when all traffic between systems goes through a gateway that checks certificates. During
automatic registration Open Notificaties sends a test message to Valtimo without a certificate; such a gateway refuses
it, so automatic registration fails.

The plugin has its own plugin key (`notificatiesapi-manual`), callback address and database table. Installing it
changes nothing for the standard Notificaties API plugin. Notificaties it receives are processed exactly like those of
the standard plugin: **Verzoek**, **Portaaltaak**, **Receive a notification** process links and other plugins all
receive them. A notificatie that arrives through both plugins is processed once (when both deliver it at the same
moment, one delivery may first be answered with an error; Open Notificaties retries it, and it is then ignored).

Each configuration chooses how its abonnement is registered:

| Abonnement registration | Who creates the abonnement | When to use |
|-------------------------|----------------------------|-------------|
| **Automatic** | Valtimo registers, updates and removes the abonnement and the channels it needs, when the configuration is saved. If Open Notificaties can't reach the callback URL, saving fails. | Open Notificaties can reach Valtimo directly. |
| **Manual** | An administrator creates the abonnement in the Open Notificaties admin. Valtimo creates, changes and removes nothing in Open Notificaties for this configuration. | Traffic goes through a gateway that checks certificates. |

---

## Installation

A developer adds the plugin to the GZAC application.

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

### Standard plugin configurations behind the gateway

On Valtimo 12, saving a configuration of the standard Notificaties API plugin registers its abonnement right away.
Behind the gateway that fails, so such a configuration can't be created or changed there. **Verzoek** and
**Portaaltaak** need one: use an existing configuration of the standard plugin, or create it while Open Notificaties
can still reach Valtimo without the gateway. Their notificaties then arrive through the abonnement of this plugin.

---

## Configuring the plugin

{% stepper %}
{% step %}
Go to **Admin** > **Plugins**
{% endstep %}
{% step %}
Click **Configure plugin**, select **Notificaties API (manual abonnement)** and click **Enter data**
{% endstep %}
{% step %}
Fill in the properties and click **Save configuration**
{% endstep %}
{% endstepper %}

The labels below are the English ones. In Dutch the plugin is called **Notificaties API (handmatig abonnement)**;
**Registratie abonnement** is Abonnement registration (**Automatisch** / **Handmatig**), **Gedeeld geheim** is Shared
secret, **Authenticatie header** is Authentication header and **Configuratie authenticatie-plug-in** is Authentication
plugin configuration. The buttons are **Plugin configureren** (Configure plugin), **Gegevens invullen** (Enter data) and
**Configuratie opslaan** (Save configuration).

| Property | Description |
|----------|-------------|
| Configuration ID | Optional; leave it empty to have one generated. |
| Configuration name | The name under which the configuration can be found in the rest of the application. |
| Notificaties API URL | The URL of the REST API of Open Notificaties, for example `https://opennotificaties.example.com/api/v1/`. |
| Callback URL | The address where Open Notificaties sends notificaties. **Automatic**: the GZAC back-end address followed by `/api/v1/notificatiesapi-manual/callback`. **Manual**: the address through which Open Notificaties reaches Valtimo via the gateway, followed by `/api/v1/notificatiesapi-manual/callback/manual`. |
| Abonnement registration | **Automatic** or **Manual**. |
| Authentication header | Shown with **Automatic**. The secret that Valtimo places on the abonnement it registers. When left empty, the secret of the existing abonnement stays in use, or a random one is generated. A changed value is sent to the abonnement when saving. |
| Shared secret | Shown with **Manual**, and required. The secret that the administrator also enters on the abonnement in Open Notificaties. |
| Authentication plugin configuration | A configuration of the **OpenNotificaties** authentication plugin, which Valtimo uses to call Open Notificaties. |

With **Manual**, **Save configuration** stays disabled until a shared secret is entered. Saving is refused, with a
general error message, when:

- the secret starts with `Bearer` (in any case), starts or ends with a space, contains characters other than printable
  ASCII, or is longer than 255 characters;
- the callback URL ends in `/api/v1/notificatiesapi/callback` (the address of the standard plugin), or is the callback
  URL of a configuration of the standard plugin;
- a **Manual** and an **Automatic** configuration of this plugin would get the same callback URL. That is why the two
  have their own address: `.../callback` and `.../callback/manual`.

The form removes spaces around the secret and stops at 255 characters; the other rules are checked when saving.

{% hint style="warning" %}
These rules also apply to configurations in a `.pluginconfig.json` file. On Valtimo 12, a configuration that breaks
one of them is not deployed, and the configurations after it in the same file are skipped; the application log names
the error.
{% endhint %}

---

## Registering the abonnement manually

### Setting the configuration to Manual

{% stepper %}
{% step %}
Open a new or existing configuration of this plugin
{% endstep %}
{% step %}
Select **Manual** in **Abonnement registration**
{% endstep %}
{% step %}
Enter the **Callback URL** ending in `/api/v1/notificatiesapi-manual/callback/manual`
{% endstep %}
{% step %}
Enter a **Shared secret**: a long, random value of letters and digits
{% endstep %}
{% step %}
Click **Save configuration**
{% endstep %}
{% endstepper %}

### Viewing what the abonnement must contain

After a configuration is saved with **Manual**, its form shows what the abonnement in Open Notificaties must contain:
the callback URL and the channel `objecten`, without filters. That is the channel the standard Notificaties API plugin
registers on Valtimo 12; **Verzoek** and **Portaaltaak** process its notificaties. For a new configuration the form asks
to save it first.

### Creating the abonnement in Open Notificaties

In the admin of Open Notificaties:

{% stepper %}
{% step %}
Make sure channel `objecten` exists; create it under **Kanalen** in the admin of Open Notificaties if it's missing.
(With **Automatic** registration Valtimo creates it itself; with **Manual** it doesn't.)
{% endstep %}
{% step %}
Create an abonnement with the **Callback URL** shown in Valtimo
{% endstep %}
{% step %}
Enter the **Shared secret** of the configuration in the **Auth** field of the abonnement
{% endstep %}
{% step %}
Add channel `objecten`, without filters
{% endstep %}
{% endstepper %}

Valtimo accepts a notificatie on the callback addresses of this plugin when it carries the shared secret of a
**Manual** configuration, or the secret of an abonnement that Valtimo registered for an **Automatic** configuration of
this plugin. Other notificaties are refused (`401`, or `403` without a secret) and not processed.

---

## Things to keep in mind

{% hint style="warning" %}
**Switching a configuration to Manual.** The abonnement that Valtimo registered earlier stays in Open Notificaties and
keeps sending notificaties until an administrator removes it. If an Authentication header was set, the **Shared
secret** field takes it over: keep it to go on accepting the old abonnement's notificaties (each is processed once), or
change it to refuse them. If the header was left empty, the field is empty too, and the old abonnement's notificaties
are refused (`401`) whatever you enter. Remove that abonnement in Open Notificaties.
{% endhint %}

{% hint style="warning" %}
**Delivery can happen twice.** When processing a notificatie fails in one of the receiving plugins or process links,
Valtimo retries it for all of them. Process logic that reacts to notificaties must handle a second delivery.
{% endhint %}

{% hint style="info" %}
**Automatic registration.** Each **Automatic** configuration registers its own abonnement on channel `objecten` when it
is saved. When saving fails because Open Notificaties can't be reached, the configuration is not saved. When the Open
Notificaties URL of a configuration is changed, its old abonnement stays in the old Open Notificaties.
{% endhint %}

{% hint style="info" %}
Deleting a configuration does not remove a manually created abonnement from Open Notificaties. When Valtimo cannot
remove an automatically registered abonnement, it logs its address; remove it in Open Notificaties. Switching a
configuration to **Manual** and back to **Automatic** registers a new abonnement. A configuration whose authentication plugin configuration has been deleted can't
be deleted any more (as with the standard plugin on Valtimo 12): delete it before its authentication configuration.
{% endhint %}

{% hint style="info" %}
The shared secret is stored and exported as a plain value. A plugin configuration export therefore contains it.
{% endhint %}

---

## Moving to the standard plugin

Valtimo 12 has no manual abonnement registration in the standard plugin. When upgrading to Valtimo `13.52.0` or later,
move to the standard plugin as described in the documentation of version `1.0.0`. Before that, upgrading to an earlier
Valtimo 13 version means switching to version `1.0.0` of this plugin.
