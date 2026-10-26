/*
 * Copyright 2026 Ritense BV, the Netherlands.
 *
 * Licensed under EUPL, Version 1.2 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ritense.valtimoplugins.notificatiesapimanual.registration

import com.ritense.logging.withLoggingContext
import com.ritense.notificatiesapi.NotificatiesApiAuthentication
import com.ritense.notificatiesapi.client.NotificatiesApiClient
import com.ritense.notificatiesapi.domain.Abonnement
import com.ritense.notificatiesapi.domain.Kanaal
import com.ritense.plugin.domain.PluginConfiguration
import com.ritense.plugin.events.PluginConfigurationCreatedEvent
import com.ritense.plugin.events.PluginConfigurationDeletedEvent
import com.ritense.plugin.events.PluginConfigurationIdUpdatedEvent
import com.ritense.plugin.events.PluginConfigurationUpdatedEvent
import com.ritense.plugin.repository.PluginProcessLinkRepository
import com.ritense.plugin.service.PluginConfigurationSearchParameters
import com.ritense.plugin.service.PluginService
import com.ritense.processlink.event.ProcessLinkCreatedEvent
import com.ritense.processlink.event.ProcessLinkDeletedEvent
import com.ritense.processlink.event.ProcessLinkUpdatedEvent
import com.ritense.valtimo.contract.event.ApplicationFullyReadyEvent
import com.ritense.valtimoplugins.notificatiesapimanual.config.NotificatiesApiManualPluginProperties
import com.ritense.valtimoplugins.notificatiesapimanual.domain.NotificatiesApiManualPluginAbonnementLink
import com.ritense.valtimoplugins.notificatiesapimanual.domain.NotificatiesApiManualPluginConfigurationId
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.CALLBACK_URL_PROPERTY
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.CORE_PLUGIN_KEY
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.PLUGIN_KEY
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.coreCallbackUrls
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.normalize
import com.ritense.valtimoplugins.notificatiesapimanual.repository.NotificatiesApiManualPluginAbonnementLinkRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionTemplate
import java.net.URI
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.concurrent.Executor

/**
 * Registers the abonnementen of this plugin's automatic configurations in Open Notificaties: one abonnement per
 * Open Notificaties URL, as core does. Copy of core's `PluginsDeployedEventListener` (with #971), limited to this
 * plugin's configurations, its link table and its callback.
 *
 * Differences with core:
 * - a `receive-notificatie` link only goes into the abonnement of its own configuration's URL;
 * - stale cleanup never deletes an abonnement whose callback is that of a manual configuration of this plugin or of a
 *   core configuration;
 * - a changed `authHeader` updates the abonnement (Open Notificaties never returns `auth`, so the stored link is
 *   compared instead);
 * - deleted process links trigger a sweep too.
 *
 * @param registrationExecutor executor the startup registration is handed off to, so the retry loop never blocks
 * the event thread.
 */
class NotificatiesApiManualPluginAbonnementRegistrar(
    private val client: NotificatiesApiClient,
    private val linkRepository: NotificatiesApiManualPluginAbonnementLinkRepository,
    private val pluginService: PluginService,
    private val pluginProcessLinkRepository: PluginProcessLinkRepository,
    transactionManager: PlatformTransactionManager,
    private val properties: NotificatiesApiManualPluginProperties = NotificatiesApiManualPluginProperties(),
    private val registrationExecutor: Executor = Executor { it.run() },
) {
    /**
     * The listeners below run during `afterCommit`, where a REQUIRED write would join the already committed
     * transaction and be discarded silently. REQUIRES_NEW is also the right semantics: the abonnement already
     * exists remotely by then, so a rollback could not undo it.
     */
    private val requiresNewTransactionTemplate =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    @Volatile
    private var applicationFullyReady = false

    @EventListener(ApplicationFullyReadyEvent::class)
    fun handleApplicationFullyReadyEvent() {
        applicationFullyReady = true
        registrationExecutor.execute { registerAbonnementenWithBackoff() }
    }

    /**
     * Registers abonnementen with exponential backoff until it succeeds, or until the configured maximum duration has
     * passed. Open Notificaties validates a new abonnement by calling the callback, which cannot succeed before the
     * pod is routable; a pod that has not managed to subscribe yet stays up instead of crash-looping.
     */
    fun registerAbonnementenWithBackoff() {
        val retryProperties = properties.abonnementRegistration
        val deadline = System.nanoTime() + retryProperties.maxDuration.toNanos()
        var backoff = retryProperties.initialBackoff
        var attempt = 1

        while (true) {
            try {
                registerAbonnementen(attemptsPerUrl = 1)
                if (attempt > 1) {
                    logger.info { "Successfully registered abonnementen on attempt $attempt" }
                }
                return
            } catch (e: Exception) {
                val remaining = Duration.ofNanos(deadline - System.nanoTime())
                if (remaining <= backoff) {
                    logger.error(e) {
                        "Giving up on registering abonnementen after $attempt attempt(s) within " +
                            "${retryProperties.maxDuration}. The application stays up, but no notifications will be " +
                            "received through automatic configurations of this plugin until registration succeeds."
                    }
                    return
                }
                logger.warn(e) { "Attempt $attempt to register abonnementen failed; retrying in $backoff" }
                if (!sleep(backoff)) return
                backoff = minOf(backoff.multipliedBy(2), retryProperties.maxBackoff)
                attempt++
            }
        }
    }

    private fun sleep(duration: Duration): Boolean =
        try {
            Thread.sleep(duration.toMillis().coerceAtLeast(1))
            true
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.info { "Abonnement registration was interrupted; not retrying" }
            false
        }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    fun handlePluginConfigurationCreatedEvent(event: PluginConfigurationCreatedEvent) {
        handlePluginConfigurationChanged(event.pluginConfiguration)
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    fun handlePluginConfigurationUpdatedEvent(event: PluginConfigurationUpdatedEvent) {
        handlePluginConfigurationChanged(event.pluginConfiguration)
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    fun handlePluginConfigurationDeletedEvent(event: PluginConfigurationDeletedEvent) {
        if (!applicationFullyReady || !isRelevant(event.pluginConfiguration)) return
        if (event.pluginConfiguration.pluginDefinition.key == PLUGIN_KEY) {
            try {
                removeAbonnementForDeletedConfiguration(event.pluginConfiguration)
            } catch (e: Exception) {
                // The configuration is already deleted; throwing out of afterCommit would only report a 500.
                logger.error(e) { "Could not clean up after the removed plugin configuration" }
            }
        }
        registerAbonnementenSafely()
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    fun handleProcessLinkCreatedEvent(event: ProcessLinkCreatedEvent) {
        handleProcessLinkChanged(event.processLinkType)
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    fun handleProcessLinkUpdatedEvent(event: ProcessLinkUpdatedEvent) {
        handleProcessLinkChanged(event.processLinkType)
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    fun handleProcessLinkDeletedEvent(event: ProcessLinkDeletedEvent) {
        handleProcessLinkChanged(event.processLinkType)
    }

    private fun handlePluginConfigurationChanged(pluginConfiguration: PluginConfiguration) {
        if (!applicationFullyReady || !isRelevant(pluginConfiguration)) return
        registerAbonnementenSafely()
    }

    // The events carry no plugin key, so every plugin process link triggers a sweep, as in core.
    private fun handleProcessLinkChanged(processLinkType: String) {
        if (!applicationFullyReady || processLinkType != "plugin") return
        registerAbonnementenSafely()
    }

    // Core configurations are relevant too: their callback URL decides what the stale cleanup may delete.
    private fun isRelevant(pluginConfiguration: PluginConfiguration): Boolean =
        pluginConfiguration.pluginDefinition.key in setOf(PLUGIN_KEY, CORE_PLUGIN_KEY)

    // Throwing out of afterCommit reports a failure for a change that is already committed; log it instead.
    private fun registerAbonnementenSafely() {
        try {
            registerAbonnementen()
        } catch (e: Exception) {
            logger.error(e) { "Could not register the abonnementen of this plugin's automatic configurations" }
        }
    }

    @JvmOverloads
    fun registerAbonnementen(attemptsPerUrl: Int = DEFAULT_ATTEMPTS) {
        val coreCallbackUrls = coreCallbackUrls(pluginService)
        warnAboutCallbackUrlsSharedWithCore(coreCallbackUrls)

        if (!properties.registerAbonnementen) {
            logger.info {
                "Abonnement registration of this plugin is disabled " +
                    "(valtimo.notificaties-api-manual.register-abonnementen=false); skipping"
            }
            return
        }

        val kanaalFilters =
            NotificatiesApiManualPluginKanaalFilters.fromPluginService(
                pluginService,
                pluginProcessLinkRepository,
            )
        val groups = kanaalFilters.automaticConfigurations().groupBy { normalize(it.url) }
        if (groups.isEmpty()) return

        // Kept up to date during the sweep, so a later URL group never takes a link saved now for stale.
        val knownLinks = linkRepository.findAll().toMutableList()
        val protectedCallbackUrls = ProtectedCallbackUrls(kanaalFilters.manualCallbackUrls(), coreCallbackUrls)

        groups.forEach { (_, group) ->
            val plugin = owner(group, knownLinks)
            withLoggingContext(PluginConfiguration::class.java.canonicalName to plugin.configurationId.id.toString()) {
                retry(times = attemptsPerUrl) {
                    val saved = registerAbonnement(plugin, group, knownLinks, kanaalFilters, protectedCallbackUrls)
                    knownLinks.removeIf { it.configurationId == saved.configurationId }
                    knownLinks.add(saved)
                }
                removeAbonnementenOfOtherMembers(plugin, group, knownLinks)
            }
        }
    }

    /**
     * A URL group has one abonnement, held by its owner. Another member that still has a link (e.g. its `url` was
     * changed to the group's) would keep an abonnement nobody updates or cleans up, delivering every notificatie twice.
     */
    private fun removeAbonnementenOfOtherMembers(
        owner: NotificatiesApiManualPlugin,
        group: List<NotificatiesApiManualPlugin>,
        knownLinks: MutableList<NotificatiesApiManualPluginAbonnementLink>,
    ) {
        group
            .filter { it.configurationId.id != owner.configurationId.id }
            .forEach { member ->
                val link =
                    knownLinks.firstOrNull { it.configurationId.id == member.configurationId.id } ?: return@forEach
                try {
                    client.deleteAbonnement(
                        member.authenticationPluginConfiguration,
                        member.url,
                        link.getAbonnementId(),
                    )
                    logger.info {
                        "Deleted abonnement '${link.url}'; its URL group's abonnement is now held by another configuration"
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Could not delete abonnement '${link.url}'; remove it in Open Notificaties" }
                }
                requiresNewTransactionTemplate.executeWithoutResult { linkRepository.delete(link) }
                knownLinks.remove(link)
            }
    }

    /**
     * Core's stale cleanup deletes every untracked abonnement whose callback URL is that of a core configuration. A
     * configuration of this plugin with the same callback URL (only possible when a core configuration is saved later;
     * this plugin's validation refuses it the other way round) would lose its abonnement, a manual one included.
     * Runs on every sweep and every core or plugin configuration change, also when there is nothing to register.
     */
    private fun warnAboutCallbackUrlsSharedWithCore(coreCallbackUrls: Set<String>) {
        if (coreCallbackUrls.isEmpty()) return
        pluginService
            .getPluginConfigurations(PluginConfigurationSearchParameters(pluginDefinitionKey = PLUGIN_KEY))
            .mapNotNull { configuration ->
                configuration.properties
                    ?.path(CALLBACK_URL_PROPERTY)
                    ?.textValue()
                    ?.let { configuration.id.id to it }
            }.filter { (_, callbackUrl) -> normalize(callbackUrl) in coreCallbackUrls }
            .forEach { (id, callbackUrl) ->
                logger.error {
                    "Plugin configuration '$id' uses callback URL '$callbackUrl', which a core Notificaties API " +
                        "configuration uses too. Core will delete its abonnement in Open Notificaties. Give the core " +
                        "configuration its own callback URL (/api/v1/notificatiesapi/callback)."
                }
            }
    }

    /**
     * The configuration whose link holds the abonnement of a URL group: the one that already has a link, else the
     * lowest id. Core takes the first in query order, which can change between sweeps and orphan an abonnement.
     */
    private fun owner(
        group: List<NotificatiesApiManualPlugin>,
        knownLinks: List<NotificatiesApiManualPluginAbonnementLink>,
    ): NotificatiesApiManualPlugin {
        val linkedIds = knownLinks.map { it.configurationId.id }.toSet()
        return group.filter { it.configurationId.id in linkedIds }.minByOrNull { it.configurationId.id }
            ?: group.minBy { it.configurationId.id }
    }

    /** A changed configuration id (core publishes no delete for it) keeps its abonnement. */
    @EventListener
    fun handlePluginConfigurationIdUpdatedEvent(event: PluginConfigurationIdUpdatedEvent) {
        val link =
            linkRepository.findById(NotificatiesApiManualPluginConfigurationId(event.oldId)).orElse(null) ?: return
        linkRepository.delete(link)
        linkRepository.save(
            NotificatiesApiManualPluginAbonnementLink(
                NotificatiesApiManualPluginConfigurationId(event.newId),
                link.url,
                link.auth,
            ),
        )
        logger.info {
            "Moved the abonnement link of plugin configuration '${event.oldId}' to its new id '${event.newId}'"
        }
    }

    private fun removeAbonnementForDeletedConfiguration(deletedConfiguration: PluginConfiguration) {
        val configurationId = NotificatiesApiManualPluginConfigurationId(deletedConfiguration.id.id)
        val link = linkRepository.findById(configurationId).orElse(null) ?: return
        val plugin =
            runCatching {
                pluginService.createInstance(deletedConfiguration) as? NotificatiesApiManualPlugin
            }.getOrNull()

        if (plugin == null) {
            requiresNewTransactionTemplate.executeWithoutResult { linkRepository.delete(link) }
            logger.error {
                "Could not delete abonnement '${link.url}' of removed plugin configuration '${configurationId.id}'; " +
                    "remove it in Open Notificaties"
            }
            return
        }
        if (plugin.isManualAbonnementRegistration() || !properties.registerAbonnementen) {
            requiresNewTransactionTemplate.executeWithoutResult { linkRepository.delete(link) }
            logger.info {
                "Removed the abonnement link of removed plugin configuration '${configurationId.id}'; " +
                    "its abonnement in Open Notificaties is left untouched"
            }
            return
        }

        withLoggingContext(PluginConfiguration::class.java.canonicalName to configurationId.id.toString()) {
            try {
                retry {
                    client.deleteAbonnement(
                        authentication = plugin.authenticationPluginConfiguration,
                        baseUrl = plugin.url,
                        abonnementId = link.getAbonnementId(),
                    )
                }
                logger.info {
                    "Successfully deleted abonnement '${link.getAbonnementId()}' of removed plugin configuration"
                }
            } catch (e: Exception) {
                // Nothing retries this later, and the link of a removed configuration no longer authorises.
                logger.error(e) {
                    "Could not delete abonnement '${link.url}' of removed plugin configuration " +
                        "'${configurationId.id}'; remove it in Open Notificaties"
                }
            } finally {
                requiresNewTransactionTemplate.executeWithoutResult { linkRepository.delete(link) }
            }
        }
    }

    private fun registerAbonnement(
        plugin: NotificatiesApiManualPlugin,
        group: List<NotificatiesApiManualPlugin>,
        knownLinks: List<NotificatiesApiManualPluginAbonnementLink>,
        kanaalFilters: NotificatiesApiManualPluginKanaalFilters,
        protectedCallbackUrls: ProtectedCallbackUrls,
    ): NotificatiesApiManualPluginAbonnementLink {
        val abonnementenInApi = client.getAbonnementen(plugin.authenticationPluginConfiguration, plugin.url)

        // An administrator's abonnement for a manual configuration cannot be told apart from a stale one.
        if (kanaalFilters.hasManualConfiguration(plugin.url.toString())) {
            logger.info {
                "Not deleting untracked abonnementen in Open Notificaties, because a configuration of this plugin on " +
                    "the same URL registers its abonnement manually"
            }
        } else {
            deleteStaleAbonnementen(plugin, abonnementenInApi, knownLinks, protectedCallbackUrls)
        }

        val kanalen = kanaalFilters.automaticKanaalFilters(group)
        val currentLink = knownLinks.firstOrNull { it.configurationId.id == plugin.configurationId.id }
        val authKey =
            plugin.authHeader?.takeIf { it.isNotBlank() }
                ?: currentLink?.auth?.takeIf { it.isNotBlank() }
                ?: createRandomKey()

        ensureKanalenExist(kanalen.map { it.naam }.toSet(), plugin.authenticationPluginConfiguration, plugin.url)

        val currentAbonnement = currentLink?.let { link -> abonnementenInApi.firstOrNull { it.url == link.url } }
        val desiredCallbackUrl = plugin.callbackUrl.toASCIIString()

        val abonnement =
            if (currentAbonnement == null) {
                client
                    .createAbonnement(
                        plugin.authenticationPluginConfiguration,
                        plugin.url,
                        Abonnement(callbackUrl = desiredCallbackUrl, auth = authKey, kanalen = kanalen),
                    ).also { logger.info { "Successfully created abonnement '${it.getId()}'" } }
            } else {
                // Open Notificaties never returns auth, so the key it has is the one stored in the link.
                val unchanged =
                    currentAbonnement.callbackUrl == desiredCallbackUrl &&
                        currentAbonnement.kanalen.toSet() == kanalen.toSet() &&
                        currentLink.auth == authKey
                if (unchanged) {
                    logger.debug {
                        "Skipping update of abonnement '${currentAbonnement.getId()}' because it is unchanged"
                    }
                    currentAbonnement
                } else {
                    client
                        .updateAbonnement(
                            authentication = plugin.authenticationPluginConfiguration,
                            baseUrl = plugin.url,
                            abonnementId = currentLink.getAbonnementId(),
                            abonnement =
                                Abonnement(
                                    callbackUrl = desiredCallbackUrl,
                                    auth = authKey,
                                    kanalen = kanalen,
                                ),
                        ).also { logger.info { "Successfully updated abonnement '${it.getId()}'" } }
                }
            }

        // A stale link (abonnement unknown in Open Notificaties) has the same key and is overwritten here.
        val link =
            NotificatiesApiManualPluginAbonnementLink(
                configurationId = NotificatiesApiManualPluginConfigurationId(plugin.configurationId.id),
                url =
                    abonnement.url ?: currentLink?.url ?: error("Open Notificaties returned an abonnement without url"),
                auth = authKey,
            )
        requiresNewTransactionTemplate.executeWithoutResult { linkRepository.save(link) }
        return link
    }

    private fun deleteStaleAbonnementen(
        plugin: NotificatiesApiManualPlugin,
        abonnementenInApi: List<Abonnement>,
        knownLinks: List<NotificatiesApiManualPluginAbonnementLink>,
        protectedCallbackUrls: ProtectedCallbackUrls,
    ) {
        val callbackUrl = plugin.callbackUrl.toString()
        if (normalize(callbackUrl) in protectedCallbackUrls.core) {
            logger.error {
                "Not deleting untracked abonnementen in Open Notificaties: a core Notificaties API " +
                    "configuration uses " +
                    "the same callback URL '$callbackUrl'. Give this plugin's configuration its own callback URL " +
                    "(/api/v1/notificatiesapi-manual/callback)."
            }
            return
        }
        if (normalize(callbackUrl) in protectedCallbackUrls.manual) {
            logger.info {
                "Not deleting untracked abonnementen in Open Notificaties: a manual configuration " +
                    "of this plugin uses " +
                    "the same callback URL '$callbackUrl'"
            }
            return
        }
        abonnementenInApi
            .filter { abonnement ->
                abonnement.callbackUrl == callbackUrl &&
                    knownLinks.none { it.url == abonnement.url }
            }.forEach { abonnement ->
                client.deleteAbonnement(plugin.authenticationPluginConfiguration, plugin.url, abonnement.getId()!!)
                logger.info { "Successfully deleted stale abonnement '${abonnement.getId()}' not tracked locally" }
            }
    }

    private fun ensureKanalenExist(
        kanalen: Set<String>,
        authentication: NotificatiesApiAuthentication,
        url: URI,
    ) {
        if (kanalen.isEmpty()) return
        val existingKanalen = client.getKanalen(authentication, url).map { it.naam }
        kanalen
            .filter { !existingKanalen.contains(it) }
            .forEach { kanaalNaam ->
                client.createKanaal(authentication = authentication, baseUrl = url, kanaal = Kanaal(naam = kanaalNaam))
                logger.info { "Successfully created Notificaties API kanaal '$kanaalNaam'" }
            }
    }

    private fun <T> retry(
        times: Int = DEFAULT_ATTEMPTS,
        block: () -> T,
    ): T {
        var lastException: Exception? = null
        repeat(times) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (times > 1) logger.warn(e) { "Attempt ${it + 1} of $times to register the abonnement failed" }
            }
        }
        throw lastException!!
    }

    private fun createRandomKey(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    private class ProtectedCallbackUrls(
        val manual: Set<String>,
        val core: Set<String>,
    )

    companion object {
        private val logger = KotlinLogging.logger {}
        private const val DEFAULT_ATTEMPTS = 3
    }
}
