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

import com.ritense.notificatiesapi.autoconfigure.NotificatiesApiAutoConfiguration
import com.ritense.notificatiesapi.client.NotificatiesApiClient
import com.ritense.notificatiesapi.service.NotificatiesApiService
import com.ritense.plugin.service.PluginService
import com.ritense.valtimo.contract.config.LiquibaseMasterChangeLogLocation
import com.ritense.valtimoplugins.notificatiesapimanual.callback.NotificatiesApiManualPluginCallbackResource
import com.ritense.valtimoplugins.notificatiesapimanual.callback.NotificatiesApiManualPluginCallbackService
import com.ritense.valtimoplugins.notificatiesapimanual.link.NotificatiesApiManualPluginConfigurationIdListener
import com.ritense.valtimoplugins.notificatiesapimanual.manual.NotificatiesApiManualPluginManualAbonnementResource
import com.ritense.valtimoplugins.notificatiesapimanual.manual.NotificatiesApiManualPluginManualAbonnementService
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPluginFactory
import com.ritense.valtimoplugins.notificatiesapimanual.repository.NotificatiesApiManualPluginAbonnementLinkRepository
import com.ritense.valtimoplugins.notificatiesapimanual.security.NotificatiesApiManualPluginHttpSecurityConfigurer
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.Order
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Every bean name starts with `notificatiesApiManualPlugin`: GZAC applications allow bean overriding, so a bean with
 * the name of a core bean would silently replace it. Core types are only used, never subclassed, so core's
 * `@ConditionalOnMissingBean` beans stay.
 */
@AutoConfiguration(after = [NotificatiesApiAutoConfiguration::class])
@EnableJpaRepositories(basePackageClasses = [NotificatiesApiManualPluginAbonnementLinkRepository::class])
@EntityScan(basePackages = ["com.ritense.valtimoplugins.notificatiesapimanual.domain"])
class NotificatiesApiManualPluginAutoConfiguration {
    @Bean
    fun notificatiesApiManualPluginFactory(
        pluginService: PluginService,
        client: NotificatiesApiClient,
        linkRepository: NotificatiesApiManualPluginAbonnementLinkRepository,
    ): NotificatiesApiManualPluginFactory = NotificatiesApiManualPluginFactory(pluginService, client, linkRepository)

    @Bean
    fun notificatiesApiManualPluginConfigurationIdListener(
        linkRepository: NotificatiesApiManualPluginAbonnementLinkRepository,
    ): NotificatiesApiManualPluginConfigurationIdListener =
        NotificatiesApiManualPluginConfigurationIdListener(linkRepository)

    @Bean
    fun notificatiesApiManualPluginCallbackService(
        pluginService: PluginService,
        linkRepository: NotificatiesApiManualPluginAbonnementLinkRepository,
        notificatiesApiService: NotificatiesApiService,
    ): NotificatiesApiManualPluginCallbackService =
        NotificatiesApiManualPluginCallbackService(pluginService, linkRepository, notificatiesApiService)

    @Bean
    fun notificatiesApiManualPluginCallbackResource(
        callbackService: NotificatiesApiManualPluginCallbackService,
    ): NotificatiesApiManualPluginCallbackResource = NotificatiesApiManualPluginCallbackResource(callbackService)

    @Bean
    fun notificatiesApiManualPluginManualAbonnementService(
        pluginService: PluginService,
    ): NotificatiesApiManualPluginManualAbonnementService =
        NotificatiesApiManualPluginManualAbonnementService(pluginService)

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

    // The table has no foreign keys, so the changelog can run in any order relative to core's.
    @Bean
    fun notificatiesApiManualPluginLiquibaseMasterChangeLogLocation(): LiquibaseMasterChangeLogLocation =
        LiquibaseMasterChangeLogLocation("config/liquibase/notificaties-api-manual-master.xml")
}
