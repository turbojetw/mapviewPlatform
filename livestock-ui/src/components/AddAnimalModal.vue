<script setup lang="ts">
import { ref } from 'vue'
import type { Animal, GeoFence } from '../types'
import { createAnimal, updateAnimal, setAnimalHomeFence } from '../api/livestock'

const props = defineProps<{
  animal?: Animal
  geofences: GeoFence[]
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'saved'): void
}>()

const isEdit = !!props.animal
const originalHomeGeofenceId = props.animal?.homeGeofenceId ?? null

const form = ref({
  name:           props.animal?.name        ?? '',
  species:        props.animal?.species     ?? 'SHEEP' as string,
  deviceEui:      props.animal?.deviceEui   ?? '',
  collarColor:    props.animal?.collarColor ?? '#4CAF50',
  notes:          props.animal?.notes       ?? '',
  homeGeofenceId: props.animal?.homeGeofenceId ?? null as number | null,
})
const loading = ref(false)
const error = ref('')

const activeFences = props.geofences.filter(f => f.active)

const SPECIES_OPTIONS = [
  { value: 'SHEEP',   label: '🐑 Sheep'   },
  { value: 'GOOSE',   label: '🦢 Goose'   },
  { value: 'CHICKEN', label: '🐔 Chicken' },
  { value: 'PIG',     label: '🐷 Pig'     },
]

async function submit() {
  if (!form.value.name || !form.value.deviceEui) {
    error.value = 'Name and Device EUI are required.'
    return
  }
  loading.value = true
  error.value = ''
  try {
    let saved: Animal
    if (isEdit && props.animal) {
      saved = await updateAnimal(props.animal.id, form.value)
    } else {
      saved = await createAnimal(form.value)
    }
    if (form.value.homeGeofenceId !== originalHomeGeofenceId) {
      await setAnimalHomeFence(saved.id, form.value.homeGeofenceId)
    }
    emit('saved')
    emit('close')
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? (isEdit ? 'Failed to save changes.' : 'Failed to create animal.')
  } finally {
    loading.value = false
  }
}

function onBackdrop(e: MouseEvent) {
  if ((e.target as HTMLElement).classList.contains('backdrop')) emit('close')
}
</script>

<template>
  <div class="backdrop" @click="onBackdrop" @keydown.esc="$emit('close')">
    <div class="modal" role="dialog" aria-modal="true">
      <div class="modal-header">
        <h2>{{ isEdit ? 'Edit Animal' : 'Add Animal' }}</h2>
        <button class="close-btn" @click="$emit('close')">✕</button>
      </div>

      <form class="modal-body" @submit.prevent="submit">
        <label>
          Name *
          <input v-model="form.name" type="text" placeholder="e.g. Sheep-006" required />
        </label>

        <label>
          Species *
          <select v-model="form.species">
            <option v-for="opt in SPECIES_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </option>
          </select>
        </label>

        <label>
          Device EUI *
          <input v-model="form.deviceEui" type="text" placeholder="e.g. DEVICE006" required />
        </label>

        <label>
          Collar Color
          <div class="color-row">
            <input v-model="form.collarColor" type="color" class="color-input" />
            <span class="color-hex">{{ form.collarColor }}</span>
          </div>
        </label>

        <label>
          Notes
          <textarea v-model="form.notes" rows="2" placeholder="Optional notes" />
        </label>

        <label>
          Home Fence
          <select v-model="form.homeGeofenceId">
            <option :value="null">— No fence —</option>
            <option v-for="f in activeFences" :key="f.id" :value="f.id">{{ f.name }}</option>
          </select>
        </label>

        <div v-if="error" class="error-msg">{{ error }}</div>

        <div class="modal-actions">
          <button type="button" class="btn-cancel" @click="$emit('close')">Cancel</button>
          <button type="submit" class="btn-submit" :disabled="loading">
            {{ loading ? 'Saving…' : (isEdit ? 'Save Changes' : 'Add Animal') }}
          </button>
        </div>
      </form>
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
  width: 380px;
  max-width: 95vw;
  box-shadow: 0 20px 60px rgba(0,0,0,0.5);
}
.modal-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
}
.modal-header h2 { font-size: 15px; font-weight: 600; }
.close-btn {
  background: none; border: none;
  color: var(--color-text-secondary);
  font-size: 16px; cursor: pointer;
  padding: 2px 6px;
}
.close-btn:hover { color: var(--color-text-primary); }

.modal-body {
  padding: 16px 20px;
  display: flex; flex-direction: column; gap: 12px;
}
label {
  display: flex; flex-direction: column; gap: 5px;
  font-size: 12px; color: var(--color-text-secondary);
}
input[type="text"], select, textarea {
  background: var(--color-bg-sidebar);
  border: 1px solid var(--color-border);
  border-radius: 5px;
  padding: 7px 10px;
  color: var(--color-text-primary);
  font-size: 13px;
  outline: none;
  transition: border-color 0.15s;
}
input:focus, select:focus, textarea:focus {
  border-color: var(--color-accent);
}
textarea { resize: vertical; min-height: 60px; }
select option { background: var(--color-bg-card); }

.color-row { display: flex; align-items: center; gap: 8px; }
.color-input { width: 40px; height: 32px; border: 1px solid var(--color-border); border-radius: 5px; padding: 2px; cursor: pointer; }
.color-hex { font-size: 12px; color: var(--color-text-secondary); font-family: monospace; }

.error-msg { color: var(--color-danger); font-size: 12px; }

.modal-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 4px; }
.btn-cancel {
  padding: 8px 16px; background: transparent;
  border: 1px solid var(--color-border); border-radius: 5px;
  color: var(--color-text-secondary); font-size: 13px;
}
.btn-cancel:hover { border-color: var(--color-text-secondary); }
.btn-submit {
  padding: 8px 16px; background: var(--color-accent);
  border: none; border-radius: 5px;
  color: #fff; font-size: 13px; font-weight: 600;
}
.btn-submit:hover { background: var(--color-accent-hover); }
.btn-submit:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
