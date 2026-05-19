<script setup lang="ts">
export interface AlertToastItem {
  uid: number
  animalId: number
  animalName: string
  fenceName: string
}

defineProps<{ items: AlertToastItem[] }>()

defineEmits<{
  (e: 'dismiss', uid: number): void
  (e: 'select-animal', animalId: number): void
}>()
</script>

<template>
  <div class="toast-stack">
    <transition-group name="alert-toast">
      <div
        v-for="item in items"
        :key="item.uid"
        class="alert-toast"
        @click="$emit('select-animal', item.animalId); $emit('dismiss', item.uid)"
      >
        <div class="toast-icon">⚠️</div>
        <div class="toast-body">
          <div class="toast-title">Fence Exit Alert</div>
          <div class="toast-msg">
            <span class="toast-animal">{{ item.animalName }}</span>
            <span class="toast-verb"> exited </span>
            <span class="toast-fence">"{{ item.fenceName }}"</span>
          </div>
          <div class="toast-action">Click to focus on map</div>
        </div>
        <button class="toast-close" @click.stop="$emit('dismiss', item.uid)">✕</button>
        <div class="toast-progress" />
      </div>
    </transition-group>
  </div>
</template>

<style scoped>
.toast-stack {
  position: fixed;
  top: calc(var(--header-height, 52px) + 12px);
  right: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  z-index: 3000;
  pointer-events: none;
}

.alert-toast {
  pointer-events: all;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 320px;
  padding: 12px 14px 14px;
  background: #1a1218;
  border: 1px solid #ef535066;
  border-left: 3px solid var(--color-danger);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: transform 0.15s, box-shadow 0.15s;
}
.alert-toast:hover {
  transform: translateX(-2px);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.6);
  border-color: #ef5350aa;
}

.toast-icon { font-size: 16px; flex-shrink: 0; margin-top: 1px; }

.toast-body { flex: 1; min-width: 0; }
.toast-title {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-danger);
  margin-bottom: 4px;
}
.toast-msg { font-size: 13px; line-height: 1.4; }
.toast-animal { font-weight: 600; color: var(--color-text-primary); }
.toast-verb { color: var(--color-text-secondary); font-size: 12px; }
.toast-fence { color: var(--color-warning); font-size: 12px; }
.toast-action { font-size: 10px; color: var(--color-text-secondary); margin-top: 4px; }

.toast-close {
  flex-shrink: 0;
  background: none;
  border: none;
  color: var(--color-text-secondary);
  font-size: 12px;
  cursor: pointer;
  padding: 0 2px;
  line-height: 1;
  opacity: 0.6;
}
.toast-close:hover { opacity: 1; color: var(--color-text-primary); }

/* Auto-dismiss progress bar at the bottom */
.toast-progress {
  position: absolute;
  bottom: 0;
  left: 0;
  height: 2px;
  width: 100%;
  background: var(--color-danger);
  transform-origin: left;
  animation: shrink 8s linear forwards;
}
@keyframes shrink {
  from { transform: scaleX(1); }
  to   { transform: scaleX(0); }
}

/* Transition animations */
.alert-toast-enter-active { transition: all 0.3s ease; }
.alert-toast-leave-active { transition: all 0.25s ease; }
.alert-toast-enter-from { opacity: 0; transform: translateX(40px); }
.alert-toast-leave-to   { opacity: 0; transform: translateX(40px); height: 0; margin-top: -8px; padding: 0; }
</style>
