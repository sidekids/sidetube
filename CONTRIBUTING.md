# Contributing

Keep one monorepo and two native clients. Put common content in `content/`, platform code in `ios/` or `android/`. Add public content to `content/public-files.txt` only after reviewing its origin, license and absence of private data. Do not copy the historical GPL Android implementation into the replacement under a different license.

Describe the problem, behavior change and relevant validation. Use small commits such as `fix(ios): require review of bundled suggestions`. New services, telemetry and broad refactors need a separate product decision. Never include signing files, credentials, device exports or raw diagnostic reports in a contribution.

Run the applicable builds/tests from the root README. Shared policy/player/build changes require both platforms. Test privacy boundaries, cancellation, parent gates, disabled/revoked content and failure paths. Do not disable a failing safety assertion to obtain a green build. Hardware-dependent tests and measurements must state device, OS, commit and whether they were actually run.

Do not publish or rewrite remote history as part of routine changes. See `docs/release/README.md` and `SECURITY.md`.
