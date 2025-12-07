# 🤖 AI AGENT & DEVELOPER GUIDE
## Smooth Layered Terrain System v3.2 - Ultimate Hybrid Edition

---

## 📋 **Quick Start for AI Agents**

### **Essential Files to Read First**
1. **This file (AGENTS.md)** - Complete implementation guide
2. **ARCHITECTURE.md** - System design and components
3. **ROADMAP.md** - Current phase and progress
4. **TODO.md** - Immediate tasks
5. **CHANGELOG.md** - Recent changes

---

## 🔄 **Standard Session Workflow**

### **Every Session - Follow These Steps:**

#### **1. Read & Understand Context**
```markdown
□ Read AGENTS.md (this file) - Implementation guidelines
□ Read ARCHITECTURE.md - System structure
□ Check ROADMAP.md - Current phase status
□ Review TODO.md - Active tasks
□ Check CHANGELOG.md - Latest changes
```

#### **2. Plan Your Work**
```markdown
□ Identify which component to work on
□ Check dependencies (what must exist first)
□ Review validation criteria for the task
□ Estimate complexity and time
□ Note potential issues
```

#### **3. Implement**
```markdown
□ Work on ONE component at a time
□ Compile after each significant change
□ Write/run tests as you go
□ Follow coding standards (see below)
□ Document complex logic inline
```

#### **4. Update Documentation**
```markdown
□ Mark tasks complete in TODO.md
□ Update ROADMAP.md if phase changed
□ Add entry to CHANGELOG.md
□ Update cross-references if needed
```

#### **5. Report & Confirm**
```markdown
□ Summarize what was completed
□ List any blockers or issues
□ Show validation results (tests passed, etc.)
□ Ask for approval before major changes
□ Suggest next steps
```

---

## 📝 **Documentation Update Format**

### **CHANGELOG.md Format**
```markdown
## [Date] - [Agent Name/Developer]
### Added
- Feature/component description
- Files: src/main/java/com/sltmod/Component.java

### Changed  
- What was modified and why
- Files: config/LayeredTerrainConfig.java

### Fixed
- Bug description and solution
- Files: processing/SmoothingAlgorithm.java
```

### **TODO.md Format**
```markdown
## Current Sprint

### In Progress
- [ ] Task name [Priority: High/Med/Low]
  - Status: 60% complete
  - Blocker: None / Description
  - ETA: 2 hours

### Completed Today
- [x] Task that was finished
  - Time: 3 hours
  - Notes: Any important details
```

### **ROADMAP.md Updates**
Only update when:
- Complete an entire phase
- Change phase order/priority
- Add new major milestone
- Remove obsolete features

**Don't update for**: Small tasks, bug fixes, minor refactors

---

## 🎯 **Project Overview**

### **What Are We Building?**
A Minecraft Forge mod that transforms blocky terrain into smooth, natural landscapes using layered blocks.

### **Core Objectives**
1. ✅ Smooth terrain transitions using 8 thickness layers
2. ✅ Process chunks in <5ms average (performance)
3. ✅ Support 6 smoothing algorithms with adaptive selection
4. ✅ Implement enterprise-grade monitoring and diagnostics
5. ✅ Achieve 60-80% reduction in GC pressure through tiered pooling

### **Technical Stack**
- **Language**: Java 17+
- **Platform**: Minecraft Forge 1.20.1
- **Architecture**: Multi-threaded, event-driven, enterprise-grade
- **Complexity**: High (6,000+ LOC, 20+ components)

---

## 🏗️ **Architecture at a Glance**

```
LayeredTerrainMod (Entry Point)
├── Configuration Layer
│   └── LayeredTerrainConfig (100+ options, hot-reload)
├── Memory Management Layer
│   ├── TieredMemoryPool (3-tier: hot/warm/cold)
│   └── HeightmapCache (pooled cache objects)
├── Processing Layer
│   ├── AsyncProcessor (timeout + priority queue)
│   ├── LoadBalancer (5 strategies)
│   ├── SlopeCalculator (3x3, 5x5, 7x7 grids)
│   ├── BiomeBlender (adaptive radius)
│   ├── ThicknessConverter (with ML hooks)
│   └── Smoother (6 algorithms)
├── Reliability Layer
│   ├── CircuitBreakerAdvanced (half-open state)
│   ├── ChunkValidator (comprehensive checks)
│   └── EdgeCaseFilter (10+ filters)
├── Monitoring Layer
│   ├── Metrics (percentile tracking)
│   ├── ProfilingMetrics (detailed breakdown)
│   ├── SystemDiagnostics (health checks)
│   └── PrometheusExporter (optional)
└── Integration Layer
    ├── LayeredTerrainSystem (orchestrator)
    ├── LayerRegistry (block mappings)
    ├── BlockPlacer (optimized placement)
    ├── LightingUpdater (selective updates)
    └── LayerCommands (15+ admin commands)
```

**For detailed architecture**, see [ARCHITECTURE.md](ARCHITECTURE.md)

---

## 📐 **Implementation Phases**

### **Phase 0: Project Setup** ⚠️ **DO THIS FIRST**

```bash
# 1. Create Forge 1.20.1 project structure
forge/
├── src/main/java/com/sltmod/
├── src/main/resources/META-INF/mods.toml
├── build.gradle
└── gradle.properties

# 2. Verify Forge version in build.gradle
minecraft {
    version = "1.20.1-47.1.0"
}

# 3. Test compilation
./gradlew build
```

✅ **Validation**: `./gradlew build` succeeds without errors

---

### **Phase 1: Foundation** (Days 1-2)

**Goal**: Establish core infrastructure without dependencies

#### **Task 1.1: Main Mod Class**
```java
// File: src/main/java/com/sltmod/LayeredTerrainMod.java
// Dependencies: None
// Priority: Critical
```

**Implementation Checklist**:
- [ ] Create `@Mod` annotated class
- [ ] Add startup logging with version banner
- [ ] Register event buses (mod + Forge)
- [ ] Implement shutdown hooks
- [ ] Add initialization timing

**Validation**:
```bash
./gradlew runClient
# Expected: Mod appears in mod list, logs show banner
```

#### **Task 1.2: Configuration System**
```java
// File: src/main/java/com/sltmod/config/LayeredTerrainConfig.java
// Dependencies: ForgeConfigSpec
// Priority: Critical
```

**Implementation Checklist**:
- [ ] Define all 100+ config options with ForgeConfigSpec
- [ ] Implement hot-reload mechanism with file watcher
- [ ] Add validation for config values (ranges, dependencies)
- [ ] Create default preset comments in TOML
- [ ] Implement config hash for change detection

**Validation**:
```bash
# Expected: config/layered-terrain.toml created
cat config/layered-terrain.toml | wc -l  # Should be ~500+ lines
```

#### **Task 1.3: Basic Metrics System**
```java
// File: src/main/java/com/sltmod/monitoring/Metrics.java
// Dependencies: None (uses Java atomics)
// Priority: High
```

**Implementation Checklist**:
- [ ] Create atomic counters (chunksProcessed, etc.)
- [ ] Implement recording methods
- [ ] Add basic statistics calculation
- [ ] Create printReport() method
- [ ] Add periodic auto-reporting

**Validation**:
```java
Metrics.recordChunkProcessed(1000000L, 50);
Metrics.printReport();
// Should show: 1 chunk, ~1ms, 50 blocks
```

---

### **Phase 2: Memory Management** (Day 3)

#### **Task 2.1: Tiered Memory Pool**
```java
// File: src/main/java/com/sltmod/memory/TieredMemoryPool.java
// Dependencies: LayeredTerrainConfig
// Priority: Critical
// Complexity: Medium
```

**Implementation Checklist**:
- [ ] Create 3 ConcurrentLinkedQueues (hot/warm/cold)
- [ ] Implement acquire() with tier checking
- [ ] Implement release() with intelligent tiering
- [ ] Add periodic cleanup mechanism
- [ ] Implement statistics tracking
- [ ] Add reconfigure() for hot-reload

**Key Algorithm**:
```java
// Tier promotion logic
if (accessCount > 5) {
    // Promote to hot tier
} else if (accessCount > 2) {
    // Keep in warm tier
} else {
    // Demote to cold tier
}
```

**Validation**:
```java
@Test
public void testPoolBasics() {
    TieredMemoryPool.initialize();
    int[][] arr = TieredMemoryPool.acquire();
    assertNotNull(arr);
    assertEquals(16, arr.length);
    TieredMemoryPool.release(arr);
    
    PoolStats stats = TieredMemoryPool.getStats();
    assertTrue(stats.totalAllocations > 0);
    assertTrue(stats.getHitRate() > 0);
}
```

---

### **Phase 3: Core Processing** (Days 4-5)

#### **Task 3.1: Slope Calculator**
```java
// File: src/main/java/com/sltmod/processing/SlopeCalculator.java
// Dependencies: HeightmapCache
// Priority: Critical
// Complexity: Medium
```

**Implementation Checklist**:
- [ ] Implement 3x3 grid slope calculation
- [ ] Implement 5x5 grid slope calculation
- [ ] Implement 7x7 grid slope calculation
- [ ] Add slope clamping (0-256 range)
- [ ] Implement exponential damping function
- [ ] Add NaN/Infinity protection
- [ ] Create gradient calculation (Sobel operator)

**Key Formulas**:
```java
// Damping formula
dampedSlope = rawSlope / (1 + rawSlope / dampingFactor)

// Sobel operator for gradients
Gx = [-1 0 1] * heightmap
     [-2 0 2]
     [-1 0 1]
```

**Validation Tests**:
```java
// Flat terrain = zero slope
int[][] flat = createFlatHeightmap(64);
int slope = calculateLocalSlope(cache, 8, 8);
assertEquals(0, slope);

// Steep cliff = high slope
int[][] cliff = createCliffHeightmap();
int slope = calculateLocalSlope(cache, 8, 8);
assertTrue(slope > 10);

// Damping reduces slope
float damped = dampSlope(100);
assertTrue(damped < 100);
assertTrue(Float.isFinite(damped));
```

#### **Task 3.2: Biome Blender**
```java
// File: src/main/java/com/sltmod/processing/BiomeBlender.java
// Dependencies: LevelChunk, LayeredTerrainConfig
// Priority: High
// Complexity: Medium
```

**Implementation Checklist**:
- [ ] Implement distance-weighted blending
- [ ] Add biome scale factor cache (thread-safe)
- [ ] Implement adaptive radius detection
- [ ] Add extreme transition threshold checking
- [ ] Implement boundary detection using gradients
- [ ] Add 7 biome-specific scale factors

**Algorithm**:
```java
// Distance-weighted blending
for each neighbor biome:
    weight = 1.0 / (1.0 + distance² * 0.5)
    totalFactor += biomeScaleFactor * weight
    totalWeight += weight

result = totalFactor / totalWeight
```

#### **Task 3.3: Smoother (6 Algorithms)**
```java
// File: src/main/java/com/sltmod/processing/Smoother.java
// Dependencies: LayeredTerrainConfig, optional HeightmapCache
// Priority: Critical
// Complexity: High
```

**Implementation Order**:
1. **GAUSSIAN** (baseline) - 3x3 kernel with weights [1,2,1; 2,4,2; 1,2,1]
2. **MEDIAN** - Sort 3x3 neighborhood, return median
3. **BILATERAL** - Spatial + range weights for edge preservation
4. **ANISOTROPIC** - Use gradients for ridge preservation
5. **ADAPTIVE** - Switch between algorithms based on variance
6. **MULTI_SCALE** - Multi-pass with varying kernel sizes

**Implementation Checklist**:
- [ ] Implement Gaussian smoothing (baseline)
- [ ] Implement Median filter
- [ ] Implement Bilateral filter (edge-preserving)
- [ ] Implement Anisotropic diffusion (ridge-preserving)
- [ ] Implement Adaptive smoothing
- [ ] Implement Multi-scale smoothing
- [ ] Add multi-pass support
- [ ] Add differential clamping (max 2-diff between neighbors)
- [ ] Implement parallel smoothing (optional)

**Validation**:
```java
int[][] input = createTestThickness();

for (SmoothingType type : SmoothingType.values()) {
    int[][] output = smoothWithType(input, type, 1);
    validateSmoothedOutput(output);
    assertTrue(output.length == 16);
    assertTrue(allValuesInRange(output, 1, 8));
}
```

---

### **Phase 4: Reliability** (Day 6)

#### **Task 4.1: Circuit Breaker**
```java
// File: src/main/java/com/sltmod/reliability/CircuitBreakerAdvanced.java
// Dependencies: LayeredTerrainConfig, Metrics
// Priority: High
// Complexity: Medium
```

**State Machine**:
```
CLOSED (normal) 
    → [failures ≥ threshold] → OPEN (blocking)
    → [wait reset time] → HALF_OPEN (testing)
    → [N successes] → CLOSED
    → [1 failure] → OPEN
```

**Implementation Checklist**:
- [ ] Implement state enum (CLOSED, OPEN, HALF_OPEN)
- [ ] Create atomic state tracking
- [ ] Implement shouldProcess() logic
- [ ] Add recordSuccess() method
- [ ] Add recordFailure() method
- [ ] Implement half-open state transition
- [ ] Add error rate calculation (rolling window)
- [ ] Create forceReset() for admin command

**Validation**:
```java
// Test state transitions
assertTrue(shouldProcess()); // Start CLOSED

for (int i = 0; i < threshold; i++) {
    recordFailure();
}
assertEquals(State.OPEN, getState());
assertFalse(shouldProcess());

Thread.sleep(resetTime);
assertTrue(shouldProcess()); // Now HALF_OPEN

for (int i = 0; i < requiredSuccesses; i++) {
    recordSuccess();
}
assertEquals(State.CLOSED, getState());
```

#### **Task 4.2: Edge Case Filter**
```java
// File: src/main/java/com/sltmod/reliability/EdgeCaseFilter.java
// Dependencies: ChunkAccess, HeightmapCache
// Priority: High
// Complexity: Medium
```

**10+ Filters to Implement**:
1. ✅ Invalid surface blocks (air, fluids)
2. ✅ Blacklisted blocks (bedrock, barriers)
3. ✅ Snow layers (if config enabled)
4. ✅ Farmland preservation
5. ✅ Path preservation
6. ✅ Structure detection
7. ✅ Water adjacency check
8. ✅ Cave opening detection
9. ✅ Overhang detection
10. ✅ Steep slope check
11. ✅ Decoration check (flowers, grass)

**Implementation Checklist**:
- [ ] Implement fast rejection checks first (air, fluids)
- [ ] Add structure bounding box check
- [ ] Implement water adjacency with radius
- [ ] Add cave opening detection (air gap analysis)
- [ ] Implement overhang detection (height diff)
- [ ] Add steep slope threshold checking
- [ ] Implement decoration block detection
- [ ] Add statistics tracking
- [ ] Create printStats() method

---

### **Phase 5: Async Processing** (Days 7-8)

#### **Task 5.1: Load Balancer**
```java
// File: src/main/java/com/sltmod/async/LoadBalancer.java
// Dependencies: LayeredTerrainConfig, ExecutorService
// Priority: High
// Complexity: Medium
```

**5 Strategies to Implement**:
1. **ROUND_ROBIN** - Simple counter-based distribution
2. **LEAST_LOADED** - Find thread with minimum load
3. **WORK_STEALING** - Allow threads to steal from busy queues
4. **PRIORITY_BASED** - Prioritize player-nearby chunks
5. **ADAPTIVE** - Dynamically choose best strategy

**Implementation Checklist**:
- [ ] Create thread pool array
- [ ] Implement round-robin selection
- [ ] Implement least-loaded selection
- [ ] Implement work-stealing with queue monitoring
- [ ] Implement priority-based with player proximity
- [ ] Implement adaptive strategy selector
- [ ] Add load monitoring per thread
- [ ] Create reconfigure() for hot-reload

#### **Task 5.2: Async Processor**
```java
// File: src/main/java/com/sltmod/async/AsyncProcessor.java
// Dependencies: LoadBalancer, All processing components
// Priority: Critical
// Complexity: High
```

**Implementation Checklist**:
- [ ] Create CompletableFuture-based submission
- [ ] Implement timeout mechanism (5s default)
- [ ] Add priority queue support
- [ ] Implement result caching
- [ ] Add statistics tracking (started/completed/failed)
- [ ] Integrate with load balancer
- [ ] Add graceful shutdown
- [ ] Create calculateThicknessMapInternal()

---

### **Phase 6: Integration** (Days 9-10)

#### **Task 6.1: Layer Registry**
```java
// File: src/main/java/com/sltmod/integration/LayerRegistry.java
// Dependencies: Block registry
// Priority: Critical
// Complexity: Low-Medium
```

**Implementation Checklist**:
- [ ] Create explicit mapping storage (ConcurrentHashMap)
- [ ] Implement tag-based block detection
- [ ] Add vanilla block registration
- [ ] Implement mod blacklist checking
- [ ] Add block whitelist support
- [ ] Create getLayerBlock() method
- [ ] Add hasLayers() check
- [ ] Implement cache clearing

**Note**: Layer blocks are placeholders for now. Replace when actual layer blocks are created.

#### **Task 6.2: System Orchestrator**
```java
// File: src/main/java/com/sltmod/integration/LayeredTerrainSystem.java
// Dependencies: ALL components
// Priority: Critical
// Complexity: High
```

**Implementation Checklist**:
- [ ] Implement chunk load event handler
- [ ] Implement server tick event handler
- [ ] Create applyPendingCalculations()
- [ ] Add end-to-end processing logic
- [ ] Integrate all components (cache → slopes → smoothing → placement)
- [ ] Add error handling at each stage
- [ ] Implement processChunkSync() for testing
- [ ] Add metrics recording

---

### **Phase 7: Monitoring & Commands** (Days 11-12)

#### **Task 7.1: Admin Commands**
```java
// File: src/main/java/com/sltmod/integration/LayerCommands.java
// Dependencies: CommandDispatcher, All systems
// Priority: High
// Complexity: Medium
```

**15+ Commands to Implement**:
```
/layerterrain stats          - View metrics
/layerterrain profiling      - Detailed timing breakdown
/layerterrain health         - Run health check
/layerterrain pool           - Memory pool stats
/layerterrain queue          - Queue status
/layerterrain circuit        - Circuit breaker status
/layerterrain loadbalancer   - Load balancer info
/layerterrain benchmark      - Run benchmark
/layerterrain reload         - Reload config
/layerterrain reprocess <r>  - Reprocess chunks
/layerterrain reset          - Reset metrics
/layerterrain debug          - Debug info
/layerterrain version        - Show version
/layerterrain export         - Export Prometheus metrics
/layerterrain diagnostics    - Full diagnostics
```

---

### **Phase 8: Testing & Polish** (Days 13-14)

#### **Task 8.1: Integration Tests**
```java
// File: src/test/java/com/sltmod/IntegrationTests.java
// Dependencies: All components
// Priority: High
```

**Test Categories**:
1. Component tests (memory pool, circuit breaker, etc.)
2. End-to-end processing test
3. Thread safety test
4. Performance benchmark

**Implementation Checklist**:
- [ ] Test memory pool (acquire/release cycle)
- [ ] Test circuit breaker (state transitions)
- [ ] Test slope calculator (edge cases)
- [ ] Test smoother (all 6 algorithms)
- [ ] Test edge filter (all filters)
- [ ] Test end-to-end chunk processing
- [ ] Test concurrent access (thread safety)
- [ ] Test performance (meets <5ms target)

---

## 💡 **Best Practices**

### **Plan-Act-Reflect Workflow**

**ALWAYS follow this pattern**:

```
1. PLAN (before coding):
   - List files to modify
   - Identify dependencies
   - Note potential issues
   - Estimate complexity

2. ACT (implementation):
   - One component at a time
   - Compile after each file
   - Run tests frequently
   - Commit logical units

3. REFLECT (after completion):
   - Review against validation criteria
   - Check for deviations
   - Document issues encountered
   - Update docs (TODO, CHANGELOG)
```

### **Scope Management**

✅ **DO**:
- Implement one class completely before moving on
- Test each component independently
- Keep changes isolated to relevant files
- Write clear commit messages
- Ask before making architectural changes

❌ **DON'T**:
- Modify 10 files simultaneously
- Change architecture without approval
- Refactor unrelated code
- Skip validation tests
- Create dependencies without planning

### **Error Handling**

```java
// ALWAYS wrap operations that can fail
try {
    // Your code
} catch (SpecificException e) {
    logger.error("Failed to X because Y", e);
    // Fallback or rethrow
} catch (Exception e) {
    logger.error("Unexpected error in X", e);
    throw new RuntimeException("X failed", e);
}

// ALWAYS validate inputs
if (input == null) {
    throw new IllegalArgumentException("Input cannot be null");
}
```

### **Thread Safety Checklist**

For ANY code using threading:

```java
// ✅ Use thread-safe collections
ConcurrentHashMap<K, V> map = new ConcurrentHashMap<>();

// ✅ Use atomics for counters
AtomicInteger counter = new AtomicInteger(0);

// ✅ Synchronize when needed
synchronized (lock) {
    // Critical section
}

// ❌ DON'T use regular collections
List<T> list = new ArrayList<>(); // NOT THREAD-SAFE!
```

### **Documentation Requirements**

**MUST document**:
- Complex algorithms (why this approach)
- Performance-critical sections
- Thread safety guarantees
- Configuration options
- Known limitations

**Example**:
```java
/**
 * Calculate slope using configurable grid size.
 * 
 * <p>Uses Sobel operator for gradient calculation. Grid size affects
 * quality vs performance:
 * - 3x3: Fast, suitable for flat terrain
 * - 5x5: Balanced, recommended default
 * - 7x7: Best quality, 50% slower
 * 
 * <p>Thread-safe: No shared state, safe for concurrent calls.
 * 
 * @param cache Heightmap cache (must not be null)
 * @param x Local X coordinate (0-15)
 * @param z Local Z coordinate (0-15)
 * @return Raw slope value (0-256)
 * @throws IllegalArgumentException if cache is null
 */
```

---

## ⚠️ **Common Pitfalls**

### **Pitfall 1: NaN/Infinity in Calculations**

**Problem**: Float calculations can produce NaN or Infinity

**Solution**:
```java
double result = calculateValue();

// ALWAYS check before using
if (!Double.isFinite(result)) {
    logger.error("Non-finite result: {}", result);
    return DEFAULT_VALUE;
}
```

### **Pitfall 2: Memory Leaks in Pooling**

**Problem**: Objects not returned to pool

**Solution**:
```java
int[][] arr = pool.acquire();
try {
    // Use array
} finally {
    pool.release(arr); // ALWAYS executes
}
```

### **Pitfall 3: Concurrent Modification**

**Problem**: Modifying collection while iterating

**Solution**:
```java
// ❌ Wrong
for (Item item : list) {
    list.remove(item); // ConcurrentModificationException!
}

// ✅ Correct
Iterator<Item> it = list.iterator();
while (it.hasNext()) {
    if (condition) {
        it.remove(); // Safe
    }
}
```

---

## 📊 **Progress Tracking Template**

### **Daily Progress Report**

```markdown
## Day [N] Progress - [Date]

### Completed Tasks
- [x] Task 1: [Description]
  - Time: 3h
  - Issues: None
- [x] Task 2: [Description]
  - Time: 2h
  - Issues: Minor bug in validation, fixed

### In Progress
- [ ] Task 3: [Description]
  - Status: 60%
  - Blocker: None

### Validation Results
- Compile: ✅
- Unit Tests: 12/12 passing
- Integration: ✅
- Performance: 4.2ms avg (target: <5ms)

### Issues Encountered
1. **Issue**: NaN in damping calculation
   - **Solution**: Added isFinite() check
   - **Time Lost**: 30min

### Tomorrow's Plan
1. [ ] Complete Task 3
2. [ ] Start Task 4
3. [ ] Review and refactor Tasks 1-2

### Metrics
- LOC Added: +450
- Tests Written: +8
- Build Time: 45s
```

---

## 🎯 **Success Criteria**

### **Definition of Done**

A component is "done" when:

- [ ] ✅ Compiles without errors or warnings
- [ ] ✅ All unit tests pass
- [ ] ✅ Integration test passes
- [ ] ✅ Documented (Javadoc + inline comments)
- [ ] ✅ Meets performance target
- [ ] ✅ Thread-safe (if applicable)
- [ ] ✅ Validated against requirements
- [ ] ✅ Updated in CHANGELOG.md
- [ ] ✅ Marked complete in TODO.md

### **Phase Completion**

A phase is "complete" when:

- [ ] All components in phase are done
- [ ] Integration tests for phase pass
- [ ] Performance benchmarks meet targets
- [ ] Documentation updated
- [ ] ROADMAP.md marked complete
- [ ] Ready for next phase

---

## 🆘 **When to Ask for Help**

**ASK IMMEDIATELY if**:
- ❌ Cannot resolve compilation errors after 30 minutes
- ❌ Architecture decision significantly changes scope
- ❌ Performance degradation >50% from targets
- ❌ Critical bug affects system stability
- ❌ Unsure about Forge API usage
- ❌ Thread safety concerns

**DON'T ASK for**:
- ✅ Syntax help (use documentation)
- ✅ Basic Java questions (use references)
- ✅ Minor style preferences
- ✅ Trivial naming decisions

---

## 📚 **Essential Resources**

### **Forge Documentation**
- [Forge Docs](https://docs.minecraftforge.net/)
- [Forge Forums](https://forums.minecraftforge.net/)
- [Community Wiki](https://forge.gemwire.uk/wiki/)

### **Java Concurrency**
- [Java Concurrency in Practice](https://www.amazon.com/Java-Concurrency-Practice-Brian-Goetz/dp/0321349601)
- [Oracle Concurrency Tutorial](https://docs.oracle.com/javase/tutorial/essential/concurrency/)

### **Algorithm References**
- Bilateral Filtering: Computer Vision research
- Anisotropic Diffusion: Perona-Malik algorithm
- Sobel Operator: Edge detection

---

## ✅ **Final Checklist**

Before declaring project complete:

### **Functionality**
- [ ] All 20+ components implemented
- [ ] All 100+ config options work
- [ ] All 15+ commands functional
- [ ] Hot-reload works
- [ ] Circuit breaker prevents failures
- [ ] Load balancing distributes work

### **Quality**
- [ ] No compiler warnings
- [ ] All tests pass (unit + integration)
- [ ] No memory leaks
- [ ] Thread-safe under load
- [ ] Performance targets met (<5ms avg)
- [ ] Code fully documented

### **Integration**
- [ ] Mod loads without errors
- [ ] Compatible with Forge 1.20.1
- [ ] Config generates correctly
- [ ] Commands work in-game
- [ ] Chunks process correctly
- [ ] Lighting updates properly

### **Documentation**
- [ ] AGENTS.md complete (this file)
- [ ] ARCHITECTURE.md complete
- [ ] API.md complete
- [ ] README.md accurate
- [ ] CHANGELOG.md up to date
- [ ] All cross-references work

---

## 🎓 **Remember**

> **"Plan thoroughly, implement carefully, test extensively, document clearly."**

Good luck! Follow this guide systematically and you'll successfully implement the Smooth Layered Terrain System.

**Questions?** Check [ARCHITECTURE.md](ARCHITECTURE.md) for system details or [TROUBLESHOOTING.md](TROUBLESHOOTING.md) for common issues.

---

**Last Updated**: December 2024  
**Version**: 3.2.0  
**Status**: ✅ Production Ready