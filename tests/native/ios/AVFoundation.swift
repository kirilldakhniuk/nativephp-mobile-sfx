import Foundation
public protocol AVAudioPlayerDelegate: AnyObject {
    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool)
}
public enum AudioError: Error { case failure }
public class AVAudioPlayer {
    public static var created: [AVAudioPlayer] = []
    public weak var delegate: AVAudioPlayerDelegate?
    public var currentTime: TimeInterval = 0
    public var prepareCount = 0
    public var playCount = 0
    public var stopped = false
    public var isPlaying = false
    public var preparationSucceeds = true
    public init(contentsOf url: URL) throws {
        precondition(Thread.isMainThread)
        if url.lastPathComponent == "corrupt" || url.lastPathComponent == "missing" { throw AudioError.failure }
        preparationSucceeds = url.lastPathComponent != "unprepared"
        Self.created.append(self)
    }
    public func prepareToPlay() -> Bool {
        precondition(Thread.isMainThread); prepareCount += 1; return preparationSucceeds
    }
    public func play() -> Bool { precondition(Thread.isMainThread); playCount += 1; isPlaying = true; return true }
    public func stop() { precondition(Thread.isMainThread); stopped = true; isPlaying = false }
    public func finish() { isPlaying = false; delegate?.audioPlayerDidFinishPlaying(self, successfully: true) }
}
public class AVAudioSession {
    public enum Category { case playback }
    public enum Mode { case `default` }
    public struct SetActiveOptions: OptionSet {
        public let rawValue: Int
        public init(rawValue: Int) { self.rawValue = rawValue }
        public static let notifyOthersOnDeactivation = Self(rawValue: 1)
    }
    public static let shared = AVAudioSession()
    public static func sharedInstance() -> AVAudioSession { shared }
    public var activationCount = 0
    public var activationFails = false
    public func setCategory(_ category: Category, mode: Mode) throws { precondition(Thread.isMainThread) }
    public func setActive(_ active: Bool, options: SetActiveOptions = []) throws {
        precondition(Thread.isMainThread)
        if active { activationCount += 1; if activationFails { throw AudioError.failure } }
    }
}
