let websocket = null;
let reconnectAttempts = 0;
const maxReconnectAttempts = 5;
const reconnectInterval = 5000;
const wsDecoder = new TextDecoder('utf-8');

function initWebSocket() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws`;

    try {
        websocket = new WebSocket(wsUrl);
        websocket.onopen = function(event) {
            console.log('WebSocket Connected');
            reconnectAttempts = 0;
            websocket.send(JSON.stringify({ type: 'connect', message: 'Connected', timestamp: new Date().toISOString() }));
        };
        websocket.onmessage = function(event) {
            handleWebSocketMessage(event.data);
        };
        websocket.onclose = function(event) {
            console.log('WebSocket Closed');
            if (reconnectAttempts < maxReconnectAttempts) {
                reconnectAttempts++;
                setTimeout(initWebSocket, reconnectInterval);
            }
        };
        websocket.onerror = function(error) { console.error('WebSocket Error:', error); };
    } catch (error) { console.error('WebSocket Init Failed:', error); }
}

function handleWebSocketMessage(message) {
    try {
        const data = JSON.parse(message);
        if (data.type) {
            switch (data.type) {
                case 'modelLoad': handleModelLoadEvent(data); break;
                case 'modelStop': handleModelStopEvent(data); break;
                case 'notification': showToast(data.title || '通知', data.message || '', data.level || 'info'); break;
                case 'model_status': handleModelStatusUpdate(data); break;
                case 'console':
                    {
                        const consoleModal = document.getElementById('consoleModal');
                        if (consoleModal && consoleModal.classList.contains('show')) {
                            let text = '';
                            if (typeof data.line64 === 'string') {
                                const bin = atob(data.line64);
                                const bytes = new Uint8Array(bin.length);
                                for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
                                text = wsDecoder.decode(bytes);
                            } else if (typeof data.line === 'string') {
                                text = data.line;
                            }
                            if (text && typeof appendLogLine === 'function') appendLogLine(text);
                        }
                    }
                    break;
            }
        }
    } catch (error) {}
}

function handleModelStatusUpdate(data) {
    if (data.modelId && data.status) {
        loadModels();
    }
}

function handleModelLoadEvent(data) {
    removeModelLoadingState(data.modelId);
    const action = data.success ? '成功' : '失败';
    showToast('模型加载', `模型 ${data.modelId} 加载${action}`, data.success ? 'success' : 'error');

    if (window.pendingModelLoad && window.pendingModelLoad.modelId === data.modelId) {
        closeModal('loadModelModal');
        window.pendingModelLoad = null;
    }
    loadModels();
}

function handleModelStopEvent(data) {
    showToast('模型停止', `模型 ${data.modelId} 停止${data.success ? '成功' : '失败'}`, data.success ? 'success' : 'error');
    loadModels();
}

