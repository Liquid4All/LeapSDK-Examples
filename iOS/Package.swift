// swift-tools-version: 5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "LeapSDK",
    platforms: [
        .iOS(.v14),
        .macOS(.v11)
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
        )
    ]
)