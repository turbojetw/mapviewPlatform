<script setup lang="ts">
import { computed } from 'vue'
import type { Animal, AnimalStatus } from '../types'
import { timeAgo } from '../utils/time'

const props = defineProps<{
  animals: Animal[]
  animalStatuses: Map<number, AnimalStatus>
  sessionAlertCount: number
}>()

defineEmits<{
  (e: 'close'): void
  (e: 'select-animal', id: number): void
}>()

const STALE_MS = 60 * 60 * 1000
const LOW_BAT = 20

const SPECIES_EMOJI: Record<string, string> = {
  GOOSE: '🦢', CHICKEN: '🐔', SHEEP: '🐑', PIG: '🐷',
}

// ── Derived rows ────────────────────────────────────────────────────────────
interface Row {
  animal: Animal
  status: AnimalStatus | undefined
  online: boolean
  stale: boolean
  batteryPct: number | null
  hdop: number | null
}

const rows = computed<Row[]>(() =>
  props.animals.map(a => {
    const s = props.animalStatuses.get(a.id)
    const age = s ? Date.now() - new Date(s.timestamp).getTime() : Infinity
    return {
      animal: a,
      status: s,
      online: age <= STALE_MS,
      stale: age > STALE_MS,
      batteryPct: s?.batteryPct ?? null,
      hdop: s?.hdop ?? null,
    }
  })
)

// ── Summary stats ────────────────────────────────────────────────────────────
const totalCount    = computed(() => props.animals.length)
const onlineCount   = computed(() => rows.value.filter(r => r.online).length)
const lowBatCount   = computed(() => rows.value.filter(r => r.batteryPct !== null && r.batteryPct < LOW_BAT).length)
const poorGpsCount  = computed(() => rows.value.filter(r => r.hdop !== null && r.hdop > 5).length)

// ── Sorted views ─────────────────────────────────────────────────────────────
const byBattery = computed(() =>
  [...rows.value].sort((a, b) => (a.batteryPct ?? 101) - (b.batteryPct ?? 101))
)
const byHdop = computed(() =>
  [...rows.value].filter(r => r.hdop !== null).sort((a, b) => (b.hdop ?? 0) - (a.hdop ?? 0))
)
const tableRows = computed(() =>
  [...rows.value].sort((a, b) => {
    if (a.online !== b.online) return a.online ? -1 : 1
    return a.animal.name.localeCompare(b.animal.name)
  })
)

// ── Color helpers ─────────────────────────────────────────────────────────────
function batColor(pct: number): string {
  if (pct > 50) return '#4CAF50'
  if (pct > 20) return '#FFA726'
  return '#ef5350'
}
function hdopColor(h: number): string {
  if (h < 1.5) return '#4CAF50'
  if (h < 3)   return '#FFA726'
  if (h < 5)   return '#FF7043'
  return '#ef5350'
}
function hdopLabel(h: number): string {
  if (h < 1.5) return 'Excellent'
  if (h < 3)   return 'Good'
  if (h < 5)   return 'Fair'
  return 'Poor'
}
function hdopBarWidth(h: number): number {
  return Math.min(100, (h / 10) * 100)
}
</script>

<template>
  <div class="backdrop" @click.self="$emit('close')">
    <div class="panel">

      <!-- Header -->
      <div class="panel-header">
        <div class="header-left">
          <span class="header-icon">📊</span>
          <h2>Dashboard</h2>
        </div>
        <button class="close-btn" @click="$emit('close')">✕</button>
      </div>

      <div class="panel-body">

        <!-- ── Summary cards ── -->
        <div class="summary-grid">
          <div class="summary-card">
            <div class="summary-value">{{ totalCount }}</div>
            <div class="summary-label">Total Animals</div>
          </div>
          <div class="summary-card" :class="{ 'card-good': onlineCount === totalCount, 'card-warn': onlineCount < totalCount }">
            <div class="summary-value" :style="{ color: onlineCount === totalCount ? 'var(--color-accent)' : 'var(--color-warning)' }">
              {{ onlineCount }}
            </div>
            <div class="summary-label">Online (last 1h)</div>
          </div>
          <div class="summary-card" :class="{ 'card-danger': lowBatCount > 0 }">
            <div class="summary-value" :style="{ color: lowBatCount > 0 ? 'var(--color-danger)' : 'var(--color-accent)' }">
              {{ lowBatCount }}
            </div>
            <div class="summary-label">Low Battery (&lt;20%)</div>
          </div>
          <div class="summary-card" :class="{ 'card-danger': sessionAlertCount > 0 }">
            <div class="summary-value" :style="{ color: sessionAlertCount > 0 ? 'var(--color-warning)' : 'var(--color-text-secondary)' }">
              {{ sessionAlertCount }}
            </div>
            <div class="summary-label">Session Alerts</div>
          </div>
        </div>

        <!-- ── Battery section ── -->
        <section class="section">
          <div class="section-title">Battery Levels</div>
          <div class="bar-grid">
            <div
              v-for="row in byBattery"
              :key="row.animal.id"
              class="bar-card"
              @click="$emit('select-animal', row.animal.id); $emit('close')"
            >
              <div class="bar-header">
                <span class="bar-emoji">{{ SPECIES_EMOJI[row.animal.species] }}</span>
                <span class="bar-name">{{ row.animal.name }}</span>
                <span class="bar-value" :style="{ color: row.batteryPct !== null ? batColor(row.batteryPct) : 'var(--color-text-secondary)' }">
                  {{ row.batteryPct !== null ? row.batteryPct + '%' : '—' }}
                </span>
              </div>
              <div class="bar-track">
                <div
                  v-if="row.batteryPct !== null"
                  class="bar-fill"
                  :style="{ width: row.batteryPct + '%', background: batColor(row.batteryPct) }"
                />
                <div v-else class="bar-fill no-data" style="width:100%" />
              </div>
              <div class="bar-sub">
                <span v-if="row.status?.batteryMv">{{ row.status.batteryMv }} mV</span>
                <span v-if="!row.online" class="offline-tag">offline</span>
              </div>
            </div>
          </div>
        </section>

        <!-- ── GPS Quality section ── -->
        <section class="section">
          <div class="section-title">GPS Signal Quality <span class="section-hint">(HDOP — lower is better)</span></div>
          <div v-if="byHdop.length === 0" class="no-data-msg">No GPS fixes received yet.</div>
          <div v-else class="bar-grid">
            <div
              v-for="row in byHdop"
              :key="row.animal.id"
              class="bar-card"
              @click="$emit('select-animal', row.animal.id); $emit('close')"
            >
              <div class="bar-header">
                <span class="bar-emoji">{{ SPECIES_EMOJI[row.animal.species] }}</span>
                <span class="bar-name">{{ row.animal.name }}</span>
                <span class="bar-value" :style="{ color: hdopColor(row.hdop!) }">
                  {{ row.hdop!.toFixed(1) }}
                </span>
              </div>
              <div class="bar-track">
                <div
                  class="bar-fill"
                  :style="{ width: hdopBarWidth(row.hdop!) + '%', background: hdopColor(row.hdop!) }"
                />
              </div>
              <div class="bar-sub">
                <span :style="{ color: hdopColor(row.hdop!) }">{{ hdopLabel(row.hdop!) }}</span>
              </div>
            </div>
          </div>
        </section>

        <!-- ── Status table ── -->
        <section class="section">
          <div class="section-title">All Animals</div>
          <table class="status-table">
            <thead>
              <tr>
                <th>Animal</th>
                <th>Status</th>
                <th>Battery</th>
                <th>HDOP</th>
                <th>Altitude</th>
                <th>Last Seen</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in tableRows"
                :key="row.animal.id"
                class="table-row"
                @click="$emit('select-animal', row.animal.id); $emit('close')"
              >
                <td class="td-name">
                  <span class="tbl-emoji">{{ SPECIES_EMOJI[row.animal.species] }}</span>
                  <span class="tbl-dot" :style="{ background: row.animal.collarColor }" />
                  {{ row.animal.name }}
                </td>
                <td>
                  <span v-if="!row.status" class="tag tag-grey">No signal</span>
                  <span v-else-if="!row.online" class="tag tag-warn">Offline</span>
                  <span v-else-if="row.status.geofenceAlert" class="tag tag-danger">⚠️ Alert</span>
                  <span v-else class="tag tag-good">Online</span>
                </td>
                <td>
                  <span v-if="row.batteryPct !== null" :style="{ color: batColor(row.batteryPct) }">
                    {{ row.batteryPct }}%
                  </span>
                  <span v-else class="dim">—</span>
                </td>
                <td>
                  <span v-if="row.hdop !== null" :style="{ color: hdopColor(row.hdop) }">
                    {{ row.hdop.toFixed(1) }}
                  </span>
                  <span v-else class="dim">—</span>
                </td>
                <td class="dim">
                  {{ row.status?.altitude != null ? row.status.altitude.toFixed(0) + ' m' : '—' }}
                </td>
                <td class="dim">
                  <span v-if="row.status" :class="{ 'stale-time': !row.online }">
                    {{ timeAgo(row.status.timestamp) }}
                  </span>
                  <span v-else>—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </section>

      </div>
    </div>
  </div>
</template>

<style scoped>
.backdrop {
  position: fixed; inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(3px);
  display: flex; align-items: center; justify-content: center;
  z-index: 1100;
}

.panel {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  width: 720px; max-width: 96vw;
  max-height: 88vh;
  display: flex; flex-direction: column;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.6);
}

.panel-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}
.header-left { display: flex; align-items: center; gap: 8px; }
.header-icon { font-size: 18px; }
h2 { font-size: 16px; font-weight: 700; }
.close-btn {
  background: none; border: none;
  color: var(--color-text-secondary); font-size: 14px;
  cursor: pointer; padding: 3px 8px; border-radius: 4px;
}
.close-btn:hover { color: var(--color-text-primary); background: var(--color-bg-card-hover); }

.panel-body {
  flex: 1; overflow-y: auto; padding: 20px;
  display: flex; flex-direction: column; gap: 24px;
}
.panel-body::-webkit-scrollbar { width: 4px; }
.panel-body::-webkit-scrollbar-thumb { background: var(--color-border); border-radius: 2px; }

/* ── Summary cards ── */
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.summary-card {
  background: var(--color-bg-sidebar);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 16px;
  text-align: center;
  transition: border-color 0.15s;
}
.summary-card.card-good { border-color: #4CAF5044; }
.summary-card.card-warn { border-color: #FFA72644; }
.summary-card.card-danger { border-color: #ef535044; }
.summary-value {
  font-size: 32px; font-weight: 700;
  color: var(--color-text-primary);
  line-height: 1.1;
  margin-bottom: 6px;
}
.summary-label {
  font-size: 11px; color: var(--color-text-secondary);
  text-transform: uppercase; letter-spacing: 0.05em;
}

/* ── Section ── */
.section { display: flex; flex-direction: column; gap: 10px; }
.section-title {
  font-size: 12px; font-weight: 700;
  text-transform: uppercase; letter-spacing: 0.07em;
  color: var(--color-text-secondary);
  border-bottom: 1px solid var(--color-border);
  padding-bottom: 6px;
}
.section-hint { font-weight: 400; font-size: 11px; text-transform: none; letter-spacing: 0; }
.no-data-msg { font-size: 12px; color: var(--color-text-secondary); font-style: italic; padding: 8px 0; }

/* ── Bar grid ── */
.bar-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 8px;
}
.bar-card {
  background: var(--color-bg-sidebar);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 10px 12px;
  cursor: pointer;
  transition: background 0.12s, border-color 0.12s;
}
.bar-card:hover { background: var(--color-bg-card-hover); border-color: var(--color-accent); }

.bar-header {
  display: flex; align-items: center; gap: 6px;
  margin-bottom: 6px;
}
.bar-emoji { font-size: 14px; flex-shrink: 0; }
.bar-name { flex: 1; font-size: 12px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bar-value { font-size: 12px; font-weight: 700; flex-shrink: 0; }

.bar-track {
  height: 5px;
  background: var(--color-border);
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 4px;
}
.bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.4s ease;
}
.bar-fill.no-data { background: var(--color-border); opacity: 0.4; }

.bar-sub {
  display: flex; justify-content: space-between;
  font-size: 10px; color: var(--color-text-secondary);
}
.offline-tag {
  color: var(--color-warning);
  font-weight: 600;
}

/* ── Status table ── */
.status-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.status-table th {
  text-align: left;
  padding: 7px 10px;
  background: var(--color-bg-sidebar);
  color: var(--color-text-secondary);
  font-weight: 600; font-size: 11px;
  border-bottom: 1px solid var(--color-border);
}
.status-table th:first-child { border-radius: 6px 0 0 0; }
.status-table th:last-child  { border-radius: 0 6px 0 0; }

.table-row {
  cursor: pointer;
  transition: background 0.1s;
}
.table-row:hover td { background: var(--color-bg-card-hover); }
.table-row td {
  padding: 8px 10px;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text-primary);
}
.table-row:last-child td { border-bottom: none; }

.td-name { display: flex; align-items: center; gap: 6px; }
.tbl-emoji { font-size: 14px; }
.tbl-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }

.tag {
  font-size: 10px; font-weight: 700; padding: 2px 7px;
  border-radius: 10px; border: 1px solid transparent;
}
.tag-good   { background: #4CAF5022; color: var(--color-accent);  border-color: #4CAF5044; }
.tag-warn   { background: #FFA72622; color: var(--color-warning); border-color: #FFA72644; }
.tag-danger { background: #ef535022; color: var(--color-danger);  border-color: #ef535044; }
.tag-grey   { background: #33384222; color: var(--color-text-secondary); border-color: #ffffff22; }

.dim { color: var(--color-text-secondary); }
.stale-time { color: var(--color-warning) !important; }
</style>
