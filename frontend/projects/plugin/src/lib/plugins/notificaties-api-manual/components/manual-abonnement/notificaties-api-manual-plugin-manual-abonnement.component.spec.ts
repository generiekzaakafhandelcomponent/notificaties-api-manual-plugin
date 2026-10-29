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

import {NO_ERRORS_SCHEMA, Pipe, PipeTransform} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {Observable, of} from 'rxjs';
import {NOTIFICATIES_API_MANUAL_PLUGIN_MANUAL_ABONNEMENT_TEST_IDS} from '../../test-ids';
import {NotificatiesApiManualPluginManualAbonnementComponent} from './notificaties-api-manual-plugin-manual-abonnement.component';

@Pipe({name: 'pluginTranslate', standalone: true})
class PluginTranslateStubPipe implements PipeTransform {
  public transform(translateKey: string): Observable<string> {
    return of(translateKey);
  }
}

describe('NotificatiesApiManualPluginManualAbonnementComponent', () => {
  let fixture: ComponentFixture<NotificatiesApiManualPluginManualAbonnementComponent>;
  const testIds = NOTIFICATIES_API_MANUAL_PLUGIN_MANUAL_ABONNEMENT_TEST_IDS;

  const byTestId = (testId: string): HTMLElement[] =>
    Array.from(fixture.nativeElement.querySelectorAll(`[data-test-id="${testId}"]`));

  beforeEach(async () => {
    await TestBed.configureTestingModule({imports: [NotificatiesApiManualPluginManualAbonnementComponent]})
      .overrideComponent(NotificatiesApiManualPluginManualAbonnementComponent, {
        set: {imports: [CommonModule, PluginTranslateStubPipe], schemas: [NO_ERRORS_SCHEMA]},
      })
      .compileComponents();

    fixture = TestBed.createComponent(NotificatiesApiManualPluginManualAbonnementComponent);
    fixture.componentInstance.pluginId = 'notificatiesapi-manual';
  });

  const coreConfiguration = (overrides: object = {}) => ({
    id: '020e6ca3-1c15-4496-a4cf-67bfaa36332f',
    title: 'Notificaties API via gateway',
    callbackUrl: 'https://gateway.example.com/api/v1/notificatiesapi/callback',
    authHeaderFilledIn: true,
    ...overrides,
  });

  const notification = (testId: string) =>
    byTestId(testId)[0] as HTMLElement & {notificationObj: {type: string; message: string}};

  it('should show the standard configuration, its callback URL and every kanaal with its filters', () => {
    fixture.componentInstance.manualAbonnement = {
      coreConfigurations: [coreConfiguration()],
      kanalen: [
        {naam: 'zaken', filters: {actie: 'create'}},
        {
          naam: 'objecten',
          filters: {objectType: 'https://objecttypen.example.com/1', actie: 'update'},
        },
      ],
    };
    fixture.detectChanges();

    expect(byTestId(testIds.standardPlugin)[0].textContent).toContain('Notificaties API via gateway');
    expect(byTestId(testIds.standardPlugin)[0].textContent).toContain('standardPluginAuthHeaderFilledIn');
    expect(byTestId(testIds.callbackUrl)[0].textContent.trim()).toBe(
      'https://gateway.example.com/api/v1/notificatiesapi/callback'
    );
    expect(fixture.nativeElement.textContent).toContain('abonnementAuthValue');
    const kanalen = byTestId(testIds.kanaal);
    expect(kanalen.length).toBe(2);
    expect(kanalen[0].querySelector('strong').textContent.trim()).toBe('zaken');
    expect(kanalen[0].textContent).toContain('actie = create');
    expect(kanalen[1].textContent).toContain('objectType = https://objecttypen.example.com/1');
    expect(byTestId(testIds.standardPluginMissing).length).toBe(0);
    expect(byTestId(testIds.standardPluginMultiple).length).toBe(0);
    expect(byTestId(testIds.standardPluginAuthHeaderEmpty).length).toBe(0);
  });

  it('should ask for the standard configuration when there is none yet', () => {
    fixture.componentInstance.manualAbonnement = {coreConfigurations: [], kanalen: []};
    fixture.detectChanges();

    expect(notification(testIds.standardPluginMissing).notificationObj.type).toBe('warning');
    expect(notification(testIds.standardPluginMissing).notificationObj.message).toBe(
      'standardPluginMissing'
    );
    expect(byTestId(testIds.callbackUrl).length).toBe(0);
    expect(byTestId(testIds.callbackUrlHint)[0].textContent.trim()).toBe('abonnementCallbackUrlHint');
    expect(fixture.nativeElement.textContent).toContain('abonnementNoKanalen');
  });

  it('should warn when the Auth header of the standard configuration is empty', () => {
    fixture.componentInstance.manualAbonnement = {
      coreConfigurations: [coreConfiguration({authHeaderFilledIn: false})],
      kanalen: [],
    };
    fixture.detectChanges();

    expect(notification(testIds.standardPluginAuthHeaderEmpty).notificationObj.type).toBe('warning');
  });

  it('should warn when more than one standard configuration uses the URL', () => {
    fixture.componentInstance.manualAbonnement = {
      coreConfigurations: [coreConfiguration(), coreConfiguration({id: 'other', title: 'Andere'})],
      kanalen: [],
    };
    fixture.detectChanges();

    expect(notification(testIds.standardPluginMultiple).notificationObj.type).toBe('warning');
    expect(byTestId(testIds.standardPlugin).length).toBe(2);
    expect(byTestId(testIds.callbackUrl).length).toBe(0);
  });

  it('should ask to save the configuration first', () => {
    fixture.componentInstance.manualAbonnement = null;
    fixture.detectChanges();

    expect(byTestId(testIds.standardPlugin).length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('saveFirst');
  });
});
