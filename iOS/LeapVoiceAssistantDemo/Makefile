.PHONY: setup generate build clean open run

setup: generate

generate:
	@echo "🔨 Generating Xcode project..."
	xcodegen generate

build: generate
	@echo "🏗️ Building LeapVoiceAssistantDemo..."
	xcodebuild -project LeapVoiceAssistantDemo.xcodeproj -scheme LeapVoiceAssistantDemo -destination 'platform=iOS Simulator,name=iPhone 16' build

run: generate
	@echo "🚀 Running LeapVoiceAssistantDemo..."
	xcodebuild -project LeapVoiceAssistantDemo.xcodeproj -scheme LeapVoiceAssistantDemo -destination 'platform=iOS Simulator,name=iPhone 16' run

clean:
	@echo "🧹 Cleaning..."
	rm -rf LeapVoiceAssistantDemo.xcodeproj
	rm -rf build/
	rm -rf DerivedData/

open: generate
	@echo "📱 Opening in Xcode..."
	open LeapVoiceAssistantDemo.xcodeproj
