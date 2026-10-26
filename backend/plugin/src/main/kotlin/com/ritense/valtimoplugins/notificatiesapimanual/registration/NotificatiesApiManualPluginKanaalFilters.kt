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
import com.ritense.plugin.domain.PluginProcessLink
import com.ritense.plugin.repository.PluginProcessLinkRepository
import com.ritense.plugin.service.PluginConfigurationSearchParameters
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.notificatiesapimanual.listener.ReceiveNotificatieProperties
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.PLUGIN_KEY
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.RECEIVE_NOTIFICATIE_ACTION_KEY
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.normalize
import java.util.UUID

/**
 * Attributes the kanaal filters of this plugin's `receive-notificatie` process links to its configurations: a link
 * belongs to the abonnement of its own configuration's Open Notificaties URL, and a manual configuration's links never
 * go into an automatic abonnement. Links without a configuration (building blocks) go into every automatic abonnement.
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

    fun hasManualConfiguration(url: String): Boolean =
        manualConfigurations().any { normalize(it.url) == normalize(url) }

    fun manualCallbackUrls(): Set<String> = manualConfigurations().map { normalize(it.callbackUrl) }.toSet()

    /** Kanaal filters of one automatic abonnement, for the automatic configurations of one Open Notificaties URL. */
    fun automaticKanaalFilters(group: List<NotificatiesApiManualPlugin>): List<Abonnement.Kanaal> {
        val groupIds = group.filter { !it.isManualAbonnementRegistration() }.map { it.configurationId.id }.toSet()
        return kanaalFilters { it == null || it in groupIds }
    }

    fun manualKanaalFilters(configurationId: UUID): List<Abonnement.Kanaal> = kanaalFilters { it == configurationId }

    private fun kanaalFilters(linkedTo: (UUID?) -> Boolean): List<Abonnement.Kanaal> =
        receiveLinks
            .filter { linkedTo(it.pluginConfigurationId?.id) }
            .mapNotNull { toKanaal(it, objectMapper) }
            .distinct()

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

        /** The kanaal filter of a `receive-notificatie` link, in the shape of an abonnement kanaal; null without kanaal. */
        fun toKanaal(
            link: PluginProcessLink,
            objectMapper: ObjectMapper,
        ): Abonnement.Kanaal? {
            val properties = link.actionProperties ?: return null
            val notificatie = objectMapper.treeToValue(properties, ReceiveNotificatieProperties::class.java)
            val kanaal = notificatie.kanaal?.takeIf { it.isNotBlank() } ?: return null
            val filters =
                buildMap {
                    notificatie.actie?.takeIf { it.isNotBlank() }?.let { put("actie", it) }
                    notificatie.kenmerken?.let { putAll(it) }
                }
            return Abonnement.Kanaal(naam = kanaal, filters = filters)
        }
    }
}
