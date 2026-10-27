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
import com.ritense.plugin.domain.PluginConfiguration
import com.ritense.plugin.domain.PluginConfigurationId
import com.ritense.plugin.repository.PluginProcessLinkRepository
import com.ritense.plugin.service.PluginConfigurationSearchParameters
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.CALLBACK_URL
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.ON_URL
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.OTHER_ON_URL
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.receiveLink
import com.ritense.valtimoplugins.notificatiesapimanual.domain.AbonnementRegistration.MANUAL
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.URI
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificatiesApiManualPluginManualAbonnementServiceTest {
    private lateinit var pluginService: PluginService
    private lateinit var processLinkRepository: PluginProcessLinkRepository
    private lateinit var service: NotificatiesApiManualPluginManualAbonnementService

    private val manual = TestFixtures.plugin(registration = MANUAL, secret = "secret")
    private val otherManual = TestFixtures.plugin(registration = MANUAL, secret = "secret")
    private val automatic = TestFixtures.plugin()

    private val coreOnSameUrl = corePlugin("$ON_URL/".trimEnd('/') + "/")
    private val coreOnOtherUrl = corePlugin(OTHER_ON_URL)
    private val verzoekOnSameUrl =
        listener(
            coreOnSameUrl,
            Abonnement.Kanaal(
                naam = "objecten",
                filters =
                    mapOf(
                        "actie" to "create",
                    ),
            ),
        )
    private val verzoekOnOtherUrl = listener(coreOnOtherUrl, Abonnement.Kanaal(naam = "taken"))

    @BeforeEach
    fun setUp() {
        pluginService = mock()
        processLinkRepository = mock()
        service = NotificatiesApiManualPluginManualAbonnementService(pluginService, processLinkRepository)

        val ownConfigurations = listOf(manual, otherManual, automatic).associateBy { TestFixtures.configurationOf(it) }
        val coreListeners =
            listOf<NotificatiesApiListener>(coreOnSameUrl, coreOnOtherUrl, verzoekOnSameUrl, verzoekOnOtherUrl)
                .associateBy { mock<PluginConfiguration>() }

        whenever(pluginService.getObjectMapper()).thenReturn(TestFixtures.objectMapper)
        whenever(pluginService.getPluginConfigurations(any())).thenAnswer { invocation ->
            val search = invocation.getArgument<PluginConfigurationSearchParameters>(0)
            when {
                search.pluginDefinitionKey == NotificatiesApiManualPlugin.PLUGIN_KEY -> ownConfigurations.keys.toList()
                search.category == "notificaties-api-plugin" -> coreListeners.keys.toList()
                else -> emptyList()
            }
        }
        whenever(pluginService.createInstance(any<PluginConfiguration>())).thenAnswer { invocation ->
            val configuration = invocation.getArgument<PluginConfiguration>(0)
            ownConfigurations[configuration] ?: coreListeners[configuration]
        }
        whenever(
            processLinkRepository.findByPluginDefinitionKeyAndPluginActionDefinitionKey(
                eq("notificatiesapi-manual"),
                any(),
            ),
        ).thenAnswer {
            listOf(
                receiveLink(manual.configurationId.id, "zaken", "create"),
                receiveLink(otherManual.configurationId.id, "besluiten"),
                receiveLink(null, "building-block"),
            )
        }
        whenever(
            processLinkRepository.findByPluginDefinitionKeyAndPluginActionDefinitionKey(eq("notificatiesapi"), any()),
        ).thenAnswer {
            listOf(
                receiveLink(coreOnSameUrl.notificatiesApiConfigurationId.id, "documenten", "update"),
                receiveLink(coreOnOtherUrl.notificatiesApiConfigurationId.id, "klanten"),
                receiveLink(null, "core-building-block"),
            )
        }
    }

    @Test
    fun `lists its own links and the core filters of the same Open Notificaties URL, nothing else`() {
        val result = service.getManualAbonnement(manual.configurationId.id)!!

        assertEquals(CALLBACK_URL, result.callbackUrl)
        assertEquals(
            setOf(
                Abonnement.Kanaal(naam = "zaken", filters = mapOf("actie" to "create")),
                Abonnement.Kanaal(naam = "documenten", filters = mapOf("actie" to "update")),
                Abonnement.Kanaal(naam = "objecten", filters = mapOf("actie" to "create")),
            ),
            result.kanalen.toSet(),
        )
    }

    @Test
    fun `returns nothing for an automatic, unknown or other configuration`() {
        assertNull(service.getManualAbonnement(automatic.configurationId.id))
        assertNull(service.getManualAbonnement(UUID.randomUUID()))
    }

    @Test
    fun `a failing core listener is skipped`() {
        val failing = mock<NotificatiesApiListener> { on { getNotificatiesApiPlugin() } doReturn coreOnSameUrl }
        whenever(failing.getKanaalFilters()).thenThrow(IllegalStateException("broken"))
        val failingConfiguration = mock<PluginConfiguration> { on { id } doReturn PluginConfigurationId.newId() }
        val current =
            pluginService.getPluginConfigurations(
                PluginConfigurationSearchParameters(category = "notificaties-api-plugin"),
            )
        whenever(
            pluginService.getPluginConfigurations(
                eq(PluginConfigurationSearchParameters(category = "notificaties-api-plugin")),
            ),
        ).thenReturn(current + failingConfiguration)
        whenever(pluginService.createInstance(failingConfiguration)).thenReturn(failing)

        val result = service.getManualAbonnement(manual.configurationId.id)!!

        assertEquals(3, result.kanalen.size)
    }

    private fun corePlugin(url: String): NotificatiesApiPlugin =
        NotificatiesApiPlugin(
            PluginConfigurationId.existingId(UUID.randomUUID()),
            mock(),
            TestFixtures.objectMapper,
            mock(),
        ).apply { this.url = URI(url) }

    private fun listener(
        core: NotificatiesApiPlugin,
        kanaal: Abonnement.Kanaal,
    ) = object : NotificatiesApiListener {
        override fun getNotificatiesApiPlugin() = core

        override fun getKanaalFilters() = listOf(kanaal)
    }
}
