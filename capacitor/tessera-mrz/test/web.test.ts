import { describe, expect, it } from 'vitest';

import type { TesseraMrzError } from '../src/definitions';
import { TesseraMrzWeb } from '../src/web';

describe('TesseraMrzWeb', () => {
  it('reports that native scanning is required', async () => {
    const plugin = new TesseraMrzWeb();

    await expect(plugin.isSupported()).resolves.toEqual({
      supported: false,
      reason: 'native-platform-required',
    });
  });

  it('rejects scan with a typed, stable error', async () => {
    const plugin = new TesseraMrzWeb();

    await expect(plugin.scan()).rejects.toMatchObject({
      name: 'TesseraMrzError',
      code: 'UNSUPPORTED_PLATFORM',
    } satisfies Partial<TesseraMrzError>);
  });

  it('allows idempotent cancellation', async () => {
    const plugin = new TesseraMrzWeb();

    await expect(plugin.cancelScan()).resolves.toBeUndefined();
  });

  it('reports pinned compatibility information', async () => {
    const plugin = new TesseraMrzWeb();

    await expect(plugin.getPluginInfo()).resolves.toEqual({
      name: 'capacitor-tessera-mrz',
      version: '0.1.0',
      tesseraVersion: '0.5.0',
      capacitorMajor: 8,
      platform: 'web',
    });
  });
});
