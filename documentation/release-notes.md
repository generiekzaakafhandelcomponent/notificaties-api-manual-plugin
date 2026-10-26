# Release notes

Changes per version.

## 1.0.0

For Valtimo 13.41.0 and later 13.x versions.

- Notificaties API plugin under key `notificatiesapi-manual`: a copy of the standard Notificaties API plugin with
  manual abonnement registration (gzac-issues#971). A configuration registers its abonnement automatically, or an
  administrator creates it in Open Notificaties (manual); the configuration form then shows the callback URL and the
  channels with filters the abonnement must contain.
- Accepted notificaties are processed like those of the standard plugin (Verzoek, Portaaltaak, process links).
- Actions **Publish a notification** and **Receive a notification**.
