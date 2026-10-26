(async () => {
    if (!await DemoAccess.ready) return;
    const form = document.querySelector('#message-form');
    const input = document.querySelector('#message-input');
    const send = document.querySelector('#send-button');
    const stop = document.querySelector('#stop-button');
    const clear = document.querySelector('#clear-button');
    const messages = document.querySelector('#messages');
    const status = document.querySelector('#status');
    const counter = document.querySelector('#counter');
    const empty = document.querySelector('#empty-state');
    const effort = document.querySelector('#effort');
    const history = [];
    let active = null;

    function updateControls() {
        send.disabled = !!active || !input.value.trim();
        send.textContent = active ? '正在回复' : '发送消息';
        stop.hidden = !active;
        clear.disabled = !!active;
        effort.disabled = !!active;
        counter.textContent = `${input.value.length} / 2000`;
        messages.querySelectorAll('.retry').forEach(button => button.disabled = !!active);
    }

    function addMessage(text, role) {
        empty.remove();
        const row = document.createElement('article');
        row.className = `message ${role}`;
        const label = document.createElement('div');
        label.className = 'message-label';
        label.textContent = role === 'user' ? '你' : 'GPT-5.6 Luna';
        const body = document.createElement('div');
        body.className = 'message-text';
        body.textContent = text;
        const meta = document.createElement('div');
        meta.className = 'message-meta';
        row.append(label, body, meta);
        messages.append(row);
        messages.scrollTop = messages.scrollHeight;
        return { row, body, meta };
    }

    async function generate(text, reply = null) {
        if (active) return;
        if (!reply) {
            // 继续新一轮后，旧失败消息不再重试，避免把旧答案插入新的上下文。
            messages.querySelectorAll('.retry').forEach(button => button.remove());
            addMessage(text, 'user');
            reply = addMessage('', 'server');
            reply.history = [];
            let size = text.length;
            for (const turn of history.slice(-20).reverse()) {
                const length = turn[0].content.length + turn[1].content.length;
                if (size + length > 64000 || turn[1].content.length > 32000) break;
                reply.history.unshift(...turn);
                size += length;
            }
            reply.effort = effort.value;
            input.value = '';
        }
        const controller = new AbortController();
        active = controller;
        let timedOut = false;
        let finished = false;
        let reader;
        // 心跳和文本均重置等待计时；服务端另有整次生成的总时限。
        let timer;
        const resetTimeout = () => {
            clearTimeout(timer);
            timer = setTimeout(() => { timedOut = true; controller.abort(); }, 45000);
        };
        resetTimeout();
        reply.body.textContent = '';
        reply.meta.textContent = '正在连接…';
        reply.row.classList.remove('error');
        reply.row.classList.add('streaming');
        status.textContent = `AI 正在思考… · 携带 ${reply.history.length / 2} 轮上下文`;
        updateControls();
        try {
            const response = await DemoAuth.fetch('/api/messages', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream, application/json' },
                body: JSON.stringify({ message: text, history: reply.history, effort: reply.effort }),
                signal: controller.signal
            });
            if (!response.ok) {
                const error = await response.json().catch(() => null);
                throw new Error(error?.message || `请求失败（${response.status}）`);
            }
            if (!response.headers.get('Content-Type')?.includes('text/event-stream') || !response.body) {
                throw new Error('服务未返回有效的流式回复');
            }
            reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            const consume = (frame) => {
                let event = 'message';
                const data = [];
                for (const line of frame.split(/\r?\n/)) {
                    if (line.startsWith('event:')) event = line.slice(6).trim();
                    if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
                }
                if (!data.length) return;
                const payload = JSON.parse(data.join('\n'));
                if (event === 'start') reply.meta.textContent = 'AI 正在思考…';
                if (event === 'delta') {
                    if (typeof payload.text !== 'string') throw new Error('回复数据格式错误');
                    const nearBottom = messages.scrollHeight - messages.scrollTop - messages.clientHeight < 100;
                    reply.body.textContent += payload.text;
                    reply.meta.textContent = '正在输出…';
                    if (nearBottom) messages.scrollTop = messages.scrollHeight;
                }
                if (event === 'done') finished = true;
                if (event === 'error') throw new Error(payload.message || '生成失败，请重试');
            };
            while (!finished) {
                const { value, done } = await reader.read();
                resetTimeout();
                buffer += done ? decoder.decode() : decoder.decode(value, { stream: true });
                // 网络分块不等于事件边界，保留半条事件及跨块的 UTF-8 字节。
                let boundary;
                while ((boundary = /\r?\n\r?\n/.exec(buffer)) !== null) {
                    const frame = buffer.slice(0, boundary.index);
                    buffer = buffer.slice(boundary.index + boundary[0].length);
                    consume(frame);
                    if (finished) break;
                }
                if (done) break;
            }
            if (!finished) throw new Error('连接中断，回复未完成');
            history.push([{ role: 'user', content: text }, { role: 'assistant', content: reply.body.textContent }]);
            reply.meta.textContent = '回复完成';
            status.textContent = '回复完成，可以继续发送';
        } catch (error) {
            const stopped = controller.signal.aborted && !timedOut;
            reply.row.classList.toggle('error', !stopped);
            reply.meta.textContent = stopped ? '已停止生成' : timedOut ? '等待超时，请重试' : error.message;
            if (!reply.body.textContent) reply.body.textContent = stopped ? '本次生成已停止。' : '暂时无法生成回复。';
            const retry = document.createElement('button');
            retry.type = 'button';
            retry.className = 'retry quiet';
            retry.textContent = '重新生成';
            retry.addEventListener('click', () => generate(text, reply));
            reply.meta.append(retry);
            status.textContent = stopped ? '已停止，可以继续发送' : '生成未完成，可重新生成';
        } finally {
            clearTimeout(timer);
            if (reader) { await reader.cancel().catch(() => {}); reader.releaseLock(); }
            reply.row.classList.remove('streaming');
            active = null;
            updateControls();
            input.focus();
        }
    }

    form.addEventListener('submit', event => {
        event.preventDefault();
        const text = input.value.trim();
        if (text && text.length <= 2000 && !active) void generate(text);
    });
    input.addEventListener('input', updateControls);
    input.addEventListener('keydown', event => {
        if (event.key === 'Enter' && !event.shiftKey && !event.isComposing && event.keyCode !== 229) {
            event.preventDefault();
            if (!send.disabled) form.requestSubmit();
        }
    });
    stop.addEventListener('click', () => active?.abort());
    clear.addEventListener('click', () => {
        if (active) return;
        messages.replaceChildren(empty);
        history.length = 0;
        status.textContent = '对话已清空';
        input.focus();
    });
    document.querySelector('#logout').addEventListener('click', async () => {
        active?.abort();
        try { await DemoAuth.fetch('/api/auth/logout', { method: 'POST' }); }
        finally { DemoAuth.clear(); location.replace('/login.html'); }
    });
    window.addEventListener('pagehide', () => active?.abort());
    updateControls();
    input.focus();
})();
