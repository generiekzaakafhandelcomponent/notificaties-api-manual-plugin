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

import com.fasterxml.jackson.databind.ObjectMapper
import com.ritense.notificatiesapi.domain.Abonnement
import com.ritense.notificatiesapi.listener.ReceiveNotificatieProperties
import com.ritense.plugin.domain.PluginProcessLink
import com.ritense.plugin.repository.PluginProcessLinkRepository
import com.ritense.plugin.service.PluginConfigurationSearchParameters
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.PLUGIN_KEY
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.RECEIVE_NOTIFICATIE_ACTION_KEY
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.normalize
import java.net.URI

/**
 * Attributes the kanaal filters of this plugin's `receive-notificatie` process links to its configurations: a link
 * belongs to the abonnement of its own configuration's Open Notificaties URL. Links without a configuration (building
 * blocks) would go into every automatic abonnement, but core's link query never returns them.
 */
class NotificatiesApiManualPluginKanaalFilters(
    val configurations: List<NotificatiesApiManualPlugin>,
    private val receiveLinks: List<PluginProcessLink>,
    private val objectMapper: ObjectMapper,
) {
    fun automaticConfigurations(): List<NotificatiesApiManualPlugin> =
        configurations.filter { !it.isManualAbonnementRegistration() }

    fun manualConfigurations(): List<NotificatiesApiManualPlugin> =
        configurations.filter { it.isManualAbonnementRegistration() }

    fun hasManualConfiguration(url: URI): Boolean = manualConfigurations().any { normalize(it.url) == normalize(url) }

    fun manualCallbackUrls(): Set<String> =
        manualConfigurations().mapNotNull { configuration -> configuration.callbackUrl?.let { normalize(it) } }.toSet()

    /** The abonnement of such a configuration can be on any Open Notificaties URL, with any callback URL. */
    fun hasManualConfigurationWithoutCallbackUrl(): Boolean = manualConfigurations().any { it.callbackUrl == null }

    /** Kanaal filters of one automatic abonnement, for the automatic configurations of one Open Notificaties URL. */
    fun automaticKanaalFilters(group: List<NotificatiesApiManualPlugin>): List<Abonnement.Kanaal> {
        val groupIds = group.map { it.configurationId.id }.toSet()
        val links = receiveLinks.filter { link -> link.pluginConfigurationId?.id.let { it == null || it in groupIds } }
        return kanalen(links, objectMapper)
    }

    companion object {
        fun fromPluginService(
            pluginService: PluginService,
            pluginProcessLinkRepository: PluginProcessLinkRepository,
        ) = NotificatiesApiManualPluginKanaalFilters(
            configurations =
                pluginService
                    .getPluginConfigurations(PluginConfigurationSearchParameters(pluginDefinitionKey = PLUGIN_KEY))
                    .map { pluginService.createInstance(it) as NotificatiesApiManualPlugin },
            receiveLinks =
                pluginProcessLinkRepository.findByPluginDefinitionKeyAndPluginActionDefinitionKey(
                    PLUGIN_KEY,
                    RECEIVE_NOTIFICATIE_ACTION_KEY,
                ),
            objectMapper = pluginService.getObjectMapper(),
        )

        /** The kanaal filters of `receive-notificatie` links, in the shape of abonnement kanalen; links without kanaal are skipped. */
        fun kanalen(
            links: List<PluginProcessLink>,
            objectMapper: ObjectMapper,
        ): List<Abonnement.Kanaal> =
            links
                .mapNotNull { link ->
                    val properties = link.actionProperties ?: return@mapNotNull null
                    val notificatie = objectMapper.treeToValue(properties, ReceiveNotificatieProperties::class.java)
                    val kanaal = notificatie.kanaal?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val filters =
                        buildMap {
                            notificatie.actie?.takeIf { it.isNotBlank() }?.let { put("actie", it) }
                            notificatie.kenmerken?.let { putAll(it) }
                        }
                    Abonnement.Kanaal(naam = kanaal, filters = filters)
                }.distinct()
    }
}
