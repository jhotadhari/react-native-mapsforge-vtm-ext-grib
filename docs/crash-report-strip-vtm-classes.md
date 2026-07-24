# Bug Report: `strip-vtm-classes.gradle` breaks Pattern B1 extensions

**Severity:** Critical — causes `NoClassDefFoundError` crash at app startup
**Affected:** Every Pattern B1 extension (ext-grib) after any Pattern C extension (ext-path-color-ramp) is built
**Date:** 2026-07-24

## Summary

The `strip-vtm-classes.gradle` script has two **global, destructive side effects** that break extensions which depend on the parent library's restored vtm classes. When building a Pattern C extension (one that shadows `LineBucket` and `RenderBuckets`), the script:

1. **Patches the shared vtm JAR in the Gradle cache** — removes `.class` entries so every app on the machine that depends on vtm 0.29.0 loses `RenderBuckets` and `LineBucket`
2. **Deletes `.java` source files from the parent library** — both in `node_modules` and, when yalc is used, from the actual source tree

Pattern B1 extensions like ext-grib (which use custom `TileSource` but no vtm class shadowing) are then left with **no provider** for these classes — the vtm JAR doesn't have them (stripped), the parent library doesn't have them (deleted), and the extension doesn't shadow them.

## Background: why the parent library ships vtm classes

vtm 0.28.0 renamed `RenderBuckets` → `RenderBucket` and deleted `LineBucket`, but `MapRenderer.onSurfaceCreated()` still calls `RenderBuckets.initRenderer()` and `LineTexBucket` still extends `LineBucket`. The parent library (commit `a7d3bcd`) restored default copies of these classes so that apps without a shadowing extension would still work:

```
android/src/main/java/org/oscim/renderer/bucket/
  RenderBuckets.java    — standard 4-short vertex format
  LineBucket.java       — no color-ramp additions
```

Pattern C extensions (ext-path-color-ramp) provide their own modified versions of these classes. The strip script removes the parent library's copies so only the extension's versions remain — the correct intent.

## The two destructive mechanisms

### Mechanism 1: Global JAR patching (lines 165–293)

The `stripShadowedVtmClasses` task locates the vtm JAR in `~/.gradle/caches/` and runs `zip -d` to remove `.class` entries for each shadowed class:

```groovy
// Walks the Gradle dependency cache
def cacheBase = new File(appProject.gradle.gradleUserHomeDir,
    'caches/modules-2/files-2.1/com.github.mapsforge.vtm/vtm/0.29.0')

// Then patches in-place:
def zipCmd = ['zip', '-d', jar.absolutePath] + entriesToRemove
```

**This is a global mutation.** There is one copy of the vtm JAR in the Gradle cache shared by ALL projects on the machine. After the script runs, every app that depends on `vtm:0.29.0` gets a JAR with `RenderBuckets.class` and `LineBucket.class` removed. The original is saved as `.orig` but never restored.

Evidence from the filesystem:

```
~/.gradle/caches/.../vtm/0.29.0/<hash>/
  vtm-0.29.0.jar        — 807,620 bytes (stripped — no LineBucket/RenderBuckets)
  vtm-0.29.0.jar.orig   — 820,749 bytes (original — has the classes)
```

### Mechanism 2: Source file deletion (lines 324–336)

During `projectsEvaluated`, the script finds the core library Gradle project and deletes `.java` source files for shadowed classes:

```groovy
if (coreLibProject) {
    allShadowed.each { className, info ->
        def relPath = className.replace('.', '/') + '.java'
        def srcFile = new File(coreLibProject.projectDir,
            'src/main/java/' + relPath)
        if (srcFile.exists()) {
            srcFile.delete()  // ← permanent deletion
        }
    }
}
```

The `coreLibProject` is found via:
```groovy
def coreLibProject = rootProject.allprojects.find {
    it.name == 'react-native-mapsforge-vtm'
}
```

React Native autolinking includes `react-native-mapsforge-vtm` as a Gradle project, so `projectDir` points to the parent library in `node_modules/`. When yalc is used, this can resolve to the **actual source directory** — deleting files from the working tree. The deletions are not staged, not committed, but the files are gone from disk.

Evidence from `git status` on the parent library:

```
Changes not staged for commit:
  deleted:    android/src/main/java/org/oscim/renderer/bucket/LineBucket.java
  deleted:    android/src/main/java/org/oscim/renderer/bucket/RenderBuckets.java
```

## Crash reproduction chain

1. Build ext-path-color-ramp's example app → `strip-vtm-classes.gradle` runs
2. Script strips `LineBucket.class` and `RenderBuckets.class` from the shared vtm JAR in `~/.gradle/caches/`
3. Script deletes `LineBucket.java` and `RenderBuckets.java` from the parent library source
4. User yalc-pushes the parent library to ext-grib → yalc copy lacks the deleted files
5. Build ext-grib's example app → compiles fine (no reference to these classes at compile time)
6. At runtime, vtm's `MapRenderer.onSurfaceCreated()` calls `RenderBuckets.initRenderer()` → **`NoClassDefFoundError`** → immediate crash

Confirmed: the parent library's own example APK was built **before** the JAR stripping (21:46 vs 21:55), which is why it works despite the same JAR now being stripped.

## Why this is a correctness issue in the library

The `strip-vtm-classes.gradle` script was designed as a "reusable DEX dedup script for extensions" (commit `66ff21e`). However, its current implementation mutates shared state:

| Mutation | Scope | Consequence |
|---|---|---|
| `zip -d` on vtm JAR | **Global** (Gradle cache) | Every project on the machine loses the classes until the JAR is re-downloaded |
| `srcFile.delete()` on parent library sources | **Cross-project** (node_modules or actual source) | Every other extension loses the classes until `git restore` |

Neither mutation is scoped to the build that triggered it. They are equivalent to `npm install -g` modifying a global package and `rm`-ing files from a shared dependency's source tree.

## Recommended fixes

### Immediate (restore working state)

```bash
cd ~/Development/android/react-native-mapsforge-vtm
git restore android/src/main/java/org/oscim/renderer/bucket/LineBucket.java
git restore android/src/main/java/org/oscim/renderer/bucket/RenderBuckets.java
yarn prepare
yalc push
```

Then rebuild ext-grib. The Gradle cache JAR will also need restoration — either delete the stripped JAR so Gradle re-downloads it, or copy the `.orig` back:

```bash
# Option A: force re-download
find ~/.gradle/caches -name "vtm-0.29.0.jar" ! -name "*.orig" -delete

# Option B: restore from backup (if rebuild is needed before re-download)
find ~/.gradle/caches -path "*/vtm/0.29.0/*/vtm-0.29.0.jar" | while read f; do
  [ -f "$f.orig" ] && cp "$f.orig" "$f"
done
```

### Architectural (fix the root cause)

**Option 1 — Scoped stripping (recommended).** Instead of mutating the shared JAR and deleting sources, copy the JAR to a build-local location before stripping. For the source-deletion step, exclude the shadowed classes from compilation using a Gradle source filter rather than deleting files from disk:

```groovy
// Instead of srcFile.delete():
// Exclude at the source set level:
coreLibProject.android.sourceSets.main.java.exclude(
    'org/oscim/renderer/bucket/LineBucket.java',
    'org/oscim/renderer/bucket/RenderBuckets.java'
)
```

The JAR stripping should work on a **copy** of the vtm JAR placed in the app's build directory, not the shared Gradle cache copy. Use a Gradle `Configuration` resolution strategy or an artifact transform to produce a filtered JAR scoped to the app's dependency graph.

**Option 2 — Per-project JAR isolation.** Resolve the vtm JAR through Gradle's dependency resolution (which gives a project-scoped file collection) rather than walking the global cache directory. This naturally isolates the stripping to the build that needs it.

**Option 3 — Restore after build.** If patching in-place must be kept, add a build-finalized hook that restores the `.orig` backup so the JAR is intact for the next consumer.

## Verification checklist

After the fix, the following should all hold:

- [ ] Build ext-path-color-ramp example → succeeds, color-ramp paths render correctly
- [ ] Build ext-grib example (on the same machine, without any manual restore) → succeeds, weather overlay renders
- [ ] Build parent library example → succeeds
- [ ] `git status` on the parent library shows **no deleted files**
- [ ] The vtm JAR in `~/.gradle/caches/` still contains `RenderBuckets.class` and `LineBucket.class` after all builds
- [ ] Running the three builds in any order produces no `NoClassDefFoundError`
