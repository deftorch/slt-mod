# 🤝 CONTRIBUTING GUIDE
## Smooth Layered Terrain System v3.2

Thank you for your interest in contributing! This guide will help you get started.

---

## 📋 **Table of Contents**

1. [Code of Conduct](#code-of-conduct)
2. [Getting Started](#getting-started)
3. [Development Setup](#development-setup)
4. [Contribution Workflow](#contribution-workflow)
5. [Coding Standards](#coding-standards)
6. [Testing Requirements](#testing-requirements)
7. [Documentation](#documentation)
8. [Pull Request Process](#pull-request-process)

---

## 📜 **Code of Conduct**

### **Our Standards**

- ✅ Be respectful and inclusive
- ✅ Welcome newcomers and help them learn
- ✅ Focus on constructive feedback
- ✅ Accept criticism gracefully
- ✅ Prioritize the community's best interests

### **Unacceptable Behavior**

- ❌ Harassment or discrimination of any kind
- ❌ Trolling or insulting comments
- ❌ Personal or political attacks
- ❌ Publishing others' private information
- ❌ Spam or off-topic content

---

## 🚀 **Getting Started**

### **What Can You Contribute?**

1. **Code Contributions**
   - New features
   - Bug fixes
   - Performance optimizations
   - New smoothing algorithms

2. **Documentation**
   - Fix typos or unclear explanations
   - Add examples
   - Translate documentation
   - Write tutorials

3. **Testing**
   - Report bugs
   - Test new features
   - Write test cases
   - Performance benchmarks

4. **Design**
   - UI/UX improvements
   - Graphics/icons
   - Configuration presets

### **Finding Issues to Work On**

Look for issues labeled:
- `good first issue` - Great for newcomers
- `help wanted` - Need community help
- `bug` - Bug fixes needed
- `enhancement` - New features
- `documentation` - Docs improvements

---

## 💻 **Development Setup**

### **Prerequisites**

```bash
# Required
- Java 17 or higher
- Gradle 8.0+
- Git
- IDE (IntelliJ IDEA recommended)

# Optional
- Docker (for testing)
- Node.js (for docs generation)
```

### **Clone Repository**

```bash
git clone https://github.com/deft-orchestrator/layered-terrain.git
cd layered-terrain
```

### **Setup Development Environment**

```bash
# Generate IDE files
./gradlew idea      # IntelliJ IDEA
./gradlew eclipse   # Eclipse

# Build project
./gradlew build

# Run client (for testing)
./gradlew runClient

# Run server (for testing)
./gradlew runServer
```

### **Verify Setup**

```bash
# Run tests
./gradlew test

# Expected: All tests pass
# Build time: ~45 seconds
```

---

## 🔄 **Contribution Workflow**

### **Step 1: Create Branch**

```bash
# Update main branch
git checkout main
git pull origin main

# Create feature branch
git checkout -b feature/your-feature-name
# or
git checkout -b fix/bug-description
```

**Branch Naming Convention**:
- `feature/` - New features
- `fix/` - Bug fixes
- `docs/` - Documentation
- `refactor/` - Code refactoring
- `test/` - Adding tests
- `perf/` - Performance improvements

### **Step 2: Make Changes**

```bash
# Make your changes
# Follow coding standards (see below)

# Test your changes
./gradlew test

# Run in development environment
./gradlew runClient
```

### **Step 3: Commit Changes**

```bash
# Stage changes
git add .

# Commit with descriptive message
git commit -m "feat: Add bilateral smoothing algorithm"
# or
git commit -m "fix: Resolve memory leak in TieredMemoryPool"
```

**Commit Message Format**:
```
<type>: <subject>

<body>

<footer>
```

**Types**:
- `feat` - New feature
- `fix` - Bug fix
- `docs` - Documentation
- `style` - Code style (formatting)
- `refactor` - Code refactoring
- `test` - Adding tests
- `perf` - Performance improvement
- `chore` - Build/tooling changes

**Example**:
```
feat: Add bilateral smoothing algorithm

Implements edge-preserving bilateral filtering for smoother terrain
transitions while maintaining sharp edges at biome boundaries.

- Added BilateralSmoother class
- Integrated with Smoother enum
- Added configuration options
- Benchmarked at ~0.9ms per pass

Closes #123
```

### **Step 4: Push Changes**

```bash
# Push to your fork
git push origin feature/your-feature-name
```

### **Step 5: Create Pull Request**

1. Go to GitHub repository
2. Click "New Pull Request"
3. Select your branch
4. Fill out PR template
5. Link related issues
6. Request reviews

---

## 📝 **Coding Standards**

### **Java Code Style**

```java
// ✅ GOOD: Clear naming, proper formatting
public class BiomeBlender {
    
    private static final int DEFAULT_RADIUS = 2;
    
    /**
     * Calculate blended scale factor with adaptive radius.
     * 
     * @param chunk Current chunk
     * @param localX Local X coordinate (0-15)
     * @param localZ Local Z coordinate (0-15)
     * @return Blended scale factor
     */
    public static float getBlendedScaleFactor(
        LevelChunk chunk, 
        int localX, 
        int localZ
    ) {
        // Implementation
    }
}

// ❌ BAD: Unclear naming, poor formatting
public class bb {
    public static float g(LevelChunk c,int x,int z){
        // Implementation
    }
}
```

### **Code Organization**

```java
// Class structure order:
1. Constants (public static final)
2. Static fields
3. Instance fields
4. Constructors
5. Public methods
6. Protected methods
7. Private methods
8. Inner classes

// Example:
public class MyComponent {
    
    // 1. Constants
    private static final int MAX_SIZE = 100;
    
    // 2. Static fields
    private static final AtomicInteger counter = new AtomicInteger(0);
    
    // 3. Instance fields
    private final String name;
    private int value;
    
    // 4. Constructor
    public MyComponent(String name) {
        this.name = name;
    }
    
    // 5. Public methods
    public void process() {
        // ...
    }
    
    // 6. Private methods
    private void validate() {
        // ...
    }
}
```

### **Naming Conventions**

```java
// Classes: PascalCase
public class TieredMemoryPool { }

// Methods: camelCase
public void calculateSlope() { }

// Constants: UPPER_SNAKE_CASE
private static final int MAX_THREADS = 8;

// Variables: camelCase
int chunkCount = 0;

// Packages: lowercase
package com.sltmod.processing;
```

### **Documentation**

```java
/**
 * Calculate local slope at position using configurable grid.
 * 
 * <p>Uses Sobel operator for gradient calculation. Grid size affects
 * quality vs performance trade-off.
 * 
 * <p><b>Thread Safety:</b> This method is thread-safe.
 * 
 * @param cache Heightmap cache (must not be null)
 * @param x Local X coordinate (0-15)
 * @param z Local Z coordinate (0-15)
 * @return Raw slope value (0-256)
 * @throws IllegalArgumentException if cache is null
 * @see #dampSlope(int)
 */
public static int calculateLocalSlope(
    HeightmapCache cache, 
    int x, 
    int z
) {
    // Implementation
}
```

### **Error Handling**

```java
// ✅ GOOD: Specific exceptions, proper logging
public void processChunk(LevelChunk chunk) {
    if (chunk == null) {
        throw new IllegalArgumentException("Chunk cannot be null");
    }
    
    try {
        // Processing logic
    } catch (OutOfMemoryError e) {
        logger.error("Out of memory processing chunk {}", chunk.getPos(), e);
        // Cleanup
        throw new RuntimeException("Processing failed", e);
    } catch (Exception e) {
        logger.error("Unexpected error", e);
        throw new RuntimeException("Processing failed", e);
    }
}

// ❌ BAD: Generic exceptions, no logging
public void processChunk(LevelChunk chunk) {
    try {
        // Processing
    } catch (Exception e) {
        // Silent failure
    }
}
```

### **Thread Safety**

```java
// ✅ GOOD: Thread-safe collections, atomics
private static final ConcurrentHashMap<K, V> cache = new ConcurrentHashMap<>();
private static final AtomicInteger counter = new AtomicInteger(0);

public void increment() {
    counter.incrementAndGet(); // Atomic operation
}

// ❌ BAD: Non-thread-safe
private static final HashMap<K, V> cache = new HashMap<>();
private static int counter = 0;

public void increment() {
    counter++; // Race condition!
}
```

---

## ✅ **Testing Requirements**

### **Unit Tests**

Every new component must have unit tests:

```java
@Test
public void testSlopeCalculation() {
    // Arrange
    HeightmapCache cache = createTestCache();
    
    // Act
    int slope = SlopeCalculator.calculateLocalSlope(cache, 8, 8);
    
    // Assert
    assertTrue(slope >= 0);
    assertTrue(slope <= 256);
}

@Test
public void testNullHandling() {
    assertThrows(IllegalArgumentException.class, () -> {
        SlopeCalculator.calculateLocalSlope(null, 0, 0);
    });
}

@Test
public void testThreadSafety() throws Exception {
    // Test concurrent access
    ExecutorService executor = Executors.newFixedThreadPool(10);
    List<Future<?>> futures = new ArrayList<>();
    
    for (int i = 0; i < 100; i++) {
        futures.add(executor.submit(() -> {
            TieredMemoryPool.acquire();
        }));
    }
    
    for (Future<?> future : futures) {
        future.get(5, TimeUnit.SECONDS);
    }
    
    executor.shutdown();
}
```

### **Integration Tests**

Test component interactions:

```java
@Test
public void testEndToEndProcessing() {
    // Setup
    LevelChunk chunk = createTestChunk();
    
    // Process
    LayeredTerrainSystem.processChunkSync(chunk);
    
    // Verify
    assertTrue(NBTHelper.isProcessed(chunk));
    assertTrue(Metrics.chunksProcessed.get() > 0);
}
```

### **Performance Tests**

Ensure performance targets are met:

```java
@Test
public void testPerformance() {
    // Run multiple iterations
    int iterations = 100;
    long totalTime = 0;
    
    for (int i = 0; i < iterations; i++) {
        long start = System.nanoTime();
        // Operation to test
        totalTime += System.nanoTime() - start;
    }
    
    double avgMs = (totalTime / iterations) / 1_000_000.0;
    
    // Assert meets target
    assertTrue(avgMs < 5.0, "Average time should be <5ms");
}
```

### **Running Tests**

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests TieredMemoryPoolTest

# Run with coverage
./gradlew test jacocoTestReport

# View coverage report
open build/reports/jacoco/test/html/index.html
```

---

## 📚 **Documentation**

### **Code Documentation**

- All public classes must have Javadoc
- All public methods must have Javadoc
- Complex algorithms need inline comments
- Include examples where helpful

### **File Documentation**

When adding new files, update:

1. **docs/ARCHITECTURE.md** - If adding new component
2. **docs/API.md** - If adding public API
3. **docs/AGENTS.md** - If changing workflow
4. **CHANGELOG.md** - Always update
5. **README.md** - If user-facing changes

### **Documentation Format**

```markdown
# Component Name

## Purpose
Brief description of what this does.

## Usage
```java
// Example code
```

## Configuration
```toml
# Config options
```

## Performance
- Typical time: 2ms
- Memory: 5MB

## See Also
- [Related Component](link)
```

---

## 🔍 **Pull Request Process**

### **Before Submitting**

**Checklist**:
- [ ] Code compiles without errors
- [ ] All tests pass
- [ ] New tests added for new features
- [ ] Documentation updated
- [ ] CHANGELOG.md updated
- [ ] Code follows style guidelines
- [ ] No compiler warnings
- [ ] Performance benchmarks run (if applicable)

### **PR Template**

```markdown
## Description
Brief description of changes.

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update
- [ ] Performance improvement

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests pass
- [ ] Manual testing completed

## Performance Impact
- Before: X ms
- After: Y ms
- Change: Z%

## Screenshots (if applicable)
[Add screenshots]

## Related Issues
Closes #123
Relates to #456

## Checklist
- [ ] Code compiles
- [ ] Tests pass
- [ ] Documentation updated
- [ ] CHANGELOG.md updated
```

### **Review Process**

1. **Automated Checks**
   - Build must pass
   - Tests must pass
   - Code style checks
   - Coverage requirements

2. **Code Review**
   - At least 1 approval required
   - Address all comments
   - No unresolved discussions

3. **Final Review**
   - Maintainer final check
   - Squash and merge
   - Update milestone

### **After Merge**

- PR is closed automatically
- Issue is closed (if linked)
- Changes appear in next release
- Your contribution is credited

---

## 🏆 **Recognition**

### **Contributors List**

All contributors are recognized in:
- **README.md** - Contributors section
- **Release notes** - Each release
- **CONTRIBUTORS.md** - Complete list

### **Types of Contributions**

We recognize:
- 💻 Code contributions
- 📖 Documentation
- 🐛 Bug reports
- 💡 Feature ideas
- 🎨 Design
- 🌍 Translations
- 🧪 Testing
- 📢 Community support

---

## ❓ **Questions?**

### **Where to Ask**

- **General Questions**: [Discord Server](https://discord.gg/yourserver)
- **Bug Reports**: [GitHub Issues](https://github.com/deft-orchestrator/layered-terrain/issues)
- **Feature Requests**: [GitHub Discussions](https://github.com/deft-orchestrator/layered-terrain/discussions)
- **Security Issues**: security@sltmod.com

### **Response Time**

- Issues: Within 48 hours
- PRs: Within 1 week
- Discord: Usually within 24 hours

---

## 📄 **License**

By contributing, you agree that your contributions will be licensed under the MIT License.

---

## 🙏 **Thank You**

Every contribution, no matter how small, makes this project better. Thank you for being part of our community!

---

**Last Updated**: December 2024  
**Version**: 3.2.0