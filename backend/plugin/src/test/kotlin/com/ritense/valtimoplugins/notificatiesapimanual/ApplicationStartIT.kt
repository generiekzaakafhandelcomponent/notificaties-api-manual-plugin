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

import com.ritense.notificatiesapi.repository.NotificatiesApiAbonnementLinkRepository
import com.ritense.plugin.repository.PluginDefinitionRepository
import com.ritense.valtimoplugins.notificatiesapimanual.repository.NotificatiesApiManualPluginAbonnementLinkRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The context runs with bean overriding off (test application.yml), so a bean-name clash with core fails here. */
internal class ApplicationStartIT : BaseIntegrationTest() {
    @Autowired
    lateinit var applicationContext: ApplicationContext

    @Autowired
    lateinit var linkRepository: NotificatiesApiManualPluginAbonnementLinkRepository

    @Autowired
    lateinit var coreLinkRepository: NotificatiesApiAbonnementLinkRepository

    @Autowired
    lateinit var pluginDefinitionRepository: PluginDefinitionRepository

    @Test
    fun `starts next to the core plugin with its own table and definition, calling no Open Notificaties`() {
        assertTrue(applicationContext.containsBean("notificatiesApiResource"), "core's callback bean is missing")
        assertEquals(0, linkRepository.count())
        coreLinkRepository.count()
        assertTrue(pluginDefinitionRepository.existsById("notificatiesapi-manual"))
        assertTrue(pluginDefinitionRepository.existsById("notificatiesapi"))
        verifyNoInteractions(notificatiesApiClient)
    }
}
