/*
 * Copyright 2015-2025 Ritense BV, the Netherlands.
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

import {
  notificatiesApiPluginSpecification,
  PluginSpecification,
  PublishNotificatieConfigurationComponent,
  ReceiveNotificatieConfigurationComponent,
} from '@valtimo/plugin';
import {NotificatiesApiManualPluginConfigurationComponent} from './components/notificaties-api-manual-plugin-configuration/notificaties-api-manual-plugin-configuration.component';

const notificatiesApiManualPluginSpecification: PluginSpecification = {
  pluginId: 'notificatiesapi-manual',
  pluginConfigurationComponent: NotificatiesApiManualPluginConfigurationComponent,
  pluginLogoBase64: notificatiesApiPluginSpecification.pluginLogoBase64,
  functionConfigurationComponents: {
    'publish-notificatie': PublishNotificatieConfigurationComponent,
    'receive-notificatie': ReceiveNotificatieConfigurationComponent,
  },
  pluginTranslations: {
    nl: {
      title: 'Notificaties API (handmatig abonnement)',
      url: 'Notificaties API URL',
      urlTooltip: 'Een URL naar de REST API van Notificaties',
      callbackUrl: 'Callback URL',
      callbackUrlTooltip:
        'Het GZAC API-endpoint waar notificaties naartoe moeten worden gestuurd: .../api/v1/notificatiesapi-manual/callback',
      description: 'Notificaties API met een abonnement dat GZAC zelf registreert of dat een beheerder aanmaakt.',
      configurationTitle: 'Configuratienaam',
      configurationTitleTooltip:
        'De naam van de huidige plugin-configuratie. Onder deze naam kan de configuratie in de rest van de applicatie teruggevonden worden.',
      authenticationPluginConfiguration: 'Configuratie authenticatie-plug-in',
      authHeader: 'Authenticatie header',
      authHeaderTooltip:
        'Secret van de authenticatie header voor de callback URL. Als niet ingevuld blijft de sleutel van het bestaande abonnement in gebruik, of wordt een random secret gegenereerd. Een wijziging wordt bij het opslaan naar het abonnement gestuurd.',
      abonnementRegistration: 'Registratie abonnement',
      abonnementRegistrationTooltip:
        'Automatisch: GZAC registreert het abonnement in Open Notificaties. Handmatig: een beheerder maakt het abonnement aan in Open Notificaties, bijvoorbeeld omdat het verkeer via een gateway met certificaten loopt.',
      abonnementRegistrationAutomatic: 'Automatisch',
      abonnementRegistrationManual: 'Handmatig',
      sharedSecret: 'Gedeeld geheim',
      sharedSecretTooltip:
        'Vul hetzelfde geheim in bij het veld Auth van het abonnement in Open Notificaties. GZAC accepteert alleen notificaties met een geheim van deze plug-in. Gebruik een gewone waarde die niet met "Bearer" begint.',
      manualAbonnementDescription:
        'Het abonnement in Open Notificaties moet deze callback URL en deze kanalen met filters bevatten. Ook de kanalen van Notificaties API-koppelingen van de standaard Notificaties API-plugin (zoals Verzoek en Portaaltaak) met dezelfde Open Notificaties URL staan erbij.',
      manualAbonnementKanaal: 'Kanaal',
      manualAbonnementNoKanalen: 'Er zijn nog geen kanalen nodig voor deze configuratie.',
      manualAbonnementSaveFirst:
        'Sla de configuratie op met handmatige registratie om te zien welke kanalen en filters het abonnement moet bevatten.',
      'publish-notificatie': 'Publiceer een notificatie',
      kanaal: 'Kanaal',
      kanaalTooltip: 'Het kanaal waarop de notificatie gepubliceerd wordt (max 50 tekens).',
      hoofdObject: 'Hoofd-object URL',
      hoofdObjectTooltip: 'URL-referentie naar het hoofd-object van de publicerende API.',
      resource: 'Resource',
      resourceTooltip: 'De resourcenaam waar de notificatie betrekking op heeft (max 100 tekens).',
      resourceUrl: 'Resource URL',
      resourceUrlTooltip: 'URL-referentie naar de resource in de publicerende API.',
      actie: 'Actie',
      actieTooltip: 'De actie die door de publicerende API is uitgevoerd (max 100 tekens).',
      aanmaakdatum: 'Aanmaakdatum',
      aanmaakdatumTooltip:
        'Tijdstip waarop de actie heeft plaatsgevonden (ISO 8601 formaat, bijv. 2024-01-15T10:30:00). Indien leeg wordt het huidige tijdstip gebruikt.',
      kenmerken: 'Kenmerken',
      kenmerkenTooltip: 'Sleutel-waardeparen voor het filteren van notificaties.',
      kenmerkenKey: 'Kenmerk',
      kenmerkenValue: 'Waarde',
      kenmerkenAddRow: 'Kenmerk toevoegen',
      kenmerkenDeleteRow: 'Kenmerk verwijderen',
      'receive-notificatie': 'Ontvang een notificatie',
      receiveNotificatieDescription:
        'Configureer de filtercriteria voor binnenkomende notificaties. Alle velden zijn optioneel — lege velden matchen alle waarden.',
      receiveKanaalTooltip: 'Filter op kanaal. Indien leeg worden alle kanalen gematcht.',
      receiveActieTooltip: 'Filter op actie. Indien leeg worden alle acties gematcht.',
      receiveKenmerkenTooltip:
        'Filter op kenmerken. Alleen notificaties die alle opgegeven kenmerken bevatten worden gematcht.',
    },
    en: {
      title: 'Notificaties API (manual abonnement)',
      url: 'Notificaties API URL',
      urlTooltip: 'A URL to the REST API of Notificaties',
      callbackUrl: 'Callback URL',
      callbackUrlTooltip:
        'The GZAC API-endpoint where notifications should be sent: .../api/v1/notificatiesapi-manual/callback',
      description: 'Notificaties API with an abonnement that GZAC registers itself or that an administrator creates.',
      configurationTitle: 'Configuration name',
      configurationTitleTooltip:
        'The name of the current plugin configuration. Under this name, the configuration can be found in the rest of the application.',
      authenticationPluginConfiguration: 'Authentication plugin configuration',
      authHeader: 'Authentication header',
      authHeaderTooltip:
        'Secret value in Authentication header for the callback URL. When omitting this field, the key of the existing abonnement stays in use, or a random secret is generated. A change is sent to the abonnement when saving.',
      abonnementRegistration: 'Abonnement registration',
      abonnementRegistrationTooltip:
        'Automatic: GZAC registers the abonnement in Open Notificaties. Manual: an administrator creates the abonnement in Open Notificaties, for example because traffic goes through a gateway that checks certificates.',
      abonnementRegistrationAutomatic: 'Automatic',
      abonnementRegistrationManual: 'Manual',
      sharedSecret: 'Shared secret',
      sharedSecretTooltip:
        'Enter the same secret in the Auth field of the abonnement in Open Notificaties. GZAC only accepts notifications that carry a secret of this plugin. Use a plain value that does not start with "Bearer".',
      manualAbonnementDescription:
        'The abonnement in Open Notificaties must contain this callback URL and these channels with their filters. The channels of links to the standard Notificaties API plugin (such as Verzoek and Portaaltaak) with the same Open Notificaties URL are included.',
      manualAbonnementKanaal: 'Channel',
      manualAbonnementNoKanalen: 'No channels are needed for this configuration yet.',
      manualAbonnementSaveFirst:
        'Save the configuration with manual registration to see which channels and filters the abonnement must contain.',
      'publish-notificatie': 'Publish a notification',
      kanaal: 'Channel',
      kanaalTooltip: 'The channel on which the notification is published (max 50 characters).',
      hoofdObject: 'Main object URL',
      hoofdObjectTooltip: 'URL reference to the main object of the publishing API.',
      resource: 'Resource',
      resourceTooltip: 'The resource name the notification concerns (max 100 characters).',
      resourceUrl: 'Resource URL',
      resourceUrlTooltip: 'URL reference to the resource in the publishing API.',
      actie: 'Action',
      actieTooltip: 'The action performed by the publishing API (max 100 characters).',
      aanmaakdatum: 'Creation date',
      aanmaakdatumTooltip:
        'Timestamp when the action occurred (ISO 8601 format, e.g. 2026-01-15T10:30:00). If empty, the current time is used.',
      kenmerken: 'Attributes',
      kenmerkenTooltip: 'Key-value pairs for notification filtering.',
      kenmerkenKey: 'Attribute',
      kenmerkenValue: 'Value',
      kenmerkenAddRow: 'Add attribute',
      kenmerkenDeleteRow: 'Delete attribute',
      'receive-notificatie': 'Receive a notification',
      receiveNotificatieDescription:
        'Configure the filter criteria for incoming notifications. All fields are optional — empty fields match all values.',
      receiveKanaalTooltip: 'Filter by channel. If empty, all channels are matched.',
      receiveActieTooltip: 'Filter by action. If empty, all actions are matched.',
      receiveKenmerkenTooltip:
        'Filter by attributes. Only notifications containing all specified attributes are matched.',
    },
    de: {
      title: 'Notificaties API (manuelles Abonnement)',
      url: 'Notificaties API URL',
      urlTooltip: 'Die URL zur REST API von Notificaties',
      callbackUrl: 'Callback URL',
      callbackUrlTooltip: 'Der GZAC API-Endpunkt, an den Benachrichtigungen gesendet werden: .../api/v1/notificatiesapi-manual/callback',
      description: 'Notificaties API mit einem Abonnement, das GZAC selbst registriert oder das ein Administrator anlegt.',
      configurationTitle: 'Konfigurationsname',
      configurationTitleTooltip: 'Der Name der aktuellen Plugin-Konfiguration. Unter diesem Namen ist die Konfiguration im Rest der Anwendung zu finden.',
      authenticationPluginConfiguration: 'Konfiguration des Authentifizierungs-Plugins',
      authHeader: 'Authentifizierungs-Header',
      authHeaderTooltip: 'Geheimer Wert im Authentifizierungs-Header für die Callback URL. Ohne Angabe bleibt der Schlüssel des bestehenden Abonnements in Gebrauch oder es wird ein zufälliger Wert erzeugt. Eine Änderung wird beim Speichern an das Abonnement gesendet.',
      abonnementRegistration: 'Registrierung des Abonnements',
      abonnementRegistrationTooltip: 'Automatisch: GZAC registriert das Abonnement in Open Notificaties. Manuell: ein Administrator legt das Abonnement in Open Notificaties an, zum Beispiel weil der Verkehr über ein Gateway mit Zertifikatsprüfung läuft.',
      abonnementRegistrationAutomatic: 'Automatisch',
      abonnementRegistrationManual: 'Manuell',
      sharedSecret: 'Gemeinsames Geheimnis',
      sharedSecretTooltip: 'Geben Sie dasselbe Geheimnis im Feld Auth des Abonnements in Open Notificaties ein. GZAC akzeptiert nur Benachrichtigungen mit einem Geheimnis dieses Plugins. Verwenden Sie einen einfachen Wert, der nicht mit "Bearer" beginnt.',
      manualAbonnementDescription: 'Das Abonnement in Open Notificaties muss diese Callback URL und diese Kanäle mit ihren Filtern enthalten. Die Kanäle von Verknüpfungen mit dem Standard-Plugin Notificaties API (wie Verzoek und Portaaltaak) mit derselben Open Notificaties URL sind enthalten.',
      manualAbonnementKanaal: 'Kanal',
      manualAbonnementNoKanalen: 'Für diese Konfiguration werden noch keine Kanäle benötigt.',
      manualAbonnementSaveFirst: 'Speichern Sie die Konfiguration mit manueller Registrierung, um zu sehen, welche Kanäle und Filter das Abonnement enthalten muss.',
      'publish-notificatie': 'Eine Benachrichtigung veröffentlichen',
      kanaal: 'Kanal',
      kanaalTooltip: 'Der Kanal, auf dem die Benachrichtigung veröffentlicht wird (max. 50 Zeichen).',
      hoofdObject: 'URL des Hauptobjekts',
      hoofdObjectTooltip: 'URL-Referenz auf das Hauptobjekt der veröffentlichenden API.',
      resource: 'Ressource',
      resourceTooltip: 'Der Name der Ressource, auf die sich die Benachrichtigung bezieht (max. 100 Zeichen).',
      resourceUrl: 'Ressourcen-URL',
      resourceUrlTooltip: 'URL-Referenz auf die Ressource in der veröffentlichenden API.',
      actie: 'Aktion',
      actieTooltip: 'Die Aktion, die die veröffentlichende API ausgeführt hat (max. 100 Zeichen).',
      aanmaakdatum: 'Erstellungsdatum',
      aanmaakdatumTooltip: 'Zeitpunkt der Aktion (ISO-8601-Format, z. B. 2026-01-15T10:30:00). Ohne Angabe wird der aktuelle Zeitpunkt verwendet.',
      kenmerken: 'Merkmale',
      kenmerkenTooltip: 'Schlüssel-Wert-Paare zum Filtern von Benachrichtigungen.',
      kenmerkenKey: 'Merkmal',
      kenmerkenValue: 'Wert',
      kenmerkenAddRow: 'Merkmal hinzufügen',
      kenmerkenDeleteRow: 'Merkmal löschen',
      'receive-notificatie': 'Eine Benachrichtigung empfangen',
      receiveNotificatieDescription: 'Konfigurieren Sie die Filterkriterien für eingehende Benachrichtigungen. Alle Felder sind optional; leere Felder passen auf alle Werte.',
      receiveKanaalTooltip: 'Nach Kanal filtern. Ohne Angabe passen alle Kanäle.',
      receiveActieTooltip: 'Nach Aktion filtern. Ohne Angabe passen alle Aktionen.',
      receiveKenmerkenTooltip: 'Nach Merkmalen filtern. Nur Benachrichtigungen mit allen angegebenen Merkmalen passen.',
    },
  },
};

export {notificatiesApiManualPluginSpecification};
