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

import com.ritense.notificatiesapi.domain.Abonnement
import com.ritense.plugin.domain.PluginConfiguration
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.CALLBACK_URL
import com.ritense.valtimoplugins.notificatiesapimanual.domain.AbonnementRegistration.MANUAL
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificatiesApiManualPluginManualAbonnementServiceTest {
    private val manual = TestFixtures.plugin(registration = MANUAL, secret = "secret")
    private val automatic = TestFixtures.plugin()
    private val configurations = listOf(manual, automatic).associateBy { TestFixtures.configurationOf(it) }
    private val pluginService =
        mock<PluginService>().also { service ->
            whenever(service.getPluginConfigurations(any())).thenAnswer { configurations.keys.toList() }
            whenever(
                service.createInstance(any<PluginConfiguration>()),
            ).thenAnswer { configurations[it.getArgument(0)] }
        }
    private val service = NotificatiesApiManualPluginManualAbonnementService(pluginService)

    @Test
    fun `a manual configuration's abonnement must cover the callback URL and kanaal objecten`() {
        val result = service.getManualAbonnement(manual.configurationId.id)!!

        assertEquals(CALLBACK_URL, result.callbackUrl)
        assertEquals(listOf(Abonnement.Kanaal(naam = "objecten")), result.kanalen)
    }

    @Test
    fun `returns nothing for an automatic or unknown configuration`() {
        assertNull(service.getManualAbonnement(automatic.configurationId.id))
        assertNull(service.getManualAbonnement(UUID.randomUUID()))
    }
}
