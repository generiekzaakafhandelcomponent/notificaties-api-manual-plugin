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

package com.ritense.valtimoplugins.notificatiesapimanual.callback

import com.ritense.notificatiesapi.event.NotificatiesApiNotificationReceivedEvent
import com.ritense.notificatiesapi.service.NotificatiesApiService
import com.ritense.plugin.service.PluginConfigurationSearchParameters
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.notificatiesapimanual.domain.AbonnementRegistration
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.ABONNEMENT_REGISTRATION_PROPERTY
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.AUTH_HEADER_PROPERTY
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.PLUGIN_KEY
import com.ritense.valtimoplugins.notificatiesapimanual.repository.NotificatiesApiManualPluginAbonnementLinkRepository
import java.security.MessageDigest
import java.util.UUID

/**
 * Decides whether a callback comes from an abonnement of this plugin, and hands accepted notificaties to core's
 * inbound queue, which publishes them to every listener of [NotificatiesApiNotificationReceivedEvent].
 */
class NotificatiesApiManualPluginCallbackService(
    private val pluginService: PluginService,
    private val linkRepository: NotificatiesApiManualPluginAbonnementLinkRepository,
    private val notificatiesApiService: NotificatiesApiService,
) {
    /**
     * Authorises the sender, not a configuration: a header equal to the shared secret of any manual configuration, or
     * to the key of an abonnement this plugin registered for an existing automatic configuration, is accepted.
     */
    fun isAuthorizedCallback(authHeader: String): Boolean {
        if (authHeader.isBlank()) return false
        val registrations = findRegistrations()
        val manualSecrets = registrations.filterValues { it.manual }.mapNotNull { it.value.secret }
        if (manualSecrets.any { !it.isBlank() && secretsMatch(it, authHeader) }) {
            return true
        }
        return linkRepository.findAllByAuth(authHeader).any { link ->
            registrations[link.configurationId.id]?.manual == false
        }
    }

    fun registerNotification(notification: NotificatiesApiNotificationReceivedEvent) {
        notificatiesApiService.registerNotification(notification)
    }

    // Read from the stored properties, so a callback doesn't instantiate every configuration.
    private fun findRegistrations(): Map<UUID, Registration> =
        pluginService
            .getPluginConfigurations(PluginConfigurationSearchParameters(pluginDefinitionKey = PLUGIN_KEY))
            .associate { configuration ->
                val properties = configuration.properties
                configuration.id.id to
                    Registration(
                        manual =
                            properties?.path(ABONNEMENT_REGISTRATION_PROPERTY)?.textValue() ==
                                AbonnementRegistration.MANUAL.value,
                        secret = properties?.path(AUTH_HEADER_PROPERTY)?.textValue(),
                    )
            }

    private fun secretsMatch(
        expected: String,
        actual: String,
    ) = MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), actual.toByteArray(Charsets.UTF_8))

    private class Registration(
        val manual: Boolean,
        val secret: String?,
    )
}
