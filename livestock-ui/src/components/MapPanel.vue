<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import maplibregl from 'maplibre-gl'
import type { AnimalStatus, GeoFence } from '../types'

const props = defineProps<{
  animalStatuses: Map<number, AnimalStatus>
  geofences: GeoFence[]
  selectedAnimalId: number | null
  selectedFenceId: number | null
  editingFenceCoords: [number, number][] | null
  editingFenceColor: string
  pendingFenceCoords: [number, number][] | null  // drawn but not yet saved
  hiddenFenceIds: number[]
}>()

const emit = defineEmits<{
  (e: 'animal-selected', id: number): void
  (e: 'fence-vertex-moved', index: number, coord: [number, number]): void
  (e: 'fence-vertex-inserted', afterIndex: number, coord: [number, number]): void
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

// Vertex handles for fence editing
const vertexMarkers: maplibregl.Marker[] = []
let draggingIndex: number | null = null

// Midpoint handles for edge insertion
const midpointMarkers: maplibregl.Marker[] = []
let draggingMidpointEdge: number | null = null

// Line drag state (drag anywhere on the polygon edge to insert + move a vertex)
let lineDragEdge: number | null = null
let lineDragLastPos: [number, number] | null = null
let lineDragHasMoved = false

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
  const editingId = props.editingFenceCoords ? getEditingFenceId() : null

  const features: GeoJSON.Feature[] = props.geofences
    .filter(f => f.active && f.id !== editingId && !props.hiddenFenceIds.includes(f.id))
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

// Track which fence is being edited (we need the id to exclude from normal layer)
let _editingFenceId: number | null = null
function getEditingFenceId() { return _editingFenceId }
function setEditingFenceId(id: number | null) { _editingFenceId = id }

// ─── Edit preview layer ────────────────────────────────────────────────────

function initEditPreviewLayer() {
  if (map.getSource('fence-edit-preview')) return
  map.addSource('fence-edit-preview', {
    type: 'geojson',
    data: { type: 'FeatureCollection', features: [] }
  })
  map.addLayer({ id: 'fence-edit-fill', type: 'fill', source: 'fence-edit-preview',
    paint: { 'fill-color': ['get', 'color'], 'fill-opacity': 0.18 } })
  map.addLayer({ id: 'fence-edit-line', type: 'line', source: 'fence-edit-preview',
    paint: { 'line-color': ['get', 'color'], 'line-width': 2.5,
             'line-dasharray': [4, 2], 'line-opacity': 1 } })
  // Transparent wider hit area so line drag works away from vertex/midpoint markers
  map.addLayer({ id: 'fence-edit-hit', type: 'line', source: 'fence-edit-preview',
    paint: { 'line-width': 20, 'line-opacity': 0 } })
  setupLineDrag()
}

function updateEditPreview(coords: [number, number][], color: string) {
  if (!map || !map.isStyleLoaded()) return
  initEditPreviewLayer()

  if (coords.length < 3) {
    const empty: GeoJSON.FeatureCollection = { type: 'FeatureCollection', features: [] }
    ;(map.getSource('fence-edit-preview') as maplibregl.GeoJSONSource).setData(empty)
    return
  }

  // Ensure closed ring
  const ring = isClosedRing(coords) ? coords : [...coords, coords[0]]
  const geojson: GeoJSON.FeatureCollection = {
    type: 'FeatureCollection',
    features: [{
      type: 'Feature',
      properties: { color },
      geometry: { type: 'Polygon', coordinates: [ring] }
    }]
  }
  ;(map.getSource('fence-edit-preview') as maplibregl.GeoJSONSource).setData(geojson)
}

function clearEditPreview() {
  if (!map || !map.isStyleLoaded() || !map.getSource('fence-edit-preview')) return
  const empty: GeoJSON.FeatureCollection = { type: 'FeatureCollection', features: [] }
  ;(map.getSource('fence-edit-preview') as maplibregl.GeoJSONSource).setData(empty)
}

function isClosedRing(coords: [number, number][]): boolean {
  if (coords.length < 2) return false
  return coords[0][0] === coords[coords.length - 1][0] &&
         coords[0][1] === coords[coords.length - 1][1]
}

// ─── Vertex drag handles ───────────────────────────────────────────────────

function makeVertexEl(index: number): HTMLElement {
  const el = document.createElement('div')
  el.style.cssText = `
    width:14px;height:14px;border-radius:50%;
    background:#fff;border:2.5px solid ${props.editingFenceColor || '#FF6B6B'};
    cursor:grab;box-shadow:0 1px 4px rgba(0,0,0,0.5);
    position:relative;z-index:10;
  `
  el.title = `Vertex ${index + 1} — drag to move`
  return el
}

function setupVertexMarkers(coords: [number, number][]) {
  clearVertexMarkers()
  // Don't show the closing duplicate point
  const points = isClosedRing(coords) ? coords.slice(0, -1) : coords

  points.forEach((coord, i) => {
    const el = makeVertexEl(i)
    const marker = new maplibregl.Marker({ element: el, draggable: true })
      .setLngLat(coord)
      .addTo(map)

    marker.on('dragstart', () => {
      draggingIndex = i
      el.style.cursor = 'grabbing'
    })

    marker.on('drag', () => {
      // Live-update only the preview polygon during drag
      if (props.editingFenceCoords) {
        const pos = marker.getLngLat()
        const updated = props.editingFenceCoords.map((c, idx) => {
          if (idx === i) return [pos.lng, pos.lat] as [number, number]
          // Also update closing point if dragging first vertex
          if (i === 0 && idx === props.editingFenceCoords!.length - 1) return [pos.lng, pos.lat] as [number, number]
          return c
        })
        updateEditPreview(updated, props.editingFenceColor)
        // Live-reposition the two adjacent midpoint markers
        const open = isClosedRing(updated) ? updated.slice(0, -1) : updated
        const n = open.length
        const prevEdge = (i - 1 + n) % n
        if (midpointMarkers[prevEdge] != null && prevEdge !== draggingMidpointEdge)
          midpointMarkers[prevEdge].setLngLat(midpoint(open[(i - 1 + n) % n], open[i]))
        if (midpointMarkers[i] != null && i !== draggingMidpointEdge)
          midpointMarkers[i].setLngLat(midpoint(open[i], open[(i + 1) % n]))
      }
    })

    marker.on('dragend', () => {
      draggingIndex = null
      el.style.cursor = 'grab'
      const pos = marker.getLngLat()
      emit('fence-vertex-moved', i, [pos.lng, pos.lat])
    })

    vertexMarkers.push(marker)
  })
  setupMidpointMarkers(coords)
}

function clearVertexMarkers() {
  clearMidpointMarkers()
  vertexMarkers.forEach(m => m.remove())
  vertexMarkers.length = 0
  draggingIndex = null
}

// Reposition vertex markers when coords change from table edits
function repositionVertexMarkers(coords: [number, number][]) {
  const points = isClosedRing(coords) ? coords.slice(0, -1) : coords
  if (points.length !== vertexMarkers.length) {
    // Vertex count changed — rebuild all
    setupVertexMarkers(coords)
    return
  }
  points.forEach((coord, i) => {
    if (i !== draggingIndex) {
      vertexMarkers[i]?.setLngLat(coord)
    }
  })
  repositionMidpointMarkers(coords)
}

// ─── Midpoint drag handles ─────────────────────────────────────────────────

function midpoint(a: [number, number], b: [number, number]): [number, number] {
  return [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2]
}

function makeMidpointEl(): HTMLElement {
  const el = document.createElement('div')
  el.style.cssText = `
    width:10px;height:10px;border-radius:50%;
    background:rgba(255,255,255,0.45);border:2px solid ${props.editingFenceColor || '#FF6B6B'};
    cursor:grab;box-shadow:0 1px 3px rgba(0,0,0,0.4);
    position:relative;z-index:9;
  `
  el.title = 'Drag to insert vertex on this edge'
  return el
}

function setupMidpointMarkers(coords: [number, number][]) {
  clearMidpointMarkers()
  const open = isClosedRing(coords) ? coords.slice(0, -1) : coords
  const n = open.length
  if (n < 2) return

  open.forEach((coord, i) => {
    const next = open[(i + 1) % n]
    const el = makeMidpointEl()

    const marker = new maplibregl.Marker({ element: el, draggable: true })
      .setLngLat(midpoint(coord, next))
      .addTo(map)

    marker.on('dragstart', () => {
      draggingMidpointEdge = i
      el.style.cursor = 'grabbing'
    })

    marker.on('drag', () => {
      if (props.editingFenceCoords) {
        const currentOpen = isClosedRing(props.editingFenceCoords)
          ? props.editingFenceCoords.slice(0, -1)
          : props.editingFenceCoords
        const pos = marker.getLngLat()
        const newOpen = [...currentOpen]
        newOpen.splice(i + 1, 0, [pos.lng, pos.lat] as [number, number])
        updateEditPreview([...newOpen, [...newOpen[0]] as [number, number]], props.editingFenceColor)
      }
    })

    marker.on('dragend', () => {
      draggingMidpointEdge = null
      el.style.cursor = 'grab'
      const pos = marker.getLngLat()
      emit('fence-vertex-inserted', i, [pos.lng, pos.lat])
    })

    midpointMarkers.push(marker)
  })
}

function clearMidpointMarkers() {
  midpointMarkers.forEach(m => m.remove())
  midpointMarkers.length = 0
  draggingMidpointEdge = null
}

function repositionMidpointMarkers(coords: [number, number][]) {
  const open = isClosedRing(coords) ? coords.slice(0, -1) : coords
  const n = open.length
  if (n !== midpointMarkers.length) {
    setupMidpointMarkers(coords)
    return
  }
  open.forEach((coord, i) => {
    if (i !== draggingMidpointEdge)
      midpointMarkers[i]?.setLngLat(midpoint(coord, open[(i + 1) % n]))
  })
}

// ─── Line drag (click anywhere on edge to insert + drag vertex) ────────────

function distPtToSeg(p: [number, number], a: [number, number], b: [number, number]): number {
  const dx = b[0] - a[0], dy = b[1] - a[1]
  if (dx === 0 && dy === 0) return Math.hypot(p[0] - a[0], p[1] - a[1])
  const t = Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / (dx * dx + dy * dy)))
  return Math.hypot(p[0] - (a[0] + t * dx), p[1] - (a[1] + t * dy))
}

function findClosestEdgeIndex(open: [number, number][], pt: [number, number]): number {
  let bestIdx = 0, bestDist = Infinity
  for (let i = 0; i < open.length; i++) {
    const d = distPtToSeg(pt, open[i], open[(i + 1) % open.length])
    if (d < bestDist) { bestDist = d; bestIdx = i }
  }
  return bestIdx
}

function setupLineDrag() {
  map.on('mouseenter', 'fence-edit-hit', () => {
    if (!props.editingFenceCoords || drawActive.value) return
    map.getCanvas().style.cursor = 'crosshair'
  })
  map.on('mouseleave', 'fence-edit-hit', () => {
    if (!drawActive.value && lineDragEdge === null) map.getCanvas().style.cursor = ''
  })

  map.on('mousedown', 'fence-edit-hit', (e) => {
    if (!props.editingFenceCoords || drawActive.value) return
    e.preventDefault()

    const open = isClosedRing(props.editingFenceCoords)
      ? props.editingFenceCoords.slice(0, -1)
      : props.editingFenceCoords
    const clickPt: [number, number] = [e.lngLat.lng, e.lngLat.lat]
    lineDragEdge = findClosestEdgeIndex(open, clickPt)
    lineDragLastPos = clickPt
    lineDragHasMoved = false
    map.getCanvas().style.cursor = 'grabbing'
    map.dragPan.disable()

    function onMove(me: maplibregl.MapMouseEvent) {
      if (lineDragEdge === null || !props.editingFenceCoords) return
      lineDragHasMoved = true
      const pos: [number, number] = [me.lngLat.lng, me.lngLat.lat]
      lineDragLastPos = pos
      const co = isClosedRing(props.editingFenceCoords)
        ? props.editingFenceCoords.slice(0, -1)
        : props.editingFenceCoords
      const newOpen = [...co]
      newOpen.splice(lineDragEdge + 1, 0, pos)
      updateEditPreview([...newOpen, [...newOpen[0]] as [number, number]], props.editingFenceColor)
    }

    function finishLineDrag(finalPos: [number, number] | null) {
      map.off('mousemove', onMove)
      map.off('mouseup', onUp)
      window.removeEventListener('mouseup', onWindowUp)
      map.getCanvas().style.cursor = ''
      map.dragPan.enable()
      if (lineDragEdge !== null) {
        const pos = finalPos ?? lineDragLastPos
        if (pos && lineDragHasMoved) {
          emit('fence-vertex-inserted', lineDragEdge, pos)
        } else if (props.editingFenceCoords) {
          // Plain click with no drag — restore preview without inserting
          updateEditPreview(props.editingFenceCoords, props.editingFenceColor)
        }
      }
      lineDragEdge = null
      lineDragLastPos = null
      lineDragHasMoved = false
    }

    function onUp(me: maplibregl.MapMouseEvent) {
      finishLineDrag([me.lngLat.lng, me.lngLat.lat])
    }

    function onWindowUp() {
      finishLineDrag(lineDragLastPos)
    }

    map.on('mousemove', onMove)
    map.on('mouseup', onUp)
    window.addEventListener('mouseup', onWindowUp, { once: true })
  })
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

watch(() => props.editingFenceCoords, (coords) => {
  if (!map || !mapReady.value) return
  if (!coords) {
    clearVertexMarkers()
    clearEditPreview()
    setEditingFenceId(null)
    updateGeofences()
    return
  }
  updateEditPreview(coords, props.editingFenceColor)
  repositionVertexMarkers(coords)
  updateGeofences() // re-render normal layer excluding the editing fence
}, { deep: true })

watch(() => props.editingFenceColor, (color) => {
  if (props.editingFenceCoords) {
    updateEditPreview(props.editingFenceCoords, color)
    vertexMarkers.forEach(m => { m.getElement().style.borderColor = color })
    midpointMarkers.forEach(m => { m.getElement().style.borderColor = color })
  }
})

// ─── Public API ────────────────────────────────────────────────────────────

function flyToAnimal(animalId: number) {
  const status = props.animalStatuses.get(animalId)
  if (status && map) map.flyTo({ center: [status.lng, status.lat], zoom: 15, duration: 800 })
}

function startFenceEdit(fence: { id: number; coords: [number, number][]; color: string }) {
  setEditingFenceId(fence.id)
  updateGeofences()
  setupVertexMarkers(fence.coords)
  updateEditPreview(fence.coords, fence.color)
  // Fly to fence — right padding accounts for the 420px edit panel
  if (fence.coords.length > 0 && map) {
    const lngs = fence.coords.map(c => c[0])
    const lats = fence.coords.map(c => c[1])
    map.fitBounds([
      [Math.min(...lngs), Math.min(...lats)],
      [Math.max(...lngs), Math.max(...lats)]
    ], { padding: { top: 80, bottom: 60, left: 60, right: 460 }, duration: 600 })
  }
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

defineExpose({ flyToAnimal, startFenceEdit, startDraw, cancelDraw, finishDraw, undoVertex, drawActive, drawVertexCount, setHistoryTrail, flyToFence })

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
    initEditPreviewLayer()
    props.animalStatuses.forEach(status => updateMarker(status))
    // Setting mapReady triggers the combined watcher which calls updateGeofences().
    // This ensures geofences render whether the API responded before or after map load.
    mapReady.value = true
  })
})

onUnmounted(() => {
  clearVertexMarkers()
  fenceLabelMarker?.remove()
  map?.remove()
})
</script>

<template>
  <div class="map-wrapper">
    <div ref="mapContainer" class="map-container" />

    <!-- Fence edit banner -->
    <div v-if="editingFenceCoords && !drawActive" class="edit-mode-banner">
      ✏️ Editing fence — drag vertices or update coordinates in the panel
    </div>
  </div>
</template>

<style scoped>
.map-wrapper { flex: 1; position: relative; overflow: hidden; }
.map-container { width: 100%; height: 100%; }


.edit-mode-banner {
  position: absolute; top: 10px; left: 50%; transform: translateX(-50%);
  background: #1a1d23ee; border: 1px solid #42A5F5aa;
  color: var(--color-info); font-size: 12px; padding: 6px 14px;
  border-radius: 20px; pointer-events: none;
  white-space: nowrap; box-shadow: 0 2px 8px rgba(0,0,0,0.3);
}
</style>
