#!/usr/bin/env python3
"""Run native control-flow tests with audio test doubles, then SDK compile checks.

Requires macOS/Xcode and a Kotlin compiler classpath (KOTLIN_COMPILER_CLASSPATH,
or Kotlin 2.0.0 in the local Gradle cache). JAVA and ANDROID_JAR may override
local tool discovery. All build output goes to a temporary directory.
"""
import glob
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
TESTS = ROOT / 'tests/native'


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True, cwd=ROOT)


def cached(group, artifact, version):
    pattern = str(Path.home() / '.gradle/caches/modules-2/files-2.1' / group / artifact / version / '*' / '*.jar')
    matches = glob.glob(pattern)
    if not matches:
        raise SystemExit(f'Missing {artifact}; set KOTLIN_COMPILER_CLASSPATH to your compiler and its dependencies')
    return matches[0]


with tempfile.TemporaryDirectory(prefix='sfx-native-') as temporary:
    build = Path(temporary)
    swift = ['xcrun', 'swiftc', '-swift-version', '5', '-module-cache-path', str(build / 'modules')]
    run(*swift, '-emit-library', '-emit-module', '-module-name', 'AVFoundation',
        TESTS / 'ios/AVFoundation.swift', '-emit-module-path', build / 'AVFoundation.swiftmodule',
        '-o', build / 'libAVFoundation.dylib')
    run(*swift, '-I', build, '-L', build, '-lAVFoundation', '-Xlinker', '-rpath', '-Xlinker', build,
        TESTS / 'ios/Bridge.swift', ROOT / 'resources/ios/SfxFunctions.swift', TESTS / 'ios/main.swift',
        '-o', build / 'ios-tests')
    run(build / 'ios-tests')
    sdk = subprocess.check_output(['xcrun', '--sdk', 'iphonesimulator', '--show-sdk-path'], text=True).strip()
    run(*swift, '-typecheck', '-sdk', sdk, '-target', 'arm64-apple-ios15.0-simulator',
        TESTS / 'ios/Bridge.swift', ROOT / 'resources/ios/SfxFunctions.swift')

    compiler = os.environ.get('KOTLIN_COMPILER_CLASSPATH')
    if not compiler:
        compiler = os.pathsep.join([
            cached('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.0.0'),
            cached('org.jetbrains.kotlin', 'kotlin-stdlib', '2.0.0'),
            cached('org.jetbrains.kotlin', 'kotlin-script-runtime', '2.0.0'),
            cached('org.jetbrains.kotlin', 'kotlin-reflect', '1.6.10'),
            cached('org.jetbrains.intellij.deps', 'trove4j', '*'),
            cached('org.jetbrains', 'annotations', '13.0'),
        ])
    java = os.environ.get('JAVA', str(Path(os.environ.get('JAVA_HOME', '/opt/homebrew/opt/openjdk@21')) / 'bin/java'))
    kotlin = [java, '-cp', compiler, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
              '-no-stdlib', '-no-reflect', '-jvm-target', '17']
    source = ROOT / 'resources/android/SfxFunctions.kt'
    run(*kotlin, '-classpath', compiler, *sorted((TESTS / 'android').glob('*.kt')), source,
        '-d', build / 'android-tests.jar')
    fixtures = build / 'fixtures'
    fixtures.mkdir()
    run(java, '-cp', str(build / 'android-tests.jar') + os.pathsep + compiler, 'MainKt', fixtures)
    android = os.environ.get('ANDROID_JAR', str(Path.home() / 'Library/Android/sdk/platforms/android-36/android.jar'))
    run(*kotlin, '-classpath', compiler + os.pathsep + android,
        TESTS / 'android/Bridge.kt', source, '-d', build / 'android-sdk.jar')
    print('Native regression tests and iOS/Android SDK compilation passed.')
