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
import com.ritense.notificatiesapi.client.NotificatiesApiClient
import com.ritense.notificatiesapi.domain.Abonnement
import com.ritense.notificatiesapi.domain.Kanaal
import com.ritense.plugin.annotation.Plugin
import com.ritense.plugin.annotation.PluginEvent
import com.ritense.plugin.annotation.PluginProperty
import com.ritense.plugin.domain.EventType
import com.ritense.plugin.domain.PluginConfiguration
import com.ritense.plugin.domain.PluginConfigurationId
import com.ritense.plugin.service.PluginConfigurationSearchParameters
import com.ritense.plugin.service.PluginService
import com.ritense.valtimo.contract.validation.Url
import com.ritense.valtimoplugins.notificatiesapimanual.domain.AbonnementRegistration
import com.ritense.valtimoplugins.notificatiesapimanual.domain.NotificatiesApiManualPluginAbonnementLink
import com.ritense.valtimoplugins.notificatiesapimanual.domain.NotificatiesApiManualPluginConfigurationId
import com.ritense.valtimoplugins.notificatiesapimanual.repository.NotificatiesApiManualPluginAbonnementLinkRepository
import jakarta.validation.ValidationException
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.web.client.HttpClientErrorException
import java.net.URI
import java.security.SecureRandom
import java.util.Base64

/**
 * Copy of the Valtimo 12 core Notificaties API plugin (key `notificatiesapi`) under its own key, with manual abonnement
 * registration (gzac-issues#971) added. Notificaties it receives go into core's inbound queue, so every existing
 * consumer of `NotificatiesApiNotificationReceivedEvent` gets them.
 *
 * As in v12 core, the abonnement is registered from the plugin events, with the fixed kanaal [KANAAL]. There is one
 * event method per event type, which validates before it calls Open Notificaties: plugin events run in no defined
 * order, and an abonnement created before a failing validation would stay behind in Open Notificaties.
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
    private val linkRepository: NotificatiesApiManualPluginAbonnementLinkRepository,
) {
    val configurationId: PluginConfigurationId = pluginConfigurationId

    private val linkId get() = NotificatiesApiManualPluginConfigurationId(configurationId.id)

    @Url
    @PluginProperty(key = "url", secret = false)
    lateinit var url: URI

    @Url
    @PluginProperty(key = CALLBACK_URL_PROPERTY, secret = false)
    lateinit var callbackUrl: URI

    @PluginProperty(key = AUTH_HEADER_PROPERTY, secret = false, required = false)
    var authHeader: String? = null

    @PluginProperty(key = "authenticationPluginConfiguration", secret = false)
    lateinit var authenticationPluginConfiguration: NotificatiesApiAuthentication

    @PluginProperty(key = ABONNEMENT_REGISTRATION_PROPERTY, secret = false, required = false)
    var abonnementRegistration: AbonnementRegistration = AbonnementRegistration.AUTOMATIC

    fun isManualAbonnementRegistration() = abonnementRegistration == AbonnementRegistration.MANUAL

    @PluginEvent(invokedOn = [EventType.CREATE])
    fun onCreate() =
        withLoggingContext(PluginConfiguration::class.java.canonicalName to configurationId.id.toString()) {
            validateConfiguration()
            if (isManualAbonnementRegistration()) return@withLoggingContext
            createAbonnement(currentKey = null)
        }

    @PluginEvent(invokedOn = [EventType.UPDATE])
    fun onUpdate() =
        withLoggingContext(PluginConfiguration::class.java.canonicalName to configurationId.id.toString()) {
            validateConfiguration()
            val link = linkRepository.findByIdOrNull(linkId)
            if (isManualAbonnementRegistration()) {
                // A left-over link would make a later switch back to automatic update an abonnement the
                // administrator has removed.
                link?.let { linkRepository.delete(it) }
                return@withLoggingContext
            }
            if (link == null) {
                createAbonnement(currentKey = null)
                return@withLoggingContext
            }
            val current =
                try {
                    client.getAbonnement(authenticationPluginConfiguration, url, link.getAbonnementId())
                } catch (e: HttpClientErrorException.NotFound) {
                    logger.info { "Abonnement '${link.url}' no longer exists in Open Notificaties; creating a new one" }
                    null
                }
            if (current == null) {
                createAbonnement(currentKey = link.auth)
                return@withLoggingContext
            }
            val key = desiredKey(link.auth)
            // Open Notificaties never returns auth, so the key it has is the one stored in the link.
            if (current.equals(link.url, callbackUrl.toASCIIString(), setOf(KANAAL)) && link.auth == key) {
                logger.debug { "Skipping abonnement update; nothing changed" }
                return@withLoggingContext
            }
            client.updateAbonnement(
                authenticationPluginConfiguration,
                url,
                link.getAbonnementId(),
                Abonnement(callbackUrl = callbackUrl.toASCIIString(), auth = key, kanalen = kanalen()),
            )
            linkRepository.save(NotificatiesApiManualPluginAbonnementLink(linkId, link.url, key))
            logger.info { "Abonnement '${link.url}' updated" }
        }

    @PluginEvent(invokedOn = [EventType.DELETE])
    fun onDelete() =
        withLoggingContext(PluginConfiguration::class.java.canonicalName to configurationId.id.toString()) {
            val link = linkRepository.findByIdOrNull(linkId) ?: return@withLoggingContext
            if (!isManualAbonnementRegistration()) {
                try {
                    client.deleteAbonnement(authenticationPluginConfiguration, url, link.getAbonnementId())
                    logger.info { "Abonnement '${link.url}' deleted" }
                } catch (e: Exception) {
                    // Valtimo 12 has no stale cleanup, so nothing would retry this.
                    logger.error(e) { "Could not delete abonnement '${link.url}'; remove it in Open Notificaties" }
                }
            }
            linkRepository.delete(link)
        }

    fun validateConfiguration() {
        val secret = authHeader
        if (isManualAbonnementRegistration() && secret.isNullOrBlank()) {
            throw ValidationException(
                "A shared secret (authHeader) is required when the abonnement is registered manually",
            )
        }
        // Valtimo's JWT filter and Spring's bearer token resolver take such a header as a (malformed) token.
        if (secret != null && secret.startsWith("bearer", ignoreCase = true)) {
            throw ValidationException("The shared secret (authHeader) must not start with 'Bearer'")
        }
        if (secret != null && secret.isNotBlank() && secret != secret.trim()) {
            // HTTP strips whitespace around header values, so such a secret could never match.
            throw ValidationException("The shared secret (authHeader) must not start or end with whitespace")
        }
        // Open Notificaties sends header values as Latin-1; plain printable ASCII is safe on every hop.
        if (secret != null && secret.any { it !in ' '..'~' }) {
            throw ValidationException("The shared secret (authHeader) may only contain printable ASCII characters")
        }
        if (secret != null && secret.length > MAX_AUTH_HEADER_LENGTH) {
            throw ValidationException(
                "The shared secret (authHeader) must be at most $MAX_AUTH_HEADER_LENGTH characters",
            )
        }
        // Core's endpoint only accepts core's own keys, so every notificatie would get 401 there.
        if (normalize(callbackUrl).endsWith(CORE_CALLBACK_PATH)) {
            throw ValidationException(
                "The callback URL points at core's endpoint; use /api/v1/notificatiesapi-manual/callback",
            )
        }
        if (coreCallbackUrls(pluginService).contains(normalize(callbackUrl))) {
            throw ValidationException(
                "The callback URL is already used by a core Notificaties API configuration; " +
                    "use this plugin's callback endpoint (/api/v1/notificatiesapi-manual/callback)",
            )
        }
        val clashing =
            otherConfigurationsOfThisPlugin()
                .filter { it.manual != isManualAbonnementRegistration() }
                .any { it.callbackUrl == normalize(callbackUrl) }
        if (clashing) {
            throw ValidationException(
                "A manual and an automatic configuration can't share a callback URL; use " +
                    "/api/v1/notificatiesapi-manual/callback/manual for manual configurations",
            )
        }
    }

    fun kanalen() = listOf(Abonnement.Kanaal(naam = KANAAL))

    private fun createAbonnement(currentKey: String?) {
        val key = desiredKey(currentKey)
        ensureKanaalExists()
        val abonnement =
            client.createAbonnement(
                authenticationPluginConfiguration,
                url,
                Abonnement(callbackUrl = callbackUrl.toASCIIString(), auth = key, kanalen = kanalen()),
            )
        linkRepository.save(NotificatiesApiManualPluginAbonnementLink(linkId, abonnement.url!!, key))
        logger.info { "Abonnement '${abonnement.url}' created" }
    }

    private fun desiredKey(currentKey: String?): String =
        authHeader?.takeIf { it.isNotBlank() } ?: currentKey?.takeIf { it.isNotBlank() } ?: createRandomKey()

    private fun ensureKanaalExists() {
        if (client.getKanalen(authenticationPluginConfiguration, url).none { it.naam == KANAAL }) {
            client.createKanaal(authenticationPluginConfiguration, url, Kanaal(naam = KANAAL))
        }
    }

    private fun otherConfigurationsOfThisPlugin(): List<StoredRegistration> =
        pluginService
            .getPluginConfigurations(PluginConfigurationSearchParameters(pluginDefinitionKey = PLUGIN_KEY))
            .filter { it.id.id != configurationId.id }
            .map { configuration ->
                val properties = configuration.properties
                StoredRegistration(
                    manual =
                        properties?.path(ABONNEMENT_REGISTRATION_PROPERTY)?.textValue() ==
                            AbonnementRegistration.MANUAL.value,
                    callbackUrl = properties?.path(CALLBACK_URL_PROPERTY)?.textValue()?.let { normalize(it) },
                )
            }

    private fun createRandomKey(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    private class StoredRegistration(
        val manual: Boolean,
        val callbackUrl: String?,
    )

    companion object {
        private val logger = KotlinLogging.logger {}
        const val PLUGIN_KEY = "notificatiesapi-manual"
        const val CORE_PLUGIN_KEY = "notificatiesapi"
        const val CORE_CALLBACK_PATH = "/api/v1/notificatiesapi/callback"
        const val AUTH_HEADER_PROPERTY = "authHeader"
        const val ABONNEMENT_REGISTRATION_PROPERTY = "abonnementRegistration"
        const val CALLBACK_URL_PROPERTY = "callbackUrl"
        const val MAX_AUTH_HEADER_LENGTH = 255

        /** The only kanaal Valtimo 12 core registers, without filters. */
        const val KANAAL = "objecten"

        fun normalize(url: URI): String = normalize(url.toString())

        fun normalize(url: String): String = url.trim().trimEnd('/')

        /** The callback URLs of all core Notificaties API configurations, normalized. */
        fun coreCallbackUrls(pluginService: PluginService): Set<String> =
            pluginService
                .getPluginConfigurations(PluginConfigurationSearchParameters(pluginDefinitionKey = CORE_PLUGIN_KEY))
                .mapNotNull { it.properties?.path(CALLBACK_URL_PROPERTY)?.textValue() }
                .map { normalize(it) }
                .toSet()
    }
}
