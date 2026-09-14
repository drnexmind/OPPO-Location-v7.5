# OPPO Location V7.2 — MapTiler Web Map Build Fix

This release removes the MapTiler Kotlin SDK that caused the Kotlin compilation failure.

## Map layer
The app now uses Android WebView + MapTiler SDK JS 4.1.0.

Available map modes:
- STREETS — detailed roads, places and POI labels
- HYBRID — satellite imagery with labels, landmarks, roads and borders

MapTiler documents HYBRID as satellite imagery with labels.

Tap directly on the map to select coordinates.

## GitHub secret
Create repository secret:

`MAPTILER_API_KEY`

The workflow passes it to Gradle as:

`-PMAPTILER_API_KEY=...`

No key is committed to source.

## Mock location
The V7 verified mock-location service is retained:
- Fused mock mode first
- `setMockLocation()`
- verifies returned coordinates
- GPS test-provider fallback
- only marks the session verified when coordinates are near the selected target

This app does not hide Android's mock-location state.
