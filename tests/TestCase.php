<?php

namespace Tests;

use Native\Mobile\NativeServiceProvider;
use Orchestra\Testbench\TestCase as Orchestra;
use StupidBrains\MobileSfx\SfxServiceProvider;

abstract class TestCase extends Orchestra
{
    /**
     * NativeServiceProvider is listed first on purpose: its register()
     * defines the nativephp_call() polyfill that FakeBridge intercepts.
     * Without it the facade's function_exists() guard is false and every
     * test that asserts a bridge call fails.
     *
     * @return list<class-string>
     */
    protected function getPackageProviders($app): array
    {
        return [
            NativeServiceProvider::class,
            SfxServiceProvider::class,
        ];
    }
}
