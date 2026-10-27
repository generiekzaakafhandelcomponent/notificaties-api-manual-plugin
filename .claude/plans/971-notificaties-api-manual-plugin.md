# 971 — Notificaties API plugin with manual abonnement registration (Valtimo 12 and 13)

Story: `valtimo/.claude/plans/971-notificaties-api-fork-plugin-story.md`. Core implementation of #971:
`valtimo-platform/valtimo` commit `f4f686d55e` (targets 13.52.0).

## Goal

A separately released copy of the core Notificaties API plugin, under its own plugin key, with #971 (manual
abonnement registration) added. Branch `main` serves Valtimo 13, branch `v12` serves Valtimo 12. Installing it changes
nothing until an administrator creates a configuration of it.

## Approach

Copy the core plugin, not the core module. The plugin depends on core `com.ritense.valtimo:notificaties-api`
(always present in a GZAC application) and reuses from it:

| Reused from core                                 | Why                                                                        |
|--------------------------------------------------|----------------------------------------------------------------------------|
| `NotificatiesApiClient` (bean)                   | Same HTTP calls to Open Notificaties                                       |
| `NotificatiesApiAuthentication`                  | Existing authentication plugins (category `notificaties-api-authentication`) keep working |
| `Abonnement`, `Kanaal`, `Notificatie`            | Request/response shapes                                                    |
| `NotificatiesApiNotificationReceivedEvent`       | Callback body, and the event every consumer listens to                     |
| `NotificatiesApiService.registerNotification`    | Core inbound queue: retries, idempotence key, then publishes the event     |
| `NotificatiesApiListener` (main only, read-only) | To list the kanaal filters of core listeners in the manual view            |

Everything else is copied under fork names (table below). Accepted notificaties go into core's inbound queue, so
Verzoek, Portaaltaak, core `receive-notificatie` links, external listeners and this plugin's own `receive-notificatie`
links all receive them through the one event. A notificatie that arrives through both core and this plugin has the same
idempotence key and is processed once.

### Names (must not clash with core)

| Thing                        | Core                                             | This plugin                                                         |
|------------------------------|--------------------------------------------------|---------------------------------------------------------------------|
| Plugin key                   | `notificatiesapi`                                | `notificatiesapi-manual`                                            |
| Plugin title                 | Notificaties API                                 | Notificaties API (handmatig abonnement)                             |
| Package                      | `com.ritense.notificatiesapi`                    | `com.ritense.valtimoplugins.notificatiesapimanual`                  |
| Plugin category              | `notificaties-api-plugin`                        | none (the plugin does not implement core `NotificatiesApiListener`) |
| Callback                     | `POST /api/v1/notificatiesapi/callback`          | `POST /api/v1/notificatiesapi-manual/callback` (Automatic and Manual) |
| Manual view (main + v12)     | `GET /api/management/v1/notificatiesapi/configurations/{id}/manual-abonnement` | `GET /api/management/v1/notificatiesapi-manual/configurations/{id}/manual-abonnement` |
| Link table / entity          | `notificaties_api_abonnement_link` / `NotificatiesApiAbonnementLink` | `notificaties_api_manual_abonnement_link` / `NotificatiesApiManualPluginAbonnementLink` (own embeddable id `NotificatiesApiManualPluginConfigurationId`) |
| Liquibase master             | (core)                                           | `config/liquibase/notificaties-api-manual-master.xml`               |
| Properties                   | `valtimo.zgw.register-abonnementen`, `valtimo.zgw.abonnement-registration.*` | `valtimo.notificaties-api-manual.register-abonnementen`, `valtimo.notificaties-api-manual.abonnement-registration.*` |
| Bean names / classes         | `notificatiesApi*`, `NotificatiesApi*`           | `notificatiesApiManualPlugin*`, `NotificatiesApiManualPlugin*`      |
| Frontend selectors / classes / test ids | `valtimo-notificaties-api-*`, `valtimo-publish-notificatie-configuration`, `PublishNotificatieConfigurationComponent`, `NOTIFICATIES_API_*` … | `valtimo-notificaties-api-manual-plugin-*`, `NotificatiesApiManualPlugin*` (also the action components and models), `NOTIFICATIES_API_MANUAL_PLUGIN_*` |
| Executor thread prefix       | `notificaties-api-abonnement-registration-`      | `notificaties-api-manual-plugin-abonnement-registration-`           |
| Maven / npm                  | —                                                | `com.ritense.valtimoplugins:notificaties-api-manual` / `@valtimo-plugins/notificaties-api-manual` |
| Versions                     | —                                                | `1.0.0` on main, `1.0.0-V12` on v12 (sibling repos use the `-V12` suffix) |

The `…ManualPlugin…` prefix (not `…Manual…`) is deliberate: core #971 (13.52) already has
`NotificatiesApiManualAbonnementService`, `NotificatiesApiManualAbonnementManagementResource`,
`NotificatiesApiManualAbonnementResponse`, beans `notificatiesApiManualAbonnement*`, selector
`valtimo-notificaties-api-manual-abonnement` and `NOTIFICATIES_API_MANUAL_ABONNEMENT_TEST_IDS`. GZAC apps run with
`allow-bean-definition-overriding: true`, so a same-named bean would silently replace core's. A unit test asserts that
none of this plugin's bean names is in the list of core notificaties-api bean names up to and including #971.

### Substitutions for the minimum versions

The #971 code uses APIs that 13.41.0 / 12.45.0 don't have:

- `@EndpointDescription` (13.47+): left out.
- `PluginService.findPluginConfiguration(PluginConfigurationId)` (13.51+): use
  `getPluginConfigurations(PluginConfigurationSearchParameters(pluginDefinitionKey = "notificatiesapi-manual"))` and
  match the id, so a missing id gives 404, not 500.
- v12 frontend: `InterceptorSkip` comes from `@valtimo/security` (not `@valtimo/shared`).

## Branch `main` (Valtimo 13)

Source to copy: core module at `f4f686d55e` (= `v13-stable` + #971; the module is identical on `origin/v13-stable`
apart from #971).

Backend:

1. `NotificatiesApiManualPlugin` (`@Plugin(key = "notificatiesapi-manual")`): properties `url`, `callbackUrl`,
   `authHeader` (optional; required for Manual), `authenticationPluginConfiguration: NotificatiesApiAuthentication`,
   `abonnementRegistration` (`automatic` default / `manual`). Actions `publish-notificatie` and `receive-notificatie`,
   same keys, properties and activity types as core.
2. `@PluginEvent(CREATE, UPDATE)` validation (one method; on v12 the same method then registers, see v12):
   - Manual requires a non-blank `authHeader` (as #971).
   - `authHeader` must not start with `bearer` in any case: Valtimo's `JwtFilter` (`Bearer `) and Spring's
     `DefaultBearerTokenResolver` (any case, when OAuth2 resource server is on) take such a header as a token and
     reject it before the callback runs. The #971 docs warn about this; here it is enforced.
   - `authHeader` without leading/trailing whitespace (HTTP strips it from header values, so it could never match).
   - `authHeader` only printable ASCII (Open Notificaties sends header values as Latin-1).
   - `authHeader` at most 255 characters (the link column; the form input has the same limit).
   - `callbackUrl` must not end in core's `/api/v1/notificatiesapi/callback` (core's endpoint would answer 401). Open Notificaties allows 1000; a longer key would create a
     remote abonnement and then fail saving the link row, creating another abonnement on every sweep.
   - `callbackUrl` must differ from the `callbackUrl` of every core `notificatiesapi` configuration, so that the
     stale-abonnement cleanup of either side can never match the other's abonnementen (both delete abonnementen on
     their Open Notificaties URL whose callback equals their own callback and that they don't track). Core doesn't check
     the other way round, so every sweep — also one with nothing to register, i.e. a Manual-only install — logs an
     error for each configuration of this plugin whose callback URL a core configuration uses (core would delete its
     abonnement).
   - A Manual and an Automatic configuration of this plugin may share a `callbackUrl` (one endpoint,
     `/api/v1/notificatiesapi-manual/callback`). Automatic stale cleanup can't tell an administrator's abonnement from a
     stale one, so it is skipped when a Manual configuration uses the same Open Notificaties URL or callback URL.
     (Earlier: a separate `…/callback/manual` path and a validation that the two differ; dropped as the cleanup guards
     already protect the manual abonnement and one URL is simpler for administrators.)
3. Automatic registration: copy of #971 `PluginsDeployedEventListener` + `NotificatiesApiKanaalFilters`, limited to
   configurations of `notificatiesapi-manual`
   (`getPluginConfigurations(PluginConfigurationSearchParameters(pluginDefinitionKey = "notificatiesapi-manual"))`).
   Kanaal filters per Open Notificaties URL group: this plugin's `receive-notificatie` links whose
   `pluginConfigurationId` is an Automatic configuration of that group; links with a null `pluginConfigurationId`
   (building blocks) would go into every Automatic group, but core's link query (an inner join on the plugin
   configuration) never returns them, so building-block `receive-notificatie` links work neither in core nor here. Deviation from #971,
   which adds every non-Manual link to every group. Same retry/backoff, same
   AFTER_COMMIT + REQUIRES_NEW handling, own executor and properties. Same #971 rules: Manual configurations are never
   registered/updated/deleted; Automatic stale cleanup deletes nothing on an Open Notificaties URL that also has a
   Manual configuration of this plugin. With no Automatic configuration of this plugin it makes zero calls to Open
   Notificaties.
   Triggers: application ready; plugin-configuration created/updated/deleted for definition key
   `notificatiesapi-manual` or `notificatiesapi` (the latter only to re-run the callback check below); plugin
   process link created/updated/deleted (the events carry no plugin key, so every plugin link triggers a sweep, as in
   core; `ProcessLinkDeletedEvent` is handled too, which core doesn't).
   Stale cleanup never deletes an abonnement whose `callbackUrl` equals the `callbackUrl` of any Manual configuration
   of this plugin or of any core `notificatiesapi` configuration, whatever their Open Notificaties URL (the same server
   can be reached under two URLs); when it skips one because of a core configuration it logs an error. Because Manual
   and Automatic callback URLs differ (item 2), a Manual configuration doesn't switch off cleanup for Automatic ones.
   Each URL group's abonnement belongs to one configuration (its link is keyed on it): the member that already has a
   link, else the lowest id. Core takes "the first", whose order can change between sweeps and orphan an abonnement.
   Other members of the group that still have a link (e.g. after a `url` change moved them into the group) get their
   abonnement deleted and their link removed; a failing remote delete is logged with the abonnement URL. Links saved
   during a sweep are added to its snapshot, so a later URL group never takes them for stale. Only the owner's
   `authHeader` is used for the group's abonnement (documented).
   Event-triggered sweeps run on the plugin's single registration thread, never on the request thread and never two at
   a time; events that arrive while a sweep is pending share it (a case import with N links gives one sweep, not N).
   Failures are logged, so an unreachable Open Notificaties never turns an unrelated save into a 500.
   The cleanup after a deleted configuration runs on that thread too, so a running sweep can't save a link for it
   afterwards. Every sweep also removes links of configurations that no longer exist (a cleanup lost on shutdown,
   an id change during a running sweep), after which the stale cleanup removes their abonnement.
   Changed `authHeader`: Open Notificaties never returns `auth` (write-only), so the "unchanged" check compares the
   desired key with the `auth` stored in this plugin's link row, not with the API response. Different → update.
   Comparing with the API response would send a PUT on every sweep, and every PUT makes Open Notificaties call the
   callback.
4. Callback `POST /api/v1/notificatiesapi-manual/callback`: copy of #971 `NotificatiesApiResource` +
   `isAuthorizedCallback`: no header → 403; kanaal `test` with a header → 204 without queuing; header equal
   (constant-time) to the `authHeader` of any Manual configuration of this plugin, or to the auth of a link of an
   existing configuration of this plugin that is not Manual → `registerNotification` + 204; otherwise 401. Blank never
   matches. A link row of a deleted configuration (left when the remote delete failed) does not authorise.
   Does not depend on `register-abonnementen`. Link lookup is a list query (`findAllByAuth`): two configurations may
   share a secret (core's single-result `findByAuth` would throw → 500).
5. `receive-notificatie` correlation: copy of core `NotificatiesApiNotificationProcessLinkListener` for links with
   definition key `notificatiesapi-manual` (own `ReceiveNotificatieProperties` copy). Core's listener only handles
   `notificatiesapi` links, so there is no double handling of one link. Failure behaviour as core (one
   `REQUIRES_NEW` transaction, exceptions propagate, the inbound event goes FAILED and is retried). A per-link catch
   doesn't work: an engine command failure marks the shared transaction rollback-only. Delivery to listeners is
   at-least-once anyway (a failing core or Verzoek listener also re-runs every listener on retry); documented.
6. Manual view `GET /api/management/v1/notificatiesapi-manual/configurations/{id}/manual-abonnement` (ADMIN only;
   404 when the configuration is missing, not of this plugin, or not Manual). Returns `callbackUrl` and `kanalen`:
   - this plugin's `receive-notificatie` links whose `pluginConfigurationId` is this configuration;
   - core `receive-notificatie` links (key `notificatiesapi`) whose `pluginConfigurationId` is a core configuration
     with the same `url` (trailing `/` ignored). Read from `PluginProcessLinkRepository` directly, because core's
     `NotificatiesApiPlugin.getKanaalFilters()` returns the links of every core configuration, whatever its URL.
     Null-id core links are left out;
   - `getKanaalFilters()` of every other core `NotificatiesApiListener` configuration (category
     `notificaties-api-plugin` minus core `NotificatiesApiPlugin`: Verzoek, Portaaltaak, external) whose
     `getNotificatiesApiPlugin().url` equals this configuration's `url`. A listener that fails to instantiate is
     logged and skipped.
   Distinct, same shape as core's abonnement kanalen.
   Why the core listeners: Verzoek and Portaaltaak can only point at a core configuration (their property is typed to
   core `NotificatiesApiPlugin`). Behind the gateway their notificaties must arrive through this plugin's abonnement.
7. Security: own `HttpSecurityConfigurer` bean with `@Order(271)` (core uses 270; `DenyAllHttpSecurityConfigurer` is
   500 and must come last): callback `permitAll`, manual view `hasAuthority(ADMIN)`.
8. Liquibase: own master + changelog creating `notificaties_api_manual_abonnement_link`
   (`notificaties_api_manual_configuration_id ${uuidType}` PK, `abonnement_url VARCHAR(255)`,
   `abonnement_auth_key VARCHAR(255)`), registered via a `LiquibaseMasterChangeLogLocation` bean. Works on PostgreSQL
   and MySQL (same types as core's table).
9. Deleting a configuration of this plugin: Automatic → delete its abonnement in Open Notificaties + link row (as
   core); Manual → delete only a leftover link row, nothing in Open Notificaties. If the remote delete fails, the link
   row is removed anyway and an error names the abonnement URL for the administrator (also when the configuration
   can't be instantiated any more): nothing would retry it, and the
   link of a removed configuration no longer authorises.
10. Changing a configuration's id (editable in the form; core publishes `PluginConfigurationIdUpdatedEvent`, no
    delete): the link row is re-keyed to the new id in the same transaction, so the abonnement keeps authorising and
    no second one is created.

Frontend (`@valtimo-plugins/notificaties-api-manual`): copy of the #971 core frontend plugin with `pluginId`
`notificatiesapi-manual`, renamed classes/selectors (see names table), the service pointing at the
fork endpoint, own test ids, translations (nl/en/de; every language must have every key, Valtimo has no fallback) with the title above and the callback placeholder
`https://{gzac-back-end-url}/api/v1/notificatiesapi-manual/callback`. Components: configuration (with registration
select, shared secret, manual panel). The `publish-notificatie` and `receive-notificatie` action forms are core's
(`@valtimo/plugin` exports them; they take their texts from this plugin's translations via `pluginId`); the logo too. The manual panel text also says
that kanalen of core Notificaties API listeners on the same URL are included.

## Branch `v12` (Valtimo 12)

Source to copy: core module at `origin/v12-stable` (identical to 12.45.0). v12 core differs: abonnement registration
happens in `@PluginEvent` CREATE/UPDATE/DELETE with the fixed kanaal `objecten` and no filters; no actions; no
`NotificatiesApiListener`; no `receive-notificatie`; no `authHeader` (random key per abonnement).

1. `NotificatiesApiManualPlugin` = v12 core plugin + `authHeader` (optional; if set, used as the auth of an Automatic
   abonnement, like v13) + `abonnementRegistration` + the validations of main item 2.
2. Automatic: v12 `@PluginEvent` behaviour against this plugin's link table and callback. Exactly one `@PluginEvent`
   method per event type, which validates first and only then calls Open Notificaties: v12 runs plugin events in
   undefined order, and a remote abonnement created before a failing validation would stay behind (the DB rolls back,
   Open Notificaties doesn't, and v12 has no stale cleanup). The update compares `auth` too.
3. Manual: CREATE/UPDATE make no call to Open Notificaties (also no `ensureKanalenExist`); UPDATE removes a leftover
   link row (so switching back to Automatic creates a fresh abonnement instead of failing on the removed one); DELETE
   removes only a leftover link row. Switching Automatic → Manual leaves the old abonnement in Open Notificaties
   (documented, as #971). Automatic UPDATE whose linked abonnement is gone (404) creates a new one (core v12 fails the
   update). Automatic DELETE: a failing remote delete is logged with the abonnement URL for the administrator (v12 has
   no stale cleanup); the link row is removed.
3a. Id change: as main item 10.
4. Callback and security: as main items 4 and 7.
5. Manual view: `callbackUrl` + kanaal `objecten` without filters (what Automatic registers on v12).
6. Liquibase: as main item 8 (v12 core uses the same `LiquibaseMasterChangeLogLocation` mechanism).
7. Frontend: v12 core configuration component (Angular 17) + #971 additions; no action components. `v-select` has no
   `clearable` input on 12.45, so that binding is dropped.

Build baseline for `v12`: as `generiekzaakafhandelcomponent/http-client-authentication-plugin@v12` (Java 17, Camunda
instead of Operaton, Valtimo 12 sandbox config, Angular 17 frontend workspace).

## Supported versions and build

- `main`: `valtimoVersion` = the minimum supported, **13.41.0** (GZAC SD), so the published jar is compiled against
  the oldest API (a recompile against the minimum in CI would miss binary-only breaks). `pr-checks` gets a second
  backend job that runs the tests with `-PvaltimoVersion=13.51.0.RELEASE` (latest). The template's `update-valtimo`
  workflow and command are removed: they bump `valtimoVersion` to the latest, which is the opposite of what this
  repo needs. `gradle.properties` says why.
- `v12`: minimum **12.45.0** (Mijn Inkomen) as `valtimoVersion`, extra CI job against 12.48.0.
- Frontend workspace `@valtimo/*` pinned to the minimum (13.41.0 / 12.45.0, with the Angular version they need), so
  the library is compiled against the oldest supported API; CI builds the library.
- On Valtimo ≥ 13.52 core has #971 itself; this plugin still works there, but the README tells users to move to core.
- Frontend library: `@angular/core`, `@angular/common` and `@angular/forms` as `peerDependencies` with a caret range
  (`^19.2.0` / `^17.3.0`) instead of the template's exact pins (an exact `@angular/forms` dependency would install a
  nested copy next to another patch version), so it installs next to any 13.x / 12.x application.
- Test database port `54355` (`notificaties-api-manual-test`), added to the port list in
  `docker-resources/docker-compose-base-test.yml`.
- Sandbox: `valtimo.zgw.register-abonnementen=false` (13.41 would crash on the unreachable core callback) and the
  sample case/process link/plugin configuration replaced by an example for this plugin.
- Template leftovers removed: sample plugin, sample metadata; `valtimo-configurator-metadata.json`, README, docs,
  release notes, `projectName`, `plugin.properties`, publishing POM name/description rewritten for this plugin.

## Decisions and why

- **Full copy incl. Automatic** (user decision): closest to "core plugin + #971". Automatic on this plugin is mainly
  there so the plugin is a drop-in for the core plugin; Manual is the reason it exists.
- **Reuse core's inbound queue** instead of copying it: one queue means one idempotence check, existing admin retry
  endpoints and health indicator cover these notificaties, and no second worker/table.
- **Own link table** instead of core's: core's cleanup and callback authorization read core's table; sharing it would
  let core delete or accept based on rows it doesn't own.
- **`authHeader` stored as a non-secret property** (as #971): it must be compared on every callback and exported with
  the configuration; the form shows it as a password field.
- **Core listeners matched by URL**, not by an extra "covers" property: zero configuration for the common case (one
  Open Notificaties); duplicates are harmless because of the idempotence key.
- **Fork listener fails like core's**: consistent retry semantics; at-least-once delivery is documented.

### Review decisions (code breaker rounds)

- Round 1, "docs are still the template" (AC 10) as mustFix: handled in the Verify step (docs written there and checked
  by a docs breaker), not in the code loop.
- Round 1 fixes: v12 logs via `mu.KotlinLogging` (Valtimo 12 ships microutils kotlin-logging, not oshai); `de`
  translations on both lines (Valtimo has no fallback language); shared secret input `maxLength` 255; shared secret
  restricted to printable ASCII (Open Notificaties sends headers as Latin-1); a callback URL ending in core's
  `/api/v1/notificatiesapi/callback` is rejected; abonnementen are matched by id, not full URL (aliases of one server).
- Left over (not fixed): one unreachable Open Notificaties stops the sweep for later URL groups (inherited from core);
  no IT that runs a `receive-notificatie` link through the real inbound queue (unit-tested only); no permanent v12 IT
  for an id change (verified by the breaker, unit-tested in the listener).
- Round 2 (v12, no mustFix), left over: a numeric `authHeader` in a `.pluginconfig.json` is accepted but never matches
  (read with `textValue()`); the core-callback-path check compares the string suffix, so a query, fragment or other
  case bypasses it; template-wide publishing: `cn.lalaki.central` leaks into the published runtime dependencies and the
  jar has a `plain` classifier (also in the sibling plugins).
- `/simplify`: core's action forms, `ReceiveNotificatieProperties`, plugin key constants, logo and (v12)
  `DEFAULT_KANALEN_NAMES` reused instead of copied; one helper reads stored configuration properties; event sweeps
  coalesced on the registration thread. Skipped: one abonnement per automatic configuration (design change), dropping
  the v12 callback separation (decision above), dropping the #971 "no cleanup next to a manual configuration" rule,
  caching v12 callback lookups (v12 has no configuration create/update events to keep a cache in sync).
- Round 3 (v12, no mustFix), left over: the manual view instantiates the plugin, so it answers 500 when the
  configuration's authentication configuration was deleted (it could read the stored properties instead).
- UI breaker round 1 (main, no mustFix). Decisions confirmed, not changed: a notificatie accepted with any
  configuration's secret reaches every `receive-notificatie` link (the callback authorises the sender, as core #971;
  links don't know which abonnement delivered a notificatie); the shared secret is visible to admins in the plugin
  configuration API (non-secret property, as #971). Left over: a refused save shows "Internal Server Error" (Valtimo
  wraps every plugin-event validation error in a 500, core included); a callback URL with another path under
  `/api/v1/notificatiesapi-manual/` is accepted but gets no notificaties (the docs name the two paths). AC 3 is covered
  by unit tests only (the UI breaker had no Open Notificaties admin access).
- UI breaker round 1 (v12): rejected as mustFix, "Automatic → Manual leaves the abonnement in Open Notificaties": the
  #971 decision (the administrator removes it, or keeps it by using its secret as the shared secret); the docs say so,
  also for the default case without Authentication header. Left over: callback URL checks compare normalized strings,
  so host case, a query or fragment bypass them; a Manual callback URL with another path under the plugin's endpoint
  is accepted but receives nothing; on Valtimo 12 a refused save leaves the configuration dialog disabled without a
  message (core behaviour, also for the standard plugin).
- Docs breaker round 1 (v12): concurrent duplicate delivery through both plugins can be answered once with a 500
  before Open Notificaties' retry is ignored (core's intake; documented).
- Docs breaker round 2 (v12): the 500 for a concurrent duplicate delivery comes from core's intake; with stack traces
  enabled (as in the sandbox for localhost) its body shows the SQL statement.
- Round 2 (main, no mustFix), left over: an Open Notificaties `url` over ~208 characters gives abonnement URLs that don't
  fit the 255-character link column (as core); the manual view shows the "save first" hint for any error, not only 404.

## Risks

- `allow-bean-definition-overriding: true` (template and many GZAC apps) hides bean-name clashes. An integration test
  starts the context with overriding disabled.
- Core stale cleanup could delete this plugin's abonnement if both use the same callback URL → validation (item 2),
  plus a docs note that the manual abonnement in Open Notificaties must use this plugin's callback URL.
- On Valtimo 13, a core configuration behind the gateway makes core's own registration fail. On 13.41.0 – 13.41.0-4
  and 13.42.x that failure crashes startup (inline retry, exception out of the ready event); from 13.41.0-5 and 13.43.0
  core retries in the background and gives up. So `valtimo.zgw.register-abonnementen=false` is **mandatory** on those
  versions and recommended later. Global switch; documented.
- Process links copied on a BPMN-only redeploy publish no event, and on 13.41–13.50 deleting all links of a process
  definition publishes no delete event; those filters reach an Automatic abonnement on the next trigger or restart (as
  core). A case import publishes one event per link; those share one sweep. Documented.
- Validation also runs on autodeploy: a `.pluginconfig.json` that breaks a rule (callback shared with core, a `Bearer…`
  secret) stops startup on Valtimo 13, as any invalid plugin configuration
  does; on Valtimo 12 the configuration (and the rest of that file) is skipped and logged. Documented.
- Valtimo 12: a configuration whose authentication configuration was deleted can't be deleted (core instantiates the
  plugin for the DELETE event, as for the standard plugin). Documented. On v12 the Manual/Automatic callback separation is kept although v12 has no stale cleanup: it keeps the
  configurations the same on both lines and costs nothing.
- Building-block `receive-notificatie` links (null configuration id) don't work, in core nor in this plugin: core's
  link query never returns them. Documented.
- Inherited from core, documented, not fixed: several pods starting at once with an Automatic configuration and no
  link can create duplicate abonnementen (one may be orphaned or deleted by the other's cleanup) — start one pod first;
  changing a configuration's `url` leaves the old abonnement in the old Open Notificaties; concurrent duplicate
  deliveries (core and plugin abonnement hit at the same moment) can give one 500 while the notificatie is still
  processed once; clearing `authHeader` keeps the key of the existing abonnement; core's `PluginFactory` is not
  thread-safe (affects every plugin).
- Never subclass core types that core guards with `@ConditionalOnMissingBean` (client, plugin factory, listeners,
  resources): core would then skip its own bean.
- Valtimo 12: Verzoek/Portaaltaak need a core configuration, whose creation registers an abonnement and fails behind
  the gateway. Documented limitation (story).

## Out of scope

- Changes to core Valtimo, Open Notificaties or the gateway.
- Migrating existing core configurations or process links.
- Valtimo lines other than 12 and 13.
- Pushing branches or publishing artifacts (pushing `main`/`v12` publishes to Maven Central and npm).

## Trying it in the sandbox (`backend/app`)

- `backend/app/src/main/resources/config/plugin/notificaties-api-manual.pluginconfig.json`: a `notificatiesapi-manual`
  configuration for the local Open Notificaties (`http://localhost:8002/api/v1/`, reusing the
  `notificatiesapiauthentication` configuration from `_zgw.pluginconfig.json`) with `abonnementRegistration: manual`,
  `authHeader: local-shared-secret`, `callbackUrl: http://host.docker.internal:8080/api/v1/notificatiesapi-manual/callback`.
- Check: the configuration form shows Manual + the kanalen panel; then
  `curl -X POST localhost:8080/api/v1/notificatiesapi-manual/callback -H 'Authorization: local-shared-secret' -H 'Content-Type: application/json' -d '{"kanaal":"objecten","hoofdObject":"http://x/1","resource":"object","resourceUrl":"http://x/1","actie":"create","aanmaakdatum":"2026-01-01T00:00:00Z","kenmerken":{}}'`
  returns 204, a wrong header 401, no header 403.

## Acceptance criteria

Both branches unless marked.

1. **Non-breaking**: in the plugin's integration-test context (core notificaties-api module + this plugin), the
   context starts with `spring.main.allow-bean-definition-overriding=false`; no bean name of this plugin is one of
   core's notificaties-api bean names up to #971; with no configuration of this plugin it makes no call to Open
   Notificaties; core's endpoints and link table are untouched.
2. A `notificatiesapi-manual` configuration can be created with registration Automatic or Manual; one saved without
   the setting is Automatic. Saving fails for Manual without a shared secret, for a shared secret starting with
   `bearer` (any case), with surrounding whitespace or over 255 characters, for a `callbackUrl` equal to a core `notificatiesapi`
   configuration's `callbackUrl`, and for a Manual and an Automatic configuration of this plugin sharing a
   `callbackUrl`. Changing a configuration's id keeps its abonnement authorising.
3. **Manual**: no create/update/delete of an abonnement or kanaal in Open Notificaties for that configuration — not at
   startup, on configuration or process-link changes, or on deletion (deletion removes only a leftover link row). On
   main: its `receive-notificatie` filters are left out of every Automatic abonnement of this plugin, and Automatic
   stale cleanup deletes nothing on an Open Notificaties URL that has a Manual configuration of this plugin, and never
   deletes an abonnement whose callback URL is that of a Manual configuration of this plugin or of a core
   configuration. On v12: a failing validation makes no call to Open Notificaties.
4. **Automatic**: registers, updates and deletes abonnementen for this plugin's configurations like the core plugin of
   that line, against this plugin's callback and link table, and never touches core's abonnementen or link rows. With
   configurations on two Open Notificaties URLs, each abonnement holds only the filters of links of its own URL's
   configurations (plus null-id links). A sweep with nothing changed sends no PUT.
   Core never touches this plugin's abonnementen or link rows, as long as the callback URLs differ. Unlike core, a
   changed `authHeader` updates the abonnement; deleting a `receive-notificatie` link removes its filter on the next
   sweep (triggered by the delete of that link).
5. **Callback** `POST /api/v1/notificatiesapi-manual/callback`: a header equal to a Manual configuration's secret, or to
   the auth of a non-Manual configuration's link → 204 and queued in core's inbound queue; any other header → 401;
   none → 403; neither of the last two queued (kanaal `test`, Open Notificaties' validation call, gets 204 with any
   header, as core). A blank secret never matches; a link of a configuration that is now
   Manual no longer authorises; a link row of a deleted configuration no longer authorises; two configurations sharing
   a secret still give 204.
6. An accepted notificatie publishes `NotificatiesApiNotificationReceivedEvent` to existing consumers (verified with a
   test listener), and the same notificatie posted to core's and this plugin's callback is processed once. On main, a
   `notificatiesapi-manual` `receive-notificatie` link matching the notificatie is triggered, and a core
   `notificatiesapi` link is not triggered by this plugin's listener. (Verzoek, Portaaltaak and external plugins
   listen to that same event class, so the test listener stands in for them.)
7. **Manual view**: ADMIN gets the callback URL and kanalen for a saved Manual configuration; non-admin → 403;
   missing / other plugin / Automatic → 404. On main the kanalen are exactly: this configuration's
   `receive-notificatie` filters + core `receive-notificatie` filters of core configurations with the same URL + the
   filters of other core `NotificatiesApiListener` configurations with the same URL; none from other URLs (also not
   core links of a core configuration on another URL), other configurations of this plugin, or null-id links. On v12: kanaal `objecten`
   without filters.
8. The configuration form (frontend) offers Automatic/Manual, requires the shared secret for Manual, and shows the
   manual panel for a saved Manual configuration; on main the `publish-notificatie` and `receive-notificatie` action
   forms work under the new key.
9. Backend tests pass against the minimum Valtimo version (13.41.0 / 12.45.0, the published build) and the latest
   (13.51.0 / 12.48.0); the frontend
   library builds against the minimum `@valtimo/*` version and lints; `valtimo-configurator-metadata.json` matches artifact, package, spec and module names.
10. README and `documentation/` describe installation, when to use it, the Open Notificaties steps, the supported
    versions, the known limitations (core `register-abonnementen`, mandatory on 13.41.0–13.41.0-4 and 13.42.x; v12 Verzoek/Portaaltaak;
    building-block `receive-notificatie` links of core and of this plugin are not in the manual view; at-least-once
    delivery; the inherited limitations under Risks; after switching to Manual the old abonnement keeps posting
    until the administrator removes it: with its old key it gets 401, or — if that key is kept as the Manual secret —
    duplicate deliveries that are processed once; imported process links reach Automatic abonnementen on the next
    sweep) and moving to core on ≥ 13.52; release notes have a 1.0.0 / 1.0.0-V12 entry.
