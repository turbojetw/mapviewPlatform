<script setup lang="ts">
import { computed } from 'vue'
import type { Animal, AnimalStatus } from '../types'
import { timeAgo } from '../utils/time'

const props = defineProps<{
  animal: Animal
  status: AnimalStatus | undefined
  selected: boolean
}>()

defineEmits<{ (e: 'click'): void }>()

const STALE_MS = 60 * 60 * 1000  // 1 hour

const noSignal = computed(() => !props.status)
const stale = computed(() =>
  !!props.status && Date.now() - new Date(props.status.timestamp).getTime() > STALE_MS
)

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

const SPECIES_EMOJI: Record<string, string> = {
  GOOSE: '🦢', CHICKEN: '🐔', SHEEP: '🐑', PIG: '🐷'
}
</script>

<template>
  <div
    class="card"
    :class="{ selected, 'no-signal-card': noSignal, 'stale-card': stale && !noSignal }"
    :style="{ borderLeftColor: noSignal ? '#555' : animal.collarColor }"
    @click="$emit('click')"
  >
    <div class="card-header">
      <span class="emoji" :class="{ dimmed: noSignal }">{{ SPECIES_EMOJI[animal.species] }}</span>
      <div class="info">
        <div class="name">{{ animal.name }}</div>
        <div class="device-eui">{{ animal.deviceEui }}</div>
      </div>
      <div v-if="noSignal" class="signal-badge">📵 No signal</div>
      <div v-else-if="stale" class="stale-badge">⚠️ Lost</div>
      <div v-else-if="status?.geofenceAlert" class="alert-badge">⚠️ {{ status.geofenceAlert }}</div>
    </div>

    <template v-if="status">
      <div class="coords" :class="{ dimmed: stale }">
        {{ status.lat.toFixed(5) }}, {{ status.lng.toFixed(5) }}
        <span class="alt">{{ status.altitude?.toFixed(0) }}m</span>
      </div>

      <div class="metrics">
        <div class="metric">
          <span class="metric-label">Battery</span>
          <div class="battery-bar">
            <div
              class="battery-fill"
              :style="{ width: status.batteryPct + '%', background: batteryColor(status.batteryPct) }"
            />
          </div>
          <span class="metric-value">{{ status.batteryPct }}%</span>
        </div>
        <div class="metric">
          <span class="metric-label">HDOP</span>
          <span class="hdop-dot" :style="{ background: hdopColor(status.hdop) }" />
          <span class="metric-value">{{ status.hdop?.toFixed(1) }}</span>
        </div>
        <div class="metric">
          <span class="metric-label">Last seen</span>
          <span class="metric-value" :class="{ 'stale-time': stale }">{{ timeAgo(status.timestamp) }}</span>
        </div>
      </div>
    </template>

    <div v-else class="no-signal-row">
      <span class="no-signal-icon">📡</span>
      <span>Waiting for first GPS fix</span>
    </div>
  </div>
</template>

<style scoped>
.card {
  background: var(--color-bg-card);
  border-left: 3px solid transparent;
  border-radius: 6px;
  padding: 10px 12px;
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 6px;
}
.card:hover { background: var(--color-bg-card-hover); }
.card.selected { background: var(--color-bg-card-hover); outline: 1px solid var(--color-accent); }

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.emoji { font-size: 22px; flex-shrink: 0; }
.info { flex: 1; min-width: 0; }
.name { font-size: 13px; font-weight: 600; color: var(--color-text-primary); }
.device-eui { font-size: 10px; color: var(--color-text-secondary); font-family: monospace; }

.no-signal-card { opacity: 0.7; }
.stale-card { opacity: 0.85; }

.signal-badge {
  font-size: 10px;
  background: #33333388;
  color: var(--color-text-secondary);
  padding: 2px 6px;
  border-radius: 10px;
  white-space: nowrap;
}
.stale-badge {
  font-size: 10px;
  background: #FFA72622;
  color: var(--color-warning);
  border: 1px solid #FFA72644;
  padding: 2px 6px;
  border-radius: 10px;
  white-space: nowrap;
}
.alert-badge {
  font-size: 10px;
  background: var(--color-danger);
  color: #fff;
  padding: 2px 6px;
  border-radius: 10px;
  white-space: nowrap;
}

.coords {
  font-size: 11px;
  color: var(--color-text-secondary);
  font-family: monospace;
  margin-bottom: 6px;
}
.alt { margin-left: 8px; color: var(--color-info); }

.metrics { display: flex; flex-direction: column; gap: 4px; }
.metric {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
}
.metric-label { color: var(--color-text-secondary); width: 56px; flex-shrink: 0; }
.metric-value { color: var(--color-text-primary); margin-left: auto; }

.battery-bar {
  flex: 1;
  height: 4px;
  background: var(--color-border);
  border-radius: 2px;
  overflow: hidden;
}
.battery-fill { height: 100%; border-radius: 2px; transition: width 0.3s; }

.hdop-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dimmed { opacity: 0.5; }
.stale-time { color: var(--color-warning) !important; }

.no-signal-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--color-text-secondary);
  font-style: italic;
  margin-top: 2px;
}
.no-signal-icon { font-size: 13px; }
</style>
