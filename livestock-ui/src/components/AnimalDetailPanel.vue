<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { Animal, AnimalStatus, LocationHistory } from '../types'
import { fetchHistory, simulatorPushRandom } from '../api/livestock'
import { timeAgo } from '../utils/time'

const props = defineProps<{
  animal: Animal
  status: AnimalStatus | undefined
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'history-loaded', animalId: number, coords: [number, number][]): void
  (e: 'edit'): void
  (e: 'delete'): void
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

watch(() => props.animal.id, () => loadHistory(), { immediate: true })
watch(historyRange, () => loadHistory())
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
        <div v-if="animal.notes" class="info-row">
          <span class="label">Notes</span>
          <span class="notes-text">{{ animal.notes }}</span>
        </div>
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
</style>
