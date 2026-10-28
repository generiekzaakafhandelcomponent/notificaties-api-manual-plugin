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
import com.ritense.valtimo.contract.annotation.SkipComponentScan
import com.ritense.valtimo.contract.domain.ValtimoMediaType.APPLICATION_JSON_UTF8_VALUE
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@SkipComponentScan
@RequestMapping("/api", produces = [APPLICATION_JSON_UTF8_VALUE])
class NotificatiesApiManualPluginCallbackResource(
    private val callbackService: NotificatiesApiManualPluginCallbackService,
) {
    @PostMapping("/v1/notificatiesapi-manual/callback")
    fun handleNotification(
        @Valid @RequestBody notification: NotificatiesApiNotificationReceivedEvent,
        @RequestHeader("Authorization", required = false) authHeader: String?,
    ): ResponseEntity<Void> {
        if (authHeader == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        // Open Notificaties sends a test notificatie when it validates a new abonnement.
        if (notification.kanaal == "test") {
            callbackService.clearMisroutedCallbacks(authHeader)
            return ResponseEntity.noContent().build()
        }
        if (!callbackService.isAuthorizedCallback(authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        callbackService.registerNotification(notification)
        callbackService.clearMisroutedCallbacks(authHeader)
        return ResponseEntity.noContent().build()
    }
}
