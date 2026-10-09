# HiveTechModeration 1.0.0

Server-side Forge mod for Minecraft 1.12.2. Client mod is not required.

## Features

- loads active BAN/MUTE actions from the HiveTech moderation API;
- incremental sync by revision;
- persistent local cache for API outages;
- local expiry by `expires_at`;
- blocks banned players on login;
- kicks online players when a new ban arrives;
- cancels `ServerChatEvent` for muted players;
- sends Bridge heartbeat data;
- provides `/htmod status` and `/htmod sync` for operators;
- never logs the Bridge token.

## Build

Requirements: Java 8 and outbound HTTPS access. `build.sh` pins Gradle 4.10.3 because the existing HiveTechKits Gradle 9.x wrapper is not compatible with this ForgeGradle 3.x project.

```bash
cd /opt/hivetech/build/hivetech-moderation
chmod +x build.sh
./build.sh
```

Output:

```text
build/libs/HiveTechModeration-1.0.0.jar
```

## Install

```bash
install -m 0644 build/libs/HiveTechModeration-1.0.0.jar \
  /opt/hivetech/server/mods/HiveTechModeration-1.0.0.jar
```

After the first server start configure:

```text
/opt/hivetech/server/config/hivetech-moderation.cfg
```

Use the values generated in HiveTech Admin -> Servers -> Bridge. Never commit the real `htb_...` token.

API base URL:

```text
https://hivegrid.online/internal/game
```

Endpoints used:

```text
GET  /moderation/snapshot
GET  /moderation/changes?after=<revision>&limit=200
GET  /moderation/player/<uuid>
POST /bridge/heartbeat
```
