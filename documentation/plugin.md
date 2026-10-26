# Notificaties API (manual abonnement)

{% hint style="success" %}
For Valtimo `13.41.0` and later 13.x versions, GZAC edition. From Valtimo `13.52.0`, the standard Notificaties API
plugin offers manual abonnement registration itself; see [Moving to the standard plugin](#moving-to-the-standard-plugin).
{% endhint %}

The Notificaties API (manual abonnement) plugin connects Valtimo to Open Notificaties, like the standard Notificaties
API plugin, and adds one option: an administrator can create the abonnement (subscription) in Open Notificaties
instead of Valtimo. Use it when all traffic between systems goes through a gateway that checks certificates. During
automatic registration Open Notificaties sends a test message to Valtimo without a certificate; such a gateway refuses
it, so automatic registration fails.

The plugin has its own plugin key (`notificatiesapi-manual`), callback address and database table. Installing it
changes nothing for the standard Notificaties API plugin. Notificaties it receives are processed exactly like those of
the standard plugin: **Verzoek**, **Portaaltaak**, **Receive a notification** process links and other plugins all
receive them. A notificatie that arrives through both plugins is processed once.

Each configuration chooses how its abonnement is registered:

| Abonnement registration | Who creates the abonnement | When to use |
|-------------------------|----------------------------|-------------|
| **Automatic** | Valtimo registers, updates and removes the abonnement and the channels it needs, in the background after saving. If Open Notificaties can't reach the callback URL, the registration fails and the application log says so. | Open Notificaties can reach Valtimo directly. |
| **Manual** | An administrator creates the abonnement in the Open Notificaties admin. Valtimo creates, changes and removes nothing in Open Notificaties for this configuration. | Traffic goes through a gateway that checks certificates. |

---

## Installation

A developer adds the plugin to the GZAC application.

**Backend** (`build.gradle.kts`):

```kotlin
dependencies {
    implementation("com.ritense.valtimoplugins:notificaties-api-manual:1.0.0")
}
```

**Frontend** (`package.json`):

```json
{
  "dependencies": {
    "@valtimo-plugins/notificaties-api-manual": "1.0.0"
  }
}
```

In `app.module.ts`:

```typescript
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

### Settings of the standard plugin

If the standard Notificaties API plugin has a configuration whose callback address is only reachable through the
gateway, its own automatic registration fails too. Turn it off in the application properties:

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

The setting applies to all configurations of the standard plugin. It does not affect this plugin, which has its own
setting:

| Property | Default | Description |
|----------|---------|-------------|
| `valtimo.notificaties-api-manual.register-abonnementen` | `true` | `false` turns off the automatic registration of this plugin's configurations. Manual configurations never register anything. |
| `valtimo.notificaties-api-manual.abonnement-registration.initial-backoff` | `2s` | First wait before retrying the registration at startup. |
| `valtimo.notificaties-api-manual.abonnement-registration.max-backoff` | `30s` | Longest wait between retries. |
| `valtimo.notificaties-api-manual.abonnement-registration.max-duration` | `15m` | After this time the startup registration stops retrying. |

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

Saving is refused, with a general error message, when:

- **Manual** is chosen without a shared secret;
- the secret starts with `Bearer` (in any case), starts or ends with a space, contains characters other than printable
  ASCII, or is longer than 255 characters;
- the callback URL ends in `/api/v1/notificatiesapi/callback` (the address of the standard plugin), or is the callback
  URL of a configuration of the standard plugin;
- a **Manual** and an **Automatic** configuration of this plugin would get the same callback URL. That is why the two
  have their own address: `.../callback` and `.../callback/manual`.

The form removes spaces around the secret and stops at 255 characters; the other rules are checked when saving.

{% hint style="warning" %}
These rules also apply to configurations in a `.pluginconfig.json` file. A file that breaks one of them stops the
application from starting.
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
the callback URL, and every channel with its filters. For a new configuration the form asks to save it first.

The list contains:

- the channels and filters of **Receive a notification** process links that use this configuration;
- the channels and filters of **Receive a notification** process links of the standard Notificaties API plugin whose
  configuration has the same Open Notificaties URL;
- the channels and filters of other plugins that point to a standard Notificaties API configuration with the same Open
  Notificaties URL, such as **Verzoek** and **Portaaltaak**.

Those plugins can only point to the standard Notificaties API plugin. Behind the gateway their notificaties arrive
through the abonnement of this plugin, which is why they are listed here.

If nothing needs a channel yet, the form says so; the abonnement then only needs the callback URL and the shared
secret. The list changes when such a process link or plugin configuration is added, changed or removed; update the abonnement
in Open Notificaties to match. **Receive a notification** process links in building blocks don't work, neither with this
plugin nor with the standard plugin.

### Creating the abonnement in Open Notificaties

In the admin of Open Notificaties:

{% stepper %}
{% step %}
Make sure every channel in the list exists; create a missing channel under **Kanalen** in the admin of Open
Notificaties. (With **Automatic** registration Valtimo creates missing channels itself; with **Manual** it doesn't.)
{% endstep %}
{% step %}
Create an abonnement with the **Callback URL** shown in Valtimo
{% endstep %}
{% step %}
Enter the **Shared secret** of the configuration in the **Auth** field of the abonnement
{% endstep %}
{% step %}
Add an entry for every channel in the list, with exactly the filters shown for it: each line `key = value` becomes a
filter with that key (for example `actie`) and value (for example `create`). These are the filters Valtimo itself uses
for **Automatic** registration.
{% endstep %}
{% endstepper %}

Valtimo accepts a notificatie on the callback addresses of this plugin when it carries the shared secret of a
**Manual** configuration, or the secret of an abonnement that Valtimo registered for an **Automatic** configuration of
this plugin. Other notificaties are refused (`401`, or `403` without a secret) and not processed.

---

## Process actions

The plugin offers the same actions as the standard Notificaties API plugin:

| Action | Description |
|--------|-------------|
| **Publish a notification** | Publishes a notificatie in Open Notificaties. |
| **Receive a notification** | Continues a receive task, catch event or message start event when a matching notificatie arrives. All filters are optional. |

The variables `notificatieKanaal`, `notificatieResourceUrl`, `notificatieActie`, `notificatieKenmerken`,
`notificatieHoofdObject` and `notificatieAanmaakdatum` are set on the process.

---

## Things to keep in mind

{% hint style="warning" %}
**Switching a configuration to Manual.** The abonnement that Valtimo registered earlier stays in Open Notificaties and
keeps sending notificaties until an administrator removes it. The **Shared secret** field takes over the current
Authentication header: keep it to go on accepting the old abonnement's notificaties (each is processed once), or
change it to refuse them.
{% endhint %}

{% hint style="warning" %}
**Delivery can happen twice.** When processing a notificatie fails in one of the receiving plugins or process links,
Valtimo retries it for all of them. Process logic that reacts to notificaties must handle a second delivery.
{% endhint %}

{% hint style="info" %}
**Automatic registration.** One abonnement is registered per Open Notificaties URL. Only the secret of the
configuration that holds it is used. When the Open Notificaties URL of a configuration is changed, its old abonnement
stays in the old Open Notificaties. Start one instance of the application first when several start at the same time
with a new **Automatic** configuration. Process links copied with a BPMN-only redeployment reach the abonnement at the
next change or restart.
{% endhint %}

{% hint style="info" %}
Deleting a configuration does not remove a manually created abonnement from Open Notificaties. When Valtimo cannot
remove an automatically registered abonnement, it logs its address; remove it in Open Notificaties.
{% endhint %}

{% hint style="info" %}
The shared secret is stored and exported as a plain value. A plugin configuration export therefore contains it.
{% endhint %}

---

## Moving to the standard plugin

From Valtimo `13.52.0` the standard Notificaties API plugin has manual abonnement registration itself. To move:

{% stepper %}
{% step %}
Create a configuration of the standard plugin with **Abonnement registration** set to **Manual** and the same shared
secret
{% endstep %}
{% step %}
Change the callback URL of the abonnement in Open Notificaties to the address of the standard plugin
(`/api/v1/notificatiesapi/callback`)
{% endstep %}
{% step %}
Link the **Publish a notification** and **Receive a notification** process links to the new configuration
{% endstep %}
{% step %}
Delete the configuration of this plugin, then remove the plugin from the application
{% endstep %}
{% endstepper %}
