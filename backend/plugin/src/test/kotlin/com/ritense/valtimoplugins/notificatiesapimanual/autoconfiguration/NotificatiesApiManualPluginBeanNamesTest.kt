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

package com.ritense.valtimoplugins.notificatiesapimanual.autoconfiguration

import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Bean
import kotlin.test.assertTrue

/**
 * GZAC applications run with bean overriding allowed, so a bean of this plugin with the name of a core bean would
 * silently replace core's. The core names below are those of core's notificaties-api module on Valtimo 12.
 */
class NotificatiesApiManualPluginBeanNamesTest {
    private val coreBeanNames =
        setOf(
            "notificatiesApiClient",
            "notificatiesApiPluginFactory",
            "notificatiesApiInboundEventIntakeService",
            "notificatiesApiService",
            "notificatiesApiInboundEventProcessingService",
            "notificatiesApiInboundEventWorker",
            "notificatiesApiTaskExecutor",
            "notificatiesApiInboundEventQueryService",
            "notificatiesApiInboundEventAdminService",
            "notificatiesApiInboundEventHealthIndicator",
            "notificatiesApiResource",
            "notificatiesApiManagementResource",
            "notificatiesApiHttpSecurityConfigurer",
            "notificatiesApiLiquibaseChangeLogLocation",
            "notificatiesApiAbonnementLinkRepository",
            "notificatiesApiInboundEventRepository",
        )

    private val beanNames =
        NotificatiesApiManualPluginAutoConfiguration::class.java.declaredMethods
            .filter { it.isAnnotationPresent(Bean::class.java) }
            .flatMap { method ->
                method
                    .getAnnotation(Bean::class.java)
                    .name
                    .toList()
                    .ifEmpty { listOf(method.name) }
            } +
            "notificatiesApiManualPluginAbonnementLinkRepository"

    @Test
    fun `every bean name has the plugin prefix`() {
        assertTrue(beanNames.isNotEmpty())
        beanNames.forEach { assertTrue(it.startsWith("notificatiesApiManualPlugin"), "bean '$it' lacks the prefix") }
    }

    @Test
    fun `no bean name is a core bean name`() {
        assertTrue(beanNames.none { it in coreBeanNames }, "clashing: ${beanNames.filter { it in coreBeanNames }}")
    }
}
