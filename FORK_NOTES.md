# Fork notes

This repository is a close fork of Tessera with a Capacitor adapter kept in the
separate `capacitor/tessera-mrz/` package.

## Baseline

- Upstream commit: `68771e692a1e593ce853f7962937ad301121f6b3`
- Tessera version: `0.5.0`
- Baseline date: 2026-08-27
- `upstream` tracks the canonical source repository.
- `origin` tracks this fork.

No Tessera core behavior was changed before the adapter was added. Fork-specific
changes should stay under `capacitor/` unless a native SDK change is unavoidable.

## Synchronizing

Fetch `upstream`, merge its `main` branch into a short-lived integration branch,
then run the Tessera and Capacitor verification suites before merging to `main`.
Do not mix synchronization conflict fixes with unrelated adapter features.
