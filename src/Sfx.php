<?php

namespace StupidBrains\MobileSfx;

class Sfx
{
    /**
     * Load named clips and prepare them for immediate playback.
     *
     * @param  array<string, string>  $clips  name => absolute filesystem path
     * @return array{loaded: list<string>, failed: list<string>}
     */
    public function preload(array $clips): array
    {
        $decoded = $this->call('Sfx.Preload', ['clips' => $clips]);

        return [
            'loaded' => $decoded['loaded'] ?? [],
            'failed' => $decoded['failed'] ?? [],
        ];
    }

    /**
     * Play a preloaded clip. False for an unknown or unloaded name — an
     * audio failure must never be able to kill a run in progress.
     */
    public function play(string $name): bool
    {
        return ($this->call('Sfx.Play', ['name' => $name])['success'] ?? false) === true;
    }

    /** Release every loaded clip and deactivate the audio session. */
    public function unload(): bool
    {
        return ($this->call('Sfx.Unload', [])['success'] ?? false) === true;
    }

    /**
     * The bridge round-trip. Returns [] for every failure mode — no
     * extension, no device, malformed JSON — so callers only handle data.
     *
     * @param  array<string, mixed>  $payload
     * @return array<string, mixed>
     */
    protected function call(string $method, array $payload): array
    {
        if (! function_exists('nativephp_call')) {
            return [];
        }

        $result = nativephp_call($method, json_encode($payload));

        if (! is_string($result)) {
            return [];
        }

        $decoded = json_decode($result, true);

        return is_array($decoded) ? $decoded : [];
    }
}
