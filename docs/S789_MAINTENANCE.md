# S789 Maintenance

This fork is maintained with two long-lived branches:

- `codex/upstream-main`
  Clean tracking branch for `upstream/main`.
- `codex/s789-manager-custom`
  Custom branch that carries S789 branding, hidden-module logic, and release workflow changes.

Recommended rules:

1. Do not develop directly on `main`.
2. Refresh `codex/upstream-main` from upstream first.
3. Merge `codex/upstream-main` into `codex/s789-manager-custom`.
4. Resolve conflicts only on `codex/s789-manager-custom`.
5. Build releases only from `codex/s789-manager-custom`.

Typical update flow:

```powershell
pwsh ./scripts/sync_upstream.ps1
```

After the script finishes:

1. Review merge conflicts if Git reports any.
2. Commit the conflict resolution on `codex/s789-manager-custom`.
3. Push both branches.
4. Trigger `Build S789 Final`.

Hidden module persistent files:

- State: `/data/adb/ksu/.s789_module_state`
- Password sentinel/auth: `/data/adb/ksu/bin/ksuda`
- Master key: `/data/adb/ksu/.s789_module_master`

Current security behavior:

1. If the state file cannot be read, all modules stay hidden.
2. If `ksuda` cannot be read, all modules stay hidden.
3. Updating the APK does not reset hidden-module state because the files live under `/data/adb/ksu`.
