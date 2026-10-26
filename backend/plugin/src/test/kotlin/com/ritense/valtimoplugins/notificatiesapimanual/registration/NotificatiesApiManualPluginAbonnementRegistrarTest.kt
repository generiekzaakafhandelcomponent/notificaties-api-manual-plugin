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

import com.ritense.notificatiesapi.client.NotificatiesApiClient
import com.ritense.notificatiesapi.domain.Abonnement
import com.ritense.notificatiesapi.domain.Kanaal
import com.ritense.plugin.domain.PluginConfiguration
import com.ritense.plugin.events.PluginConfigurationDeletedEvent
import com.ritense.plugin.events.PluginConfigurationUpdatedEvent
import com.ritense.plugin.repository.PluginProcessLinkRepository
import com.ritense.plugin.service.PluginConfigurationSearchParameters
import com.ritense.plugin.service.PluginService
import com.ritense.processlink.event.ProcessLinkCreatedEvent
import com.ritense.processlink.event.ProcessLinkDeletedEvent
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.CALLBACK_URL
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.ON_URL
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.OTHER_ON_URL
import com.ritense.valtimoplugins.notificatiesapimanual.TestFixtures.receiveLink
import com.ritense.valtimoplugins.notificatiesapimanual.config.NotificatiesApiManualPluginProperties
import com.ritense.valtimoplugins.notificatiesapimanual.domain.AbonnementRegistration.MANUAL
import com.ritense.valtimoplugins.notificatiesapimanual.domain.NotificatiesApiManualPluginAbonnementLink
import com.ritense.valtimoplugins.notificatiesapimanual.domain.NotificatiesApiManualPluginConfigurationId
import com.ritense.valtimoplugins.notificatiesapimanual.plugin.NotificatiesApiManualPlugin
import com.ritense.valtimoplugins.notificatiesapimanual.repository.NotificatiesApiManualPluginAbonnementLinkRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import java.net.URI
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class NotificatiesApiManualPluginAbonnementRegistrarTest {
    private lateinit var client: NotificatiesApiClient
    private lateinit var linkRepository: NotificatiesApiManualPluginAbonnementLinkRepository
    private lateinit var pluginService: PluginService
    private lateinit var processLinkRepository: PluginProcessLinkRepository
    private lateinit var properties: NotificatiesApiManualPluginProperties
    private lateinit var registrar: NotificatiesApiManualPluginAbonnementRegistrar

    private val plugins = mutableListOf<NotificatiesApiManualPlugin>()
    private val coreConfigurations = mutableListOf<PluginConfiguration>()
    private val links = mutableListOf<NotificatiesApiManualPluginAbonnementLink>()

    @BeforeEach
    fun setUp() {
        client = mock()
        linkRepository = mock()
        pluginService = mock()
        processLinkRepository = mock()
        properties = NotificatiesApiManualPluginProperties()
        registrar =
            NotificatiesApiManualPluginAbonnementRegistrar(
                client,
                linkRepository,
                pluginService,
                processLinkRepository,
                mock<PlatformTransactionManager>(),
                properties,
            )

        whenever(pluginService.getObjectMapper()).thenReturn(TestFixtures.objectMapper)
        whenever(pluginService.getPluginConfigurations(any())).thenAnswer { invocation ->
            when (invocation.getArgument<PluginConfigurationSearchParameters>(0).pluginDefinitionKey) {
                NotificatiesApiManualPlugin.PLUGIN_KEY -> plugins.map { TestFixtures.configurationOf(it) }
                NotificatiesApiManualPlugin.CORE_PLUGIN_KEY -> coreConfigurations
                else -> emptyList()
            }
        }
        whenever(pluginService.createInstance(any<PluginConfiguration>())).thenAnswer { invocation ->
            val id = invocation.getArgument<PluginConfiguration>(0).id.id
            plugins.first { it.configurationId.id == id }
        }
        whenever(
            processLinkRepository.findByPluginDefinitionKeyAndPluginActionDefinitionKey(any(), any()),
        ).thenReturn(emptyList())
        whenever(linkRepository.findAll()).thenAnswer { links.toList() }
        whenever(linkRepository.save(any<NotificatiesApiManualPluginAbonnementLink>())).thenAnswer { invocation ->
            invocation.getArgument<NotificatiesApiManualPluginAbonnementLink>(0).also { saved ->
                links.removeIf { it.configurationId == saved.configurationId }
                links.add(saved)
            }
        }
        whenever(client.getAbonnementen(any(), any())).thenReturn(emptyList())
        whenever(client.getKanalen(any(), any())).thenReturn(emptyList())
        whenever(client.createAbonnement(any(), any(), any())).thenAnswer { invocation ->
            invocation.getArgument<Abonnement>(2).copy(url = "${ON_URL}abonnement/${UUID.randomUUID()}")
        }
        whenever(client.updateAbonnement(any(), any(), any(), any())).thenAnswer { invocation ->
            invocation.getArgument<Abonnement>(3).copy(url = "${ON_URL}abonnement/${invocation.getArgument<String>(2)}")
        }
    }

    @Test
    fun `makes no call to Open Notificaties when there is no configuration of this plugin`() {
        registrar.registerAbonnementen()

        verifyNoInteractions(client)
    }

    @Test
    fun `makes no call to Open Notificaties for a manual configuration`() {
        plugins += TestFixtures.plugin(registration = MANUAL, secret = "secret")
        whenever(processLinkRepository.findByPluginDefinitionKeyAndPluginActionDefinitionKey(any(), any()))
            .thenAnswer { listOf(receiveLink(plugins[0].configurationId.id, "zaken")) }

        registrar.registerAbonnementen()

        verifyNoInteractions(client)
    }

    @Test
    fun `makes no call when registration is disabled`() {
        plugins += TestFixtures.plugin()
        properties.registerAbonnementen = false

        registrar.registerAbonnementen()

        verifyNoInteractions(client)
    }

    @Test
    fun `creates an abonnement with the configured secret and the kanalen of its links`() {
        val plugin = TestFixtures.plugin(secret = "own-secret").also { plugins += it }
        whenever(processLinkRepository.findByPluginDefinitionKeyAndPluginActionDefinitionKey(any(), any()))
            .thenAnswer { listOf(receiveLink(plugin.configurationId.id, "zaken", "create")) }

        registrar.registerAbonnementen()

        val created = argumentCaptor<Abonnement>()
        verify(client).createAbonnement(any(), eq(URI(ON_URL)), created.capture())
        assertEquals(CALLBACK_URL, created.firstValue.callbackUrl)
        assertEquals("own-secret", created.firstValue.auth)
        assertEquals(
            listOf(Abonnement.Kanaal(naam = "zaken", filters = mapOf("actie" to "create"))),
            created.firstValue.kanalen,
        )
        verify(client).createKanaal(any(), eq(URI(ON_URL)), eq(Kanaal(naam = "zaken")))
        assertEquals("own-secret", links.single().auth)
    }

    @Test
    fun `each Open Notificaties URL gets only the kanalen of its own configurations`() {
        val first = TestFixtures.plugin().also { plugins += it }
        val second = TestFixtures.plugin(url = OTHER_ON_URL).also { plugins += it }
        whenever(processLinkRepository.findByPluginDefinitionKeyAndPluginActionDefinitionKey(any(), any())).thenAnswer {
            listOf(
                receiveLink(first.configurationId.id, "zaken"),
                receiveLink(second.configurationId.id, "documenten"),
            )
        }

        registrar.registerAbonnementen()

        verify(client).createAbonnement(any(), eq(URI(ON_URL)), argThat { kanalen.map { it.naam } == listOf("zaken") })
        verify(client).createAbonnement(
            any(),
            eq(URI(OTHER_ON_URL)),
            argThat {
                kanalen.map { it.naam } ==
                    listOf("documenten")
            },
        )
    }

    @Test
    fun `sends no update when nothing changed, although Open Notificaties never returns auth`() {
        val plugin = TestFixtures.plugin(secret = "own-secret").also { plugins += it }
        registrar.registerAbonnementen()
        val abonnementUrl = links.single().url
        whenever(client.getAbonnementen(any(), any()))
            .thenReturn(
                listOf(Abonnement(url = abonnementUrl, callbackUrl = CALLBACK_URL, auth = null, kanalen = emptyList())),
            )

        registrar.registerAbonnementen()

        verify(client, never()).updateAbonnement(any(), any(), any(), any())
        assertEquals(plugin.configurationId.id, links.single().configurationId.id)
    }

    @Test
    fun `sends an update when the secret changed`() {
        val plugin = TestFixtures.plugin(secret = "old-secret").also { plugins += it }
        registrar.registerAbonnementen()
        val abonnementUrl = links.single().url
        whenever(client.getAbonnementen(any(), any()))
            .thenReturn(
                listOf(Abonnement(url = abonnementUrl, callbackUrl = CALLBACK_URL, auth = null, kanalen = emptyList())),
            )
        plugin.authHeader = "new-secret"

        registrar.registerAbonnementen()

        verify(client).updateAbonnement(
            any(),
            any(),
            eq(abonnementUrl.substringAfterLast('/')),
            argThat {
                auth ==
                    "new-secret"
            },
        )
        assertEquals("new-secret", links.single().auth)
    }

    @Test
    fun `deletes an untracked abonnement with its own callback URL`() {
        plugins += TestFixtures.plugin()
        val stale = Abonnement(url = "${ON_URL}abonnement/stale", callbackUrl = CALLBACK_URL, auth = null)
        whenever(client.getAbonnementen(any(), any())).thenReturn(listOf(stale))

        registrar.registerAbonnementen()

        verify(client).deleteAbonnement(any(), any(), eq("stale"))
    }

    @Test
    fun `never deletes an abonnement whose callback URL belongs to a manual configuration, also on another URL`() {
        plugins += TestFixtures.plugin()
        plugins += TestFixtures.plugin(registration = MANUAL, secret = "secret", url = OTHER_ON_URL)
        val manualAbonnement = Abonnement(url = "${ON_URL}abonnement/manual", callbackUrl = CALLBACK_URL, auth = null)
        whenever(client.getAbonnementen(any(), any())).thenReturn(listOf(manualAbonnement))

        registrar.registerAbonnementen()

        verify(client, never()).deleteAbonnement(any(), any(), any())
    }

    @Test
    fun `never deletes untracked abonnementen when a core configuration uses the same callback URL`() {
        plugins += TestFixtures.plugin()
        coreConfigurations +=
            TestFixtures.pluginConfiguration(UUID.randomUUID(), "notificatiesapi", mapOf("callbackUrl" to CALLBACK_URL))
        val coreAbonnement = Abonnement(url = "${ON_URL}abonnement/core", callbackUrl = CALLBACK_URL, auth = null)
        whenever(client.getAbonnementen(any(), any())).thenReturn(listOf(coreAbonnement))

        registrar.registerAbonnementen()

        verify(client, never()).deleteAbonnement(any(), any(), any())
    }

    @Test
    fun `deleting a manual configuration removes only its leftover link row`() {
        val plugin = TestFixtures.plugin(registration = MANUAL, secret = "secret").also { plugins += it }
        val link = link(plugin, "${ON_URL}abonnement/old")
        whenever(linkRepository.findById(link.configurationId)).thenReturn(Optional.of(link))
        registrar.handleApplicationFullyReadyEvent()

        registrar.handlePluginConfigurationDeletedEvent(
            PluginConfigurationDeletedEvent(TestFixtures.configurationOf(plugin)),
        )

        verify(linkRepository).delete(link)
        verify(client, never()).deleteAbonnement(any(), any(), any())
    }

    @Test
    fun `deleting an automatic configuration deletes its abonnement`() {
        val plugin = TestFixtures.plugin().also { plugins += it }
        val link = link(plugin, "${ON_URL}abonnement/abc")
        whenever(linkRepository.findById(link.configurationId)).thenReturn(Optional.of(link))
        registrar.handleApplicationFullyReadyEvent()
        val configuration = TestFixtures.configurationOf(plugin)
        plugins.clear()
        doAnswer { plugin }.whenever(pluginService).createInstance(configuration)

        registrar.handlePluginConfigurationDeletedEvent(PluginConfigurationDeletedEvent(configuration))

        verify(client).deleteAbonnement(any(), eq(URI(ON_URL)), eq("abc"))
        verify(linkRepository).delete(link)
    }

    @Test
    fun `a deleted process link triggers a sweep`() {
        plugins += TestFixtures.plugin()
        registrar.handleApplicationFullyReadyEvent()

        registrar.handleProcessLinkDeletedEvent(ProcessLinkDeletedEvent("plugin", "process:1"))

        verify(client, org.mockito.kotlin.atLeastOnce()).getAbonnementen(any(), any())
    }

    @Test
    fun `changes to unrelated plugin configurations trigger nothing`() {
        plugins += TestFixtures.plugin()
        registrar.handleApplicationFullyReadyEvent()
        org.mockito.Mockito.clearInvocations(client)

        registrar.handlePluginConfigurationUpdatedEvent(
            PluginConfigurationUpdatedEvent(
                TestFixtures.pluginConfiguration(UUID.randomUUID(), "zakenapi", emptyMap()),
            ),
        )

        verifyNoInteractions(client)
    }

    @Test
    fun `the abonnement of a URL group stays with the configuration that has the link, whatever the query order`() {
        val owner = TestFixtures.plugin(id = UUID.fromString("ffffffff-0000-0000-0000-000000000000"))
        val other = TestFixtures.plugin(id = UUID.fromString("00000000-0000-0000-0000-000000000001"))
        plugins += listOf(other, owner)
        links += link(owner, "${ON_URL}abonnement/existing")
        whenever(client.getAbonnementen(any(), any()))
            .thenReturn(
                listOf(Abonnement(url = "${ON_URL}abonnement/existing", callbackUrl = CALLBACK_URL, auth = null)),
            )

        registrar.registerAbonnementen()
        plugins.reverse()
        registrar.registerAbonnementen()

        verify(client, never()).createAbonnement(any(), any(), any())
        assertEquals(listOf(owner.configurationId.id), links.map { it.configurationId.id })
    }

    @Test
    fun `a changed configuration id keeps its link`() {
        val oldId = UUID.randomUUID()
        val newId = UUID.randomUUID()
        val link =
            NotificatiesApiManualPluginAbonnementLink(
                NotificatiesApiManualPluginConfigurationId(oldId),
                "${ON_URL}abonnement/a",
                "key",
            )
        whenever(linkRepository.findById(link.configurationId)).thenReturn(Optional.of(link))

        registrar.handlePluginConfigurationIdUpdatedEvent(
            com.ritense.plugin.events
                .PluginConfigurationIdUpdatedEvent(newId, oldId, mock()),
        )

        verify(linkRepository).delete(link)
        assertEquals(newId, links.single().configurationId.id)
        assertEquals("key", links.single().auth)
    }

    @Test
    fun `removes the link of a deleted automatic configuration also when Open Notificaties refuses the delete`() {
        val plugin = TestFixtures.plugin().also { plugins += it }
        val link = link(plugin, "${ON_URL}abonnement/abc")
        whenever(linkRepository.findById(link.configurationId)).thenReturn(Optional.of(link))
        whenever(client.deleteAbonnement(any(), any(), any())).thenThrow(IllegalStateException("unreachable"))
        registrar.handleApplicationFullyReadyEvent()
        val configuration = TestFixtures.configurationOf(plugin)
        plugins.clear()
        doAnswer { plugin }.whenever(pluginService).createInstance(configuration)

        registrar.handlePluginConfigurationDeletedEvent(PluginConfigurationDeletedEvent(configuration))

        verify(linkRepository).delete(link)
    }

    @Test
    fun `checks for a callback URL shared with core also when there is nothing to register`() {
        plugins += TestFixtures.plugin(registration = MANUAL, secret = "secret")
        coreConfigurations +=
            TestFixtures.pluginConfiguration(UUID.randomUUID(), "notificatiesapi", mapOf("callbackUrl" to CALLBACK_URL))

        registrar.registerAbonnementen()

        verify(pluginService).getPluginConfigurations(argThat { pluginDefinitionKey == "notificatiesapi" })
        verifyNoInteractions(client)
    }

    @Test
    fun `a second linked configuration in a URL group loses its abonnement to the owner`() {
        val owner = TestFixtures.plugin(id = UUID.fromString("10000000-0000-0000-0000-000000000000"))
        val other = TestFixtures.plugin(id = UUID.fromString("20000000-0000-0000-0000-000000000000"))
        plugins += listOf(owner, other)
        links += link(owner, "${ON_URL}abonnement/owner")
        links += link(other, "${ON_URL}abonnement/other")
        whenever(client.getAbonnementen(any(), any())).thenReturn(
            listOf(
                Abonnement(url = "${ON_URL}abonnement/owner", callbackUrl = CALLBACK_URL, auth = null),
                Abonnement(url = "${ON_URL}abonnement/other", callbackUrl = CALLBACK_URL, auth = null),
            ),
        )

        registrar.registerAbonnementen()

        verify(client).deleteAbonnement(any(), any(), eq("other"))
        verify(client, never()).deleteAbonnement(any(), any(), eq("owner"))
        verify(linkRepository).delete(
            org.mockito.kotlin.argThat<NotificatiesApiManualPluginAbonnementLink> {
                url.endsWith("/other")
            },
        )
    }

    @Test
    fun `a link saved for one URL is not taken for stale by the next URL group`() {
        val first =
            TestFixtures.plugin(id = UUID.fromString("00000000-0000-0000-0000-000000000001")).also {
                plugins +=
                    it
            }
        plugins += TestFixtures.plugin(id = UUID.fromString("00000000-0000-0000-0000-000000000002"), url = OTHER_ON_URL)
        // Both URLs reach the same Open Notificaties: it lists what was created for the first group.
        val created = mutableListOf<Abonnement>()
        whenever(client.createAbonnement(any(), any(), any())).thenAnswer { invocation ->
            invocation
                .getArgument<Abonnement>(
                    2,
                ).copy(url = "${ON_URL}abonnement/${created.size}")
                .also { created += it }
        }
        // ...and lists them under the URL it is asked on.
        whenever(client.getAbonnementen(any(), any())).thenAnswer { invocation ->
            val baseUrl = invocation.getArgument<URI>(1).toString()
            created.map { it.copy(url = "${baseUrl}abonnement/${it.getId()}") }
        }

        registrar.registerAbonnementen()

        verify(client, never()).deleteAbonnement(any(), any(), any())
        assertEquals(2, links.size)
        assertEquals(first.configurationId.id, links.first().configurationId.id)
    }

    @Test
    fun `events share one queued sweep until it starts`() {
        val queue = ArrayDeque<Runnable>()
        val queued = registrarWith { queue.add(it) }
        queued.handleApplicationFullyReadyEvent()
        queue.clear()

        queued.handleProcessLinkCreatedEvent(ProcessLinkCreatedEvent("plugin", "process:1"))
        queued.handleProcessLinkCreatedEvent(ProcessLinkCreatedEvent("plugin", "process:1"))
        assertEquals(1, queue.size)

        queue.removeFirst().run()
        queued.handleProcessLinkCreatedEvent(ProcessLinkCreatedEvent("plugin", "process:1"))
        assertEquals(1, queue.size)
    }

    @Test
    fun `the cleanup after a deleted configuration waits for a running sweep`() {
        val plugin = TestFixtures.plugin().also { plugins += it }
        val link = link(plugin, "${ON_URL}abonnement/abc")
        whenever(linkRepository.findById(link.configurationId)).thenReturn(Optional.of(link))
        val queue = ArrayDeque<Runnable>()
        val queued = registrarWith { queue.add(it) }
        queued.handleApplicationFullyReadyEvent()
        queue.clear()

        queued.handlePluginConfigurationDeletedEvent(
            PluginConfigurationDeletedEvent(TestFixtures.configurationOf(plugin)),
        )

        verify(linkRepository, never()).delete(any())
        while (queue.isNotEmpty()) queue.removeFirst().run()
        verify(linkRepository).delete(link)
    }

    @Test
    fun `a disabled registration instantiates nothing`() {
        plugins += TestFixtures.plugin()
        properties.registerAbonnementen = false
        whenever(pluginService.createInstance(any<PluginConfiguration>())).thenThrow(IllegalStateException("broken"))

        registrar.registerAbonnementen()

        verifyNoInteractions(client)
    }

    @Test
    fun `a sweep removes the link of a removed configuration and then its abonnement`() {
        plugins += TestFixtures.plugin()
        links +=
            NotificatiesApiManualPluginAbonnementLink(
                NotificatiesApiManualPluginConfigurationId(UUID.randomUUID()),
                "${ON_URL}abonnement/orphan",
                "old-key",
            )
        whenever(client.getAbonnementen(any(), any()))
            .thenReturn(listOf(Abonnement(url = "${ON_URL}abonnement/orphan", callbackUrl = CALLBACK_URL, auth = null)))

        registrar.registerAbonnementen()

        verify(linkRepository).delete(argThat<NotificatiesApiManualPluginAbonnementLink> { url.endsWith("/orphan") })
        verify(client).deleteAbonnement(any(), any(), eq("orphan"))
    }

    @Test
    fun `a rejected sweep doesn't stop later sweeps`() {
        var reject = false
        val queue = ArrayDeque<Runnable>()
        val queued =
            registrarWith {
                if (reject) {
                    reject = false
                    throw java.util.concurrent.RejectedExecutionException("full")
                }
                queue.add(it)
            }
        queued.handleApplicationFullyReadyEvent()
        queue.clear()
        reject = true

        queued.handleProcessLinkCreatedEvent(ProcessLinkCreatedEvent("plugin", "process:1"))
        queued.handleProcessLinkCreatedEvent(ProcessLinkCreatedEvent("plugin", "process:1"))

        assertEquals(1, queue.size)
    }

    private fun registrarWith(executor: java.util.concurrent.Executor) =
        NotificatiesApiManualPluginAbonnementRegistrar(
            client,
            linkRepository,
            pluginService,
            processLinkRepository,
            mock<PlatformTransactionManager>(),
            properties,
            executor,
        )

    private fun link(
        plugin: NotificatiesApiManualPlugin,
        url: String,
    ) = NotificatiesApiManualPluginAbonnementLink(
        NotificatiesApiManualPluginConfigurationId(plugin.configurationId.id),
        url,
        "auth",
    )
}
