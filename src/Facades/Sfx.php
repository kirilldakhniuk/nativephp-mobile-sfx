<?php

namespace StupidBrains\MobileSfx\Facades;

use Illuminate\Support\Facades\Facade;

/**
 * @method static array preload(array $clips)
 * @method static bool play(string $name)
 * @method static bool unload()
 *
 * @see \StupidBrains\MobileSfx\Sfx
 */
class Sfx extends Facade
{
    protected static function getFacadeAccessor(): string
    {
        return \StupidBrains\MobileSfx\Sfx::class;
    }
}
