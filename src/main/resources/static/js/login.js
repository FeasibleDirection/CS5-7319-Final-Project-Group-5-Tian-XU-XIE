document.addEventListener('DOMContentLoaded', () => {
    const usernameInput = document.getElementById('username');
    const passwordInput = document.getElementById('password');
    const loginBtn = document.getElementById('loginBtn');
    const msgBox = document.getElementById('message');

    const saved = getSavedCredentials();
    if (saved.username) {
        usernameInput.value = saved.username;
    }
    if (saved.password) {
        passwordInput.value = saved.password;
    }

    loginBtn.addEventListener('click', async () => {
        const username = usernameInput.value.trim();
        const password = passwordInput.value.trim();

        msgBox.textContent = '';
        msgBox.className = 'message';

        if (!username || !password) {
            msgBox.textContent = 'Please enter account and password';
            msgBox.classList.add('error');
            return;
        }

        try {
            const resp = await fetch('/api/login', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ username, password })
            });

            if (!resp.ok) {
                const text = await resp.text();
                msgBox.textContent = text || 'Login failed';
                msgBox.classList.add('error');
                return;
            }

            const data = await resp.json();
            // Save token + user info + password (for demo)
            saveLoginSession(data.token, data.user, true, password);

            msgBox.textContent = 'Login successful! Entering lobby...';
            msgBox.classList.add('success');

            // Redirect to lobby after login success
            // Delay 500ms to let user see "Login successful" message
            setTimeout(() => {
                window.location.href = '/lobby.html';
            }, 500);
        } catch (e) {
            msgBox.textContent = 'Cannot connect to server';
            msgBox.classList.add('error');
        }
    });
});
