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

import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.CALLBACK_URL
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.CORE_CALLBACK_URL
import com.ritense.valtimoplugins.notificatiesapimanual.domain.AbonnementRegistration.AUTOMATIC
import com.ritense.valtimoplugins.notificatiesapimanual.domain.AbonnementRegistration.MANUAL
import jakarta.validation.ValidationException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID
import kotlin.test.assertEquals

class NotificatiesApiManualPluginTest {
    private lateinit var pluginService: PluginService

    @BeforeEach
    fun setUp() {
        pluginService = mock()
        whenever(pluginService.getPluginConfigurations(any())).thenReturn(emptyList())
    }

    @Test
    fun `a configuration saved without the setting registers automatically`() {
        val plugin = NotificatiesApiManualPlugin(TestFixtures.plugin().configurationId, mock(), pluginService)

        assertEquals(AUTOMATIC, plugin.abonnementRegistration)
    }

    @Test
    fun `manual registration requires a shared secret`() {
        assertThrows<ValidationException> { plugin(MANUAL, secret = null).validateConfiguration() }
        assertThrows<ValidationException> { plugin(MANUAL, secret = "  ").validateConfiguration() }
        assertDoesNotThrow { plugin(MANUAL, secret = "secret").validateConfiguration() }
    }

    @Test
    fun `automatic registration does not require a shared secret`() {
        assertDoesNotThrow { plugin(AUTOMATIC, secret = null).validateConfiguration() }
    }

    @Test
    fun `manual registration does not require a callback URL`() {
        assertDoesNotThrow {
            TestFixtures
                .plugin(registration = MANUAL, secret = "secret", callbackUrl = null, pluginService = pluginService)
                .validateConfiguration()
        }
    }

    @Test
    fun `automatic registration requires a callback URL`() {
        assertThrows<ValidationException> {
            TestFixtures.plugin(callbackUrl = null, pluginService = pluginService).validateConfiguration()
        }
    }

    @Test
    fun `a shared secret starting with Bearer is rejected because the security filter takes it as a token`() {
        assertThrows<ValidationException> { plugin(MANUAL, secret = "Bearer abc").validateConfiguration() }
        assertThrows<ValidationException> { plugin(AUTOMATIC, secret = "Bearer abc").validateConfiguration() }
        assertThrows<ValidationException> { plugin(MANUAL, secret = "bearer abc").validateConfiguration() }
        assertThrows<ValidationException> { plugin(MANUAL, secret = "BearerSecret1").validateConfiguration() }
    }

    @Test
    fun `a shared secret with surrounding whitespace is rejected because HTTP strips it`() {
        assertThrows<ValidationException> { plugin(MANUAL, secret = " secret").validateConfiguration() }
        assertThrows<ValidationException> { plugin(MANUAL, secret = "secret ").validateConfiguration() }
    }

    @Test
    fun `a manual and an automatic configuration of this plugin can share a callback URL`() {
        val automaticConfiguration = TestFixtures.configurationOf(TestFixtures.plugin(callbackUrl = CALLBACK_URL))
        whenever(pluginService.getPluginConfigurations(any())).thenAnswer { invocation ->
            val search = invocation.getArgument<com.ritense.plugin.service.PluginConfigurationSearchParameters>(0)
            if (search.pluginDefinitionKey == "notificatiesapi-manual") listOf(automaticConfiguration) else emptyList()
        }

        assertDoesNotThrow { plugin(MANUAL, secret = "secret").validateConfiguration() }
        assertDoesNotThrow { plugin(AUTOMATIC).validateConfiguration() }
    }

    @Test
    fun `a shared secret with characters outside printable ASCII is rejected`() {
        assertThrows<ValidationException> { plugin(MANUAL, secret = "geheim€").validateConfiguration() }
        assertThrows<ValidationException> { plugin(MANUAL, secret = "geheim\u0000").validateConfiguration() }
    }

    @Test
    fun `a shared secret longer than the link column is rejected`() {
        assertThrows<ValidationException> { plugin(MANUAL, secret = "x".repeat(256)).validateConfiguration() }
        assertDoesNotThrow { plugin(MANUAL, secret = "x".repeat(255)).validateConfiguration() }
    }

    @Test
    fun `a callback URL used by a core configuration is rejected`() {
        whenever(pluginService.getPluginConfigurations(any())).thenAnswer {
            listOf(
                TestFixtures.pluginConfiguration(
                    UUID.randomUUID(),
                    "notificatiesapi",
                    mapOf(
                        "callbackUrl" to "$CALLBACK_URL/",
                    ),
                ),
            )
        }

        assertThrows<ValidationException> { plugin(AUTOMATIC).validateConfiguration() }
    }

    @Test
    fun `a callback URL that points at core's endpoint is rejected`() {
        assertThrows<ValidationException> {
            TestFixtures.plugin(callbackUrl = CORE_CALLBACK_URL, pluginService = pluginService).validateConfiguration()
        }
    }

    @Test
    fun `a callback URL that differs from every core configuration is accepted`() {
        whenever(pluginService.getPluginConfigurations(any())).thenAnswer {
            listOf(
                TestFixtures.pluginConfiguration(
                    UUID.randomUUID(),
                    "notificatiesapi",
                    mapOf(
                        "callbackUrl" to CORE_CALLBACK_URL,
                    ),
                ),
            )
        }

        assertDoesNotThrow { plugin(AUTOMATIC).validateConfiguration() }
    }

    private fun plugin(
        registration: com.ritense.valtimoplugins.notificatiesapimanual.domain.AbonnementRegistration,
        secret: String? = null,
    ) = TestFixtures.plugin(registration = registration, secret = secret, pluginService = pluginService)
}
