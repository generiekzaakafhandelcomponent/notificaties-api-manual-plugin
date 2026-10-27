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

import com.ritense.notificatiesapi.domain.Abonnement
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.OTHER_ON_URL
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.receiveLink
import com.ritense.valtimoplugins.notificatiesapimanual.domain.AbonnementRegistration.MANUAL
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificatiesApiManualPluginKanaalFiltersTest {
    private val automatic = TestFixtures.plugin()
    private val otherUrlAutomatic = TestFixtures.plugin(url = OTHER_ON_URL)
    private val manual = TestFixtures.plugin(registration = MANUAL, secret = "secret")

    private val filters =
        NotificatiesApiManualPluginKanaalFilters(
            configurations = listOf(automatic, otherUrlAutomatic, manual),
            receiveLinks =
                listOf(
                    receiveLink(automatic.configurationId.id, "zaken", "create", mapOf("zaaktype" to "abc")),
                    receiveLink(otherUrlAutomatic.configurationId.id, "documenten", "update"),
                    receiveLink(manual.configurationId.id, "objecten", "create"),
                    receiveLink(null, "besluiten"),
                    receiveLink(automatic.configurationId.id, null, "create"),
                ),
            objectMapper = TestFixtures.objectMapper,
        )

    @Test
    fun `an automatic abonnement only has the links of its own URL's configurations and building-block links`() {
        assertEquals(
            setOf(
                Abonnement.Kanaal(naam = "zaken", filters = mapOf("actie" to "create", "zaaktype" to "abc")),
                Abonnement.Kanaal(naam = "besluiten"),
            ),
            filters.automaticKanaalFilters(listOf(automatic)).toSet(),
        )
        assertEquals(
            setOf(
                Abonnement.Kanaal(naam = "documenten", filters = mapOf("actie" to "update")),
                Abonnement.Kanaal(naam = "besluiten"),
            ),
            filters.automaticKanaalFilters(listOf(otherUrlAutomatic)).toSet(),
        )
    }

    @Test
    fun `a manual configuration's links never go into an automatic abonnement`() {
        assertFalse(filters.automaticKanaalFilters(filters.automaticConfigurations()).any { it.naam == "objecten" })
    }

    @Test
    fun `manual configurations are recognised by URL and callback`() {
        assertTrue(filters.hasManualConfiguration(URI(TestFixtures.ON_URL.trimEnd('/'))))
        assertFalse(filters.hasManualConfiguration(URI(OTHER_ON_URL)))
        assertEquals(setOf(TestFixtures.CALLBACK_URL), filters.manualCallbackUrls())
        assertEquals(listOf(automatic, otherUrlAutomatic), filters.automaticConfigurations())
    }
}
