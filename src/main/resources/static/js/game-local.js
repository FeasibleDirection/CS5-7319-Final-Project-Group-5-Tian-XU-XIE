// game-local.js
// 本地单人游戏 - 完全离线，无需服务器

const CANVAS_WIDTH = 480;
const CANVAS_HEIGHT = 640;
const FPS = 60;

// 游戏配置
let winMode = 'SCORE_50';
let username = 'LocalPlayer';

// 游戏状态
let gameState = {
    phase: 'IN_PROGRESS', // COUNTDOWN, IN_PROGRESS, FINISHED
    countdownSeconds: 3
};

// 玩家状态
let player = {
    x: CANVAS_WIDTH / 2,
    y: CANVAS_HEIGHT - 80,
    hp: 3,
    maxHp: 3,
    score: 0,
    alive: true,
    lastFireTime: 0
};

// 游戏实体
let asteroids = {}; // id -> {x, y, velocityY, radius, hp, isBig}
let bullets = {}; // id -> {x, y, velocityY}

// 输入状态
let keys = {
    w: false,
    a: false,
    s: false,
    d: false,
    j: false,
    ' ': false
};

// 计数器
let asteroidIdCounter = 0;
let bulletIdCounter = 0;
let lastAsteroidSpawnTime = 0;

// 游戏时间
let gameStartTime = 0;
let gameElapsedSeconds = 0;

// 常量
const PLAYER_SPEED = 200; // pixels/second
const BULLET_SPEED = 400;
const ASTEROID_MIN_SPEED = 80;
const ASTEROID_MAX_SPEED = 160;
const ASTEROID_SPAWN_INTERVAL = 800; // ms
const PLAYER_RADIUS = 16;
const BULLET_RADIUS = 4;
const MIN_FIRE_INTERVAL = 200; // ms

// Canvas
let canvas, ctx;

// 特效
let explosionEffects = [];
let hitFlashEndTime = 0;
let lastHp = null;

// ============ 初始化 ============
(function init() {
    console.log('[LOCAL] 本地游戏初始化');

    const params = new URLSearchParams(window.location.search);
    winMode = params.get('win') || 'SCORE_50';

    username = localStorage.getItem('game_demo_username') || 'LocalPlayer';

    console.log('[LOCAL] winMode:', winMode, 'username:', username);

    const lblUser = document.getElementById('lblUser');
    const lblArch = document.getElementById('lblArchitecture');

    if (lblUser) lblUser.textContent = username;
    if (lblArch) lblArch.textContent = '[本地单人游戏 - 离线模式]';

    canvas = document.getElementById('gameCanvas');
    if (!canvas) {
        console.error('[LOCAL] canvas not found');
        return;
    }
    ctx = canvas.getContext('2d');

    setupInput();
    setupButtons();

    // 开始倒计时
    startCountdown();
})();

function startCountdown() {
    gameState.phase = 'COUNTDOWN';
    gameState.countdownSeconds = 3;

    const countdownInterval = setInterval(() => {
        gameState.countdownSeconds--;
        
        if (gameState.countdownSeconds <= 0) {
            clearInterval(countdownInterval);
            startGame();
        }
    }, 1000);

    // 渲染循环
    setInterval(gameLoop, 1000 / FPS);
}

function startGame() {
    console.log('[LOCAL] 游戏开始!');
    gameState.phase = 'IN_PROGRESS';
    gameStartTime = Date.now();
    lastAsteroidSpawnTime = performance.now();
}

// ============ 游戏主循环 ============
function gameLoop() {
    const deltaSeconds = 1 / FPS;
    const now = performance.now();

    if (gameState.phase === 'IN_PROGRESS') {
        // 更新玩家
        updatePlayer(deltaSeconds);

        // 生成石头
        spawnAsteroids(now);

        // 更新石头
        updateAsteroids(deltaSeconds);

        // 更新子弹
        updateBullets(deltaSeconds);

        // 碰撞检测
        detectCollisions();

        // 检查游戏结束
        checkGameEnd();

        // 更新游戏时间
        gameElapsedSeconds = Math.floor((Date.now() - gameStartTime) / 1000);
    }

    // 渲染
    render();

    // 更新UI
    updateUI();
}

// ============ 玩家更新 ============
function updatePlayer(deltaSeconds) {
    if (!player.alive) return;

    let vx = 0, vy = 0;

    if (keys.w) vy -= 1;
    if (keys.s) vy += 1;
    if (keys.a) vx -= 1;
    if (keys.d) vx += 1;

    // 归一化
    const mag = Math.hypot(vx, vy);
    if (mag > 0) {
        vx = (vx / mag) * PLAYER_SPEED;
        vy = (vy / mag) * PLAYER_SPEED;
    }

    player.x += vx * deltaSeconds;
    player.y += vy * deltaSeconds;

    // 边界限制
    player.x = Math.max(PLAYER_RADIUS, Math.min(CANVAS_WIDTH - PLAYER_RADIUS, player.x));
    player.y = Math.max(PLAYER_RADIUS, Math.min(CANVAS_HEIGHT - PLAYER_RADIUS, player.y));

    // 射击
    const now = performance.now();
    if ((keys.j || keys[' ']) && now - player.lastFireTime >= MIN_FIRE_INTERVAL) {
        fireBullet();
        player.lastFireTime = now;
    }
}

function fireBullet() {
    const bulletId = `bullet_${bulletIdCounter++}`;
    
    bullets[bulletId] = {
        x: player.x,
        y: player.y - 20,
        velocityY: -BULLET_SPEED
    };
}

// ============ 石头管理 ============
function spawnAsteroids(now) {
    if (now - lastAsteroidSpawnTime < ASTEROID_SPAWN_INTERVAL) return;
    if (Object.keys(asteroids).length >= 10) return; // 最多10个石头

    lastAsteroidSpawnTime = now;

    const asteroidId = `asteroid_${asteroidIdCounter++}`;
    const x = 30 + Math.random() * (CANVAS_WIDTH - 60);
    const isBig = Math.random() < 0.3;
    const radius = isBig ? 26 : 16;
    const hp = isBig ? 2 : 1;
    const velocityY = ASTEROID_MIN_SPEED + Math.random() * (ASTEROID_MAX_SPEED - ASTEROID_MIN_SPEED);

    asteroids[asteroidId] = {
        x,
        y: -radius,
        velocityY,
        radius,
        hp,
        isBig
    };
}

function updateAsteroids(deltaSeconds) {
    Object.keys(asteroids).forEach(asteroidId => {
        const asteroid = asteroids[asteroidId];
        asteroid.y += asteroid.velocityY * deltaSeconds;

        // 移除飞出屏幕的石头
        if (asteroid.y - asteroid.radius > CANVAS_HEIGHT + 40) {
            delete asteroids[asteroidId];
        }
    });
}

function updateBullets(deltaSeconds) {
    Object.keys(bullets).forEach(bulletId => {
        const bullet = bullets[bulletId];
        bullet.y += bullet.velocityY * deltaSeconds;

        // 移除飞出屏幕的子弹
        if (bullet.y < -10) {
            delete bullets[bulletId];
        }
    });
}

// ============ 碰撞检测 ============
function detectCollisions() {
    // 子弹 vs 石头
    Object.keys(bullets).forEach(bulletId => {
        const bullet = bullets[bulletId];
        
        Object.keys(asteroids).forEach(asteroidId => {
            const asteroid = asteroids[asteroidId];
            if (!asteroid) return;

            const dx = bullet.x - asteroid.x;
            const dy = bullet.y - asteroid.y;
            const dist = Math.hypot(dx, dy);

            if (dist < BULLET_RADIUS + asteroid.radius) {
                // 命中！
                asteroid.hp -= 1;

                if (asteroid.hp <= 0) {
                    // 石头被打爆
                    const scoreGain = asteroid.isBig ? 10 : 5;
                    player.score += scoreGain;

                    // 爆炸特效
                    explosionEffects.push({
                        x: asteroid.x,
                        y: asteroid.y,
                        endTime: performance.now() + 300
                    });

                    delete asteroids[asteroidId];
                }

                // 删除子弹
                delete bullets[bulletId];
            }
        });
    });

    // 石头 vs 玩家
    if (player.alive) {
        Object.keys(asteroids).forEach(asteroidId => {
            const asteroid = asteroids[asteroidId];
            if (!asteroid) return;

            const dx = player.x - asteroid.x;
            const dy = player.y - asteroid.y;
            const dist = Math.hypot(dx, dy);

            if (dist < PLAYER_RADIUS + asteroid.radius) {
                // 被撞！
                player.hp -= 1;

                // 删除石头
                delete asteroids[asteroidId];

                // 检查是否死亡
                if (player.hp <= 0) {
                    player.alive = false;
                    player.hp = 0;
                    gameState.phase = 'FINISHED';
                }
            }
        });
    }
}

// ============ 游戏结束检测 ============
function checkGameEnd() {
    if (gameState.phase !== 'IN_PROGRESS') return;

    // 条件1：玩家死亡
    if (!player.alive) {
        endGame('PLAYER_DEAD');
        return;
    }

    // 条件2：达到目标分数
    if (winMode && winMode.startsWith('SCORE_')) {
        const targetScore = parseInt(winMode.substring(6));
        if (player.score >= targetScore) {
            endGame('SCORE_TARGET_REACHED');
            return;
        }
    }

    // 条件3：超过时间限制
    if (winMode && winMode.startsWith('TIME_')) {
        const timeStr = winMode.substring(5);
        const minutes = parseInt(timeStr.substring(0, timeStr.length - 1));
        const limitSeconds = minutes * 60;
        
        if (gameElapsedSeconds >= limitSeconds) {
            endGame('TIME_LIMIT_REACHED');
            return;
        }
    }
}

function endGame(reason) {
    console.log('[LOCAL] 游戏结束, reason:', reason);
    gameState.phase = 'FINISHED';

    setTimeout(() => {
        const overlay = document.getElementById('overlay');
        const overTitle = document.getElementById('overTitle');
        const overSummary = document.getElementById('overSummary');

        if (overlay) overlay.classList.remove('hidden');
        if (overTitle) {
            if (reason === 'PLAYER_DEAD') {
                overTitle.textContent = '游戏结束';
            } else {
                overTitle.textContent = '恭喜完成!';
            }
        }
        if (overSummary) {
            overSummary.textContent = `得分: ${player.score} | 时间: ${formatTime(gameElapsedSeconds)}`;
        }
    }, 1000);
}

// ============ 渲染 ============
function render() {
    if (!ctx) return;

    // 清空画布
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

    if (gameState.phase === 'COUNTDOWN') {
        // 倒计时画面
        ctx.fillStyle = '#fff';
        ctx.font = '72px Arial';
        ctx.textAlign = 'center';
        ctx.fillText(gameState.countdownSeconds, CANVAS_WIDTH / 2, CANVAS_HEIGHT / 2);
        ctx.font = '24px Arial';
        ctx.fillText('准备开始!', CANVAS_WIDTH / 2, CANVAS_HEIGHT / 2 + 50);
        return;
    }

    const now = performance.now();

    // 渲染玩家
    if (player.alive) {
        // 被撞后闪烁
        const shouldFlash = now < hitFlashEndTime && Math.floor(now / 100) % 2 === 0;
        
        if (!shouldFlash) {
            ctx.fillStyle = '#ff4d4f';
            ctx.fillRect(player.x - 16, player.y - 16, 32, 32);

            // 名字
            ctx.fillStyle = '#fff';
            ctx.font = '12px Arial';
            ctx.textAlign = 'center';
            ctx.fillText(`✈ ${username}`, player.x, player.y - 25);

            // HP条
            const barWidth = 32;
            const barHeight = 4;
            ctx.fillStyle = '#f00';
            ctx.fillRect(player.x - barWidth / 2, player.y - 20, barWidth, barHeight);
            ctx.fillStyle = '#0f0';
            const hpRatio = player.hp / player.maxHp;
            ctx.fillRect(player.x - barWidth / 2, player.y - 20, barWidth * hpRatio, barHeight);
        }
    }

    // 渲染爆炸特效
    explosionEffects = explosionEffects.filter(e => e.endTime > now);
    explosionEffects.forEach(e => {
        const t = 1 - (e.endTime - now) / 300;
        const r = 10 + 20 * t;
        ctx.save();
        ctx.globalAlpha = 1 - t;
        ctx.strokeStyle = '#ff0';
        ctx.lineWidth = 3;
        ctx.beginPath();
        ctx.arc(e.x, e.y, r, 0, Math.PI * 2);
        ctx.stroke();
        ctx.restore();
    });

    // 渲染子弹
    ctx.fillStyle = '#ff0';
    Object.values(bullets).forEach(bullet => {
        ctx.beginPath();
        ctx.arc(bullet.x, bullet.y, 4, 0, Math.PI * 2);
        ctx.fill();
    });

    // 渲染石头
    Object.values(asteroids).forEach(asteroid => {
        let color = asteroid.isBig ? '#a67c52' : '#c48a4b';
        if (asteroid.hp < (asteroid.isBig ? 2 : 1)) {
            color = '#8a6239';
        }

        ctx.fillStyle = color;
        ctx.beginPath();
        ctx.arc(asteroid.x, asteroid.y, asteroid.radius, 0, Math.PI * 2);
        ctx.fill();

        ctx.strokeStyle = '#6a4a2a';
        ctx.lineWidth = 2;
        ctx.stroke();
    });

    // 游戏结束提示
    if (gameState.phase === 'FINISHED') {
        ctx.fillStyle = 'rgba(0, 0, 0, 0.7)';
        ctx.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        ctx.fillStyle = '#fff';
        ctx.font = '48px Arial';
        ctx.textAlign = 'center';
        ctx.fillText('游戏结束!', CANVAS_WIDTH / 2, CANVAS_HEIGHT / 2);
    }
}

// ============ UI更新 ============
function updateUI() {
    // 检测掉血
    if (lastHp !== null && player.hp < lastHp) {
        hitFlashEndTime = performance.now() + 1000;
    }
    lastHp = player.hp;

    const hpText = document.getElementById('hpText');
    const scoreText = document.getElementById('scoreText');
    const timeText = document.getElementById('timeText');

    if (hpText) hpText.textContent = player.hp;
    if (scoreText) scoreText.textContent = player.score;
    if (timeText) timeText.textContent = formatTime(gameElapsedSeconds);

    // 更新计分板
    const scoreList = document.getElementById('scoreList');
    if (scoreList) {
        scoreList.innerHTML = `
            <li style="color: #ff4d4f;">
                ✈ ${username}: ${player.score} (HP: ${player.hp})
                ${player.alive ? '' : '<span style="color: #888;">(已死亡)</span>'}
            </li>
        `;
    }
}

function formatTime(seconds) {
    const minutes = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${minutes}:${secs.toString().padStart(2, '0')}`;
}

// ============ 输入处理 ============
function setupInput() {
    document.addEventListener('keydown', (e) => {
        const key = e.key.toLowerCase();
        if (key in keys) {
            keys[key] = true;
            e.preventDefault();
        }
    });

    document.addEventListener('keyup', (e) => {
        const key = e.key.toLowerCase();
        if (key in keys) {
            keys[key] = false;
            e.preventDefault();
        }
    });
}

function setupButtons() {
    const btnLeave = document.getElementById('btnLeave');
    const btnBackLobby = document.getElementById('btnBackLobby');

    if (btnLeave) btnLeave.addEventListener('click', backToLobby);
    if (btnBackLobby) btnBackLobby.addEventListener('click', backToLobby);
}

function backToLobby() {
    window.location.href = '/lobby.html';
}

