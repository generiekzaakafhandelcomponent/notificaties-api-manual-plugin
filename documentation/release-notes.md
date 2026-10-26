# Release notes

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
