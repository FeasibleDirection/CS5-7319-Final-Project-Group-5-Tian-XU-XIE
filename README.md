## Table of Contents

1. [Architecture A: Server-Authoritative (Layered Architecture)](#architecture-a-server-authoritative-layered-architecture)
2. [Architecture B: P2P Lockstep (Gossip Architecture)](#architecture-b-p2p-lockstep-gossip-architecture)
3. [Detailed Implementation Differences](#detailed-implementation-differences)
4. [Reusable Components and Connectors](#reusable-components-and-connectors)
5. [Compilation and Execution](#compilation-and-execution)
6. [Architecture Selection Rationale](#architecture-selection-rationale)
7. [Project Structure](#project-structure)

---

## Project Overview

This project implements a multiplayer online game called "Ground Attack" with two distinct architectural approaches:

- **Architecture A (Layered)**: Server-authoritative architecture where the server maintains the authoritative game state and performs all game logic calculations.
- **Architecture B (P2P)**: Peer-to-peer gossip architecture where each client maintains its own game state and the server only acts as a message relay.

Both architectures are fully implemented and can be tested independently.

---

## Architecture Overview

### Architecture A: Server-Authoritative (Layered)

**Location**: `select/Layered/`

**Key Characteristics**:
- Server maintains authoritative game state
- Server performs all physics calculations and collision detection
- Clients send only input commands
- Server broadcasts game state updates at 25 FPS
- Centralized game logic ensures consistency

### Architecture B: P2P Lockstep (Gossip)

**Location**: `Unselected/p2p/`

**Key Characteristics**:
- Each client maintains its own game state
- Clients perform local physics and collision detection
- Server acts as a message relay only
- Clients broadcast their state to peers
- Decentralized logic with eventual consistency

---

## Architecture A: Server-Authoritative (Layered Architecture)

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │   Client 1   │  │   Client 2   │  │   Client 3   │     │
│  │ (Browser)    │  │ (Browser)    │  │ (Browser)    │     │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘     │
│         │                  │                  │            │
│         └──────────────────┼──────────────────┘            │
│                            │                                 │
│                    WebSocket (/ws/game)                      │
└────────────────────────────┼─────────────────────────────────┘
                             │
┌────────────────────────────┼─────────────────────────────────┐
│                    Business Logic Layer                      │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         GameWebSocketHandler                         │  │
│  │  - Handles WebSocket connections                     │  │
│  │  - Processes player input                           │  │
│  │  - Broadcasts game state                             │  │
│  └──────────────┬───────────────────────────────────────┘  │
│                 │                                            │
│  ┌──────────────┴──────────────────────────────────────┐  │
│  │         GameTickScheduler (25 FPS)                   │  │
│  │  - Fixed-rate game loop                              │  │
│  │  - Updates all game worlds                          │  │
│  └──────────────┬───────────────────────────────────────┘  │
│                 │                                            │
│  ┌──────────────┴──────────────────────────────────────┐  │
│  │         GameRoomManager                             │  │
│  │  - Manages active game rooms                        │  │
│  │  - Creates/destroys GameWorld instances             │  │
│  └──────────────┬───────────────────────────────────────┘  │
│                 │                                            │
│  ┌──────────────┴──────────────────────────────────────┐  │
│  │         PhysicsEngine                                │  │
│  │  - Physics calculations                             │  │
│  │  - Collision detection                               │  │
│  │  - Asteroid spawning                                │  │
│  └──────────────┬───────────────────────────────────────┘  │
│                 │                                            │
│  ┌──────────────┴──────────────────────────────────────┐  │
│  │         EventBus                                      │  │
│  │  - Event-driven communication                        │  │
│  │  - Decouples components                              │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────────┼─────────────────────────────────┘
                             │
┌────────────────────────────┼─────────────────────────────────┐
│                      Data Layer                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         UserRepository                               │  │
│  │         GameLogRepository                            │  │
│  │         SQLite Database (airplane.db)                │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Key Components

#### 1. GameWebSocketHandler
**File**: `websocket/GameWebSocketHandler.java`

**Responsibilities**:
- Manages WebSocket connections for Architecture A
- Receives `PLAYER_INPUT` messages from clients
- Processes input through the event system
- Broadcasts `GAME_STATE` updates to all clients in a room

**Key Methods**:
- `handlePlayerInputArchA()`: Processes player input and publishes `InputReceivedEvent`
- `broadcastGameState()`: Sends game state snapshot to all clients

**Dependencies**:
- `GameRoomManager`: Access to game worlds
- `PhysicsEngine`: Physics calculations
- `EventBus`: Event-driven communication
- `AuthService`: Authentication

#### 2. GameTickScheduler
**File**: `game/GameTickScheduler.java`

**Responsibilities**:
- Implements the main game loop at 25 FPS (40ms per tick)
- Updates all active `GameWorld` instances
- Triggers physics updates and collision detection
- Broadcasts game state after each tick

**Key Features**:
- `@Scheduled(fixedRate = 40)`: Spring scheduled task
- Iterates through all active game rooms
- Updates physics, handles collisions, spawns asteroids
- Sends state snapshots via WebSocket

**Dependencies**:
- `GameRoomManager`: Access to game worlds
- `PhysicsEngine`: Physics calculations
- `GameWebSocketHandler`: Broadcasting state
- `EventBus`: Event handling

#### 3. GameWorld
**File**: `game/GameWorld.java`

**Responsibilities**:
- Maintains authoritative game state for a single room
- Stores all players, bullets, and asteroids
- Tracks game phase (WAITING, COUNTDOWN, IN_PROGRESS, FINISHED)
- Manages frame numbers and timing

**Key Data Structures**:
- `Map<String, PlayerEntity> players`: All players in the room
- `Map<String, BulletEntity> bullets`: All active bullets
- `Map<String, AsteroidEntity> asteroids`: All active asteroids

#### 4. PhysicsEngine
**File**: `game/PhysicsEngine.java`

**Responsibilities**:
- Performs all physics calculations on the server
- Handles player movement based on input
- Detects collisions (bullet-asteroid, player-asteroid)
- Spawns new asteroids at regular intervals
- Updates entity positions

**Key Methods**:
- `applyPlayerInput()`: Converts input to velocity
- `updateEntities()`: Updates positions based on velocities
- `detectCollisions()`: Detects and handles collisions
- `spawnAsteroid()`: Creates new asteroids

#### 5. EventBus
**File**: `event/EventBus.java`

**Responsibilities**:
- Implements event-driven communication pattern
- Decouples components (WebSocket handler, physics engine, game logic)
- Publishes events: `InputReceivedEvent`, `CollisionDetectedEvent`, `ScoreUpdatedEvent`, `GameEndedEvent`

**Event Flow**:
```
InputReceivedEvent → PhysicsEngine → CollisionDetectedEvent → ScoreUpdatedEvent → GameEndedEvent
```

### Frontend: game-architecture-a.js

**Key Characteristics**:
- **Thin Client**: Only sends input, receives state
- **No Local Game Logic**: All game state comes from server
- **Rendering Only**: Displays server-provided state
- **Input Rate**: Sends input at 20 Hz (50ms intervals)

**Key Functions**:
- `connectWebSocket()`: Connects to `/ws/game`
- `sendInput()`: Sends `PLAYER_INPUT` messages
- `handleGameState()`: Receives and renders `GAME_STATE` updates
- `render()`: Renders game state to canvas

---

## Architecture B: P2P Lockstep (Gossip Architecture)

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Peer Layer (Clients)                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │   Peer 1     │  │   Peer 2     │  │   Peer 3     │     │
│  │ (Full Logic) │  │ (Full Logic) │  │ (Full Logic) │     │
│  │             │  │             │  │             │     │
│  │ - Physics   │  │ - Physics   │  │ - Physics   │     │
│  │ - Collision │  │ - Collision │  │ - Collision │     │
│  │ - Spawn     │  │ - Spawn     │  │ - Spawn     │     │
│  └──────┬──────┘  └──────┬──────┘  └──────┬───────┘     │
│         │                  │                  │            │
│         └──────────────────┼──────────────────┘            │
│                            │                                 │
│                    WebSocket (/ws/game-b)                   │
│                    (Gossip Messages)                        │
└────────────────────────────┼─────────────────────────────────┘
                             │
┌────────────────────────────┼─────────────────────────────────┐
│                    Relay Layer (Server)                      │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         GameWebSocketHandlerB                        │  │
│  │  - Authentication                                   │  │
│  │  - Room validation                                  │  │
│  │  - Message relay (no game logic)                    │  │
│  │  - Game end voting coordination                    │  │
│  └──────────────┬───────────────────────────────────────┘  │
└─────────────────┼──────────────────────────────────────────┘
                   │
┌──────────────────┼──────────────────────────────────────────┐
│                  Business Layer (Minimal)                    │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         LobbyService                                 │  │
│  │         AuthService                                  │  │
│  └──────────────────────────────────────────────────────┘  │
└──────────────────┼──────────────────────────────────────────┘
                   │
┌──────────────────┼──────────────────────────────────────────┐
│                      Data Layer                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         UserRepository                               │  │
│  │         GameLogRepository                            │  │
│  │         SQLite Database (airplane.db)                │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Key Components

#### 1. GameWebSocketHandlerB
**File**: `websocket/GameWebSocketHandlerB.java`

**Responsibilities**:
- Manages WebSocket connections for Architecture B
- **Only relays messages** - no game logic
- Handles authentication and room validation
- Coordinates game end voting
- Logs all gossip messages for debugging

**Key Methods**:
- `handleJoinGame()`: Validates and registers players
- `handleGossipMessage()`: Relays game messages to room peers
- `handleGameEndVote()`: Collects votes and finalizes game end
- `broadcastToRoom()`: Forwards messages to all players in room

**Message Types Handled**:
- `JOIN_GAME_B`: Player joins game
- `PLAYER_POSITION`: Player position update (relayed)
- `ASTEROID_SPAWN`: Asteroid spawn (relayed)
- `ASTEROID_POSITION`: Asteroid position (relayed)
- `BULLET_FIRED`: Bullet fired (relayed)
- `GAME_END_VOTE`: Game end voting

**Dependencies**:
- `AuthService`: Authentication only
- `LobbyService`: Room validation only
- `GameLogRepository`: Save game logs
- **No dependencies on**: `GameRoomManager`, `PhysicsEngine`, `EventBus`

#### 2. Game End Voting System
**File**: `websocket/GameWebSocketHandlerB.java` (internal classes)

**Responsibilities**:
- Coordinates game end voting among peers
- Collects votes from all players
- Determines final game result
- Saves game log to database

**Key Classes**:
- `GameEndVote`: Stores vote information (reason, score, hp, alive)
- `checkAndFinalizeGameEnd()`: Checks if all players voted and finalizes

### Frontend: game-architecture-b.js

**Key Characteristics**:
- **Thick Client**: Full game logic implementation
- **Local State Management**: Maintains own game state
- **Local Physics**: Performs physics calculations
- **Local Collision Detection**: Detects collisions locally
- **Gossip Protocol**: Broadcasts state to peers

**Key Functions**:
- `connectWebSocket()`: Connects to `/ws/game-b`
- `gameLoop()`: Main game loop (60 FPS)
- `updateMyPlayer()`: Updates local player position
- `spawnMyAsteroids()`: Generates asteroids locally
- `detectMyCollisions()`: Local collision detection
- `broadcastStates()`: Sends state to peers
- `checkGameEndConditions()`: Detects game end conditions
- `voteGameEnd()`: Sends game end vote

**Local State**:
- `myPlayer`: Local player state
- `myAsteroids`: Locally generated asteroids
- `myBullets`: Locally fired bullets
- `gameState.players`: Merged state from all peers
- `gameState.asteroids`: Merged asteroids from all peers

---

## Detailed Implementation Differences

### 1. Source Code Differences

#### Backend Components

| Component | Architecture A | Architecture B |
|-----------|---------------|----------------|
| **WebSocket Handler** | `GameWebSocketHandler.java` | `GameWebSocketHandlerB.java` |
| **Game Loop** | `GameTickScheduler.java` (Server-side, 25 FPS) | None (Client-side, 60 FPS) |
| **Game State** | `GameWorld.java` (Server maintains) | None (Clients maintain) |
| **Physics Engine** | `PhysicsEngine.java` (Server-side) | None (Client-side in JS) |
| **Event System** | `EventBus.java` + Event classes | None |
| **Scheduling** | `SchedulingConfig.java` (Required) | Not required |

#### Frontend Components

| Component | Architecture A | Architecture B |
|-----------|---------------|----------------|
| **Main Script** | `game-architecture-a.js` | `game-architecture-b.js` |
| **Game Logic** | Rendering only | Full game logic |
| **State Management** | Receives from server | Maintains locally |
| **Physics** | None | Local calculations |
| **Collision Detection** | None | Local detection |
| **Asteroid Generation** | None | Local generation |

#### WebSocket Endpoints

| Architecture | Endpoint | Handler |
|--------------|----------|---------|
| Architecture A | `/ws/game` | `GameWebSocketHandler` |
| Architecture B | `/ws/game-b` | `GameWebSocketHandlerB` |

#### Message Types

**Architecture A Messages**:
- `JOIN_GAME`: Client joins game
- `PLAYER_INPUT`: Client sends input
- `GAME_STATE`: Server broadcasts state
- `LEAVE_GAME`: Client leaves

**Architecture B Messages**:
- `JOIN_GAME_B`: Client joins game
- `PLAYER_POSITION`: Client broadcasts position
- `ASTEROID_SPAWN`: Client broadcasts asteroid spawn
- `ASTEROID_POSITION`: Client broadcasts asteroid position
- `BULLET_FIRED`: Client broadcasts bullet
- `BULLET_POSITION`: Client broadcasts bullet position
- `PLAYER_HIT`: Client broadcasts hit event
- `PLAYER_DEAD`: Client broadcasts death
- `SCORE_UPDATE`: Client broadcasts score
- `GAME_END_VOTE`: Client votes for game end
- `GAME_ENDED`: Server broadcasts game end

### 2. Component-Level Differences

#### GameWebSocketHandler vs GameWebSocketHandlerB

**GameWebSocketHandler (Architecture A)**:
```java
// Dependencies
private final GameRoomManager roomManager;
private final PhysicsEngine physicsEngine;
private final EventBus eventBus;

// Handles game logic
- Processes player input
- Publishes events to EventBus
- Broadcasts game state from GameWorld
- Manages game room state
```

**GameWebSocketHandlerB (Architecture B)**:
```java
// Dependencies
private final AuthService authService;
private final LobbyService lobbyService;
private final GameLogRepository gameLogRepository;

// No game logic dependencies
- Only authenticates and validates
- Relays messages to room peers
- Coordinates game end voting
- No physics, no game state management
```

#### GameTickScheduler (Architecture A Only)

**File**: `game/GameTickScheduler.java`

**Purpose**: Implements server-side game loop

**Key Features**:
- `@Scheduled(fixedRate = 40)`: Runs every 40ms (25 FPS)
- Iterates through all active `GameWorld` instances
- Calls `PhysicsEngine` to update physics
- Detects collisions and handles events
- Broadcasts state via `GameWebSocketHandler`

**Why Architecture B doesn't need it**:
- Architecture B has no server-side game loop
- All game logic runs on clients
- Server only relays messages

#### PhysicsEngine (Architecture A Only)

**File**: `game/PhysicsEngine.java`

**Purpose**: Server-side physics calculations

**Key Features**:
- `applyPlayerInput()`: Converts input to velocity
- `updateEntities()`: Updates positions
- `detectCollisions()`: Detects collisions
- `spawnAsteroid()`: Creates asteroids

**Why Architecture B doesn't need it**:
- Physics calculations run on each client
- Implemented in JavaScript (`game-architecture-b.js`)
- Each peer maintains its own physics state

#### EventBus (Architecture A Only)

**File**: `event/EventBus.java`

**Purpose**: Event-driven communication between components

**Event Types**:
- `InputReceivedEvent`
- `CollisionDetectedEvent`
- `ScoreUpdatedEvent`
- `GameEndedEvent`
- `PlayerJoinedEvent`

**Why Architecture B doesn't need it**:
- Architecture B uses direct message passing
- No server-side event system needed
- Clients communicate via WebSocket messages

### 3. Data Flow Differences

#### Architecture A Data Flow

```
Client Input
    ↓
WebSocket (/ws/game)
    ↓
GameWebSocketHandler.handlePlayerInputArchA()
    ↓
EventBus.publish(InputReceivedEvent)
    ↓
GameTickScheduler (25 FPS loop)
    ↓
PhysicsEngine.updateEntities()
    ↓
PhysicsEngine.detectCollisions()
    ↓
EventBus.publish(CollisionDetectedEvent)
    ↓
GameWorld.updateState()
    ↓
GameWebSocketHandler.broadcastGameState()
    ↓
WebSocket → All Clients
    ↓
Clients Render State
```

#### Architecture B Data Flow

```
Client Game Loop (60 FPS)
    ↓
Local Physics Calculation
    ↓
Local Collision Detection
    ↓
Local State Update
    ↓
Broadcast State (20 Hz)
    ↓
WebSocket (/ws/game-b)
    ↓
GameWebSocketHandlerB.handleGossipMessage()
    ↓
Relay to Room Peers
    ↓
WebSocket → Other Clients
    ↓
Clients Merge Received State
    ↓
Clients Render Merged State
```

### 4. State Management Differences

#### Architecture A: Centralized State

**Server State** (`GameWorld.java`):
```java
Map<String, PlayerEntity> players;      // Authoritative
Map<String, BulletEntity> bullets;     // Authoritative
Map<String, AsteroidEntity> asteroids;  // Authoritative
```

**Client State** (`game-architecture-a.js`):
```javascript
let gameState = {
    phase: 'WAITING',      // From server
    frame: 0,             // From server
    players: [],          // From server
    bullets: [],          // From server
    asteroids: []         // From server
};
```

#### Architecture B: Distributed State

**Server State**: None (only connection management)

**Client State** (`game-architecture-b.js`):
```javascript
// Local state
let myPlayer = { x, y, hp, score, alive };
let myAsteroids = {};  // Locally generated
let myBullets = {};    // Locally fired

// Merged state from peers
let gameState = {
    players: {},     // Merged from all peers
    asteroids: {},   // Merged from all peers
    bullets: {}      // Merged from all peers
};
```

### 5. Consistency and Synchronization

#### Architecture A: Strong Consistency

- **Single Source of Truth**: Server maintains authoritative state
- **Synchronization**: Clients receive state updates from server
- **Consistency**: All clients see the same state (with network delay)
- **Conflict Resolution**: Server decides (no conflicts)

#### Architecture B: Eventual Consistency

- **Multiple Sources**: Each client maintains its own state
- **Synchronization**: Clients broadcast state to peers
- **Consistency**: Clients may see slightly different states
- **Conflict Resolution**: Last message wins (or client-side logic)

---

## Reusable Components and Connectors

### Shared Components (Used by Both Architectures)

#### 1. Controllers
**Location**: `controller/`

**Components**:
- `AuthController.java`: User authentication (login, token validation)
- `LobbyController.java`: Room management (create, join, leave rooms)
- `GameController.java`: Game-related REST endpoints
- `HealthController.java`: Health check endpoint

**Reusability**:  Fully reusable - no architecture-specific logic

#### 2. Data Access Objects (DAOs)
**Location**: `dao/`

**Components**:
- `UserRepository.java`: User data access
- `GameLogRepository.java`: Game log data access

**Reusability**:  Fully reusable - database operations are architecture-agnostic

#### 3. Data Transfer Objects (DTOs)
**Location**: `dto/`

**Components**:
- `LoginRequest.java`, `LoginResponse.java`: Authentication
- `RoomDto.java`, `CreateRoomRequest.java`: Room management
- `GameScoreEntry.java`, `LeaderboardEntryDto.java`: Game data
- `PlayerInfoDto.java`, `LobbySlotDto.java`: Player information

**Reusability**:  Fully reusable - data structures are shared

#### 4. Entities
**Location**: `entity/`

**Components**:
- `User.java`: User entity
- `GameLog.java`: Game log entity

**Reusability**:  Fully reusable - database entities are shared

#### 5. Services
**Location**: `service/`

**Components**:
- `AuthService.java`: Authentication logic
- `LobbyService.java`: Room management logic
- `GameServiceArchA.java`: Architecture A specific (optional)
- `GameServiceArchB.java`: Architecture B specific (optional)

**Reusability**: 
- `AuthService`:  Fully reusable
- `LobbyService`:  Fully reusable
- `GameServiceArchA/ArchB`:  Architecture-specific (but can coexist)

#### 6. Configuration
**Location**: `config/`

**Components**:
- `WebSocketConfig.java`: WebSocket endpoint configuration
- `SchedulingConfig.java`: Scheduling configuration (Architecture A only)
- `DatabaseInitializer.java`: Database initialization

**Reusability**:
- `WebSocketConfig`:  Registers both handlers (can be split)
- `SchedulingConfig`:  Architecture A only
- `DatabaseInitializer`:  Fully reusable

#### 7. Frontend Shared Resources
**Location**: `resources/static/`

**Components**:
- `login.html`, `lobby.html`, `game.html`: HTML pages
- `login.js`, `lobby.js`, `session.js`: Shared JavaScript
- `*.css`: Stylesheets

**Reusability**:  Fully reusable - HTML dynamically loads architecture-specific JS

### Architecture-Specific Components

#### Architecture A Only

| Component | Purpose | Why Architecture-Specific |
|-----------|---------|---------------------------|
| `GameWebSocketHandler.java` | WebSocket handler for Arch A | Processes game logic, manages game state |
| `GameTickScheduler.java` | Server-side game loop | Architecture B has no server loop |
| `GameWorld.java` | Game state container | Architecture B has no server state |
| `PhysicsEngine.java` | Physics calculations | Architecture B does physics on client |
| `EventBus.java` | Event system | Architecture B uses direct messaging |
| `game/` package entities | Game entities | Used by server-side logic |
| `event/` package | Event classes | Used by event-driven architecture |
| `game-architecture-a.js` | Client-side script | Thin client implementation |
| `SchedulingConfig.java` | Enables scheduling | Needed for GameTickScheduler |

#### Architecture B Only

| Component | Purpose | Why Architecture-Specific |
|-----------|---------|---------------------------|
| `GameWebSocketHandlerB.java` | WebSocket handler for Arch B | Message relay only, no game logic |
| `game-architecture-b.js` | Client-side script | Thick client with full game logic |

### Connectors

#### WebSocket Connectors

**Architecture A Connector**:
- **Type**: Request-Reply + Publish-Subscribe hybrid
- **Implementation**: `GameWebSocketHandler`
- **Protocol**: WebSocket over `/ws/game`
- **Message Flow**: 
  - Client → Server: `PLAYER_INPUT` (request)
  - Server → Clients: `GAME_STATE` (broadcast)

**Architecture B Connector**:
- **Type**: Publish-Subscribe (Gossip)
- **Implementation**: `GameWebSocketHandlerB`
- **Protocol**: WebSocket over `/ws/game-b`
- **Message Flow**:
  - Client → Server → Clients: `PLAYER_POSITION`, `ASTEROID_SPAWN`, etc. (relay)

#### Event Bus Connector (Architecture A Only)

**Type**: Event-driven
**Implementation**: `EventBus.java`
**Components Connected**:
- `GameWebSocketHandler` → `EventBus` → `GameTickScheduler`
- `PhysicsEngine` → `EventBus` → `GameWorld`
- `GameTickScheduler` → `EventBus` → `GameWebSocketHandler`

**Event Types**:
- `InputReceivedEvent`
- `CollisionDetectedEvent`
- `ScoreUpdatedEvent`
- `GameEndedEvent`

#### Database Connector

**Type**: JDBC
**Implementation**: Spring JDBC Template
**Components Using**:
- `UserRepository`: User data
- `GameLogRepository`: Game logs
- **Reusability**:  Used by both architectures

---

## Compilation and Execution

### Prerequisites

- **Java**: JDK 17 or higher
- **Maven**: 3.6+ (for building)
- **Database**: SQLite (embedded, no installation needed)
- **Browser**: Modern browser (Chrome, Firefox, Edge, Safari)

### Platform Information

- **Development Platform**: Windows 10/11, macOS, Linux
- **Java Version**: 17.0.17 (Dragonwell)
- **Spring Boot Version**: 3.3.5
- **Maven Version**: 3.x
- **SQLite JDBC**: 3.45.3.0

### Compilation Instructions

#### Option 1: Compile Main Project (Both Architectures)

```bash
# Navigate to project root
cd "D:\0 movie_subtitle\Software Architecture & Design\2 proposal_final\Project_Group_5_game_demo2"

# Clean and compile
mvn clean compile

# Package into JAR
mvn clean package

# Run the application
java -jar target/project-group5-game-demo2-0.0.1-SNAPSHOT.jar
```

#### Option 2: Compile Architecture A Only

```bash
cd select/Layered
mvn clean package
java -jar target/project-group5-game-demo2-0.0.1-SNAPSHOT.jar
```

#### Option 3: Compile Architecture B Only

```bash
cd Unselected/p2p
mvn clean package
java -jar target/project-group5-game-demo2-0.0.1-SNAPSHOT.jar
```

### Execution Instructions

#### Starting the Application

1. **Compile the project** (see above)

2. **Run the JAR file**:
   ```bash
   java -jar target/project-group5-game-demo2-0.0.1-SNAPSHOT.jar
   ```

3. **Verify startup**:
   - Check console for: `Started ProjectGroup5GameDemoApplication`
   - Database will be auto-created at: `src/main/resources/airplane.db`
   - Server runs on: http://localhost:8080

#### Accessing the Application

1. **Login Page**: http://localhost:8080/login.html
   - Username: `zhaoyuantian`, `xushikuan`, or `xiejing`
   - Password: `123456`

2. **Lobby**: http://localhost:8080/lobby.html
   - Create or join rooms
   - Select architecture (A or B)

3. **Game (Architecture A)**: http://localhost:8080/game.html?roomId=1&win=SCORE_50&arch=A

4. **Game (Architecture B)**: http://localhost:8080/game.html?roomId=1&win=SCORE_50&arch=B

### Database Initialization

The database is automatically initialized on first startup:

- **Location**: `src/main/resources/airplane.db`
- **Tables**: `users`, `game_logs`
- **Initial Data**: 3 users, sample game logs
- **Initialization Class**: `DatabaseInitializer.java`

No manual database setup required.

---

## Architecture Selection Rationale

### Selected Architecture: Architecture A (Server-Authoritative)

**Location**: `select/Layered/`

### Why Architecture A Was Selected

#### 1. **Security and Anti-Cheat**

**Architecture A**:
-  Server maintains authoritative state
-  All game logic runs on server
-  Clients cannot manipulate game state
-  Prevents cheating and exploits

**Architecture B**:
-  Clients maintain their own state
-  Game logic runs on client
-  Vulnerable to client-side manipulation
-  Requires additional anti-cheat measures

#### 2. **Consistency and Reliability**

**Architecture A**:
-  Single source of truth (server)
-  All clients see the same state
-  Deterministic game outcomes
-  Easier to debug and reproduce issues

**Architecture B**:
-  Multiple sources of truth (each client)
-  Clients may see different states
-  Non-deterministic outcomes possible
-  Harder to debug synchronization issues

#### 3. **Scalability**

**Architecture A**:
-  Server can handle game logic efficiently
-  Can implement server-side optimizations
-  Centralized state management
-  Easier to add features (server-side)

**Architecture B**:
-  Each client performs full game logic
-  Network bandwidth increases with player count
-  O(n²) message complexity (each peer broadcasts to all)
-  Client performance varies by device

#### 4. **Development and Maintenance**

**Architecture A**:
-  Clear separation of concerns
-  Server-side logic is easier to test
-  Centralized bug fixes
-  Event-driven architecture is well-structured

**Architecture B**:
-  Game logic duplicated across clients
-  Bug fixes must be deployed to all clients
-  Client-side logic harder to test
-  More complex state synchronization

#### 5. **Network Efficiency**

**Architecture A**:
-  Server broadcasts one state update to all clients
-  O(n) message complexity
-  Lower bandwidth usage
-  Predictable network load

**Architecture B**:
-  Each client broadcasts to all peers
-  O(n²) message complexity
-  Higher bandwidth usage
-  Network load increases quadratically

### Trade-offs

#### Architecture A Disadvantages

- **Latency**: Input → Server → Response adds one network round-trip
- **Server Load**: Server must process all game logic
- **Server Costs**: Requires more powerful server hardware

#### Architecture B Advantages

- **Low Latency**: Local calculations have zero network delay
- **Server Load**: Minimal server processing
- **Server Costs**: Lower server requirements

### Final Decision

**Architecture A was selected** because:

1. **Security is paramount** for multiplayer games
2. **Consistency** ensures fair gameplay
3. **Maintainability** is easier with centralized logic
4. **Scalability** is better for larger player counts
5. **Network efficiency** is important for mobile/limited bandwidth users

The trade-off of slightly higher latency is acceptable for the benefits of security, consistency, and maintainability.

---

## Project Structure

```
Project_Group_5_game_demo2/
├── src/main/java/com/projectgroup5/gamedemo/
│   ├── config/                    # Configuration classes
│   │   ├── DatabaseInitializer.java      (Shared)
│   │   ├── SchedulingConfig.java         (Arch A only)
│   │   └── WebSocketConfig.java          (Both)
│   │
│   ├── controller/                # REST controllers (Shared)
│   │   ├── AuthController.java
│   │   ├── GameController.java
│   │   ├── HealthController.java
│   │   └── LobbyController.java
│   │
│   ├── dao/                       # Data access (Shared)
│   │   ├── GameLogRepository.java
│   │   └── UserRepository.java
│   │
│   ├── dto/                       # Data transfer objects (Shared)
│   │   ├── LoginRequest.java
│   │   ├── LoginResponse.java
│   │   ├── RoomDto.java
│   │   └── ...
│   │
│   ├── entity/                    # Database entities (Shared)
│   │   ├── GameLog.java
│   │   └── User.java
│   │
│   ├── event/                     # Event system (Arch A only)
│   │   ├── EventBus.java
│   │   ├── InputReceivedEvent.java
│   │   ├── CollisionDetectedEvent.java
│   │   └── ...
│   │
│   ├── game/                      # Game logic (Arch A only)
│   │   ├── GameWorld.java
│   │   ├── GameRoomManager.java
│   │   ├── GameTickScheduler.java
│   │   ├── PhysicsEngine.java
│   │   ├── PlayerEntity.java
│   │   ├── BulletEntity.java
│   │   └── AsteroidEntity.java
│   │
│   ├── service/                   # Business logic (Mostly shared)
│   │   ├── AuthService.java       (Shared)
│   │   ├── LobbyService.java      (Shared)
│   │   ├── GameServiceArchA.java  (Arch A)
│   │   └── GameServiceArchB.java  (Arch B)
│   │
│   ├── websocket/                 # WebSocket handlers
│   │   ├── GameWebSocketHandler.java    (Arch A)
│   │   └── GameWebSocketHandlerB.java   (Arch B)
│   │
│   └── ProjectGroup5GameDemoApplication.java
│
├── src/main/resources/
│   ├── application.properties      # Configuration
│   └── static/
│       ├── *.html                 # HTML pages (Shared)
│       ├── css/                   # Stylesheets (Shared)
│       └── js/
│           ├── login.js           (Shared)
│           ├── lobby.js           (Shared)
│           ├── session.js        (Shared)
│           ├── game-architecture-a.js  (Arch A)
│           └── game-architecture-b.js  (Arch B)
│
├── select/Layered/                 # Architecture A implementation
│   └── [Complete Arch A code]
│
├── Unselected/p2p/                 # Architecture B implementation
│   └── [Complete Arch B code]
│
├── common/                         # Shared main class
│   └── src/main/java/.../ProjectGroup5GameDemoApplication.java
│
└── pom.xml                         # Maven configuration
```

---

## Component Mapping

### Architecture A Component → Class Mapping

| Component | Implementing Class(es) |
|-----------|----------------------|
| **WebSocket Handler** | `GameWebSocketHandler` |
| **Game Loop Scheduler** | `GameTickScheduler` |
| **Game State Manager** | `GameRoomManager`, `GameWorld` |
| **Physics Engine** | `PhysicsEngine` |
| **Event Bus** | `EventBus` |
| **Player Entity** | `PlayerEntity` |
| **Bullet Entity** | `BulletEntity` |
| **Asteroid Entity** | `AsteroidEntity` |
| **Input Handler** | `PlayerInput` |
| **Collision Detector** | `PhysicsEngine.detectCollisions()` |

### Architecture B Component → Class Mapping

| Component | Implementing Class(es) |
|-----------|----------------------|
| **WebSocket Relay** | `GameWebSocketHandlerB` |
| **Game Logic** | `game-architecture-b.js` (Client-side) |
| **Physics Engine** | `game-architecture-b.js` (Client-side) |
| **State Manager** | `game-architecture-b.js` (Client-side) |
| **Voting Coordinator** | `GameWebSocketHandlerB.handleGameEndVote()` |

### Shared Components

| Component | Implementing Class(es) | Used By |
|-----------|----------------------|---------|
| **Authentication** | `AuthController`, `AuthService`, `UserRepository` | Both |
| **Room Management** | `LobbyController`, `LobbyService` | Both |
| **Game Logging** | `GameLogRepository` | Both |
| **Database** | SQLite via Spring JDBC | Both |
| **WebSocket Config** | `WebSocketConfig` | Both |

---

## Key Implementation Details

### Architecture A: Event-Driven Flow

```
1. Client sends PLAYER_INPUT
   ↓
2. GameWebSocketHandler receives input
   ↓
3. Publishes InputReceivedEvent to EventBus
   ↓
4. GameTickScheduler (scheduled task) processes events
   ↓
5. Calls PhysicsEngine to update game state
   ↓
6. PhysicsEngine detects collisions
   ↓
7. Publishes CollisionDetectedEvent
   ↓
8. GameWorld updates scores, HP, etc.
   ↓
9. GameTickScheduler broadcasts GAME_STATE
   ↓
10. Clients receive and render state
```

### Architecture B: Gossip Protocol Flow

```
1. Client game loop (60 FPS) runs locally
   ↓
2. Client calculates physics locally
   ↓
3. Client detects collisions locally
   ↓
4. Client updates local state
   ↓
5. Client broadcasts state (20 Hz)
   ↓
6. GameWebSocketHandlerB receives message
   ↓
7. Relays message to all room peers
   ↓
8. Other clients receive and merge state
   ↓
9. Clients render merged state
```

### Message Frequency Comparison

| Architecture | Input Rate | State Update Rate | Network Messages |
|--------------|-----------|-------------------|------------------|
| **Architecture A** | 20 Hz (client→server) | 25 Hz (server→clients) | O(n) per update |
| **Architecture B** | 60 Hz (local) | 20 Hz (client→server→clients) | O(n²) per update |

---

## Testing Both Architectures

### Testing Architecture A

1. Start the application
2. Login and create/join a room
3. Click "Start (Arch A)" button
4. Access: http://localhost:8080/game.html?roomId=1&arch=A
5. Verify:
   - Client only sends input
   - Server broadcasts game state
   - All clients see synchronized state

### Testing Architecture B

1. Start the application
2. Login and create/join a room
3. Click "Start (Arch B)" button
4. Access: http://localhost:8080/game.html?roomId=1&arch=B
5. Verify:
   - Client performs local calculations
   - Client broadcasts state to peers
   - State is merged from all peers

---

## Performance Characteristics

### Architecture A

- **Server CPU**: High (processes all game logic)
- **Server Memory**: Medium (maintains game state)
- **Network Bandwidth**: Low (O(n) messages)
- **Client CPU**: Low (rendering only)
- **Client Memory**: Low (state from server)
- **Latency**: One network round-trip

### Architecture B

- **Server CPU**: Very Low (message relay only)
- **Server Memory**: Low (connection management only)
- **Network Bandwidth**: High (O(n²) messages)
- **Client CPU**: High (full game logic)
- **Client Memory**: Medium (local + merged state)
- **Latency**: Zero (local calculations)

---

## Conclusion

This project demonstrates two fundamentally different architectural approaches to multiplayer game development:

- **Architecture A (Layered)**: Centralized, server-authoritative, event-driven
- **Architecture B (P2P)**: Decentralized, client-authoritative, gossip-based

Both architectures are fully implemented and demonstrate the trade-offs between consistency, security, latency, and scalability in distributed systems.

---

## Contact and Support

For questions or issues, please contact the development team.

**Last Updated**: December 9, 2025

