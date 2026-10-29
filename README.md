# Notificaties API plugin with manual abonnement registration

> [!IMPORTANT]
> This plugin is replaced by [valtimo-platform/valtimo#1155](https://github.com/valtimo-platform/valtimo/pull/1155),
> which adds manual abonnement registration to the standard Notificaties API plugin. Use the standard plugin once
> your Valtimo version includes that change.

A GZAC plugin that receives notificaties from Open Notificaties through an abonnement that an administrator creates,
for environments where all traffic between systems goes through a gateway that checks certificates. It is a copy of
the standard Notificaties API plugin under its own key (`notificatiesapi-manual`), with manual abonnement registration
([gzac-issues#971](https://github.com/generiekzaakafhandelcomponent/gzac-issues/issues/971)) added. Installing it
changes nothing for the standard plugin.

| Branch | Valtimo | Version |
|--------|---------|---------|
| `main` | 13.41.0 and later 13.x | `1.0.0` |
| `v12`  | 12.45.0 and later 12.x | `1.0.0-V12` |

From Valtimo 13.52.0 the standard Notificaties API plugin has manual abonnement registration itself
([valtimo#1155](https://github.com/valtimo-platform/valtimo/pull/1155)); move to it when you upgrade.

## Documentation

- [Plugin](documentation/plugin.md) — configuration in the user interface and Open Notificaties steps, for administrators
- [Installation and technical details](documentation/technical-details.md) — installation, application properties, callback behaviour, for developers
- [Example application](documentation/example-application.md) — running the sandbox application locally
- [Release notes](documentation/release-notes.md)

## Development

- Backend: `./gradlew :backend:plugin:test` (needs Docker). The jar is compiled against the minimum supported Valtimo
  version (`valtimoVersion` in `gradle.properties`); CI also tests against the latest.
- Frontend: in `frontend/`, `npm install`, `npx ng build @valtimo-plugins/notificaties-api-manual`,
  `npx ng test @valtimo-plugins/notificaties-api-manual --watch=false --browsers=ChromeHeadless`.

## Contact

Team Valtimo (Ritense)
