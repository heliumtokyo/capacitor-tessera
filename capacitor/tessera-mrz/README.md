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
declaration from the plugin manifest. Android host applications must compile against
API 37 or newer because Tessera's default Android UI has that compile-time floor; this
does not change the API 24 runtime minimum.

On Android, the adapter requests camera permission through Capacitor, presents
Tessera's Compose scanner in a private activity, prevents concurrent sessions, and
maps Tessera `Success` and `PartialSuccess` values into the TypeScript DTO. Tessera
0.5.0 only exposes the back camera through its default UI, so requesting `front`
rejects with `UNSUPPORTED_OPTION` instead of silently ignoring the option.

On iOS, the adapter requests AVFoundation camera authorization and presents
TesseraUI's scanner from a full-screen `UIHostingController`. The package pins the
published Tessera Swift package at 0.5.0 so Android and iOS share a known native
compatibility baseline. CocoaPods is not advertised because the native Tessera UI is
distributed through Swift Package Manager.

## Example app

The validation app in `example-app` shows support metadata, starts a passport scan,
cancels an active scan, and renders the latest result without storing or uploading it.
Generated Android and iOS projects stay out of git; its preparation scripts recreate
them and apply the plugin's documented native platform floors.

```sh
cd example-app
npm ci
npm run native:android
# or: npm run native:ios
```

Open the generated native project with the platform's supported development driver.

## Data boundary

Camera frames stay in the native scanner. The plugin returns normalized document
fields, validation observations, and raw MRZ lines to the caller, then releases its
native call state. It performs no network requests, persistence, analytics, clipboard
access, image capture, or MRZ logging.

<!-- API -->
