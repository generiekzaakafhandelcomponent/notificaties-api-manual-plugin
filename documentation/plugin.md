# 🔌 Notificaties API, manual abonnement

{% hint style="success" %}
For Valtimo `13.41.0` and later 13.x versions. The plugin is a ZGW plugin and can only be used in the GZAC edition.
From Valtimo `13.52.0`, the standard Notificaties API plugin offers manual abonnement registration itself; see
[Moving to the standard plugin](#moving-to-the-standard-plugin).
{% endhint %}

Use this plugin when all traffic between systems goes through a gateway that checks certificates. The standard
Notificaties API plugin registers its abonnement (subscription) in Open Notificaties itself. Open Notificaties then
first sends a test message to Valtimo, which such a gateway refuses, so the registration fails.

A configuration of this plugin tells Valtimo: an administrator creates the abonnement in this Open Notificaties.
Everything else stays with the standard Notificaties API plugin. **Verzoek**, **Portaaltaak** and **Receive a
notification** process links are linked to a configuration of the standard plugin as usual, and notificaties arrive on
its callback address. For the Open Notificaties of this plugin's configuration, Valtimo only no longer creates, changes
or removes anything in Open Notificaties: no abonnement and no channels.

{% hint style="info" %}
A developer adds the plugin to the application first. See [Installation and technical details](technical-details.md).
{% endhint %}

---

## Overview

| Step | Where | What |
|------|-------|------|
| 1 | **Admin** > **Plugins** | A configuration of this plugin with the URL of Open Notificaties. |
| 2 | **Admin** > **Plugins** | A configuration of the standard **Notificaties API** plugin with **exactly the same URL** and an **Authentication header**. |
| 3 | Verzoek, Portaaltaak, process links | Linked to the configuration of step 2. |
| 4 | Open Notificaties | An abonnement with the callback URL and Authentication header of step 2, and the channels the form of step 1 shows. |

{% hint style="danger" %}
**Configure this plugin first, and the standard plugin after it.** In the other order Valtimo tries to register the
abonnement of the standard plugin itself as soon as it is saved, which fails behind the gateway. On Valtimo `13.41.0`
up to `13.41.0-4` and on `13.42.x`, such a failing registration even stops the application from starting. The form of
this plugin says so too.
{% endhint %}

---

## 1. Configuring this plugin

{% stepper %}
{% step %}
Go to **Admin** > **Plugins**

<figure><img src="assets/plugins/notificaties-api-manual/01-plugins.png" alt=""><figcaption>Plugins overview</figcaption></figure>
{% endstep %}
{% step %}
Click **Configure plugin**, select **Notificaties API, manual abonnement** and click **Enter data**
{% endstep %}
{% step %}
Fill in the properties and click **Save configuration**
{% endstep %}
{% endstepper %}

| Property | Description |
|----------|-------------|
| Configuration ID | Optional; leave it empty to have one generated. |
| Configuration name | The name under which the configuration can be found in the rest of the application. |
| Notificaties API URL | The address of Open Notificaties, for example `https://opennotificaties.example.com/api/v1/`. Use the same URL as the configuration of the standard plugin; a trailing `/` doesn't matter. |

The form starts with a notice that the standard Notificaties API plugin is configured after this one.

---

## 2. Configuring the standard Notificaties API plugin

Create a configuration of the standard **Notificaties API** plugin:

| Property | Value |
|----------|-------|
| Notificaties API URL | Exactly the URL of step 1. |
| Callback URL | The address through which Open Notificaties reaches Valtimo via the gateway, followed by `/api/v1/notificatiesapi/callback`. Valtimo doesn't call it; it is what the abonnement must contain. |
| Authentication header | A long, random value of letters and digits. This is the secret of the abonnement: Valtimo only accepts notificaties that carry it. Don't leave it empty: Valtimo then picks a secret that you can't see. |
| Authentication plugin configuration | A configuration of the **OpenNotificaties** authentication plugin, which Valtimo uses to publish notificaties. |

Use one configuration of the standard plugin per Open Notificaties URL: Valtimo accepts the Authentication header of
only one of them.

---

## 3. Linking Verzoek, Portaaltaak and process links

Link **Verzoek**, **Portaaltaak** and the **Publish a notification** and **Receive a notification** process links to
the configuration of step 2, as without a gateway.

---

## 4. Creating the abonnement in Open Notificaties

Open the configuration of step 1 again. Its form now shows:

- **Step 2. Standard Notificaties API plugin**: the configurations of the standard plugin with the same URL, and a
  warning when there is none yet, when there is more than one, or when its Authentication header is empty;
- **Abonnement in Open Notificaties**: the callback URL of that configuration, and every channel with its filters.

The channels are those of **Receive a notification** process links of the configuration of step 2, and of plugins
linked to it, such as **Verzoek** and **Portaaltaak**. The list changes when such a process link or plugin
configuration is added, changed or removed. Update the abonnement in Open Notificaties to match.

In the admin of Open Notificaties:

{% stepper %}
{% step %}
Make sure every channel in the list exists. Create a missing channel under **Kanalen**.
{% endstep %}
{% step %}
Create an abonnement with the **Callback URL** of the configuration of step 2
{% endstep %}
{% step %}
Enter the **Authentication header** of the configuration of step 2 in the **Auth** field of the abonnement
{% endstep %}
{% step %}
Add every channel in the list, with exactly the filters shown for it. A line such as `actie = create` becomes a filter
with key `actie` and value `create`.
{% endstep %}
{% endstepper %}

The gateway must forward `/api/v1/notificatiesapi/callback` to Valtimo.

<details>
<summary>Dutch labels</summary>

| English | Dutch |
|---------|-------|
| Notificaties API, manual abonnement | Notificaties API, handmatig abonnement |
| Configure plugin | Plugin configureren |
| Enter data | Gegevens invullen |
| Save configuration | Configuratie opslaan |
| Authentication header (restart server required) | Authenticatie header (herstart server vereist) |

The standard plugin's form says a restart is needed after changing the Authentication header. For a URL of this
plugin that isn't needed: Valtimo takes over the new value when the configuration is saved.

</details>

---

## Things to keep in mind

{% hint style="warning" %}
**An abonnement that the standard plugin registered earlier** for this Open Notificaties stays there. Remove it in Open
Notificaties.
{% endhint %}

{% hint style="warning" %}
**Deleting this plugin's configuration, or changing its URL,** makes Valtimo register the abonnement of the standard
plugin in that Open Notificaties again, which fails behind the gateway. Delete or change the configuration of the
standard plugin first.
{% endhint %}

{% hint style="warning" %}
**A notificatie can arrive twice.** When processing a notificatie fails somewhere, Valtimo tries again for every
process and plugin that receives it. Processes that react to notificaties must be able to handle the same notificatie
twice.
{% endhint %}

{% hint style="info" %}
**Deleting the configuration of the standard plugin** does not remove the abonnement from Open Notificaties. Remove it
there.
{% endhint %}

---

## Moving to the standard plugin

From Valtimo `13.52.0` the standard Notificaties API plugin has manual abonnement registration itself. To move:

{% stepper %}
{% step %}
Set **Abonnement registration** of the configuration of the standard plugin to **Manual**, keeping the same secret
{% endstep %}
{% step %}
Check that the configuration of the standard plugin shows the same callback URL and channels as the abonnement in Open
Notificaties
{% endstep %}
{% step %}
Delete the configuration of this plugin, then ask a developer to remove the plugin from the application
{% endstep %}
{% endstepper %}

The abonnement already uses the callback address and secret of the standard plugin, so it can usually stay as it is.
