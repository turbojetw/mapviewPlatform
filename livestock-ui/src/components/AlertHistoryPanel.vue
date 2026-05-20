<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { GeofenceAlertRecord } from '../types'
import { fetchAlerts } from '../api/livestock'
import { timeAgo } from '../utils/time'

defineProps<{ sessionCount: number }>()
defineEmits<{ (e: 'close'): void }>()

const alerts = ref<GeofenceAlertRecord[]>([])
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try { alerts.value = await fetchAlerts(50) }
  catch { error.value = 'Failed to load alerts. Is the backend running?' }
  finally { loading.value = false }
}

onMounted(load)
</script>

<template>
  <div class="backdrop" @click.self="$emit('close')">
    <div class="panel">
      <div class="panel-header">
        <div class="header-left">
          <span class="header-icon">🔔</span>
          <h2>Alert History</h2>
          <span v-if="sessionCount > 0" class="session-badge">{{ sessionCount }} this session</span>
        </div>
        <div class="header-actions">
          <button class="refresh-btn" :disabled="loading" @click="load" title="Refresh">↻</button>
          <button class="close-btn" @click="$emit('close')">✕</button>
        </div>
      </div>

      <div class="panel-body">
        <div v-if="loading" class="state-msg">Loading…</div>
        <div v-else-if="error" class="state-msg error">{{ error }}</div>
        <div v-else-if="alerts.length === 0" class="state-msg">
          No geofence alerts recorded yet.<br/>
          <span class="hint">Alerts are saved when an animal exits a fence with "Alert on exit" enabled.</span>
        </div>

        <div v-else class="alert-list">
          <div v-for="alert in alerts" :key="alert.id" class="alert-row">
            <div class="alert-icon">⚠️</div>
            <div class="alert-body">
              <div class="alert-main">
                <span class="animal-name">{{ alert.animalName }}</span>
                <span class="verb">exited</span>
                <span class="fence-name">"{{ alert.fenceName }}"</span>
              </div>
              <div class="alert-time">{{ timeAgo(alert.timestamp) }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.backdrop {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.55);
  display: flex; align-items: center; justify-content: center;
  z-index: 1200;
}

.panel {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  width: 420px; max-width: 95vw;
  max-height: 70vh;
  display: flex; flex-direction: column;
  box-shadow: 0 20px 60px rgba(0,0,0,0.5);
}

.panel-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}
.header-left { display: flex; align-items: center; gap: 8px; }
.header-icon { font-size: 16px; }
h2 { font-size: 15px; font-weight: 600; }
.session-badge {
  font-size: 10px; padding: 2px 7px;
  background: #ef535022; color: var(--color-danger);
  border: 1px solid #ef535044; border-radius: 10px;
}
.header-actions { display: flex; align-items: center; gap: 6px; }
.refresh-btn {
  background: none; border: none;
  color: var(--color-text-secondary); font-size: 15px;
  cursor: pointer; padding: 2px 6px; border-radius: 4px;
  transition: all 0.15s;
}
.refresh-btn:hover:not(:disabled) { color: var(--color-text-primary); background: var(--color-bg-card-hover); }
.refresh-btn:disabled { opacity: 0.4; }
.close-btn {
  background: none; border: none;
  color: var(--color-text-secondary); font-size: 14px;
  cursor: pointer; padding: 2px 7px; border-radius: 4px;
}
.close-btn:hover { color: var(--color-text-primary); }

.panel-body {
  flex: 1; overflow-y: auto; padding: 14px 18px;
}
.panel-body::-webkit-scrollbar { width: 4px; }
.panel-body::-webkit-scrollbar-thumb { background: var(--color-border); border-radius: 2px; }

.state-msg {
  font-size: 13px; color: var(--color-text-secondary);
  text-align: center; padding: 24px 0; line-height: 1.7;
}
.state-msg.error { color: var(--color-danger); }
.hint { font-size: 11px; }

.alert-list { display: flex; flex-direction: column; gap: 6px; }

.alert-row {
  display: flex; gap: 10px; align-items: flex-start;
  padding: 10px 12px;
  background: var(--color-bg-sidebar);
  border-radius: 6px;
  border-left: 3px solid var(--color-warning);
}
.alert-icon { font-size: 14px; flex-shrink: 0; margin-top: 1px; }
.alert-body { flex: 1; }
.alert-main { font-size: 13px; display: flex; flex-wrap: wrap; gap: 4px; align-items: baseline; margin-bottom: 3px; }
.animal-name { font-weight: 600; color: var(--color-text-primary); }
.verb { color: var(--color-text-secondary); font-size: 12px; }
.fence-name { color: var(--color-warning); font-size: 12px; }
.alert-time { font-size: 11px; color: var(--color-text-secondary); }
</style>
