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
