# NEXUS LAUNCHER PRO

**Custom Minecraft Java Edition Launcher Engine for Android**  
Built from scratch · No PojavLauncher / ZalithLauncher fork · 100% own engine  
**Tagline:** PLAY BEYOND LIMITS

---

## Status

🚧 **Engine under active development**  
UI later. Core engine first.

### Roadmap
- [x] Project scaffolding & Gradle (AGP 8.6 + Kotlin 2.0)
- [ ] Authentication (Microsoft OAuth2 + Ely.by + Offline)
- [ ] Version manifest + inheritance resolver
- [ ] Asset & Library download + SHA1 verification
- [ ] JRE management (Liberica 8/17/21)
- [ ] Loaders (Fabric / Forge / Quilt / NeoForge / OptiFine)
- [ ] Launch pipeline (ProcessBuilder + argument builder)
- [ ] Native bridge (OpenGL ES + OpenAL + Input)
- [ ] Mod management (Modrinth + CurseForge)
- [ ] Worlds + Multiplayer (ping, LAN, Realms)
- [ ] Settings + Logging + Self-update
- [ ] GitHub Actions CI/CD

---

## Architecture

```
UI Layer (Compose) → Business Logic → Data Layer → Native Bridge (JNI)
                                              ↓
                                    Minecraft Java Process
```

## Tech Stack

- Kotlin 2.0 + Jetpack Compose + Material 3
- Hilt, Room, DataStore, Retrofit, OkHttp, Coroutines, Timber
- External (runtime download): Liberica JDK, LWJGL3 Android, GL4ES, OpenAL Soft

## License

MIT

---

Built by Rohit / NEXUS team
