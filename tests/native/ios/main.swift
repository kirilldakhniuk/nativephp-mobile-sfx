import Foundation
import AVFoundation

let preload = SfxFunctions.Preload()
let play = SfxFunctions.Play()
let unload = SfxFunctions.Unload()
func load(_ path: Any) -> [String: Any] {
    try! preload.execute(parameters: ["clips": ["cue": path]])
}
func plays(_ name: String = "cue") -> Bool {
    try! play.execute(parameters: ["name": name])["success"] as! Bool
}
assert(load("/good")["loaded"] as! [String] == ["cue"])
let original = AVAudioPlayer.created.last!
assert(plays())
original.finish()
assert(original.prepareCount == 2)
assert(plays())
original.currentTime = 0.25
original.delegate!.audioPlayerDidFinishPlaying(original, successfully: true)
assert(original.prepareCount == 2 && original.currentTime == 0.25)
for path: Any in ["", "   ", 42, "relative", "/missing", "/corrupt", "/unprepared"] {
    assert(load(path)["failed"] as! [String] == ["cue"])
    assert(plays() && !original.stopped)
}
let session = AVAudioSession.shared
session.activationFails = true
let playsBefore = original.playCount
assert(plays())
assert(original.playCount == playsBefore + 1)
session.activationFails = false
let oldDelegate = original.delegate!
assert(load("/replacement")["loaded"] as! [String] == ["cue"])
assert(original.stopped)
let preparations = original.prepareCount
oldDelegate.audioPlayerDidFinishPlaying(original, successfully: true)
assert(original.prepareCount == preparations)
let replacement = AVAudioPlayer.created.last!
let replacementDelegate = replacement.delegate!
replacement.preparationSucceeds = false
replacement.finish()
assert(plays())
_ = try! unload.execute(parameters: [:])
let activations = session.activationCount
assert(!plays("missing"))
assert(session.activationCount == activations)
let replacementPreparations = replacement.prepareCount
replacementDelegate.audioPlayerDidFinishPlaying(replacement, successfully: true)
assert(replacement.prepareCount == replacementPreparations)

// Exercise worker-thread bridge entry while main services the player run loop.
let group = DispatchGroup()
for n in 0..<20 {
    group.enter()
    DispatchQueue.global().async {
        let name = "clip\(n)"
        let result = try! preload.execute(parameters: ["clips": [name: "/good"]])
        assert(result["loaded"] as! [String] == [name])
        assert(plays(name))
        group.leave()
    }
}
let deadline = Date().addingTimeInterval(5)
while group.wait(timeout: .now()) != .success {
    precondition(Date() < deadline, "Worker bridge calls deadlocked")
    _ = RunLoop.main.run(mode: .default, before: Date().addingTimeInterval(0.01))
}
_ = try! unload.execute(parameters: [:])
print("iOS regression checks passed: preparation, failed replacement, activation failure, unknown clip, stale delegates, concurrent entry")
