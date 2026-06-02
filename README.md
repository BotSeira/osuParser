# osuParser

Java library for parsing osu! beatmaps and replays, plus computing difficulty and performance attributes via `rosu-pp` FFI.

## Features

- Parse `.osu` beatmaps into structured data.
- Parse `.osr` replays and compute replay analytics.
- Estimate performance points (pp) and difficulty specs using `rosu-pp` via JNA.

## Requirements

- Java 25 (per `pom.xml`).
- Maven 3.9+ (or compatible).

## Install

Add the dependency from source (for now) and build locally:

```bash
mvn clean package
```

The native `rosu-pp` libraries are bundled under `src/main/resources/native`. They are included in the built JAR and loaded via JNA at runtime.

## Usage

### Parse a beatmap

```java
import xyz.zcraft.osu.parser.BeatmapParser;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;

import java.nio.file.Path;

public class Example {
    public static void main(String[] args) throws Exception {
        Path beatmapFile = Path.of("/path/to/beatmap.osu");
        OsuBeatmap map = BeatmapParser.parseBeatmap(beatmapFile);
        System.out.println(map.getTitleUnicode());
    }
}
```

### Parse a replay

```java
import xyz.zcraft.osu.parser.ReplayParser;
import xyz.zcraft.osu.parser.data.replay.OsuReplay;

import java.nio.file.Path;

public class Example {
    public static void main(String[] args) throws Exception {
        Path replayFile = Path.of("/path/to/replay.osr");
        OsuReplay replay = ReplayParser.parseReplay(replayFile);
        System.out.println(replay.count300());
    }
}
```

### Analyze a replay

```java
import xyz.zcraft.osu.parser.BeatmapParser;
import xyz.zcraft.osu.parser.ReplayAnalyzer;
import xyz.zcraft.osu.parser.ReplayParser;
import xyz.zcraft.osu.parser.data.replay.OsuReplay;
import xyz.zcraft.osu.parser.data.replay.ReplayAnalyze;

import java.nio.file.Path;

public class Example {
    public static void main(String[] args) throws Exception {
        Path beatmapFile = Path.of("/path/to/beatmap.osu");
        OsuBeatmap beatmap = BeatmapParser.parseBeatmap(beatmapFile);
        Path replayFile = Path.of("/path/to/replay.osr");
        OsuReplay replay = ReplayParser.parseReplay(replayFile);
        ReplayAnalyze analyze = ReplayAnalyzer.analyze(beatmap, replay);
        System.out.println(analyze.unstableRate());
    }
}
```

### Estimate pp for a score

```java
import xyz.zcraft.osu.model.Score;
import xyz.zcraft.osu.parser.OsuParser;

import java.nio.file.Path;

public class Example {
    public static void main(String[] args) {
        Score score = new Score();
        // ...populate stats and mods...

        double pp = OsuParser.estimatePp(score, Path.of("/path/to/beatmap.osu"));
        System.out.println(pp);
    }
}
```

## Notes

- The library depends on `osuModel` for data models.
- If you replace native libraries, ensure the correct platform file is present:
  - Windows: `rosu_pp_ffi.dll`
  - Linux: `librosu_pp_ffi.so`
  - macOS: `librosu_pp_ffi.dylib`

## License

Add your preferred license here.

