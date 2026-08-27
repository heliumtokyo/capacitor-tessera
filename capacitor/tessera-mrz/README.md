# capacitor-tessera-mrz

Capacitor 8 adapter for Tessera's native Android and iOS MRZ scanner. The package
keeps CameraX, ML Kit, AVFoundation, Vision, Compose, SwiftUI, and Tessera's native
object graph behind a stable TypeScript boundary.

```ts
import { TesseraMrz } from 'capacitor-tessera-mrz';

const result = await TesseraMrz.scan({
  documentTypes: ['passport'],
  allowPartialResults: false,
});

if (result.status === 'success') {
  console.log(result.document.documentNumber);
}
```

The plugin extracts document data and reports validation observations. It does not
decide that a document is genuine or that its holder owns it.

## Compatibility

| Plugin | Capacitor | Tessera | Android | iOS |
|---|---|---|---|---|
| 0.1.x | 8.x | 0.5.x | API 24+ | 18+ |

The iOS adapter uses Swift Package Manager. Camera access requires the host app to
provide `NSCameraUsageDescription`; Android receives the required camera permission
declaration from the plugin manifest.

## Data boundary

Camera frames stay in the native scanner. The plugin returns normalized document
fields, validation observations, and raw MRZ lines to the caller, then releases its
native call state. It performs no network requests, persistence, analytics, clipboard
access, image capture, or MRZ logging.

<!-- API -->
