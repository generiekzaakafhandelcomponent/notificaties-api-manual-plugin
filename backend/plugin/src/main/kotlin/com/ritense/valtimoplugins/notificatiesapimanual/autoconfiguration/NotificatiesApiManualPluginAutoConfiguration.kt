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
import com.ritense.case.service.CaseDefinitionService
import com.ritense.notificatiesapi.autoconfigure.NotificatiesApiAutoConfiguration
import com.ritense.notificatiesapi.client.NotificatiesApiClient
import com.ritense.notificatiesapi.service.NotificatiesApiService
import com.ritense.plugin.repository.PluginProcessLinkRepository
import com.ritense.plugin.service.PluginService
import com.ritense.processdocument.service.ProcessDefinitionCaseDefinitionService
import com.ritense.processdocument.service.ProcessDocumentService
import com.ritense.valtimo.contract.config.LiquibaseMasterChangeLogLocation
import com.ritense.valtimo.service.ProcessPropertyService
import com.ritense.valtimoplugins.notificatiesapimanual.callback.NotificatiesApiManualPluginCallbackResource
import com.ritense.valtimoplugins.notificatiesapimanual.callback.NotificatiesApiManualPluginCallbackService
import com.ritense.valtimoplugins.notificatiesapimanual.callback.NotificatiesApiManualPluginMisroutedCallbackFilter
import com.ritense.valtimoplugins.notificatiesapimanual.config.NotificatiesApiManualPluginProperties
import com.ritense.valtimoplugins.notificatiesapimanual.listener.NotificatiesApiManualPluginProcessLinkListener
import com.ritense.valtimoplugins.notificatiesapimanual.manual.NotificatiesApiManualPluginManualAbonnementResource
import com.ritense.valtimoplugins.notificatiesapimanual.manual.NotificatiesApiManualPluginManualAbonnementService
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.CORE_CALLBACK_PATH
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPluginFactory
import com.ritense.valtimoplugins.notificatiesapimanual.registration.NotificatiesApiManualPluginAbonnementRegistrar
import com.ritense.valtimoplugins.notificatiesapimanual.repository.NotificatiesApiManualPluginAbonnementLinkRepository
import com.ritense.valtimoplugins.notificatiesapimanual.repository.NotificatiesApiManualPluginMisroutedCallbackRepository
import com.ritense.valtimoplugins.notificatiesapimanual.security.NotificatiesApiManualPluginHttpSecurityConfigurer
import org.operaton.bpm.engine.RepositoryService
import org.operaton.bpm.engine.RuntimeService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.Order
import org.springframework.core.task.TaskExecutor
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.transaction.PlatformTransactionManager

/**
 * Every bean name starts with `notificatiesApiManualPlugin`: core #971 (13.52) already has beans named
 * `notificatiesApiManualAbonnement*`, and GZAC applications allow bean overriding, so a clash would silently replace
 * a core bean. Core types are only used, never subclassed, so core's `@ConditionalOnMissingBean` beans stay.
 */
@AutoConfiguration(after = [NotificatiesApiAutoConfiguration::class])
@EnableConfigurationProperties(NotificatiesApiManualPluginProperties::class)
@EnableJpaRepositories(basePackageClasses = [NotificatiesApiManualPluginAbonnementLinkRepository::class])
@EntityScan(basePackages = ["com.ritense.valtimoplugins.notificatiesapimanual.domain"])
class NotificatiesApiManualPluginAutoConfiguration {
    @Bean
    fun notificatiesApiManualPluginFactory(
        pluginService: PluginService,
        client: NotificatiesApiClient,
    ): NotificatiesApiManualPluginFactory = NotificatiesApiManualPluginFactory(pluginService, client)

    @Bean
    fun notificatiesApiManualPluginAbonnementRegistrar(
        client: NotificatiesApiClient,
        linkRepository: NotificatiesApiManualPluginAbonnementLinkRepository,
        pluginService: PluginService,
        pluginProcessLinkRepository: PluginProcessLinkRepository,
        transactionManager: PlatformTransactionManager,
        properties: NotificatiesApiManualPluginProperties,
        @Qualifier("notificatiesApiManualPluginRegistrationExecutor") registrationExecutor: TaskExecutor,
    ): NotificatiesApiManualPluginAbonnementRegistrar =
        NotificatiesApiManualPluginAbonnementRegistrar(
            client,
            linkRepository,
            pluginService,
            pluginProcessLinkRepository,
            transactionManager,
            properties,
            registrationExecutor,
        )

    /**
     * Single-threaded executor all registration work runs on (the startup retry loop, event-triggered sweeps, cleanup
     * after a deleted configuration): never on the event thread or a request, and never two at a time.
     * Daemon threads that are not awaited on shutdown, so a pending retry never delays JVM exit.
     */
    @Bean("notificatiesApiManualPluginRegistrationExecutor")
    fun notificatiesApiManualPluginRegistrationExecutor(): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            threadNamePrefix = "notificaties-api-manual-plugin-abonnement-registration-"
            corePoolSize = 1
            maxPoolSize = 1
            // Unbounded: cleanups after deleted configurations are not coalesced, and none may be rejected.
            setDaemon(true)
            setWaitForTasksToCompleteOnShutdown(false)
            initialize()
        }

    @Bean
    fun notificatiesApiManualPluginCallbackService(
        pluginService: PluginService,
        linkRepository: NotificatiesApiManualPluginAbonnementLinkRepository,
        notificatiesApiService: NotificatiesApiService,
        misroutedCallbackRepository: NotificatiesApiManualPluginMisroutedCallbackRepository,
    ): NotificatiesApiManualPluginCallbackService =
        NotificatiesApiManualPluginCallbackService(
            pluginService,
            linkRepository,
            notificatiesApiService,
            misroutedCallbackRepository,
        )

    @Bean
    fun notificatiesApiManualPluginCallbackResource(
        callbackService: NotificatiesApiManualPluginCallbackService,
    ): NotificatiesApiManualPluginCallbackResource = NotificatiesApiManualPluginCallbackResource(callbackService)

    /** Notices notificaties for a manual configuration that arrive on core's callback address. */
    @Bean
    fun notificatiesApiManualPluginMisroutedCallbackFilter(
        callbackService: NotificatiesApiManualPluginCallbackService,
    ): FilterRegistrationBean<NotificatiesApiManualPluginMisroutedCallbackFilter> =
        FilterRegistrationBean(NotificatiesApiManualPluginMisroutedCallbackFilter(callbackService)).apply {
            addUrlPatterns(CORE_CALLBACK_PATH)
        }

    @Bean
    fun notificatiesApiManualPluginManualAbonnementService(
        pluginService: PluginService,
        pluginProcessLinkRepository: PluginProcessLinkRepository,
        misroutedCallbackRepository: NotificatiesApiManualPluginMisroutedCallbackRepository,
    ): NotificatiesApiManualPluginManualAbonnementService =
        NotificatiesApiManualPluginManualAbonnementService(
            pluginService,
            pluginProcessLinkRepository,
            misroutedCallbackRepository,
        )

    @Bean
    fun notificatiesApiManualPluginManualAbonnementResource(
        manualAbonnementService: NotificatiesApiManualPluginManualAbonnementService,
    ): NotificatiesApiManualPluginManualAbonnementResource =
        NotificatiesApiManualPluginManualAbonnementResource(manualAbonnementService)

    @Bean
    fun notificatiesApiManualPluginProcessLinkListener(
        pluginProcessLinkRepository: PluginProcessLinkRepository,
        runtimeService: RuntimeService,
        repositoryService: RepositoryService,
        processPropertyService: ProcessPropertyService,
        processDefinitionCaseDefinitionService: ProcessDefinitionCaseDefinitionService,
        processDocumentService: ProcessDocumentService,
        caseDefinitionService: CaseDefinitionService,
        objectMapper: ObjectMapper,
    ): NotificatiesApiManualPluginProcessLinkListener =
        NotificatiesApiManualPluginProcessLinkListener(
            pluginProcessLinkRepository,
            runtimeService,
            repositoryService,
            processPropertyService,
            processDefinitionCaseDefinitionService,
            processDocumentService,
            caseDefinitionService,
            objectMapper,
        )

    // Core uses 270; DenyAllHttpSecurityConfigurer (500) must stay last.
    @Bean
    @Order(271)
    fun notificatiesApiManualPluginHttpSecurityConfigurer(): NotificatiesApiManualPluginHttpSecurityConfigurer =
        NotificatiesApiManualPluginHttpSecurityConfigurer()

    // The tables have no foreign keys, so the changelog can run in any order relative to core's.
    @Bean
    fun notificatiesApiManualPluginLiquibaseMasterChangeLogLocation(): LiquibaseMasterChangeLogLocation =
        LiquibaseMasterChangeLogLocation("config/liquibase/notificaties-api-manual-master.xml")
}
