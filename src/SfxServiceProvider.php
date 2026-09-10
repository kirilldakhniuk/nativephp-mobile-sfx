<?php

namespace StupidBrains\MobileSfx;

use Illuminate\Support\ServiceProvider;
use Native\Mobile\Testing\FakeBridge;

class SfxServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        $this->app->singleton(Sfx::class, function () {
            return new Sfx;
        });
    }

    public function boot(): void
    {
        if ($this->app->environment('testing') && class_exists(FakeBridge::class)) {
            FakeBridge::macro('assertPlayedSfx', function (string $name) {
                /** @var FakeBridge $this */
                return $this->assertCalled('Sfx.Play', fn (array $p) => $p['name'] === $name);
            });
        }
    }
}
