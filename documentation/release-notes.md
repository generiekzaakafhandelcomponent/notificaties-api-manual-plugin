# Release notes

## 1.0.1

For Valtimo 13.41.0 and later 13.x versions.

---

## Improvements

### Callback URL optional with manual registration

With **Manual** abonnement registration, the **Callback URL** no longer has to be filled in: the administrator enters
it in the abonnement in Open Notificaties, and Valtimo doesn't use it. When it is filled in, the configuration form
shows it with the abonnement; otherwise the form shows how it is made up. With **Automatic** registration it stays
required.

**Abonnement registration** now comes before **Callback URL** in the configuration form, so the form shows whether the
callback URL is required.

When a **Manual** configuration has no callback URL, **Automatic** configurations no longer delete abonnementen in Open
Notificaties that Valtimo didn't register, because the administrator's abonnement can't be told apart from a leftover
one. Remove leftover abonnementen in Open Notificaties yourself.

### Warning for notificaties on the address of the standard plugin

An abonnement or gateway route that points to `/api/v1/notificatiesapi/callback`, the address of the standard
Notificaties API plugin, is accepted by Open Notificaties, but every notificatie is then refused and lost. The form of
a **Manual** configuration now warns against this address, and shows a warning with the time of the last notificatie
when one with its shared secret arrived there. Valtimo also logs a warning. The warning disappears once a notificatie
arrives on `/api/v1/notificatiesapi-manual/callback`.

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
