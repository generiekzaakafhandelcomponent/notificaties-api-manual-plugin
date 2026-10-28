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
import com.ritense.notificatiesapi.domain.NotificatiesApiAbonnementLink
import com.ritense.notificatiesapi.domain.NotificatiesApiConfigurationId
import com.ritense.notificatiesapi.event.NotificatiesApiNotificationReceivedEvent
import com.ritense.notificatiesapi.repository.NotificatiesApiAbonnementLinkRepository
import com.ritense.notificatiesapi.repository.NotificatiesApiInboundEventRepository
import com.ritense.plugin.domain.PluginConfiguration
import com.ritense.plugin.service.PluginService
import com.ritense.valtimo.contract.authentication.AuthoritiesConstants.ADMIN
import com.ritense.valtimo.contract.authentication.AuthoritiesConstants.USER
import com.ritense.valtimo.contract.json.MapperSingleton
import com.ritense.valtimoplugins.notificatiesapimanual.repository.NotificatiesApiManualPluginMisroutedCallbackRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals

@AutoConfigureMockMvc
@Import(CallbackIT.ReceivedEvents::class)
class CallbackIT : BaseIntegrationTest() {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var pluginService: PluginService

    @Autowired
    lateinit var inboundEventRepository: NotificatiesApiInboundEventRepository

    @Autowired
    lateinit var coreLinkRepository: NotificatiesApiAbonnementLinkRepository

    @Autowired
    lateinit var misroutedCallbackRepository: NotificatiesApiManualPluginMisroutedCallbackRepository

    @Autowired
    lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    lateinit var receivedEvents: ReceivedEvents

    private val createdConfigurations = mutableListOf<PluginConfiguration>()
    private lateinit var manual: PluginConfiguration

    @BeforeEach
    fun setUp() {
        transactionTemplate.executeWithoutResult {
            inboundEventRepository.deleteAll()
            coreLinkRepository.deleteAll()
            misroutedCallbackRepository.deleteAll()
        }
        receivedEvents.events.clear()
        val authentication =
            create(
                "notificatiesapiauthentication",
                mapOf("clientId" to "valtimo", "clientSecret" to "a-client-secret-of-at-least-32-characters"),
            )
        manual =
            create(
                "notificatiesapi-manual",
                mapOf(
                    "url" to "http://localhost:8002/api/v1/",
                    "callbackUrl" to MANUAL_CALLBACK_URL,
                    "authenticationPluginConfiguration" to authentication.id.id.toString(),
                    "abonnementRegistration" to "manual",
                    "authHeader" to SHARED_SECRET,
                ),
            )
    }

    @AfterEach
    fun tearDown() {
        createdConfigurations.reversed().forEach { pluginService.deletePluginConfiguration(it.id) }
        createdConfigurations.clear()
    }

    @Test
    fun `accepts the shared secret, queues the notificatie and publishes the core event`() {
        mockMvc.perform(callback(SHARED_SECRET)).andExpect(status().isNoContent)

        assertEquals(1, inboundEventRepository.count())
        awaitEvents(1)
        assertEquals("objecten", receivedEvents.events.single().kanaal)
    }

    @Test
    fun `rejects another secret with 401 and a missing header with 403, queuing nothing`() {
        mockMvc.perform(callback("not-the-secret")).andExpect(status().isUnauthorized)
        mockMvc.perform(callback(null)).andExpect(status().isForbidden)

        assertEquals(0, inboundEventRepository.count())
    }

    @Test
    fun `a notificatie that arrives through core and through this plugin is processed once`() {
        transactionTemplate.executeWithoutResult {
            coreLinkRepository.save(
                NotificatiesApiAbonnementLink(
                    NotificatiesApiConfigurationId(UUID.randomUUID()),
                    "http://localhost:8002/api/v1/abonnement/core",
                    "core-key",
                ),
            )
        }

        mockMvc.perform(callback("core-key", path = CORE_CALLBACK_PATH)).andExpect(status().isNoContent)
        mockMvc.perform(callback(SHARED_SECRET)).andExpect(status().isNoContent)

        assertEquals(1, inboundEventRepository.count())
        awaitEvents(1)
        Thread.sleep(500)
        assertEquals(1, receivedEvents.events.size)
    }

    @Test
    @WithMockUser(authorities = [ADMIN])
    fun `shows an admin the callback URL of a manual configuration`() {
        mockMvc
            .perform(get(MANUAL_VIEW_PATH, manual.id.id))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.callbackUrl").value(MANUAL_CALLBACK_URL))
            .andExpect(jsonPath("$.kanalen.length()").value(0))
    }

    @Test
    @WithMockUser(authorities = [ADMIN])
    fun `warns an admin about notificaties for a manual configuration on core's address until one arrives here`() {
        mockMvc
            .perform(get(MANUAL_VIEW_PATH, manual.id.id))
            .andExpect(jsonPath("$.misroutedCallbackAt").doesNotExist())

        mockMvc.perform(callback(SHARED_SECRET, path = CORE_CALLBACK_PATH)).andExpect(status().isUnauthorized)

        assertEquals(0, inboundEventRepository.count())
        mockMvc
            .perform(get(MANUAL_VIEW_PATH, manual.id.id))
            .andExpect(jsonPath("$.misroutedCallbackAt").isString)

        mockMvc.perform(callback(SHARED_SECRET)).andExpect(status().isNoContent)

        mockMvc
            .perform(get(MANUAL_VIEW_PATH, manual.id.id))
            .andExpect(jsonPath("$.misroutedCallbackAt").doesNotExist())
    }

    @Test
    fun `notices the test notificatie on core's address, which core still answers`() {
        mockMvc
            .perform(callback(SHARED_SECRET, path = CORE_CALLBACK_PATH, kanaal = "test"))
            .andExpect(status().isNoContent)

        assertEquals(1, misroutedCallbackRepository.count())
    }

    @Test
    fun `does not notice core's own notificaties on core's address`() {
        transactionTemplate.executeWithoutResult {
            coreLinkRepository.save(
                NotificatiesApiAbonnementLink(
                    NotificatiesApiConfigurationId(UUID.randomUUID()),
                    "http://localhost:8002/api/v1/abonnement/core",
                    SHARED_SECRET,
                ),
            )
        }

        mockMvc.perform(callback(SHARED_SECRET, path = CORE_CALLBACK_PATH)).andExpect(status().isNoContent)
        mockMvc.perform(callback("not-a-secret", path = CORE_CALLBACK_PATH)).andExpect(status().isUnauthorized)

        assertEquals(0, misroutedCallbackRepository.count())
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

    private fun create(
        key: String,
        properties: Map<String, String>,
    ): PluginConfiguration =
        pluginService
            .createPluginConfiguration("$key test", MapperSingleton.get().valueToTree<ObjectNode>(properties), key)
            .also { createdConfigurations += it }

    private fun awaitEvents(count: Int) {
        val deadline = System.currentTimeMillis() + 10_000
        while (receivedEvents.events.size < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertEquals(count, receivedEvents.events.size, "events published by core's inbound queue")
    }

    private fun callback(
        authorization: String?,
        path: String = "/api/v1/notificatiesapi-manual/callback",
        kanaal: String = "objecten",
    ) = post(path)
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {
                "kanaal": "$kanaal",
                "hoofdObject": "http://localhost:8010/api/v2/objects/1",
                "resource": "object",
                "resourceUrl": "http://localhost:8010/api/v2/objects/1",
                "actie": "create",
                "aanmaakdatum": "2026-10-26T12:00:00",
                "kenmerken": {"objectType": "http://localhost:8011/api/v2/objecttypes/1"}
            }
            """.trimIndent(),
        ).apply { if (authorization != null) header("Authorization", authorization) }

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
        const val SHARED_SECRET = "a-shared-secret"
        const val CORE_CALLBACK_PATH = "/api/v1/notificatiesapi/callback"
        const val MANUAL_CALLBACK_URL =
            "https://gateway.example.com/valtimo/api/v1/notificatiesapi-manual/callback"
        const val MANUAL_VIEW_PATH = "/api/management/v1/notificatiesapi-manual/configurations/{id}/manual-abonnement"
    }
}
