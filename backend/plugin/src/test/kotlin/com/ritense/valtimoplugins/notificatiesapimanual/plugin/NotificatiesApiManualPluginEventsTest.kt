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

import com.ritense.notificatiesapi.client.NotificatiesApiClient
import com.ritense.notificatiesapi.domain.Abonnement
import com.ritense.notificatiesapi.domain.Kanaal
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.CALLBACK_URL
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.ON_URL
import com.ritense.valtimoplugins.notificatiesapimanual.domain.AbonnementRegistration
import com.ritense.valtimoplugins.notificatiesapimanual.domain.AbonnementRegistration.AUTOMATIC
import com.ritense.valtimoplugins.notificatiesapimanual.domain.AbonnementRegistration.MANUAL
import com.ritense.valtimoplugins.notificatiesapimanual.domain.NotificatiesApiManualPluginAbonnementLink
import com.ritense.valtimoplugins.notificatiesapimanual.domain.NotificatiesApiManualPluginConfigurationId
import com.ritense.valtimoplugins.notificatiesapimanual.repository.NotificatiesApiManualPluginAbonnementLinkRepository
import jakarta.validation.ValidationException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The Valtimo 12 registration runs in the plugin events. */
class NotificatiesApiManualPluginEventsTest {
    private lateinit var client: NotificatiesApiClient
    private lateinit var pluginService: PluginService
    private lateinit var linkRepository: NotificatiesApiManualPluginAbonnementLinkRepository
    private val links = mutableMapOf<UUID, NotificatiesApiManualPluginAbonnementLink>()

    @BeforeEach
    fun setUp() {
        client = mock()
        pluginService = mock()
        linkRepository = mock()
        whenever(pluginService.getPluginConfigurations(any())).thenReturn(emptyList())
        whenever(linkRepository.findById(any())).thenAnswer {
            Optional.ofNullable(links[it.getArgument<NotificatiesApiManualPluginConfigurationId>(0).id])
        }
        whenever(linkRepository.save(any<NotificatiesApiManualPluginAbonnementLink>())).thenAnswer { invocation ->
            invocation.getArgument<NotificatiesApiManualPluginAbonnementLink>(0).also {
                links[it.configurationId.id] =
                    it
            }
        }
        whenever(linkRepository.delete(any())).thenAnswer {
            links.remove(it.getArgument<NotificatiesApiManualPluginAbonnementLink>(0).configurationId.id)
        }
        whenever(client.getKanalen(any(), any())).thenReturn(emptyList())
        whenever(client.createAbonnement(any(), any(), any())).thenAnswer {
            it.getArgument<Abonnement>(2).copy(url = "${ON_URL}abonnement/${UUID.randomUUID()}")
        }
    }

    @Test
    fun `creating an automatic configuration registers an abonnement on kanaal objecten`() {
        val plugin = plugin(AUTOMATIC, secret = "own-secret")

        plugin.onCreate()

        verify(client).createKanaal(any(), any(), eq(Kanaal(naam = "objecten")))
        verify(client).createAbonnement(
            any(),
            any(),
            argThat {
                callbackUrl == CALLBACK_URL &&
                    auth == "own-secret" &&
                    kanalen == listOf(Abonnement.Kanaal(naam = "objecten"))
            },
        )
        assertEquals("own-secret", links.values.single().auth)
    }

    @Test
    fun `creating or updating a manual configuration makes no call to Open Notificaties`() {
        val plugin = plugin(MANUAL, secret = "secret")

        plugin.onCreate()
        plugin.onUpdate()

        verifyNoInteractions(client)
    }

    @Test
    fun `a failing validation makes no call to Open Notificaties`() {
        val plugin = plugin(AUTOMATIC, secret = "Bearer x")

        assertThrows<ValidationException> { plugin.onCreate() }
        assertThrows<ValidationException> { plugin.onUpdate() }

        verifyNoInteractions(client)
    }

    @Test
    fun `switching to manual removes the left-over link, so switching back creates a fresh abonnement`() {
        val plugin = plugin(AUTOMATIC)
        plugin.onCreate()
        val firstUrl = links.values.single().url

        plugin.abonnementRegistration = MANUAL
        plugin.authHeader = "secret"
        plugin.onUpdate()
        assertNull(links[plugin.configurationId.id])

        plugin.abonnementRegistration = AUTOMATIC
        plugin.onUpdate()

        verify(client, never()).getAbonnement(any(), any(), any())
        assert(links.values.single().url != firstUrl)
    }

    @Test
    fun `an update whose abonnement is gone in Open Notificaties creates a new one`() {
        val plugin = plugin(AUTOMATIC)
        plugin.onCreate()
        whenever(client.getAbonnement(any(), any(), any()))
            .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null))

        plugin.onUpdate()

        verify(client, org.mockito.kotlin.times(2)).createAbonnement(any(), any(), any())
    }

    @Test
    fun `an unchanged update sends nothing, a changed secret is sent`() {
        val plugin = plugin(AUTOMATIC, secret = "first")
        plugin.onCreate()
        val link = links.values.single()
        whenever(client.getAbonnement(any(), any(), any())).thenReturn(
            Abonnement(
                url = link.url,
                callbackUrl = CALLBACK_URL,
                auth = null,
                kanalen = listOf(Abonnement.Kanaal(naam = "objecten")),
            ),
        )

        plugin.onUpdate()
        verify(client, never()).updateAbonnement(any(), any(), any(), any())

        plugin.authHeader = "second"
        plugin.onUpdate()
        verify(client).updateAbonnement(any(), any(), eq(link.getAbonnementId()), argThat { auth == "second" })
        assertEquals("second", links.values.single().auth)
    }

    @Test
    fun `deleting an automatic configuration removes the link also when Open Notificaties refuses`() {
        val plugin = plugin(AUTOMATIC)
        plugin.onCreate()
        whenever(client.deleteAbonnement(any(), any(), any())).thenThrow(IllegalStateException("unreachable"))

        plugin.onDelete()

        assertEquals(0, links.size)
    }

    @Test
    fun `deleting a manual configuration makes no call to Open Notificaties`() {
        val plugin = plugin(MANUAL, secret = "secret")
        links[plugin.configurationId.id] =
            NotificatiesApiManualPluginAbonnementLink(
                NotificatiesApiManualPluginConfigurationId(plugin.configurationId.id),
                "${ON_URL}abonnement/old",
                "k",
            )

        plugin.onDelete()

        verifyNoInteractions(client)
        assertEquals(0, links.size)
    }

    private fun plugin(
        registration: AbonnementRegistration,
        secret: String? = null,
    ) = TestFixtures.plugin(
        registration = registration,
        secret = secret,
        client = client,
        pluginService = pluginService,
        linkRepository = linkRepository,
    )
}
