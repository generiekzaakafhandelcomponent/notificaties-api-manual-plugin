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

package com.ritense.valtimoplugins.notificatiesapimanual.registration

import com.fasterxml.jackson.databind.ObjectMapper
import com.ritense.notificatiesapi.client.NotificatiesApiClient
import com.ritense.notificatiesapi.domain.Abonnement
import com.ritense.notificatiesapi.domain.Kanaal
import com.ritense.notificatiesapi.domain.Notificatie
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.CORE_CALLBACK_URL
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.ON_URL
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.OTHER_ON_URL
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.authentication
import com.ritense.valtimoplugins.notificatiesapimanual.registration.NotificatiesApiManualPluginCoreClient.Companion.unwrapped
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.client.RestClient
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NotificatiesApiManualPluginCoreClientTest {
    private lateinit var delegate: NotificatiesApiClient
    private lateinit var pluginService: PluginService
    private lateinit var client: NotificatiesApiManualPluginCoreClient

    private val manualUrl = URI(ON_URL)
    private val otherUrl = URI(OTHER_ON_URL)
    private val abonnement =
        Abonnement(
            callbackUrl = CORE_CALLBACK_URL,
            auth = "core-key",
            kanalen = listOf(Abonnement.Kanaal(naam = "objecten", filters = mapOf("actie" to "create"))),
        )

    @BeforeEach
    fun setUp() {
        delegate = mock()
        pluginService = mock()
        // Without the trailing slash core's configuration has.
        val manual = TestFixtures.manualConfiguration(url = ON_URL.trimEnd('/'))
        whenever(pluginService.getPluginConfigurations(any())).thenReturn(listOf(manual))
        client = wrap(delegate)
    }

    @Test
    fun `leaves Open Notificaties alone on a URL with a manual configuration`() {
        assertEquals(emptyList(), client.getAbonnementen(authentication, manualUrl))
        assertEquals(emptyList(), client.getKanalen(authentication, manualUrl))
        val kanaal = Kanaal(naam = "objecten")
        assertEquals(kanaal, client.createKanaal(authentication, manualUrl, kanaal))
        client.deleteAbonnement(authentication, manualUrl, "some-id")

        val placeholder = abonnement.copy(url = "${ON_URL.trimEnd('/')}/abonnement/registered-manually")
        assertEquals(placeholder, client.createAbonnement(authentication, manualUrl, abonnement))
        assertEquals(placeholder, client.updateAbonnement(authentication, manualUrl, "registered-manually", abonnement))

        verifyNoInteractions(delegate)
    }

    @Test
    fun `passes every call on another URL to core's client`() {
        val created = abonnement.copy(url = "${OTHER_ON_URL}abonnement/1")
        whenever(delegate.getAbonnementen(authentication, otherUrl)).thenReturn(listOf(created))
        whenever(delegate.getKanalen(authentication, otherUrl)).thenReturn(listOf(Kanaal(naam = "zaken")))
        whenever(
            delegate.createKanaal(authentication, otherUrl, Kanaal(naam = "objecten")),
        ).thenAnswer { it.getArgument(2) }
        whenever(delegate.createAbonnement(authentication, otherUrl, abonnement)).thenReturn(created)
        whenever(delegate.updateAbonnement(authentication, otherUrl, "1", abonnement)).thenReturn(created)

        assertEquals(listOf(created), client.getAbonnementen(authentication, otherUrl))
        assertEquals(listOf(Kanaal(naam = "zaken")), client.getKanalen(authentication, otherUrl))
        val kanaal = Kanaal(naam = "objecten")
        assertEquals(kanaal, client.createKanaal(authentication, otherUrl, kanaal))
        assertEquals(created, client.createAbonnement(authentication, otherUrl, abonnement))
        assertEquals(created, client.updateAbonnement(authentication, otherUrl, "1", abonnement))
        client.deleteAbonnement(authentication, otherUrl, "1")

        verify(delegate).deleteAbonnement(authentication, otherUrl, "1")
    }

    @Test
    fun `always publishes and reads a single abonnement through core's client`() {
        val notificatie = mock<Notificatie>()
        whenever(delegate.createNotificatie(authentication, manualUrl, notificatie)).thenReturn(notificatie)
        whenever(delegate.getAbonnement(authentication, manualUrl, "1")).thenReturn(abonnement)

        assertSame(notificatie, client.createNotificatie(authentication, manualUrl, notificatie))
        assertEquals(abonnement, client.getAbonnement(authentication, manualUrl, "1"))
    }

    @Test
    fun `unwraps to core's client`() {
        assertSame(delegate, client.unwrapped())
        assertSame(delegate, delegate.unwrapped())
    }

    @Test
    fun `the post-processor wraps a client once and leaves other beans alone`() {
        val postProcessor =
            NotificatiesApiManualPluginCoreClientPostProcessor(
                provider(pluginService),
                provider(RestClient.builder()),
                provider(ObjectMapper()),
            )

        val wrapped = postProcessor.postProcessAfterInitialization(delegate, "notificatiesApiClient")
        assertTrue(wrapped is NotificatiesApiManualPluginCoreClient)
        assertSame(delegate, wrapped.delegate)
        assertSame(wrapped, postProcessor.postProcessAfterInitialization(wrapped, "notificatiesApiClient"))
        assertSame(pluginService, postProcessor.postProcessAfterInitialization(pluginService, "pluginService"))
    }

    private fun wrap(delegate: NotificatiesApiClient) =
        NotificatiesApiManualPluginCoreClient(delegate, provider(pluginService), RestClient.builder(), ObjectMapper())

    private fun <T : Any> provider(value: T): ObjectProvider<T> = mock { on { getObject() } doReturn value }
}
