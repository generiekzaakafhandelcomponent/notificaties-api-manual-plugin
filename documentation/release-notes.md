# Release notes

Changes per version.

## 1.0.0-V12

For Valtimo 12.45.0 and later 12.x versions.

- Notificaties API plugin under key `notificatiesapi-manual`: a copy of the standard Valtimo 12 Notificaties API plugin
  with manual abonnement registration (gzac-issues#971). A configuration registers its abonnement (channel `objecten`)
  automatically, or an administrator creates it in Open Notificaties (manual); the configuration form then shows the
  callback URL and the channel the abonnement must contain.
- Accepted notificaties are processed like those of the standard plugin (Verzoek, Portaaltaak).
