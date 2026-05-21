.PHONY: setup generate build clean open run

setup: generate

generate:
	@echo "🔨 Generating Xcode project..."
	xcodegen generate

build: generate
	@echo "🏗️ Building LeapVLMExample..."
	xcodebuild -project LeapVLMExample.xcodeproj -scheme LeapVLMExample -destination 'platform=iOS Simulator,name=iPhone 16' build

run: generate
	@echo "🚀 Running LeapVLMExample..."
	xcodebuild -project LeapVLMExample.xcodeproj -scheme LeapVLMExample -destination 'platform=iOS Simulator,name=iPhone 16' run

clean:
	@echo "🧹 Cleaning..."
	rm -rf LeapVLMExample.xcodeproj
	rm -rf build/
	rm -rf DerivedData/

open: generate
	@echo "📱 Opening in Xcode..."
	open LeapVLMExample.xcodeproj
