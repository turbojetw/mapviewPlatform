<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import type { Animal, GeoFence, FenceStats, AnimalStatus } from '../types'
import { fetchFenceStats, fetchFenceAnimals, assignAnimalsToFence } from '../api/livestock'

const props = defineProps<{
  fence: GeoFence
  animals: Animal[]
  animalStatuses: Map<number, AnimalStatus>
  stats: FenceStats | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'saved'): void
}>()

const SPECIES_EMOJI: Record<string, string> = {
  GOOSE: '🦢', CHICKEN: '🐔', SHEEP: '🐑', PIG: '🐷',
}

// IDs of animals currently checked (assigned) — initialized from API
const selectedIds = ref<Set<number>>(new Set())
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const liveStats = ref<FenceStats | null>(props.stats)
const search = ref('')

onMounted(async () => {
  try {
    const [assigned, freshStats] = await Promise.all([
      fetchFenceAnimals(props.fence.id),
      fetchFenceStats(props.fence.id),
    ])
    selectedIds.value = new Set(assigned.map(a => a.id))
    liveStats.value = freshStats
  } catch {
    error.value = 'Failed to load fence data.'
  } finally {
    loading.value = false
  }
})

// Called from App.vue when a WebSocket stats update arrives for this fence
function applyStatsUpdate(s: FenceStats) {
  if (s.fenceId === props.fence.id) liveStats.value = s
}
defineExpose({ applyStatsUpdate })

function toggle(id: number) {
  const s = new Set(selectedIds.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selectedIds.value = s
}

async function save() {
  saving.value = true
  error.value = ''
  try {
    await assignAnimalsToFence(props.fence.id, Array.from(selectedIds.value))
    emit('saved')
    emit('close')
  } catch {
    error.value = 'Failed to save assignments.'
  } finally {
    saving.value = false
  }
}

function isInside(animalId: number): boolean {
  return liveStats.value?.insideAnimalIds.includes(animalId) ?? false
}

function statusLabel(animal: Animal): { text: string; cls: string } {
  const s = props.animalStatuses.get(animal.id)
  if (!s) return { text: 'No signal', cls: 'tag-grey' }
  if (isInside(animal.id) && selectedIds.value.has(animal.id)) return { text: 'Inside', cls: 'tag-good' }
  const stale = Date.now() - new Date(s.timestamp).getTime() > 3600_000
  if (stale) return { text: 'Offline', cls: 'tag-warn' }
  return { text: 'Away', cls: 'tag-danger' }
}

const filteredAnimals = computed(() => {
  const q = search.value.toLowerCase().trim()
  return props.animals.filter(a =>
    a.active && (
      !q ||
      a.name.toLowerCase().includes(q) ||
      a.deviceEui.toLowerCase().includes(q) ||
      a.species.toLowerCase().includes(q)
    )
  )
})

const assignedFiltered = computed(() => filteredAnimals.value.filter(a => selectedIds.value.has(a.id)))
const unassignedFiltered = computed(() => filteredAnimals.value.filter(a => !selectedIds.value.has(a.id)))
</script>

<template>
  <div class="backdrop" @click.self="$emit('close')">
    <div class="panel">

      <!-- Header -->
      <div class="panel-header">
        <div class="header-left">
          <span class="fence-dot" :style="{ background: fence.color }" />
          <h2>{{ fence.name }}</h2>
          <span class="header-sub">Animal Assignment</span>
        </div>
        <button class="close-btn" @click="$emit('close')">✕</button>
      </div>

      <!-- Stats -->
      <div class="stats-row">
        <div class="stat-card">
          <div class="stat-val">{{ liveStats?.totalAssigned ?? selectedIds.size }}</div>
          <div class="stat-lbl">Total Assigned</div>
        </div>
        <div class="stat-card stat-inside">
          <div class="stat-val" style="color: var(--color-accent)">{{ liveStats?.currentlyInside ?? '—' }}</div>
          <div class="stat-lbl">Currently Inside</div>
        </div>
        <div class="stat-card stat-away">
          <div class="stat-val" style="color: var(--color-warning)">{{ liveStats?.away ?? '—' }}</div>
          <div class="stat-lbl">Away / Undetected</div>
        </div>
      </div>

      <div v-if="loading" class="body-state">Loading…</div>
      <div v-else-if="error" class="body-state error">{{ error }}</div>

      <div v-else class="panel-body">

        <!-- Search -->
        <input
          v-model="search"
          type="text"
          class="search-input"
          placeholder="Search by name, device EUI, or species…"
        />

        <!-- Assigned animals -->
        <div v-if="assignedFiltered.length > 0" class="section">
          <div class="section-title">
            Assigned ({{ selectedIds.size }})
            <span class="section-hint">Click to remove</span>
          </div>
          <div
            v-for="animal in assignedFiltered"
            :key="animal.id"
            class="animal-row assigned"
            @click="toggle(animal.id)"
          >
            <input type="checkbox" :checked="true" class="chk" @click.stop="toggle(animal.id)" />
            <span class="a-emoji">{{ SPECIES_EMOJI[animal.species] }}</span>
            <div class="a-info">
              <div class="a-name">{{ animal.name }}</div>
              <div class="a-eui">{{ animal.deviceEui }}</div>
            </div>
            <span class="tag" :class="statusLabel(animal).cls">{{ statusLabel(animal).text }}</span>
          </div>
        </div>

        <!-- Unassigned animals -->
        <div v-if="unassignedFiltered.length > 0" class="section">
          <div class="section-title">
            Not Assigned ({{ props.animals.filter(a => a.active && !selectedIds.has(a.id)).length }})
            <span class="section-hint">Click to add</span>
          </div>
          <div
            v-for="animal in unassignedFiltered"
            :key="animal.id"
            class="animal-row"
            @click="toggle(animal.id)"
          >
            <input type="checkbox" :checked="false" class="chk" @click.stop="toggle(animal.id)" />
            <span class="a-emoji dimmed">{{ SPECIES_EMOJI[animal.species] }}</span>
            <div class="a-info">
              <div class="a-name dimmed">{{ animal.name }}</div>
              <div class="a-eui">{{ animal.deviceEui }}</div>
            </div>
            <span v-if="animal.homeGeofenceId && animal.homeGeofenceId !== fence.id" class="tag tag-grey">
              Other fence
            </span>
          </div>
        </div>

        <div v-if="filteredAnimals.length === 0" class="body-state">No animals match your search.</div>
      </div>

      <!-- Footer -->
      <div v-if="!loading && !error" class="panel-footer">
        <div class="footer-summary">{{ selectedIds.size }} animal{{ selectedIds.size !== 1 ? 's' : '' }} assigned</div>
        <button class="btn-cancel" @click="$emit('close')">Cancel</button>
        <button class="btn-save" :disabled="saving" @click="save">
          {{ saving ? 'Saving…' : 'Save Assignments' }}
        </button>
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
  z-index: 1300;
}

.panel {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  width: 460px; max-width: 96vw;
  max-height: 86vh;
  display: flex; flex-direction: column;
  box-shadow: 0 20px 60px rgba(0,0,0,0.6);
}

.panel-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}
.header-left { display: flex; align-items: center; gap: 8px; }
.fence-dot { width: 12px; height: 12px; border-radius: 50%; flex-shrink: 0; }
h2 { font-size: 15px; font-weight: 700; }
.header-sub { font-size: 11px; color: var(--color-text-secondary); }
.close-btn { background: none; border: none; color: var(--color-text-secondary); font-size: 14px; cursor: pointer; padding: 2px 7px; border-radius: 4px; }
.close-btn:hover { color: var(--color-text-primary); }

/* Stats */
.stats-row {
  display: grid; grid-template-columns: repeat(3, 1fr);
  gap: 1px; background: var(--color-border);
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}
.stat-card {
  background: var(--color-bg-sidebar);
  padding: 12px; text-align: center;
}
.stat-card.stat-inside { background: #4CAF5008; }
.stat-card.stat-away   { background: #FFA72608; }
.stat-val  { font-size: 26px; font-weight: 700; line-height: 1; margin-bottom: 4px; }
.stat-lbl  { font-size: 10px; color: var(--color-text-secondary); text-transform: uppercase; letter-spacing: 0.05em; }

.body-state {
  padding: 24px; text-align: center;
  font-size: 13px; color: var(--color-text-secondary);
}
.body-state.error { color: var(--color-danger); }

.panel-body {
  flex: 1; overflow-y: auto; padding: 12px 16px;
  display: flex; flex-direction: column; gap: 10px;
}
.panel-body::-webkit-scrollbar { width: 4px; }
.panel-body::-webkit-scrollbar-thumb { background: var(--color-border); border-radius: 2px; }

.search-input {
  width: 100%;
  background: var(--color-bg-sidebar);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 7px 10px;
  color: var(--color-text-primary);
  font-size: 12px;
  outline: none;
}
.search-input:focus { border-color: var(--color-accent); }

.section { display: flex; flex-direction: column; gap: 4px; }
.section-title {
  font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.06em;
  color: var(--color-text-secondary);
  display: flex; align-items: center; gap: 8px;
  margin-bottom: 2px;
}
.section-hint { font-weight: 400; font-size: 10px; text-transform: none; letter-spacing: 0; }

.animal-row {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px;
  background: var(--color-bg-sidebar);
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.1s, border-color 0.1s;
}
.animal-row:hover { background: var(--color-bg-card-hover); border-color: var(--color-border); }
.animal-row.assigned { border-color: #4CAF5033; background: #4CAF5008; }
.animal-row.assigned:hover { border-color: #4CAF5066; }

.chk { width: 14px; height: 14px; flex-shrink: 0; accent-color: var(--color-accent); cursor: pointer; }
.a-emoji { font-size: 16px; flex-shrink: 0; }
.a-info { flex: 1; min-width: 0; }
.a-name { font-size: 13px; font-weight: 600; }
.a-eui  { font-size: 10px; color: var(--color-text-secondary); font-family: monospace; }
.dimmed { opacity: 0.55; }

.tag { font-size: 10px; font-weight: 700; padding: 2px 7px; border-radius: 10px; border: 1px solid transparent; white-space: nowrap; flex-shrink: 0; }
.tag-good   { background: #4CAF5022; color: var(--color-accent);  border-color: #4CAF5044; }
.tag-warn   { background: #FFA72622; color: var(--color-warning); border-color: #FFA72644; }
.tag-danger { background: #ef535022; color: var(--color-danger);  border-color: #ef535044; }
.tag-grey   { background: #33384222; color: var(--color-text-secondary); border-color: #ffffff22; }

.panel-footer {
  display: flex; align-items: center; gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--color-border);
  flex-shrink: 0;
}
.footer-summary { flex: 1; font-size: 12px; color: var(--color-text-secondary); }
.btn-cancel { padding: 7px 14px; background: transparent; border: 1px solid var(--color-border); border-radius: 5px; color: var(--color-text-secondary); font-size: 12px; cursor: pointer; }
.btn-save   { padding: 7px 16px; background: var(--color-accent); border: none; border-radius: 5px; color: #fff; font-size: 12px; font-weight: 600; cursor: pointer; }
.btn-save:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
