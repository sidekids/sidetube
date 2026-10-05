# Energy and performance gates

Idle means no app-controlled periodic work without an active user function. Static evidence is separate from measured battery usage. No physical-device power measurement has been completed by this audit.

Observed before changes: Android created a player for the app session, scheduled startup warmup and retained a five-second interval at pause. iOS held player bridges after close, used a permanent 500 ms position interval, and PeerTube polling had no owned cancellation handle. Parent profile switching on Android accumulated collectors. Corrective changes bind resources/subscriptions more closely to their use and cancel paused player timers. JavaScript tests verify zero owned repeating timers at ready/pause/stop and no growth after 50 state repetitions. These are not battery measurements.

Remaining: Android HTTP reads can block on IO until timeout despite coroutine cancellation. Provider processes and image loads need runtime traces. The documented Sidephone WebView startup workaround must be retested after lifetime changes. No numerical energy/performance improvement is claimed without measurements.

iOS follow-up (2026-09-10): the fixed 15-second child-home check is replaced by calendar-boundary scheduling. Profile rules, player identity/play state, sleep-timer state, foreground lifecycle and significant clock changes restart the calculation. Bedtime warnings/start/end, parent-exception expiry, DST changes and daily-budget midnight rollover have explicit deadlines. With all protections disabled and no sleep timer there is no scheduled protection wakeup. A running sleep timer waits for its fade window or expiry; second-by-second updates occur only during the final audio fade when a player exists. PIN entry has a one-second timeline only during an active foreground lockout and removes it at expiry. Nine new unit tests cover scheduling boundaries; this is not yet a device CPU or battery measurement.

Android daily-limit enforcement adds one cancellable coroutine deadline only during limited playback. Pause/buffering cancels it, resume recalculates remaining time, and closing/backgrounding removes it. Unlimited playback and the idle home screen do not schedule this deadline. Viewing uses a monotonic clock; buffering and failed-video transitions no longer inflate/misattribute time. This scheduling behavior is code-reviewed, not yet verified in a device trace.

Quiet hours use a separate boundary deadline only while a player session exists (including paused/loading players). Relevant boundaries are local start/end, midnight, parent-exception expiry and daylight-saving transitions. Clock/time-zone broadcasts trigger reevaluation; the receiver exists only for the player session. Closing/backgrounding cancels the deadline. No quiet-hours polling is scheduled on the idle home screen. These lifecycle paths still require device tests.

Android interrupted-session protection adds a marker write before playback and a removal after successful close, not periodic checkpoints or a background worker. The parent list observes database changes without polling. Device measurements must include these writes; no numerical overhead or savings are claimed.

| Gate | Protocol | Pass criterion | Current status |
|---|---|---|---|
| A Idle | Release build, settle, foreground home 10 min, no input | No app periodic network requests or unnecessary recurring work; CPU close to device baseline | BLOCKER: device evidence absent; identified iOS idle polling removed in code |
| B Navigation | 50 complete main-screen round trips, repeat after warmup | No monotonic retained player/task/object growth; explain cache plateau | BLOCKER: device memory traces absent |
| C Video exit | Play, pause, resume, leave; repeat 10 times and include load-in-progress exit | No continuing audio/media requests; app releases bridge and callbacks | WARNING: code/tests improved, real providers/devices unmeasured |
| D Background | Background Release app for 30 min after navigation/playback | No self-initiated requests or keep-alive work | BLOCKER: device evidence absent |

iOS: use Instruments Time Profiler, Allocations, Network and available power/energy instrumentation; capture Memory Graph before/after navigation. Android: use CPU/Memory/Network/Power Profiler and per-app batterystats. Export diagnostics privately; avoid recording child history. Never reset device-wide batterystats on a personal device without coordinating the measurement.

Performance protocol: record commit, signed/unsigned configuration, device model, OS/WebView version, battery/thermal state, network, content set, warmup and samples. Measure cold/warm launch, screen transitions, scrolling, decode size, database calls and player start separately. Use at least five repeats, report median/p95 and before/after under identical conditions. Define device-specific thresholds before judging results. Simulator timings are not device battery evidence.
