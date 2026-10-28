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

package com.ritense.valtimoplugins.notificatiesapimanual.callback

import com.ritense.notificatiesapi.event.NotificatiesApiNotificationReceivedEvent
import com.ritense.notificatiesapi.exception.AuthorizationException
import com.ritense.notificatiesapi.service.NotificatiesApiService
import com.ritense.plugin.service.PluginConfigurationSearchParameters
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.notificatiesapimanual.domain.NotificatiesApiManualPluginConfigurationId
import com.ritense.valtimoplugins.notificatiesapimanual.domain.NotificatiesApiManualPluginMisroutedCallback
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.AUTH_HEADER_PROPERTY
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.CORE_CALLBACK_PATH
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.CORE_PLUGIN_KEY
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.storedRegistrations
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.StoredRegistration
import com.ritense.valtimoplugins.notificatiesapimanual.repository.NotificatiesApiManualPluginAbonnementLinkRepository
import com.ritense.valtimoplugins.notificatiesapimanual.repository.NotificatiesApiManualPluginMisroutedCallbackRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * Decides whether a callback comes from an abonnement of this plugin, and hands accepted notificaties to core's
 * inbound queue, which publishes them to every listener of [NotificatiesApiNotificationReceivedEvent].
 *
 * Also notices notificaties for a manual configuration that arrive on core's callback address instead, where core
 * refuses them, so the configuration form can warn the administrator.
 */
class NotificatiesApiManualPluginCallbackService(
    private val pluginService: PluginService,
    private val linkRepository: NotificatiesApiManualPluginAbonnementLinkRepository,
    private val notificatiesApiService: NotificatiesApiService,
    private val misroutedCallbackRepository: NotificatiesApiManualPluginMisroutedCallbackRepository,
) {
    /**
     * Authorises the sender, not a configuration: a header equal to the shared secret of any manual configuration, or
     * to the key of an abonnement this plugin registered for an existing automatic configuration, is accepted.
     */
    fun isAuthorizedCallback(authHeader: String): Boolean {
        if (authHeader.isBlank()) return false
        val registrations = storedRegistrations(pluginService)
        if (manualConfigurationIds(registrations, authHeader).isNotEmpty()) {
            return true
        }
        val automaticIds = registrations.filter { !it.manual }.map { it.id }.toSet()
        return linkRepository.findAllByAuth(authHeader).any { it.configurationId.id in automaticIds }
    }

    fun registerNotification(notification: NotificatiesApiNotificationReceivedEvent) {
        notificatiesApiService.registerNotification(notification)
    }

    /**
     * Called for every callback on core's address. When [authHeader] is the shared secret of a manual configuration
     * and not a secret core knows, core answers 401 (or 204 to the test notificatie): the abonnement in Open
     * Notificaties, or the gateway in front of Valtimo, points to the wrong address. Never fails the callback.
     */
    fun recordIfMisrouted(authHeader: String) {
        try {
            if (authHeader.isBlank()) return
            val configurationIds = manualConfigurationIds(storedRegistrations(pluginService), authHeader)
            if (configurationIds.isEmpty() || isCoreSecret(authHeader)) return

            logger.warn {
                "Received a notificatie with the shared secret of manual configuration(s) $configurationIds on " +
                    "the callback address of the standard Notificaties API plugin ($CORE_CALLBACK_PATH), which " +
                    "refuses it. Point the abonnement in Open Notificaties, or the gateway route to Valtimo, to " +
                    "/api/v1/notificatiesapi-manual/callback."
            }
            val now = Instant.now()
            configurationIds.forEach { id ->
                try {
                    misroutedCallbackRepository.save(
                        NotificatiesApiManualPluginMisroutedCallback(
                            NotificatiesApiManualPluginConfigurationId(id),
                            now,
                        ),
                    )
                } catch (e: DataIntegrityViolationException) {
                    // Another request recorded the same configuration at the same moment.
                    logger.debug(e) { "Misrouted callback of configuration '$id' was recorded concurrently" }
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Could not check a callback on $CORE_CALLBACK_PATH for manual configurations" }
        }
    }

    /**
     * Called for every callback this plugin accepts: the configurations with this secret are reached through the right
     * address again. Also forgets records of configurations that are gone or no longer manual. Never fails the callback.
     */
    fun clearMisroutedCallbacks(authHeader: String) {
        try {
            if (misroutedCallbackRepository.count() == 0L) return
            val registrations = storedRegistrations(pluginService)
            val manualIds = registrations.filter { it.manual }.map { it.id }.toSet()
            val reachedIds = manualConfigurationIds(registrations, authHeader)
            val cleared =
                misroutedCallbackRepository.findAll().filter {
                    it.configurationId.id in reachedIds || it.configurationId.id !in manualIds
                }
            if (cleared.isNotEmpty()) {
                misroutedCallbackRepository.deleteAll(cleared)
            }
        } catch (e: Exception) {
            logger.warn(e) { "Could not clear misrouted callbacks of manual configurations" }
        }
    }

    private fun manualConfigurationIds(
        registrations: List<StoredRegistration>,
        authHeader: String,
    ): Set<UUID> =
        registrations
            .filter { it.manual && it.secret != null && secretsMatch(it.secret, authHeader) }
            .map { it.id }
            .toSet()

    /** Core accepts the keys of the abonnementen it registered, and the secrets of its own configurations. */
    private fun isCoreSecret(authHeader: String): Boolean {
        val registeredByCore =
            try {
                notificatiesApiService.findAbonnementSubscription(authHeader)
                true
            } catch (e: AuthorizationException) {
                false
            }
        return registeredByCore ||
            pluginService
                .getPluginConfigurations(PluginConfigurationSearchParameters(pluginDefinitionKey = CORE_PLUGIN_KEY))
                .mapNotNull { it.properties?.path(AUTH_HEADER_PROPERTY)?.textValue() }
                .any { secretsMatch(it, authHeader) }
    }

    private fun secretsMatch(
        expected: String,
        actual: String,
    ) = MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), actual.toByteArray(Charsets.UTF_8))

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
