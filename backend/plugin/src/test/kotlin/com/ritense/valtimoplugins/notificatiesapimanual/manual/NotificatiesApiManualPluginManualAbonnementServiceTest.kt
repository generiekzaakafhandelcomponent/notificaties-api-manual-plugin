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
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.CORE_CALLBACK_URL
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.ON_URL
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.OTHER_ON_URL
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.receiveLink
import com.ritense.valtimoplugins.notificatiesapimanual.manual.NotificatiesApiManualPluginManualAbonnementService.CoreConfiguration
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

    private val manual = TestFixtures.manualConfiguration(url = ON_URL.trimEnd('/'))

    private val coreOnSameUrl = corePlugin(ON_URL)
    private val coreOnOtherUrl = corePlugin(OTHER_ON_URL)
    private val verzoekOnSameUrl =
        listener(coreOnSameUrl, Abonnement.Kanaal(naam = "objecten", filters = mapOf("actie" to "create")))
    private val verzoekOnOtherUrl = listener(coreOnOtherUrl, Abonnement.Kanaal(naam = "taken"))

    private lateinit var coreConfigurations: List<PluginConfiguration>
    private lateinit var coreListeners: Map<PluginConfiguration, NotificatiesApiListener>

    @BeforeEach
    fun setUp() {
        pluginService = mock()
        processLinkRepository = mock()
        service = NotificatiesApiManualPluginManualAbonnementService(pluginService, processLinkRepository)

        coreConfigurations =
            listOf(
                coreConfiguration(coreOnSameUrl, "Notificaties API via gateway", authHeader = "a-shared-secret"),
                coreConfiguration(coreOnOtherUrl, "Notificaties API elders", authHeader = null),
            )
        coreListeners =
            listOf<NotificatiesApiListener>(coreOnSameUrl, coreOnOtherUrl, verzoekOnSameUrl, verzoekOnOtherUrl)
                .associateBy { mock<PluginConfiguration>() }

        whenever(pluginService.getObjectMapper()).thenReturn(TestFixtures.objectMapper)
        whenever(pluginService.getPluginConfigurations(any())).thenAnswer { invocation ->
            val search = invocation.getArgument<PluginConfigurationSearchParameters>(0)
            when {
                search.pluginDefinitionKey == "notificatiesapi-manual" -> listOf(manual)
                search.pluginDefinitionKey == "notificatiesapi" -> coreConfigurations
                search.category == "notificaties-api-plugin" -> coreListeners.keys.toList()
                else -> emptyList()
            }
        }
        whenever(pluginService.createInstance(any<PluginConfiguration>())).thenAnswer { invocation ->
            coreListeners[invocation.getArgument<PluginConfiguration>(0)]
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
    fun `lists the standard configurations on the same URL, without their secret`() {
        val result = service.getManualAbonnement(manual.id.id)!!

        assertEquals(
            listOf(
                CoreConfiguration(
                    id = coreOnSameUrl.notificatiesApiConfigurationId.id,
                    title = "Notificaties API via gateway",
                    callbackUrl = CORE_CALLBACK_URL,
                    authHeaderFilledIn = true,
                ),
            ),
            result.coreConfigurations,
        )
    }

    @Test
    fun `tells when the Auth header of a standard configuration is empty`() {
        coreConfigurations = listOf(coreConfiguration(coreOnSameUrl, "Zonder geheim", authHeader = " "))

        assertEquals(
            false,
            service
                .getManualAbonnement(manual.id.id)!!
                .coreConfigurations
                .single()
                .authHeaderFilledIn,
        )
    }

    @Test
    fun `lists the kanalen of the standard configurations and listeners on the same URL, nothing else`() {
        val result = service.getManualAbonnement(manual.id.id)!!

        assertEquals(
            setOf(
                Abonnement.Kanaal(naam = "documenten", filters = mapOf("actie" to "update")),
                Abonnement.Kanaal(naam = "objecten", filters = mapOf("actie" to "create")),
            ),
            result.kanalen.toSet(),
        )
    }

    @Test
    fun `lists nothing to do yet before the standard configuration exists`() {
        coreConfigurations = emptyList()
        coreListeners = emptyMap()

        val result = service.getManualAbonnement(manual.id.id)!!

        assertEquals(emptyList(), result.coreConfigurations)
        assertEquals(emptyList(), result.kanalen)
    }

    @Test
    fun `returns nothing for an unknown configuration`() {
        assertNull(service.getManualAbonnement(UUID.randomUUID()))
    }

    @Test
    fun `a failing listener is skipped`() {
        val failing = mock<NotificatiesApiListener> { on { getNotificatiesApiPlugin() } doReturn coreOnSameUrl }
        whenever(failing.getKanaalFilters()).thenThrow(IllegalStateException("broken"))
        val failingConfiguration = mock<PluginConfiguration> { on { id } doReturn PluginConfigurationId.newId() }
        coreListeners = coreListeners + (failingConfiguration to failing)

        assertEquals(2, service.getManualAbonnement(manual.id.id)!!.kanalen.size)
    }

    private fun corePlugin(url: String): NotificatiesApiPlugin =
        NotificatiesApiPlugin(
            PluginConfigurationId.existingId(UUID.randomUUID()),
            mock(),
            TestFixtures.objectMapper,
            mock(),
        ).apply { this.url = URI(url) }

    private fun coreConfiguration(
        core: NotificatiesApiPlugin,
        title: String,
        authHeader: String?,
    ) = TestFixtures.pluginConfiguration(
        core.notificatiesApiConfigurationId.id,
        "notificatiesapi",
        mapOf("url" to core.url.toString(), "callbackUrl" to CORE_CALLBACK_URL, "authHeader" to authHeader),
        title,
    )

    private fun listener(
        core: NotificatiesApiPlugin,
        kanaal: Abonnement.Kanaal,
    ) = object : NotificatiesApiListener {
        override fun getNotificatiesApiPlugin() = core

        override fun getKanaalFilters() = listOf(kanaal)
    }
}
