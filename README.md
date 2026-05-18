# 牧场管理平台 · Livestock GPS Tracker

A full-stack web platform for real-time GPS tracking of free-ranging hill-area livestock — goats, sheep, chickens, pigs — via compact collar devices.

![Platform screenshot](https://github.com/turbojetw/mapviewPlatform/raw/main/docs/screenshot.png)

## Features

### Map & Tracking
- **Live animal markers** on an interactive MapLibre GL map, color-coded by collar
- **GPS trail** drawn on the map per animal (last 24 h or 7 days, fetched from backend)
- **Real-time updates** via STOMP WebSocket — markers move as fixes arrive
- **Animal detail panel** — slide-in panel showing current position, battery, HDOP, altitude, last-seen time, and a scrollable fix history

### Geofence Management
- **Draw fences on the map** with a click-to-place polygon tool (undo, finish, cancel)
- **Edit fences** by dragging vertices, midpoints, or polygon edges directly on the map; coordinates sync to a live table
- **Fence pool** — select any fence from the list to fly to it on the map; selected fence is highlighted with a brighter fill and a floating name label
- **Per-fence controls**: enable/disable (persisted), hide/show (client-side), delete
- **Geofence breach detection** — alerts fire when an animal exits a fence marked "alert on exit"

### Alerts
- **Toast notifications** on breach via WebSocket
- **Persistent alert history** — all breach events stored in the database and viewable from the 🔔 header button
- **Session badge** counts new alerts since the page loaded

### Sidebar
- Animal list sorted by signal status (animals with active GPS signal first)
- Stale signal indicator (> 1 hour since last fix) and no-signal state
- Offline count badge

### Simulator
- Push random GPS fixes for any or all animals without physical hardware (`⚡ Sim` button per animal or global)

---

## Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.3 · Java 17 · Spring Data JPA · Spring WebSocket (STOMP) |
| Database | H2 in-memory (dev) · PostgreSQL (prod) |
| Geospatial | JTS Topology Suite (point-in-polygon, no PostGIS required) |
| Frontend | Vue 3 · Vite · TypeScript · MapLibre GL JS |
| Map tiles | OpenStreetMap (raster) |
| Realtime | SockJS + @stomp/stompjs |
| HTTP client | Axios |

---

## Project Structure

```
mapviewPlatform/
├── livestock-backend/          # Spring Boot API
│   └── src/main/java/com/agri/livestock/
│       ├── controller/         # REST endpoints + WebSocket
│       ├── service/            # Business logic
│       ├── entity/             # JPA entities
│       ├── repository/         # Spring Data repositories
│       └── dto/                # Request/response records
└── livestock-ui/               # Vue 3 frontend
    └── src/
        ├── components/         # MapPanel, AnimalSidebar, GeoFenceModal, …
        ├── composables/        # useWebSocket
        ├── api/                # Axios calls
        ├── types/              # TypeScript interfaces
        └── utils/              # timeAgo, formatCoord
```

---

## Getting Started

### Prerequisites
- Java 17+
- Node.js 18+
- Maven 3.8+ (or use `./mvnw`)

### Backend

```bash
cd livestock-backend
mvn spring-boot:run
# API available at http://localhost:8081
# H2 console at http://localhost:8081/h2-console
```

Seed data (5 animals + 1 geofence) is loaded automatically on startup.

### Frontend

```bash
cd livestock-ui
npm install
npm run dev
# UI at http://localhost:3001
```

The Vite dev server proxies `/api` and `/ws` to the backend at `:8081`.

### Production (PostgreSQL)

```bash
cd livestock-backend
mvn spring-boot:run --spring.profiles.active=prod \
  -DDB_URL=jdbc:postgresql://localhost:5432/livestock \
  -DDB_USER=livestock -DDB_PASS=yourpassword
```

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/animals` | List all active animals |
| POST | `/api/animals` | Register a new animal |
| GET | `/api/locations/latest` | Latest GPS fix per animal |
| GET | `/api/locations/{id}/history` | Fix history with `from`/`to` params |
| POST | `/api/simulator/push-random/{id}` | Push a random fix for one animal |
| POST | `/api/simulator/push-random-all` | Push random fixes for all animals |
| GET | `/api/geofences` | List all geofences |
| POST | `/api/geofences` | Create a geofence |
| PUT | `/api/geofences/{id}` | Update a geofence |
| DELETE | `/api/geofences/{id}` | Soft-delete a geofence |
| GET | `/api/alerts` | Recent geofence breach events |

WebSocket topic: `/topic/animal/location` — broadcasts `AnimalStatusDto` on each GPS fix.

---

## Device Payload Format

POST to `/api/simulator/push` (or via MQTT with `mqtt.enabled=true`):

```json
{
  "deviceEui": "DEVICE001",
  "lat": 28.201,
  "lng": 105.504,
  "alt": 1240,
  "hdop": 1.2,
  "bat": 3800
}
```

`bat` is battery voltage in mV (3200 mV = 0%, 4200 mV = 100%).
