import SwiftUI
import Tessera
import TesseraUI
import UIKit

@MainActor
final class TesseraScannerController {
    private let request: ScanRequest
    private let onResult: (TesseraUIResult) -> Void
    private var hostingController: UIViewController?
    private var completed = false

    init(request: ScanRequest, onResult: @escaping (TesseraUIResult) -> Void) {
        self.request = request
        self.onResult = onResult
    }

    func present(from presenter: UIViewController) -> Bool {
        guard hostingController == nil,
              presenter.viewIfLoaded?.window != nil,
              presenter.presentedViewController == nil else { return false }

        let config = MrzScannerConfig(
            enabledMethods: [.camera],
            reviewMode: .instantReturn,
            torchOnByDefault: request.torchEnabled,
            scanTimeout: request.timeoutMs.map { .milliseconds(Int64($0)) }
        )
        let rootView = ScannerHostView(
            config: config,
            request: request,
            onResult: { [weak self] result in self?.complete(result) }
        )
        let controller = UIHostingController(rootView: rootView)
        controller.modalPresentationStyle = .fullScreen
        hostingController = controller
        presenter.present(controller, animated: true)
        return true
    }

    func cancel() {
        complete(.cancelled(.userDismissed))
    }

    private func complete(_ result: TesseraUIResult) {
        guard !completed else { return }
        completed = true
        let controller = hostingController
        controller?.dismiss(animated: true) { [onResult] in onResult(result) }
        if controller == nil {
            onResult(result)
        }
    }
}

private struct ScannerHostView: View {
    let config: MrzScannerConfig
    let request: ScanRequest
    let onResult: (TesseraUIResult) -> Void

    @State private var session = 0

    var body: some View {
        MrzScannerView(config: config) { result in
            guard case let .confirmed(decoded) = result else {
                onResult(result)
                return
            }

            if shouldContinueScanning(decoded.parse) {
                session += 1
            } else {
                onResult(result)
            }
        }
        .id(session)
    }

    private func shouldContinueScanning(_ parse: ParseResult) -> Bool {
        if parse is ParseResult.PartialSuccess, !request.allowPartialResults {
            return true
        }
        if request.documentTypes.isEmpty {
            return false
        }
        return !request.documentTypes.contains(MrzResultMapper.documentFamily(parse) ?? "")
    }
}
