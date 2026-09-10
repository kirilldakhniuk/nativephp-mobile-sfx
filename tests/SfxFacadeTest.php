<?php

use Native\Mobile\Testing\FakeBridge;
use StupidBrains\MobileSfx\Facades\Sfx;

beforeEach(function () {
    $this->bridge = FakeBridge::enable();
});

it('sends one Preload call carrying the clip map', function () {
    $this->bridge->respondTo('Sfx.Preload', [
        'success' => true,
        'loaded' => ['letter-c'],
        'failed' => [],
    ]);

    $result = Sfx::preload(['letter-c' => '/tmp/letter-c.wav']);

    $this->bridge->assertCalledTimes('Sfx.Preload', 1);
    $this->bridge->assertCalled(
        'Sfx.Preload',
        fn (array $p) => $p['clips'] === ['letter-c' => '/tmp/letter-c.wav']
    );
    expect($result)->toBe(['loaded' => ['letter-c'], 'failed' => []]);
});

it('reports partially failed preloads', function () {
    $this->bridge->respondTo('Sfx.Preload', [
        'success' => true,
        'loaded' => ['letter-c'],
        'failed' => ['letter-h'],
    ]);

    expect(Sfx::preload([
        'letter-c' => '/tmp/letter-c.wav',
        'letter-h' => '/tmp/missing.wav',
    ]))->toBe(['loaded' => ['letter-c'], 'failed' => ['letter-h']]);
});

it('plays a clip by name', function () {
    $this->bridge->respondTo('Sfx.Play', ['success' => true]);

    expect(Sfx::play('letter-c'))->toBeTrue();
    $this->bridge->assertCalled('Sfx.Play', fn (array $p) => $p['name'] === 'letter-c');
});

it('returns false when the native side reports failure', function () {
    $this->bridge->respondTo('Sfx.Play', ['success' => false]);

    expect(Sfx::play('nope'))->toBeFalse();
});

it('returns false without throwing when no native side answers', function () {
    // Unscripted: FakeBridge records the call and returns null — the same
    // shape a dev machine with no device attached produces.
    expect(Sfx::play('letter-c'))->toBeFalse();
    $this->bridge->assertCalled('Sfx.Play');
});

it('returns empty lists when a preload gets no answer', function () {
    expect(Sfx::preload(['letter-c' => '/tmp/letter-c.wav']))
        ->toBe(['loaded' => [], 'failed' => []]);
});

it('unloads', function () {
    $this->bridge->respondTo('Sfx.Unload', ['success' => true]);

    expect(Sfx::unload())->toBeTrue();
    $this->bridge->assertCalled('Sfx.Unload');
});

it('preloads before playing when a caller does both', function () {
    $this->bridge->respondTo('Sfx.Preload', ['success' => true, 'loaded' => ['a'], 'failed' => []]);
    $this->bridge->respondTo('Sfx.Play', ['success' => true]);

    Sfx::preload(['a' => '/tmp/a.wav']);
    Sfx::play('a');

    $this->bridge->assertCallOrder(['Sfx.Preload', 'Sfx.Play']);
});

it('teaches FakeBridge an assertPlayedSfx vocabulary', function () {
    $this->bridge->respondTo('Sfx.Play', ['success' => true]);

    Sfx::play('letter-k');

    $this->bridge->assertPlayedSfx('letter-k');
});
