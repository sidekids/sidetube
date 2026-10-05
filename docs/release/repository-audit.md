# Repository comparison and consolidation decision

Audit baseline 2026-09-09:

| Repository | Main commit | Reachable commits | Advertised branches/tags |
|---|---|---|---|
| Public GitHub | `a0e6ee40407b4a3ad1fd5f7ddfaf7293dbba4d78` | 4 | main / no tags |
| Developer source of truth | `5444d61286239fa6c161c033d359313666528d22` | 1 | main / no tags |

No shared Git ancestor exists. The developer snapshot is a new root, not a merge of GitHub history. A local review branch descends from GitHub and contains additional development, but is not a substitute for the developer source of truth. Its working tree and both standalone local clients were backed up without modification, including the user's uncommitted Android player change.

Both remote mirrors and verified bundles are retained outside this repository, together with full local archives, object inventories and redacted evidence. Backups contain potentially private information and must never be pushed. The full path-by-path comparison remains in the private audit workspace (`reports/repository-diff.md` and `.json`) with mode/type/blob IDs and review flags.

| Classification | Result / decision |
|---|---|
| GitHub only | 272 paths: predominantly old Android modules, tests and supporting docs. Preserve history/attribution; do not restore removed auth, old package structure or GPL code into replacement |
| Developer repo only | 85 paths: replacement Android, new iOS behavior, tests and developer review material. Review privacy and provenance before publication |
| Identical | 148 paths, verified by Git content object and mode/type identity |
| Modified | 53 paths, including licenses, READMEs, iOS player and shared data |
| Potential conflict | Licensing narrative and stale docs do not agree across snapshots; Android build configuration targets different architectures |
| Potential regression | Replacement loses old Android capabilities and tests; current parity docs describe deleted classes. iOS starter autoapproval contradicts the approval promise |
| Security relevant | Auth removal is beneficial; missing content packaging, player navigation/lifecycle, automatic approvals, stale cache visibility and signing metadata need action |
| License relevant | GPL fork ancestry is present publicly; a new MPL declaration requires independent provenance evidence. Preserve original notices and authorship |

Content, branding and platform directories already form a monorepo. They are retained; no directory reshuffle or shared-UI framework is warranted. Work proceeds on `audit/pre-public-release` from the developer root, with local incremental fixes. No GitHub-only application code has been copied in.

History decision: retain existing original histories privately; preserve important history and licensing provenance in any eventual public migration. Do not manufacture a sequence of historical feature commits. No secret-driven rewrite is currently justified by scanner findings; do not rewrite merely for appearance. A final canonical-history plan depends on provenance/publication review. No merge, rebase, squash, branch deletion, force push or remote modification has occurred.
