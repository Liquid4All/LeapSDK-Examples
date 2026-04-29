/// Local status type for SwiftUI colour-coding, bridged from Kotlin's `StatusType` enum.
///
/// Named `DemoStatusType` to avoid collision with `LeapUi.StatusType`.
enum DemoStatusType {
    case loading, ready, error

    init(kotlinName: String) {
        switch kotlinName {
        case "READY": self = .ready
        case "ERROR": self = .error
        default: self = .loading
        }
    }
}
