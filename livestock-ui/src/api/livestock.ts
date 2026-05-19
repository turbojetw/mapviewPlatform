import axios from 'axios'
import type { Animal, AnimalStatus, GeoFence, LocationHistory, DevicePayload, GeofenceAlertRecord, FenceStats } from '../types'

const api = axios.create({ baseURL: '/api' })

export const fetchAnimals = (): Promise<Animal[]> =>
  api.get('/animals').then(r => r.data)

export const createAnimal = (data: Partial<Animal>): Promise<Animal> =>
  api.post('/animals', data).then(r => r.data)

export const updateAnimal = (id: number, data: Partial<Animal>): Promise<Animal> =>
  api.put(`/animals/${id}`, data).then(r => r.data)

export const deleteAnimal = (id: number): Promise<void> =>
  api.delete(`/animals/${id}`).then(() => undefined)

export const fetchLatestStatuses = (): Promise<AnimalStatus[]> =>
  api.get('/locations/latest').then(r => r.data)

export const fetchHistory = (animalId: number, from: string, to: string): Promise<LocationHistory[]> =>
  api.get(`/locations/${animalId}/history`, { params: { from, to } }).then(r => r.data)

export const fetchGeoFences = (): Promise<GeoFence[]> =>
  api.get('/geofences').then(r => r.data)

export const createGeoFence = (data: Partial<GeoFence>): Promise<GeoFence> =>
  api.post('/geofences', data).then(r => r.data)

export const updateGeoFence = (id: number, data: Partial<GeoFence>): Promise<GeoFence> =>
  api.put(`/geofences/${id}`, data).then(r => r.data)

export const deleteGeoFence = (id: number): Promise<void> =>
  api.delete(`/geofences/${id}`).then(() => undefined)

export const simulatorPush = (payload: DevicePayload): Promise<void> =>
  api.post('/simulator/push', payload).then(() => undefined)

export const simulatorPushRandom = (animalId: number): Promise<void> =>
  api.post(`/simulator/push-random/${animalId}`).then(() => undefined)

export const simulatorPushRandomAll = (): Promise<void> =>
  api.post('/simulator/push-random-all').then(() => undefined)

export const fetchAlerts = (limit = 50): Promise<GeofenceAlertRecord[]> =>
  api.get('/alerts', { params: { limit } }).then(r => r.data)

export const fetchFenceStats = (fenceId: number): Promise<FenceStats> =>
  api.get(`/geofences/${fenceId}/stats`).then(r => r.data)

export const fetchFenceAnimals = (fenceId: number): Promise<Animal[]> =>
  api.get(`/geofences/${fenceId}/animals`).then(r => r.data)

export const assignAnimalsToFence = (fenceId: number, animalIds: number[]): Promise<void> =>
  api.put(`/geofences/${fenceId}/animals`, animalIds).then(() => undefined)
