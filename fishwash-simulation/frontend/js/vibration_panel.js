var VibrationPanel = (function () {
  var API_BASE = 'http://localhost:8080';

  function VibrationPanel() {
    this.vibrationChart = null;
    this.sprayChart = null;
    this.shapeCompareChart = null;
    this.crossEraRadarChart = null;
    this.frictionCurveChart = null;
    this.selectedDevice = null;
    this.selectedDeviceMeta = null;
    this.alertCount = 0;
    this.wsClient = null;
    this.onDeviceChange = null;
    this.onSimulate = null;
    this.onFrequencyChange = null;
    this.onSprayHeightChange = null;
    this.onAmplitudeChange = null;
    this.onModeChange = null;
    this.onShapeChange = null;
    this.onInteractiveFrictionToggle = null;
    this.basin3dRef = null;
    this.currentTab = 'simulation';
  }

  VibrationPanel.prototype.init = function () {
    this.initDeviceSelector();
    this.initCharts();
    this.initWebSocket();
    this.bindControls();
    this.initTabs();
    this.bindShapeControls();
    this.bindAmplitudeControls();
    this.bindInteractiveFriction();
    this.bindShapeComparison();
    this.bindCrossEraComparison();
    this.bindFrictionAnalysis();
  };

  VibrationPanel.prototype.setBasin3dRef = function (ref) {
    this.basin3dRef = ref;
  };

  VibrationPanel.prototype.initDeviceSelector = function () {
    var self = this;
    var selector = document.getElementById('deviceSelector');
    if (!selector) return;

    fetch(API_BASE + '/api/devices')
      .then(function (response) { return response.json(); })
      .then(function (result) {
        var devices = result.data || result;
        selector.innerHTML = '<option value="">-- 请选择设备 --</option>';
        devices.forEach(function (device) {
          var option = document.createElement('option');
          option.value = device.id;
          option.textContent = device.deviceName || device.name || device.id;
          option._meta = device;
          selector.appendChild(option);
        });
      })
      .catch(function (err) {
        console.error('Failed to fetch devices:', err);
      });

    selector.addEventListener('change', function (e) {
      self.selectedDevice = e.target.value;
      if (self.selectedDevice) {
        var selectedOption = e.target.selectedOptions[0];
        if (selectedOption && selectedOption._meta) {
          self.selectedDeviceMeta = selectedOption._meta;
          var shape = (self.selectedDeviceMeta.basinShape || 'CIRCLE').toUpperCase();
          self.syncShapeRadio(shape);
        }
        self.fetchLatestData(self.selectedDevice);
        self.fetchVibrationModes(self.selectedDevice);
        if (typeof self.onDeviceChange === 'function') {
          self.onDeviceChange(self.selectedDevice, self.selectedDeviceMeta);
        }
      }
    });
  };

  VibrationPanel.prototype.syncShapeRadio = function (shape) {
    var radios = document.querySelectorAll('input[name="shapeSelector"]');
    if (!radios) return;
    radios.forEach(function (r) {
      r.checked = (r.value === shape);
    });
  };

  VibrationPanel.prototype.updateSensorDisplay = function (data) {
    if (!data) return;
    var fields = [
      { id: 'frictionFreq', key: 'frictionFreq', unit: 'Hz' },
      { id: 'amplitude', key: 'amplitude', unit: 'mm' },
      { id: 'sprayHeight', key: 'sprayHeight', unit: 'cm' },
      { id: 'waterTemp', key: 'waterTemp', unit: '°C' }
    ];
    var self = this;
    fields.forEach(function (field) {
      var el = document.getElementById(field.id);
      if (el) {
        var val = data[field.key];
        el.textContent = val !== undefined && val !== null ? self.formatValue(val, field.unit) : '--';
      }
    });
  };

  VibrationPanel.prototype.initCharts = function () {
    var self = this;
    var vibrationCtx = document.getElementById('vibrationChart');
    if (vibrationCtx) {
      this.vibrationChart = new Chart(vibrationCtx.getContext('2d'), {
        type: 'bar',
        data: { labels: [], datasets: [{
          label: '共振频率 (Hz)', data: [],
          backgroundColor: 'rgba(139, 105, 20, 0.7)',
          borderColor: 'rgba(139, 105, 20, 1)',
          borderWidth: 1, borderRadius: 4
        }]},
        options: {
          responsive: true, maintainAspectRatio: false,
          plugins: { legend: { display: true, labels: { color: '#B8A080', font: { family: '"Noto Serif SC", "SimSun", serif', size: 11 } } } },
          scales: {
            x: { title: { display: true, text: '模态阶数', color: '#8B7355', font: { family: '"Noto Serif SC", "SimSun", serif', size: 12 } }, ticks: { color: '#8B7355' }, grid: { color: 'rgba(139, 105, 20, 0.1)' } },
            y: { title: { display: true, text: '频率 (Hz)', color: '#8B7355', font: { family: '"Noto Serif SC", "SimSun", serif', size: 12 } }, ticks: { color: '#8B7355' }, grid: { color: 'rgba(139, 105, 20, 0.1)' } }
          }
        }
      });
    }

    var sprayCtx = document.getElementById('sprayChart');
    if (sprayCtx) {
      this.sprayChart = new Chart(sprayCtx.getContext('2d'), {
        type: 'line', data: { labels: [], datasets: [{
          label: '喷水高度 (cm)', data: [],
          borderColor: 'rgba(70, 130, 180, 1)',
          backgroundColor: 'rgba(70, 130, 180, 0.15)',
          borderWidth: 2, fill: true, tension: 0.4, pointRadius: 3,
          pointBackgroundColor: 'rgba(70, 130, 180, 1)'
        }]},
        options: {
          responsive: true, maintainAspectRatio: false,
          plugins: { legend: { display: true, labels: { color: '#B8A080', font: { family: '"Noto Serif SC", "SimSun", serif', size: 11 } } } },
          scales: {
            x: { title: { display: true, text: '时间', color: '#8B7355', font: { family: '"Noto Serif SC", "SimSun", serif', size: 12 } }, ticks: { color: '#8B7355' }, grid: { color: 'rgba(139, 105, 20, 0.1)' } },
            y: { title: { display: true, text: '高度 (cm)', color: '#8B7355', font: { family: '"Noto Serif SC", "SimSun", serif', size: 12 } }, ticks: { color: '#8B7355' }, grid: { color: 'rgba(139, 105, 20, 0.1)' } }
          }
        }
      });
    }

    var shapeCompareCtx = document.getElementById('shapeCompareChart');
    if (shapeCompareCtx) {
      this.shapeCompareChart = new Chart(shapeCompareCtx.getContext('2d'), {
        type: 'bar',
        data: {
          labels: ['2阶', '3阶', '4阶', '5阶', '6阶'],
          datasets: [
            { label: '圆形 (Hz)', data: [], backgroundColor: 'rgba(139, 105, 20, 0.75)', borderColor: 'rgba(139, 105, 20, 1)', borderWidth: 1, borderRadius: 3 },
            { label: '方形 (Hz)', data: [], backgroundColor: 'rgba(70, 130, 180, 0.75)', borderColor: 'rgba(70, 130, 180, 1)', borderWidth: 1, borderRadius: 3 }
          ]
        },
        options: {
          responsive: true, maintainAspectRatio: false,
          plugins: { legend: { display: true, labels: { color: '#B8A080', font: { family: '"Noto Serif SC", "SimSun", serif', size: 11 } } } },
          scales: {
            x: { ticks: { color: '#8B7355' }, grid: { color: 'rgba(139, 105, 20, 0.1)' } },
            y: { title: { display: true, text: '共振频率 (Hz)', color: '#8B7355', font: { family: '"Noto Serif SC", "SimSun", serif', size: 12 } }, ticks: { color: '#8B7355' }, grid: { color: 'rgba(139, 105, 20, 0.1)' } }
          }
        }
      });
    }

    var crossEraRadarCtx = document.getElementById('crossEraRadarChart');
    if (crossEraRadarCtx) {
      this.crossEraRadarChart = new Chart(crossEraRadarCtx, {
        type: 'radar',
        data: {
          labels: ['工作频率', '喷水/喷雾高度', '粒子尺寸', '能量效率', '工艺复杂度', '文化价值'],
          datasets: [
            {
              label: '古代鱼洗', data: [],
              backgroundColor: 'rgba(139, 105, 20, 0.2)',
              borderColor: 'rgba(139, 105, 20, 1)',
              borderWidth: 2, pointBackgroundColor: 'rgba(139, 105, 20, 1)'
            },
            {
              label: '现代超声雾化', data: [],
              backgroundColor: 'rgba(70, 130, 180, 0.2)',
              borderColor: 'rgba(70, 130, 180, 1)',
              borderWidth: 2, pointBackgroundColor: 'rgba(70, 130, 180, 1)'
            }
          ]
        },
        options: {
          responsive: true, maintainAspectRatio: false,
          plugins: { legend: { display: true, labels: { color: '#B8A080', font: { family: '"Noto Serif SC", "SimSun", serif', size: 11 } } } },
          scales: {
            r: {
              angleLines: { color: 'rgba(139, 105, 20, 0.2)' },
              grid: { color: 'rgba(139, 105, 20, 0.15)' },
              pointLabels: { color: '#B8A080', font: { family: '"Noto Serif SC", "SimSun", serif', size: 11 } },
              suggestedMin: 0, suggestedMax: 100,
              ticks: { color: '#8B7355', backdropColor: 'transparent' }
            }
          }
        }
      });
    }

    var frictionCurveCtx = document.getElementById('frictionCurveChart');
    if (frictionCurveCtx) {
      this.frictionCurveChart = new Chart(frictionCurveCtx.getContext('2d'), {
        type: 'line',
        data: { labels: [], datasets: [{
          label: 'Stribeck曲线 μ-v', data: [],
          borderColor: 'rgba(255, 215, 0, 1)',
          backgroundColor: 'rgba(255, 215, 0, 0.15)',
          borderWidth: 2, fill: true, tension: 0.35,
          pointRadius: 3,
          pointBackgroundColor: 'rgba(255, 215, 0, 1)'
        }]},
        options: {
          responsive: true, maintainAspectRatio: false,
          plugins: { legend: { display: true, labels: { color: '#B8A080', font: { family: '"Noto Serif SC", "SimSun", serif', size: 11 } } } },
          scales: {
            x: { title: { display: true, text: '滑动速度 (m/s)', color: '#8B7355', font: { family: '"Noto Serif SC", "SimSun", serif', size: 12 } }, ticks: { color: '#8B7355' }, grid: { color: 'rgba(139, 105, 20, 0.1)' } },
            y: { title: { display: true, text: '有效摩擦系数 μ_eff', color: '#8B7355', font: { family: '"Noto Serif SC", "SimSun", serif', size: 12 } }, ticks: { color: '#8B7355' }, grid: { color: 'rgba(139, 105, 20, 0.1)' } }
          }
        }
      });
    }
  };

  VibrationPanel.prototype.updateVibrationChart = function (modes) {
    if (!this.vibrationChart || !modes) return;
    var labels = modes.map(function (m) { return m.modeOrder + '阶'; });
    var data = modes.map(function (m) { return m.resonanceFreq; });
    this.vibrationChart.data.labels = labels;
    this.vibrationChart.data.datasets[0].data = data;
    this.vibrationChart.update();
  };

  VibrationPanel.prototype.updateSprayChart = function (analyses) {
    if (!this.sprayChart || !analyses) return;
    var labels = analyses.map(function (a) {
      if (a.analyzedAt || a.timestamp) {
        var d = new Date(a.analyzedAt || a.timestamp);
        return d.getHours().toString().padStart(2, '0') + ':' +
               d.getMinutes().toString().padStart(2, '0') + ':' +
               d.getSeconds().toString().padStart(2, '0');
      }
      return '';
    });
    var data = analyses.map(function (a) { return a.predictedSprayHeight || a.sprayHeight; });
    this.sprayChart.data.labels = labels;
    this.sprayChart.data.datasets[0].data = data;
    this.sprayChart.update();
  };

  VibrationPanel.prototype.showAlert = function (alert) {
    var alertList = document.getElementById('alertList');
    if (!alertList) return;
    var emptyMsg = alertList.querySelector('.alert-empty');
    if (emptyMsg) {
      emptyMsg.remove();
    }

    var item = document.createElement('div');
    item.className = 'alert-item ' + (alert.alertLevel || alert.level || 'INFO');

    var time = document.createElement('div');
    time.className = 'alert-item-time';
    time.textContent = new Date(alert.createdAt || alert.timestamp || Date.now()).toLocaleString('zh-CN');

    var message = document.createElement('div');
    message.className = 'alert-item-message';
    message.textContent = alert.alertMessage || alert.message || alert.content || '';

    item.appendChild(time);
    item.appendChild(message);
    alertList.insertBefore(item, alertList.firstChild);

    this.alertCount++;
    this.updateAlertIndicator(alert.alertLevel || alert.level);
  };

  VibrationPanel.prototype.updateAlertIndicator = function (level) {
    var indicator = document.getElementById('alertIndicator');
    if (!indicator) return;
    var alertText = indicator.querySelector('.alert-text');
    if (level === 'CRITICAL' || level === 'WARNING') {
      indicator.classList.add('has-alert');
      if (alertText) {
        alertText.textContent = level === 'CRITICAL' ? '严重告警' : '警告';
      }
    }
  };

  VibrationPanel.prototype.initWebSocket = function () {
    var self = this;
    this.wsClient = {
      stompClient: null, connected: false,
      connect: function () {
        var client = this;
        try {
          var socket = new SockJS(API_BASE + '/ws');
          client.stompClient = Stomp.over(socket);
          client.stompClient.connect({}, function () {
            client.connected = true;
            client.updateConnectionStatus(true);
            client.stompClient.subscribe('/topic/alerts', function (message) {
              var alert = JSON.parse(message.body);
              self.showAlert(alert);
            });
          }, function () {
            client.connected = false;
            client.updateConnectionStatus(false);
          });
        } catch (e) {
          console.log('WebSocket not available');
        }
      },
      disconnect: function () {
        if (this.stompClient) { this.stompClient.disconnect(); }
        this.connected = false;
        this.updateConnectionStatus(false);
      },
      updateConnectionStatus: function (connected) {
        var statusEl = document.getElementById('connectionStatus');
        if (!statusEl) return;
        var dot = statusEl.querySelector('.status-dot');
        var text = statusEl.querySelector('span:last-child') || statusEl;
        if (connected) { if (dot) dot.classList.add('connected'); if (text) text.textContent = '已连接'; }
        else { if (dot) dot.classList.remove('connected'); if (text) text.textContent = '未连接'; }
      }
    };
    this.wsClient.connect();
  };

  VibrationPanel.prototype.initTabs = function () {
    var self = this;
    var tabBtns = document.querySelectorAll('.tab-btn');
    var tabPanes = document.querySelectorAll('.tab-pane');

    tabBtns.forEach(function (btn) {
      btn.addEventListener('click', function () {
        var tab = btn.getAttribute('data-tab');
        tabBtns.forEach(function (b) { b.classList.remove('tab-active'); });
        tabPanes.forEach(function (p) { p.classList.remove('tab-active'); });
        btn.classList.add('tab-active');
        var targetPane = document.querySelector('.tab-pane[data-tab="' + tab + '"]');
        if (targetPane) targetPane.classList.add('tab-active');
        self.currentTab = tab;
      });
    });
  };

  VibrationPanel.prototype.bindShapeControls = function () {
    var self = this;
    var radios = document.querySelectorAll('input[name="shapeSelector"]');
    radios.forEach(function (radio) {
      radio.addEventListener('change', function (e) {
        var shape = e.target.value;
        if (typeof self.onShapeChange === 'function') {
          self.onShapeChange(shape);
        }
      });
    });
  };

  VibrationPanel.prototype.bindAmplitudeControls = function () {
    var self = this;

    var ampSlider = document.getElementById('amplitudeSlider');
    var ampValue = document.getElementById('amplitudeSliderValue');
    if (ampSlider) {
      ampSlider.addEventListener('input', function (e) {
        var val = parseFloat(e.target.value);
        if (typeof self.onAmplitudeChange === 'function') {
          self.onAmplitudeChange(val);
        }
        if (ampValue) { ampValue.textContent = val.toFixed(2) + ' mm'; }
      });
    }

    var spraySlider = document.getElementById('spraySlider');
    var sprayValue = document.getElementById('spraySliderValue');
    if (spraySlider) {
      spraySlider.addEventListener('input', function (e) {
        var val = parseFloat(e.target.value);
        if (typeof self.onSprayHeightChange === 'function') {
          self.onSprayHeightChange(val);
        }
        if (sprayValue) {
          sprayValue.textContent = val.toFixed(1) + ' cm';
        }
      });
    }

    var modeSelector = document.getElementById('modeOrder');
    if (modeSelector) {
      modeSelector.addEventListener('change', function (e) {
        var order = parseInt(e.target.value);
        if (typeof self.onModeChange === 'function') {
          self.onModeChange(order);
        }
      });
    }
  };

  VibrationPanel.prototype.bindInteractiveFriction = function () {
    var self = this;
    var toggle = document.getElementById('interactiveFrictionToggle');
    var statusEl = document.getElementById('frictionStatus');
    if (!toggle) return;

    toggle.addEventListener('change', function () {
      var enabled = toggle.checked;
      if (statusEl) {
        if (enabled) {
          statusEl.textContent = '✋ 等待摩擦动作...';
          statusEl.classList.add('active');
        } else {
          statusEl.textContent = '交互已关闭';
          statusEl.classList.remove('active');
        }
      }
      if (typeof self.onInteractiveFrictionToggle === 'function') {
        self.onInteractiveFrictionToggle(enabled, function (metrics) {
          self._updateFrictionMetrics(metrics);
        });
      }
    });
  };

  VibrationPanel.prototype._updateFrictionMetrics = function (m) {
    var map = {
      metricVelocity: m && m.velocity !== undefined && m.velocity !== null ? m.velocity.toFixed(3) + ' m/s' : '--',
      metricFreq: m && m.frequency !== undefined && m.frequency !== null ? m.frequency.toFixed(0) + ' Hz' : '--',
      metricMode: m && m.modeOrder ? m.modeOrder + ' 阶模态' : '--',
      metricSpray: m && m.sprayHeight !== undefined && m.sprayHeight !== null ? m.sprayHeight.toFixed(1) + ' cm' : '--',
      metricAmp: m && m.amplitude !== undefined && m.amplitude !== null ? m.amplitude.toFixed(2) + ' mm' : '--'
    };
    Object.keys(map).forEach(function (k) {
      var el = document.getElementById(k);
      if (el) { el.textContent = map[k]; }
    });
    var statusEl = document.getElementById('frictionStatus');
    if (statusEl) {
      if (m && m.isDragging) {
        statusEl.textContent = '🔥 摩擦中 — 能量注入中...';
        statusEl.classList.add('active');
      } else if (!m || !m.isActive) {
        statusEl.textContent = '✋ 等待摩擦动作...';
      }
    }
  };

  VibrationPanel.prototype.bindShapeComparison = function () {
    var self = this;
    var btn = document.getElementById('runShapeCompare');
    if (!btn) return;
    btn.addEventListener('click', function () {
      var modeOrderSel = document.getElementById('shapeModeOrder');
      var maxOrder = modeOrderSel ? parseInt(modeOrderSel.value) : 6;
      var body = {
        materialDensity: 8960, elasticModulusPa: 1.1e11, poissons: 0.35,
        thicknessMm: 4.5,
        circleRadiusMm: 190, squareSideMm: 380,
        waterDepthMm: 80, maxModeOrder: maxOrder
      };
      fetch(API_BASE + '/api/simulation/shape-comparison', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
      })
        .then(function (r) { return r.json(); })
        .then(function (res) {
          var result = res && res.data ? res.data : res;
          self._renderShapeCompare(result);
        })
        .catch(function (err) {
          console.error('Shape comparison error:', err);
          self._renderShapeCompareFallback(body, maxOrder);
        });
    });
  };

  VibrationPanel.prototype._renderShapeCompare = function (r) {
    if (!r || !r.circleBasin || !r.squareBasin) return;

    var c = r.circleBasin;
    var s = r.squareBasin;

    var cData = []; var sData = [];
    var cFreqs = c.resonanceFrequencies || [];
    var sFreqs = s.resonanceFrequencies || [];
    var n = Math.min(5, Math.min(cFreqs.length, sFreqs.length));
    var labels = [];
    for (var i = 0; i < n; i++) {
      labels.push((i + 2) + '阶');
      cData.push(cFreqs[i]); sData.push(sFreqs[i]);
    }
    this.shapeCompareChart.data.labels = labels;
    this.shapeCompareChart.data.datasets[0].data = cData;
    this.shapeCompareChart.data.datasets[1].data = sData;
    this.shapeCompareChart.update();

    var circleResult = document.getElementById('circleResult');
    if (circleResult) {
      circleResult.innerHTML =
        '<div><span>共振基频 f₁</span><span>' + (c.primaryResonanceFreq ? c.primaryResonanceFreq.toFixed(1) + ' Hz' : '--') + '</span></div>' +
        '<div><span>等效弯曲刚度 D</span><span>' + (c.bendingStiffness ? c.bendingStiffness.toExponential(2) : '--') + '</span></div>' +
        '<div><span>附加质量系数</span><span>' + (c.addedMassFactor ? c.addedMassFactor.toFixed(3) : '--') + '</span></div>' +
        '<div><span>液耦系数</span><span>' + (c.aleCouplingFactor ? c.aleCouplingFactor.toFixed(3) : '--') + '</span></div>' +
        '<div><span>典型喷水</span><span>' + (c.typicalSprayHeightCm ? c.typicalSprayHeightCm.toFixed(1) + ' cm' : '--') + '</span></div>';
    }
    var squareResult = document.getElementById('squareResult');
    if (squareResult) {
      squareResult.innerHTML =
        '<div><span>共振基频 f₁</span><span>' + (s.primaryResonanceFreq ? s.primaryResonanceFreq.toFixed(1) + ' Hz' : '--') + '</span></div>' +
        '<div><span>角部刚度集中 K_corner</span><span>' + (s.cornerStiffnessFactor ? s.cornerStiffnessFactor.toFixed(3) : '--') + '</span></div>' +
        '<div><span>附加质量系数</span><span>' + (s.addedMassFactor ? s.addedMassFactor.toFixed(3) : '--') + '</span></div>' +
        '<div><span>液耦系数</span><span>' + (s.aleCouplingFactor ? s.aleCouplingFactor.toFixed(3) : '--') + '</span></div>' +
        '<div><span>典型喷水</span><span>' + (s.typicalSprayHeightCm ? s.typicalSprayHeightCm.toFixed(1) + ' cm' : '--') + '</span></div>';
    }
    var conclusion = document.getElementById('shapeCompareConclusion');
    if (conclusion) {
      var ratioText = r.frequencyRatio ? r.frequencyRatio.toFixed(2) + ' 倍' : '-- 倍';
      conclusion.textContent = r.interpretation || ('结论：方形由于角部刚度集中效应使其基频高于圆形约' + ratioText + '。圆形振型为径向驻波，方形振型沿四边呈余弦分布，各自激发独特的喷水图案。');
    }
  };

  VibrationPanel.prototype._renderShapeCompareFallback = function (body, maxOrder) {
    var E = body.elasticModulusPa, t = body.thicknessMm / 1000, nu = body.poissons;
    var R = body.circleRadiusMm / 1000, a = body.squareSideMm / 1000, rho = body.materialDensity;
    var D = E * Math.pow(t, 3) / (12 * (1 - nu * nu));
    var cFreqs = [], sFreqs = [];
    var cnt = Math.min(5, Math.max(0, maxOrder - 1));
    for (var n = 2; n < 2 + cnt; n++) {
      var lamCircle = n * n * (n * n - 1);
      var fC = lamCircle / (2 * Math.PI) * Math.sqrt(D / (rho * t * R * R));
      cFreqs.push(fC);
      var m = n, p = n;
      var lamS = Math.pow(m * Math.PI / a, 2) + Math.pow(p * Math.PI / a, 2);
      var fS = (lamS / (2 * Math.PI)) * Math.sqrt(D / (rho * t));
      var cornerFactor = 1.18;
      fS *= cornerFactor;
      sFreqs.push(fS);
    }
    this._renderShapeCompare({
      circleBasin: { primaryResonanceFreq: cFreqs[0], resonanceFrequencies: cFreqs, bendingStiffness: D, addedMassFactor: 1.28, aleCouplingFactor: 0.85, typicalSprayHeightCm: 38.5 },
      squareBasin: { primaryResonanceFreq: sFreqs[0], resonanceFrequencies: sFreqs, cornerStiffnessFactor: 1.18, addedMassFactor: 1.35, aleCouplingFactor: 0.82, typicalSprayHeightCm: 31.2 },
      frequencyRatio: sFreqs[0] / cFreqs[0],
      interpretation: '【物理解读】在相同材质与厚度条件下，方形鱼洗因四角刚性约束导致等效刚度高于圆形，基频提升约' + (sFreqs[0] / cFreqs[0]).toFixed(2) + '倍；方形振型节点沿四边独立，喷水图案呈现"田字格"分布（圆形为"八卦"环状）；方形在相同激励下喷水高度略低但图案更具对称性。'
    });
  };

  VibrationPanel.prototype.bindCrossEraComparison = function () {
    var self = this;
    var btn = document.getElementById('runCrossEra');
    if (!btn) return;
    btn.addEventListener('click', function () {
      var deviceParam = self.selectedDevice || 'FW-HAN-001';
      fetch(API_BASE + '/api/cross-era/comparison?deviceId=' + encodeURIComponent(deviceParam), { method: 'GET' })
        .then(function (r) { return r.json(); })
        .then(function (res) {
          var result = res && res.data ? res.data : res;
          self._renderCrossEra(result);
        })
        .catch(function () {
          self._renderCrossEraFallback();
        });
    });
  };

  VibrationPanel.prototype._renderCrossEra = function (r) {
    if (!r) return;
    var ancient = r.ancientFishWash || r.ancientProfile || {};
    var modern = r.modernUltrasonic || r.modernProfile || {};
    var radar = r.radarComparison || r.radarData || [];
    var eff = r.energyEfficiency || r.energyEfficiencyComparison || {};

    var ancientData = [], modernData = [];
    radar.forEach(function (pt) { ancientData.push(pt.ancientValueNormalized); modernData.push(pt.modernValueNormalized); });
    this.crossEraRadarChart.data.datasets[0].data = ancientData;
    this.crossEraRadarChart.data.datasets[1].data = modernData;
    this.crossEraRadarChart.update();

    var aCard = document.getElementById('ancientCard');
    if (aCard) {
      var ancientParticle = ancient.particleSizeMicrons ? (ancient.particleSizeMicrons / 1000).toFixed(1) + ' mm' : '2-5 mm';
      aCard.innerHTML =
        '<div><span>工作频率</span><span>' + (ancient.frequencyHz ? ancient.frequencyHz.toFixed(0) + ' Hz' : '200-800 Hz') + '</span></div>' +
        '<div><span>激励方式</span><span>手掌摩擦双耳</span></div>' +
        '<div><span>粒子形态</span><span>宏观水滴喷射</span></div>' +
        '<div><span>液滴直径</span><span>' + ancientParticle + '</span></div>' +
        '<div><span>最大喷射高度</span><span>' + (ancient.waterSprayHeightCm ? ancient.waterSprayHeightCm.toFixed(0) + ' cm' : '30-80 cm') + '</span></div>' +
        '<div><span>输入功率</span><span>' + (ancient.energyInputW ? ancient.energyInputW.toFixed(1) + ' W' : '~8.5 W') + '</span></div>';
    }
    var mCard = document.getElementById('modernCard');
    if (mCard) {
      var modernParticle = modern.particleSizeMicrons ? modern.particleSizeMicrons.toFixed(0) + ' μm' : '1-5 μm';
      mCard.innerHTML =
        '<div><span>工作频率</span><span>' + (modern.frequencyHz ? (modern.frequencyHz / 1e6).toFixed(1) + ' MHz' : '1.7-3.0 MHz') + '</span></div>' +
        '<div><span>激励方式</span><span>PZT压电换能器</span></div>' +
        '<div><span>粒子形态</span><span>微米级冷雾</span></div>' +
        '<div><span>雾粒直径</span><span>' + modernParticle + '</span></div>' +
        '<div><span>有效射程</span><span>' + (modern.waterSprayHeightCm ? modern.waterSprayHeightCm.toFixed(1) + ' cm' : '0.5-5 cm') + '</span></div>' +
        '<div><span>输入功率</span><span>' + (modern.energyInputW ? modern.energyInputW.toFixed(1) + ' W' : '~20 W') + '</span></div>';
    }
    var eraParadigm = document.getElementById('eraParadigm');
    if (eraParadigm) {
      eraParadigm.textContent = r.vibrationParadigmDifference || (r.paradigmDifferences ? r.paradigmDifferences.join('\n') : '');
    }
    if (eff) {
      var effDiv = document.getElementById('energyEfficiencyComparison');
      if (effDiv) {
        var aJ = eff.ancientJoulesPerMl || eff.ancientInputPowerW || 15;
        var mJ = eff.modernJoulesPerMl || eff.modernInputPowerW || 20;
        var ratio = eff.efficiencyRatio ? eff.efficiencyRatio.toFixed(1) : '70.8';
        var interp = eff.interpretation || '';
        effDiv.innerHTML = '<h5 style="margin:10px 0 0 0;padding:8px;background:rgba(46,139,87,0.1);border-radius:6px;color:#2E8B57;">能量效率对比：古代 ' + aJ + ' J/ml；现代 ' + mJ + ' J/ml；效率比 ' + ratio + ':1。' + interp + '</h5>';
      }
    }
  };

  VibrationPanel.prototype._renderCrossEraFallback = function () {
    this._renderCrossEra({
      ancientFishWash: { frequencyHz: 245.0, particleSizeMicrons: 3000, waterSprayHeightCm: 55, energyInputW: 8.5 },
      modernUltrasonic: { frequencyHz: 1700000, particleSizeMicrons: 3, waterSprayHeightCm: 0.1, energyInputW: 20 },
      radarComparison: [
        { dimension: '工作频率', ancientValueNormalized: 25, modernValueNormalized: 100 },
        { dimension: '高度', ancientValueNormalized: 85, modernValueNormalized: 18 },
        { dimension: '粒子尺寸', ancientValueNormalized: 95, modernValueNormalized: 10 },
        { dimension: '能量效率', ancientValueNormalized: 8, modernValueNormalized: 100 },
        { dimension: '工艺复杂度', ancientValueNormalized: 92, modernValueNormalized: 58 },
        { dimension: '文化价值', ancientValueNormalized: 100, modernValueNormalized: 20 }
      ],
      paradigmDifferences: [
        '【1. 尺度差异】鱼洗：10² Hz 机械驻波 (λ≈cm级) 大尺度相干振动；超声雾化：10⁶ Hz 压电振动 (λ≈μm级) 纳米位移振幅',
        '【2. 能量传递】鱼洗：库仑摩擦 → 结构共振 → 液固耦合 → 界面断裂（能量沿结构→液界面）；超声雾化：逆压电 → PZT厚度振动 → 聚焦空化 → 毛细管波破碎',
        '【3. 文化跨度】两千年共振原理的延续：本质都是"振动破碎液面"。从手掌拍打到PZT驱动，跨越整个工业史，但物理原理不变——共振 + 流体表面不稳定性破碎。'
      ],
      energyEfficiency: { ancientJoulesPerMl: 8.5, modernJoulesPerMl: 0.12, efficiencyRatio: 70.8, interpretation: '现代雾化器效率远高于古代鱼洗' }
    });
  };

  VibrationPanel.prototype.bindFrictionAnalysis = function () {
    var self = this;
    var btn = document.getElementById('runFrictionAnalyze');
    if (!btn) return;
    btn.addEventListener('click', function () {
      var deviceParam = self.selectedDevice || 'FW-HAN-001';
      var body = {
        normalForceN: parseFloat(document.getElementById('frictionNormalForce').value) || 25,
        frictionCoefficient: parseFloat(document.getElementById('frictionCoeff').value) || 0.35,
        frictionVelocityMps: parseFloat(document.getElementById('frictionVelocity').value) || 0.8,
        handleDiameterMm: parseFloat(document.getElementById('frictionHandleDiameter').value) || 22,
        strokeLengthMm: parseFloat(document.getElementById('frictionStroke').value) || 120,
        contactAreaCm2: parseFloat(document.getElementById('frictionContactArea').value) || 12.5
      };
      fetch(API_BASE + '/api/friction/analyze/' + encodeURIComponent(deviceParam), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      })
        .then(function (r) { return r.json(); })
        .then(function (res) {
          var result = res && res.data ? res.data : res;
          self._renderFrictionAnalysis(result);
        })
        .catch(function (err) {
          console.error('Friction analysis error:', err);
          self._renderFrictionFallback(body);
        });
    });
  };

  VibrationPanel.prototype._renderFrictionAnalysis = function (r) {
    if (!r) return;
    var curve = null;
    if (r && r.mechanicsParamsJsonb && r.mechanicsParamsJsonb.stribeckTransitionCurve) {
      curve = r.mechanicsParamsJsonb.stribeckTransitionCurve;
    }
    if (!curve && r.stribeckTransitionCurve) {
      curve = r.stribeckTransitionCurve;
    }
    this._renderFrictionCurve(curve);

    var matchLabel = r.frequencyMatchFactor > 0.75 ? '优' : (r.frequencyMatchFactor > 0.5 ? '中' : '弱');
    var resultEl = document.getElementById('frictionAnalysisResult');
    if (resultEl) {
      resultEl.innerHTML =
        '<div class="analysis-grid">' +
        '<div><span>切向驱动力 F_t</span><span>' + (r.tangentialForceN ? r.tangentialForceN.toFixed(2) + ' N' : '--') + '</span></div>' +
        '<div><span>接触压强 P</span><span>' + (r.contactPressurePa ? (r.contactPressurePa / 1e6).toFixed(3) + ' MPa' : '--') + '</span></div>' +
        '<div><span>剪切应力 τ</span><span>' + (r.shearStressPa ? (r.shearStressPa / 1e3).toFixed(1) + ' kPa' : '--') + '</span></div>' +
        '<div><span>激励扭矩 M</span><span>' + (r.excitationTorqueNm ? r.excitationTorqueNm.toFixed(3) + ' N·m' : '--') + '</span></div>' +
        '<div><span>瞬时输入功率</span><span>' + (r.instantaneousPowerW ? r.instantaneousPowerW.toFixed(2) + ' W' : '--') + '</span></div>' +
        '<div><span>单次行程能量</span><span>' + (r.cycleEnergyJ ? r.cycleEnergyJ.toFixed(3) + ' J' : '--') + '</span></div>' +
        '<div><span>粘滑振荡频率 f_ss</span><span>' + (r.stickSlipFreqHz ? r.stickSlipFreqHz.toFixed(1) + ' Hz' : '--') + '</span></div>' +
        '<div><span>频率匹配因子</span><span>' + (r.frequencyMatchFactor ? r.frequencyMatchFactor.toFixed(3) + ' (' + matchLabel + ')' : '--') + '</span></div>' +
        '<div><span>能量传递效率 η</span><span>' + (r.transferEfficiencyPct ? r.transferEfficiencyPct.toFixed(1) + '%' : '--') + '</span></div>' +
        '<div><span>预期结构振幅</span><span>' + (r.expectedAmplitudeUm ? r.expectedAmplitudeUm.toFixed(1) + ' μm' : '--') + '</span></div>' +
        '</div>' +
        '<h5>力学分析方程</h5>' +
        '<div class="formula-line">F_t = μ_eff · N = ' + (r.tangentialForceN ? r.tangentialForceN.toFixed(2) : '--') + ' N (库仑摩擦定律，水膜润滑修正)</div>' +
        '<div class="formula-line">P = F_t / A_contact = ' + (r.contactPressurePa ? (r.contactPressurePa / 1e6).toFixed(3) : '--') + ' MPa (接触区压强)</div>' +
        '<div class="formula-line">η = P有用 / P输入 = ' + (r.transferEfficiencyPct ? r.transferEfficiencyPct.toFixed(1) : '--') + '% (60%热耗 + 3%声辐射 + 37%结构振动)</div>' +
        '<h5>受力分解 (自由体图)</h5>' +
        '<div style="padding:6px 10px;border-left:3px solid var(--copper-aged);background:rgba(139,105,20,0.06);border-radius:4px;">' +
        (r.freeBodyDiagramDescription || '① 手侧：法向 + 切向摩擦驱动；② 柄侧：鱼洗青铜柄传递振动；③ 能量流分配（热/声/结构）。') +
        '</div>';
    }
  };

  VibrationPanel.prototype._renderFrictionCurve = function (curve) {
    if (!curve || !Array.isArray(curve) || curve.length === 0) return;
    var labels = curve.map(function (p) { return p.velocityMps !== undefined ? p.velocityMps.toFixed(3) : ''; });
    var data = curve.map(function (p) { return p.frictionCoeff; });
    this.frictionCurveChart.data.labels = labels;
    this.frictionCurveChart.data.datasets[0].data = data;
    this.frictionCurveChart.update();
  };

  VibrationPanel.prototype._renderFrictionFallback = function (body) {
    var N = body.normalForceN, mu0 = body.frictionCoefficient, v = body.frictionVelocityMps;
    var d = body.handleDiameterMm / 1000, L = body.strokeLengthMm / 1000, A = body.contactAreaCm2 * 1e-4;
    var delta_break = 15e-6, r_handle = d / 2;
    var mu_eff = mu0 * (1 - 0.18 * Math.min(v * 1.05, 1));
    var Ft = mu_eff * N;
    var P = N / A;
    var tau = mu_eff * P;
    var M = Ft * r_handle;
    var P_in = Ft * v;
    var E_cycle = Ft * L;
    var f_ss = v / delta_break;
    var f_match = Math.exp(-Math.pow(f_ss - 220, 2) / (80 * 80));
    var eta = 0.37 * (0.5 + 0.5 * f_match);
    var amp_um = Math.sqrt(2 * P_in * eta / (Math.pow(2 * Math.PI * 245, 2) * 0.001)) * 1e6;
    var stribeck = [];
    var vs = [0, 0.01, 0.05, 0.1, 0.2, 0.4, 0.6, 0.8, 1.0, 1.5];
    vs.forEach(function (vv) {
      var boundary = 1.15 * mu0 * Math.exp(-vv * 45);
      var coulomb = mu0;
      var mix = boundary + (coulomb - boundary) * (1 - Math.exp(-vv / 0.08));
      stribeck.push({ velocityMps: vv, frictionCoeff: +mix.toFixed(4) });
    });
    this._renderFrictionCurve(stribeck);
    this._renderFrictionAnalysis({
      tangentialForceN: +Ft.toFixed(2),
      contactPressurePa: +P.toFixed(0),
      shearStressPa: +tau.toFixed(0),
      excitationTorqueNm: +M.toFixed(4),
      instantaneousPowerW: +P_in.toFixed(2),
      cycleEnergyJ: +E_cycle.toFixed(4),
      stickSlipFreqHz: +f_ss.toFixed(1),
      frequencyMatchFactor: +f_match.toFixed(3),
      transferEfficiencyPct: +(eta * 100).toFixed(1),
      expectedAmplitudeUm: +amp_um.toFixed(1),
      freeBodyDiagramDescription:
        '自由体受力分解：① 手侧施加法向 N=' + N + 'N + 切向摩擦驱动 F_t=' + Ft.toFixed(2) + 'N；② 柄侧由青铜鱼洗柄传递振动响应；③ 能量流：60%热耗散 + 3%声辐射 + 37%结构振动（有效激发喷水）。'
    });
  };

  VibrationPanel.prototype.bindControls = function () {
    var self = this;
    var simulateBtn = document.getElementById('simulateBtn');
    if (simulateBtn) {
      simulateBtn.addEventListener('click', function () {
        var modeSelector = document.getElementById('modeOrder');
        var modeOrder = modeSelector ? parseInt(modeSelector.value) : 2;
        if (typeof self.onSimulate === 'function') {
          self.onSimulate(modeOrder);
        }
        self.triggerSimulationAPI(modeOrder);
      });
    }
    var frictionSlider = document.getElementById('frictionSlider');
    var frictionSliderValue = document.getElementById('frictionSliderValue');
    if (frictionSlider) {
      frictionSlider.addEventListener('input', function (e) {
        var freq = parseFloat(e.target.value);
        if (typeof self.onFrequencyChange === 'function') {
          self.onFrequencyChange(freq);
        }
        if (frictionSliderValue) {
          frictionSliderValue.textContent = freq.toFixed(0) + ' Hz';
        }
      });
    }
  };

  VibrationPanel.prototype.triggerSimulationAPI = function (modeOrder) {
    if (!this.selectedDevice) return;
    var self = this;
    fetch(API_BASE + '/api/simulation/modal-analysis/' + this.selectedDevice + '?maxModeOrder=' + modeOrder, { method: 'POST' })
      .then(function (res) { return res.json(); })
      .then(function (data) {
        var modes = data && data.data ? data.data : data;
        if (Array.isArray(modes)) self.updateVibrationChart(modes);
      })
      .catch(function () { console.log('Modal analysis API not available'); });
    var frictionSlider = document.getElementById('frictionSlider');
    var freq = frictionSlider ? parseFloat(frictionSlider.value) : 100;
    fetch(API_BASE + '/api/spray/analysis/' + this.selectedDevice, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ frictionFreq: freq, measuredSprayHeight: null })
    })
      .then(function (res) { return res.json(); })
      .then(function (data) {
        var result = data && data.data ? data.data : data;
        if (result && result.predictedSprayHeight && typeof self.onFrequencyChange === 'function') {
          self.onFrequencyChange(freq);
        }
      })
      .catch(function () { console.log('Spray analysis API not available'); });
  };

  VibrationPanel.prototype.fetchLatestData = function (deviceId) {
    var self = this;
    fetch(API_BASE + '/api/sensor-data/' + deviceId + '/latest')
      .then(function (res) { return res.json(); })
      .then(function (data) {
        var sensorData = data && data.data ? data.data : data;
        self.updateSensorDisplay(sensorData);
      })
      .catch(function () { console.log('Sensor data not available'); });
  };

  VibrationPanel.prototype.fetchVibrationModes = function (deviceId) {
    var self = this;
    fetch(API_BASE + '/api/simulation/vibration-modes/' + deviceId)
      .then(function (res) { return res.json(); })
      .then(function (data) {
        var modes = data && data.data ? data.data : data;
        if (Array.isArray(modes)) self.updateVibrationChart(modes);
      })
      .catch(function () { console.log('Vibration modes not available'); });
  };

  VibrationPanel.prototype.formatValue = function (val, unit) {
    if (val === null || val === undefined) return '--';
    var num = typeof val === 'number' ? val : parseFloat(val);
    if (isNaN(num)) return '--';
    return num.toFixed(2) + ' ' + unit;
  };

  VibrationPanel.prototype.destroy = function () {
    if (this.wsClient) this.wsClient.disconnect();
  };

  return VibrationPanel;
})();
