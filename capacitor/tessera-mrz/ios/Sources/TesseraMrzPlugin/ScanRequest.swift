import Capacitor

struct ScanRequest {
    let documentTypes: Set<String>
    let allowPartialResults: Bool
    let timeoutMs: Int?
    let torchEnabled: Bool

    static func parse(_ call: CAPPluginCall) -> ScanRequest? {
        let rawDocumentTypes = call.getArray("documentTypes")
        let documentTypes: Set<String>
        if let rawDocumentTypes {
            guard let values = rawDocumentTypes as? [String], !values.isEmpty else {
                call.reject("documentTypes must be a non-empty array of strings.", "UNSUPPORTED_OPTION")
                return nil
            }
            documentTypes = Set(values)
        } else {
            documentTypes = []
        }

        let supportedDocumentTypes = Set(["passport", "id-card", "visa"])
        guard documentTypes.isSubset(of: supportedDocumentTypes) else {
            call.reject("documentTypes contains an unsupported value.", "UNSUPPORTED_OPTION")
            return nil
        }

        guard call.getString("camera", "back") == "back" else {
            call.reject("Only the back camera is supported by Tessera 0.5.0.", "UNSUPPORTED_OPTION")
            return nil
        }

        let timeoutMs = call.getInt("timeoutMs")
        if let timeoutMs, timeoutMs <= 0 {
            call.reject("timeoutMs must be greater than zero.", "UNSUPPORTED_OPTION")
            return nil
        }

        return ScanRequest(
            documentTypes: documentTypes,
            allowPartialResults: call.getBool("allowPartialResults", false),
            timeoutMs: timeoutMs,
            torchEnabled: call.getBool("torchEnabled", false)
        )
    }
}
