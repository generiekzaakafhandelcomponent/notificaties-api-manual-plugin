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

package com.ritense.valtimoplugins.notificatiesapimanual.link

import com.ritense.plugin.events.PluginConfigurationIdUpdatedEvent
import com.ritense.valtimoplugins.notificatiesapimanual.domain.NotificatiesApiManualPluginAbonnementLink
import com.ritense.valtimoplugins.notificatiesapimanual.domain.NotificatiesApiManualPluginConfigurationId
import com.ritense.valtimoplugins.notificatiesapimanual.repository.NotificatiesApiManualPluginAbonnementLinkRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.event.EventListener
import org.springframework.data.repository.findByIdOrNull

/**
 * A changed configuration id keeps its abonnement. Core publishes no delete or update event for it, only this one,
 * so without it the link would stay under the old id and the abonnement would stop authorising.
 */
class NotificatiesApiManualPluginConfigurationIdListener(
    private val linkRepository: NotificatiesApiManualPluginAbonnementLinkRepository,
) {
    @EventListener
    fun handlePluginConfigurationIdUpdatedEvent(event: PluginConfigurationIdUpdatedEvent) {
        val link = linkRepository.findByIdOrNull(NotificatiesApiManualPluginConfigurationId(event.oldId)) ?: return
        linkRepository.delete(link)
        linkRepository.save(
            NotificatiesApiManualPluginAbonnementLink(
                NotificatiesApiManualPluginConfigurationId(event.newId),
                link.url,
                link.auth,
            ),
        )
        logger.info {
            "Moved the abonnement link of plugin configuration '${event.oldId}' to its new id '${event.newId}'"
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
