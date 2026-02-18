// swift-tools-version: 5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import CompilerPluginSupport
import PackageDescription

let package = Package(
    name: "LeapSDK",
    platforms: [
        .iOS(.v14),
        .macOS(.v13)
    ],
    products: [
        .library(
            name: "LeapSDK",
            targets: ["LeapSDK"]
        ),
        .library(
            name: "LeapModelDownloader",
            targets: ["LeapModelDownloader", "LeapSDK"]
        ),
        .library(
            name: "LeapSDKMacros",
            targets: ["LeapSDKMacros"]
        ),
    ],
    dependencies: [
        .package(url: "https://github.com/swiftlang/swift-syntax.git", from: "509.0.0"),
    ],
    targets: [
        // Local XCFrameworks from parent leap-android-sdk repository
        .binaryTarget(
            name: "LeapSDK",
            path: "./LeapSDK.xcframework"
        ),
        .binaryTarget(
            name: "LeapModelDownloader",
            path: "./LeapModelDownloader.xcframework"
        ),

        // Swift macro compiler plugin
        .macro(
            name: "LeapSDKConstrainedGenerationPlugin",
            dependencies: [
                .product(name: "SwiftSyntaxMacros", package: "swift-syntax"),
                .product(name: "SwiftCompilerPlugin", package: "swift-syntax"),
            ],
            path: "Sources/LeapSDKConstrainedGenerationPlugin"
        ),

        // Public macro declarations library
        .target(
            name: "LeapSDKMacros",
            dependencies: [
                "LeapSDK",
                "LeapSDKConstrainedGenerationPlugin",
            ],
            path: "Sources/LeapSDKMacros"
        ),
    ]
)
