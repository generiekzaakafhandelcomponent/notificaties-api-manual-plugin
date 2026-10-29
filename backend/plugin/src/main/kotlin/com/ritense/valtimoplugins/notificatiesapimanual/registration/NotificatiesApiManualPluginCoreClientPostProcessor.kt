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
import com.ritense.plugin.service.PluginService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.web.client.RestClient

/**
 * Wraps every [NotificatiesApiClient] bean in a [NotificatiesApiManualPluginCoreClient]: core's own one, or one an
 * application defines itself. Wrapping instead of defining the bean keeps core's `@ConditionalOnMissingBean` bean and
 * needs no ordering against core's auto-configuration. The providers are resolved lazily: [PluginService] depends on
 * the plugin factories, which depend on the client.
 */
class NotificatiesApiManualPluginCoreClientPostProcessor(
    private val pluginService: ObjectProvider<PluginService>,
    private val restClientBuilder: ObjectProvider<RestClient.Builder>,
    private val objectMapper: ObjectProvider<ObjectMapper>,
) : BeanPostProcessor {
    override fun postProcessAfterInitialization(
        bean: Any,
        beanName: String,
    ): Any =
        if (bean is NotificatiesApiClient && bean !is NotificatiesApiManualPluginCoreClient) {
            NotificatiesApiManualPluginCoreClient(
                bean,
                pluginService,
                restClientBuilder.getObject(),
                objectMapper.getObject(),
            )
        } else {
            bean
        }
}
