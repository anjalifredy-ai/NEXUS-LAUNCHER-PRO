# NEXUS LAUNCHER PRO

**Custom Minecraft Java Edition launcher + on-device server hosting for Android**  
Engine-first · Own codebase (not a drop-in CS Launcher / Pojav UI fork)

---

## Implemented (server hosting core)

- [x] Gradle + Hilt + Compose shell
- [x] Version manifest (Mojang) + version JSON models
- [x] Download queue + SHA1 helpers
- [x] Launch pipeline skeleton (args, process, logs)
- [x] **On-device server hosting**
  - Server list with **banner cards**
  - Create wizard: version select, software, RAM advisor, EULA
  - Installers: **Vanilla, Paper, Purpur, Fabric, Quilt, Forge, NeoForge**
  - Process start/stop + real console + commands
  - Settings → writes `server.properties` (online-mode, whitelist, PvP, …)
  - Worlds: list / import from launcher saves / set active
  - Plugins/mods: install URL, enable/disable
  - **ViaVersion** optional on Paper/Purpur (multi-client protocol)
  - **Java 8 / 17 / 21 auto-install** (BellSoft Liberica)
  - **Foreground service** notification while server runs
  - **LAN IPv4** helper for same-Wi‑Fi join address

## Not done yet

- Full Microsoft / Ely.by auth UI
- Full client game launch (GLES bridge polish)
- Modrinth/CurseForge browser UI
- Public relay/tunnel
- Voice chat
- Production CI APK pipeline

## Honest limits

- Server runs **on the phone** — not remote hosting
- No 24/7 guarantee (OEM battery killers, thermal)
- Public internet needs a **relay** (CGNAT); LAN works on same Wi‑Fi
- Spigot: use Paper instead (BuildTools too heavy on device)

## Paths

```
/sdcard/NexusLauncher/.minecraft/   # client data
/sdcard/NexusLauncher/servers/      # server instances
/sdcard/NexusLauncher/runtime/      # JRE 8/17/21
```

## Build

Open in Android Studio (SDK 34, JDK 17). Resolve signing env vars for release.

```bash
./gradlew :app:assembleDebug
```

## License

MIT (application code). Upstream Minecraft EULA applies to server jars. Liberica / Paper / Fabric licenses apply to their artifacts.

---

**Note:** CS Launcher V3 is a separate product (CS branding). This repo is **Nexus Launcher PRO**.
