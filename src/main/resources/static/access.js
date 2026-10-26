(() => {
    let access = { permissions: [], menus: [] };
    const can = (permission) => access.permissions.includes('*') || access.permissions.includes(permission);
    async function api(url, options = {}) {
        const response = await DemoAuth.fetch(url, { ...options, headers: { 'Content-Type': 'application/json', ...options.headers } });
        const data = response.status === 204 ? null : await response.json().catch(() => null);
        if (!response.ok) throw new Error(data?.message || `请求失败（${response.status}）`);
        return data;
    }
    const ready = (async () => {
        if (!DemoAuth.requireLogin()) return false;
        try {
            access = await api('/api/auth/access');
            document.querySelectorAll('[data-navigation]').forEach(nav => {
                nav.replaceChildren();
                const append = (parent, depth) => access.menus.filter(m => m.parentId === parent).forEach(menu => {
                    if (menu.type === 'DIRECTORY') {
                        if (nav.classList.contains('nav-links')) {
                            const label = document.createElement('span'); label.className = 'nav-group'; label.textContent = menu.name; nav.append(label);
                        }
                    } else {
                        const link = document.createElement('a'); link.href = menu.path; link.textContent = menu.name;
                        link.className = 'nav-link' + (menu.path === location.pathname ? ' active' : '');
                        if (menu.path === location.pathname) link.setAttribute('aria-current', 'page');
                        nav.append(link);
                    }
                    append(menu.id, depth + 1);
                });
                append(null, 0);
            });
            const permission = document.body.dataset.permission;
            if (permission && !can(permission)) {
                const main = document.querySelector('main');
                main.replaceChildren();
                const panel = document.createElement('section'); panel.className = 'list-state';
                const title = document.createElement('h1'); title.textContent = '暂无访问权限';
                const info = document.createElement('p'); info.textContent = '请联系管理员分配角色或权限，也可从导航进入已授权页面。';
                const logout = document.createElement('button'); logout.className = 'button button-outline'; logout.textContent = '退出并切换账号';
                logout.onclick = async () => { try { await api('/api/auth/logout', {method:'POST'}); } finally { DemoAuth.clear(); location.replace('/login.html'); } };
                panel.append(title, info, logout); main.append(panel);
                return false;
            }
            return true;
        } catch (error) {
            const main = document.querySelector('main');
            if (main) { main.replaceChildren(); const message = document.createElement('p'); message.textContent = error.message; main.append(message); }
            return false;
        }
    })();
    window.DemoAccess = { ready, can, api };
})();
