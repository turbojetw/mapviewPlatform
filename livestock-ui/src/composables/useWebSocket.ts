import { ref } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { AnimalStatus, AlertNotification, FenceStats } from '../types'

export function useWebSocket() {
  const isConnected = ref(false)
  let updateCallback: ((status: AnimalStatus) => void) | null = null
  let alertCallback: ((n: AlertNotification) => void) | null = null
  let fenceStatsCallback: ((s: FenceStats) => void) | null = null

  const client = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: 3000,
    onConnect: () => {
      isConnected.value = true
      client.subscribe('/topic/animal/location', (msg) => {
        try {
          const status: AnimalStatus = JSON.parse(msg.body)
          updateCallback?.(status)
        } catch (e) {
          console.error('Failed to parse location message', e)
        }
      })
      client.subscribe('/topic/geofence/alert', (msg) => {
        try {
          const notification: AlertNotification = JSON.parse(msg.body)
          alertCallback?.(notification)
        } catch (e) {
          console.error('Failed to parse alert message', e)
        }
      })
      client.subscribe('/topic/fence/stats', (msg) => {
        try {
          const stats: FenceStats = JSON.parse(msg.body)
          fenceStatsCallback?.(stats)
        } catch (e) {
          console.error('Failed to parse fence stats message', e)
        }
      })
    },
    onDisconnect: () => {
      isConnected.value = false
    },
    onStompError: (frame) => {
      console.error('STOMP error', frame)
    },
  })

  function connect() {
    client.activate()
  }

  function disconnect() {
    client.deactivate()
  }

  function onAnimalUpdate(cb: (status: AnimalStatus) => void) {
    updateCallback = cb
  }

  function onAlert(cb: (n: AlertNotification) => void) {
    alertCallback = cb
  }

  function onFenceStats(cb: (s: FenceStats) => void) {
    fenceStatsCallback = cb
  }

  return { connect, disconnect, onAnimalUpdate, onAlert, onFenceStats, isConnected }
}
