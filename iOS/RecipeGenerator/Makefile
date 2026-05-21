.PHONY: setup generate build clean open run

setup: generate

generate:
	@echo "🔨 Generating Xcode project..."
	xcodegen generate

build: generate
	@echo "🏗️ Building RecipeGenerator..."
	xcodebuild -project RecipeGenerator.xcodeproj -scheme RecipeGenerator -destination 'platform=iOS Simulator,name=iPhone 16' build

run: generate
	@echo "🚀 Running RecipeGenerator..."
	xcodebuild -project RecipeGenerator.xcodeproj -scheme RecipeGenerator -destination 'platform=iOS Simulator,name=iPhone 16' run

clean:
	@echo "🧹 Cleaning..."
	rm -rf RecipeGenerator.xcodeproj
	rm -rf build/
	rm -rf DerivedData/

open: generate
	@echo "📱 Opening in Xcode..."
	open RecipeGenerator.xcodeproj
