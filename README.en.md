# simxz-chat

[![Version](https://img.shields.io/badge/version-1.0-blue)](https://github.com/qwe123535/simxz-chat/releases)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![Status](https://img.shields.io/badge/status-not_long_term_maintained-orange)](#maintenance-status)

**[中文](README.md) | English**

An Android chat client for OpenAI-compatible endpoints, written in Jetpack Compose. Conversations stay on the phone.

This is an **unofficial fork** of [Simple-Chat](https://github.com/Pokanshuu/Simple-Chat). The package name is `com.simxz.chat`, so it installs alongside the original. There is no affiliation between the two; this repository is the source of truth for its own features and bugs.

> **Maintenance status: not long-term maintained.**
> This is a personal build tuned to how I use it. It is not a product and has no release schedule.
> Updates are occasional. Issues and PRs are not guaranteed a reply, and nothing promises compatibility with older databases or settings.
> For long-term use or further development, start from the upstream project instead.

---

## What this fork changes

On top of upstream:

**Interface**
- Bottom tab bar: Chat / Settings as peers, cross-faded
- Settings root is a two-column tile grid showing the current value on each tile
- **DeepSeek style**: a switch in settings toggles "Original / DeepSeek". The latter redraws the chat screen after DeepSeek's official app — centered title, a filled input field instead of a white card, 22dp user bubbles with a tightened bottom-right corner, looser message spacing, no assistant bubble
- Custom colors: accent, own bubble, page, card and body text, each editable with an HSV picker
- Reading options: body line height 0.9×–1.6×, optional monospace body, code-block wrapping
- Minimal mode and custom waiting phrases

**Input**
- Long-press the empty input to open the conversation panel (replaces the old gear button)
- Send-on-Enter switch (off by default — on phones Enter usually means newline)
- `/` shortcuts: keep your own list, typing `/` pops it up
- Multi-select attachments (up to 20), with size labels, reordering and a full-screen viewer
- Quote reply: long-press a message → quote; the quote shows above the input

**Conversation**
- Switching conversations no longer cancels generation; it continues in the background and posts a notification when done
- Reply, continue and stop straight from the notification
- Per-conversation status dots: spinner = generating, green = finished unseen, amber = failed
- Collapsible per-message token usage (input / output)
- Edit history: previous versions are kept (up to 20) and can be expanded
- Context budget bar above the input; turns amber past 85%
- Manual context window (auto / 32K / 64K / 128K / 256K / 512K / 1M) — custom and local models no longer default to 128K
- Automatic context compaction at 85% of the window, and again if the server rejects the request as too long
- "Branch 2/3" in the top bar
- Reasoning always visible — no longer hidden behind a settings switch
- Favorites for both messages and conversations
- Long-image sharing: render a conversation to PNG
- Tool calling: two fully local tools (`get_current_time`, `calculator`) over standard function calling

**Settings**
- Haptics with separate switches for send, long-press and delete
- Storage & cache breakdown (database, attachments, orphans, cache) with one-tap cleanup
- App lock (system credential prompt) and screenshot blocking
- Background keep-alive: foreground service, wake lock, battery-optimization exemption, silent audio
- Memories, error log, usage stats per model
- First-run onboarding: key → model → done, skippable at every step

**Other**
- A "Local" provider preset defaulting to `http://127.0.0.1:11434/v1`, usable without an API key (cleartext HTTP allowed)
- Recent searches, plain-text (.txt) export
- Storage cleanup only removes unreferenced attachments and cache — never the database

## Download

Grab the APK from [Releases](https://github.com/qwe123535/simxz-chat/releases). Current version: **1.0**.

Package name `com.simxz.chat`; it coexists with the original. The APK is debug-signed (no release keystore is configured), so **uninstall any older build with the same package name first**.

## Building

You need JDK 17+ and Android SDK (platform 37, build-tools 37.0.0):

```bash
./gradlew assembleDebug
```

Output lands in `app/build/outputs/apk/debug/`.

The repo also ships a GitHub Actions workflow (`.github/workflows/build.yml`): pushing to `main` or triggering `workflow_dispatch` runs `assembleRelease` + `assembleDebug` on a runner and uploads the APKs as artifacts. I don't run full release builds on low-memory hardware — CI is the main way this gets built.

## Known limits

- No built-in browser; links go to the system
- No accounts, cloud sync or automatic backups
- The database is not encrypted (conversations are plain files in app-private storage, protected by app lock and screenshot blocking)
- Incognito mode, cross-conversation message search and batch message operations are not implemented
- A few settings currently store a value without affecting behaviour (auto-expanded process blocks, preserved reasoning, some keep-alive options); these will either be wired up or removed from the UI

## Development & credits

This fork:

- **qwe123535** — maintenance, UI and feature work

Upstream and dependencies:

- **DeepSeek** — model capabilities and API (reasoning traces, context windows and function calling all come from it), and the design reference for the DeepSeek interface style
- **[Simple-Chat](https://github.com/Pokanshuu/Simple-Chat)** by **Pokanshuu** — the foundation: most of the architecture, protocol layer, rendering and data layer come from upstream

The DeepSeek interface style follows the visual spec of [deepseek-app-replica](https://github.com/LiJiaHua1024/deepseek-app-replica) (visual parameters only; none of its code was copied).

## License

[MIT](LICENSE).

Modified from the upstream Simple-Chat under the same MIT license; copyright of the original work belongs to its author.
