import LeapSDK

@MainActor
class GeneratorViewModel: ObservableObject {
    @Published var isModelLoading = false
    @Published var isGenerating = false
    @Published var recipe: Recipe? = nil
    private var modelRunner: ModelRunner?
 
    func setupModel() async {
        do {
            isModelLoading = true
            guard let modelURL = Bundle.main.url(
                forResource: "LFM2-1.2B-8da4w_output_8da8w-seq_4096",
                withExtension: "bundle"
            ) else {
                print("Could not find model bundle")
                return
            }
 
            modelRunner = try await Leap.load(url: modelURL)
            isModelLoading = false
        } catch {
            print("Failed to load model: \(error)")
        }
    }
    
    func generateRecipe() async throws {
        guard let modelRunner = modelRunner else {
            print("Model not yet loaded")
            return
        }
        isGenerating = true
        let conversation = modelRunner.createConversation(systemPrompt: "You know a lot in cooking. Generate a recipe based on user's requirements.");
        var generationOptions = GenerationOptions()
        try generationOptions.setResponseFormat(type: Recipe.self)
        
        let flow = conversation.generateResponse(
            userTextMessage: "A dinner dish with shrimps.",
            generationOptions: generationOptions
        )
        var recipeJsonBuffer = ""
        for try await response in flow {
            switch (response) {
            case MessageResponse.chunk(let text):
                recipeJsonBuffer.append(contentsOf: text)
            case MessageResponse.complete(let content, _):
                break
            default:
                // ignore
                continue
            }
        }
        print("Generated recipe json: \(recipeJsonBuffer)")
        let recipeJsonData = recipeJsonBuffer.data(using: .utf8)!
        recipe = try JSONDecoder().decode(Recipe.self, from: recipeJsonData)
        isGenerating = false
    }
}
