# Notificaties API plugin with manual abonnement registration

A GZAC plugin for environments where all traffic between systems goes through a gateway that checks certificates
([gzac-issues#971](https://github.com/generiekzaakafhandelcomponent/gzac-issues/issues/971)). A configuration of it,
with only an Open Notificaties URL, tells Valtimo that an administrator creates the abonnement in that Open
Notificaties. The standard Notificaties API plugin then registers nothing there, but works as usual otherwise:
Verzoek, Portaaltaak and process links are linked to it, and notificaties arrive on its callback address.

Configure this plugin first, then a configuration of the standard plugin with the same URL and an Authentication
header; see [Plugin](documentation/plugin.md).

| Branch | Valtimo | Version |
|--------|---------|---------|
| `main` | 13.41.0 and later 13.x | `2.0.0` |
| `v12`  | 12.45.0 and later 12.x | `1.0.0-V12` |

From Valtimo 13.52.0 the standard Notificaties API plugin has manual abonnement registration itself; move to it when
you upgrade.

## Documentation

- [Plugin](documentation/plugin.md) — configuration in the user interface and Open Notificaties steps, for administrators
- [Installation and technical details](documentation/technical-details.md) — installation, how it works, upgrading from 1.0.x, for developers
- [Example application](documentation/example-application.md) — running the sandbox application locally
- [Release notes](documentation/release-notes.md)

## Development

- Backend: `./gradlew :backend:plugin:test` (needs Docker). The jar is compiled against the minimum supported Valtimo
  version (`valtimoVersion` in `gradle.properties`); CI also tests against the latest.
- Frontend: in `frontend/`, `npm install`, `npx ng build @valtimo-plugins/notificaties-api-manual`,
  `npx ng test @valtimo-plugins/notificaties-api-manual --watch=false --browsers=ChromeHeadless`.

## Contact

Team Valtimo (Ritense)
