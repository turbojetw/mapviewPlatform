<script setup lang="ts">
import { computed } from 'vue'
import type { Animal, AnimalStatus } from '../types'
import AnimalCard from './AnimalCard.vue'

const props = defineProps<{
  animals: Animal[]
  animalStatuses: Map<number, AnimalStatus>
  selectedAnimalId: number | null
}>()

const emit = defineEmits<{
  (e: 'animal-selected', id: number): void
  (e: 'open-add-modal'): void
  (e: 'open-geofence-modal'): void
  (e: 'simulate-all'): void
}>()

const STALE_MS = 60 * 60 * 1000

function hasSignal(animal: Animal): boolean {
  const s = props.animalStatuses.get(animal.id)
  if (!s) return false
  return Date.now() - new Date(s.timestamp).getTime() <= STALE_MS
}

const sortedAnimals = computed(() =>
  [...props.animals].sort((a, b) => {
    const aOk = hasSignal(a) ? 0 : 1
    const bOk = hasSignal(b) ? 0 : 1
    return aOk - bOk
  })
)

const noSignalCount = computed(() =>
  props.animals.filter(a => !hasSignal(a)).length
)

function selectAnimal(id: number) {
  emit('animal-selected', id)
}
</script>

<template>
  <aside class="sidebar">
    <div class="actions">
      <button class="btn-primary" @click="$emit('open-add-modal')">+ Animal</button>
      <button class="btn-secondary" @click="$emit('open-geofence-modal')">Fences</button>
      <button class="btn-simulate" @click="$emit('simulate-all')" title="Simulate random GPS fix for all animals">⚡ Sim</button>
    </div>

    <div class="stats">
      <span class="stat">{{ animals.length }} animals</span>
      <span class="stat">{{ animalStatuses.size }} tracked</span>
      <span v-if="noSignalCount > 0" class="stat stat-warn">{{ noSignalCount }} offline</span>
    </div>

    <div class="animal-list">
      <AnimalCard
        v-for="animal in sortedAnimals"
        :key="animal.id"
        :animal="animal"
        :status="animalStatuses.get(animal.id)"
        :selected="selectedAnimalId === animal.id"
        @click="selectAnimal(animal.id)"
      />
      <div v-if="animals.length === 0" class="empty">
        No animals registered yet.<br/>Click "+ Animal" to add one.
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  flex-shrink: 0;
  background: var(--color-bg-sidebar);
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.actions {
  display: flex;
  gap: 6px;
  padding: 10px;
  border-bottom: 1px solid var(--color-border);
}

.btn-primary {
  flex: 1;
  padding: 7px 10px;
  background: var(--color-accent);
  color: #fff;
  border: none;
  border-radius: 5px;
  font-size: 12px;
  font-weight: 600;
  transition: background 0.15s;
}
.btn-primary:hover { background: var(--color-accent-hover); }

.btn-secondary {
  padding: 7px 10px;
  background: transparent;
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
  border-radius: 5px;
  font-size: 12px;
  transition: all 0.15s;
}
.btn-secondary:hover { border-color: var(--color-accent); color: var(--color-accent); }

.btn-simulate {
  padding: 7px 10px;
  background: #1a237e22;
  color: var(--color-info);
  border: 1px solid #42A5F533;
  border-radius: 5px;
  font-size: 12px;
  transition: all 0.15s;
}
.btn-simulate:hover { background: #42A5F522; }

.stats {
  display: flex;
  gap: 12px;
  padding: 6px 12px;
  border-bottom: 1px solid var(--color-border);
}
.stat {
  font-size: 11px;
  color: var(--color-text-secondary);
}
.stat-warn {
  color: var(--color-warning);
  font-weight: 600;
}

.animal-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.animal-list::-webkit-scrollbar { width: 4px; }
.animal-list::-webkit-scrollbar-track { background: transparent; }
.animal-list::-webkit-scrollbar-thumb { background: var(--color-border); border-radius: 2px; }

.empty {
  text-align: center;
  color: var(--color-text-secondary);
  font-size: 13px;
  margin-top: 40px;
  line-height: 1.6;
}
</style>
