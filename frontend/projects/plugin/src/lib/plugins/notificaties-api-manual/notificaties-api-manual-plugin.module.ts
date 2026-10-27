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

import {NgModule} from '@angular/core';
import {NotificatiesApiManualPluginConfigurationComponent} from './components/notificaties-api-manual-plugin-configuration/notificaties-api-manual-plugin-configuration.component';
import {NotificatiesApiManualPluginManualAbonnementComponent} from './components/manual-abonnement/notificaties-api-manual-plugin-manual-abonnement.component';
import {PluginTranslatePipeModule} from '@valtimo/plugin';
import {CommonModule} from '@angular/common';
import {
  FormModule,
  InputModule,
  ParagraphModule,
  SelectModule,
} from '@valtimo/components';

@NgModule({
  declarations: [NotificatiesApiManualPluginConfigurationComponent],
  imports: [
    CommonModule,
    NotificatiesApiManualPluginManualAbonnementComponent,
    PluginTranslatePipeModule,
    FormModule,
    InputModule,
    SelectModule,
    ParagraphModule,
    ],
  exports: [NotificatiesApiManualPluginConfigurationComponent],
})
export class NotificatiesApiManualPluginModule {}
