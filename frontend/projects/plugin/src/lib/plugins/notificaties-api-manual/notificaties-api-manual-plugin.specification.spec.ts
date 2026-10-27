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

import {notificatiesApiManualPluginSpecification} from './notificaties-api-manual-plugin.specification';

describe('notificatiesApiManualPluginSpecification', () => {
  // Valtimo looks up plugin translations per language without a fallback; a missing language breaks plugin screens.
  it('should have the same translation keys in nl, en and de', () => {
    const translations = notificatiesApiManualPluginSpecification.pluginTranslations;
    const keys = (language: string) => Object.keys(translations[language] ?? {}).sort();

    expect(keys('nl').length).toBeGreaterThan(0);
    expect(keys('en')).toEqual(keys('nl'));
    expect(keys('de')).toEqual(keys('nl'));
  });
});
