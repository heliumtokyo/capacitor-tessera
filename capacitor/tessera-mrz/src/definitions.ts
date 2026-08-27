/** Document families that a scan may return. */
export type MrzDocumentType = 'passport' | 'id-card' | 'visa';

/** Camera lens requested for a scan. Native support is reported by rejection when unavailable. */
export type MrzCamera = 'back' | 'front';

/** Options for one native scanner session. */
export interface ScanOptions {
  /** Restrict results to these document families. All supported families are accepted by default. */
  documentTypes?: MrzDocumentType[];

  /** Return Tessera partial successes instead of continuing the scan. Defaults to false. */
  allowPartialResults?: boolean;

  /** End the scanner session after this many milliseconds. Omit for no timeout. */
  timeoutMs?: number;

  /** Camera lens to use. Defaults to the back camera. */
  camera?: MrzCamera;

  /** Start the scanner with the torch enabled. Defaults to false. */
  torchEnabled?: boolean;
}

/** The five MRZ formats defined by ICAO Doc 9303. */
export type MrzFormat = 'TD1' | 'TD2' | 'TD3' | 'MRVA' | 'MRVB';

/** Normalized document data returned across the Capacitor bridge. */
export interface MrzDocument {
  format: MrzFormat;
  documentCode: string;
  issuingState: string;
  documentNumber: string;
  primaryIdentifier: string;
  secondaryIdentifiers: string[];
  nationality: string;
  /** Tessera's computed full date in YYYY-MM-DD form, or null when no century can be resolved. */
  dateOfBirth: string | null;
  /** The raw MRZ sex character, or null for the filler character. */
  sex: string | null;
  /** Tessera's computed full date in YYYY-MM-DD form, or null when no century can be resolved. */
  dateOfExpiry: string | null;
  optionalData: string | null;
}

/** A field named by a native Tessera validation observation. */
export type MrzField =
  | 'document-type'
  | 'issuing-state'
  | 'name'
  | 'document-number'
  | 'nationality'
  | 'date-of-birth'
  | 'date-of-expiry'
  | 'optional-data'
  | 'composite'
  | null;

/** Stable, non-PII diagnostic for one validation failure. */
export interface ValidationFailure {
  /** Stable Tessera error type name, without the native error message or observed value. */
  code: string;
  field: MrzField;
}

/** Per-check observations. Null means that the document format has no such check digit. */
export interface MrzValidation {
  structurallyValid: boolean;
  documentNumberCheckDigit: boolean | null;
  dateOfBirthCheckDigit: boolean | null;
  dateOfExpiryCheckDigit: boolean | null;
  optionalDataCheckDigit: boolean | null;
  compositeCheckDigit: boolean | null;
}

export interface ScanSuccess {
  status: 'success';
  document: MrzDocument;
  validation: MrzValidation;
  raw: { lines: string[] };
}

export interface ScanPartial {
  status: 'partial';
  document: Partial<MrzDocument>;
  validation: MrzValidation;
  validationFailures: ValidationFailure[];
  raw: { lines: string[] };
}

export interface ScanCancelled {
  status: 'cancelled';
}

/** A scanner session resolves with data or cancellation. Operational failures reject the promise. */
export type ScanResult = ScanSuccess | ScanPartial | ScanCancelled;

export type SupportReason = 'native-platform-required' | 'camera-unavailable' | 'unsupported-os-version';

export interface SupportResult {
  supported: boolean;
  reason?: SupportReason;
}

export interface PluginInfo {
  name: 'capacitor-tessera-mrz';
  version: string;
  tesseraVersion: string;
  capacitorMajor: number;
  platform: 'android' | 'ios' | 'web';
}

/** Stable rejection codes. Error messages never contain MRZ or document data. */
export type TesseraMrzErrorCode =
  | 'CAMERA_PERMISSION_DENIED'
  | 'CAMERA_UNAVAILABLE'
  | 'SCANNER_ALREADY_ACTIVE'
  | 'SCAN_TIMEOUT'
  | 'SCANNER_INITIALIZATION_FAILED'
  | 'OCR_FAILED'
  | 'MRZ_PARSE_FAILED'
  | 'UNSUPPORTED_DEVICE'
  | 'UNSUPPORTED_OPTION'
  | 'UNSUPPORTED_PLATFORM'
  | 'INTERNAL_ERROR';

/** Web-side typed error. Native rejections expose the same code through Capacitor. */
export class TesseraMrzError extends Error {
  constructor(
    public readonly code: TesseraMrzErrorCode,
    message: string,
  ) {
    super(message);
    this.name = 'TesseraMrzError';
  }
}

export interface TesseraMrzPlugin {
  /** Open the native MRZ scanner. */
  scan(options?: ScanOptions): Promise<ScanResult>;

  /** Dismiss an active scanner. This is idempotent. */
  cancelScan(): Promise<void>;

  /** Report whether native camera scanning is available on this platform. */
  isSupported(): Promise<SupportResult>;

  /** Return adapter and native SDK compatibility information. */
  getPluginInfo(): Promise<PluginInfo>;
}
