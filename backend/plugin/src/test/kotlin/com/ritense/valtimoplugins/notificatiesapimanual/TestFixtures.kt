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

package com.ritense.valtimoplugins.notificatiesapimanual

import com.fasterxml.jackson.databind.node.ObjectNode
import com.ritense.notificatiesapi.NotificatiesApiAuthentication
import com.ritense.notificatiesapi.client.NotificatiesApiClient
import com.ritense.plugin.domain.PluginConfiguration
import com.ritense.plugin.domain.PluginConfigurationId
import com.ritense.plugin.domain.PluginDefinition
import com.ritense.plugin.domain.PluginProcessLink
import com.ritense.plugin.service.PluginService
import com.ritense.processlink.domain.ActivityTypeWithEventName
import com.ritense.valtimo.contract.json.MapperSingleton
import com.ritense.valtimoplugins.notificatiesapimanual.domain.AbonnementRegistration
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.net.URI
import java.util.UUID

object TestFixtures {
    const val ON_URL = "http://open-notificaties.example.com/api/v1/"
    const val OTHER_ON_URL = "http://other-notificaties.example.com/api/v1/"
    const val CALLBACK_URL = "http://valtimo.example.com/api/v1/notificatiesapi-manual/callback"
    const val CORE_CALLBACK_URL = "http://valtimo.example.com/api/v1/notificatiesapi/callback"

    val objectMapper = MapperSingleton.get()

    val authentication: NotificatiesApiAuthentication = mock()

    fun plugin(
        id: UUID = UUID.randomUUID(),
        registration: AbonnementRegistration = AbonnementRegistration.AUTOMATIC,
        secret: String? = null,
        url: String = ON_URL,
        callbackUrl: String = CALLBACK_URL,
        client: NotificatiesApiClient = mock(),
        pluginService: PluginService = mock(),
    ) = NotificatiesApiManualPlugin(PluginConfigurationId.existingId(id), client, pluginService).apply {
        this.url = URI(url)
        this.callbackUrl = URI(callbackUrl)
        this.authHeader = secret
        this.abonnementRegistration = registration
        this.authenticationPluginConfiguration = authentication
    }

    fun pluginConfiguration(
        id: UUID,
        key: String,
        properties: Map<String, Any?>,
    ): PluginConfiguration {
        val definition = mock<PluginDefinition> { on { this.key } doReturn key }
        val node = objectMapper.valueToTree<ObjectNode>(properties.filterValues { it != null })
        return mock {
            on { this.id } doReturn PluginConfigurationId.existingId(id)
            on { this.pluginDefinition } doReturn definition
            on { this.properties } doReturn node
        }
    }

    fun configurationOf(plugin: NotificatiesApiManualPlugin): PluginConfiguration =
        pluginConfiguration(
            plugin.configurationId.id,
            NotificatiesApiManualPlugin.PLUGIN_KEY,
            mapOf(
                "url" to plugin.url.toString(),
                "callbackUrl" to plugin.callbackUrl.toString(),
                "authHeader" to plugin.authHeader,
                "abonnementRegistration" to plugin.abonnementRegistration.value,
            ),
        )

    fun receiveLink(
        configurationId: UUID?,
        kanaal: String?,
        actie: String? = null,
        kenmerken: Map<String, String>? = null,
    ): PluginProcessLink {
        val properties =
            objectMapper.valueToTree<ObjectNode>(
                mapOf("kanaal" to kanaal, "actie" to actie, "kenmerken" to kenmerken).filterValues { it != null },
            )
        return mock {
            on { this.pluginConfigurationId } doReturn configurationId?.let { PluginConfigurationId.existingId(it) }
            on { this.actionProperties } doReturn properties
            on { this.activityType } doReturn ActivityTypeWithEventName.RECEIVE_TASK_END
        }
    }
}
