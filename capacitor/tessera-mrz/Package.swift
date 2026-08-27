// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "CapacitorTesseraMrz",
    platforms: [.iOS(.v18)],
    products: [
        .library(
            name: "CapacitorTesseraMrz",
            targets: ["TesseraMrzPlugin"]
        ),
    ],
    dependencies: [
        .package(
            url: "https://github.com/ionic-team/capacitor-swift-pm.git",
            from: "8.0.0"
        ),
        .package(
            url: "https://github.com/lightine-io/tessera-swift.git",
            exact: "0.5.0"
        ),
    ],
    targets: [
        .target(
            name: "TesseraMrzPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm"),
                .product(name: "Tessera", package: "tessera-swift"),
                .product(name: "TesseraUI", package: "tessera-swift"),
            ],
            path: "ios/Sources/TesseraMrzPlugin"
        ),
        .testTarget(
            name: "TesseraMrzPluginTests",
            dependencies: [
                "TesseraMrzPlugin",
                .product(name: "Tessera", package: "tessera-swift"),
            ],
            path: "ios/Tests/TesseraMrzPluginTests"
        ),
    ]
)
