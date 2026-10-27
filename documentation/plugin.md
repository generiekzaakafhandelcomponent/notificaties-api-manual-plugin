# 🔌 Notificaties API (manual abonnement)

{% hint style="success" %}
For Valtimo `12.45.0` and later 12.x versions (plugin version `1.0.0-V12`); on Valtimo 13, use version `1.0.0`. The
Notificaties API (manual abonnement) is a ZGW plugin and can only be used in the GZAC edition.
{% endhint %}

The Notificaties API (manual abonnement) plugin connects Valtimo to Open Notificaties, like the standard Notificaties
API plugin, and adds one option: an administrator can create the abonnement (subscription) in Open Notificaties
instead of Valtimo.

Use this option when all traffic between systems goes through a gateway that checks certificates. When Valtimo
registers an abonnement itself, Open Notificaties first sends a test message to Valtimo. Such a gateway refuses that
message, so the registration fails.

The plugin works next to the standard Notificaties API plugin and changes nothing for it. Notificaties it receives are
processed exactly like those of the standard plugin: **Verzoek** and **Portaaltaak** receive them. A notificatie that
arrives through both plugins is processed once.

Each plugin configuration chooses how its abonnement is registered:

| Abonnement registration | Who creates the abonnement | When to use |
|-------------------------|----------------------------|-------------|
| **Automatic** | Valtimo creates, updates and removes the abonnement and the channel it needs when the configuration is saved. If Open Notificaties can't reach Valtimo, saving fails. | Open Notificaties can reach Valtimo directly. |
| **Manual** | An administrator creates the abonnement in Open Notificaties. Valtimo creates, changes and removes nothing in Open Notificaties for this configuration. | Traffic goes through a gateway that checks certificates. |

{% hint style="info" %}
A developer adds the plugin to the application first. See [Installation and technical details](technical-details.md).
{% endhint %}

---

## Configuring the plugin

{% stepper %}
{% step %}
Go to **Admin** > **Plugins**

<figure><img src="assets/plugins/notificaties-api-manual/01-plugins.png" alt=""><figcaption>Plugins overview</figcaption></figure>
{% endstep %}
{% step %}
Click **Configure plugin**, select **Notificaties API (manual abonnement)** and click **Enter data**

<figure><img src="assets/plugins/notificaties-api-manual/02-choose-plugin.png" alt=""><figcaption>Choosing the plugin</figcaption></figure>
{% endstep %}
{% step %}
Fill in the properties and click **Save configuration**

<figure><img src="assets/plugins/notificaties-api-manual/03-automatic.png" alt=""><figcaption>Configuration with automatic registration</figcaption></figure>
{% endstep %}
{% endstepper %}

| Property | Description |
|----------|-------------|
| Configuration ID | Optional; leave it empty to have one generated. |
| Configuration name | The name under which the configuration can be found in the rest of the application. |
| Notificaties API URL | The address of Open Notificaties, for example `https://opennotificaties.example.com/api/v1/`. |
| Callback URL | The address where Open Notificaties sends notificaties: the address through which Open Notificaties reaches Valtimo, followed by `/api/v1/notificatiesapi-manual/callback`. With **Manual**, that is usually the address of the gateway. |
| Abonnement registration | **Automatic** (the default) or **Manual**. |
| Authentication header | Shown with **Automatic**. The secret that Valtimo places on the abonnement it registers. When left empty, the secret of the existing abonnement stays in use, or a random one is generated. |
| Shared secret | Shown with **Manual**, and required. The secret that the administrator also enters on the abonnement in Open Notificaties. |
| Authentication plugin configuration | A configuration of the **OpenNotificaties** authentication plugin, which Valtimo uses to connect to Open Notificaties. |

Saving shows a general error message when:

- the shared secret starts with the word `Bearer`, or contains special characters such as accented letters;
- the callback URL is the address of the standard Notificaties API plugin (ending in `/api/v1/notificatiesapi/callback`),
  or the callback URL of one of its configurations.

<details>
<summary>Dutch labels</summary>

| English | Dutch |
|---------|-------|
| Notificaties API (manual abonnement) | Notificaties API (handmatig abonnement) |
| Configure plugin | Plugin configureren |
| Enter data | Gegevens invullen |
| Save configuration | Configuratie opslaan |
| Abonnement registration | Registratie abonnement |
| Automatic / Manual | Automatisch / Handmatig |
| Authentication header | Authenticatie header |
| Shared secret | Gedeeld geheim |
| Authentication plugin configuration | Configuratie authenticatie-plug-in |

</details>

### Standard plugin configurations behind the gateway

On Valtimo 12, saving a configuration of the standard Notificaties API plugin registers its abonnement right away.
Behind the gateway that fails, so such a configuration can't be created or changed there. **Verzoek** and
**Portaaltaak** need one: use an existing configuration of the standard plugin, or create it while Open Notificaties
can still reach Valtimo without the gateway. Their notificaties then arrive through the abonnement of this plugin.

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
Enter the **Callback URL**: the address through which Open Notificaties reaches Valtimo via the gateway, followed by
`/api/v1/notificatiesapi-manual/callback`
{% endstep %}
{% step %}
Enter a **Shared secret**: a long, random value of letters and digits
{% endstep %}
{% step %}
Click **Save configuration**
{% endstep %}
{% endstepper %}

An example configuration with manual registration:

<figure><img src="assets/plugins/notificaties-api-manual/04-manual.png" alt=""><figcaption>Configuration with manual registration</figcaption></figure>

### Viewing what the abonnement must contain

After a configuration is saved with **Manual**, its form shows what the abonnement in Open Notificaties must contain:
the callback URL and the channel `objecten`, without filters. That is the channel that **Verzoek** and **Portaaltaak**
use. For a new configuration the form asks to save it first.

<figure><img src="assets/plugins/notificaties-api-manual/05-abonnement.png" alt=""><figcaption>What the abonnement must contain</figcaption></figure>

### Creating the abonnement in Open Notificaties

In the admin of Open Notificaties:

{% stepper %}
{% step %}
Make sure the channel `objecten` exists. Create it under **Kanalen** if it's missing.
{% endstep %}
{% step %}
Create an abonnement with the **Callback URL** shown in Valtimo
{% endstep %}
{% step %}
Enter the **Shared secret** of the configuration in the **Auth** field of the abonnement
{% endstep %}
{% step %}
Add the channel `objecten`, without filters
{% endstep %}
{% endstepper %}

Valtimo only processes notificaties that carry the shared secret of this plugin. Other notificaties are refused.

---

## Things to keep in mind

{% hint style="warning" %}
**Switching a configuration to Manual.** The abonnement that Valtimo registered earlier stays in Open Notificaties and
keeps sending notificaties until an administrator removes it there. If an **Authentication header** was filled in, the
**Shared secret** field takes it over. Keep it to go on accepting the old abonnement's notificaties, or change it to
refuse them. Remove the old abonnement in Open Notificaties.
{% endhint %}

{% hint style="info" %}
**Switching back to Automatic** registers a new abonnement. Remove the manually created abonnement in Open
Notificaties.
{% endhint %}

{% hint style="warning" %}
**A notificatie can arrive twice.** When processing a notificatie fails somewhere, Valtimo tries again for every
plugin that receives it.
{% endhint %}

{% hint style="info" %}
**Deleting a configuration** does not remove a manually created abonnement from Open Notificaties. Remove it there.
The same applies to an abonnement that Valtimo registered when the **Notificaties API URL** of a configuration is
changed: the old abonnement stays in the old Open Notificaties.
{% endhint %}

{% hint style="warning" %}
Delete a configuration of this plugin before its **Authentication plugin configuration**. Once the authentication
plugin configuration has been deleted, the configuration of this plugin can't be deleted any more. The standard plugin
behaves the same on Valtimo 12.
{% endhint %}

{% hint style="info" %}
The shared secret is included as readable text when plugin configurations are exported.
{% endhint %}

---

## Moving to the standard plugin

The standard Notificaties API plugin of Valtimo 12 has no manual abonnement registration. When upgrading to Valtimo
`13.52.0` or later, move to the standard plugin as described in the documentation of version `1.0.0`. When upgrading
to an earlier Valtimo 13 version, a developer switches to version `1.0.0` of this plugin.
