# Native regression tests

Run from the project root:

```bash
python3 tests/native/run.py
```

The runner compiles the plugin's Swift and Kotlin code with controllable
audio test doubles. It checks failed replacements, preparation, invalid
inputs, timeouts, late callbacks, and concurrent bridge calls. It also
checks compilation against the iOS simulator and Android SDKs.

These tests don't play real audio or measure playback latency. Verify audio
behavior and timing on physical devices before relying on it for timed tasks.

## Requirements

- macOS with Xcode
- JDK 21
- Kotlin 2.0.0 compiler dependencies in the local Gradle cache, or a compiler
  classpath supplied through `KOTLIN_COMPILER_CLASSPATH`
- Android SDK

The runner uses Homebrew's `openjdk@21` by default. Set `JAVA` to a Java
executable or `JAVA_HOME` to your JDK directory to use another installation.

The default Android SDK path is
`~/Library/Android/sdk/platforms/android-36/android.jar`.
Set `ANDROID_JAR` to use a different path.

Build output and test fixtures are created in a temporary directory and
removed when the runner exits.
