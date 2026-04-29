import AppKit
import SwiftUI

@main
struct LeapVoiceAssistantDemoApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        Window("Leap UI Demo", id: "main") {
            ContentView()
                .frame(minWidth: 390, minHeight: 700)
        }
        .defaultSize(width: 390, height: 700)
        .windowStyle(.hiddenTitleBar)
        .windowResizability(.contentMinSize)
    }
}

/// Intercepts app termination to unload the inference engine before exit.
///
/// When the last window is closed macOS auto-terminates the app. Without an explicit
/// `runner.unload()` the ggml Metal device destructor runs with active resource sets
/// still held, causing `ggml_metal_rsets_free` to abort.
final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        guard let runner = DemoViewModel.sharedRunner else { return .terminateNow }
        DemoViewModel.sharedRunner = nil
        Task {
            try? await runner.unload()
            NSApplication.shared.reply(toApplicationShouldTerminate: true)
        }
        return .terminateLater
    }
}
