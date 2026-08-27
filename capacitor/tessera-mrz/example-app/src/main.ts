import { TesseraMrz } from 'capacitor-tessera-mrz';

const supportStatus = requireElement('support-status');
const pluginInfo = requireElement('plugin-info');
const resultOutput = requireElement('result');

document.querySelector('#scan')?.addEventListener('click', async () => {
  resultOutput.textContent = 'Scanner active…';
  try {
    const result = await TesseraMrz.scan({
      documentTypes: ['passport'],
      allowPartialResults: false,
    });
    show(result);
  } catch (error) {
    showError(error);
  }
});

document.querySelector('#cancel')?.addEventListener('click', async () => {
  try {
    await TesseraMrz.cancelScan();
  } catch (error) {
    showError(error);
  }
});

document.querySelector('#clear')?.addEventListener('click', () => {
  resultOutput.textContent = 'No scan yet.';
});

void loadSupport();

async function loadSupport(): Promise<void> {
  try {
    const [support, info] = await Promise.all([TesseraMrz.isSupported(), TesseraMrz.getPluginInfo()]);
    supportStatus.textContent = support.supported ? 'Supported' : `Unavailable: ${support.reason}`;
    supportStatus.classList.toggle('supported', support.supported);
    pluginInfo.textContent = JSON.stringify(info, null, 2);
  } catch (error) {
    supportStatus.textContent = 'Plugin error';
    showError(error);
  }
}

function show(value: unknown): void {
  resultOutput.textContent = JSON.stringify(value, null, 2);
}

function showError(error: unknown): void {
  if (error instanceof Error) {
    const code = 'code' in error ? String(error.code) : 'UNKNOWN';
    show({ code, message: error.message });
  } else {
    show({ code: 'UNKNOWN', message: 'The plugin returned an unknown error.' });
  }
}

function requireElement(id: string): HTMLElement {
  const element = document.getElementById(id);
  if (!element) throw new Error(`Missing example-app element: ${id}`);
  return element;
}
