.PHONY: setup generate build clean open run

setup: generate

generate:
	@echo "🔨 Generating Xcode project..."
	xcodegen generate

build: generate
	@echo "🏗️ Building LeapChatExample..."
	xcodebuild -project LeapChatExample.xcodeproj -scheme LeapChatExample -destination 'platform=iOS Simulator,name=iPhone 17' build

run: generate
	@echo "🚀 Running LeapChatExample..."
	xcodebuild -project LeapChatExample.xcodeproj -scheme LeapChatExample -destination 'platform=iOS Simulator,name=iPhone 17' run

clean:
	@echo "🧹 Cleaning..."
	rm -rf LeapChatExample.xcodeproj
	rm -rf build/
	rm -rf DerivedData/

open: generate
	@echo "📱 Opening in Xcode..."
	open LeapChatExample.xcodeproj
