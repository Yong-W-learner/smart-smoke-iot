import request from '@/utils/request'

export const getForestBootstrap = () => request.get('/api/forest/bootstrap')

export const createForestMission = data => request.post('/api/forest/missions', data)

export const startForestMission = (id, data) => request.post(`/api/forest/missions/${id}/start`, data)

export const updateForestMissionStatus = (id, data) => request.put(`/api/forest/missions/${id}/status`, data)

export const saveDroneTelemetry = (id, data) => request.post(`/api/forest/missions/${id}/telemetry`, data)

export const completeForestMission = (id, data) => request.post(`/api/forest/missions/${id}/complete`, data)

export const getForestMissionDetail = id => request.get(`/api/forest/missions/${id}/detail`)

export const addForestMissionPhoto = (id, data) => request.post(`/api/forest/missions/${id}/photos`, data)

export const updateForestIncident = (id, data) => request.put(`/api/forest/incidents/${id}`, data)

export const createForestIncident = data => request.post('/api/forest/incidents', data)

export const simulateForestIncident = data => request.post('/api/forest/incidents/simulate', data)

export const updateForestSensor = (id, data) => request.post(`/api/forest/sensors/${id}/reading`, data)

export const getForestSensorHistory = (id, metric, limit = 120) => request.get(`/api/forest/sensors/${id}/history`, { params: { metric, limit } })

export const publishForestBroadcast = data => request.post('/api/forest/broadcast', data)

export const getLatestForestBroadcast = () => request.get('/api/forest/broadcast/latest')

export const getForestBroadcastHistory = (limit = 8) => request.get('/api/forest/broadcast/history', { params: { limit } })

export const getForestMap = () => request.get('/api/forest/map')

export const saveForestMap = data => request.put('/api/forest/map', data)

export const saveMapView = data => request.post('/api/forest/map/view', data)

export const updateForestZone = (id, data) => request.put(`/api/forest/zones/${id}`, data)

export const createForestZone = data => request.post('/api/forest/zones', data)

export const deleteForestZone = id => request.delete(`/api/forest/zones/${id}`)

export const reportRangerPosition = data => request.post('/api/forest/rangers/position', data)

export const getForestEquipment = () => request.get('/api/forest/equipment/bootstrap')

export const addForestEquipment = data => request.post('/api/forest/equipment', data)

export const updateForestEquipmentStatus = (id, data) => request.put(`/api/forest/equipment/${id}/status`, data)

export const updateForestFault = (id, data) => request.put(`/api/forest/equipment/faults/${id}`, data)

export const addForestMaintenance = data => request.post('/api/forest/equipment/maintenance', data)

export const startForestEquipmentSelfTest = id => request.post(`/api/forest/equipment/${id}/self-test`)

export const getForestEquipmentSelfTest = testNo => request.get(`/api/forest/equipment/self-tests/${testNo}`)
