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

import {notificatiesApiPluginSpecification, PluginSpecification} from '@valtimo/plugin';
import {NotificatiesApiManualPluginConfigurationComponent} from './components/notificaties-api-manual-plugin-configuration/notificaties-api-manual-plugin-configuration.component';

const notificatiesApiManualPluginSpecification: PluginSpecification = {
  pluginId: 'notificatiesapi-manual',
  pluginConfigurationComponent: NotificatiesApiManualPluginConfigurationComponent,
  pluginLogoBase64: notificatiesApiPluginSpecification.pluginLogoBase64,
  pluginTranslations: {
    nl: {
      title: 'Notificaties API, handmatig abonnement',
      description:
        'Open Notificaties waarin een beheerder het abonnement van de Notificaties API-plugin aanmaakt, bijvoorbeeld achter een gateway met certificaten.',
      configurationTitle: 'Configuratienaam',
      configurationTitleTooltip:
        'De naam van de huidige plugin-configuratie. Onder deze naam kan de configuratie in de rest van de applicatie teruggevonden worden.',
      url: 'Notificaties API URL',
      urlTooltip:
        'De URL van de REST API van Open Notificaties, precies zoals in de configuratie van de standaard Notificaties API-plugin.',
      orderTitle: 'Stap 1 van 2. Configureer hierna de standaard Notificaties API-plugin',
      orderMessage:
        'Deze configuratie vertelt GZAC alleen dat een beheerder het abonnement in deze Open Notificaties aanmaakt. Sla haar eerst op. Maak daarna een configuratie van de standaard Notificaties API-plugin met precies dezelfde Notificaties API URL, en vul daar de Authenticatie header in: dat is het geheim van het abonnement. Koppel Verzoek, Portaaltaak en proceskoppelingen aan die configuratie. In de andere volgorde probeert GZAC het abonnement zelf te registreren, wat achter een gateway met certificaten mislukt.',
      standardPluginTitle: 'Stap 2. Standaard Notificaties API-plugin',
      standardPluginMissing:
        'Nog geen configuratie van de standaard Notificaties API-plugin gebruikt deze URL. Maak die nu aan, met dezelfde Notificaties API URL en een Authenticatie header.',
      standardPluginMultiple:
        'Meer dan één configuratie van de standaard Notificaties API-plugin gebruikt deze URL. GZAC accepteert de Authenticatie header van maar één ervan: gebruik één configuratie.',
      standardPluginAuthHeaderFilledIn: 'Authenticatie header ingevuld',
      standardPluginAuthHeaderEmpty:
        'De Authenticatie header is leeg. Vul die in: anders kiest GZAC zelf een geheim dat u niet kunt zien, en weigert het de notificaties.',
      abonnementTitle: 'Abonnement in Open Notificaties',
      abonnementDescription:
        'Maak in Open Notificaties een abonnement aan met deze gegevens. GZAC ontvangt de notificaties op het adres van de standaard Notificaties API-plugin.',
      abonnementCallbackUrl: 'Callback URL',
      abonnementCallbackUrlHint:
        'De callback URL van de standaard configuratie: het adres waarlangs Open Notificaties GZAC bereikt, gevolgd door /api/v1/notificatiesapi/callback',
      abonnementAuth: 'Auth',
      abonnementAuthValue: 'De Authenticatie header van de standaard configuratie',
      abonnementKanaal: 'Kanaal',
      abonnementNoKanalen:
        'Er zijn nog geen kanalen nodig: er zijn geen proceskoppelingen, Verzoek- of Portaaltaak-configuraties die deze URL gebruiken.',
      saveFirst:
        'Sla deze configuratie op om te zien welke configuratie van de standaard plugin en welk abonnement nog nodig zijn.',
    },
    en: {
      title: 'Notificaties API, manual abonnement',
      description:
        'Open Notificaties in which an administrator creates the abonnement of the Notificaties API plugin, for example behind a gateway that checks certificates.',
      configurationTitle: 'Configuration name',
      configurationTitleTooltip:
        'The name of the current plugin configuration. Under this name, the configuration can be found in the rest of the application.',
      url: 'Notificaties API URL',
      urlTooltip:
        'The URL of the REST API of Open Notificaties, exactly as in the configuration of the standard Notificaties API plugin.',
      orderTitle: 'Step 1 of 2. Configure the standard Notificaties API plugin after this',
      orderMessage:
        'This configuration only tells GZAC that an administrator creates the abonnement in this Open Notificaties. Save it first. Then create a configuration of the standard Notificaties API plugin with exactly the same Notificaties API URL, and fill in its Authentication header: that is the secret of the abonnement. Link Verzoek, Portaaltaak and process links to that configuration. In the other order, GZAC tries to register the abonnement itself, which fails behind a gateway that checks certificates.',
      standardPluginTitle: 'Step 2. Standard Notificaties API plugin',
      standardPluginMissing:
        'No configuration of the standard Notificaties API plugin uses this URL yet. Create one now, with the same Notificaties API URL and an Authentication header.',
      standardPluginMultiple:
        'More than one configuration of the standard Notificaties API plugin uses this URL. GZAC accepts the Authentication header of only one of them: use one configuration.',
      standardPluginAuthHeaderFilledIn: 'Authentication header filled in',
      standardPluginAuthHeaderEmpty:
        'The Authentication header is empty. Fill it in: otherwise GZAC picks a secret you cannot see, and refuses the notifications.',
      abonnementTitle: 'Abonnement in Open Notificaties',
      abonnementDescription:
        'Create an abonnement in Open Notificaties with these details. GZAC receives the notifications on the address of the standard Notificaties API plugin.',
      abonnementCallbackUrl: 'Callback URL',
      abonnementCallbackUrlHint:
        'The callback URL of the standard configuration: the address through which Open Notificaties reaches GZAC, followed by /api/v1/notificatiesapi/callback',
      abonnementAuth: 'Auth',
      abonnementAuthValue: 'The Authentication header of the standard configuration',
      abonnementKanaal: 'Channel',
      abonnementNoKanalen:
        'No channels are needed yet: no process links, Verzoek or Portaaltaak configurations use this URL.',
      saveFirst:
        'Save this configuration to see which configuration of the standard plugin and which abonnement are still needed.',
    },
    de: {
      title: 'Notificaties API, manuelles Abonnement',
      description:
        'Open Notificaties, in dem ein Administrator das Abonnement des Notificaties API-Plugins anlegt, zum Beispiel hinter einem Gateway mit Zertifikatsprüfung.',
      configurationTitle: 'Konfigurationsname',
      configurationTitleTooltip:
        'Der Name der aktuellen Plugin-Konfiguration. Unter diesem Namen ist die Konfiguration im Rest der Anwendung zu finden.',
      url: 'Notificaties API URL',
      urlTooltip:
        'Die URL der REST API von Open Notificaties, genau wie in der Konfiguration des Standard-Plugins Notificaties API.',
      orderTitle: 'Schritt 1 von 2. Konfigurieren Sie danach das Standard-Plugin Notificaties API',
      orderMessage:
        'Diese Konfiguration teilt GZAC nur mit, dass ein Administrator das Abonnement in diesem Open Notificaties anlegt. Speichern Sie sie zuerst. Legen Sie danach eine Konfiguration des Standard-Plugins Notificaties API mit genau derselben Notificaties API URL an und füllen Sie dort den Authentifizierungs-Header aus: Das ist das Geheimnis des Abonnements. Verknüpfen Sie Verzoek, Portaaltaak und Prozessverknüpfungen mit dieser Konfiguration. In der anderen Reihenfolge versucht GZAC, das Abonnement selbst zu registrieren, was hinter einem Gateway mit Zertifikatsprüfung fehlschlägt.',
      standardPluginTitle: 'Schritt 2. Standard-Plugin Notificaties API',
      standardPluginMissing:
        'Noch keine Konfiguration des Standard-Plugins Notificaties API verwendet diese URL. Legen Sie jetzt eine an, mit derselben Notificaties API URL und einem Authentifizierungs-Header.',
      standardPluginMultiple:
        'Mehr als eine Konfiguration des Standard-Plugins Notificaties API verwendet diese URL. GZAC akzeptiert den Authentifizierungs-Header nur einer davon: Verwenden Sie eine Konfiguration.',
      standardPluginAuthHeaderFilledIn: 'Authentifizierungs-Header ausgefüllt',
      standardPluginAuthHeaderEmpty:
        'Der Authentifizierungs-Header ist leer. Füllen Sie ihn aus: Sonst wählt GZAC ein Geheimnis, das Sie nicht sehen können, und lehnt die Benachrichtigungen ab.',
      abonnementTitle: 'Abonnement in Open Notificaties',
      abonnementDescription:
        'Legen Sie in Open Notificaties ein Abonnement mit diesen Angaben an. GZAC empfängt die Benachrichtigungen an der Adresse des Standard-Plugins Notificaties API.',
      abonnementCallbackUrl: 'Callback URL',
      abonnementCallbackUrlHint:
        'Die Callback URL der Standard-Konfiguration: die Adresse, über die Open Notificaties GZAC erreicht, gefolgt von /api/v1/notificatiesapi/callback',
      abonnementAuth: 'Auth',
      abonnementAuthValue: 'Der Authentifizierungs-Header der Standard-Konfiguration',
      abonnementKanaal: 'Kanal',
      abonnementNoKanalen:
        'Noch keine Kanäle nötig: Keine Prozessverknüpfungen, Verzoek- oder Portaaltaak-Konfigurationen verwenden diese URL.',
      saveFirst:
        'Speichern Sie diese Konfiguration, um zu sehen, welche Konfiguration des Standard-Plugins und welches Abonnement noch nötig sind.',
    },
  },
};

export {notificatiesApiManualPluginSpecification};
