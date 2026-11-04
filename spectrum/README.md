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

Also on that branch, since `27.0.0-alpha-7-spectrum.4`: a `put` whose value equals the stored one
returns before boxing the value or copying the array (struct puts compare the encoded bytes); a
struct's schema is checked once per type, not on every put; `getSubtable` returns the same table
each time, so `processInputs` builds no key or table per call; and the `LoggedNetwork*` inputs
build their slash-less key once. The table ends up the same as before. Replaying the same match
on a laptop: allocation 238 to 213 KB per loop, replayed outputs identical.

Alerts are still read every cycle: WPILib keeps them in native code with no change counter, and
reading them less often would log an alert late or miss a short one.

## Using it

The fork is its own vendordep. In VS Code, "WPILib: Manage Vendor Libraries" > "Install new
library (online)" with:

```
https://spectrum3847.github.io/AdvantageKit/AdvantageKit-Spectrum.json
```

and delete `vendordeps/AdvantageKit.json` (the two conflict on purpose). Its `jsonUrl` is that
address, so "Check for updates" moves to the fork's next version, never back to upstream. Every
published version's file is also kept under `vendordeps/` on the site.

It brings `akit-java` and `akit-autolog` from this fork and the native `akit-wpilibio` from
upstream, at the upstream version the fork version starts with. A `build.gradle` that reads the
AdvantageKit version for the annotation processor must read `AdvantageKit-Spectrum.json`.

## Publishing

```bash
spectrum/publish_maven.sh 27.0.0-alpha-7-spectrum.3
```

Builds and tests the fork from the current commit and pushes `akit-java`, `akit-autolog` and the
vendordep (from `spectrum/AdvantageKit-Spectrum.json`) to the `maven` branch, which GitHub Pages
serves. Versions end in `-spectrum.N` and are never overwritten.
