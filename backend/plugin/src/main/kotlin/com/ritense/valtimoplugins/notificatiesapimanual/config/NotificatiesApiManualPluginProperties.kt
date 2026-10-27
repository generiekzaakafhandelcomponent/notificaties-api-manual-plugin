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

package com.ritense.valtimoplugins.notificatiesapimanual.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Settings of this plugin's automatic abonnement registration. Separate from core's `valtimo.zgw.*` settings, so
 * that turning off core's registration (needed behind a certificate-checking gateway) doesn't affect this plugin.
 *
 * The retry policy is the one of core #912: the startup registration retries with backoff until it succeeds or
 * [maxDuration] has passed, because Open Notificaties calls the callback while registering and the pod may not be
 * routable yet.
 */
@ConfigurationProperties(prefix = "valtimo.notificaties-api-manual")
class NotificatiesApiManualPluginProperties {
    var registerAbonnementen: Boolean = true
    var abonnementRegistration: AbonnementRegistration = AbonnementRegistration()

    class AbonnementRegistration {
        var initialBackoff: Duration = Duration.ofSeconds(2)
        var maxBackoff: Duration = Duration.ofSeconds(30)
        var maxDuration: Duration = Duration.ofMinutes(15)
    }
}
