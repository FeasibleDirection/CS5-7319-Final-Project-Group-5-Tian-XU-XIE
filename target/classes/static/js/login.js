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
            msgBox.textContent = '请输入账号和密码';
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
                msgBox.textContent = text || '登录失败';
                msgBox.classList.add('error');
                return;
            }

            const data = await resp.json();
            // 保存 token + 用户信息 + 账号密码（demo 用）
            saveLoginSession(data.token, data.user, true, password);

            msgBox.textContent = '登录成功！正在进入大厅…';
            msgBox.classList.add('success');

            //登录成功后跳转到大厅页面
            // 稍微延迟 500ms，让用户看到“登录成功”提示
            setTimeout(() => {
                window.location.href = '/lobby.html';
            }, 500);
        } catch (e) {
            msgBox.textContent = '无法连接服务器';
            msgBox.classList.add('error');
        }
    });
});
