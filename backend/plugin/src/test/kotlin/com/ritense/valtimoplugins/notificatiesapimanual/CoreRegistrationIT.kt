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
import com.ritense.notificatiesapi.PluginsDeployedEventListener
import com.ritense.notificatiesapi.domain.Abonnement
import com.ritense.notificatiesapi.domain.NotificatiesApiConfigurationId
import com.ritense.notificatiesapi.event.NotificatiesApiNotificationReceivedEvent
import com.ritense.notificatiesapi.repository.NotificatiesApiAbonnementLinkRepository
import com.ritense.notificatiesapi.repository.NotificatiesApiInboundEventRepository
import com.ritense.plugin.domain.PluginConfiguration
import com.ritense.plugin.service.PluginService
import com.ritense.valtimo.contract.authentication.AuthoritiesConstants.ADMIN
import com.ritense.valtimo.contract.authentication.AuthoritiesConstants.USER
import com.ritense.valtimo.contract.json.MapperSingleton
import com.ritense.valtimoplugins.notificatiesapimanual.registration.NotificatiesApiManualPluginCoreClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.context.event.EventListener
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionTemplate
import java.net.URI
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Core's own registration and callback, with a configuration of the standard plugin (as Verzoek needs) on the URL of a
 * configuration of this plugin, and one on another URL.
 */
@AutoConfigureMockMvc
@Import(CoreRegistrationIT.ReceivedEvents::class)
class CoreRegistrationIT : BaseIntegrationTest() {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var pluginService: PluginService

    @Autowired
    lateinit var coreRegistration: PluginsDeployedEventListener

    @Autowired
    lateinit var coreLinkRepository: NotificatiesApiAbonnementLinkRepository

    @Autowired
    lateinit var inboundEventRepository: NotificatiesApiInboundEventRepository

    @Autowired
    lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    lateinit var receivedEvents: ReceivedEvents

    private val createdConfigurations = mutableListOf<PluginConfiguration>()
    private lateinit var manual: PluginConfiguration
    private lateinit var coreOnManualUrl: PluginConfiguration
    private lateinit var coreOnOtherUrl: PluginConfiguration

    @BeforeEach
    fun setUp() {
        transactionTemplate.executeWithoutResult {
            coreLinkRepository.deleteAll()
            inboundEventRepository.deleteAll()
        }
        receivedEvents.events.clear()
        whenever(notificatiesApiClient.createAbonnement(any(), any(), any())).thenAnswer { invocation ->
            invocation.getArgument<Abonnement>(2).copy(url = "${OTHER_URL}abonnement/core")
        }

        val authentication =
            create(
                "notificatiesapiauthentication",
                mapOf("clientId" to "valtimo", "clientSecret" to "a-client-secret-of-at-least-32-characters"),
            )
        // First this plugin, then the standard plugin, as the form says.
        manual = create("notificatiesapi-manual", mapOf("url" to MANUAL_URL))
        coreOnManualUrl = createCore(MANUAL_URL.trimEnd('/'), authentication, authHeader = SHARED_SECRET)
        coreOnOtherUrl = createCore(OTHER_URL, authentication, authHeader = null)
    }

    @AfterEach
    fun tearDown() {
        createdConfigurations.reversed().forEach { pluginService.deletePluginConfiguration(it.id) }
        createdConfigurations.clear()
    }

    @Test
    fun `core's client bean is wrapped`() {
        assertTrue(notificatiesApiClientBean is NotificatiesApiManualPluginCoreClient)
    }

    @Test
    fun `core registers nothing on the URL of this plugin, and registers on another URL`() {
        coreRegistration.registerAbonnementenForNotificatiesApiPlugins()

        val manualUrl = URI(MANUAL_URL.trimEnd('/'))
        verify(notificatiesApiClient, never()).getAbonnementen(any(), eq(manualUrl))
        verify(notificatiesApiClient, never()).createAbonnement(any(), eq(manualUrl), any())
        verify(notificatiesApiClient, never()).createKanaal(any(), eq(manualUrl), any())
        verify(notificatiesApiClient, atLeastOnce()).createAbonnement(any(), eq(URI(OTHER_URL)), any())

        assertEquals("${MANUAL_URL.trimEnd('/')}/abonnement/registered-manually", link(coreOnManualUrl)?.url)
        assertEquals("${OTHER_URL}abonnement/core", link(coreOnOtherUrl)?.url)
    }

    @Test
    fun `core's callback accepts the Auth header of the standard configuration and publishes the event`() {
        coreRegistration.registerAbonnementenForNotificatiesApiPlugins()
        assertEquals(SHARED_SECRET, link(coreOnManualUrl)?.auth)

        mockMvc.perform(callback(SHARED_SECRET)).andExpect(status().isNoContent)
        mockMvc.perform(callback("not-the-secret")).andExpect(status().isUnauthorized)

        awaitEvents(1)
        assertEquals("objecten", receivedEvents.events.single().kanaal)
    }

    @Test
    fun `deleting the standard configuration deletes nothing in Open Notificaties`() {
        coreRegistration.registerAbonnementenForNotificatiesApiPlugins()

        pluginService.deletePluginConfiguration(coreOnManualUrl.id)
        createdConfigurations.remove(coreOnManualUrl)

        verify(notificatiesApiClient, never()).deleteAbonnement(any(), eq(URI(MANUAL_URL.trimEnd('/'))), any())
    }

    @Test
    @WithMockUser(authorities = [ADMIN])
    fun `shows the standard configuration that the abonnement belongs to`() {
        mockMvc
            .perform(get(MANUAL_VIEW_PATH, manual.id.id))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.coreConfigurations[0].id").value(coreOnManualUrl.id.id.toString()))
            .andExpect(jsonPath("$.coreConfigurations[0].authHeaderFilledIn").value(true))
            .andExpect(jsonPath("$.coreConfigurations[0].authHeader").doesNotExist())
            .andExpect(jsonPath("$.coreConfigurations.length()").value(1))
    }

    @Test
    @WithMockUser(authorities = [ADMIN])
    fun `answers 404 for an unknown configuration`() {
        mockMvc.perform(get(MANUAL_VIEW_PATH, UUID.randomUUID())).andExpect(status().isNotFound)
    }

    @Test
    @WithMockUser(authorities = [USER])
    fun `refuses a non-admin`() {
        mockMvc.perform(get(MANUAL_VIEW_PATH, manual.id.id)).andExpect(status().isForbidden)
    }

    private fun link(configuration: PluginConfiguration) =
        transactionTemplate.execute {
            coreLinkRepository.findById(NotificatiesApiConfigurationId(configuration.id.id)).orElse(null)
        }

    private fun awaitEvents(count: Int) {
        val deadline = System.currentTimeMillis() + 10_000
        while (receivedEvents.events.size < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertEquals(count, receivedEvents.events.size, "events published by core's inbound queue")
    }

    private fun callback(authorization: String) =
        post("/api/v1/notificatiesapi/callback")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", authorization)
            .content(
                """
                {
                    "kanaal": "objecten",
                    "hoofdObject": "http://localhost:8010/api/v2/objects/1",
                    "resource": "object",
                    "resourceUrl": "http://localhost:8010/api/v2/objects/1",
                    "actie": "create",
                    "aanmaakdatum": "2026-10-26T12:00:00",
                    "kenmerken": {"objectType": "http://localhost:8011/api/v2/objecttypes/1"}
                }
                """.trimIndent(),
            )

    private fun createCore(
        url: String,
        authentication: PluginConfiguration,
        authHeader: String?,
    ) = create(
        "notificatiesapi",
        mapOf(
            "url" to url,
            "callbackUrl" to "http://localhost:8080/api/v1/notificatiesapi/callback",
            "authenticationPluginConfiguration" to authentication.id.id.toString(),
            "authHeader" to authHeader,
        ),
    )

    private fun create(
        key: String,
        properties: Map<String, String?>,
    ): PluginConfiguration =
        pluginService
            .createPluginConfiguration(
                "$key test",
                MapperSingleton.get().valueToTree<ObjectNode>(properties.filterValues { it != null }),
                key,
            ).also { createdConfigurations += it }

    /** Stands in for Verzoek, Portaaltaak and external plugins, which listen to the same event. */
    @TestConfiguration
    class ReceivedEvents {
        val events = CopyOnWriteArrayList<NotificatiesApiNotificationReceivedEvent>()

        @EventListener
        fun on(event: NotificatiesApiNotificationReceivedEvent) {
            events += event
        }
    }

    companion object {
        const val MANUAL_URL = "http://localhost:8002/api/v1/"
        const val OTHER_URL = "http://localhost:8003/api/v1/"
        const val SHARED_SECRET = "a-shared-secret"
        const val MANUAL_VIEW_PATH = "/api/management/v1/notificatiesapi-manual/configurations/{id}/manual-abonnement"
    }
}
