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

package com.ritense.valtimoplugins.notificatiesapimanual

import com.ritense.notificatiesapi.client.NotificatiesApiClient
import com.ritense.testutilscommon.junit.extension.LiquibaseRunnerExtension
import com.ritense.valtimo.contract.authentication.UserManagementService
import com.ritense.valtimo.contract.mail.MailSender
import com.ritense.valtimoplugins.notificatiesapimanual.registration.NotificatiesApiManualPluginCoreClient.Companion.unwrapped
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.junit.jupiter.SpringExtension

@SpringBootTest
@ExtendWith(value = [SpringExtension::class, LiquibaseRunnerExtension::class])
@Tag("integration")
abstract class BaseIntegrationTest : BaseTest() {
    @MockitoBean
    lateinit var userManagementService: UserManagementService

    @MockitoBean
    lateinit var mailSender: MailSender

    /** The bean in the context: this plugin's wrapper around the mock of [TestApplication]. */
    @Autowired
    lateinit var notificatiesApiClientBean: NotificatiesApiClient

    /** The mock behind [notificatiesApiClientBean]: every call that would reach Open Notificaties. */
    val notificatiesApiClient: NotificatiesApiClient
        get() = notificatiesApiClientBean.unwrapped()

    @AfterEach
    fun resetNotificatiesApiClient() {
        reset(notificatiesApiClient)
    }
}
