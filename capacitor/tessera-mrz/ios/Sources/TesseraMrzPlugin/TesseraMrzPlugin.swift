import AVFoundation
import Capacitor
import Foundation
import Tessera
import TesseraUI

@objc(TesseraMrzPlugin)
public class TesseraMrzPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "TesseraMrzPlugin"
    public let jsName = "TesseraMrz"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "scan", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "cancelScan", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isSupported", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getPluginInfo", returnType: CAPPluginReturnPromise),
    ]

    private var activeCall: CAPPluginCall?
    private var scannerController: TesseraScannerController?
    private var cancelRequested = false

    @objc func scan(_ call: CAPPluginCall) {
        guard let request = ScanRequest.parse(call) else { return }
        DispatchQueue.main.async { [weak self] in
            self?.beginScan(call, request: request)
        }
    }

    @objc func cancelScan(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            guard let self else {
                call.resolve()
                return
            }
            if let scannerController = self.scannerController {
                scannerController.cancel()
            } else if self.activeCall != nil {
                // The system permission alert cannot be dismissed programmatically. Resolve the scan as
                // cancelled as soon as the alert returns.
                self.cancelRequested = true
            }
            call.resolve()
        }
    }

    @objc func isSupported(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            let supported = AVCaptureDevice.default(for: .video) != nil
            var result: JSObject = ["supported": supported]
            if !supported {
                result["reason"] = "camera-unavailable"
            }
            call.resolve(result)
        }
    }

    @objc func getPluginInfo(_ call: CAPPluginCall) {
        call.resolve([
            "name": "capacitor-tessera-mrz",
            "version": "0.1.0",
            "tesseraVersion": "0.5.0",
            "capacitorMajor": 8,
            "platform": "ios",
        ])
    }

    @MainActor
    private func beginScan(_ call: CAPPluginCall, request: ScanRequest) {
        guard activeCall == nil else {
            call.reject("A scanner session is already active.", "SCANNER_ALREADY_ACTIVE")
            return
        }
        activeCall = call
        cancelRequested = false

        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            presentScanner(request)
        case .notDetermined:
            let callbackId = call.callbackId
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    self?.permissionRequestCompleted(callbackId: callbackId, granted: granted, request: request)
                }
            }
        case .denied, .restricted:
            rejectActive("Camera permission was denied.", code: "CAMERA_PERMISSION_DENIED")
        @unknown default:
            rejectActive("Camera access is unavailable.", code: "CAMERA_UNAVAILABLE")
        }
    }

    @MainActor
    private func permissionRequestCompleted(callbackId: String, granted: Bool, request: ScanRequest) {
        guard activeCall?.callbackId == callbackId else { return }
        if cancelRequested {
            activeCall?.resolve(["status": "cancelled"])
            clearActiveScan()
        } else if granted {
            presentScanner(request)
        } else {
            rejectActive("Camera permission was denied.", code: "CAMERA_PERMISSION_DENIED")
        }
    }

    @MainActor
    private func presentScanner(_ request: ScanRequest) {
        guard let presenter = bridge?.viewController else {
            rejectActive("The scanner could not be presented.", code: "SCANNER_INITIALIZATION_FAILED")
            return
        }

        let controller = TesseraScannerController(request: request) { [weak self] result in
            self?.scannerCompleted(result)
        }
        guard controller.present(from: presenter) else {
            rejectActive("The scanner could not be presented.", code: "SCANNER_INITIALIZATION_FAILED")
            return
        }
        scannerController = controller
    }

    @MainActor
    private func scannerCompleted(_ result: TesseraUIResult) {
        guard let call = activeCall else { return }
        switch result {
        case let .confirmed(decoded):
            guard let mapped = MrzResultMapper.map(decoded.parse) else {
                call.reject("The MRZ could not be parsed.", "MRZ_PARSE_FAILED")
                clearActiveScan()
                return
            }
            call.resolve(mapped.toJSObject())
        case let .cancelled(reason):
            switch reason {
            case .userDismissed:
                call.resolve(["status": "cancelled"])
            case .timedOut:
                call.reject("The scanner session timed out.", "SCAN_TIMEOUT")
            case .cameraUnavailable:
                call.reject("The camera is unavailable.", "CAMERA_UNAVAILABLE")
            case .permissionDenied:
                call.reject("Camera permission was denied.", "CAMERA_PERMISSION_DENIED")
            }
        }
        clearActiveScan()
    }

    @MainActor
    private func rejectActive(_ message: String, code: String) {
        activeCall?.reject(message, code)
        clearActiveScan()
    }

    @MainActor
    private func clearActiveScan() {
        activeCall = nil
        scannerController = nil
        cancelRequested = false
    }
}
