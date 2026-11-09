# Codex package compatibility

Install the package with:

```bash
npm install -g @openai/codex
```

Codex uses a bubblewrap sandbox that is not available on Android. This may cause Codex to crash and does not by itself indicate a defect in Arch Terminal.

To use Codex without its sandbox, enter `/permission` in Codex and select **Full Access**. This grants broader permissions, so use it only when you trust the commands and files Codex will work with.

If it still crashes, try starting Codex without the daemon:

```bash
codex --no-daemon
```
