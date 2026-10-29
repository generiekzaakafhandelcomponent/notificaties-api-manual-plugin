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
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {Observable, of, Subject} from 'rxjs';
import {NotificatiesApiManualPluginConfig, NotificatiesApiManualPluginManualAbonnement} from '../../models';
import {NotificatiesApiManualPluginService} from '../../services';
import {NOTIFICATIES_API_MANUAL_PLUGIN_CONFIGURATION_TEST_IDS} from '../../test-ids';
import {NotificatiesApiManualPluginConfigurationComponent} from './notificaties-api-manual-plugin-configuration.component';

const MANUAL_ABONNEMENT: NotificatiesApiManualPluginManualAbonnement = {
  coreConfigurations: [
    {
      id: '020e6ca3-1c15-4496-a4cf-67bfaa36332f',
      title: 'Notificaties API via gateway',
      callbackUrl: 'https://gateway.example.com/api/v1/notificatiesapi/callback',
      authHeaderFilledIn: true,
    },
  ],
  kanalen: [{naam: 'zaken', filters: {actie: 'create'}}],
};

const CONFIGURATION = {
  configurationTitle: 'Open Notificaties via gateway',
  url: 'https://opennotificaties.example.com/api/v1/',
} as NotificatiesApiManualPluginConfig;

describe('NotificatiesApiManualPluginConfigurationComponent', () => {
  let component: NotificatiesApiManualPluginConfigurationComponent;
  let save$: Subject<void>;
  let notificatiesApiPluginService: jasmine.SpyObj<NotificatiesApiManualPluginService>;

  beforeEach(() => {
    save$ = new Subject<void>();
    notificatiesApiPluginService = jasmine.createSpyObj<NotificatiesApiManualPluginService>(
      'NotificatiesApiManualPluginService',
      {getManualAbonnement: of(MANUAL_ABONNEMENT)}
    );

    component = new NotificatiesApiManualPluginConfigurationComponent(notificatiesApiPluginService);
    component.save$ = save$;
    component.ngOnInit();
  });

  afterEach(() => {
    component.ngOnDestroy();
  });

  it('should save a configuration with a title and a URL', () => {
    const validEmissions: boolean[] = [];
    const emittedConfigurations: NotificatiesApiManualPluginConfig[] = [];
    component.valid.subscribe(valid => validEmissions.push(valid));
    component.configuration.subscribe(configuration => emittedConfigurations.push(configuration));

    component.formValueChange(CONFIGURATION);
    save$.next();

    expect(validEmissions).toEqual([true]);
    expect(emittedConfigurations).toEqual([CONFIGURATION]);
  });

  it('should not save a configuration without a URL', () => {
    const emittedConfigurations: NotificatiesApiManualPluginConfig[] = [];
    component.configuration.subscribe(configuration => emittedConfigurations.push(configuration));

    component.formValueChange({...CONFIGURATION, url: ''});
    save$.next();

    expect(emittedConfigurations).toEqual([]);
  });

  it('should load what is still needed for a saved configuration', () => {
    component.prefillConfiguration$ = of({
      ...CONFIGURATION,
      configurationId: 'b2f4c1d0-0000-4000-8000-000000000001',
    });
    const emissions: Array<NotificatiesApiManualPluginManualAbonnement | null> = [];
    component.manualAbonnement$.subscribe(abonnement => emissions.push(abonnement));

    expect(notificatiesApiPluginService.getManualAbonnement).toHaveBeenCalledOnceWith(
      'b2f4c1d0-0000-4000-8000-000000000001'
    );
    expect(emissions).toEqual([MANUAL_ABONNEMENT]);
  });

  it('should load nothing for a new configuration', () => {
    const emissions: Array<NotificatiesApiManualPluginManualAbonnement | null> = [];
    component.manualAbonnement$.subscribe(abonnement => emissions.push(abonnement));

    expect(notificatiesApiPluginService.getManualAbonnement).not.toHaveBeenCalled();
    expect(emissions).toEqual([null]);
  });
});

@Pipe({name: 'pluginTranslate', standalone: false})
class PluginTranslateStubPipe implements PipeTransform {
  public transform(translateKey: string): Observable<string> {
    return of(translateKey);
  }
}

describe('NotificatiesApiManualPluginConfigurationComponent template', () => {
  let fixture: ComponentFixture<NotificatiesApiManualPluginConfigurationComponent>;

  const element = (selector: string): any => fixture.nativeElement.querySelector(selector);

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [NotificatiesApiManualPluginConfigurationComponent, PluginTranslateStubPipe],
      providers: [
        {
          provide: NotificatiesApiManualPluginService,
          useValue: {getManualAbonnement: () => of(MANUAL_ABONNEMENT)},
        },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();
  });

  const render = (configurationId?: string): void => {
    fixture = TestBed.createComponent(NotificatiesApiManualPluginConfigurationComponent);
    fixture.componentInstance.pluginId = 'notificatiesapi-manual';
    fixture.componentInstance.disabled$ = of(false);
    fixture.componentInstance.save$ = new Subject<void>();
    fixture.componentInstance.prefillConfiguration$ = of({...CONFIGURATION, configurationId});
    fixture.detectChanges();
  };

  it('should only ask for a title and a URL', () => {
    render();

    expect(
      Array.from(fixture.nativeElement.querySelectorAll('v-input')).map((input: Element) => input.getAttribute('name'))
    ).toEqual(['configurationTitle', 'url']);
    expect(element('v-select')).toBeNull();
  });

  it('should say before the fields that the standard plugin is configured after this one', () => {
    render();

    const order = element(
      `[data-test-id="${NOTIFICATIES_API_MANUAL_PLUGIN_CONFIGURATION_TEST_IDS.order}"]`
    );
    expect(order.notificationObj.type).toBe('info');
    expect(order.notificationObj.title).toBe('orderTitle');
    expect(order.notificationObj.message).toBe('orderMessage');
    expect(order.compareDocumentPosition(element('v-form')) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('should ask to save first for a new configuration', () => {
    render();

    expect(element('valtimo-notificaties-api-manual-plugin-manual-abonnement').manualAbonnement).toBeNull();
  });

  it('should show what is still needed for a saved configuration', () => {
    render('b2f4c1d0-0000-4000-8000-000000000001');

    expect(element('valtimo-notificaties-api-manual-plugin-manual-abonnement').manualAbonnement).toEqual(
      MANUAL_ABONNEMENT
    );
  });
});
