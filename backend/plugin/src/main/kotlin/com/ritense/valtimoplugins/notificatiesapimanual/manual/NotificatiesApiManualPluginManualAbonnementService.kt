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

import com.ritense.notificatiesapi.domain.Abonnement
import com.ritense.plugin.service.PluginConfigurationSearchParameters
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.PLUGIN_KEY
import java.util.UUID

/**
 * What the abonnement of a manual configuration must contain: its callback URL and the kanaal that Valtimo 12 core
 * registers automatically (`objecten`, without filters).
 */
class NotificatiesApiManualPluginManualAbonnementService(
    private val pluginService: PluginService,
) {
    fun getManualAbonnement(configurationId: UUID): ManualAbonnement? {
        val configuration =
            pluginService
                .getPluginConfigurations(PluginConfigurationSearchParameters(pluginDefinitionKey = PLUGIN_KEY))
                .firstOrNull { it.id.id == configurationId }
                ?: return null
        val plugin = pluginService.createInstance(configuration) as NotificatiesApiManualPlugin
        if (!plugin.isManualAbonnementRegistration()) return null
        return ManualAbonnement(callbackUrl = plugin.callbackUrl.toASCIIString(), kanalen = plugin.kanalen())
    }

    data class ManualAbonnement(
        val callbackUrl: String,
        val kanalen: List<Abonnement.Kanaal>,
    )
}
