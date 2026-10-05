# Security policy

SideTube is prerelease. No released version is currently certified by this audit as supported for child-safe deployment. Security fixes target the current audited development branch; support for a release must be announced with that release.

Report a vulnerability through GitHub's private vulnerability reporting interface for `sidekids/sidetube` **if the repository owner has enabled it**. Its availability is not verified. If unavailable, ask the maintainer to establish a private reporting channel using a public issue containing only the request, with no exploit details, child data or secrets. A verified confidential reporting route is a release gate. No private contact address is published here.

Never post API keys, PINs, profiles, watch histories or raw device logs publicly. Include affected version, platform and a sanitized reproduction. Treat any committed credential as compromised: the credential owner must revoke/rotate it, then remove it from published history where appropriate and rescan. Deleting a file is not rotation. Embedded mobile API keys are recoverable from binaries and must have provider restrictions.

Scan all branches/tags and history, not just the checkout. Preserve restricted backups before history edits. Never push backup refs that might contain private information. Keep redacted scanner evidence outside the repository.

Dependency changes require upstream provenance, license review, vulnerability review and relevant tests. CI runs with read-only repository permissions and no signing credentials. No automatic release, push, tag or store upload is authorized by these workflows.
