# Release notes

## 2.0.0

For Valtimo 13.41.0 and later 13.x versions.

---

## Breaking changes

### The abonnement belongs to the standard plugin

A configuration of this plugin now only has a **Notificaties API URL**. It tells Valtimo that an administrator creates
the abonnement in that Open Notificaties, and the standard Notificaties API plugin does everything else:

- **Verzoek**, **Portaaltaak** and process links are linked to a configuration of the standard plugin with the same
  URL;
- notificaties arrive on the callback address of the standard plugin (`/api/v1/notificatiesapi/callback`), with its
  **Authentication header** as secret;
- for that Open Notificaties, the standard plugin creates, changes and removes nothing: no abonnement and no channels.
  It keeps registering abonnementen in other Open Notificaties, so `valtimo.zgw.register-abonnementen: false` is no
  longer needed.

Before, Verzoek and Portaaltaak needed a configuration of both plugins with the same values, and their notificaties
arrived through this plugin's own callback.

The form of this plugin starts with a notice that the standard plugin is configured after it, and then shows the
configuration of the standard plugin with the same URL (with a warning when there is none, more than one, or one
without Authentication header), and the callback URL and channels the abonnement must contain.

Removed: the callback address `/api/v1/notificatiesapi-manual/callback`, **Automatic** registration, the shared secret,
the **Publish a notification** and **Receive a notification** actions, the warning for notificaties on the wrong
address, the `valtimo.notificaties-api-manual.*` properties and the plugin's database tables. See
**Upgrading from 1.0.x** in [Installation and technical details](technical-details.md).

**[Try it out →](plugin.md)**

---

## 1.0.0

For Valtimo 13.41.0 and later 13.x versions.

---

## New Features

### Manual abonnement registration

Receive notificaties from Open Notificaties when all traffic goes through a gateway that checks certificates. Select
**Manual** in **Abonnement registration**, enter a shared secret, and create the abonnement in Open Notificaties
yourself. The configuration form shows the callback URL and the channels with filters the abonnement must contain.

Notificaties are processed like those of the standard Notificaties API plugin: **Verzoek**, **Portaaltaak** and
**Receive a notification** process links all receive them. The actions **Publish a notification** and **Receive a
notification** are available as well.

**[Try it out →](plugin.md)**
