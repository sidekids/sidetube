# Android PIN counter hardening

Follow-up: 2026-09-10. `PinStore.verify` previously incremented the persisted `Int` failure count directly. `Int.MAX_VALUE + 1` wraps negative, and a negative count falls below the lockout threshold. The previous bounded-shift correction inside `lockoutSeconds` did not protect this preceding increment.

`PinLockoutPolicy.nextFailureCount` now saturates at the largest representable multiple of the five-attempt threshold. Negative/corrupt counts map to that same boundary. Every further wrong PIN at saturation receives the existing maximum one-hour penalty, rather than reopening a free-attempt window. Normal counts retain the existing cadence; a valid PIN still uses the existing successful-verification reset path. This changes no storage format, dependency, timer or background work.

Tests cover ordinary counts 0–1000, both integer extremes, negative data, the saturation boundary and repeated increments after saturation. These are arithmetic-policy tests, not a successful corruption/recovery experiment against Android Keystore or encrypted preferences.

The complete Android core unit suite passes 128 tests with zero failures/errors after this change (previously 126).

Remaining requirements: encrypted-preference read/type failures, synchronous durability of PIN setup and penalties, process-kill behavior, clock manipulation and complete PIN UI/device tests. `SharedPreferences.apply()` remains asynchronous; this correction does not certify durable storage or close the parental-security release gate.
