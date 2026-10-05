import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { test } from 'node:test';
import assert from 'node:assert/strict';

for (const path of ['ios/Sources/Resources/Player.html', 'android/app/src/main/assets/player.html']) {
  test(`${path}: timers exist only during playback and never multiply`, () => {
    const html = readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
    const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
    const timers = new Map();
    let sequence = 0;
    let options;
    const loaded = [];
    const player = {
      loadVideoById: id => loaded.push(id), stopVideo() {}, pauseVideo() {}, playVideo() {},
      getCurrentTime: () => 10, getDuration: () => 100, getPlayerState: () => 1,
      getVideoData: () => ({ video_id: 'abcdefghijk' }), loadModule() {}, getOption: () => [],
    };
    const context = {
      window: {},
      document: { createElement: () => ({}), head: { appendChild() {} }, querySelector: () => null },
      setInterval: (fn, ms) => { const id = ++sequence; timers.set(id, {fn, ms}); return id; },
      clearInterval: id => timers.delete(id), setTimeout: () => ++sequence,
      YT: { Player: function (_, config) { options = config; return player; } },
    };
    runInNewContext(script, context);
    context.loadVideo('abcdefghijk');
    context.stopVideo();
    context.onYouTubeIframeAPIReady();
    options.events.onReady();
    assert.equal(loaded.length, 0, 'a cancelled pending load must not resume on readiness');
    assert.equal(timers.size, 0, 'ready and idle must not start periodic work');
    context.loadVideo('abcdefghijk');
    options.events.onStateChange({data: 1});
    const activeCount = timers.size;
    assert.ok(activeCount > 0);
    for (let i = 0; i < 50; i++) options.events.onStateChange({data: 1});
    assert.equal(timers.size, activeCount);
    options.events.onStateChange({data: 2});
    assert.equal(timers.size, 0, 'pause must cancel timers');
    options.events.onStateChange({data: 1});
    context.stopVideo();
    assert.equal(timers.size, 0, 'stop must cancel timers without waiting for provider callbacks');
    options.events.onStateChange({data: 0});
    assert.equal(timers.size, 0, 'ended must remain idle');
  });
}
