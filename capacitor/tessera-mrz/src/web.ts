import { WebPlugin } from '@capacitor/core';

import {
  TesseraMrzError,
  type PluginInfo,
  type ScanResult,
  type SupportResult,
  type TesseraMrzPlugin,
} from './definitions';

export class TesseraMrzWeb extends WebPlugin implements TesseraMrzPlugin {
  async scan(): Promise<ScanResult> {
    throw new TesseraMrzError('UNSUPPORTED_PLATFORM', 'MRZ scanning requires the native Android or iOS plugin.');
  }

  async cancelScan(): Promise<void> {
    return Promise.resolve();
  }

  async isSupported(): Promise<SupportResult> {
    return { supported: false, reason: 'native-platform-required' };
  }

  async getPluginInfo(): Promise<PluginInfo> {
    return {
      name: 'capacitor-tessera-mrz',
      version: '0.1.0',
      tesseraVersion: '0.5.0',
      capacitorMajor: 8,
      platform: 'web',
    };
  }
}
