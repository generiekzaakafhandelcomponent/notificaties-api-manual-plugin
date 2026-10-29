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

package com.ritense.valtimoplugins.notificatiesapimanual.plugin

import com.ritense.notificatiesapi.NotificatiesApiPlugin
import com.ritense.plugin.annotation.Plugin
import com.ritense.plugin.annotation.PluginProperty
import com.ritense.plugin.domain.PluginConfigurationId
import com.ritense.plugin.service.PluginConfigurationSearchParameters
import com.ritense.plugin.service.PluginService
import com.ritense.valtimo.contract.validation.Url
import java.net.URI

/**
 * Marks an Open Notificaties in which an administrator creates the abonnement of the standard Notificaties API plugin
 * (gzac-issues#971), for example because a gateway that checks certificates refuses the test message Open Notificaties
 * sends when Valtimo registers it. Configurations of the standard plugin on [url] keep working as usual (Verzoek,
 * Portaaltaak, process links), but Valtimo creates, changes and deletes nothing in that Open Notificaties for them.
 */
@Plugin(
    key = NotificatiesApiManualPlugin.PLUGIN_KEY,
    title = "Notificaties API, handmatig abonnement",
    description = "Open Notificaties waarin een beheerder het abonnement van de Notificaties API-plugin aanmaakt",
)
class NotificatiesApiManualPlugin(
    pluginConfigurationId: PluginConfigurationId,
) {
    val configurationId: PluginConfigurationId = pluginConfigurationId

    @Url
    @PluginProperty(key = URL_PROPERTY, secret = false)
    lateinit var url: URI

    companion object {
        const val PLUGIN_KEY = "notificatiesapi-manual"
        const val CORE_PLUGIN_KEY = NotificatiesApiPlugin.NOTIFICATIES_API_PLUGIN_DEFINITION_KEY
        const val RECEIVE_NOTIFICATIE_ACTION_KEY = NotificatiesApiPlugin.RECEIVE_NOTIFICATIE_ACTION_KEY
        const val URL_PROPERTY = "url"

        fun normalize(url: URI): String = normalize(url.toString())

        fun normalize(url: String): String = url.trim().trimEnd('/')

        /** The normalized URLs of this plugin's configurations, without instantiating them. */
        fun manualUrls(pluginService: PluginService): Set<String> =
            pluginService
                .getPluginConfigurations(PluginConfigurationSearchParameters(pluginDefinitionKey = PLUGIN_KEY))
                .mapNotNull { it.properties?.path(URL_PROPERTY)?.textValue() }
                .map { normalize(it) }
                .toSet()
    }
}
