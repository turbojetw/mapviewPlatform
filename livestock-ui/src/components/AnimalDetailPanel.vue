<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { Animal, AnimalStatus, GeoFence, LocationHistory, AnimalEvent } from '../types'
import { fetchHistory, simulatorPushRandom, setAnimalHomeFence, fetchAnimalEvents, createAnimalEvent, deleteAnimalEvent } from '../api/livestock'
import { timeAgo } from '../utils/time'

const props = defineProps<{
  animal: Animal
  status: AnimalStatus | undefined
  geofences: GeoFence[]
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'history-loaded', animalId: number, coords: [number, number][]): void
  (e: 'edit'): void
  (e: 'delete'): void
  (e: 'home-fence-changed'): void
}>()

const STALE_MS = 60 * 60 * 1000
const history = ref<LocationHistory[]>([])
const historyLoading = ref(false)
const historyRange = ref<'24h' | '7d'>('24h')
const simulating = ref(false)

const SPECIES_EMOJI: Record<string, string> = {
  GOOSE: '🦢', CHICKEN: '🐔', SHEEP: '🐑', PIG: '🐷'
}

const isStale = computed(() =>
  !!props.status && Date.now() - new Date(props.status.timestamp).getTime() > STALE_MS
)

const noSignal = computed(() => !props.status)

function batteryColor(pct: number): string {
  if (pct > 50) return '#4CAF50'
  if (pct > 20) return '#FFA726'
  return '#ef5350'
}

function hdopColor(hdop: number): string {
  if (hdop < 1.5) return '#4CAF50'
  if (hdop < 3) return '#FFA726'
  return '#ef5350'
}

async function loadHistory() {
  historyLoading.value = true
  history.value = []
  try {
    const to = new Date()
    const from = new Date(to)
    if (historyRange.value === '24h') from.setHours(from.getHours() - 24)
    else from.setDate(from.getDate() - 7)
    history.value = await fetchHistory(props.animal.id, from.toISOString(), to.toISOString())
    const coords = history.value
      .filter(h => h.lat != null && h.lng != null)
      .map(h => [h.lng, h.lat] as [number, number])
    emit('history-loaded', props.animal.id, coords)
  } finally {
    historyLoading.value = false
  }
}

async function simulate() {
  simulating.value = true
  try { await simulatorPushRandom(props.animal.id) }
  finally { simulating.value = false }
}

const recentFixes = computed(() => [...history.value].reverse().slice(0, 30))

// ── Home fence ───────────────────────────────────────────────────────────────
const activeFences = computed(() => props.geofences.filter(f => f.active))
const homeFence = computed(() =>
  props.animal.homeGeofenceId != null
    ? activeFences.value.find(f => f.id === props.animal.homeGeofenceId) ?? null
    : null
)
const editingFence = ref(false)
const selectedFenceId = ref<number | null>(props.animal.homeGeofenceId ?? null)
const fenceSaving = ref(false)
const fenceError = ref('')

watch(() => props.animal.homeGeofenceId, (v) => { selectedFenceId.value = v ?? null })

async function saveHomeFence() {
  fenceSaving.value = true
  fenceError.value = ''
  try {
    await setAnimalHomeFence(props.animal.id, selectedFenceId.value)
    editingFence.value = false
    emit('home-fence-changed')
  } catch {
    fenceError.value = 'Failed to save.'
  } finally {
    fenceSaving.value = false
  }
}

function cancelFenceEdit() {
  selectedFenceId.value = props.animal.homeGeofenceId ?? null
  editingFence.value = false
  fenceError.value = ''
}

// ── Growth log ───────────────────────────────────────────────────────────────
const EVENT_TYPES = [
  { value: 'HEALTH_CHECK', label: '🩺 Health Check' },
  { value: 'VACCINATION',  label: '💉 Vaccination'  },
  { value: 'WEIGHT',       label: '⚖️ Weight'        },
  { value: 'TREATMENT',    label: '💊 Treatment'     },
  { value: 'BIRTH',        label: '🐣 Birth'         },
  { value: 'NOTE',         label: '📝 Note'          },
]
const EVENT_LABEL: Record<string, string> = Object.fromEntries(EVENT_TYPES.map(e => [e.value, e.label]))

const events = ref<AnimalEvent[]>([])
const eventsLoading = ref(false)
const showAddEvent = ref(false)
const eventForm = ref({ eventType: 'HEALTH_CHECK', description: '', eventDate: new Date().toISOString().slice(0, 10) })
const eventSaving = ref(false)
const eventError = ref('')

async function loadEvents() {
  eventsLoading.value = true
  try { events.value = await fetchAnimalEvents(props.animal.id) }
  finally { eventsLoading.value = false }
}

async function submitEvent() {
  if (!eventForm.value.description.trim()) { eventError.value = 'Description is required.'; return }
  eventSaving.value = true
  eventError.value = ''
  try {
    await createAnimalEvent(props.animal.id, eventForm.value)
    eventForm.value = { eventType: 'HEALTH_CHECK', description: '', eventDate: new Date().toISOString().slice(0, 10) }
    showAddEvent.value = false
    await loadEvents()
  } catch {
    eventError.value = 'Failed to save event.'
  } finally {
    eventSaving.value = false
  }
}

async function removeEvent(eventId: number) {
  try {
    await deleteAnimalEvent(props.animal.id, eventId)
    events.value = events.value.filter(e => e.id !== eventId)
  } catch { /* ignore */ }
}

watch(() => props.animal.id, () => loadHistory(), { immediate: true })
watch(historyRange, () => loadHistory())
watch(() => props.animal.id, () => loadEvents(), { immediate: true })
</script>

<template>
  <aside class="detail-panel">
    <!-- Header -->
    <div class="panel-header">
      <span class="animal-emoji">{{ SPECIES_EMOJI[animal.species] }}</span>
      <div class="panel-title">
        <div class="animal-name">{{ animal.name }}</div>
        <div class="animal-species">{{ animal.species }}</div>
      </div>
      <button class="close-btn" title="Close" @click="$emit('close')">✕</button>
    </div>

    <div class="panel-actions">
      <button class="action-btn edit-btn" @click="$emit('edit')">✏️ Edit</button>
      <button class="action-btn delete-btn" @click="$emit('delete')">🗑 Delete</button>
    </div>

    <div class="panel-body">
      <!-- Device info -->
      <section class="section">
        <div class="info-row">
          <span class="label">Device</span>
          <span class="mono">{{ animal.deviceEui }}</span>
        </div>
        <div class="info-row">
          <span class="label">Collar</span>
          <span class="collar-chip" :style="{ background: animal.collarColor + '22', borderColor: animal.collarColor + '77' }">
            <span class="collar-dot" :style="{ background: animal.collarColor }" />
            {{ animal.collarColor }}
          </span>
        </div>
        <div class="info-row">
          <span class="label">Added</span>
          <span class="mono">{{ new Date(animal.createdAt).toLocaleDateString() }}</span>
        </div>
        <div v-if="animal.notes" class="info-row">
          <span class="label">Notes</span>
          <span class="notes-text">{{ animal.notes }}</span>
        </div>
      </section>

      <!-- Home fence -->
      <section class="section">
        <div class="fence-header">
          <div class="section-title">Home Fence</div>
          <button v-if="!editingFence" class="fence-edit-btn" @click="editingFence = true" title="Change home fence">✏️</button>
        </div>

        <!-- View mode -->
        <template v-if="!editingFence">
          <div v-if="homeFence" class="fence-chip">
            <span class="fence-dot" :style="{ background: homeFence.color }" />
            <span class="fence-chip-name">{{ homeFence.name }}</span>
            <button class="fence-unassign-btn" title="Remove assignment" @click="editingFence = true">✕</button>
          </div>
          <div v-else class="fence-none">
            Not assigned —
            <button class="fence-assign-link" @click="editingFence = true">Assign fence</button>
          </div>
        </template>

        <!-- Edit mode -->
        <template v-else>
          <select v-model="selectedFenceId" class="fence-select">
            <option :value="null">— No fence —</option>
            <option v-for="f in activeFences" :key="f.id" :value="f.id">{{ f.name }}</option>
          </select>
          <div v-if="fenceError" class="fence-err">{{ fenceError }}</div>
          <div class="fence-edit-actions">
            <button class="fence-btn-cancel" @click="cancelFenceEdit">Cancel</button>
            <button class="fence-btn-save" :disabled="fenceSaving" @click="saveHomeFence">
              {{ fenceSaving ? 'Saving…' : 'Save' }}
            </button>
          </div>
        </template>
      </section>

      <!-- Current status -->
      <section class="section">
        <div class="section-title">Current Position</div>

        <div v-if="noSignal" class="no-signal">
          <span>📡</span> Waiting for first GPS fix
        </div>

        <template v-else-if="status">
          <div class="coords-block" :class="{ stale: isStale }">
            {{ status.lat.toFixed(5) }}, {{ status.lng.toFixed(5) }}
            <span class="alt-tag">{{ status.altitude?.toFixed(0) ?? '—' }}m</span>
          </div>

          <div class="metrics-grid">
            <div class="metric-cell">
              <span class="m-label">Last seen</span>
              <span class="m-value" :class="{ 'stale-text': isStale }">{{ timeAgo(status.timestamp) }}</span>
            </div>
            <div class="metric-cell">
              <span class="m-label">HDOP</span>
              <span class="hdop-row">
                <span class="hdop-dot" :style="{ background: hdopColor(status.hdop) }" />
                <span class="m-value">{{ status.hdop?.toFixed(1) ?? '—' }}</span>
              </span>
            </div>
          </div>

          <div class="battery-row">
            <span class="m-label">Battery</span>
            <div class="bat-bar">
              <div class="bat-fill"
                :style="{ width: status.batteryPct + '%', background: batteryColor(status.batteryPct) }" />
            </div>
            <span class="bat-pct">{{ status.batteryPct }}%</span>
          </div>

          <div v-if="status.geofenceAlert" class="alert-banner">
            ⚠️ Exited "{{ status.geofenceAlert }}"
          </div>
        </template>
      </section>

      <!-- History trail -->
      <section class="section">
        <div class="trail-header">
          <div class="section-title">Trail</div>
          <div class="range-tabs">
            <button :class="{ active: historyRange === '24h' }" @click="historyRange = '24h'">24h</button>
            <button :class="{ active: historyRange === '7d' }" @click="historyRange = '7d'">7d</button>
          </div>
          <button class="sim-btn" :disabled="simulating" title="Push a random GPS fix" @click="simulate">
            {{ simulating ? '…' : '⚡ Sim' }}
          </button>
        </div>

        <div v-if="historyLoading" class="sub-text">Loading…</div>
        <div v-else-if="history.length === 0" class="sub-text">No fixes in this range.</div>
        <div v-else class="sub-text">{{ history.length }} fixes — trail drawn on map</div>

        <div v-if="!historyLoading && recentFixes.length > 0" class="fix-list">
          <div v-for="(fix, i) in recentFixes" :key="i" class="fix-row">
            <span class="fix-time">{{ timeAgo(fix.timestamp) }}</span>
            <span class="fix-coords">{{ fix.lat?.toFixed(4) }}, {{ fix.lng?.toFixed(4) }}</span>
            <span v-if="fix.altitude" class="fix-alt">{{ fix.altitude.toFixed(0) }}m</span>
          </div>
        </div>
      </section>
      <!-- Growth log -->
      <section class="section">
        <div class="log-header">
          <div class="section-title">Growth Log</div>
          <button class="log-add-btn" @click="showAddEvent = !showAddEvent" :title="showAddEvent ? 'Cancel' : 'Add event'">
            {{ showAddEvent ? '✕' : '+ Add' }}
          </button>
        </div>

        <!-- Add event form -->
        <div v-if="showAddEvent" class="log-form">
          <select v-model="eventForm.eventType" class="log-select">
            <option v-for="t in EVENT_TYPES" :key="t.value" :value="t.value">{{ t.label }}</option>
          </select>
          <input v-model="eventForm.eventDate" type="date" class="log-date" />
          <textarea v-model="eventForm.description" class="log-desc" rows="2" placeholder="Description…" />
          <div v-if="eventError" class="log-error">{{ eventError }}</div>
          <div class="log-form-actions">
            <button class="log-btn-cancel" @click="showAddEvent = false; eventError = ''">Cancel</button>
            <button class="log-btn-save" :disabled="eventSaving" @click="submitEvent">
              {{ eventSaving ? 'Saving…' : 'Save' }}
            </button>
          </div>
        </div>

        <div v-if="eventsLoading" class="sub-text">Loading…</div>
        <div v-else-if="events.length === 0 && !showAddEvent" class="sub-text">No events recorded yet.</div>

        <div v-if="events.length > 0" class="event-list">
          <div v-for="ev in events" :key="ev.id" class="event-row">
            <div class="event-meta">
              <span class="event-type-badge">{{ EVENT_LABEL[ev.eventType] ?? ev.eventType }}</span>
              <span class="event-date">{{ ev.eventDate }}</span>
              <button class="event-del-btn" title="Delete" @click="removeEvent(ev.id)">✕</button>
            </div>
            <div class="event-desc">{{ ev.description }}</div>
          </div>
        </div>
      </section>
    </div>
  </aside>
</template>

<style scoped>
.detail-panel {
  width: 290px;
  flex-shrink: 0;
  background: var(--color-bg-sidebar);
  border-left: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ── Header ── */
.panel-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
  background: #13151a;
}
.animal-emoji { font-size: 26px; }
.panel-title { flex: 1; }
.animal-name { font-size: 14px; font-weight: 700; color: var(--color-text-primary); }
.animal-species { font-size: 11px; color: var(--color-text-secondary); text-transform: capitalize; }
.close-btn {
  background: none; border: none;
  color: var(--color-text-secondary); font-size: 14px;
  cursor: pointer; padding: 4px 7px; border-radius: 4px;
  transition: all 0.15s;
}
.close-btn:hover { background: #ef535022; color: var(--color-danger); }

/* ── Action bar ── */
.panel-actions {
  display: flex; gap: 6px;
  padding: 8px 14px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}
.action-btn {
  flex: 1; padding: 6px 0;
  border: none; border-radius: 5px;
  font-size: 12px; font-weight: 600; cursor: pointer;
  transition: opacity 0.15s;
}
.action-btn:hover { opacity: 0.8; }
.edit-btn { background: #42A5F522; color: var(--color-info); border: 1px solid #42A5F544; }
.delete-btn { background: #ef535018; color: var(--color-danger); border: 1px solid #ef535044; }

/* ── Body ── */
.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 0 14px 16px;
}
.panel-body::-webkit-scrollbar { width: 4px; }
.panel-body::-webkit-scrollbar-thumb { background: var(--color-border); border-radius: 2px; }

/* ── Sections ── */
.section {
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border);
}
.section:last-child { border-bottom: none; }
.section-title {
  font-size: 10px; font-weight: 700;
  color: var(--color-text-secondary);
  text-transform: uppercase; letter-spacing: 0.06em;
  margin-bottom: 8px;
}

/* ── Home fence ── */
.fence-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.fence-header .section-title { margin-bottom: 0; }
.fence-edit-btn { background: none; border: none; font-size: 12px; cursor: pointer; color: var(--color-text-secondary); padding: 1px 4px; border-radius: 3px; }
.fence-edit-btn:hover { color: var(--color-info); background: #42A5F511; }

.fence-chip {
  display: inline-flex; align-items: center; gap: 7px;
  padding: 5px 8px 5px 10px;
  background: var(--color-bg-card); border: 1px solid var(--color-border);
  border-radius: 20px; max-width: 100%;
}
.fence-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.fence-chip-name { font-size: 12px; font-weight: 600; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fence-unassign-btn { background: none; border: none; color: var(--color-text-secondary); font-size: 11px; cursor: pointer; padding: 0 2px; line-height: 1; }
.fence-unassign-btn:hover { color: var(--color-danger); }

.fence-none { font-size: 12px; color: var(--color-text-secondary); font-style: italic; }
.fence-assign-link { background: none; border: none; color: var(--color-accent); font-size: 12px; cursor: pointer; text-decoration: underline; padding: 0; }

.fence-select {
  width: 100%; background: var(--color-bg-sidebar);
  border: 1px solid var(--color-border); border-radius: 5px;
  padding: 6px 8px; color: var(--color-text-primary);
  font-size: 12px; outline: none; margin-bottom: 6px;
}
.fence-select:focus { border-color: var(--color-accent); }
.fence-err { font-size: 11px; color: var(--color-danger); margin-bottom: 4px; }
.fence-edit-actions { display: flex; gap: 6px; }
.fence-btn-cancel { flex: 1; padding: 5px; background: transparent; border: 1px solid var(--color-border); border-radius: 4px; color: var(--color-text-secondary); font-size: 11px; cursor: pointer; }
.fence-btn-save { flex: 1; padding: 5px; background: var(--color-accent); border: none; border-radius: 4px; color: #fff; font-size: 11px; font-weight: 600; cursor: pointer; }
.fence-btn-save:disabled { opacity: 0.5; cursor: not-allowed; }

/* ── Device info ── */
.info-row {
  display: flex; align-items: center;
  gap: 8px; margin-bottom: 5px; font-size: 12px;
}
.label { color: var(--color-text-secondary); width: 48px; flex-shrink: 0; font-size: 11px; }
.mono { font-family: monospace; font-size: 10px; color: var(--color-text-primary); word-break: break-all; }
.notes-text { color: var(--color-text-secondary); font-style: italic; font-size: 11px; }

.collar-chip {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 2px 8px; border-radius: 10px; border: 1px solid;
  font-family: monospace; font-size: 10px; color: var(--color-text-primary);
}
.collar-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }

/* ── Status ── */
.no-signal {
  font-size: 12px; color: var(--color-text-secondary);
  font-style: italic; display: flex; align-items: center; gap: 6px;
}
.coords-block {
  font-family: monospace; font-size: 13px; font-weight: 600;
  color: var(--color-text-primary); margin-bottom: 8px;
  display: flex; align-items: baseline; gap: 8px;
}
.coords-block.stale { color: var(--color-warning); }
.alt-tag { font-size: 11px; color: var(--color-info); font-weight: 400; }

.metrics-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-bottom: 8px; }
.metric-cell { display: flex; flex-direction: column; gap: 3px; }
.m-label { font-size: 10px; color: var(--color-text-secondary); }
.m-value { font-size: 12px; color: var(--color-text-primary); }
.stale-text { color: var(--color-warning) !important; }

.hdop-row { display: flex; align-items: center; gap: 5px; }
.hdop-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }

.battery-row {
  display: flex; align-items: center; gap: 8px; font-size: 11px; margin-bottom: 4px;
}
.bat-bar { flex: 1; height: 5px; background: var(--color-border); border-radius: 3px; overflow: hidden; }
.bat-fill { height: 100%; border-radius: 3px; transition: width 0.3s; }
.bat-pct { width: 28px; text-align: right; color: var(--color-text-primary); font-size: 11px; }

.alert-banner {
  margin-top: 6px; padding: 5px 8px;
  background: #ef535018; border: 1px solid #ef535044;
  border-radius: 5px; font-size: 11px; color: var(--color-danger);
}

/* ── Trail ── */
.trail-header { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; }
.trail-header .section-title { margin-bottom: 0; flex: 1; }

.range-tabs {
  display: flex; border: 1px solid var(--color-border);
  border-radius: 5px; overflow: hidden;
}
.range-tabs button {
  background: none; border: none; padding: 3px 9px;
  font-size: 11px; color: var(--color-text-secondary); cursor: pointer;
}
.range-tabs button.active { background: var(--color-accent); color: #fff; }
.range-tabs button:not(.active):hover { background: var(--color-bg-card-hover); }

.sim-btn {
  background: #1a237e22; color: var(--color-info);
  border: 1px solid #42A5F533; border-radius: 4px;
  padding: 3px 7px; font-size: 11px; cursor: pointer;
  white-space: nowrap;
}
.sim-btn:hover:not(:disabled) { background: #42A5F522; }
.sim-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.sub-text { font-size: 11px; color: var(--color-text-secondary); font-style: italic; margin-bottom: 6px; }

.fix-list {
  display: flex; flex-direction: column; gap: 1px;
  max-height: 220px; overflow-y: auto; margin-top: 4px;
}
.fix-list::-webkit-scrollbar { width: 3px; }
.fix-list::-webkit-scrollbar-thumb { background: var(--color-border); }

.fix-row {
  display: flex; align-items: center; gap: 6px;
  font-size: 11px; padding: 4px 6px; border-radius: 4px;
}
.fix-row:hover { background: var(--color-bg-card-hover); }
.fix-time { color: var(--color-text-secondary); width: 52px; flex-shrink: 0; font-family: monospace; font-size: 10px; }
.fix-coords { flex: 1; font-family: monospace; color: var(--color-text-primary); font-size: 10px; }
.fix-alt { color: var(--color-info); font-size: 10px; }

/* ── Growth log ── */
.log-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.log-header .section-title { margin-bottom: 0; }
.log-add-btn {
  background: none; border: 1px solid var(--color-border); border-radius: 4px;
  color: var(--color-accent); font-size: 11px; font-weight: 600;
  padding: 2px 8px; cursor: pointer; transition: all 0.15s;
}
.log-add-btn:hover { background: var(--color-accent); color: #fff; }

.log-form { display: flex; flex-direction: column; gap: 6px; margin-bottom: 10px; }
.log-select, .log-date, .log-desc {
  background: var(--color-bg-sidebar);
  border: 1px solid var(--color-border); border-radius: 5px;
  padding: 6px 8px; color: var(--color-text-primary);
  font-size: 12px; outline: none; width: 100%; box-sizing: border-box;
}
.log-select:focus, .log-date:focus, .log-desc:focus { border-color: var(--color-accent); }
.log-desc { resize: vertical; min-height: 52px; font-family: inherit; }
.log-error { font-size: 11px; color: var(--color-danger); }
.log-form-actions { display: flex; gap: 6px; }
.log-btn-cancel { flex: 1; padding: 5px; background: transparent; border: 1px solid var(--color-border); border-radius: 4px; color: var(--color-text-secondary); font-size: 11px; cursor: pointer; }
.log-btn-save { flex: 1; padding: 5px; background: var(--color-accent); border: none; border-radius: 4px; color: #fff; font-size: 11px; font-weight: 600; cursor: pointer; }
.log-btn-save:disabled { opacity: 0.5; cursor: not-allowed; }

.event-list { display: flex; flex-direction: column; gap: 6px; }
.event-row {
  background: var(--color-bg-card); border: 1px solid var(--color-border);
  border-radius: 6px; padding: 7px 9px;
}
.event-meta { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.event-type-badge { font-size: 11px; font-weight: 600; color: var(--color-text-primary); flex: 1; }
.event-date { font-size: 10px; color: var(--color-text-secondary); font-family: monospace; flex-shrink: 0; }
.event-del-btn { background: none; border: none; color: var(--color-text-secondary); font-size: 10px; cursor: pointer; padding: 0 2px; line-height: 1; flex-shrink: 0; }
.event-del-btn:hover { color: var(--color-danger); }
.event-desc { font-size: 11px; color: var(--color-text-secondary); line-height: 1.45; }
</style>
