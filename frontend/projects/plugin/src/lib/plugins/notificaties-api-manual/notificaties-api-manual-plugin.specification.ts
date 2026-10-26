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

import {PluginSpecification} from '@valtimo/plugin';
import {NotificatiesApiManualPluginConfigurationComponent} from './components/notificaties-api-manual-plugin-configuration/notificaties-api-manual-plugin-configuration.component';
import {NOTIFICATIES_API_MANUAL_PLUGIN_LOGO_BASE64} from './assets/notificaties-api-manual-plugin-logo';

const notificatiesApiManualPluginSpecification: PluginSpecification = {
  pluginId: 'notificatiesapi-manual',
  pluginConfigurationComponent: NotificatiesApiManualPluginConfigurationComponent,
  pluginLogoBase64: NOTIFICATIES_API_MANUAL_PLUGIN_LOGO_BASE64,
  pluginTranslations: {
    nl: {
      title: 'Notificaties API (handmatig abonnement)',
      url: 'Notificaties API URL',
      urlTooltip: 'Een URL naar de REST API van Notificaties',
      callbackUrl: 'Callback URL',
      callbackUrlTooltip:
        'Het GZAC API-endpoint waar notificaties naartoe moeten worden gestuurd: .../api/v1/notificatiesapi-manual/callback bij automatische registratie, .../api/v1/notificatiesapi-manual/callback/manual bij handmatige registratie.',
      description: 'Een API om een notificatierouteringscomponent te benaderen.',
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
        'Vul hetzelfde geheim in bij het veld Auth van het abonnement in Open Notificaties. GZAC accepteert alleen notificaties met dit geheim. Gebruik een gewone waarde die niet met "Bearer" begint.',
      manualAbonnementDescription:
        'Het abonnement in Open Notificaties moet deze callback URL en dit kanaal zonder filters bevatten.',
      manualAbonnementKanaal: 'Kanaal',
      manualAbonnementNoKanalen: 'Er zijn nog geen kanalen nodig voor deze configuratie.',
      manualAbonnementSaveFirst:
        'Sla de configuratie op met handmatige registratie om te zien welke kanalen en filters het abonnement moet bevatten.',
    },
    en: {
      title: 'Notificaties API (manual abonnement)',
      url: 'Notificaties API URL',
      urlTooltip: 'A URL to the REST API of Notificaties',
      callbackUrl: 'Callback URL',
      callbackUrlTooltip:
        'The GZAC API-endpoint where notifications should be sent: .../api/v1/notificatiesapi-manual/callback for automatic registration, .../api/v1/notificatiesapi-manual/callback/manual for manual registration.',
      description: 'An API to access a notification routing component.',
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
        'Enter the same secret in the Auth field of the abonnement in Open Notificaties. GZAC only accepts notifications that carry this secret. Use a plain value that does not start with "Bearer".',
      manualAbonnementDescription:
        'The abonnement in Open Notificaties must contain this callback URL and this channel without filters.',
      manualAbonnementKanaal: 'Channel',
      manualAbonnementNoKanalen: 'No channels are needed for this configuration yet.',
      manualAbonnementSaveFirst:
        'Save the configuration with manual registration to see which channels and filters the abonnement must contain.',
    },
    de: {
      title: 'Notificaties API (manuelles Abonnement)',
      url: 'Notificaties API URL',
      urlTooltip: 'Die URL zur REST API von Notificaties',
      callbackUrl: 'Callback URL',
      callbackUrlTooltip: 'Der GZAC API-Endpunkt, an den Benachrichtigungen gesendet werden: .../api/v1/notificatiesapi-manual/callback bei automatischer Registrierung, .../api/v1/notificatiesapi-manual/callback/manual bei manueller Registrierung.',
      description: 'Eine API für den Zugriff auf eine Komponente zur Weiterleitung von Benachrichtigungen.',
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
      sharedSecretTooltip: 'Geben Sie dasselbe Geheimnis im Feld Auth des Abonnements in Open Notificaties ein. GZAC akzeptiert nur Benachrichtigungen mit diesem Geheimnis. Verwenden Sie einen einfachen Wert, der nicht mit "Bearer" beginnt.',
      manualAbonnementDescription: 'Das Abonnement in Open Notificaties muss diese Callback URL und diesen Kanal ohne Filter enthalten.',
      manualAbonnementKanaal: 'Kanal',
      manualAbonnementNoKanalen: 'Für diese Konfiguration werden noch keine Kanäle benötigt.',
      manualAbonnementSaveFirst: 'Speichern Sie die Konfiguration mit manueller Registrierung, um zu sehen, welche Kanäle und Filter das Abonnement enthalten muss.',
    },
  },
};

export {notificatiesApiManualPluginSpecification};
