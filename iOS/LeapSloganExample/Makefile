.PHONY: setup generate build clean open run

setup: generate

generate:
	@echo "🔨 Generating Xcode project..."
	xcodegen generate

build: generate
	@echo "🏗️ Building LeapSloganExample..."
	xcodebuild -project LeapSloganExample.xcodeproj -scheme LeapSloganExample -destination 'platform=iOS Simulator,name=iPhone 17' build

run: generate
	@echo "🚀 Running LeapSloganExample..."
	xcodebuild -project LeapSloganExample.xcodeproj -scheme LeapSloganExample -destination 'platform=iOS Simulator,name=iPhone 17' run

clean:
	@echo "🧹 Cleaning..."
	rm -rf LeapSloganExample.xcodeproj
	rm -rf build/
	rm -rf DerivedData/

open: generate
	@echo "📱 Opening in Xcode..."
	open LeapSloganExample.xcodeproj