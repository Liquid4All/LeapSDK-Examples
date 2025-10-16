.PHONY: setup generate build clean open run

setup: generate

generate:
	@echo "🔨 Generating Xcode project..."
	xcodegen generate

build: generate
	@echo "🏗️ Building LeapAudioDemo..."
	xcodebuild -project LeapAudioDemo.xcodeproj -scheme LeapAudioDemo -destination 'platform=iOS Simulator,name=iPhone 16' build

run: generate
	@echo "🚀 Running LeapAudioDemo..."
	xcodebuild -project LeapAudioDemo.xcodeproj -scheme LeapAudioDemo -destination 'platform=iOS Simulator,name=iPhone 16' run

clean:
	@echo "🧹 Cleaning..."
	rm -rf LeapAudioDemo.xcodeproj
	rm -rf build/
	rm -rf DerivedData/

open: generate
	@echo "📱 Opening in Xcode..."
	open LeapAudioDemo.xcodeproj
