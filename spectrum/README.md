# Spectrum 3847's AdvantageKit fork

Upstream: [Mechanical-Advantage/AdvantageKit](https://github.com/Mechanical-Advantage/AdvantageKit).
Branches are cut from upstream release tags.

## Changes

`spectrum/delta-logging` (from `v27.0.0-alpha-7`): the main thread sends the receiver thread only
the entries that changed each cycle, instead of a copy of every entry ever logged. The receiver
thread keeps the full table; `WPILOGWriter` and `NT4Publisher` write only the changes, and any
other receiver gets a full copy through `LogDataReceiver.putChanges`'s default. A receiver that
rewrites values for the receivers after it must override `putChanges` and fix both tables.

Measured on a Raspberry Pi 5 (SystemCore's CPU) replaying an 8515 TXMAN1 match: main thread 1.99
to 1.64 ms per loop, log receiver 0.52 to 0.16 ms, allocation 539 to 255 KB per loop, replayed
outputs identical.

## Using it

The fork publishes only `akit-java`. In a robot project's `vendordeps/AdvantageKit.json`, add the
repository and change only the `akit-java` version; `akit-wpilibio` and `akit-autolog` stay at the
upstream version:

```json
"mavenUrls": [
  "https://frcmaven.wpi.edu/artifactory/littletonrobotics-mvn-release/",
  "https://spectrum3847.github.io/AdvantageKit/"
],
"javaDependencies": [
  { "groupId": "org.littletonrobotics.akit", "artifactId": "akit-java", "version": "27.0.0-alpha-7-spectrum.1" }
]
```

## Publishing

```bash
spectrum/publish_maven.sh 27.0.0-alpha-7-spectrum.2
```

Builds and tests `akit-java` from the current commit and pushes it to the `maven` branch, which
GitHub Pages serves. Versions end in `-spectrum.N` and are never overwritten.
