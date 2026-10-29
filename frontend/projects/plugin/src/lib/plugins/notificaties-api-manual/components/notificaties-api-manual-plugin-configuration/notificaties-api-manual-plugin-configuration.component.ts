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

import {Component, EventEmitter, Input, OnDestroy, OnInit, Output} from '@angular/core';
import {PluginConfigurationComponent} from '@valtimo/plugin';
import {
  BehaviorSubject,
  catchError,
  combineLatest,
  defer,
  map,
  Observable,
  of,
  Subscription,
  switchMap,
  take,
} from 'rxjs';
import {NotificatiesApiManualPluginConfig, NotificatiesApiManualPluginManualAbonnement} from '../../models';
import {NotificatiesApiManualPluginService} from '../../services';
import {NOTIFICATIES_API_MANUAL_PLUGIN_CONFIGURATION_TEST_IDS} from '../../test-ids';

@Component({
  standalone: false,
  selector: 'valtimo-notificaties-api-manual-plugin-configuration',
  templateUrl: './notificaties-api-manual-plugin-configuration.component.html',
  styleUrls: ['./notificaties-api-manual-plugin-configuration.component.scss'],
})
export class NotificatiesApiManualPluginConfigurationComponent
  implements PluginConfigurationComponent, OnInit, OnDestroy
{
  @Input() save$: Observable<void>;
  @Input() disabled$: Observable<boolean>;
  @Input() pluginId: string;
  @Input() prefillConfiguration$: Observable<NotificatiesApiManualPluginConfig>;
  @Output() valid: EventEmitter<boolean> = new EventEmitter<boolean>();
  @Output() configuration: EventEmitter<NotificatiesApiManualPluginConfig> =
    new EventEmitter<NotificatiesApiManualPluginConfig>();
  protected readonly testIds = NOTIFICATIES_API_MANUAL_PLUGIN_CONFIGURATION_TEST_IDS;
  /** What is still needed for a saved configuration; null before it is saved. */
  public readonly manualAbonnement$: Observable<NotificatiesApiManualPluginManualAbonnement | null> =
    defer(() => this.prefillConfiguration$ ?? of(null)).pipe(
      map(prefill => prefill?.configurationId),
      switchMap(configurationId =>
        configurationId
          ? this.notificatiesApiPluginService
              .getManualAbonnement(configurationId)
              .pipe(catchError(() => of(null)))
          : of(null)
      )
    );
  private saveSubscription!: Subscription;
  private readonly formValue$ = new BehaviorSubject<NotificatiesApiManualPluginConfig | null>(null);
  private readonly valid$ = new BehaviorSubject<boolean>(false);

  constructor(private readonly notificatiesApiPluginService: NotificatiesApiManualPluginService) {}

  public ngOnInit(): void {
    this.openSaveSubscription();
  }

  public ngOnDestroy() {
    this.saveSubscription?.unsubscribe();
  }

  public formValueChange(formValue: NotificatiesApiManualPluginConfig): void {
    this.formValue$.next(formValue);
    this.handleValid(formValue);
  }

  private handleValid(formValue: NotificatiesApiManualPluginConfig): void {
    const valid = !!(formValue.configurationTitle && formValue.url);

    this.valid$.next(valid);
    this.valid.emit(valid);
  }

  private openSaveSubscription(): void {
    this.saveSubscription = this.save$
      ?.pipe(switchMap(() => combineLatest([this.formValue$, this.valid$]).pipe(take(1))))
      .subscribe(([formValue, valid]) => {
        if (valid) {
          this.configuration.emit(formValue);
        }
      });
  }
}
