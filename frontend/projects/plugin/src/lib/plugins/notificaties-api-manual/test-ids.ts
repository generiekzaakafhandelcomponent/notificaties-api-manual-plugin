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

export const NOTIFICATIES_API_MANUAL_PLUGIN_CONFIGURATION_TEST_IDS = {
  order: 'notificatiesApiManualPluginOrder',
  configurationTitle: 'notificatiesApiManualPluginConfigurationTitle',
  url: 'notificatiesApiManualPluginUrl',
} as const;

export const NOTIFICATIES_API_MANUAL_PLUGIN_MANUAL_ABONNEMENT_TEST_IDS = {
  panel: 'notificatiesApiManualPluginManualAbonnement',
  standardPlugin: 'notificatiesApiManualPluginStandardPlugin',
  standardPluginMissing: 'notificatiesApiManualPluginStandardPluginMissing',
  standardPluginMultiple: 'notificatiesApiManualPluginStandardPluginMultiple',
  standardPluginAuthHeaderEmpty: 'notificatiesApiManualPluginStandardPluginAuthHeaderEmpty',
  callbackUrl: 'notificatiesApiManualPluginManualAbonnementCallbackUrl',
  callbackUrlHint: 'notificatiesApiManualPluginManualAbonnementCallbackUrlHint',
  kanaal: 'notificatiesApiManualPluginManualAbonnementKanaal',
} as const;
