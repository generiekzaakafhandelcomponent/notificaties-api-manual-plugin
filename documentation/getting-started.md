# Getting Started

For installing the plugin in a GZAC application, see [Installation and technical details](technical-details.md); for
configuring it, see [Plugin](plugin.md).

For working on the plugin:

1. Backend: `./gradlew :backend:plugin:test` runs the tests (needs Docker for PostgreSQL).
2. Frontend: in `frontend/`, run `npm install`, then `npx ng build @valtimo-plugins/notificaties-api-manual` and
   `npx ng test @valtimo-plugins/notificaties-api-manual --watch=false --browsers=ChromeHeadless`.
3. Try it in the [example application](example-application.md).

The plugin is compiled against the minimum supported Valtimo version (`valtimoVersion` in `gradle.properties`); don't
raise it to the latest. CI also runs the backend tests against the latest version.

For more information on building plugins, see
the [Custom Plugin Definition](https://docs.valtimo.nl/features/plugins/plugins/custom-plugin-definition) documentation.
