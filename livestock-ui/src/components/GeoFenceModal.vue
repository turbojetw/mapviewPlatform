<script setup lang="ts">
import { ref, watch } from 'vue'
import type { GeoFence } from '../types'
import { createGeoFence, updateGeoFence, deleteGeoFence } from '../api/livestock'
import axios from 'axios'

const props = defineProps<{
  geofences: GeoFence[]
  prefilledCoords: [number, number][] | null // coords drawn on map → pre-fill create form
  hiddenFenceIds: number[]
  selectedFenceId: number | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'updated'): void
  (e: 'start-draw'): void
  (e: 'toggle-visibility', id: number): void
  (e: 'select-fence', id: number): void
  (e: 'open-assign', id: number): void
}>()

type View = 'list' | 'create' | 'edit'
const view = ref<View>('list')

// ── Inline style editor ──────────────────────────────────────────────────────
const styleOpenId = ref<number | null>(null)
const styleForm = ref({ color: '#FF6B6B', fillOpacityPct: 15, strokeWidth: 2 })
const styleLoading = ref(false)

function openStyleEditor(fence: GeoFence) {
  styleOpenId.value = fence.id
  styleForm.value = {
    color: fence.color,
    fillOpacityPct: Math.round((fence.fillOpacity ?? 0.15) * 100),
    strokeWidth: fence.strokeWidth ?? 2,
  }
}

async function applyStyle(fence: GeoFence) {
  styleLoading.value = true
  try {
    await updateGeoFence(fence.id, {
      name: fence.name,
      coordinatesJson: fence.coordinatesJson,
      color: styleForm.value.color,
      fillOpacity: styleForm.value.fillOpacityPct / 100,
      strokeWidth: styleForm.value.strokeWidth,
      alertOnExit: fence.alertOnExit,
      active: fence.active,
    })
    styleOpenId.value = null
    emit('updated')
  } finally {
    styleLoading.value = false
  }
}

// ── Create form ─────────────────────────────────────────────────────────────
const createForm = ref({ name: '', coordinatesJson: '', color: '#FF6B6B', alertOnExit: true })
const createError = ref('')
const createLoading = ref(false)

// Pre-fill create form when user draws a fence on the map.
// immediate:true ensures this fires on mount when the modal is re-opened
// with existing drawn coords (watcher alone won't fire for initial prop value).
// createForm must be declared BEFORE this watch because immediate:true fires
// the callback synchronously during setup — before any later declarations.
watch(() => props.prefilledCoords, (coords) => {
  if (!coords) return
  createForm.value.coordinatesJson = JSON.stringify(coords)
  view.value = 'create'
}, { immediate: true })

async function submitCreate() {
  if (!createForm.value.name || !createForm.value.coordinatesJson) {
    createError.value = 'Name and coordinates are required.'
    return
  }
  try { JSON.parse(createForm.value.coordinatesJson) } catch {
    createError.value = 'Invalid JSON coordinate format.'
    return
  }
  createLoading.value = true
  createError.value = ''
  try {
    const created = await createGeoFence(createForm.value)
    createForm.value = { name: '', coordinatesJson: '', color: '#FF6B6B', alertOnExit: true }
    view.value = 'list'
    emit('updated')
    emit('select-fence', created.id)
  } catch (e: any) {
    createError.value = e?.response?.data?.message ?? 'Failed to create.'
  } finally { createLoading.value = false }
}

// ── Edit form ────────────────────────────────────────────────────────────────
const editingFence = ref<GeoFence | null>(null)
const editName = ref('')
const editColor = ref('#FF6B6B')
const editFillOpacityPct = ref(15)
const editStrokeWidth = ref(2)
const editAlertOnExit = ref(true)
const editError = ref('')
const editLoading = ref(false)

function openFenceForEdit(fence: GeoFence) {
  editingFence.value = fence
  editName.value = fence.name
  editColor.value = fence.color
  editFillOpacityPct.value = Math.round((fence.fillOpacity ?? 0.15) * 100)
  editStrokeWidth.value = fence.strokeWidth ?? 2
  editAlertOnExit.value = fence.alertOnExit
  editError.value = ''
  view.value = 'edit'
}

async function saveEdit() {
  if (!editingFence.value) return
  editLoading.value = true
  editError.value = ''
  try {
    await axios.put(`/api/geofences/${editingFence.value.id}`, {
      name: editName.value,
      coordinatesJson: editingFence.value.coordinatesJson,
      color: editColor.value,
      fillOpacity: editFillOpacityPct.value / 100,
      strokeWidth: editStrokeWidth.value,
      alertOnExit: editAlertOnExit.value,
      active: true,
    })
    cancelEdit()
    emit('updated')
  } catch (e: any) {
    editError.value = e?.response?.data?.message ?? 'Failed to save.'
  } finally { editLoading.value = false }
}

function cancelEdit() {
  view.value = 'list'
  editingFence.value = null
  editError.value = ''
}

async function removeFence(id: number) {
  if (!confirm('Delete this geofence?')) return
  await deleteGeoFence(id)
  emit('updated')
}

async function toggleActive(fence: GeoFence) {
  await updateGeoFence(fence.id, {
    name: fence.name,
    coordinatesJson: fence.coordinatesJson,
    color: fence.color,
    alertOnExit: fence.alertOnExit,
    active: !fence.active,
  })
  emit('updated')
}

function onBackdrop(e: MouseEvent) {
  if ((e.target as HTMLElement).classList.contains('backdrop')) {
    if (view.value === 'edit') cancelEdit()
    emit('close')
  }
}

const SAMPLE = '[[105.496,28.196],[105.504,28.196],[105.504,28.204],[105.496,28.204],[105.496,28.196]]'
</script>

<template>
  <div class="backdrop" @click="onBackdrop">
    <div class="modal" role="dialog">

      <!-- ── Header ── -->
      <div class="modal-header">
        <div class="header-left">
          <button v-if="view !== 'list'" class="back-btn" @click="view === 'edit' ? cancelEdit() : (view = 'list')">‹</button>
          <h2>{{ view === 'list' ? 'Geofences' : view === 'create' ? 'New Fence' : 'Edit Fence' }}</h2>
        </div>
        <button class="close-btn" @click="view === 'edit' ? cancelEdit() : null; $emit('close')">✕</button>
      </div>

      <!-- ═══════════════ LIST VIEW ═══════════════ -->
      <div v-if="view === 'list'" class="modal-body">
        <div v-if="geofences.length === 0" class="empty">
          No geofences yet. Click below to create one.
        </div>
        <template v-for="fence in geofences" :key="fence.id">
          <div
            class="fence-row"
            :class="{ inactive: !fence.active, selected: fence.id === selectedFenceId }"
            :title="fence.active ? 'Click to focus on map' : ''"
            @click="fence.active && $emit('select-fence', fence.id)"
          >
            <span class="color-swatch" :style="{ background: fence.color }" />
            <span class="fence-name">{{ fence.name }}</span>
            <span class="fence-meta">{{ fence.alertOnExit ? '🔔' : '' }}</span>
            <button
              class="toggle-btn"
              :class="fence.active ? 'toggle-on' : 'toggle-off'"
              :title="fence.active ? 'Disable fence' : 'Enable fence'"
              @click.stop="toggleActive(fence)"
            >{{ fence.active ? 'On' : 'Off' }}</button>
            <button
              class="icon-btn style-btn"
              :class="{ active: styleOpenId === fence.id }"
              title="Edit appearance"
              :disabled="!fence.active"
              @click.stop="styleOpenId === fence.id ? styleOpenId = null : openStyleEditor(fence)"
            >🎨</button>
            <button
              class="icon-btn eye-btn"
              :class="{ hidden: hiddenFenceIds.includes(fence.id) }"
              :title="hiddenFenceIds.includes(fence.id) ? 'Show on map' : 'Hide from map'"
              :disabled="!fence.active"
              @click.stop="$emit('toggle-visibility', fence.id)"
            >{{ hiddenFenceIds.includes(fence.id) ? '🙈' : '👁' }}</button>
            <button class="icon-btn assign-btn" title="Assign animals" :disabled="!fence.active" @click.stop="$emit('open-assign', fence.id)">👥</button>
            <button class="icon-btn edit-btn" title="Edit" :disabled="!fence.active" @click.stop="openFenceForEdit(fence)">✏️</button>
            <button class="icon-btn del-btn" title="Delete" @click.stop="removeFence(fence.id)">✕</button>
          </div>

          <!-- Inline style editor — inside the template so 'fence' is in scope -->
          <div v-if="styleOpenId === fence.id" class="style-panel">
            <div class="style-row">
              <span class="style-label">Color</span>
              <input type="color" v-model="styleForm.color" class="style-color-input" />
              <span class="style-preview" :style="{ background: styleForm.color }" />
              <span class="style-hex">{{ styleForm.color }}</span>
            </div>
            <div class="style-row">
              <span class="style-label">Fill</span>
              <input type="range" min="0" max="60" step="1" v-model.number="styleForm.fillOpacityPct" class="style-slider" />
              <span class="style-val">{{ styleForm.fillOpacityPct }}%</span>
            </div>
            <div class="style-row">
              <span class="style-label">Border</span>
              <input type="range" min="1" max="8" step="0.5" v-model.number="styleForm.strokeWidth" class="style-slider" />
              <span class="style-val">{{ styleForm.strokeWidth }}px</span>
            </div>
            <div class="style-actions">
              <button class="btn-style-cancel" @click="styleOpenId = null">Cancel</button>
              <button class="btn-style-apply" :disabled="styleLoading" @click="applyStyle(fence)">
                {{ styleLoading ? 'Saving…' : 'Apply' }}
              </button>
            </div>
          </div>
        </template>

        <div class="create-row">
          <button class="btn-create" @click="view = 'create'">+ Coordinates</button>
          <button class="btn-draw" @click="$emit('start-draw')">🖊️ Draw on Map</button>
        </div>
      </div>

      <!-- ═══════════════ CREATE VIEW ═══════════════ -->
      <div v-else-if="view === 'create'" class="modal-body">
        <form @submit.prevent="submitCreate">
          <label>Name *<input v-model="createForm.name" type="text" placeholder="e.g. North Pasture" /></label>
          <label>
            Color
            <div class="color-row">
              <input v-model="createForm.color" type="color" class="color-input" />
              <span class="color-hex">{{ createForm.color }}</span>
            </div>
          </label>
          <label>
            Coordinates * (JSON array of [lng, lat] pairs)
            <textarea v-model="createForm.coordinatesJson" rows="4" :placeholder="SAMPLE" />
            <span class="hint">First and last point must be identical to close the ring.</span>
          </label>
          <label class="checkbox-label">
            <input v-model="createForm.alertOnExit" type="checkbox" />
            Alert when animal exits this fence
          </label>
          <div v-if="createError" class="error-msg">{{ createError }}</div>
          <div class="row-actions">
            <button type="button" class="btn-cancel" @click="view = 'list'">Cancel</button>
            <button type="submit" class="btn-submit" :disabled="createLoading">
              {{ createLoading ? 'Saving…' : 'Create' }}
            </button>
          </div>
        </form>
      </div>

      <!-- ═══════════════ EDIT VIEW ═══════════════ -->
      <div v-else-if="view === 'edit'" class="modal-body">
        <div class="field-row">
          <label style="flex:1">
            Name
            <input v-model="editName" type="text" />
          </label>
          <label>
            Color
            <div class="color-row">
              <input v-model="editColor" type="color" class="color-input" />
            </div>
          </label>
        </div>

        <div class="field-row">
          <label style="flex:1">
            Fill opacity
            <div class="slider-label-row">
              <input type="range" min="0" max="60" step="1" v-model.number="editFillOpacityPct" class="style-slider" />
              <span class="style-val">{{ editFillOpacityPct }}%</span>
            </div>
          </label>
          <label style="flex:1">
            Border width
            <div class="slider-label-row">
              <input type="range" min="1" max="8" step="0.5" v-model.number="editStrokeWidth" class="style-slider" />
              <span class="style-val">{{ editStrokeWidth }}px</span>
            </div>
          </label>
        </div>

        <label class="checkbox-label">
          <input v-model="editAlertOnExit" type="checkbox" />
          Alert when animal exits
        </label>

        <div v-if="editError" class="error-msg">{{ editError }}</div>

        <div class="row-actions">
          <button class="btn-cancel" @click="cancelEdit">Cancel</button>
          <button class="btn-submit" :disabled="editLoading" @click="saveEdit">
            {{ editLoading ? 'Saving…' : 'Save Fence' }}
          </button>
        </div>
      </div>

    </div>
  </div>
</template>

<style scoped>
.backdrop {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.6);
  display: flex; align-items: center; justify-content: center;
  z-index: 1000;
}
.modal {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  width: 420px; max-width: 95vw;
  max-height: 88vh; display: flex; flex-direction: column;
  box-shadow: 0 20px 60px rgba(0,0,0,0.5);
}
.modal-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}
.header-left { display: flex; align-items: center; gap: 8px; }
.back-btn { background: none; border: none; color: var(--color-text-secondary); font-size: 20px; cursor: pointer; padding: 0 4px; line-height: 1; }
.back-btn:hover { color: var(--color-text-primary); }
.modal-header h2 { font-size: 15px; font-weight: 600; }
.close-btn { background: none; border: none; color: var(--color-text-secondary); font-size: 15px; cursor: pointer; padding: 2px 6px; }

.modal-body {
  padding: 14px 18px;
  display: flex; flex-direction: column; gap: 10px;
  overflow-y: auto; flex: 1;
}
.modal-body::-webkit-scrollbar { width: 4px; }
.modal-body::-webkit-scrollbar-thumb { background: var(--color-border); border-radius: 2px; }

.empty { font-size: 12px; color: var(--color-text-secondary); font-style: italic; text-align: center; padding: 12px 0; }

/* ── Fence list rows ── */
.fence-row {
  display: flex; align-items: center; gap: 8px;
  padding: 7px 10px;
  background: var(--color-bg-sidebar);
  border-radius: 5px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: background 0.12s, border-color 0.12s;
}
.fence-row:not(.inactive):hover { background: var(--color-bg-card-hover); border-color: var(--color-border); }
.fence-row.selected { border-color: var(--color-accent); background: #4CAF5010; }
.fence-row.inactive { opacity: 0.45; cursor: default; }

.color-swatch { width: 12px; height: 12px; border-radius: 3px; flex-shrink: 0; }
.fence-name { flex: 1; font-size: 13px; }
.fence-meta { font-size: 13px; }
.icon-btn { background: none; border: none; cursor: pointer; padding: 2px 5px; font-size: 13px; border-radius: 3px; }
.style-btn { color: var(--color-text-secondary); }
.style-btn:hover { background: #ffffff11; }
.style-btn.active { background: #FFA72622; color: var(--color-warning); }
.eye-btn { color: var(--color-text-secondary); opacity: 0.9; }
.eye-btn:hover { background: #ffffff11; }
.eye-btn.hidden { opacity: 0.4; }

/* ── Inline style editor ── */
.style-panel {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 10px 12px;
  display: flex; flex-direction: column; gap: 8px;
  margin-top: -4px; margin-bottom: 2px;
}
.style-row {
  display: flex; align-items: center; gap: 8px; font-size: 12px;
}
.style-label { color: var(--color-text-secondary); width: 44px; flex-shrink: 0; }
.style-color-input { width: 32px; height: 26px; border: 1px solid var(--color-border); border-radius: 4px; padding: 1px; cursor: pointer; }
.style-preview { width: 16px; height: 16px; border-radius: 3px; flex-shrink: 0; }
.style-hex { font-family: monospace; font-size: 11px; color: var(--color-text-secondary); }
.style-slider { flex: 1; accent-color: var(--color-accent); height: 4px; cursor: pointer; }
.style-val { width: 36px; text-align: right; color: var(--color-text-primary); font-size: 11px; }
.style-actions { display: flex; gap: 6px; justify-content: flex-end; margin-top: 2px; }
.btn-style-cancel {
  padding: 4px 12px; background: transparent;
  border: 1px solid var(--color-border); border-radius: 4px;
  color: var(--color-text-secondary); font-size: 12px; cursor: pointer;
}
.btn-style-apply {
  padding: 4px 12px; background: var(--color-accent);
  border: none; border-radius: 4px;
  color: #fff; font-size: 12px; font-weight: 600; cursor: pointer;
}
.btn-style-apply:disabled { opacity: 0.5; cursor: not-allowed; }

.slider-label-row { display: flex; align-items: center; gap: 6px; margin-top: 2px; }
.assign-btn { color: var(--color-text-secondary); }
.assign-btn:hover { background: #ffffff11; }
.edit-btn { color: var(--color-info); }
.edit-btn:hover { background: #42A5F522; }
.del-btn { color: var(--color-danger); }
.del-btn:hover { background: #ef535022; }
.toggle-btn {
  font-size: 10px; font-weight: 700; letter-spacing: 0.03em;
  padding: 2px 7px; border-radius: 10px; border: none; cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.toggle-on  { background: #4CAF5022; color: var(--color-accent); border: 1px solid #4CAF5055; }
.toggle-on:hover  { background: #4CAF5044; }
.toggle-off { background: #33384222; color: var(--color-text-secondary); border: 1px solid #ffffff22; }
.toggle-off:hover { background: #33384244; }
.create-row { display: flex; gap: 6px; margin-top: 4px; }
.btn-create {
  flex: 1; padding: 8px;
  background: transparent; border: 1px dashed var(--color-border);
  border-radius: 5px; color: var(--color-text-secondary);
  font-size: 13px; cursor: pointer;
}
.btn-create:hover { border-color: var(--color-accent); color: var(--color-accent); }
.btn-draw {
  flex: 1; padding: 8px;
  background: transparent; border: 1px dashed #FF6B6B88;
  border-radius: 5px; color: #FF6B6B;
  font-size: 13px; cursor: pointer;
}
.btn-draw:hover { background: #FF6B6B11; border-color: #FF6B6B; }

/* ── Form fields ── */
label { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--color-text-secondary); }
.checkbox-label { flex-direction: row; align-items: center; font-size: 13px; color: var(--color-text-primary); gap: 8px; }
.field-row { display: flex; gap: 10px; align-items: flex-start; }
input[type="text"], textarea {
  background: var(--color-bg-sidebar); border: 1px solid var(--color-border);
  border-radius: 5px; padding: 7px 10px; color: var(--color-text-primary);
  font-size: 12px; outline: none;
}
input[type="text"]:focus, textarea:focus { border-color: var(--color-accent); }
textarea { resize: vertical; font-family: monospace; }
.color-row { display: flex; align-items: center; gap: 6px; }
.color-input { width: 40px; height: 32px; border: 1px solid var(--color-border); border-radius: 5px; padding: 2px; cursor: pointer; }
.color-hex { font-size: 11px; color: var(--color-text-secondary); font-family: monospace; }
.hint { font-size: 10px; color: var(--color-text-secondary); }

/* ── Actions ── */
.row-actions { display: flex; gap: 8px; justify-content: flex-end; }
.btn-cancel { padding: 8px 16px; background: transparent; border: 1px solid var(--color-border); border-radius: 5px; color: var(--color-text-secondary); font-size: 13px; cursor: pointer; }
.btn-submit { padding: 8px 16px; background: var(--color-accent); border: none; border-radius: 5px; color: #fff; font-size: 13px; font-weight: 600; cursor: pointer; }
.btn-submit:disabled { opacity: 0.5; cursor: not-allowed; }
.error-msg { color: var(--color-danger); font-size: 12px; }
</style>
