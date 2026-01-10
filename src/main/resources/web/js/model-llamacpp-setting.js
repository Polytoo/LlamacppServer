// --- Initialization ---
document.addEventListener('DOMContentLoaded', function() {
    loadLlamaCppList();
});

// --- Global Variables ---
let llamaCppItems = [];

// --- Load Llama.cpp List ---
function loadLlamaCppList() {
    const container = document.getElementById('llamacppList');
    container.innerHTML = '<div class="loading-spinner"><div class="spinner"></div></div>';

    fetch('/api/llamacpp/list')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                llamaCppItems = data.data.items || [];
                document.getElementById('llamacppCount').textContent = llamaCppItems.length;
                renderLlamaCppList();
            } else {
                showToast('错误', data.error || '加载失败', 'error');
                container.innerHTML = `<div class="empty-state"><div class="empty-state-icon"><i class="fas fa-exclamation-triangle"></i></div><div class="empty-state-title">加载失败</div><div class="empty-state-text">${data.error || '未知错误'}</div></div>`;
            }
        })
        .catch(error => {
            console.error('加载 Llama.cpp 列表出错:', error);
            showToast('错误', '网络请求失败', 'error');
            container.innerHTML = `<div class="empty-state"><div class="empty-state-icon"><i class="fas fa-exclamation-triangle"></i></div><div class="empty-state-title">网络错误</div><div class="empty-state-text">无法连接到服务器</div></div>`;
        });
}

// --- Render Llama.cpp List ---
function renderLlamaCppList() {
    const container = document.getElementById('llamacppList');

    if (!llamaCppItems || llamaCppItems.length === 0) {
        container.innerHTML = `<div class="empty-state"><div class="empty-state-icon"><i class="fas fa-folder-open"></i></div><div class="empty-state-title">暂无配置</div><div class="empty-state-text">尚未配置任何 Llama.cpp 路径</div></div>`;
        return;
    }

    let html = '';
    llamaCppItems.forEach((item, index) => {
        const path = item.path || '';
        const name = item.name || '';
        const desc = item.description || '';
        const displayName = name || path;
        const escapedPath = path.replace(/\\/g, '\\\\').replace(/'/g, "\\'");
        const escapedName = name ? name.replace(/'/g, "\\'") : '';
        const escapedDesc = desc ? desc.replace(/'/g, "\\'") : '';

        html += `
            <div class="model-item">
                <div class="model-icon-wrapper">
                    <i class="fas fa-microchip"></i>
                </div>
                <div class="model-details">
                    <div class="model-name" title="${displayName}">${displayName}</div>
                    <div class="model-meta">
                        <span><i class="fas fa-folder"></i> ${path}</span>
                    </div>
                    ${desc ? `<div class="model-desc" title="${desc}"><i class="fas fa-info-circle"></i> ${desc}</div>` : ''}
                </div>
                <div class="model-actions">
                    <button class="btn-icon" onclick="testLlamaCpp('${escapedPath}', '${escapedName}', '${escapedDesc}')" title="测试">
                        <i class="fas fa-vial"></i>
                    </button>
                    <button class="btn-icon" onclick="editLlamaCpp('${escapedPath}', '${escapedName}', '${escapedDesc}')" title="编辑">
                        <i class="fas fa-edit"></i>
                    </button>
                    <button class="btn-icon danger" onclick="removeLlamaCpp('${escapedPath}')" title="删除">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </div>
        `;
    });
    container.innerHTML = html;
}

// --- Add/Edit Llama.cpp ---
let editingPath = null;

function openAddLlamaCppModal() {
    editingPath = null;
    document.getElementById('addLlamaCppPathInput').value = '';
    document.getElementById('addLlamaCppNameInput').value = '';
    document.getElementById('addLlamaCppDescInput').value = '';
    document.querySelector('#addLlamaCppModal .modal-title').innerHTML = '<i class="fas fa-plus"></i> 添加 Llama.cpp 路径';
    document.getElementById('addLlamaCppModal').classList.add('show');
}

function editLlamaCpp(path, name, desc) {
    editingPath = path;
    document.getElementById('addLlamaCppPathInput').value = path;
    document.getElementById('addLlamaCppNameInput').value = name;
    document.getElementById('addLlamaCppDescInput').value = desc;
    document.querySelector('#addLlamaCppModal .modal-title').innerHTML = '<i class="fas fa-edit"></i> 编辑 Llama.cpp 路径';
    document.getElementById('addLlamaCppModal').classList.add('show');
}

async function addLlamaCpp() {
    const path = document.getElementById('addLlamaCppPathInput').value.trim();
    const name = document.getElementById('addLlamaCppNameInput').value.trim();
    const desc = document.getElementById('addLlamaCppDescInput').value.trim();

    if (!path) {
        showToast('错误', '目录路径不能为空', 'error');
        return;
    }

    const payload = { path };
    if (name) payload.name = name;
    if (desc) payload.description = desc;

    // 如果是编辑模式，先删除旧的再添加新的
    if (editingPath) {
        await fetch('/api/llamacpp/remove', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ path: editingPath })
        });
    }

    fetch('/api/llamacpp/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showToast('成功', editingPath ? '更新成功' : '添加成功', 'success');
            closeModal('addLlamaCppModal');
            loadLlamaCppList();
        } else {
            showToast('错误', data.error || '添加失败', 'error');
        }
    })
    .catch(error => {
        console.error('添加 Llama.cpp 出错:', error);
        showToast('错误', '网络请求失败', 'error');
    });
}

// --- Remove Llama.cpp ---
function removeLlamaCpp(path) {
    if (!confirm(`确定要删除路径 "${path}" 吗？`)) {
        return;
    }

    fetch('/api/llamacpp/remove', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ path })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showToast('成功', '删除成功', 'success');
            loadLlamaCppList();
        } else {
            showToast('错误', data.error || '删除失败', 'error');
        }
    })
    .catch(error => {
        console.error('删除 Llama.cpp 出错:', error);
        showToast('错误', '网络请求失败', 'error');
    });
}

function ensureLlamaCppTestModal() {
    const modalId = 'llamaCppTestModal';
    let modal = document.getElementById(modalId);
    if (modal) return modal;

    modal = document.createElement('div');
    modal.id = modalId;
    modal.className = 'modal';
    modal.innerHTML = `
        <div class="modal-content" style="max-width: 980px; height: 85vh;">
            <div class="modal-header">
                <h3 class="modal-title" id="llamaCppTestModalTitle"><i class="fas fa-vial"></i> Llama.cpp 测试</h3>
                <button class="modal-close" onclick="closeModal('${modalId}')">&times;</button>
            </div>
            <div class="modal-body" style="height: calc(85vh - 132px); overflow: auto;">
                <div style="margin-bottom: 14px;">
                    <div style="font-weight: 600; margin-bottom: 6px;">llama-cli --version</div>
                    <div style="font-size: 0.875rem; color: var(--text-secondary); margin-bottom: 6px;">
                        <span id="llamaCppTestVersionCmd"></span>
                        <span style="margin-left: 10px;">exitCode: <span id="llamaCppTestVersionExit"></span></span>
                    </div>
                    <pre id="llamaCppTestVersionOut" style="white-space: pre-wrap; padding: 10px; border: 1px solid var(--border-color); border-radius: 10px; background: #0b1220; color: #e5e7eb;"></pre>
                    <pre id="llamaCppTestVersionErr" style="white-space: pre-wrap; padding: 10px; border: 1px solid var(--border-color); border-radius: 10px; background: #1f2937; color: #fca5a5;"></pre>
                </div>
                <div style="margin-bottom: 14px;">
                    <div style="font-weight: 600; margin-bottom: 6px;">llama-cli --list-devices</div>
                    <div style="font-size: 0.875rem; color: var(--text-secondary); margin-bottom: 6px;">
                        <span id="llamaCppTestDevicesCmd"></span>
                        <span style="margin-left: 10px;">exitCode: <span id="llamaCppTestDevicesExit"></span></span>
                    </div>
                    <pre id="llamaCppTestDevicesOut" style="white-space: pre-wrap; padding: 10px; border: 1px solid var(--border-color); border-radius: 10px; background: #0b1220; color: #e5e7eb;"></pre>
                    <pre id="llamaCppTestDevicesErr" style="white-space: pre-wrap; padding: 10px; border: 1px solid var(--border-color); border-radius: 10px; background: #1f2937; color: #fca5a5;"></pre>
                </div>
                <div style="font-size: 0.875rem; color: var(--text-secondary);">原始响应</div>
                <pre id="llamaCppTestRaw" style="white-space: pre-wrap; padding: 10px; border: 1px solid var(--border-color); border-radius: 10px; background: #111827; color: #d1d5db;"></pre>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" onclick="closeModal('${modalId}')">关闭</button>
            </div>
        </div>
    `;

    const root = document.getElementById('dynamicModalRoot') || document.body;
    root.appendChild(modal);
    return modal;
}

function setLlamaCppTestModalLoading(titleText) {
    const titleEl = document.getElementById('llamaCppTestModalTitle');
    if (titleEl) titleEl.textContent = titleText || 'Llama.cpp 测试';
    const ids = [
        'llamaCppTestVersionCmd', 'llamaCppTestVersionExit', 'llamaCppTestVersionOut', 'llamaCppTestVersionErr',
        'llamaCppTestDevicesCmd', 'llamaCppTestDevicesExit', 'llamaCppTestDevicesOut', 'llamaCppTestDevicesErr',
        'llamaCppTestRaw'
    ];
    ids.forEach(id => {
        const el = document.getElementById(id);
        if (el) el.textContent = id.endsWith('Out') || id.endsWith('Err') || id.endsWith('Raw') ? '加载中...' : '';
    });
}

function fillLlamaCppTestModal(res) {
    const data = res && res.data ? res.data : null;
    const version = data && data.version ? data.version : null;
    const listDevices = data && data.listDevices ? data.listDevices : null;

    const setText = (id, v) => {
        const el = document.getElementById(id);
        if (el) el.textContent = v == null ? '' : String(v);
    };

    setText('llamaCppTestVersionCmd', version ? version.command : '');
    setText('llamaCppTestVersionExit', version ? version.exitCode : '');
    setText('llamaCppTestVersionOut', version ? (version.output || '') : '');
    setText('llamaCppTestVersionErr', version ? (version.error || '') : '');

    setText('llamaCppTestDevicesCmd', listDevices ? listDevices.command : '');
    setText('llamaCppTestDevicesExit', listDevices ? listDevices.exitCode : '');
    setText('llamaCppTestDevicesOut', listDevices ? (listDevices.output || '') : '');
    setText('llamaCppTestDevicesErr', listDevices ? (listDevices.error || '') : '');

    setText('llamaCppTestRaw', JSON.stringify(res, null, 2));
}

async function testLlamaCpp(path, name, desc) {
    const modal = ensureLlamaCppTestModal();
    modal.classList.add('show');

    const displayName = (name && name.trim()) ? name.trim() : path;
    setLlamaCppTestModalLoading('Llama.cpp 测试 - ' + displayName);

    try {
        const payload = { path };
        if (name) payload.name = name;
        if (desc) payload.description = desc;

        const resp = await fetch('/api/llamacpp/test', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await resp.json();

        if (!data || !data.success) {
            const rawEl = document.getElementById('llamaCppTestRaw');
            if (rawEl) rawEl.textContent = JSON.stringify(data, null, 2);
            showToast('错误', (data && data.error) ? data.error : '测试失败', 'error');
            fillLlamaCppTestModal(data || { success: false, error: '测试失败' });
            return;
        }

        fillLlamaCppTestModal(data);
    } catch (e) {
        const rawEl = document.getElementById('llamaCppTestRaw');
        if (rawEl) rawEl.textContent = String(e && e.message ? e.message : e);
        showToast('错误', '网络请求失败', 'error');
    }
}

// --- Modal Functions ---
function closeModal(id) {
    const el = document.getElementById(id);
    if (el) el.classList.remove('show');
}

window.onclick = function(e) {
    if (e.target.classList.contains('modal')) {
        closeModal(e.target.id);
    }
};

// --- Toast Function ---
function showToast(title, msg, type = 'info') {
    const container = document.getElementById('toastContainer');
    const id = 'toast-' + Date.now();
    const html = `
        <div class="toast ${type}" id="${id}">
            <div class="toast-icon"><i class="fas ${type === 'success' ? 'fa-check-circle' : type === 'error' ? 'fa-exclamation-circle' : 'fa-info-circle'}"></i></div>
            <div class="toast-content"><div class="toast-title">${title}</div><div class="toast-message">${msg}</div></div>
            <button class="toast-close" onclick="document.getElementById('${id}').remove()">&times;</button>
        </div>`;
    container.insertAdjacentHTML('beforeend', html);
    setTimeout(() => { const el = document.getElementById(id); if (el) el.remove(); }, 5000);
}

// --- Shutdown Service ---
function shutdownService() {
    if (confirm('确定要停止服务吗？')) {
        fetch('/api/shutdown', { method: 'POST' }).then(r => r.json()).then(d => {
            if (d.success) {
                document.body.innerHTML = '<div style="width: 100%;display:flex;justify-content:center;align-items:center;height:100vh;"><h1>服务已停止</h1></div>';
            }
        });
    }
}

// --- Console/Settings entrypoints ---
(function () {
    if (typeof window.openConsoleModal !== 'function') {
        window.openConsoleModal = function () {
            window.location.href = 'index.html';
        };
    }
})();
