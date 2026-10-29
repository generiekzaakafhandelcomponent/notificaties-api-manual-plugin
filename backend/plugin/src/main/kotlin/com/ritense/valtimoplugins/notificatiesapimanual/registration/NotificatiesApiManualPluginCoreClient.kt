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
import com.ritense.notificatiesapi.NotificatiesApiAuthentication
import com.ritense.notificatiesapi.client.NotificatiesApiClient
import com.ritense.notificatiesapi.domain.Abonnement
import com.ritense.notificatiesapi.domain.Kanaal
import com.ritense.notificatiesapi.domain.Notificatie
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.manualUrls
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin.Companion.normalize
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.client.RestClient
import java.net.URI

/**
 * Replaces core's [NotificatiesApiClient] bean, so that core's abonnement registration leaves an Open Notificaties URL
 * with a configuration of this plugin alone.
 *
 * Core registers an abonnement for every core Notificaties API configuration. Behind a gateway that registration fails
 * (and on 13.41.0 up to 13.41.0-4 and 13.42.x stops the application from starting). An administrator creates that
 * abonnement instead (see `NotificatiesApiManualPluginManualAbonnementService`), so on such a URL this client:
 * - returns no abonnementen and no kanalen, so core deletes nothing and reads nothing;
 * - creates no kanalen and no abonnement, and changes and deletes no abonnement; a created or updated abonnement is
 *   returned with [placeholderAbonnementUrl] and the key core chose, which core stores as its link. Core's callback
 *   accepts notificaties that carry that key: the Auth header of the core configuration, when filled in.
 *
 * Every other call, and every call on another URL, goes to [delegate].
 *
 * Core's client is not final (Spring's all-open compiler plugin) and its constructor and methods are the same from
 * 13.41 up to and including 13.51; the constructor arguments are only used by methods that are all overridden here.
 */
class NotificatiesApiManualPluginCoreClient(
    val delegate: NotificatiesApiClient,
    private val pluginService: ObjectProvider<PluginService>,
    restClientBuilder: RestClient.Builder,
    objectMapper: ObjectMapper,
) : NotificatiesApiClient(restClientBuilder, objectMapper) {
    override fun getAbonnementen(
        authentication: NotificatiesApiAuthentication,
        baseUrl: URI,
    ): List<Abonnement> =
        if (hasManualConfiguration(baseUrl)) {
            emptyList()
        } else {
            delegate.getAbonnementen(authentication, baseUrl)
        }

    override fun getAbonnement(
        authentication: NotificatiesApiAuthentication,
        baseUrl: URI,
        abonnementId: String,
    ): Abonnement = delegate.getAbonnement(authentication, baseUrl, abonnementId)

    override fun updateAbonnement(
        authentication: NotificatiesApiAuthentication,
        baseUrl: URI,
        abonnementId: String,
        abonnement: Abonnement,
    ): Abonnement =
        if (hasManualConfiguration(baseUrl)) {
            logSkipped("Not updating abonnement '$abonnementId'", baseUrl)
            abonnement.copy(url = placeholderAbonnementUrl(baseUrl))
        } else {
            delegate.updateAbonnement(authentication, baseUrl, abonnementId, abonnement)
        }

    override fun createAbonnement(
        authentication: NotificatiesApiAuthentication,
        baseUrl: URI,
        abonnement: Abonnement,
    ): Abonnement =
        if (hasManualConfiguration(baseUrl)) {
            logSkipped("Not creating an abonnement", baseUrl)
            abonnement.copy(url = placeholderAbonnementUrl(baseUrl))
        } else {
            delegate.createAbonnement(authentication, baseUrl, abonnement)
        }

    override fun deleteAbonnement(
        authentication: NotificatiesApiAuthentication,
        baseUrl: URI,
        abonnementId: String,
    ) {
        if (hasManualConfiguration(baseUrl)) {
            logSkipped("Not deleting abonnement '$abonnementId'", baseUrl)
        } else {
            delegate.deleteAbonnement(authentication, baseUrl, abonnementId)
        }
    }

    override fun createNotificatie(
        authentication: NotificatiesApiAuthentication,
        baseUrl: URI,
        notificatie: Notificatie,
    ): Notificatie = delegate.createNotificatie(authentication, baseUrl, notificatie)

    override fun createKanaal(
        authentication: NotificatiesApiAuthentication,
        baseUrl: URI,
        kanaal: Kanaal,
    ): Kanaal =
        if (hasManualConfiguration(baseUrl)) {
            logSkipped("Not creating kanaal '${kanaal.naam}'", baseUrl)
            kanaal
        } else {
            delegate.createKanaal(authentication, baseUrl, kanaal)
        }

    override fun getKanalen(
        authentication: NotificatiesApiAuthentication,
        baseUrl: URI,
    ): List<Kanaal> =
        if (hasManualConfiguration(baseUrl)) {
            emptyList()
        } else {
            delegate.getKanalen(authentication, baseUrl)
        }

    private fun hasManualConfiguration(baseUrl: URI): Boolean =
        normalize(baseUrl) in manualUrls(pluginService.getObject())

    private fun logSkipped(
        action: String,
        baseUrl: URI,
    ) = logger.info {
        "$action in Open Notificaties at '$baseUrl' for a standard Notificaties API configuration: a " +
            "'Notificaties API, handmatig abonnement' configuration uses this URL, so an administrator creates " +
            "the abonnement and its kanalen"
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        /** Not an abonnement in Open Notificaties: what core stores as its link for a configuration on such a URL. */
        const val PLACEHOLDER_ABONNEMENT_ID = "registered-manually"

        fun placeholderAbonnementUrl(baseUrl: URI): String =
            "${normalize(baseUrl)}/abonnement/$PLACEHOLDER_ABONNEMENT_ID"

        /** The client that reaches Open Notificaties for every URL. */
        fun NotificatiesApiClient.unwrapped(): NotificatiesApiClient =
            (this as? NotificatiesApiManualPluginCoreClient)?.delegate ?: this
    }
}
