// lobby.js

const TABLE_COUNT = 20;

let lobbyAutoRefreshTimer = null;
let lobbyAutoRefreshing = false;
let leaderboardAutoRefreshTimer = null; // 🔥 排行榜定时器
let leaderboardData = []; // 🔥 存储排行榜数据
let currentUser = null;
let currentRoomId = null; // 当前用户所在的房间 id（如果有）
let allowAutoEnterGame = true; // 🔥 是否允许自动进入游戏（防止无限跳转）
let lastGameSessionId = -1; // 🔥 记录上一次的游戏局数ID，用于检测"新游戏开始"

function startAutoRefreshLobby() {
    if (lobbyAutoRefreshTimer !== null) return;

    lobbyAutoRefreshTimer = setInterval(async () => {
        if (lobbyAutoRefreshing) return;
        lobbyAutoRefreshing = true;
        try {
            await fetchLobby();
        } catch (e) {
            console.error('auto refresh lobby error', e);
        } finally {
            lobbyAutoRefreshing = false;
        }
    }, 500); // 每 500ms 刷新一次
}

// 🔥 启动排行榜自动刷新（每30秒）
function startAutoRefreshLeaderboard() {
    if (leaderboardAutoRefreshTimer !== null) return;

    // 立即调用一次
    fetchLeaderboard();

    leaderboardAutoRefreshTimer = setInterval(async () => {
        try {
            console.log('[LEADERBOARD] Auto-refreshing...');
            await fetchLeaderboard();
        } catch (e) {
            console.error('[LEADERBOARD] Auto refresh error', e);
        }
    }, 30000); // 每 30 秒刷新一次
}

// 🔥 获取排行榜数据并存储
async function fetchLeaderboard() {
    try {
        console.log('[LEADERBOARD] Fetching data from /api/lobby/leaderboard...');
        const resp = await authFetch('/api/lobby/leaderboard');
        
        if (!resp.ok) {
            console.error('[LEADERBOARD] API error:', resp.status);
            return;
        }
        
        const data = await resp.json();
        
        // 🔥 存储到全局变量
        leaderboardData = data;
        
        console.log('[LEADERBOARD] Data received and stored:', leaderboardData);
        console.log('[LEADERBOARD] Total entries:', leaderboardData.length);
        
        // 打印详细数据
        if (leaderboardData.length > 0) {
            console.table(leaderboardData);
        }
        
    } catch (e) {
        console.error('[LEADERBOARD] Fetch error:', e);
    }
}

// 🔥 渲染排行榜
function renderLeaderboard() {
    const leaderboardContent = document.getElementById('leaderboardContent');
    
    if (!leaderboardContent) {
        console.error('[LEADERBOARD] Content element not found');
        return;
    }
    
    if (!leaderboardData || leaderboardData.length === 0) {
        leaderboardContent.innerHTML = '<div class="lb-loading">No game data</div>';
        return;
    }
    
    // 计算最高分，用于进度条
    const maxScore = Math.max(...leaderboardData.map(e => e.totalScore));
    
    // 渲染表头
    leaderboardContent.innerHTML = `
        <div class="lb-table-header">
            <div>排名</div>
            <div>名称</div>
            <div>分数</div>
        </div>
    `;
    
    // 渲染排行榜条目
    leaderboardData.forEach((entry, index) => {
        const rank = index + 1;
        const entryDiv = document.createElement('div');
        
        // 添加条目类
        let entryClass = 'lb-entry';
        if (rank === 1) entryClass += ' top1';
        else if (rank === 2) entryClass += ' top2';
        else if (rank === 3) entryClass += ' top3';
        entryDiv.className = entryClass;
        
        // 排名类
        let rankClass = 'lb-rank';
        if (rank === 1) rankClass += ' top1';
        else if (rank === 2) rankClass += ' top2';
        else if (rank === 3) rankClass += ' top3';
        
        // 进度条类
        let progressClass = 'lb-progress-fill';
        if (rank === 1) progressClass += ' top1';
        else if (rank === 2) progressClass += ' top2';
        else if (rank === 3) progressClass += ' top3';
        
        // 计算进度条宽度
        const progressWidth = maxScore > 0 ? Math.min(100, (entry.totalScore / maxScore) * 100) : 0;
        
        entryDiv.innerHTML = `
            <div class="${rankClass}">${rank}</div>
            <div class="lb-info">
                <div class="lb-username">${entry.username}</div>
                <div class="lb-stats">
                    <div class="lb-games">${entry.gamesPlayed}局</div>
                </div>
            </div>
            <div class="lb-score-container">
                <div class="lb-score">${entry.totalScore}</div>
                <div class="lb-progress-bar">
                    <div class="${progressClass}" style="width: ${progressWidth}%"></div>
                </div>
            </div>
        `;
        
        leaderboardContent.appendChild(entryDiv);
    });
    
    console.log('[LEADERBOARD] Rendered:', leaderboardData.length, 'entries');
}

document.addEventListener('DOMContentLoaded', async () => {
    // 🔥 检查URL参数：区分错误返回和正常退出
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('fromGameError')) {
        // 错误返回：禁用自动跳转
        allowAutoEnterGame = false;
        console.log('[LOBBY] Disabled auto-enter game (error from game)');
        window.history.replaceState({}, document.title, '/lobby.html');
    } else if (urlParams.has('fromGameExit')) {
        // 正常退出：重置为允许自动跳转
        allowAutoEnterGame = true;
        console.log('[LOBBY] Reset allowAutoEnterGame=true (game ended normally)');
        window.history.replaceState({}, document.title, '/lobby.html');
    }

    // 先检查登录状态
    try {
        const user = await validateToken(); // 来自 session.js
        if (!user) {
            window.location.href = '/login.html';
            return;
        }
        currentUser = user.username;
    } catch (e) {
        window.location.href = '/login.html';
        return;
    }

    const btnLeaderboard = document.getElementById('btnLeaderboard');
    const btnRefresh = document.getElementById('btnRefresh');
    const btnCreateRoom = document.getElementById('btnCreateRoom');
    const btnCancelCreate = document.getElementById('btnCancelCreate');
    const btnConfirmCreate = document.getElementById('btnConfirmCreate');
    const btnCancelLeaderboard = document.getElementById('btnCancelLeaderboard');

    const createPanel = document.getElementById('createPanel');
    const createMsg = document.getElementById('createMessage');
    const leaderboardPanel = document.getElementById('leaderboardPanel');

    // 初始化 20 个空桌子
    renderEmptyTables();

    // 首次拉取大厅
    await fetchLobby();
    // 启动自动刷新
    startAutoRefreshLobby();
    
    // 🔥 启动排行榜自动刷新（立即调用一次，然后每30秒刷新）
    startAutoRefreshLeaderboard();

    // 🏆 排行榜按钮
    btnLeaderboard.addEventListener('click', () => {
        // 如果创建房间面板显示，先触发取消
        if (!createPanel.classList.contains('hidden')) {
            resetCreateOptions();
            createMsg.textContent = '';
            createMsg.style.color = '#ffffff';
            createPanel.classList.add('hidden');
        }
        
        // 显示排行榜
        leaderboardPanel.classList.remove('hidden');
        // 刷新排行榜数据
        fetchLeaderboard();
        // 渲染排行榜
        renderLeaderboard();
    });

    // 取消排行榜按钮
    btnCancelLeaderboard.addEventListener('click', () => {
        leaderboardPanel.classList.add('hidden');
    });

    // 顶部按钮
    btnRefresh.addEventListener('click', async () => {
        await fetchLobby();
        // 🔥 手动刷新时也获取排行榜数据
        await fetchLeaderboard();
        // 如果排行榜显示，刷新渲染
        if (!leaderboardPanel.classList.contains('hidden')) {
            renderLeaderboard();
        }
    });

    btnCreateRoom.addEventListener('click', () => {
        // 如果排行榜显示，先隐藏
        if (!leaderboardPanel.classList.contains('hidden')) {
            leaderboardPanel.classList.add('hidden');
        }
        
        createMsg.textContent = '';
        createMsg.style.color = '#ffffff';
        createPanel.classList.remove('hidden');
    });

    btnCancelCreate.addEventListener('click', () => {
        resetCreateOptions();
        createMsg.textContent = '';
        createMsg.style.color = '#ffffff';
        createPanel.classList.add('hidden');
        // 不管排行榜状态
    });

    btnConfirmCreate.addEventListener('click', async () => {
        await onCreateRoom();
    });

    // 选项 chips
    setupChipGroup('optPlayers');
    setupChipGroup('optMap');
    setupChipGroup('optWin');
});

// 画 20 个“空桌子”的卡片
function renderEmptyTables() {
    const container = document.getElementById('tablesContainer');
    container.innerHTML = '';
    for (let i = 0; i < TABLE_COUNT; i++) {
        const card = document.createElement('div');
        card.className = 'table-card';
        card.dataset.index = String(i);

        card.innerHTML = `
          <div class="table-header">
              <div class="table-title">Room #${i}</div>
              <div class="table-status" id="table-status-${i}">Empty Room</div>
          </div>
          <div class="table-body" id="table-body-${i}">
              <div class="table-empty">No rooms available</div>
          </div>
        `;
        container.appendChild(card);
    }
}

// 向后端请求大厅数据
async function fetchLobby() {
    try {
        const resp = await authFetch('/api/lobby');
        if (!resp.ok) {
            console.error('fetch lobby failed:', await resp.text());
            return;
        }
        const slots = await resp.json();
        applyLobbySlots(slots);
    } catch (e) {
        console.error('fetchLobby error', e);
    }
}

// 把 lobby 数据映射到 20 个桌子卡片上
function applyLobbySlots(slots) {
    currentRoomId = null;

    // 先清空所有桌子为“空桌”
    for (let i = 0; i < TABLE_COUNT; i++) {
        const statusEl = document.getElementById(`table-status-${i}`);
        const bodyEl = document.getElementById(`table-body-${i}`);
        if (!statusEl || !bodyEl) continue;
        statusEl.textContent = 'Empty';
        bodyEl.innerHTML = `<div class="table-empty">Empty room</div>`;
    }

    if (!Array.isArray(slots)) return;

    let shouldEnterGame = false;
    let enterRoomId = null;
    let enterArchitecture = 'A'; // 🔥 记录架构模式
    let enterWinMode = 'SCORE_50'; // 🔥 记录胜利条件

    for (const slot of slots) {
        const idx = slot.index;
        if (idx < 0 || idx >= TABLE_COUNT) continue;
        const statusEl = document.getElementById(`table-status-${idx}`);
        const bodyEl = document.getElementById(`table-body-${idx}`);
        if (!statusEl || !bodyEl) continue;

        if (!slot.occupied || !slot.room) continue;
        const room = slot.room;

        // --- 新结构：players 是 [{username, owner, ready}, ...] ---
        const players = Array.isArray(room.players) ? room.players : [];

        // 当前用户在不在这个房间里
        const currentPlayer = players.find(p => p.username === currentUser);
        const isInRoom = !!currentPlayer;
        const isOwner = currentPlayer ? !!currentPlayer.owner : false;
        const isReady = currentPlayer ? !!currentPlayer.ready : false;

        // 记录当前房间 ID，用于控制"只能加入一个房间"
        if (isInRoom) {
            currentRoomId = room.roomId;
            
            // 🔥 检测游戏局数ID变化：只要gameSessionId递增，就说明新游戏开始
            const currentSessionId = room.gameSessionId || 0;
            if (currentSessionId > lastGameSessionId) {
                console.log('[LOBBY] Detected new game (session', lastGameSessionId, '→', currentSessionId, '), reset allowAutoEnterGame=true');
                allowAutoEnterGame = true;  // 🔥 Reset to initial state
            }
            lastGameSessionId = currentSessionId;
            
            if (room.started) {
                shouldEnterGame = true;
                enterRoomId = room.roomId;
                // 🔥 记录架构模式和胜利条件
                enterArchitecture = room.architecture || 'A';
                enterWinMode = room.winMode || 'SCORE_50';
            }
        }

        // Room title
        statusEl.textContent = `Room #${room.roomId}${room.started ? ' (Started)' : ''}`;

        // Members list text
        let membersText = '';
        if (players.length > 0) {
            // Non-owner players are members
            const others = players.filter(p => !p.owner);
            if (others.length > 0) {
                membersText = others.map(p =>
                    `${p.username} (${p.ready ? 'Ready' : 'Not Ready'})`
                ).join(', ');
            } else {
                membersText = '(No members)';
            }
        }

        const winText = (() => {
            switch (room.winMode) {
                case 'SCORE_50': return 'Score 50';
                case 'SCORE_100': return 'Score 100';
                case 'TIME_1M': return 'Time 1m';
                case 'TIME_5M': return 'Time 5m';
                default: return room.winMode;
            }
        })();

        // Fill table info + button area
        bodyEl.innerHTML = '';
        const info = document.createElement('div');
        info.innerHTML = `
            <div>Host: ${room.ownerName}</div>
            <div>Members: ${membersText}</div>
            <div>Players: ${room.currentPlayers} / ${room.maxPlayers}</div>
            <div>Map: ${room.mapName}</div>
            <div>Win Condition: ${winText}</div>
        `;
        bodyEl.appendChild(info);

        const btnBox = document.createElement('div');
        btnBox.className = 'table-actions';

        if (isInRoom) {
            // The current user is in this room.
            if (!room.started) {
                if (isOwner) {
                    // Owner: Three start buttons + Exit
                    const btnStartA = document.createElement('button');
                    btnStartA.textContent = 'Start (Arch A)';
                    btnStartA.className = 'btn-primary';
                    btnStartA.title = 'Architecture A: Server-Authoritative + Event-Driven';
                    btnStartA.onclick = () => startGameArchitectureA(room.roomId, room.winMode);

                    const btnStartB = document.createElement('button');
                    btnStartB.textContent = 'Start (Arch B)';
                    btnStartB.className = 'btn-secondary';
                    btnStartB.title = 'Architecture B: P2P Gossip';
                    btnStartB.onclick = () => startGameArchitectureB(room.roomId, room.winMode);

                    const btnLocal = document.createElement('button');
                    btnLocal.textContent = 'local game';
                    btnLocal.className = 'btn-local';
                    btnLocal.title = 'Local Game: Offline Single Player';
                    btnLocal.onclick = () => startLocalGame(room.winMode);

                    const btnLeave = document.createElement('button');
                    btnLeave.textContent = 'quit';
                    btnLeave.className = 'btn-danger';
                    btnLeave.onclick = () => leaveRoom(room.roomId);

                    btnBox.appendChild(btnStartA);
                    btnBox.appendChild(btnStartB);
                    btnBox.appendChild(btnLocal);
                    btnBox.appendChild(btnLeave);
                } else {
                    // Members: Ready/Unready + Leave
                    const btnReady = document.createElement('button');
                    btnReady.textContent = isReady ? 'Unready' : 'Ready';
                    btnReady.className = isReady ? 'btn-secondary' : 'btn-primary';
                    btnReady.onclick = () => toggleReady(room.roomId);

                    const btnLeave = document.createElement('button');
                    btnLeave.textContent = 'Leave';
                    btnLeave.className = 'btn-danger';
                    btnLeave.onclick = () => leaveRoom(room.roomId);

                    btnBox.appendChild(btnReady);
                    btnBox.appendChild(btnLeave);
                }
            } else {
                // Game started: Enter Game + Leave
                const btnEnter = document.createElement('button');
                btnEnter.textContent = 'Enter Game';
                btnEnter.className = 'btn-primary';
                // 🔥 When manually clicking "Enter Game", re-enable auto-jump and use correct architecture
                btnEnter.onclick = () => {
                    allowAutoEnterGame = true;
                    const arch = room.architecture || 'A';
                    enterGame(room.roomId, room.winMode, arch);
                };

                const btnLeave = document.createElement('button');
                btnLeave.textContent = 'Leave';
                btnLeave.className = 'btn-danger';
                btnLeave.onclick = () => leaveRoom(room.roomId);

                btnBox.appendChild(btnEnter);
                btnBox.appendChild(btnLeave);
            }
        } else {
            // Current user not in this room
            const btnJoin = document.createElement('button');
            btnJoin.textContent = 'Join';
            btnJoin.className = 'btn-primary';
            btnJoin.disabled =
                room.started ||
                room.currentPlayers >= room.maxPlayers ||
                (currentRoomId !== null && currentRoomId !== room.roomId);

            btnJoin.onclick = () => joinRoom(room.roomId);
            btnBox.appendChild(btnJoin);
        }

        bodyEl.appendChild(btnBox);
    }

    // 🔥 Only auto-enter when allowed (prevent infinite loop after error return)
    if (shouldEnterGame && enterRoomId !== null && allowAutoEnterGame) {
        console.log('[LOBBY] Auto-enter game roomId:', enterRoomId, 'arch:', enterArchitecture, 'winMode:', enterWinMode);
        enterGame(enterRoomId, enterWinMode, enterArchitecture);
    }
}

// --------- Create Room ---------

async function onCreateRoom() {
    const createMsg = document.getElementById('createMessage');
    createMsg.textContent = '';
    createMsg.style.color = '#ffffff';

    const maxPlayers = parseInt(getSelectedValue('optPlayers', '2'), 10);
    const mapName = getSelectedValue('optMap', 'Nebula-01');
    const winMode = getSelectedValue('optWin', 'SCORE_50');

    const body = { maxPlayers, mapName, winMode };

    try {
        const resp = await authFetch('/api/lobby/rooms', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });

        if (!resp.ok) {
            const text = await resp.text();
            createMsg.style.color = '#ff6b6b';
            createMsg.textContent = text || 'Failed to create room';
            return;
        }

        createMsg.style.color = '#8df59d';
        createMsg.textContent = 'Created successfully!';
        await fetchLobby();
    } catch (e) {
        console.error('create room error', e);
        createMsg.style.color = '#ff6b6b';
        createMsg.textContent = 'Network error, failed to create';
    }
}

// --------- 按钮动作（加入 / 退出 / 准备 / 开始 / 进入游戏） ---------

async function joinRoom(roomId) {
    try {
        const resp = await authFetch(`/api/lobby/rooms/${roomId}/join`, {
            method: 'POST'
        });
        if (!resp.ok) {
            console.error('join room failed', await resp.text());
        } else {
            await fetchLobby();
        }
    } catch (e) {
        console.error('joinRoom error', e);
    }
}

async function leaveRoom(roomId) {
    try {
        const resp = await authFetch(`/api/lobby/rooms/${roomId}/leave`, {
            method: 'POST'
        });
        if (!resp.ok) {
            console.error('leave room failed', await resp.text());
        } else {
            await fetchLobby();
        }
    } catch (e) {
        console.error('leaveRoom error', e);
    }
}

async function toggleReady(roomId) {
    try {
        const resp = await authFetch(`/api/lobby/rooms/${roomId}/toggle-ready`, {
            method: 'POST'
        });
        if (!resp.ok) {
            console.error('toggle ready failed', await resp.text());
        } else {
            await fetchLobby();
        }
    } catch (e) {
        console.error('toggleReady error', e);
    }
}

// Architecture A: 服务器权威 + 事件驱动
async function startGameArchitectureA(roomId, winMode) {
    try {
        const resp = await authFetch(`/api/lobby/rooms/${roomId}/start-architecture-a`, {
            method: 'POST'
        });
        if (!resp.ok) {
            console.error('start game (Arch A) failed', await resp.text());
            alert('Cannot start game (Architecture A)');
            // 🔥 Start failed, disable auto-jump
            allowAutoEnterGame = false;
        } else {
            // 🔥 Start succeeded, allow auto-jump
            allowAutoEnterGame = true;
            console.log('[LOBBY] Game started successfully, jumping to game.html');
            // Host immediately jumps to Architecture A game
            enterGame(roomId, winMode, 'A');
        }
    } catch (e) {
        console.error('startGameArchitectureA error', e);
        alert('Network error, cannot start game');
        allowAutoEnterGame = false;
    }
}

// Architecture B: P2P Lockstep（未实现）
async function startGameArchitectureB(roomId, winMode) {
    try {
        const resp = await authFetch(`/api/lobby/rooms/${roomId}/start-architecture-b`, {
            method: 'POST'
        });
        if (!resp.ok) {
            const text = await resp.text();
            alert('Architecture B not implemented: ' + text);
            // 🔥 Start failed, disable auto-jump
            allowAutoEnterGame = false;
        } else {
            // 🔥 Start succeeded, allow auto-jump
            allowAutoEnterGame = true;
            console.log('[LOBBY] Game started successfully (Arch B), jumping to game.html');
            enterGame(roomId, winMode, 'B');
        }
    } catch (e) {
        console.error('startGameArchitectureB error', e);
        alert('Network error, cannot start game');
        allowAutoEnterGame = false;
    }
}

function enterGame(roomId, winMode, architecture = 'A') {
    // 跳转到游戏页面，传递架构类型
    window.location.href = `/game.html?roomId=${roomId}&win=${winMode}&arch=${architecture}`;
}

// 本地游戏（单人离线模式）
function startLocalGame(winMode) {
    console.log('[LOBBY] Starting local game, winMode:', winMode);
    window.location.href = `/game-local.html?win=${winMode}`;
}

// --------- 选项 chips 工具函数 ---------

function setupChipGroup(containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;
    container.addEventListener('click', (e) => {
        const target = e.target;
        if (!(target instanceof HTMLElement)) return;
        if (!target.classList.contains('chip')) return;
        for (const child of container.querySelectorAll('.chip')) {
            child.classList.remove('selected');
        }
        target.classList.add('selected');
    });
}

function getSelectedValue(containerId, defaultValue) {
    const container = document.getElementById(containerId);
    if (!container) return defaultValue;
    const selected = container.querySelector('.chip.selected');
    if (!selected) return defaultValue;
    return selected.getAttribute('data-value') || defaultValue;
}

function resetCreateOptions() {
    ['optPlayers', 'optMap', 'optWin'].forEach(id => {
        const container = document.getElementById(id);
        if (!container) return;
        const chips = container.querySelectorAll('.chip');
        chips.forEach((chip, i) => {
            chip.classList.toggle('selected', i === 0);
        });
    });
}


function renderRoomActions(room, currentUsername, container) {
    const isInRoom = room.players.some(p => p.username === currentUsername);
    const isOwner = room.ownerName === currentUsername;

    const actionsDiv = document.createElement('div');
    actionsDiv.className = 'table-actions';

    if (isOwner) {
        const btnStartA = document.createElement('button');
        btnStartA.textContent = 'Start(A)';
        btnStartA.className = 'btn btn-primary';
        btnStartA.onclick = () => startGame(room.roomId, 'ARCH_A');

        const btnStartB = document.createElement('button');
        btnStartB.textContent = 'Start(B)';
        btnStartB.className = 'btn btn-secondary';
        btnStartB.onclick = () => startGame(room.roomId, 'ARCH_B');

        const btnLeave = document.createElement('button');
        btnLeave.textContent = 'Leave';
        btnLeave.onclick = () => leaveRoom(room.roomId);

        actionsDiv.appendChild(btnStartA);
        actionsDiv.appendChild(btnStartB);
        actionsDiv.appendChild(btnLeave);
    } else if (isInRoom) {
        // Members: Ready / Leave
        const btnReady = document.createElement('button');
        btnReady.textContent = room.isReady ? 'Unready' : 'Ready';
        btnReady.onclick = () => toggleReady(room.roomId);

        const btnLeave = document.createElement('button');
        btnLeave.textContent = 'Leave';
        btnLeave.onclick = () => leaveRoom(room.roomId);

        actionsDiv.appendChild(btnReady);
        actionsDiv.appendChild(btnLeave);
    } else {
        const btnJoin = document.createElement('button');
        btnJoin.textContent = 'Join';
        btnJoin.onclick = () => joinRoom(room.roomId);
        actionsDiv.appendChild(btnJoin);
    }

    container.appendChild(actionsDiv);
}

async function startGame(roomId, mode) {
    const resp = await authFetch(`/api/lobby/rooms/${roomId}/start?mode=${mode}`, {
        method: 'POST'
    });
    if (!resp.ok) {
        const txt = await resp.text();
        alert(txt || 'Failed to start');
    } else {
        // 开始成功后，由 lobby 的 500ms 轮询检测到 room.started=true 后自动跳转 game.html
        await fetchLobbyOnce();
    }
}

