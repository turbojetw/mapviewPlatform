export type Species = 'GOOSE' | 'CHICKEN' | 'SHEEP' | 'PIG'

export interface Animal {
  id: number
  name: string
  species: Species
  deviceEui: string
  collarColor: string
  notes?: string
  active: boolean
  createdAt: string
}

export interface AnimalStatus {
  animalId: number
  name: string
  species: Species
  speciesEmoji: string
  collarColor: string
  lat: number
  lng: number
  altitude: number
  hdop: number
  batteryMv: number
  batteryPct: number
  timestamp: string
  geofenceAlert: string | null
}

export interface GeoFence {
  id: number
  name: string
  coordinatesJson: string
  color: string
  fillOpacity: number
  strokeWidth: number
  alertOnExit: boolean
  active: boolean
}

export interface LocationHistory {
  lat: number
  lng: number
  altitude: number
  timestamp: string
  batteryMv: number
}

export interface GeofenceAlertRecord {
  id: number
  animalId: number
  animalName: string
  fenceName: string
  timestamp: string
}

export interface AlertNotification {
  animalId: number
  animalName: string
  fenceName: string
  timestamp: string
}

export interface DevicePayload {
  deviceEui: string
  ts?: number
  lat: number
  lng: number
  alt?: number
  hdop?: number
  bat?: number
}
