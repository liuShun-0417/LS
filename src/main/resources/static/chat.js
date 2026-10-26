(async () => {
    if (!DemoAuth.requireLogin()) return;
    if (!await DemoAccess.ready) return;
    const $ = selector => document.querySelector(selector);
    let socket, me, selected, groups = [], ready = false, subscribed = false, stopped = false;
    let reconnectTimer, heartbeat, pongDeadline, selectionVersion = 0;
    let reconnectDelay = 1000;
    const messages = new Map();
    const drafts = new Map();
    const outgoing = new Map();
    const sendTimers = new Map();
    const savedKeys = new Set();
    const keyOf = message => `${message.groupId}:${message.senderId}:${message.clientMessageId}`;
    function rememberOutgoing() {
        try { sessionStorage.setItem(`demo.chat.outbox.${me.id}`, JSON.stringify([...outgoing.values()])); }
        catch (_) { showError("浏览器暂时无法保存待发送记录，请等待消息保存成功后再刷新。"); }
    }

    function showError(message = "") {
        $("#error").textContent = message;
        $("#error").hidden = !message;
    }
    async function api(path, method = "GET", body) {
        const response = await DemoAuth.fetch(path, { method,
            headers: { "Content-Type": "application/json" },
            body: body === undefined ? undefined : JSON.stringify(body) });
        const data = await response.json().catch(() => null);
        if (!response.ok) throw new Error(data?.message || `请求失败（${response.status}）`);
        return data;
    }
    function controls() {
        $("#content").disabled = !selected;
        $("#send").disabled = !ready || !subscribed || !selected;
    }
    function renderGroups() {
        const container = $("#groups");
        container.replaceChildren();
        if (!groups.length) { container.textContent = "还没有群组，创建第一个群组吧"; return; }
        for (const group of groups) {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "group" + (selected?.id === group.id ? " active" : "");
            button.setAttribute("aria-pressed", String(selected?.id === group.id));
            const name = document.createElement("strong");
            name.textContent = group.name;
            const detail = document.createElement("small");
            detail.textContent = `${group.memberCount} 位成员 · ${group.joined ? "已加入 · 进入聊天" : "点击加入群组"}`;
            button.append(name, detail);
            button.onclick = () => choose(group).catch(error => showError(error.message));
            container.append(button);
        }
    }
    async function refreshGroups() {
        let cursor = 0;
        const result = [];
        while (true) {
            const batch = await api(`/api/groups?afterId=${cursor}`);
            result.push(...batch);
            if (batch.length < 50) break;
            cursor = batch.at(-1).id;
        }
        groups = result;
        renderGroups();
    }
    async function choose(group) {
        const version = ++selectionVersion;
        if (!group.joined) {
            await api(`/api/groups/${group.id}/join`, "POST");
            group.joined = true;
            group.memberCount++;
        }
        if (version !== selectionVersion) return;
        if (selected) drafts.set(selected.id, $("#content").value);
        selected = group;
        subscribed = false;
        messages.clear();
        for (const message of outgoing.values()) if (message.groupId === group.id) messages.set(keyOf(message), message);
        showError();
        $("#content").value = drafts.get(group.id) || "";
        $("#group-title").textContent = group.name;
        $("#group-info").textContent = "可查看入群前最近 30 条，以及入群后的消息";
        $("#send-status").textContent = "消息即时发送，随后自动保存";
        renderGroups(); renderMessages(); controls();
        subscribe();
    }
    function subscribe() {
        if (ready && selected) socket.send(JSON.stringify({ type: "subscribe", groupId: selected.id, requestId: String(selectionVersion) }));
    }
    function merge(message) {
        const key = keyOf(message);
        if (savedKeys.has(key) && message.deliveryStatus !== "saved") return;
        if (message.deliveryStatus === "saved") savedKeys.add(key);
        const current = messages.get(key);
        if (current?.deliveryStatus === "saved" && message.deliveryStatus !== "saved") return;
        if (message.groupId === selected?.id) messages.set(key, message);
        if (message.senderId === me.id) {
            clearTimeout(sendTimers.get(key));
            sendTimers.delete(key);
            if (message.deliveryStatus === "saved") outgoing.delete(key);
            else outgoing.set(key, message);
            rememberOutgoing();
        }
    }
    function renderMessages() {
        const box = $("#messages");
        const nearBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 100;
        const previousTop = box.scrollTop;
        box.replaceChildren();
        if (!messages.size) {
            const empty = document.createElement("p");
            empty.className = "empty";
            empty.textContent = selected ? "还没有消息，发送第一条吧" : "加入群组后，消息会显示在这里";
            box.append(empty);
        }
        for (const message of [...messages.values()].sort((a, b) => a.sentAt.localeCompare(b.sentAt) || (a.id || 0) - (b.id || 0))) {
            const row = document.createElement("div");
            row.className = "message" + (message.senderId === me.id ? " own" : "");
            const meta = document.createElement("span");
            meta.className = "meta";
            meta.textContent = `${message.senderName} · ${message.sentAt.replace("T", " ").slice(0, 19)}`;
            const bubble = document.createElement("div");
            bubble.className = "bubble";
            bubble.textContent = message.content;
            const state = document.createElement("span");
            state.className = "delivery-state";
            state.textContent = ({ sending: "发送中…", pending: "已发送 · 正在保存", failed: "未保存成功", saved: "已保存" })[message.deliveryStatus] || "已保存";
            row.append(meta, bubble, state);
            if (message.deliveryStatus === "failed" && message.senderId === me.id) {
                const retry = document.createElement("button");
                retry.type = "button";
                retry.className = "retry-message";
                retry.textContent = "重试";
                retry.disabled = !ready || !subscribed;
                retry.onclick = () => transmit(message);
                row.append(retry);
            }
            box.append(row);
        }
        box.scrollTop = nearBottom ? box.scrollHeight : previousTop;
    }
    async function loadHistory(groupId, version, connection) {
        // 从固定入群边界分页补齐，用群组 + 发送者 + 客户端消息编号合并临时消息和数据库记录。
        let cursor = 0;
        do {
            const page = await api(`/api/groups/${groupId}/messages?afterId=${cursor}`);
            if (selected?.id !== groupId || version !== selectionVersion || socket !== connection) return;
            page.items.forEach(merge);
            renderMessages();
            cursor = page.nextAfterId;
            if (!page.hasMore) break;
        } while (true);
        if (socket === connection && selected?.id === groupId && version === selectionVersion) {
            for (const message of [...outgoing.values()]) {
                if (message.groupId === groupId && message.deliveryStatus !== "failed") transmit(message);
            }
        }
    }
    function connect() {
        if (stopped) return;
        $("#connection").textContent = "正在连接";
        const connection = new WebSocket(`${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/ws/chat`);
        socket = connection;
        connection.onopen = () => connection.send(JSON.stringify({ type: "auth", token: DemoAuth.token() }));
        connection.onmessage = event => {
            if (socket !== connection) return;
            const data = JSON.parse(event.data);
            switch (data.type) {
                case "ready":
                    ready = true; reconnectDelay = 1000;
                    $("#connection").textContent = "已连接";
                    subscribe(); controls();
                    heartbeat = setInterval(() => {
                        connection.send(JSON.stringify({ type: "ping" }));
                        pongDeadline = setTimeout(() => connection.close(), 10000);
                    }, 25000);
                    break;
                case "pong": clearTimeout(pongDeadline); break;
                case "subscribed":
                    if (data.groupId !== selected?.id || data.requestId !== String(selectionVersion)) break;
                    subscribed = true; controls();
                    loadHistory(data.groupId, selectionVersion, connection).catch(error => {
                        showError(`历史消息加载失败：${error.message}。正在重新连接补齐。`);
                        connection.close();
                    });
                    break;
                case "message": case "ack": case "saved": case "save_failed":
                    merge(data.message); renderMessages();
                    if (data.type === "save_failed") showError("消息已发送，但保存失败，可点击消息旁的重试。");
                    break;
                case "error":
                    showError(data.message);
                    if (data.status === 401) { stopped = true; DemoAuth.clear(); DemoAuth.requireLogin(); }
                    for (const message of outgoing.values()) if (message.clientMessageId === data.requestId) markFailed(message);
                    break;
            }
        };
        connection.onclose = event => {
            if (socket !== connection) return;
            ready = false; subscribed = false;
            clearInterval(heartbeat); clearTimeout(pongDeadline);
            for (const timer of sendTimers.values()) clearTimeout(timer);
            sendTimers.clear();
            controls();
            $("#connection").textContent = "连接已断开，正在重连";
            if (event.code === 1008) { stopped = true; DemoAuth.clear(); DemoAuth.requireLogin(); return; }
            if (!stopped) {
                reconnectTimer = setTimeout(connect, reconnectDelay);
                reconnectDelay = Math.min(reconnectDelay * 2, 15000);
            }
        };
        connection.onerror = () => connection.close();
    }
    $("#create-group").onsubmit = async event => {
        event.preventDefault();
        const name = $("#group-name").value.trim();
        if (!name) { showError("请输入群组名称"); return; }
        $("#create").disabled = true;
        try {
            const created = await api("/api/groups", "POST", { name });
            $("#group-name").value = "";
            await refreshGroups();
            await choose(groups.find(group => group.id === created.id));
        } catch (error) { showError(error.message); }
        finally { $("#create").disabled = false; }
    };
    function markFailed(message) {
        merge({ ...message, deliveryStatus: "failed" });
        renderMessages();
    }
    function transmit(message) {
        if (!ready || !subscribed || selected?.id !== message.groupId) return;
        const key = keyOf(message);
        merge({ ...message, deliveryStatus: "sending" });
        renderMessages();
        try {
            socket.send(JSON.stringify({ type: "send", groupId: message.groupId, content: message.content,
                clientMessageId: message.clientMessageId, requestId: message.clientMessageId }));
            sendTimers.set(key, setTimeout(() => {
                const unconfirmed = outgoing.get(key);
                if (unconfirmed) { markFailed(unconfirmed); showError("发送确认超时，可点击消息旁的重试。"); }
            }, 10000));
        } catch (_) { markFailed(message); }
    }
    $("#send-message").onsubmit = event => {
        event.preventDefault();
        if (!ready || !subscribed || !selected) return;
        const content = $("#content").value;
        if (!content.trim()) { showError("请输入消息内容"); return; }
        const clientMessageId = Array.from(crypto.getRandomValues(new Uint8Array(16)), n => n.toString(16).padStart(2, "0")).join("");
        const sentAt = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, -1);
        showError();
        transmit({ groupId: selected.id, senderId: me.id, senderName: me.name || me.account,
            content, clientMessageId, sentAt, deliveryStatus: "sending" });
        $("#content").value = "";
        $("#content").focus();
        $("#send-status").textContent = "可继续发送，保存状态显示在每条消息下方";
    };
    $("#content").onkeydown = event => {
        if (event.key === "Enter" && !event.shiftKey && !event.isComposing) { event.preventDefault(); $("#send-message").requestSubmit(); }
    };
    $("#refresh").onclick = () => refreshGroups().catch(error => showError(error.message));
    $("#logout").onclick = async () => {
        try {
            const response = await DemoAuth.fetch("/api/auth/logout", { method: "POST" });
            if (!response.ok) throw new Error("退出失败，请重试");
            stopped = true; socket?.close(); DemoAuth.clear(); location.replace("/login.html");
        } catch (error) { showError(error.message); }
    };
    window.addEventListener("pagehide", () => {
        stopped = true; clearTimeout(reconnectTimer); clearInterval(heartbeat); clearTimeout(pongDeadline);
        for (const timer of sendTimers.values()) clearTimeout(timer);
        socket?.close();
    });
    (async () => {
        me = await api("/api/auth/me");
        try {
            const previous = JSON.parse(sessionStorage.getItem(`demo.chat.outbox.${me.id}`) || "[]");
            for (const message of previous) if (message.senderId === me.id) outgoing.set(keyOf(message), message);
        } catch (_) { /* 损坏的本地记录不影响登录和历史加载。 */ }
        $("#current-user").textContent = me.name || me.account;
        await refreshGroups(); connect();
    })().catch(error => showError(error.message));
})();
