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

import com.fasterxml.jackson.databind.ObjectMapper
import com.ritense.notificatiesapi.autoconfigure.NotificatiesApiAutoConfiguration
import com.ritense.plugin.repository.PluginProcessLinkRepository
import com.ritense.plugin.service.PluginService
import com.ritense.valtimo.contract.config.LiquibaseMasterChangeLogLocation
import com.ritense.valtimoplugins.notificatiesapimanual.manual.NotificatiesApiManualPluginManualAbonnementResource
import com.ritense.valtimoplugins.notificatiesapimanual.manual.NotificatiesApiManualPluginManualAbonnementService
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPluginFactory
import com.ritense.valtimoplugins.notificatiesapimanual.registration.NotificatiesApiManualPluginCoreClientPostProcessor
import com.ritense.valtimoplugins.notificatiesapimanual.security.NotificatiesApiManualPluginHttpSecurityConfigurer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.Order
import org.springframework.web.client.RestClient

/**
 * Every bean name starts with `notificatiesApiManualPlugin`: core #971 (13.52) already has beans named
 * `notificatiesApiManualAbonnement*`, and GZAC applications allow bean overriding, so a clash would silently replace
 * a core bean. Core's `@ConditionalOnMissingBean` beans stay: the only core type subclassed,
 * `NotificatiesApiClient`, is wrapped after creation instead of replaced.
 */
@AutoConfiguration(after = [NotificatiesApiAutoConfiguration::class])
class NotificatiesApiManualPluginAutoConfiguration {
    @Bean
    fun notificatiesApiManualPluginFactory(pluginService: PluginService): NotificatiesApiManualPluginFactory =
        NotificatiesApiManualPluginFactory(pluginService)

    @Bean
    fun notificatiesApiManualPluginManualAbonnementService(
        pluginService: PluginService,
        pluginProcessLinkRepository: PluginProcessLinkRepository,
    ): NotificatiesApiManualPluginManualAbonnementService =
        NotificatiesApiManualPluginManualAbonnementService(pluginService, pluginProcessLinkRepository)

    @Bean
    fun notificatiesApiManualPluginManualAbonnementResource(
        manualAbonnementService: NotificatiesApiManualPluginManualAbonnementService,
    ): NotificatiesApiManualPluginManualAbonnementResource =
        NotificatiesApiManualPluginManualAbonnementResource(manualAbonnementService)

    // Core uses 270; DenyAllHttpSecurityConfigurer (500) must stay last.
    @Bean
    @Order(271)
    fun notificatiesApiManualPluginHttpSecurityConfigurer(): NotificatiesApiManualPluginHttpSecurityConfigurer =
        NotificatiesApiManualPluginHttpSecurityConfigurer()

    // Only drops the tables of versions before 2.0.0, so it can run in any order relative to core's changelog.
    @Bean
    fun notificatiesApiManualPluginLiquibaseMasterChangeLogLocation(): LiquibaseMasterChangeLogLocation =
        LiquibaseMasterChangeLogLocation("config/liquibase/notificaties-api-manual-master.xml")

    companion object {
        /** Static, as a bean post-processor must be, so this configuration class is not created early. */
        @JvmStatic
        @Bean
        fun notificatiesApiManualPluginCoreClientPostProcessor(
            pluginService: ObjectProvider<PluginService>,
            restClientBuilder: ObjectProvider<RestClient.Builder>,
            objectMapper: ObjectProvider<ObjectMapper>,
        ): NotificatiesApiManualPluginCoreClientPostProcessor =
            NotificatiesApiManualPluginCoreClientPostProcessor(pluginService, restClientBuilder, objectMapper)
    }
}
