import { registerPlugin } from '@capacitor/core';

import type { TesseraMrzPlugin } from './definitions';

const TesseraMrz = registerPlugin<TesseraMrzPlugin>('TesseraMrz', {
  web: () => import('./web').then((module) => new module.TesseraMrzWeb()),
});

export * from './definitions';
export { TesseraMrz };
