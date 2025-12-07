# 🚀 QUICK START GUIDE
## Smooth Layered Terrain System v3.2

Get up and running in 5 minutes!

---

## 📋 **Prerequisites**

```bash
✅ Minecraft 1.20.1
✅ Minecraft Forge 47.1.0+
✅ Java 17 or higher
✅ 2GB+ RAM allocated
```

---

## ⚡ **Installation (1 Minute)**

### **Step 1: Download**
```bash
# Get the latest release
https://github.com/deft-orchestrator/layered-terrain/releases/latest

# Download: layered-terrain-3.2.0.jar
```

### **Step 2: Install**
```bash
# Place JAR in your mods folder
minecraft/
└── mods/
    └── layered-terrain-3.2.0.jar
```

### **Step 3: Start**
```bash
# Start Minecraft with Forge
# The mod will initialize automatically
```

---

## ✅ **Verification (30 Seconds)**

### **In-Game Check**
```bash
# 1. Open chat (T key)
# 2. Type command:
/layerterrain version

# Expected output:
# "Layered Terrain System v3.2.0 - Ultimate Hybrid Edition"
```

### **Check Logs**
```bash
# Look for this in logs/latest.log:
[LayeredTerrain]: ╔═══════════════════════════════════════════════╗
[LayeredTerrain]: ║  LAYERED TERRAIN SYSTEM v3.2.0 STARTED       ║
[LayeredTerrain]: ╚═══════════════════════════════════════════════╝
```

---

## ⚙️ **Basic Configuration (2 Minutes)**

### **Default Settings (Recommended)**
```toml
# config/layered-terrain.toml

[layered_terrain.smoothing]
smoothing_type = "ADAPTIVE"    # Auto-adjusts quality
smoothing_passes = 2            # Balanced smoothing
max_differential = 2            # Smooth transitions

[layered_terrain.performance]
async_processing = true         # Non-blocking
worker_threads = 0              # Auto-detect CPU cores
max_chunks_per_tick = 4         # Balanced throughput
```

**Expected Performance**: 4-5ms per chunk ✅

### **High Performance (Busy Servers)**
```toml
[layered_terrain.smoothing]
smoothing_type = "GAUSSIAN"     # Fastest algorithm
smoothing_passes = 1            # Single pass
max_differential = 2

[layered_terrain.performance]
worker_threads = 8              # More threads
max_chunks_per_tick = 8         # Process more per tick
```

**Expected Performance**: 3-4ms per chunk ⚡

### **High Quality (Screenshots)**
```toml
[layered_terrain.smoothing]
smoothing_type = "BILATERAL"    # Edge-preserving
smoothing_passes = 3            # More smoothing

[layered_terrain.general]
use_5x5_sampling = true         # Better quality

[layered_terrain.biome_blending]
biome_blend_radius = 3          # Smoother transitions
```

**Expected Performance**: 6-8ms per chunk 🎨

---

## 🎮 **Essential Commands**

### **Check Status**
```bash
/layerterrain stats
# Shows: chunks processed, avg time, success rate
```

### **Performance Check**
```bash
/layerterrain health
# Runs system diagnostics
```

### **Reload Config**
```bash
# 1. Edit config/layered-terrain.toml
# 2. Run command:
/layerterrain reload
```

### **Reprocess Area**
```bash
# Reprocess chunks in 5 chunk radius
/layerterrain reprocess 5
```

---

## 🔧 **Troubleshooting (If Something Goes Wrong)**

### **Issue: Mod Not Loading**
```bash
# Check logs/latest.log for errors
grep -i "layered" logs/latest.log

# Common fixes:
1. Verify Forge version (47.1.0+)
2. Check Java version (17+)
3. Remove conflicting mods
```

### **Issue: Slow Performance (>7ms)**
```bash
# 1. Check current performance:
/layerterrain profiling

# 2. Adjust config:
smoothing_type = "GAUSSIAN"    # Faster algorithm
smoothing_passes = 1           # Less passes

# 3. Reload:
/layerterrain reload
```

### **Issue: "Circuit Breaker OPENED"**
```bash
# System detected errors and stopped processing

# Fix:
1. Check logs for error messages
2. Fix underlying issue (memory, config, etc.)
3. Reset circuit:
/layerterrain reset
```

### **Issue: Config Changes Not Working**
```bash
# Enable hot-reload:
[layered_terrain.hot_reload]
enable_hot_reload = true

# Then:
/layerterrain reload
```

---

## 📊 **Performance Expectations**

### **Typical Performance**
```
Scenario            | Avg Time | Quality | Recommended For
--------------------|----------|---------|------------------
High Performance    | 3-4ms    | ⭐⭐   | Busy servers
Balanced (Default)  | 4-5ms    | ⭐⭐⭐ | Most servers
High Quality        | 6-8ms    | ⭐⭐⭐⭐ | Creative/Screenshots
Ultra Quality       | 8-12ms   | ⭐⭐⭐⭐⭐ | Single-player only
```

### **TPS Impact**
```
Chunks/Tick | TPS Impact | Notes
------------|------------|---------------------------
2           | <0.3       | Very safe
4 (default) | <0.5       | Recommended
8           | <1.0       | High-end servers only
16          | <2.0       | Only if you have 16+ cores
```

---

## 🎯 **Next Steps**

### **Learn More**
- 📖 **[Full Manual](README.md)** - Complete documentation
- 🏗️ **[Architecture](docs/ARCHITECTURE.md)** - How it works
- 🛠️ **[Troubleshooting](docs/TROUBLESHOOTING.md)** - Solve problems
- 💻 **[API Guide](docs/API.md)** - Mod integration

### **Get Help**
- 💬 [Discord Server](https://discord.gg/yourserver)
- 🐛 [Report Issues](https://github.com/deft-orchestrator/layered-terrain/issues)
- 💡 [Feature Requests](https://github.com/deft-orchestrator/layered-terrain/discussions)

### **Contribute**
- 🤝 [Contributing Guide](CONTRIBUTING.md)
- 👨‍💻 [Developer Guide](docs/AGENTS.md)

---

## ⚡ **Quick Tips**

### **Optimize for Your Server**
```bash
# 1. Run benchmark to see current performance:
/layerterrain benchmark

# 2. If avg time >7ms:
#    - Reduce smoothing_passes
#    - Use "GAUSSIAN" algorithm
#    - Reduce biome_blend_radius

# 3. If avg time <3ms and TPS good:
#    - Increase smoothing_passes
#    - Try "BILATERAL" algorithm
#    - Increase max_chunks_per_tick
```

### **Monitor Performance**
```bash
# Enable auto-reporting:
[layered_terrain.monitoring]
metrics_interval_seconds = 300  # Report every 5 minutes

# Check logs for periodic reports
```

### **Best Practices**
```
✅ Start with default config
✅ Test in creative mode first
✅ Monitor TPS with /tps command
✅ Adjust one setting at a time
✅ Reload config after changes
✅ Run benchmark after optimization
```

---

## 📸 **See It In Action**

### **Before**
```
████████████████
████████████████  ← Blocky, harsh edges
████████████████
```

### **After**
```
    ████████
  ████████████
████████████████  ← Smooth, natural slopes
████████████████
```

**The difference is dramatic!** 🎨

---

## ✅ **Checklist**

Before reporting issues, check:

- [ ] Latest version installed (3.2.0)
- [ ] Forge 47.1.0+ installed
- [ ] Java 17+ installed
- [ ] Config file exists in `config/`
- [ ] Commands work (`/layerterrain version`)
- [ ] Logs show no errors
- [ ] Performance within expectations

---

## 🎉 **You're All Set!**

Enjoy your smooth terrain! If you need help:
- Check [TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)
- Ask on [Discord](https://discord.gg/yourserver)
- Read [Full Manual](README.md)

**Happy Mining!** ⛏️

---

**Quick Start Guide v3.2.0** | [Full Documentation](README.md) | [Report Issues](https://github.com/deft-orchestrator/layered-terrain/issues)