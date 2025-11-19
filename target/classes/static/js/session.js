const STORAGE_KEY_TOKEN = 'game_demo_token';
const STORAGE_KEY_USER = 'game_demo_user';
const STORAGE_KEY_USERNAME = 'game_demo_username';
const STORAGE_KEY_PASSWORD = 'game_demo_password';

function saveLoginSession(token, user, rememberPassword, password) {
    localStorage.setItem(STORAGE_KEY_TOKEN, token);
    localStorage.setItem(STORAGE_KEY_USER, JSON.stringify(user));
    localStorage.setItem(STORAGE_KEY_USERNAME, user.username);
    if (rememberPassword && typeof password === 'string') {
        localStorage.setItem(STORAGE_KEY_PASSWORD, password);
    }
}

function clearLoginSession() {
    localStorage.removeItem(STORAGE_KEY_TOKEN);
    localStorage.removeItem(STORAGE_KEY_USER);
    localStorage.removeItem(STORAGE_KEY_USERNAME);
    localStorage.removeItem(STORAGE_KEY_PASSWORD);
}

function getSavedCredentials() {
    const username = localStorage.getItem(STORAGE_KEY_USERNAME) || '';
    const password = localStorage.getItem(STORAGE_KEY_PASSWORD) || '';
    return { username, password };
}

function getCurrentToken() {
    return localStorage.getItem(STORAGE_KEY_TOKEN);
}

function getCurrentUser() {
    const raw = localStorage.getItem(STORAGE_KEY_USER);
    if (!raw) return null;
    try {
        return JSON.parse(raw);
    } catch (e) {
        return null;
    }
}

async function authFetch(url, options = {}) {
    const token = getCurrentToken();
    if (!token) {
        throw new Error('No token in localStorage');
    }
    const headers = options.headers ? { ...options.headers } : {};
    headers['Authorization'] = 'Bearer ' + token;
    return fetch(url, { ...options, headers });
}

async function validateToken() {
    const token = getCurrentToken();
    if (!token) return null;

    const resp = await authFetch('/api/auth/me');
    if (!resp.ok) {
        return null;
    }
    return await resp.json();
}
