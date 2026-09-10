protocol BridgeFunction {
    func execute(parameters: [String: Any]) throws -> [String: Any]
}
