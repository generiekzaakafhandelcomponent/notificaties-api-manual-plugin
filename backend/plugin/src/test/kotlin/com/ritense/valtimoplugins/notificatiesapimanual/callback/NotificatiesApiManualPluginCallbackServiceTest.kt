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

package com.ritense.valtimoplugins.notificatiesapimanual.callback

import com.ritense.notificatiesapi.event.NotificatiesApiNotificationReceivedEvent
import com.ritense.notificatiesapi.service.NotificatiesApiService
import com.ritense.plugin.domain.PluginConfiguration
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures
import com.ritense.valtimoplugins.notificatiesapimanual.domain.AbonnementRegistration.MANUAL
import com.ritense.valtimoplugins.notificatiesapimanual.domain.NotificatiesApiManualPluginAbonnementLink
import com.ritense.valtimoplugins.notificatiesapimanual.domain.NotificatiesApiManualPluginConfigurationId
import com.ritense.valtimoplugins.notificatiesapimanual.repository.NotificatiesApiManualPluginAbonnementLinkRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificatiesApiManualPluginCallbackServiceTest {
    private lateinit var pluginService: PluginService
    private lateinit var linkRepository: NotificatiesApiManualPluginAbonnementLinkRepository
    private lateinit var notificatiesApiService: NotificatiesApiService
    private lateinit var service: NotificatiesApiManualPluginCallbackService
    private val configurations = mutableListOf<PluginConfiguration>()
    private val links = mutableListOf<NotificatiesApiManualPluginAbonnementLink>()

    @BeforeEach
    fun setUp() {
        pluginService = mock()
        linkRepository = mock()
        notificatiesApiService = mock()
        service = NotificatiesApiManualPluginCallbackService(pluginService, linkRepository, notificatiesApiService)
        whenever(pluginService.getPluginConfigurations(any())).thenAnswer { configurations.toList() }
        whenever(linkRepository.findAllByAuth(any())).thenAnswer { invocation ->
            links.filter { it.auth == invocation.getArgument<String>(0) }
        }
    }

    @Test
    fun `accepts the shared secret of a manual configuration`() {
        configurations +=
            TestFixtures.configurationOf(TestFixtures.plugin(registration = MANUAL, secret = "manual-secret"))

        assertTrue(service.isAuthorizedCallback("manual-secret"))
        assertFalse(service.isAuthorizedCallback("other"))
    }

    @Test
    fun `accepts the key of an abonnement registered for an existing automatic configuration`() {
        val plugin = TestFixtures.plugin().also { configurations += TestFixtures.configurationOf(it) }
        links += link(plugin.configurationId.id, "generated-key")

        assertTrue(service.isAuthorizedCallback("generated-key"))
    }

    @Test
    fun `does not accept the link of a configuration that is now manual`() {
        val plugin =
            TestFixtures.plugin(registration = MANUAL, secret = "new-secret").also {
                configurations +=
                    TestFixtures.configurationOf(it)
            }
        links += link(plugin.configurationId.id, "old-key")

        assertFalse(service.isAuthorizedCallback("old-key"))
        assertTrue(service.isAuthorizedCallback("new-secret"))
    }

    @Test
    fun `does not accept the link of a deleted configuration`() {
        links += link(UUID.randomUUID(), "left-over-key")

        assertFalse(service.isAuthorizedCallback("left-over-key"))
    }

    @Test
    fun `two configurations may share a key`() {
        val first = TestFixtures.plugin().also { configurations += TestFixtures.configurationOf(it) }
        val second = TestFixtures.plugin().also { configurations += TestFixtures.configurationOf(it) }
        links += link(first.configurationId.id, "shared")
        links += link(second.configurationId.id, "shared")
        configurations += TestFixtures.configurationOf(TestFixtures.plugin(registration = MANUAL, secret = "shared"))

        assertTrue(service.isAuthorizedCallback("shared"))
    }

    @Test
    fun `a blank header never matches`() {
        configurations +=
            TestFixtures.pluginConfiguration(
                UUID.randomUUID(),
                "notificatiesapi-manual",
                mapOf("abonnementRegistration" to "manual", "authHeader" to ""),
            )

        assertFalse(service.isAuthorizedCallback(""))
        assertFalse(service.isAuthorizedCallback(" "))
    }

    @Test
    fun `the resource returns 403 without header, 401 for a wrong one and 204 after queuing an accepted one`() {
        configurations +=
            TestFixtures.configurationOf(TestFixtures.plugin(registration = MANUAL, secret = "manual-secret"))
        val resource = NotificatiesApiManualPluginCallbackResource(service)

        assertEquals(HttpStatus.FORBIDDEN, resource.handleNotification(notificatie(), null).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, resource.handleNotification(notificatie(), "wrong").statusCode)
        verify(notificatiesApiService, never()).registerNotification(any())

        assertEquals(HttpStatus.NO_CONTENT, resource.handleNotification(notificatie(), "manual-secret").statusCode)
        verify(notificatiesApiService).registerNotification(notificatie())
    }

    @Test
    fun `the test notificatie of Open Notificaties is answered without queuing`() {
        val resource = NotificatiesApiManualPluginCallbackResource(service)

        assertEquals(
            HttpStatus.NO_CONTENT,
            resource.handleNotification(notificatie(kanaal = "test"), "anything").statusCode,
        )
        verify(notificatiesApiService, never()).registerNotification(any())
    }

    private fun link(
        configurationId: UUID,
        auth: String,
    ) = NotificatiesApiManualPluginAbonnementLink(
        NotificatiesApiManualPluginConfigurationId(configurationId),
        "${TestFixtures.ON_URL}abonnement/${UUID.randomUUID()}",
        auth,
    )

    private fun notificatie(kanaal: String = "objecten") =
        NotificatiesApiNotificationReceivedEvent(
            kanaal = kanaal,
            hoofdObject = "http://objecten/1",
            resourceUrl = "http://objecten/1",
            resource = "object",
            actie = "create",
            aanmaakdatum = null,
            kenmerken = emptyMap(),
        )
}
