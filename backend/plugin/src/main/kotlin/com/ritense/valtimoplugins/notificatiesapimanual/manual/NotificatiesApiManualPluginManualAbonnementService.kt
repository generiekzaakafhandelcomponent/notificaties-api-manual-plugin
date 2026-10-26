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

package com.ritense.valtimoplugins.notificatiesapimanual.manual

import com.ritense.notificatiesapi.NotificatiesApiListener
import com.ritense.notificatiesapi.NotificatiesApiPlugin
import com.ritense.notificatiesapi.domain.Abonnement
import com.ritense.plugin.repository.PluginProcessLinkRepository
import com.ritense.plugin.service.PluginConfigurationSearchParameters
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.CORE_PLUGIN_KEY
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.PLUGIN_KEY
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.RECEIVE_NOTIFICATIE_ACTION_KEY
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.normalize
import com.ritense.valtimoplugins.notificatiesapimanual.registration.NotificatiesApiManualPluginKanaalFilters
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

/**
 * What the abonnement of a manual configuration must contain: its callback URL, and the kanalen with filters of
 * - this configuration's `receive-notificatie` process links,
 * - core `receive-notificatie` links of core Notificaties API configurations on the same Open Notificaties URL, and
 * - other core Notificaties API listeners (Verzoek, Portaaltaak, external plugins) on the same URL.
 *
 * The core listeners are included because they can only point at a core configuration; behind a gateway their
 * notificaties have to arrive through this plugin's abonnement.
 */
class NotificatiesApiManualPluginManualAbonnementService(
    private val pluginService: PluginService,
    private val pluginProcessLinkRepository: PluginProcessLinkRepository,
) {
    fun getManualAbonnement(configurationId: UUID): ManualAbonnement? {
        val configuration =
            pluginService
                .getPluginConfigurations(PluginConfigurationSearchParameters(pluginDefinitionKey = PLUGIN_KEY))
                .firstOrNull { it.id.id == configurationId }
                ?: return null
        val plugin = pluginService.createInstance(configuration) as NotificatiesApiManualPlugin
        if (!plugin.isManualAbonnementRegistration()) return null

        val url = normalize(plugin.url)
        val objectMapper = pluginService.getObjectMapper()
        val ownKanalen =
            pluginProcessLinkRepository
                .findByPluginDefinitionKeyAndPluginActionDefinitionKey(PLUGIN_KEY, RECEIVE_NOTIFICATIE_ACTION_KEY)
                .filter { it.pluginConfigurationId?.id == configurationId }
                .mapNotNull { NotificatiesApiManualPluginKanaalFilters.toKanaal(it, objectMapper) }

        return ManualAbonnement(
            callbackUrl = plugin.callbackUrl.toASCIIString(),
            kanalen = (ownKanalen + coreKanalen(url)).distinct(),
        )
    }

    private fun coreKanalen(url: String): List<Abonnement.Kanaal> {
        val listeners =
            pluginService
                .getPluginConfigurations(PluginConfigurationSearchParameters(category = CORE_LISTENER_CATEGORY))
                .mapNotNull { configuration ->
                    try {
                        pluginService.createInstance(configuration) as? NotificatiesApiListener
                    } catch (e: Exception) {
                        logger.warn(e) { "Skipping Notificaties API listener configuration '${configuration.id.id}'" }
                        null
                    }
                }.filter { listener ->
                    try {
                        normalize(listener.getNotificatiesApiPlugin().url) == url
                    } catch (e: Exception) {
                        logger.warn(
                            e,
                        ) { "Skipping a Notificaties API listener whose Notificaties API configuration fails" }
                        false
                    }
                }

        // Core's own getKanaalFilters() returns the links of every core configuration, whatever its URL, so the core
        // receive-notificatie links are read here and limited to the core configurations on this URL.
        val coreConfigurationIds =
            listeners
                .filterIsInstance<NotificatiesApiPlugin>()
                .map { it.notificatiesApiConfigurationId.id }
                .toSet()
        val objectMapper = pluginService.getObjectMapper()
        val coreReceiveKanalen =
            pluginProcessLinkRepository
                .findByPluginDefinitionKeyAndPluginActionDefinitionKey(CORE_PLUGIN_KEY, RECEIVE_NOTIFICATIE_ACTION_KEY)
                .filter { it.pluginConfigurationId?.id in coreConfigurationIds }
                .mapNotNull { NotificatiesApiManualPluginKanaalFilters.toKanaal(it, objectMapper) }

        val otherListenerKanalen =
            listeners
                .filter { it !is NotificatiesApiPlugin }
                .flatMap { listener ->
                    try {
                        listener.getKanaalFilters()
                    } catch (e: Exception) {
                        logger.warn(e) { "Skipping the kanaal filters of a Notificaties API listener" }
                        emptyList()
                    }
                }

        return coreReceiveKanalen + otherListenerKanalen
    }

    data class ManualAbonnement(
        val callbackUrl: String,
        val kanalen: List<Abonnement.Kanaal>,
    )

    companion object {
        private val logger = KotlinLogging.logger {}
        private const val CORE_LISTENER_CATEGORY = "notificaties-api-plugin"
    }
}
