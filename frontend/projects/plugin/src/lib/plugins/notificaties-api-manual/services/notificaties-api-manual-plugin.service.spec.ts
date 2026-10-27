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

import {HttpClient} from '@angular/common/http';
import {of} from 'rxjs';
import {ConfigService} from '@valtimo/config';
import {InterceptorSkip} from '@valtimo/security';
import {NotificatiesApiManualPluginService} from './notificaties-api-manual-plugin.service';

describe('NotificatiesApiManualPluginService', () => {
  it('should keep the expected 404 of a configuration not saved as manual off the error toast', () => {
    const http = jasmine.createSpyObj<HttpClient>('HttpClient', {get: of(null)});
    const service = new NotificatiesApiManualPluginService(http, {
      config: {valtimoApi: {endpointUri: 'http://localhost:8080/api/'}},
    } as ConfigService);

    service.getManualAbonnement('b2f4c1d0-0000-4000-8000-000000000001').subscribe();

    const [url, options] = http.get.calls.mostRecent().args as [string, any];
    expect(url).toBe(
      'http://localhost:8080/api/management/v1/notificatiesapi-manual/configurations/b2f4c1d0-0000-4000-8000-000000000001/manual-abonnement'
    );
    expect(options.headers.get(InterceptorSkip)).toBe('404');
  });
});
