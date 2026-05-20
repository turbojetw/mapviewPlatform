<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import type { Animal, AnimalStatus, GeoFence } from './types'
import { fetchAnimals, fetchLatestStatuses, fetchGeoFences, simulatorPushRandomAll, deleteAnimal } from './api/livestock'
import { useWebSocket } from './composables/useWebSocket'
import MapPanel from './components/MapPanel.vue'
import AnimalSidebar from './components/AnimalSidebar.vue'
import AddAnimalModal from './components/AddAnimalModal.vue'
import GeoFenceModal from './components/GeoFenceModal.vue'
import AnimalDetailPanel from './components/AnimalDetailPanel.vue'
import AlertHistoryPanel from './components/AlertHistoryPanel.vue'
import AlertToastStack from './components/AlertToastStack.vue'
import type { AlertToastItem } from './components/AlertToastStack.vue'
import DashboardPanel from './components/DashboardPanel.vue'
import FenceAssignPanel from './components/FenceAssignPanel.vue'

const animals = ref<Animal[]>([])
const animalStatuses = reactive(new Map<number, AnimalStatus>())
const geofences = ref<GeoFence[]>([])
const selectedAnimalId = ref<number | null>(null)
const showAddModal = ref(false)
const animalToEdit = ref<Animal | null>(null)
const showFenceModal = ref(false)
const showAlertPanel = ref(false)
const showDashboard = ref(false)
const assignFenceId = ref<number | null>(null)
const assignFencePanel = ref<InstanceType<typeof FenceAssignPanel>>()

const assignFence = computed(() =>
  assignFenceId.value != null ? geofences.value.find(f => f.id === assignFenceId.value) ?? null : null
)
const mapPanel = ref<InstanceType<typeof MapPanel>>()
const toast = ref<string | null>(null)

// Fence visibility — client-side only, not persisted
const hiddenFenceIds = ref<number[]>([])

// Fence pool selection
const selectedFenceId = ref<number | null>(null)

// Session alert count — increments each time a geofence breach arrives via WS
const sessionAlertCount = ref(0)

// Stacking alert toast notifications
const alertToasts = ref<AlertToastItem[]>([])
let toastUidCounter = 0

function addAlertToast(animalId: number, animalName: string, fenceName: string) {
  const uid = ++toastUidCounter
  alertToasts.value = [...alertToasts.value.slice(-4), { uid, animalId, animalName, fenceName }]
  setTimeout(() => dismissToast(uid), 8000)
}

function dismissToast(uid: number) {
  alertToasts.value = alertToasts.value.filter(t => t.uid !== uid)
}

// Derived: full Animal record for the selected animal
const selectedAnimal = computed(() =>
  selectedAnimalId.value != null
    ? animals.value.find(a => a.id === selectedAnimalId.value) ?? null
    : null
)
const selectedStatus = computed(() =>
  selectedAnimalId.value != null ? animalStatuses.get(selectedAnimalId.value) : undefined
)

// ── Draw toolbar state (sourced from MapPanel's exposed refs) ──────────────
const isDrawing = computed(() => mapPanel.value?.drawActive.value ?? false)
const drawCount = computed(() => mapPanel.value?.drawVertexCount.value ?? 0)

// ── Fence editing state ────────────────────────────────────────────────────
const editingFenceCoords = ref<[number, number][] | null>(null)
const editingFenceColor = ref('#FF6B6B')
const editUndoStack = ref<[number, number][][]>([])

// ── Fence drawing state ────────────────────────────────────────────────────
const drawnFenceCoords = ref<[number, number][] | null>(null)

function onStartDraw() {
  showFenceModal.value = false    // close modal so map is fully accessible
  drawnFenceCoords.value = null
  mapPanel.value?.startDraw()
}

function onDrawComplete(coords: [number, number][]) {
  drawnFenceCoords.value = coords
  showFenceModal.value = true     // reopen modal — watcher will pre-fill create form
}

function onDrawCancelled() {
  drawnFenceCoords.value = null   // clears the preview via watcher
  showFenceModal.value = true
}

function onStartEditing(fence: GeoFence) {
  editUndoStack.value = []
  editingFenceColor.value = fence.color
  try {
    const coords: [number, number][] = JSON.parse(fence.coordinatesJson)
    editingFenceCoords.value = coords
    mapPanel.value?.startFenceEdit({ id: fence.id, coords, color: fence.color })
  } catch {
    showToast('Failed to parse fence coordinates.')
  }
}

function onStopEditing() {
  editingFenceCoords.value = null
}

function onEditingCoordsChanged(coords: [number, number][]) {
  editingFenceCoords.value = coords
}

function pushEditUndo() {
  if (editingFenceCoords.value)
    editUndoStack.value.push(editingFenceCoords.value.map(c => [...c] as [number, number]))
}

function onFenceVertexMoved(index: number, coord: [number, number]) {
  if (!editingFenceCoords.value) return
  pushEditUndo()
  const coords = [...editingFenceCoords.value]
  coords[index] = coord
  if (index === 0 && coords.length > 1) coords[coords.length - 1] = [...coord]
  editingFenceCoords.value = coords
}

function onFenceVertexInserted(afterIndex: number, coord: [number, number]) {
  if (!editingFenceCoords.value) return
  pushEditUndo()
  const open = editingFenceCoords.value.slice(0, -1)
  open.splice(afterIndex + 1, 0, coord)
  editingFenceCoords.value = [...open, [...open[0]] as [number, number]]
}

function onFenceVertexDeleted(index: number) {
  if (!editingFenceCoords.value) return
  const open = editingFenceCoords.value.slice(0, -1)
  if (open.length <= 3) return
  pushEditUndo()
  open.splice(index, 1)
  editingFenceCoords.value = [...open, [...open[0]] as [number, number]]
}

function onEditUndo() {
  const prev = editUndoStack.value.pop()
  if (prev) editingFenceCoords.value = prev
}

// ── WebSocket ──────────────────────────────────────────────────────────────
const { connect, onAnimalUpdate, onAlert, onFenceStats, isConnected } = useWebSocket()

onAnimalUpdate((status) => {
  animalStatuses.set(status.animalId, status)
})

onAlert((n) => {
  sessionAlertCount.value++
  addAlertToast(n.animalId, n.animalName, n.fenceName)
})

onFenceStats((s) => {
  assignFencePanel.value?.applyStatsUpdate(s)
})

async function loadAll() {
  try {
    const [a, s, g] = await Promise.all([fetchAnimals(), fetchLatestStatuses(), fetchGeoFences()])
    animals.value = a
    s.forEach(status => animalStatuses.set(status.animalId, status))
    geofences.value = g
  } catch {
    showToast('Failed to load data. Is the backend running?')
  }
}

function selectAnimal(id: number) {
  selectedAnimalId.value = id
  mapPanel.value?.flyToAnimal(id)
}

function onHistoryLoaded(animalId: number, coords: [number, number][]) {
  mapPanel.value?.setHistoryTrail(animalId, coords)
}

function onEditAnimal() {
  if (selectedAnimal.value) animalToEdit.value = selectedAnimal.value
}

async function onDeleteAnimal() {
  if (!selectedAnimal.value) return
  if (!confirm(`Delete ${selectedAnimal.value.name}? This cannot be undone.`)) return
  try {
    await deleteAnimal(selectedAnimal.value.id)
    selectedAnimalId.value = null
    await loadAll()
    showToast(`${selectedAnimal.value?.name ?? 'Animal'} deleted.`)
  } catch {
    showToast('Failed to delete animal.')
  }
}

function toggleFenceVisibility(id: number) {
  const idx = hiddenFenceIds.value.indexOf(id)
  if (idx === -1) hiddenFenceIds.value = [...hiddenFenceIds.value, id]
  else hiddenFenceIds.value = hiddenFenceIds.value.filter(x => x !== id)
}

function onSelectFence(id: number) {
  selectedFenceId.value = id
  mapPanel.value?.flyToFence(id)
}

async function onSimulateAll() {
  try { await simulatorPushRandomAll() }
  catch { showToast('Simulation failed. Is the backend running?') }
}

function showToast(msg: string) {
  toast.value = msg
  setTimeout(() => { toast.value = null }, 4000)
}

onMounted(async () => {
  await loadAll()
  connect()
})
</script>

<template>
  <div class="app">
    <header class="header">
      <div class="header-brand">
        <span class="logo">🐄</span>
        <span class="title">牧场管理平台</span>
        <span class="subtitle">Livestock GPS Tracker</span>
      </div>
      <div class="header-meta">
        <button class="alert-btn" :class="{ 'has-alerts': sessionAlertCount > 0 }" @click="showAlertPanel = true" title="Alert history">
          🔔
          <span v-if="sessionAlertCount > 0" class="alert-badge">{{ sessionAlertCount }}</span>
        </button>
        <div class="conn-badge" :class="isConnected ? 'connected' : 'disconnected'">
          <span class="conn-dot" />
          {{ isConnected ? 'Live' : 'Reconnecting…' }}
        </div>
        <div class="animal-count">{{ animals.length }} animals</div>
      </div>
    </header>

    <main class="main">
      <AnimalSidebar
        :animals="animals"
        :animal-statuses="animalStatuses"
        :selected-animal-id="selectedAnimalId"
        @animal-selected="selectAnimal"
        @open-add-modal="showAddModal = true"
        @open-geofence-modal="mapPanel?.cancelDraw(); showFenceModal = true"
        @open-dashboard="showDashboard = true"
        @simulate-all="onSimulateAll"
      />
      <MapPanel
        ref="mapPanel"
        :animal-statuses="animalStatuses"
        :geofences="geofences"
        :selected-animal-id="selectedAnimalId"
        :selected-fence-id="selectedFenceId"
        :editing-fence-coords="editingFenceCoords"
        :editing-fence-color="editingFenceColor"
        :pending-fence-coords="drawnFenceCoords"
        :hidden-fence-ids="hiddenFenceIds"
        @animal-selected="selectAnimal"
        @fence-vertex-moved="onFenceVertexMoved"
        @fence-vertex-inserted="onFenceVertexInserted"
        @fence-vertex-deleted="onFenceVertexDeleted"
        @edit-undo="onEditUndo"
        @draw-complete="onDrawComplete"
        @draw-cancelled="onDrawCancelled"
      />
      <transition name="slide-right">
        <AnimalDetailPanel
          v-if="selectedAnimal"
          :animal="selectedAnimal"
          :status="selectedStatus"
          :geofences="geofences"
          @close="selectedAnimalId = null"
          @history-loaded="onHistoryLoaded"
          @edit="onEditAnimal"
          @delete="onDeleteAnimal"
          @home-fence-changed="loadAll"
        />
      </transition>
    </main>

    <AddAnimalModal
      v-if="showAddModal || animalToEdit"
      :animal="animalToEdit ?? undefined"
      :geofences="geofences"
      @close="showAddModal = false; animalToEdit = null"
      @saved="loadAll"
    />

    <GeoFenceModal
      v-if="showFenceModal"
      :geofences="geofences"
      :editing-coords="editingFenceCoords"
      :prefilled-coords="drawnFenceCoords"
      :hidden-fence-ids="hiddenFenceIds"
      :selected-fence-id="selectedFenceId"
      @close="showFenceModal = false; onStopEditing(); drawnFenceCoords = null"
      @updated="loadAll(); drawnFenceCoords = null"
      @start-editing="onStartEditing"
      @stop-editing="onStopEditing"
      @editing-coords-changed="onEditingCoordsChanged"
      @start-draw="onStartDraw"
      @toggle-visibility="toggleFenceVisibility"
      @select-fence="onSelectFence"
      @open-assign="assignFenceId = $event; showFenceModal = false"
    />

    <AlertHistoryPanel
      v-if="showAlertPanel"
      :session-count="sessionAlertCount"
      @close="showAlertPanel = false"
    />

    <DashboardPanel
      v-if="showDashboard"
      :animals="animals"
      :animal-statuses="animalStatuses"
      :session-alert-count="sessionAlertCount"
      @close="showDashboard = false"
      @select-animal="selectAnimal"
    />

    <FenceAssignPanel
      v-if="assignFence"
      ref="assignFencePanel"
      :fence="assignFence"
      :animals="animals"
      :animal-statuses="animalStatuses"
      :stats="null"
      @close="assignFenceId = null"
      @saved="loadAll"
    />

    <!-- Draw toolbar — lives in App.vue so MapLibre cannot intercept its clicks -->
    <div v-if="isDrawing" class="draw-toolbar">
      <span class="draw-instruction">
        {{ drawCount < 3
          ? `Click to place vertices (${drawCount} placed, need ${3 - drawCount} more)`
          : `${drawCount} vertices — finish or keep adding` }}
      </span>
      <div class="draw-actions">
        <button class="draw-btn undo" :disabled="drawCount === 0" @click="mapPanel?.undoVertex()" title="Undo last vertex (Ctrl+Z)">
          ↩ Undo
        </button>
        <button class="draw-btn finish" :disabled="drawCount < 3" @click="mapPanel?.finishDraw()" title="Finish polygon (Enter)">
          ✓ Finish
        </button>
        <button class="draw-btn cancel" @click="mapPanel?.cancelDraw()" title="Cancel drawing (Esc)">
          ✕
        </button>
      </div>
    </div>

    <AlertToastStack
      :items="alertToasts"
      @dismiss="dismissToast"
      @select-animal="selectAnimal"
    />

    <transition name="toast">
      <div v-if="toast" class="toast">{{ toast }}</div>
    </transition>
  </div>
</template>

<style scoped>
.app { display: flex; flex-direction: column; height: 100vh; background: var(--color-bg-sidebar); }
.header {
  height: var(--header-height); background: #13151a;
  border-bottom: 1px solid var(--color-border);
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 16px; flex-shrink: 0; z-index: 10;
}
.header-brand { display: flex; align-items: center; gap: 8px; }
.logo { font-size: 20px; }
.title { font-size: 15px; font-weight: 700; color: var(--color-text-primary); }
.subtitle { font-size: 11px; color: var(--color-text-secondary); }
.header-meta { display: flex; align-items: center; gap: 12px; }
.conn-badge {
  display: flex; align-items: center; gap: 5px;
  font-size: 11px; padding: 3px 8px;
  border-radius: 10px; border: 1px solid transparent;
}
.conn-badge.connected { color: var(--color-accent); border-color: #4CAF5033; background: #4CAF5011; }
.conn-badge.disconnected { color: var(--color-warning); border-color: #FFA72633; background: #FFA72611; }
.conn-dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; animation: pulse 2s infinite; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
.animal-count { font-size: 12px; color: var(--color-text-secondary); }

.alert-btn {
  position: relative;
  background: none; border: none; cursor: pointer;
  font-size: 16px; padding: 4px 6px; border-radius: 6px;
  color: var(--color-text-secondary); transition: all 0.15s;
}
.alert-btn:hover { background: var(--color-bg-card-hover); color: var(--color-text-primary); }
.alert-btn.has-alerts { color: var(--color-warning); }
.alert-badge {
  position: absolute; top: -2px; right: -2px;
  background: var(--color-danger); color: #fff;
  font-size: 9px; font-weight: 700; min-width: 16px; height: 16px;
  border-radius: 8px; display: flex; align-items: center; justify-content: center;
  padding: 0 3px;
}

.main { flex: 1; display: flex; overflow: hidden; }

/* Animal detail panel slide-in from right */
.slide-right-enter-active, .slide-right-leave-active { transition: width 0.25s ease, opacity 0.2s ease; overflow: hidden; }
.slide-right-enter-from, .slide-right-leave-to { width: 0 !important; opacity: 0; }
/* ── Draw toolbar ── lives at app level so map events can't block it */
.draw-toolbar {
  position: fixed;
  top: calc(var(--header-height, 52px) + 12px);
  left: 50%; transform: translateX(-50%);
  background: #13151aee; border: 1px solid #FF6B6B88;
  border-radius: 24px; padding: 7px 10px 7px 16px;
  display: flex; align-items: center; gap: 12px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.4);
  white-space: nowrap; z-index: 1500;
}
.draw-instruction { font-size: 12px; color: var(--color-text-secondary); }
.draw-actions { display: flex; gap: 6px; }
.draw-btn {
  padding: 5px 12px; border: none; border-radius: 14px;
  font-size: 12px; font-weight: 600; cursor: pointer;
  transition: opacity 0.15s;
}
.draw-btn:disabled { opacity: 0.35; cursor: not-allowed; }
.draw-btn.undo { background: #333842; color: var(--color-text-primary); }
.draw-btn.undo:not(:disabled):hover { background: #3d4452; }
.draw-btn.finish { background: var(--color-accent); color: #fff; }
.draw-btn.finish:not(:disabled):hover { background: var(--color-accent-hover); }
.draw-btn.cancel { background: transparent; color: var(--color-text-secondary); padding: 5px 8px; }
.draw-btn.cancel:hover { color: var(--color-danger); }

.toast {
  position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%);
  background: #22262f; border: 1px solid var(--color-border);
  color: var(--color-text-primary); padding: 10px 20px;
  border-radius: 8px; font-size: 13px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.4); z-index: 2000;
  max-width: 400px; text-align: center;
}
.toast-enter-active, .toast-leave-active { transition: all 0.25s ease; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(-50%) translateY(10px); }
</style>
