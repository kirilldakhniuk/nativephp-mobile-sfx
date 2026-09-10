## stupidbrains/mobile-sfx

Low-latency playback of short bundled audio clips for NativePHP Mobile.
Not a media player — no seeking, playlists, background playback, or
lock-screen controls.

### Installation

```bash
composer require stupidbrains/mobile-sfx
php artisan native:plugin:register stupidbrains/mobile-sfx
```

The `native:plugin:register` step is required — without it the plugin is
inert and every call returns false.

### PHP Usage

Use the `Sfx` facade. There are exactly three methods; there is no
`execute()` or `getStatus()`.

@verbatim
<code-snippet name="Using Sfx Facade" lang="php">
use StupidBrains\MobileSfx\Facades\Sfx;

// name => absolute filesystem path
$result = Sfx::preload([
    'letter-c' => resource_path('audio/letter-c.wav'),
]);
// => ['loaded' => ['letter-c'], 'failed' => []]

Sfx::play('letter-c'); // bool — false for unknown/unloaded name, never throws

Sfx::unload(); // bool — releases every loaded clip
</code-snippet>
@endverbatim

### Available Methods

- `Sfx::preload(array $clips): array` — `array{loaded: list<string>, failed: list<string>}`
- `Sfx::play(string $name): bool`
- `Sfx::unload(): bool`

### Events

This package emits no native events. There is no `SfxCompleted` event.

### Audio format

16-bit PCM WAV, mono, 44.1 kHz. Avoid MP3/AAC — decoding adds onset
jitter this package exists to eliminate.

### Notes

- Namespace is `StupidBrains\MobileSfx` (composer `stupidbrains/mobile-sfx`).
- No JavaScript bridge is exposed by this package.
- iOS uses the `.playback` audio session category, so playback is audible
  even with the hardware mute switch engaged, and interrupts other audio.
