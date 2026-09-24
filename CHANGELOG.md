# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/)
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed

- **Scene-based layer ordering** — migrated from `useLayerOrder` to the parent's scene-model
  hooks (`useLayerAnchor({ kind: 'layer' })` + `useSceneUuidBinding`). Layer z-order is now
  derived from React tree order; the component renders a `VtmAnchorView` instead of `null`.
- **`react-native-mapsforge-vtm` peer dependency bumped to `^0.9.0`** (breaking — the scene-model
  release). The example app consumes the published `^0.9.0` instead of a local yalc copy.

### Removed

- **`positionIndex` create-time param** — the `createLayer` native call no longer carries a
  `positionIndex`; ordering is applied by the parent's scene (`reorderLayers`).

### Fixed

- **Sample data grid dimensions** — `example/data/sample-wind.json` declared `nj: 24` but only
  contained 384 values; corrected to `nj: 12` (32 × 12) so tile queries no longer index out of
  bounds.
