import Foundation
import AVFoundation

enum SfxFunctions {
    // AVAudioPlayer delegates need a running run loop. Confine players, session
    // changes, and delegate callbacks to main, including embedded-webview calls.
    private static var players: [String: AVAudioPlayer] = [:]
    private static let delegate = PlaybackDelegate()

    private static func onMain<T>(_ work: () -> T) -> T {
        Thread.isMainThread ? work() : DispatchQueue.main.sync(execute: work)
    }

    private class PlaybackDelegate: NSObject, AVAudioPlayerDelegate {
        func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
            onMain {
                // A queued callback must not re-prepare a replaced or unloaded player.
                guard !player.isPlaying,
                      let name = players.first(where: { $0.value === player })?.key else { return }
                player.currentTime = 0
                if !flag {
                    print("Sfx: \(name) finished unsuccessfully; keeping it loaded")
                }
                if !player.prepareToPlay() {
                    print("Sfx: could not re-prepare \(name); will prepare on next play")
                }
            }
        }
    }

    private static func report(loaded: [String] = [], failed: [String] = []) -> [String: Any] {
        ["success": true, "loaded": loaded, "failed": failed]
    }

    class Preload: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            onMain {
                guard let clips = parameters["clips"] as? [String: Any], !clips.isEmpty else {
                    return report()
                }
                var loaded: [String] = []
                var failed: [String] = []
                var valid: [String: String] = [:]
                for (name, value) in clips {
                    guard let path = value as? String,
                          !path.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                          (path as NSString).isAbsolutePath else {
                        failed.append(name)
                        continue
                    }
                    valid[name] = path
                }
                guard !valid.isEmpty else { return report(failed: failed) }

                do {
                    let session = AVAudioSession.sharedInstance()
                    try session.setCategory(.playback, mode: .default)
                    try session.setActive(true)
                } catch {
                    print("Sfx.Preload: audio session setup failed: \(error)")
                    return report(failed: failed + Array(valid.keys))
                }

                for (name, path) in valid {
                    do {
                        let player = try AVAudioPlayer(contentsOf: URL(fileURLWithPath: path))
                        guard player.prepareToPlay() else {
                            failed.append(name)
                            continue
                        }
                        player.delegate = delegate
                        // Keep the old clip playable until its replacement is ready.
                        players[name]?.stop()
                        players[name]?.delegate = nil
                        players[name] = player
                        loaded.append(name)
                    } catch {
                        print("Sfx.Preload: could not load \(name): \(error)")
                        failed.append(name)
                    }
                }
                return report(loaded: loaded, failed: failed)
            }
        }
    }

    class Play: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            onMain {
                guard let name = parameters["name"] as? String,
                      let player = players[name] else {
                    return ["success": false]
                }
                do {
                    try AVAudioSession.sharedInstance().setActive(true)
                } catch {
                    print("Sfx.Play: session re-activation failed: \(error)")
                }
                player.currentTime = 0
                return ["success": player.play()]
            }
        }
    }

    class Unload: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            onMain {
                players.values.forEach {
                    $0.delegate = nil
                    $0.stop()
                }
                players.removeAll()
                do {
                    try AVAudioSession.sharedInstance().setActive(
                        false, options: .notifyOthersOnDeactivation
                    )
                } catch {
                    print("Sfx.Unload: session deactivation failed: \(error)")
                }
                return ["success": true]
            }
        }
    }
}
