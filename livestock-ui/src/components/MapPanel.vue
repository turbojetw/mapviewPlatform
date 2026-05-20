<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import maplibregl from 'maplibre-gl'
import type { AnimalStatus, GeoFence } from '../types'

const props = defineProps<{
  animalStatuses: Map<number, AnimalStatus>
  geofences: GeoFence[]
  selectedAnimalId: number | null
  selectedFenceId: number | null
  pendingFenceCoords: [number, number][] | null  // drawn but not yet saved
  hiddenFenceIds: number[]
}>()

const emit = defineEmits<{
  (e: 'animal-selected', id: number): void
  (e: 'draw-complete', coords: [number, number][]): void
  (e: 'draw-cancelled'): void
}>()

const mapContainer = ref<HTMLDivElement>()
let map: maplibregl.Map
const mapReady = ref(false)

// ─── Draw mode state ───────────────────────────────────────────────────────
const drawActive = ref(false)
let drawCoords: [number, number][] = []
const drawVertexCount = ref(0)   // reactive copy for toolbar display
let drawDotMarkers: maplibregl.Marker[] = []

function startDraw() {
  if (drawActive.value) return
  drawActive.value = true
  drawCoords = []
  drawVertexCount.value = 0
  map.getCanvas().style.cursor = 'crosshair'
  map.doubleClickZoom.disable()
  initDrawLayers()
  map.on('click', handleDrawClick)
  map.on('mousemove', handleDrawMouseMove)
  window.addEventListener('keydown', handleDrawKey)
}

function cancelDraw() {
  stopDraw({ clearPreview: true })
  emit('draw-cancelled')
}

function finishDraw() {
  if (drawCoords.length < 3) return
  const closed: [number, number][] = [...drawCoords, [...drawCoords[0]] as [number, number]]
  // Keep the polygon visible on the map; only stop the interactive draw mode
  stopDraw({ clearPreview: false })
  emit('draw-complete', closed)
}

function undoVertex() {
  if (drawCoords.length === 0) return
  drawCoords.pop()
  drawVertexCount.value = drawCoords.length
  drawDotMarkers.pop()?.remove()
  refreshDrawPreview()
}

function stopDraw({ clearPreview = true } = {}) {
  drawActive.value = false
  drawCoords = []
  drawVertexCount.value = 0
  drawDotMarkers.forEach(m => m.remove())
  drawDotMarkers = []
  map.getCanvas().style.cursor = ''
  map.doubleClickZoom.enable()
  map.off('click', handleDrawClick)
  map.off('mousemove', handleDrawMouseMove)
  window.removeEventListener('keydown', handleDrawKey)
  // Always clear the rubber-band line (it would linger after Finish otherwise)
  if (map.getSource('draw-rubber')) {
    ;(map.getSource('draw-rubber') as maplibregl.GeoJSONSource).setData(
      { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: [] } }
    )
  }
  if (clearPreview) clearDrawPreview()
}

function handleDrawClick(e: maplibregl.MapMouseEvent) {
  const coord: [number, number] = [e.lngLat.lng, e.lngLat.lat]
  drawCoords.push(coord)
  drawVertexCount.value = drawCoords.length
  // Vertex dot marker
  const el = document.createElement('div')
  el.style.cssText = `width:10px;height:10px;border-radius:50%;background:#fff;
    border:2px solid #FF6B6B;box-shadow:0 1px 4px rgba(0,0,0,0.5);cursor:default;`
  const marker = new maplibregl.Marker({ element: el }).setLngLat(coord).addTo(map)
  drawDotMarkers.push(marker)
  refreshDrawPreview()
}

function handleDrawMouseMove(e: maplibregl.MapMouseEvent) {
  if (!map.getSource('draw-rubber')) return
  const cursor: [number, number] = [e.lngLat.lng, e.lngLat.lat]
  const lineCoords = drawCoords.length > 0
    ? [...drawCoords, cursor]
    : [cursor, cursor]
  ;(map.getSource('draw-rubber') as maplibregl.GeoJSONSource).setData({
    type: 'Feature', properties: {},
    geometry: { type: 'LineString', coordinates: lineCoords }
  })
}

function handleDrawKey(e: KeyboardEvent) {
  if (e.key === 'Escape') cancelDraw()
  if (e.key === 'Enter' && drawCoords.length >= 3) finishDraw()
  if ((e.key === 'z' || e.key === 'Z') && (e.ctrlKey || e.metaKey)) {
    e.preventDefault(); undoVertex()
  }
}

function initDrawLayers() {
  if (!map.getSource('draw-polygon')) {
    map.addSource('draw-polygon', { type: 'geojson', data: { type: 'FeatureCollection', features: [] } })
    map.addLayer({ id: 'draw-polygon-fill', type: 'fill', source: 'draw-polygon',
      paint: { 'fill-color': '#FF6B6B', 'fill-opacity': 0.15 } })
    map.addLayer({ id: 'draw-polygon-line', type: 'line', source: 'draw-polygon',
      paint: { 'line-color': '#FF6B6B', 'line-width': 2 } })
  }
  if (!map.getSource('draw-rubber')) {
    map.addSource('draw-rubber', { type: 'geojson',
      data: { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: [] } }
    })
    map.addLayer({ id: 'draw-rubber-line', type: 'line', source: 'draw-rubber',
      paint: { 'line-color': '#FF6B6B', 'line-width': 1.5, 'line-dasharray': [3, 2], 'line-opacity': 0.7 } })
  }
}

function refreshDrawPreview() {
  if (!map.getSource('draw-polygon')) return
  if (drawCoords.length < 2) {
    ;(map.getSource('draw-polygon') as maplibregl.GeoJSONSource).setData({ type: 'FeatureCollection', features: [] })
    return
  }
  const coords = drawCoords.length >= 3
    ? [...drawCoords, drawCoords[0]]   // closed ring
    : drawCoords
  ;(map.getSource('draw-polygon') as maplibregl.GeoJSONSource).setData({
    type: 'Feature', properties: {},
    geometry: {
      type: drawCoords.length >= 3 ? 'Polygon' : 'LineString',
      coordinates: drawCoords.length >= 3 ? [coords] : coords
    }
  })
}

function clearDrawPreview() {
  if (!map || !map.isStyleLoaded()) return
  if (map.getSource('draw-polygon')) {
    ;(map.getSource('draw-polygon') as maplibregl.GeoJSONSource).setData({ type: 'FeatureCollection', features: [] })
  }
  if (map.getSource('draw-rubber')) {
    ;(map.getSource('draw-rubber') as maplibregl.GeoJSONSource).setData(
      { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: [] } }
    )
  }
}
const markers = new Map<number, maplibregl.Marker>()
const positionHistory = new Map<number, [number, number][]>()
const MAX_TRAIL = 50

// ─── Animal markers ────────────────────────────────────────────────────────

function makeMarkerEl(status: AnimalStatus): HTMLElement {
  const el = document.createElement('div')
  el.style.cssText = `
    width:36px;height:36px;border-radius:50%;
    border:3px solid ${status.collarColor};
    background:#1a1d23dd;display:flex;align-items:center;justify-content:center;
    font-size:18px;cursor:pointer;
    box-shadow:0 2px 8px rgba(0,0,0,0.5);
    transition:transform 0.15s;
  `
  el.textContent = status.speciesEmoji
  el.title = status.name
  return el
}

function updateMarker(status: AnimalStatus) {
  if (!map) return
  const lngLat: [number, number] = [status.lng, status.lat]

  const history = positionHistory.get(status.animalId) ?? []
  history.push(lngLat)
  if (history.length > MAX_TRAIL) history.shift()
  positionHistory.set(status.animalId, history)

  if (markers.has(status.animalId)) {
    markers.get(status.animalId)!.setLngLat(lngLat)
  } else {
    const el = makeMarkerEl(status)
    const popup = new maplibregl.Popup({ offset: 20, closeButton: false })
    const marker = new maplibregl.Marker({ element: el })
      .setLngLat(lngLat).setPopup(popup).addTo(map)
    el.addEventListener('click', () => emit('animal-selected', status.animalId))
    markers.set(status.animalId, marker)
  }

  markers.get(status.animalId)!.getPopup()?.setHTML(`
    <div style="font-family:system-ui;font-size:12px;color:#e8ecf0;background:#22262f;padding:8px;border-radius:6px;min-width:160px">
      <div style="font-weight:700;font-size:14px;margin-bottom:6px">${status.speciesEmoji} ${status.name}</div>
      <div>📍 ${status.lat.toFixed(5)}, ${status.lng.toFixed(5)}</div>
      <div>⛰️ ${status.altitude?.toFixed(0) ?? '—'}m</div>
      <div>🔋 ${status.batteryPct}%</div>
      <div>HDOP: ${status.hdop?.toFixed(1) ?? '—'}</div>
      ${status.geofenceAlert ? `<div style="color:#ef5350;margin-top:4px">⚠️ Exited: ${status.geofenceAlert}</div>` : ''}
    </div>
  `)

  if (props.selectedAnimalId === status.animalId) drawTrail(status.animalId)
}

function drawTrail(animalId: number) {
  if (!map || !map.isStyleLoaded()) return
  const history = positionHistory.get(animalId) ?? []
  const sourceId = `trail-${animalId}`
  const geojson: GeoJSON.GeoJSON = {
    type: 'Feature', properties: {},
    geometry: { type: 'LineString', coordinates: history }
  }
  if (map.getSource(sourceId)) {
    (map.getSource(sourceId) as maplibregl.GeoJSONSource).setData(geojson)
  } else {
    map.addSource(sourceId, { type: 'geojson', data: geojson })
    map.addLayer({
      id: `trail-line-${animalId}`, type: 'line', source: sourceId,
      paint: {
        'line-color': props.animalStatuses.get(animalId)?.collarColor ?? '#4CAF50',
        'line-width': 2, 'line-opacity': 0.5, 'line-dasharray': [2, 2],
      }
    })
  }
}

function clearTrail(animalId: number) {
  if (!map) return
  if (map.getLayer(`trail-line-${animalId}`)) map.removeLayer(`trail-line-${animalId}`)
  if (map.getSource(`trail-${animalId}`)) map.removeSource(`trail-${animalId}`)
}

// ─── Normal geofence layer ─────────────────────────────────────────────────

// Label marker for the currently selected fence
let fenceLabelMarker: maplibregl.Marker | null = null

function fenceCentroid(coords: [number, number][]): [number, number] {
  const open = coords[0][0] === coords[coords.length - 1][0] &&
               coords[0][1] === coords[coords.length - 1][1]
               ? coords.slice(0, -1) : coords
  return [
    open.reduce((s, c) => s + c[0], 0) / open.length,
    open.reduce((s, c) => s + c[1], 0) / open.length,
  ]
}

function updateFenceLabel() {
  fenceLabelMarker?.remove()
  fenceLabelMarker = null
  if (!props.selectedFenceId || !map) return
  const fence = props.geofences.find(f => f.id === props.selectedFenceId && f.active)
  if (!fence) return
  try {
    const coords: [number, number][] = JSON.parse(fence.coordinatesJson)
    const [lng, lat] = fenceCentroid(coords)
    const el = document.createElement('div')
    el.style.cssText = `
      background: rgba(19,21,26,0.9);
      border: 1px solid ${fence.color}99;
      border-radius: 4px;
      padding: 3px 8px;
      font-size: 11px;
      font-weight: 700;
      color: ${fence.color};
      white-space: nowrap;
      pointer-events: none;
      box-shadow: 0 2px 6px rgba(0,0,0,0.5);
    `
    el.textContent = fence.name
    fenceLabelMarker = new maplibregl.Marker({ element: el, anchor: 'center' })
      .setLngLat([lng, lat])
      .addTo(map)
  } catch {}
}

function updateGeofences() {
  if (!map || !mapReady.value) return

  const features: GeoJSON.Feature[] = props.geofences
    .filter(f => f.active && !props.hiddenFenceIds.includes(f.id))
    .map(fence => {
      try {
        const coords: [number, number][] = JSON.parse(fence.coordinatesJson)
        return {
          type: 'Feature' as const,
          properties: {
            name: fence.name,
            color: fence.color,
            fillOpacity: fence.fillOpacity ?? 0.15,
            strokeWidth: fence.strokeWidth ?? 2.0,
            id: fence.id,
            selected: fence.id === props.selectedFenceId,
          },
          geometry: { type: 'Polygon' as const, coordinates: [coords] }
        }
      } catch { return null }
    })
    .filter(Boolean) as GeoJSON.Feature[]

  const geojson: GeoJSON.FeatureCollection = { type: 'FeatureCollection', features }

  if (map.getSource('geofences')) {
    (map.getSource('geofences') as maplibregl.GeoJSONSource).setData(geojson)
  } else {
    map.addSource('geofences', { type: 'geojson', data: geojson })
    map.addLayer({ id: 'geofences-fill', type: 'fill', source: 'geofences',
      paint: {
        'fill-color': ['get', 'color'],
        'fill-opacity': ['case',
          ['get', 'selected'],
          ['min', ['*', ['coalesce', ['get', 'fillOpacity'], 0.15], 2.0], 0.55],
          ['coalesce', ['get', 'fillOpacity'], 0.15],
        ],
      }
    })
    map.addLayer({ id: 'geofences-line', type: 'line', source: 'geofences',
      paint: {
        'line-color': ['get', 'color'],
        'line-width': ['case',
          ['get', 'selected'],
          ['+', ['coalesce', ['get', 'strokeWidth'], 2.0], 1.5],
          ['coalesce', ['get', 'strokeWidth'], 2.0],
        ],
        'line-opacity': ['case', ['get', 'selected'], 1.0, 0.75],
      }
    })
  }

  updateFenceLabel()
}

// ─── Watchers ─────────────────────────────────────────────────────────────

watch(() => props.animalStatuses, (statuses) => {
  statuses.forEach(status => updateMarker(status))
}, { deep: true })

watch(() => props.selectedAnimalId, (newId, oldId) => {
  if (oldId != null) clearTrail(oldId)
  if (newId != null) drawTrail(newId)
})

// Single watcher covering all geofence-related state.
// Including mapReady ensures that if geofence data arrives before the map's
// load event (e.g. cached tiles on refresh), the render still fires once
// the map is ready — and vice versa.
watch(
  [mapReady, () => props.geofences, () => props.hiddenFenceIds, () => props.selectedFenceId],
  () => { if (mapReady.value) updateGeofences() },
  { deep: true }
)

// Clear draw preview once the pending fence is saved or cancelled
watch(() => props.pendingFenceCoords, (coords) => {
  if (!coords) clearDrawPreview()
})

// ─── Public API ────────────────────────────────────────────────────────────

function flyToAnimal(animalId: number) {
  const status = props.animalStatuses.get(animalId)
  if (status && map) map.flyTo({ center: [status.lng, status.lat], zoom: 15, duration: 800 })
}

function setHistoryTrail(animalId: number, coords: [number, number][]) {
  positionHistory.set(animalId, coords)
  if (props.selectedAnimalId === animalId) drawTrail(animalId)
}

function flyToFence(fenceId: number) {
  const fence = props.geofences.find(f => f.id === fenceId)
  if (!fence || !map) return
  try {
    const coords: [number, number][] = JSON.parse(fence.coordinatesJson)
    const lngs = coords.map(c => c[0])
    const lats = coords.map(c => c[1])
    map.fitBounds(
      [[Math.min(...lngs), Math.min(...lats)], [Math.max(...lngs), Math.max(...lats)]],
      { padding: 120, duration: 700, maxZoom: 17 }
    )
  } catch {}
}

defineExpose({ flyToAnimal, startDraw, cancelDraw, finishDraw, undoVertex, drawActive, drawVertexCount, setHistoryTrail, flyToFence })

// ─── Mount ─────────────────────────────────────────────────────────────────

onMounted(() => {
  map = new maplibregl.Map({
    container: mapContainer.value!,
    style: {
      version: 8,
      sources: {
        osm: {
          type: 'raster',
          tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
          tileSize: 256,
          attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
          maxzoom: 19
        }
      },
      layers: [{ id: 'osm', type: 'raster', source: 'osm' }]
    },
    center: [105.5, 28.2],
    zoom: 13,
  })

  map.addControl(new maplibregl.NavigationControl(), 'top-right')
  map.addControl(new maplibregl.ScaleControl({ unit: 'metric' }), 'bottom-right')

  map.on('load', () => {
    initDrawLayers()
    props.animalStatuses.forEach(status => updateMarker(status))
    // Setting mapReady triggers the combined watcher which calls updateGeofences().
    // This ensures geofences render whether the API responded before or after map load.
    mapReady.value = true
  })
})

onUnmounted(() => {
  fenceLabelMarker?.remove()
  map?.remove()
})
</script>

<template>
  <div class="map-wrapper">
    <div ref="mapContainer" class="map-container" />
  </div>
</template>

<style scoped>
.map-wrapper { flex: 1; position: relative; overflow: hidden; }
.map-container { width: 100%; height: 100%; }
</style>
