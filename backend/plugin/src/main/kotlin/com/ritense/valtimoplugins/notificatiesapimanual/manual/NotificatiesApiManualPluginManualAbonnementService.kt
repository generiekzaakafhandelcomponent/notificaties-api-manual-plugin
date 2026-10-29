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
import com.ritense.notificatiesapi.listener.ReceiveNotificatieProperties
import com.ritense.plugin.annotation.PluginCategory
import com.ritense.plugin.repository.PluginProcessLinkRepository
import com.ritense.plugin.service.PluginConfigurationSearchParameters
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.CORE_PLUGIN_KEY
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.PLUGIN_KEY
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.RECEIVE_NOTIFICATIE_ACTION_KEY
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.URL_PROPERTY
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.normalize
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

/**
 * What an administrator still has to do for a configuration of this plugin: the configurations of the standard
 * Notificaties API plugin on its URL, and the kanalen with filters the abonnement must contain, of
 * - `receive-notificatie` process links of those configurations, and
 * - other Notificaties API listeners (Verzoek, Portaaltaak, external plugins) linked to them.
 */
class NotificatiesApiManualPluginManualAbonnementService(
    private val pluginService: PluginService,
    private val pluginProcessLinkRepository: PluginProcessLinkRepository,
) {
    fun getManualAbonnement(configurationId: UUID): ManualAbonnement? {
        val url =
            pluginService
                .getPluginConfigurations(PluginConfigurationSearchParameters(pluginDefinitionKey = PLUGIN_KEY))
                .firstOrNull { it.id.id == configurationId }
                ?.properties
                ?.path(URL_PROPERTY)
                ?.textValue()
                ?.let { normalize(it) }
                ?: return null

        val listeners = listenersOn(url)
        val coreConfigurations =
            pluginService
                .getPluginConfigurations(PluginConfigurationSearchParameters(pluginDefinitionKey = CORE_PLUGIN_KEY))
                .filter {
                    it.properties
                        ?.path(URL_PROPERTY)
                        ?.textValue()
                        ?.let { coreUrl -> normalize(coreUrl) } == url
                }.map { configuration ->
                    val properties = configuration.properties
                    CoreConfiguration(
                        id = configuration.id.id,
                        title = configuration.title,
                        callbackUrl = properties?.path(CALLBACK_URL_PROPERTY)?.textValue(),
                        authHeaderFilledIn = !properties?.path(AUTH_HEADER_PROPERTY)?.textValue().isNullOrBlank(),
                    )
                }

        return ManualAbonnement(coreConfigurations = coreConfigurations, kanalen = kanalen(listeners).distinct())
    }

    private fun listenersOn(url: String): List<NotificatiesApiListener> =
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
                    logger.warn(e) { "Skipping a Notificaties API listener whose Notificaties API configuration fails" }
                    false
                }
            }

    private fun kanalen(listeners: List<NotificatiesApiListener>): List<Abonnement.Kanaal> {
        // Core's own getKanaalFilters() returns the links of every core configuration, whatever its URL, so the core
        // receive-notificatie links are read here and limited to the core configurations on this URL.
        val coreConfigurationIds =
            listeners
                .filterIsInstance<NotificatiesApiPlugin>()
                .map { it.notificatiesApiConfigurationId.id }
                .toSet()
        val coreReceiveKanalen =
            pluginProcessLinkRepository
                .findByPluginDefinitionKeyAndPluginActionDefinitionKey(CORE_PLUGIN_KEY, RECEIVE_NOTIFICATIE_ACTION_KEY)
                .filter { it.pluginConfigurationId?.id in coreConfigurationIds }
                .mapNotNull { link ->
                    val properties = link.actionProperties ?: return@mapNotNull null
                    val notificatie =
                        pluginService.getObjectMapper().treeToValue(
                            properties,
                            ReceiveNotificatieProperties::class.java,
                        )
                    val kanaal = notificatie.kanaal?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val filters =
                        buildMap {
                            notificatie.actie?.takeIf { it.isNotBlank() }?.let { put("actie", it) }
                            notificatie.kenmerken?.let { putAll(it) }
                        }
                    Abonnement.Kanaal(naam = kanaal, filters = filters)
                }

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
        /** The standard plugin's configurations on this URL; core only accepts the Auth header of one per URL. */
        val coreConfigurations: List<CoreConfiguration>,
        val kanalen: List<Abonnement.Kanaal>,
    )

    /** Without the value of the Auth header: the form only needs to know whether it is filled in. */
    data class CoreConfiguration(
        val id: UUID,
        val title: String,
        val callbackUrl: String?,
        val authHeaderFilledIn: Boolean,
    )

    companion object {
        private val logger = KotlinLogging.logger {}
        private const val CALLBACK_URL_PROPERTY = "callbackUrl"
        private const val AUTH_HEADER_PROPERTY = "authHeader"
        private val CORE_LISTENER_CATEGORY =
            NotificatiesApiListener::class.java.getAnnotation(PluginCategory::class.java).key
    }
}
