# RagdolMod 🪆

**RagdolMod** is a Minecraft Fabric mod for 1.21.1 that replaces vanilla player movement with a full physics-based ragdoll simulation — floppy, unstable, goofy, and momentum-driven.

---

## 🎮 Gameplay Feel

| Normal WASD walking | Barely moves. The body is too weak and floppy to walk properly. |
|---|---|
| **Jump + WASD** | **Launches you with momentum. Chain jumps to build speed.** |
| Direction changes | Difficult — inertia resists sudden reversals. |
| Landing | Creates stumble, body sway, and camera dip. |
| Camera | Rolls with body sway, pitches on impact. |
| Head | Lags behind body rotation (visible in multiplayer). |

Think: **Human Fall Flat × Gang Beasts × Minecraft**.

---

## ⚙️ Physics System

### 1. Verlet / Euler Integration
Each tick (50ms), the engine integrates velocity:
```
v(t+dt) = v(t) + a(t)*dt
x(t+dt) = x(t) + v(t+dt)*dt
```

### 2. Spring-Damper Oscillators
Body tilt, sway, head lag, and camera roll all use spring-damper systems:
```
F = -k*x - b*v
```
- **k** = spring stiffness (restoring force towards equilibrium)
- **b** = damping coefficient (energy dissipation)
- **x** = displacement from equilibrium
- **v** = velocity of the oscillator

### 3. Momentum Preservation
```
p = m*v
Δv = impulse / m
```
Jump impulses are scaled by inverse mass, so heavier configs feel sluggish.

### 4. Quadratic Drag
Horizontal velocity is resisted by air drag:
```
F_drag = -c_d * v * |v|
```
This naturally limits top speed and creates realistic deceleration curves.

### 5. Angular Inertia (Tilt & Sway)
Body tilt uses a moment of inertia approximation:
```
τ = I * α
I ≈ m * r²   (r ≈ 0.3 for player torso)
```

### 6. Head Lag
The head has its own angular spring with lower stiffness, causing it to lag behind body yaw rotation. Visible to other players in multiplayer.

### 7. Stumble System
Hard landings trigger a randomized oscillation burst. Stumble decays using exponential smoothing:
```
intensity(t) = intensity(t-1) * (1 - recovery_rate)
```

---

## 📦 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.1
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) (0.102.0+1.21.1 or newer)
3. Drop `ragdolmod-1.0.0.jar` into your `mods/` folder
4. Launch Minecraft

---

## 🔧 Configuration

Config file: `.minecraft/config/ragdolmod.json`

```json
{
  "springStiffness": 4.5,
  "dampingCoefficient": 1.8,
  "dragCoefficient": 0.3,
  "bodyMass": 1.8,
  "walkSpeedMultiplier": 0.08,
  "jumpImpulse": 1.4,
  "jumpMomentumBoost": 1.6,
  "airControlFactor": 0.15,
  "wobbleIntensity": 1.0,
  "recoverySpeed": 0.6,
  "cameraRollSensitivity": 0.7,
  "landingStumbleThreshold": 0.3,
  "enabled": true
}
```

### Config Guide

| Parameter | Description | Range |
|---|---|---|
| `springStiffness` | How fast the body returns to upright | 1.0–20.0 |
| `dampingCoefficient` | Oscillation damping (higher = less bouncy) | 0.1–5.0 |
| `dragCoefficient` | Air/ground drag on XZ velocity | 0.0–2.0 |
| `bodyMass` | Body mass (affects momentum feel) | 0.5–5.0 |
| `walkSpeedMultiplier` | How fast WASD walking is (keep very low!) | 0.01–0.3 |
| `jumpImpulse` | Vertical jump force multiplier | 0.5–3.0 |
| `jumpMomentumBoost` | Horizontal momentum added on each jump | 0.5–3.0 |
| `airControlFactor` | WASD control while airborne (0=none) | 0.0–1.0 |
| `wobbleIntensity` | Sway/tilt visual intensity | 0.0–2.0 |
| `recoverySpeed` | How fast stumble effects fade | 0.1–2.0 |
| `cameraRollSensitivity` | Camera roll response to body sway | 0.0–2.0 |
| `landingStumbleThreshold` | Minimum fall speed to trigger stumble | 0.1–1.0 |
| `enabled` | Completely disable the mod | true/false |

### Preset Examples

**Ultra Floppy (Gang Beasts mode)**
```json
{
  "springStiffness": 2.0,
  "dampingCoefficient": 0.8,
  "wobbleIntensity": 2.0,
  "jumpMomentumBoost": 2.5,
  "walkSpeedMultiplier": 0.04
}
```

**Slightly Wobbly (subtle effect)**
```json
{
  "springStiffness": 8.0,
  "dampingCoefficient": 3.0,
  "wobbleIntensity": 0.4,
  "jumpMomentumBoost": 1.0,
  "walkSpeedMultiplier": 0.15
}
```

---

## 🏗️ Building from Source

### Requirements
- Java 21 JDK
- Gradle 8+ (wrapper included)

```bash
git clone https://github.com/kintil555/ragdolmod
cd ragdolmod
./gradlew build
```

Output jar: `build/libs/ragdolmod-1.0.0.jar`

### Development Environment
```bash
./gradlew genSources      # Generate Minecraft sources
./gradlew runClient       # Launch test client
./gradlew runServer       # Launch test server
```

---

## 🌐 Multiplayer

- Physics runs **server-side** (authoritative simulation)
- Client receives sync packets at **25 Hz** (every 2 ticks)
- Client interpolates between received states for smooth visuals
- Jump packets are sent client → server with directional input
- Other players see your ragdoll physics visually (head lag, body tilt)

---

## 📁 Project Structure

```
src/
├── main/
│   └── java/com/ragdolmod/
│       ├── RagdolMod.java              # Server entrypoint
│       ├── config/
│       │   └── RagdollConfig.java      # Config system
│       ├── physics/
│       │   ├── RagdollPhysicsEngine.java  # Core physics simulation
│       │   └── PlayerRagdollState.java    # Per-player state tracker
│       ├── network/
│       │   ├── RagdollSyncPacket.java  # Server→Client sync
│       │   └── JumpImpulsePacket.java  # Client→Server jump
│       └── mixin/
│           ├── LivingEntityMixin.java
│           └── ServerPlayerEntityMixin.java
└── client/
    └── java/com/ragdolmod/
        ├── client/
│           ├── RagdolModClient.java    # Client entrypoint
│           └── ClientRagdollCache.java # Received state cache
        └── mixin/client/
            ├── ClientPlayerEntityMixin.java
            ├── GameRendererMixin.java
            └── CameraAccessor.java
```

---

## 📜 License

MIT License — free to use, modify, distribute.

---

## 🙏 Credits

Inspired by:
- Human Fall Flat (No Brakes Games)
- Gang Beasts (Boneloaf)
- Drunken physics mods in the Minecraft community
