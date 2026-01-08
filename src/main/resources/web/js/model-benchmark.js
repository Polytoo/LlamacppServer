function openModelBenchmarkDialog(modelId, modelName) {
    const modalId = 'modelBenchmarkModal';
    let modal = document.getElementById(modalId);
    if (!modal) {
        modal = document.createElement('div');
        modal.id = modalId;
        modal.className = 'modal';
        modal.innerHTML = `
            <div class="modal-content load-model-modal benchmark-modal">
                <div class="modal-header">
                    <h3 class="modal-title"><i class="fas fa-tachometer-alt"></i> 模型性能测试</h3>
                    <button class="modal-close" onclick="closeModal('${modalId}')">&times;</button>
                </div>
                <div class="modal-body">
                    <form id="modelBenchmarkForm">
                        <div class="load-model-layout">
                            <div id="benchmarkBasicParamsContainer">
                                <div class="form-group">
                                    <label class="form-label">模型</label>
                                    <div class="form-control" id="benchmarkModelName" style="background-color: #f3f4f6;"></div>
                                </div>

                                <div class="form-group">
                                    <label class="form-label" for="benchmarkLlamaBinPathSelect">Llama.cpp 版本</label>
                                    <select class="form-control" id="benchmarkLlamaBinPathSelect"></select>
                                </div>

                                <div class="form-group">
                                    <label class="form-label" for="benchmarkOutputSelect">输出格式 (-o)</label>
                                    <select class="form-control" id="benchmarkOutputSelect">
                                        <option value="md">md</option>
                                        <option value="csv">csv</option>
                                        <option value="json">json</option>
                                        <option value="jsonl">jsonl</option>
                                        <option value="sql">sql</option>
                                    </select>
                                    <small class="form-text">llama-bench 输出会保存到 benchmarks 文件夹</small>
                                </div>

                                <div class="form-group">
                                    <label class="form-label" for="benchmarkOutputErrSelect">错误输出格式 (-oe)</label>
                                    <select class="form-control" id="benchmarkOutputErrSelect">
                                        <option value="">不输出</option>
                                        <option value="md">md</option>
                                        <option value="csv">csv</option>
                                        <option value="json">json</option>
                                        <option value="jsonl">jsonl</option>
                                        <option value="sql">sql</option>
                                    </select>
                                </div>

                                <div class="form-group">
                                    <label class="form-label">可用计算设备 (-dev)</label>
                                    <small class="form-text">未启用或未选择设备时，使用 auto</small>
                                    <label style="display:inline-flex; align-items:center; gap:8px; margin-bottom:8px;">
                                        <input type="checkbox" id="benchmarkEnableDeviceSelect">
                                        启用设备选择
                                    </label>
                                    <div id="benchmarkDeviceChecklist" style="border: 1px solid var(--border-color); border-radius: 0.75rem; padding: 0.75rem; max-height: 260px; overflow: auto;">
                                        <div class="settings-empty">请先选择 Llama.cpp 版本</div>
                                    </div>
                                </div>

                                <div class="form-group">
                                    <label class="form-label" for="benchmarkRpcInput">RPC 设备 (-rpc)</label>
                                    <input type="text" class="form-control" id="benchmarkRpcInput" placeholder="例如: 127.0.0.1:50052,127.0.0.1:50053">
                                </div>
                            </div>

                            <div id="benchmarkParamsContainer">
                                <div class="form-group">
                                    <label class="form-label">重复次数 (-r)</label>
                                    <input type="number" class="form-control" id="benchmarkInputRepetitions" min="1" value="5">
                                </div>

                                <div class="form-group">
                                    <label class="form-label" for="benchmarkNumaSelect">NUMA 模式 (--numa)</label>
                                    <select class="form-control" id="benchmarkNumaSelect">
                                        <option value="">disabled</option>
                                        <option value="distribute">distribute</option>
                                        <option value="isolate">isolate</option>
                                        <option value="numactl">numactl</option>
                                    </select>
                                </div>

                                <div class="form-group">
                                    <label class="form-label" for="benchmarkPrioSelect">优先级 (--prio)</label>
                                    <select class="form-control" id="benchmarkPrioSelect">
                                        <option value="0">0</option>
                                        <option value="1">1</option>
                                        <option value="2">2</option>
                                        <option value="3">3</option>
                                    </select>
                                </div>

                                <div class="form-group">
                                    <label class="form-label">测试间隔 (--delay, seconds)</label>
                                    <input type="number" class="form-control" id="benchmarkDelayInput" min="0" step="1" value="0">
                                </div>

                                <div class="form-group">
                                    <label style="display:inline-flex; align-items:center; gap:8px;">
                                        <input type="checkbox" id="benchmarkVerboseCheckbox">
                                        verbose (-v)
                                    </label>
                                    <label style="display:inline-flex; align-items:center; gap:8px; margin-left: 18px;">
                                        <input type="checkbox" id="benchmarkProgressCheckbox">
                                        progress (--progress)
                                    </label>
                                </div>

                                <div class="form-group">
                                    <label class="form-label">提示长度 (-p, --n-prompt)</label>
                                    <input type="text" class="form-control" id="benchmarkInputNPrompt" value="512" placeholder="例如: 512 或 512,1024 或 0-4096+512">
                                    <small class="form-text">支持逗号、范围与步长（参考 README-beanch.md）</small>
                                </div>

                                <div class="form-group">
                                    <label class="form-label">生成长度 (-n, --n-gen)</label>
                                    <input type="text" class="form-control" id="benchmarkInputNGen" value="128" placeholder="例如: 128 或 128,256">
                                </div>

                                <div class="form-group">
                                    <label class="form-label">Prompt+生成 (-pg)</label>
                                    <input type="text" class="form-control" id="benchmarkInputPg" placeholder="例如: 512,128 或 256,64">
                                </div>

                                <div class="form-group">
                                    <label class="form-label">预填深度 (-d, --n-depth)</label>
                                    <input type="text" class="form-control" id="benchmarkDepthInput" value="0" placeholder="例如: 0 或 512 或 0,512">
                                </div>

                                <div class="form-group">
                                    <label class="form-label">批量 (-b, --batch-size)</label>
                                    <input type="text" class="form-control" id="benchmarkInputBatchSize" value="2048" placeholder="例如: 128,256,512,1024">
                                </div>

                                <div class="form-group">
                                    <label class="form-label">子批 (-ub, --ubatch-size)</label>
                                    <input type="text" class="form-control" id="benchmarkInputUBatchSize" value="512" placeholder="例如: 512">
                                </div>

                                <div class="form-group">
                                    <label class="form-label">缓存类型 K (-ctk)</label>
                                    <input type="text" class="form-control" id="benchmarkCacheTypeKInput" value="f16" placeholder="例如: f16">
                                </div>

                                <div class="form-group">
                                    <label class="form-label">缓存类型 V (-ctv)</label>
                                    <input type="text" class="form-control" id="benchmarkCacheTypeVInput" value="f16" placeholder="例如: f16">
                                </div>

                                <div class="form-group">
                                    <label class="form-label">线程 (-t, --threads)</label>
                                    <input type="text" class="form-control" id="benchmarkInputThreads" placeholder="例如: 8 或 4,8,16">
                                </div>

                                <div class="form-group">
                                    <label class="form-label">CPU Mask (-C)</label>
                                    <input type="text" class="form-control" id="benchmarkCpuMaskInput" placeholder="例如: 0x0 或 0xff,0xff00">
                                </div>

                                <div class="form-group">
                                    <label class="form-label" for="benchmarkCpuStrictSelect">CPU Strict (--cpu-strict)</label>
                                    <select class="form-control" id="benchmarkCpuStrictSelect">
                                        <option value="">默认</option>
                                        <option value="0">0</option>
                                        <option value="1">1</option>
                                    </select>
                                </div>

                                <div class="form-group">
                                    <label class="form-label">Poll (--poll)</label>
                                    <input type="number" class="form-control" id="benchmarkPollInput" min="0" max="100" step="1" value="50">
                                </div>

                                <div class="form-group">
                                    <label class="form-label">GPU Layers (-ngl)</label>
                                    <input type="text" class="form-control" id="benchmarkGpuLayersInput" value="99" placeholder="例如: 0 或 99">
                                </div>

                                <div class="form-group">
                                    <label class="form-label">CPU MOE (-ncmoe)</label>
                                    <input type="text" class="form-control" id="benchmarkCpuMoeInput" value="0" placeholder="例如: 0">
                                </div>

                                <div class="form-group">
                                    <label class="form-label" for="benchmarkSplitModeSelect">Split Mode (-sm)</label>
                                    <select class="form-control" id="benchmarkSplitModeSelect">
                                        <option value="">默认</option>
                                        <option value="none">none</option>
                                        <option value="layer">layer</option>
                                        <option value="row">row</option>
                                    </select>
                                </div>

                                <div class="form-group">
                                    <label class="form-label">主 GPU (-mg)</label>
                                    <input type="text" class="form-control" id="benchmarkMainGpuInput" placeholder="例如: 0">
                                </div>

                                <div class="form-group">
                                    <label class="form-label" for="benchmarkNoKvSelect">禁用 KV Offload (-nkvo)</label>
                                    <select class="form-control" id="benchmarkNoKvSelect">
                                        <option value="">默认</option>
                                        <option value="0">0</option>
                                        <option value="1">1</option>
                                    </select>
                                </div>

                                <div class="form-group">
                                    <label class="form-label" for="benchmarkFlashAttnSelect">Flash Attention (-fa)</label>
                                    <select class="form-control" id="benchmarkFlashAttnSelect">
                                        <option value="">默认</option>
                                        <option value="0">0</option>
                                        <option value="1">1</option>
                                    </select>
                                </div>

                                <div class="form-group">
                                    <label class="form-label" for="benchmarkMmapSelect">内存映射 (-mmp, --mmap)</label>
                                    <select class="form-control" id="benchmarkMmapSelect">
                                        <option value="">默认</option>
                                        <option value="0">0</option>
                                        <option value="1">1</option>
                                    </select>
                                </div>

                                <div class="form-group">
                                    <label class="form-label" for="benchmarkEmbeddingsSelect">Embeddings (-embd)</label>
                                    <select class="form-control" id="benchmarkEmbeddingsSelect">
                                        <option value="">默认</option>
                                        <option value="0">0</option>
                                        <option value="1">1</option>
                                    </select>
                                </div>

                                <div class="form-group">
                                    <label class="form-label">Tensor Split (-ts)</label>
                                    <input type="text" class="form-control" id="benchmarkTensorSplitInput" placeholder="例如: 0 或 0.00 或 0.5/0.5">
                                </div>

                                <div class="form-group">
                                    <label class="form-label">额外参数</label>
                                    <textarea class="form-control" id="benchmarkInputExtraParams" rows="2" placeholder="例如: -ot layer.*=f16 -nopo 1"></textarea>
                                    <small class="form-text">不在表单中的参数可写在这里，用空格分隔</small>
                                </div>
                            </div>
                        </div>
                    </form>
                </div>
                <div class="modal-footer">
                    <button class="btn btn-secondary" onclick="closeModal('${modalId}')">取消</button>
                    <button class="btn btn-secondary" id="benchmarkResetBtn" onclick="resetModelBenchmarkForm()">重置</button>
                    <button class="btn btn-primary" id="benchmarkRunBtn" onclick="submitModelBenchmark()">开始测试</button>
                </div>
            </div>
        `;
        const root = document.getElementById('dynamicModalRoot') || document.body;
        root.appendChild(modal);
    }

    window.__benchmarkModelId = modelId;
    window.__benchmarkModelName = modelName;

    const nameEl = document.getElementById('benchmarkModelName');
    if (nameEl) nameEl.textContent = modelName || modelId;

    const enableDeviceSelectEl = document.getElementById('benchmarkEnableDeviceSelect');
    const deviceListEl = document.getElementById('benchmarkDeviceChecklist');
    if (enableDeviceSelectEl && deviceListEl) {
        enableDeviceSelectEl.checked = false;
        enableDeviceSelectEl.onchange = () => {
            const enabled = !!enableDeviceSelectEl.checked;
            Array.from(deviceListEl.querySelectorAll('input[type="checkbox"][data-device-value]')).forEach(cb => {
                cb.disabled = !enabled;
            });
        };
    }

    resetModelBenchmarkForm();

    const binSelect = document.getElementById('benchmarkLlamaBinPathSelect');
    if (!binSelect) {
        modal.classList.add('show');
        return;
    }

    binSelect.onchange = () => loadBenchmarkDevices(binSelect.value);

    binSelect.innerHTML = '<option value="">加载中...</option>';
    fetch('/api/llamacpp/list')
        .then(r => r.json())
        .then(listData => {
            const paths = (listData && listData.success && listData.data) ? (listData.data.paths || []) : [];
            if (!paths.length) {
                binSelect.innerHTML = '<option value="">未配置路径</option>';
                loadBenchmarkDevices('');
                return;
            }
            binSelect.innerHTML = paths.map(p => `<option value="${p}">${p}</option>`).join('');
            binSelect.value = paths[0];
            loadBenchmarkDevices(paths[0]);
        })
        .catch(() => {
            binSelect.innerHTML = '<option value="">加载失败</option>';
            loadBenchmarkDevices('');
        })
        .finally(() => {
            modal.classList.add('show');
        });
}

function resetModelBenchmarkForm() {
    const form = document.getElementById('modelBenchmarkForm');
    if (form) form.reset();

    const rep = document.getElementById('benchmarkInputRepetitions');
    if (rep) rep.value = '5';

    const output = document.getElementById('benchmarkOutputSelect');
    if (output) output.value = 'md';

    const outputErr = document.getElementById('benchmarkOutputErrSelect');
    if (outputErr) outputErr.value = '';

    const prio = document.getElementById('benchmarkPrioSelect');
    if (prio) prio.value = '0';

    const delay = document.getElementById('benchmarkDelayInput');
    if (delay) delay.value = '0';

    const poll = document.getElementById('benchmarkPollInput');
    if (poll) poll.value = '50';

    const p = document.getElementById('benchmarkInputNPrompt');
    if (p) p.value = '512';

    const n = document.getElementById('benchmarkInputNGen');
    if (n) n.value = '128';

    const batch = document.getElementById('benchmarkInputBatchSize');
    if (batch) batch.value = '2048';

    const ubatch = document.getElementById('benchmarkInputUBatchSize');
    if (ubatch) ubatch.value = '512';

    const ctk = document.getElementById('benchmarkCacheTypeKInput');
    if (ctk) ctk.value = 'f16';

    const ctv = document.getElementById('benchmarkCacheTypeVInput');
    if (ctv) ctv.value = 'f16';

    const depth = document.getElementById('benchmarkDepthInput');
    if (depth) depth.value = '0';

    const ngl = document.getElementById('benchmarkGpuLayersInput');
    if (ngl) ngl.value = '99';

    const ncmoe = document.getElementById('benchmarkCpuMoeInput');
    if (ncmoe) ncmoe.value = '0';

    const enableDeviceSelectEl = document.getElementById('benchmarkEnableDeviceSelect');
    if (enableDeviceSelectEl) enableDeviceSelectEl.checked = false;

    const deviceListEl = document.getElementById('benchmarkDeviceChecklist');
    if (deviceListEl) {
        Array.from(deviceListEl.querySelectorAll('input[type="checkbox"][data-device-value]')).forEach(cb => {
            cb.checked = false;
            cb.disabled = true;
        });
    }
}

function loadBenchmarkDevices(llamaBinPath) {
    const listEl = document.getElementById('benchmarkDeviceChecklist');
    if (!listEl) return;

    if (!llamaBinPath) {
        listEl.innerHTML = '<div class="settings-empty">请先选择 Llama.cpp 版本</div>';
        return;
    }

    listEl.innerHTML = '<div class="settings-empty">加载中...</div>';
    fetch('/api/model/device/list?llamaBinPath=' + encodeURIComponent(llamaBinPath))
        .then(r => r.json())
        .then(d => {
            if (!d || !d.success) {
                listEl.innerHTML = '<div class="settings-empty">加载失败</div>';
                return;
            }
            const devices = (d.data && d.data.devices) ? d.data.devices : [];
            if (!devices.length) {
                listEl.innerHTML = '<div class="settings-empty">未发现可用设备</div>';
                return;
            }
            const enabled = !!(document.getElementById('benchmarkEnableDeviceSelect') && document.getElementById('benchmarkEnableDeviceSelect').checked);
            const html = devices.map((raw, idx) => {
                const line = (raw == null) ? '' : String(raw);
                const trimmed = line.trim();
                let value = trimmed.split(/\s+/)[0] || '';
                if (value.endsWith(':')) value = value.slice(0, -1);
                if (!value) value = String(idx);
                const safeId = 'benchmarkDevice_' + idx;
                return `
                    <label style="display:flex; align-items:flex-start; gap:10px; padding:6px 4px;">
                        <input type="checkbox" id="${safeId}" data-device-value="${value}" ${enabled ? '' : 'disabled'}>
                        <span style="font-size: 13px; color: var(--text-secondary);">${trimmed || value}</span>
                    </label>
                `;
            }).join('');
            listEl.innerHTML = html;
        })
        .catch(() => {
            listEl.innerHTML = '<div class="settings-empty">加载失败</div>';
        });
}

function submitModelBenchmark() {
    const modelId = window.__benchmarkModelId;
    const modelName = window.__benchmarkModelName;
    if (!modelId) {
        showToast('错误', '未选择模型', 'error');
        return;
    }

    const repInput = document.getElementById('benchmarkInputRepetitions');
    const outputSelect = document.getElementById('benchmarkOutputSelect');
    const outputErrSelect = document.getElementById('benchmarkOutputErrSelect');
    const numaSelect = document.getElementById('benchmarkNumaSelect');
    const prioSelect = document.getElementById('benchmarkPrioSelect');
    const delayInput = document.getElementById('benchmarkDelayInput');
    const verboseCheckbox = document.getElementById('benchmarkVerboseCheckbox');
    const progressCheckbox = document.getElementById('benchmarkProgressCheckbox');

    const nPromptInput = document.getElementById('benchmarkInputNPrompt');
    const nGenInput = document.getElementById('benchmarkInputNGen');
    const pgInput = document.getElementById('benchmarkInputPg');
    const depthInput = document.getElementById('benchmarkDepthInput');
    const batchInput = document.getElementById('benchmarkInputBatchSize');
    const ubatchInput = document.getElementById('benchmarkInputUBatchSize');
    const cacheTypeKInput = document.getElementById('benchmarkCacheTypeKInput');
    const cacheTypeVInput = document.getElementById('benchmarkCacheTypeVInput');
    const threadsInput = document.getElementById('benchmarkInputThreads');
    const cpuMaskInput = document.getElementById('benchmarkCpuMaskInput');
    const cpuStrictSelect = document.getElementById('benchmarkCpuStrictSelect');
    const pollInput = document.getElementById('benchmarkPollInput');
    const nglInput = document.getElementById('benchmarkGpuLayersInput');
    const ncmoeInput = document.getElementById('benchmarkCpuMoeInput');
    const splitModeSelect = document.getElementById('benchmarkSplitModeSelect');
    const mainGpuInput = document.getElementById('benchmarkMainGpuInput');
    const noKvSelect = document.getElementById('benchmarkNoKvSelect');
    const flashAttnSelect = document.getElementById('benchmarkFlashAttnSelect');
    const mmapSelect = document.getElementById('benchmarkMmapSelect');
    const embeddingsSelect = document.getElementById('benchmarkEmbeddingsSelect');
    const tensorSplitInput = document.getElementById('benchmarkTensorSplitInput');
    const rpcInput = document.getElementById('benchmarkRpcInput');
    const extraInput = document.getElementById('benchmarkInputExtraParams');

    const btn = document.getElementById('benchmarkRunBtn');
    const binSelect = document.getElementById('benchmarkLlamaBinPathSelect');

    let repetitions = repInput ? parseInt(repInput.value, 10) : 3;
    const output = outputSelect ? outputSelect.value.trim() : '';
    const outputErr = outputErrSelect ? outputErrSelect.value.trim() : '';
    const numa = numaSelect ? numaSelect.value.trim() : '';
    const prio = prioSelect ? prioSelect.value.trim() : '';
    const delay = delayInput ? delayInput.value.trim() : '';
    const verbose = !!(verboseCheckbox && verboseCheckbox.checked);
    const progress = !!(progressCheckbox && progressCheckbox.checked);

    const p = nPromptInput ? nPromptInput.value.trim() : '';
    const n = nGenInput ? nGenInput.value.trim() : '';
    const pg = pgInput ? pgInput.value.trim() : '';
    const depth = depthInput ? depthInput.value.trim() : '';
    const batchSize = batchInput ? batchInput.value.trim() : '';
    const ubatchSize = ubatchInput ? ubatchInput.value.trim() : '';
    const cacheTypeK = cacheTypeKInput ? cacheTypeKInput.value.trim() : '';
    const cacheTypeV = cacheTypeVInput ? cacheTypeVInput.value.trim() : '';
    const t = threadsInput ? threadsInput.value.trim() : '';
    const cpuMask = cpuMaskInput ? cpuMaskInput.value.trim() : '';
    const cpuStrict = cpuStrictSelect ? cpuStrictSelect.value.trim() : '';
    const poll = pollInput ? pollInput.value.trim() : '';
    const ngl = nglInput ? nglInput.value.trim() : '';
    const ncmoe = ncmoeInput ? ncmoeInput.value.trim() : '';
    const splitMode = splitModeSelect ? splitModeSelect.value.trim() : '';
    const mg = mainGpuInput ? mainGpuInput.value.trim() : '';
    const nkvo = noKvSelect ? noKvSelect.value.trim() : '';
    const fa = flashAttnSelect ? flashAttnSelect.value.trim() : '';
    const mmp = mmapSelect ? mmapSelect.value.trim() : '';
    const embd = embeddingsSelect ? embeddingsSelect.value.trim() : '';
    const tensorSplit = tensorSplitInput ? tensorSplitInput.value.trim() : '';
    const rpc = rpcInput ? rpcInput.value.trim() : '';
    const extraParams = extraInput ? extraInput.value.trim() : '';
    const llamaBinPath = binSelect ? (binSelect.value || '').trim() : '';

    if (isNaN(repetitions) || repetitions <= 0) repetitions = 3;
    if (!llamaBinPath) {
        showToast('错误', '请先选择 Llama.cpp 版本路径', 'error');
        return;
    }

    const payload = { modelId: modelId };
    if (repetitions > 0) payload.repetitions = repetitions;
    if (output) payload.output = output;
    if (outputErr) payload.outputErr = outputErr;
    if (numa) payload.numa = numa;
    if (prio) payload.prio = prio;
    if (delay) payload.delay = delay;
    if (verbose) payload.verbose = true;
    if (progress) payload.progress = true;
    if (rpc) payload.rpc = rpc;

    if (p) payload.p = p;
    if (n) payload.n = n;
    if (t) payload.t = t;
    if (batchSize) payload.batchSize = batchSize;
    if (ubatchSize) payload.ubatchSize = ubatchSize;
    if (pg) payload.pg = pg;
    if (depth) payload.depth = depth;
    if (cacheTypeK) payload.cacheTypeK = cacheTypeK;
    if (cacheTypeV) payload.cacheTypeV = cacheTypeV;
    if (cpuMask) payload.cpuMask = cpuMask;
    if (cpuStrict) payload.cpuStrict = cpuStrict;
    if (poll) payload.poll = poll;
    if (ngl) payload.ngl = ngl;
    if (ncmoe) payload.ncmoe = ncmoe;
    if (splitMode) payload.splitMode = splitMode;
    if (mg) payload.mg = mg;
    if (nkvo) payload.nkvo = nkvo;
    if (fa) payload.fa = fa;
    if (mmp) payload.mmp = mmp;
    if (embd) payload.embd = embd;
    if (tensorSplit) payload.tensorSplit = tensorSplit;
    if (extraParams) payload.extraParams = extraParams;
    const enableDeviceSelectEl = document.getElementById('benchmarkEnableDeviceSelect');
    const listEl = document.getElementById('benchmarkDeviceChecklist');
    if (enableDeviceSelectEl && enableDeviceSelectEl.checked && listEl) {
        const values = Array.from(listEl.querySelectorAll('input[type="checkbox"][data-device-value]'))
            .filter(cb => cb.checked)
            .map(cb => cb.getAttribute('data-device-value'))
            .filter(v => v && v.trim().length > 0);
        if (values.length) payload.device = values.join('/');
    }
    payload.llamaBinPath = llamaBinPath;

    if (btn) {
        btn.disabled = true;
        btn.innerHTML = '测试中...';
    }
    showToast('提示', '已开始模型测试', 'info');

    fetch('/api/models/benchmark', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
        .then(r => r.json())
        .then(res => {
            if (!res || !res.success) {
                const message = res && res.error ? res.error : '模型测试失败';
                showToast('错误', message, 'error');
            } else {
                const data = res.data || {};
                closeModal('modelBenchmarkModal');
                showModelBenchmarkResultModal(modelId, modelName, data);
            }
        })
        .catch(() => {
            showToast('错误', '网络请求失败', 'error');
        })
        .then(() => {
            if (btn) {
                btn.disabled = false;
                btn.innerHTML = '开始测试';
            }
        });
}

function openModelBenchmarkList(modelId, modelName) {
    const modalId = 'modelBenchmarkCompareModal';
    let modal = document.getElementById(modalId);
    if (!modal) {
        modal = document.createElement('div');
        modal.id = modalId;
        modal.className = 'modal';
        modal.innerHTML = `
            <div class="modal-content" style="min-width: 70vw; max-width: 95vw;">
                <div class="modal-header">
                    <h3 class="modal-title"><i class="fas fa-file-alt"></i> 模型测试结果对比</h3>
                    <button class="modal-close" onclick="closeModal('${modalId}')">&times;</button>
                </div>
                <div class="modal-body">
                    <div style="display:flex; gap:16px; height:60vh;">
                        <div style="width:32%; border:1px solid #e5e7eb; border-radius:0.75rem; overflow:hidden; background:#f9fafb;">
                            <div style="padding:8px 10px; border-bottom:1px solid #e5e7eb; font-size:13px; color:#374151;">测试结果文件</div>
                            <div id="${modalId}List" style="max-height:calc(60vh - 36px); overflow:auto; font-size:13px; color:#374151;">加载中...</div>
                        </div>
                        <div style="flex:1; display:flex; flex-direction:column; min-width:0;">
                            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
                                <div id="${modalId}ModelInfo" style="font-size:14px; color:#374151;"></div>
                                <div>
                                    <button class="btn btn-secondary" style="padding:4px 10px; font-size:12px;" onclick="clearBenchmarkResultContent()">清空内容</button>
                                </div>
                            </div>
                            <pre id="${modalId}Content" style="flex:1; max-height:calc(60vh - 36px); overflow:auto; font-size:13px; background:#111827; color:#e5e7eb; padding:10px; border-radius:0.75rem;"></pre>
                        </div>
                    </div>
                </div>
                <div class="modal-footer">
                    <button class="btn btn-secondary" onclick="closeModal('${modalId}')">关闭</button>
                </div>
            </div>`;
        document.body.appendChild(modal);
    }
    window.__benchmarkModelId = modelId;
    window.__benchmarkModelName = modelName;
    const listEl = document.getElementById(modalId + 'List');
    const modelInfoEl = document.getElementById(modalId + 'ModelInfo');
    if (modelInfoEl) {
        const name = modelName || modelId;
        modelInfoEl.textContent = name ? '当前模型: ' + name : '';
    }
    if (listEl) listEl.innerHTML = '加载中...';
    modal.classList.add('show');
    fetch('/api/models/benchmark/list?modelId=' + encodeURIComponent(modelId))
        .then(r => r.json())
        .then(d => {
            if (!d.success) {
                showToast('错误', d.error || '获取测试结果列表失败', 'error');
                return;
            }
            const files = (d.data && d.data.files) ? d.data.files : [];
            if (!files.length) {
                listEl.innerHTML = '<div style="color:#666; padding:8px 10px;">未找到测试结果文件</div>';
                return;
            }
            let html = '<div style="border-top:1px solid #e5e7eb;">';
            files.forEach(item => {
                const fn = typeof item === 'string' ? item : (item && item.name) ? item.name : '';
                const size = (item && typeof item === 'object' && item.size != null) ? item.size : null;
                const modified = (item && typeof item === 'object' && item.modified) ? item.modified : '';
                const sizeText = size != null ? (typeof formatFileSize === 'function' ? formatFileSize(size) : (size + ' B')) : '';
                html += `
                    <div class="list-row" style="display:flex; justify-content:space-between; align-items:center; padding:8px 10px; border-bottom:1px solid #e5e7eb; background:#f9fafb;">
                        <div style="display:flex; flex-direction:column; gap:4px; max-width:65%;">
                            <span style="word-break:break-all;"><i class="fas fa-file-alt" style="margin-right:6px;"></i>${fn}</span>
                            <span style="color:#6b7280; font-size:12px;">修改时间: ${modified || '-'}</span>
                            <span style="color:#6b7280; font-size:12px;">大小: ${sizeText || '-'}</span>
                        </div>
                        <div style="display:flex; flex-direction:column; gap:4px; align-items:flex-end;">
                            <button class="btn btn-primary" style="padding:2px 10px; font-size:12px;" onclick="loadBenchmarkResult(this.dataset.fn)" data-fn="${fn}">追加</button>
                            <button class="btn btn-secondary" style="padding:2px 10px; font-size:12px;" onclick="deleteBenchmarkResult(this.dataset.fn, this)" data-fn="${fn}">删除</button>
                        </div>
                    </div>`;
            });
            html += '</div>';
            listEl.innerHTML = html;
        }).catch(() => {
            showToast('错误', '网络错误，获取测试结果列表失败', 'error');
        });
}

function deleteBenchmarkResult(fileName, btn) {
    if (!fileName) {
        showToast('错误', '无效的文件名', 'error');
        return;
    }
    if (!confirm('确定要删除该测试结果文件吗？')) {
        return;
    }
    btn.disabled = true;
    fetch('/api/models/benchmark/delete?fileName=' + encodeURIComponent(fileName), {
        method: 'POST'
    }).then(r => r.json()).then(d => {
        if (!d.success) {
            showToast('错误', d.error || '删除测试结果失败', 'error');
            btn.disabled = false;
            return;
        }
        const row = btn.closest('.list-row');
        if (row && row.parentElement) {
            row.parentElement.removeChild(row);
        }
        showToast('成功', '测试结果已删除', 'success');
    }).catch(() => {
        showToast('错误', '网络错误，删除测试结果失败', 'error');
        btn.disabled = false;
    });
}

function clearBenchmarkResultContent() {
    const modalId = 'modelBenchmarkCompareModal';
    const contentEl = document.getElementById(modalId + 'Content');
    if (contentEl) {
        contentEl.textContent = '';
    }
}

function appendBenchmarkResultBlock(modelId, modelName, data) {
    const modalId = 'modelBenchmarkCompareModal';
    const contentEl = document.getElementById(modalId + 'Content');
    if (!contentEl) {
        return;
    }
    const name = modelName || modelId || '';
    const d = data || {};
    let text = '';
    const existing = contentEl.textContent || '';
    if (existing.trim().length > 0) {
        text += '\n\n';
    }
    const fileName = d.fileName || '';
    text += '==============================\n';
    if (fileName) {
        text += '文件: ' + fileName + '\n';
    }
    if (name) {
        text += '模型: ' + name + '\n';
    }
    if (d.modelId || modelId) {
        text += '模型ID: ' + (d.modelId || modelId || '') + '\n';
    }
    if (d.commandStr) {
        text += '\n命令:\n' + d.commandStr + '\n';
    } else if (d.command && d.command.length) {
        text += '\n命令:\n' + d.command.join(' ') + '\n';
    }
    if (d.exitCode != null) {
        text += '\n退出码: ' + d.exitCode + '\n';
    }
    if (d.savedPath) {
        text += '\n保存文件: ' + d.savedPath + '\n';
    }
    if (d.rawOutput) {
        text += '\n原始输出:\n' + d.rawOutput + '\n';
    }
    contentEl.textContent += text;
}

function loadBenchmarkResult(fileName) {
    const modelId = window.__benchmarkModelId;
    const modelName = window.__benchmarkModelName;
    if (!fileName) {
        showToast('错误', '无效的文件名', 'error');
        return;
    }
    fetch('/api/models/benchmark/get?fileName=' + encodeURIComponent(fileName))
        .then(r => r.json())
        .then(d => {
            if (!d.success) {
                showToast('错误', d.error || '读取测试结果失败', 'error');
                return;
            }
            const data = d.data || {};
            appendBenchmarkResultBlock(modelId, modelName, data);
        }).catch(() => {
            showToast('错误', '网络错误，读取测试结果失败', 'error');
        });
}

function showModelBenchmarkResultModal(modelId, modelName, data) {
    openModelBenchmarkList(modelId, modelName);
    appendBenchmarkResultBlock(modelId, modelName, data);
}
