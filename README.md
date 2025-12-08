# Smooth Layered Terrain (SLT) Mod

**Smooth Layered Terrain System v3.2 - Ultimate Hybrid Edition**

## Overview

Smooth Layered Terrain (SLT) is a Minecraft Forge mod (v1.20.1) designed to transform standard blocky Minecraft terrain into smooth, natural landscapes. It achieves this by utilizing layered blocks (8 thickness levels) and sophisticated smoothing algorithms.

The mod is engineered for high performance and reliability, featuring multi-threaded processing, custom memory management, and enterprise-grade monitoring.

### Key Features

*   **Smooth Terrain Transitions**: Uses 8 thickness layers to create gradual slopes.
*   **High Performance**: Processes chunks in under 5ms average using async processing and load balancing.
*   **Advanced Smoothing**: Supports 6 different smoothing algorithms (Gaussian, Median, Bilateral, Anisotropic, Adaptive, Multi-scale).
*   **Reliability**: Includes circuit breakers, chunk validation, and edge case filtering.
*   **Memory Management**: Custom tiered memory pooling to reduce Garbage Collection pressure.
*   **Monitoring**: Built-in metrics, profiling, and diagnostics with optional Prometheus export.

## Architecture

The project follows a modular architecture:

*   **Configuration Layer**: `LayeredTerrainConfig` for extensive customization.
*   **Memory Layer**: `TieredMemoryPool` and `HeightmapCache` for efficient resource usage.
*   **Processing Layer**: `AsyncProcessor`, `SlopeCalculator`, `Smoother`, `BiomeBlender`.
*   **Reliability Layer**: `CircuitBreakerAdvanced`, `ChunkValidator`.
*   **Integration Layer**: Handles Minecraft Forge events and block placement.

## Setup and Installation

### Prerequisites

*   Java 17 or higher
*   Minecraft Forge 1.20.1 MDK

### Building from Source

1.  Clone the repository.
2.  Navigate to the project directory.
3.  Build the project using Gradle:

    ```bash
    ./gradlew build
    ```

    This will verify the installation and compile the mod.

### Running in IDE

**Eclipse:**
1.  Run `./gradlew genEclipseRuns`
2.  Import the project as an Existing Gradle Project.

**IntelliJ IDEA:**
1.  Import the project via `build.gradle`.
2.  Run `./gradlew genIntellijRuns`.

## Usage

Once installed, the mod automatically processes terrain generation. However, it also provides a suite of administrative commands for monitoring and configuration.

### Commands

The main command is `/layerterrain`. Available subcommands include:

*   `/layerterrain stats`: View current performance metrics.
*   `/layerterrain profiling`: Detailed timing breakdown of processing stages.
*   `/layerterrain health`: Run system health checks.
*   `/layerterrain pool`: View memory pool statistics.
*   `/layerterrain circuit`: Check the status of the circuit breaker.
*   `/layerterrain reload`: Reload configuration from disk.
*   `/layerterrain reprocess <radius>`: Manually reprocess chunks within a radius.
*   `/layerterrain diagnostics`: Run full system diagnostics.

## Configuration

The mod is highly configurable via `config/layered-terrain.toml`. You can adjust:

*   Smoothing algorithms and kernels.
*   Performance settings (thread pool size, timeouts).
*   Memory pool limits.
*   Feature toggles (snow layers, fluid handling).

## Contributing

Please refer to `CONTRIBUTING.md` (if available) for guidelines. Ensure all new code includes comprehensive Javadoc and passes all tests.

## License

See `LICENSE.txt` for license information.
