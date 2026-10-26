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
import {NotificatiesApiManualPluginPublishNotificatieConfig} from '../../models';

@Component({
  standalone: false,
  selector: 'valtimo-notificaties-api-manual-plugin-publish-notificatie',
  templateUrl: './notificaties-api-manual-plugin-publish-notificatie.component.html',
})
export class NotificatiesApiManualPluginPublishNotificatieComponent
  implements FunctionConfigurationComponent, OnInit, OnDestroy
{
  @Input() save$: Observable<void>;
  @Input() disabled$: Observable<boolean>;
  @Input() pluginId: string;
  @Input() prefillConfiguration$: Observable<NotificatiesApiManualPluginPublishNotificatieConfig>;
  @Output() valid: EventEmitter<boolean> = new EventEmitter<boolean>();
  @Output() configuration: EventEmitter<NotificatiesApiManualPluginPublishNotificatieConfig> =
    new EventEmitter<NotificatiesApiManualPluginPublishNotificatieConfig>();

  public kenmerkenDefaultValues: MultiInputValues = [];

  private saveSubscription!: Subscription;
  private prefillSubscription!: Subscription;

  private readonly formValue$ = new BehaviorSubject<NotificatiesApiManualPluginPublishNotificatieConfig | null>(null);
  private readonly valid$ = new BehaviorSubject<boolean>(false);
  private kenmerken: {[key: string]: string} = {};

  public ngOnInit(): void {
    this.openSaveSubscription();
    this.openPrefillSubscription();
  }

  public ngOnDestroy() {
    this.saveSubscription?.unsubscribe();
    this.prefillSubscription?.unsubscribe();
  }

  public formValueChange(formValue: NotificatiesApiManualPluginPublishNotificatieConfig): void {
    this.formValue$.next({...formValue, kenmerken: this.kenmerken});
    this.handleValid(formValue);
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
    const currentFormValue = this.formValue$.value;
    if (currentFormValue) {
      this.formValue$.next({...currentFormValue, kenmerken: this.kenmerken});
    }
  }

  private handleValid(formValue: NotificatiesApiManualPluginPublishNotificatieConfig): void {
    const valid =
      !!formValue.kanaal &&
      !!formValue.hoofdObject &&
      !!formValue.resource &&
      !!formValue.resourceUrl &&
      !!formValue.actie;

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

  private openPrefillSubscription(): void {
    if (this.prefillConfiguration$) {
      this.prefillSubscription = this.prefillConfiguration$.pipe(take(1)).subscribe(config => {
        if (!config) return;

        this.kenmerken = config.kenmerken ?? {};
        this.kenmerkenDefaultValues = Object.entries(this.kenmerken).map(([key, value]) => ({
          key,
          value,
        }));

        this.formValue$.next({...config, kenmerken: this.kenmerken});
        this.handleValid(config);
      });
    }
  }
}
