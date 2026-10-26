/*
 * Copyright 2015-2026 Ritense BV, the Netherlands.
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
import {FunctionConfigurationComponent} from '@valtimo/plugin';
import {BehaviorSubject, combineLatest, Observable, Subscription, switchMap, take} from 'rxjs';
import {MultiInputOutput, MultiInputValues} from '@valtimo/components';
import {NotificatiesApiManualPluginReceiveNotificatieConfig} from '../../models';

@Component({
  standalone: false,
  selector: 'valtimo-notificaties-api-manual-plugin-receive-notificatie',
  templateUrl: './notificaties-api-manual-plugin-receive-notificatie.component.html',
})
export class NotificatiesApiManualPluginReceiveNotificatieComponent
  implements FunctionConfigurationComponent, OnInit, OnDestroy
{
  @Input() save$: Observable<void>;
  @Input() disabled$: Observable<boolean>;
  @Input() pluginId: string;
  @Input() prefillConfiguration$: Observable<NotificatiesApiManualPluginReceiveNotificatieConfig>;
  @Output() valid: EventEmitter<boolean> = new EventEmitter<boolean>();
  @Output() configuration: EventEmitter<NotificatiesApiManualPluginReceiveNotificatieConfig> =
    new EventEmitter<NotificatiesApiManualPluginReceiveNotificatieConfig>();

  public kenmerkenDefaultValues: MultiInputValues = [];

  private saveSubscription!: Subscription;
  private prefillSubscription!: Subscription;

  private readonly formValue$ = new BehaviorSubject<NotificatiesApiManualPluginReceiveNotificatieConfig | null>(null);
  private readonly valid$ = new BehaviorSubject<boolean>(false);
  private kenmerken: {[key: string]: string} = {};

  public ngOnInit(): void {
    this.openSaveSubscription();
    this.openPrefillSubscription();
    this.valid$.next(true);
    this.valid.emit(true);
  }

  public ngOnDestroy() {
    this.saveSubscription?.unsubscribe();
    this.prefillSubscription?.unsubscribe();
  }

  public formValueChange(formValue: NotificatiesApiManualPluginReceiveNotificatieConfig): void {
    this.formValue$.next({...formValue, kenmerken: this.kenmerken});
    this.valid$.next(true);
    this.valid.emit(true);
  }

  public kenmerkenChange(value: MultiInputOutput): void {
    const values = value as MultiInputValues;
    this.kenmerken = values.reduce(
      (acc, curr) => ({
        ...acc,
        ...(!curr.key || !curr.value ? {} : {[curr.key]: curr.value}),
      }),
      {} as {[key: string]: string}
    );
    const currentFormValue = this.formValue$.value ?? {};
    this.formValue$.next({...currentFormValue, kenmerken: this.kenmerken});
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

  private openPrefillSubscription(): void {
    if (this.prefillConfiguration$) {
      this.prefillSubscription = this.prefillConfiguration$
        .pipe(take(1))
        .subscribe(config => {
          if (config) {
            this.formValue$.next({...config});
            if (config.kenmerken) {
              this.kenmerken = config.kenmerken;
              this.kenmerkenDefaultValues = Object.entries(config.kenmerken).map(
                ([key, value]) => ({key, value})
              );
            }
          } else {
            this.formValue$.next({});
          }
        });
    } else {
      this.formValue$.next({});
    }
  }
}
