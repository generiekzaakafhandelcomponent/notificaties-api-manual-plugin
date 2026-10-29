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
import com.ritense.plugin.domain.PluginConfiguration
import com.ritense.plugin.domain.PluginConfigurationId
import com.ritense.plugin.domain.PluginDefinition
import com.ritense.plugin.domain.PluginProcessLink
import com.ritense.processlink.domain.ActivityTypeWithEventName
import com.ritense.valtimo.contract.json.MapperSingleton
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

object TestFixtures {
    const val ON_URL = "http://open-notificaties.example.com/api/v1/"
    const val OTHER_ON_URL = "http://other-notificaties.example.com/api/v1/"
    const val CORE_CALLBACK_URL = "http://valtimo.example.com/api/v1/notificatiesapi/callback"

    val objectMapper = MapperSingleton.get()

    val authentication: NotificatiesApiAuthentication = mock()

    fun pluginConfiguration(
        id: UUID,
        key: String,
        properties: Map<String, Any?>,
        title: String = "$key configuration",
    ): PluginConfiguration {
        val definition = mock<PluginDefinition> { on { this.key } doReturn key }
        val node = objectMapper.valueToTree<ObjectNode>(properties.filterValues { it != null })
        return mock {
            on { this.id } doReturn PluginConfigurationId.existingId(id)
            on { this.title } doReturn title
            on { this.pluginDefinition } doReturn definition
            on { this.properties } doReturn node
        }
    }

    /** A stored configuration of this plugin. */
    fun manualConfiguration(
        url: String = ON_URL,
        id: UUID = UUID.randomUUID(),
    ): PluginConfiguration = pluginConfiguration(id, NotificatiesApiManualPlugin.PLUGIN_KEY, mapOf("url" to url))

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
