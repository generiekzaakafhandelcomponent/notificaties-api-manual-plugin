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

import com.ritense.plugin.repository.PluginDefinitionRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The context runs with bean overriding off (test application.yml), so a bean-name clash with core fails here. */
internal class ApplicationStartIT : BaseIntegrationTest() {
    @Autowired
    lateinit var applicationContext: ApplicationContext

    @Autowired
    lateinit var pluginDefinitionRepository: PluginDefinitionRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `starts next to the core plugin, with its own plugin definition`() {
        assertTrue(
            applicationContext.containsBean("pluginsDeployedEventListener"),
            "core's registration bean is missing",
        )
        assertTrue(applicationContext.containsBean("notificatiesApiResource"), "core's callback bean is missing")
        assertTrue(pluginDefinitionRepository.existsById("notificatiesapi-manual"))
        assertTrue(pluginDefinitionRepository.existsById("notificatiesapi"))
    }

    @Test
    fun `drops the tables of earlier versions`() {
        val tables =
            jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_name LIKE 'notificaties_api_manual%'",
                String::class.java,
            )

        assertEquals(emptyList(), tables)
    }
}
