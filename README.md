# NativePHP Mobile SFX

Play short audio clips in your NativePHP Mobile app. Load sounds before you
need them, play them by name, and release them when you're done.

Requires PHP 8.4+ and NativePHP Mobile 4.3+. Supports iOS 15+ and Android
API 21+, subject to your NativePHP app's platform requirements.

## Install

```bash
composer require stupidbrains/mobile-sfx
php artisan native:plugin:register stupidbrains/mobile-sfx
```

Register the plugin before building your app so its native code is included.
No runtime permissions are needed.

## Usage

Place your audio files in `resources/audio`, then pass their absolute paths:

```php
use StupidBrains\MobileSfx\Facades\Sfx;

// Load sounds before starting a session.
$result = Sfx::preload([
    'correct' => resource_path('audio/correct.wav'),
    'incorrect' => resource_path('audio/incorrect.wav'),
]);
// ['loaded' => ['correct', 'incorrect'], 'failed' => []]
// The order of names may vary.

Sfx::play('correct'); // true if playback starts

// Release all sounds when the session ends.
Sfx::unload();
```

`preload()` returns the names that loaded and those that failed. If the
native bridge is unavailable, both lists are empty. Check that all required
sounds appear in `loaded` before starting a session that depends on audio.

`play()` returns `false` if the sound isn't loaded or playback cannot start.
`unload()` releases all loaded sounds and returns whether the operation
succeeded.

## Playback behavior

- Playing the same sound again restarts it. Different sounds can overlap;
  Android supports up to four at once.
- Loading a new file under an existing name replaces that sound. If loading
  fails, the previous sound stays available.
- On iOS, sounds play even with the silent switch enabled and interrupt
  other apps' audio. Loading sounds also activates the audio session.
- Preloading waits for sounds to load and can take several seconds. Do it
  before a timed activity. Exact playback timing is not guaranteed.

## Audio files

Use short **16-bit PCM WAV files, mono, at 44.1 kHz**. Supply your own files;
no sounds are included in the package.

This package is intended for sound effects and short prompts. It doesn't
provide playlists, seeking, background playback, or lock-screen controls.

## Testing

```bash
vendor/bin/pest
vendor/bin/pint --test
```

See [native tests](tests/native/README.md) for Swift and Kotlin regression
checks and SDK compilation instructions.

## License

[MIT](LICENSE)
