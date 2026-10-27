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

package com.ritense.valtimoplugins.notificatiesapimanual.plugin

import com.ritense.logging.withLoggingContext
import com.ritense.notificatiesapi.NotificatiesApiAuthentication
import com.ritense.notificatiesapi.NotificatiesApiPlugin
import com.ritense.notificatiesapi.client.NotificatiesApiClient
import com.ritense.notificatiesapi.domain.Notificatie
import com.ritense.plugin.annotation.Plugin
import com.ritense.plugin.annotation.PluginAction
import com.ritense.plugin.annotation.PluginActionProperty
import com.ritense.plugin.annotation.PluginEvent
import com.ritense.plugin.annotation.PluginProperty
import com.ritense.plugin.domain.EventType
import com.ritense.plugin.domain.PluginConfiguration
import com.ritense.plugin.domain.PluginConfigurationId
import com.ritense.plugin.service.PluginConfigurationSearchParameters
import com.ritense.plugin.service.PluginService
import com.ritense.processlink.domain.ActivityTypeWithEventName.INTERMEDIATE_CATCH_EVENT_END
import com.ritense.processlink.domain.ActivityTypeWithEventName.INTERMEDIATE_THROW_EVENT_START
import com.ritense.processlink.domain.ActivityTypeWithEventName.MESSAGE_START_EVENT_START
import com.ritense.processlink.domain.ActivityTypeWithEventName.RECEIVE_TASK_END
import com.ritense.processlink.domain.ActivityTypeWithEventName.SEND_TASK_START
import com.ritense.valtimo.contract.validation.Url
import com.ritense.valtimoplugins.notificatiesapimanual.domain.AbonnementRegistration
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.ValidationException
import java.net.URI
import java.time.LocalDateTime
import java.util.UUID

/**
 * Copy of the core Notificaties API plugin (key `notificatiesapi`) under its own key, with manual abonnement
 * registration (gzac-issues#971) added. Notificaties it receives go into core's inbound queue, so every existing
 * consumer of `NotificatiesApiNotificationReceivedEvent` gets them.
 */
@Plugin(
    key = NotificatiesApiManualPlugin.PLUGIN_KEY,
    title = "Notificaties API (handmatig abonnement)",
    description = "Notificaties API with an abonnement that Valtimo registers itself or that an administrator creates",
)
class NotificatiesApiManualPlugin(
    pluginConfigurationId: PluginConfigurationId,
    private val client: NotificatiesApiClient,
    private val pluginService: PluginService,
) {
    val configurationId: PluginConfigurationId = pluginConfigurationId

    @Url
    @PluginProperty(key = "url", secret = false)
    lateinit var url: URI

    @Url
    @PluginProperty(key = "callbackUrl", secret = false)
    lateinit var callbackUrl: URI

    @PluginProperty(key = AUTH_HEADER_PROPERTY, secret = false, required = false)
    var authHeader: String? = null

    @PluginProperty(key = "authenticationPluginConfiguration", secret = false)
    lateinit var authenticationPluginConfiguration: NotificatiesApiAuthentication

    @PluginProperty(key = ABONNEMENT_REGISTRATION_PROPERTY, secret = false, required = false)
    var abonnementRegistration: AbonnementRegistration = AbonnementRegistration.AUTOMATIC

    fun isManualAbonnementRegistration() = abonnementRegistration == AbonnementRegistration.MANUAL

    @PluginEvent(invokedOn = [EventType.CREATE, EventType.UPDATE])
    fun validateConfiguration() {
        authHeader?.let { secret ->
            // Valtimo's JWT filter and Spring's bearer token resolver take such a header as a (malformed) token.
            if (secret.startsWith("bearer", ignoreCase = true)) {
                throw ValidationException("The shared secret (authHeader) must not start with 'Bearer'")
            }
            // HTTP strips whitespace around header values, so such a secret could never match.
            if (secret.isNotBlank() && secret != secret.trim()) {
                throw ValidationException("The shared secret (authHeader) must not start or end with whitespace")
            }
            // Open Notificaties sends header values as Latin-1; plain printable ASCII is safe on every hop.
            if (secret.any { it !in ' '..'~' }) {
                throw ValidationException("The shared secret (authHeader) may only contain printable ASCII characters")
            }
            if (secret.length > MAX_AUTH_HEADER_LENGTH) {
                throw ValidationException(
                    "The shared secret (authHeader) must be at most $MAX_AUTH_HEADER_LENGTH characters",
                )
            }
        }
        if (isManualAbonnementRegistration() && authHeader.isNullOrBlank()) {
            throw ValidationException(
                "A shared secret (authHeader) is required when the abonnement is registered manually",
            )
        }
        val callback = normalize(callbackUrl)
        // Core's endpoint only accepts core's own keys, so every notificatie would get 401 there.
        if (callback.endsWith(CORE_CALLBACK_PATH)) {
            throw ValidationException(
                "The callback URL points at core's endpoint; use /api/v1/notificatiesapi-manual/callback",
            )
        }
        if (callback in coreCallbackUrls(pluginService)) {
            throw ValidationException(
                "The callback URL is already used by a core Notificaties API configuration; " +
                    "use this plugin's callback endpoint (/api/v1/notificatiesapi-manual/callback)",
            )
        }
    }

    @PluginAction(
        key = "publish-notificatie",
        title = "Publiceer een notificatie",
        description = "Publiceert een notificatie via de Notificaties API",
        activityTypes = [SEND_TASK_START, INTERMEDIATE_THROW_EVENT_START],
    )
    fun publishNotificatie(
        @PluginActionProperty kanaal: String,
        @PluginActionProperty hoofdObject: URI,
        @PluginActionProperty resource: String,
        @PluginActionProperty resourceUrl: URI,
        @PluginActionProperty actie: String,
        @PluginActionProperty aanmaakdatum: LocalDateTime?,
        @PluginActionProperty kenmerken: Map<String, String>?,
    ) = withLoggingContext(PluginConfiguration::class.java.canonicalName to configurationId.id.toString()) {
        logger.debug { "Publishing notificatie on kanaal '$kanaal'" }
        val notificatie =
            Notificatie(
                kanaal = kanaal,
                hoofdObject = hoofdObject,
                resource = resource,
                resourceUrl = resourceUrl,
                actie = actie,
                aanmaakdatum = aanmaakdatum ?: LocalDateTime.now(),
                kenmerken = kenmerken,
            )
        client.createNotificatie(authenticationPluginConfiguration, url, notificatie)
        logger.info { "Successfully published notificatie on kanaal '$kanaal'" }
    }

    @PluginAction(
        key = RECEIVE_NOTIFICATIE_ACTION_KEY,
        title = "Ontvang een notificatie",
        description = "Wacht op een binnenkomende notificatie via de Notificaties API",
        activityTypes = [RECEIVE_TASK_END, INTERMEDIATE_CATCH_EVENT_END, MESSAGE_START_EVENT_START],
    )
    fun receiveNotificatie(
        @PluginActionProperty kanaal: String?,
        @PluginActionProperty actie: String?,
        @PluginActionProperty kenmerken: Map<String, String>?,
    ) {
        logger.debug { "Receive notificatie action invoked for kanaal='$kanaal', actie='$actie', kenmerken=$kenmerken" }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        const val PLUGIN_KEY = "notificatiesapi-manual"
        const val CORE_PLUGIN_KEY = NotificatiesApiPlugin.NOTIFICATIES_API_PLUGIN_DEFINITION_KEY
        const val CORE_CALLBACK_PATH = "/api/v1/notificatiesapi/callback"
        const val RECEIVE_NOTIFICATIE_ACTION_KEY = NotificatiesApiPlugin.RECEIVE_NOTIFICATIE_ACTION_KEY
        const val AUTH_HEADER_PROPERTY = "authHeader"
        const val ABONNEMENT_REGISTRATION_PROPERTY = "abonnementRegistration"
        const val CALLBACK_URL_PROPERTY = "callbackUrl"
        const val MAX_AUTH_HEADER_LENGTH = 255

        fun normalize(url: URI): String = normalize(url.toString())

        fun normalize(url: String): String = url.trim().trimEnd('/')

        /** The callback URLs of all core Notificaties API configurations, normalized. */
        fun coreCallbackUrls(pluginService: PluginService): Set<String> =
            pluginService
                .getPluginConfigurations(PluginConfigurationSearchParameters(pluginDefinitionKey = CORE_PLUGIN_KEY))
                .mapNotNull { it.properties?.path(CALLBACK_URL_PROPERTY)?.textValue() }
                .map { normalize(it) }
                .toSet()

        /**
         * The stored properties of this plugin's configurations, without instantiating them (which would also resolve
         * their authentication configuration).
         */
        fun storedRegistrations(pluginService: PluginService): List<StoredRegistration> =
            pluginService
                .getPluginConfigurations(PluginConfigurationSearchParameters(pluginDefinitionKey = PLUGIN_KEY))
                .map { configuration ->
                    val properties = configuration.properties
                    StoredRegistration(
                        id = configuration.id.id,
                        manual =
                            properties?.path(ABONNEMENT_REGISTRATION_PROPERTY)?.textValue() ==
                                AbonnementRegistration.MANUAL.value,
                        callbackUrl = properties?.path(CALLBACK_URL_PROPERTY)?.textValue()?.let { normalize(it) },
                        secret = properties?.path(AUTH_HEADER_PROPERTY)?.textValue(),
                    )
                }
    }

    data class StoredRegistration(
        val id: UUID,
        val manual: Boolean,
        val callbackUrl: String?,
        val secret: String?,
    )
}
