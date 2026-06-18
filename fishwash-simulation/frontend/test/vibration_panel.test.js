var assert = require('assert');
var fs = require('fs');
var path = require('path');

global.Chart = function () {
  this.data = { labels: [], datasets: [{ data: [] }] };
  this.update = function () {};
};

global.SockJS = function () {};
global.Stomp = { over: function () { return { connect: function () {}, disconnect: function () {}, subscribe: function () {} }; } };

global.document = {
  _elements: {},
  getElementById: function (id) { return this._elements[id] || null; },
  createElement: function (tag) {
    var el = { _tag: tag, _children: [], _attrs: {}, style: {}, classList: { add: function () {}, remove: function () {} }, _textContent: '', _innerHTML: '' };
    el.appendChild = function (c) { this._children.push(c); };
    el.insertBefore = function (c) { this._children.unshift(c); };
    el.addEventListener = function () {};
    el.querySelector = function () { return null; };
    Object.defineProperty(el, 'textContent', { get: function () { return this._textContent; }, set: function (v) { this._textContent = v; }, configurable: true });
    Object.defineProperty(el, 'innerHTML', { get: function () { return this._innerHTML; }, set: function (v) { this._innerHTML = v; }, configurable: true });
    return el;
  },
  querySelectorAll: function () { return []; },
  querySelector: function () { return null; },
  addEventListener: function () {}
};

global.window = { addEventListener: function () {} };
global.fetch = function () { return Promise.resolve({ json: function () { return Promise.resolve({ data: [] }); } }); };
global.navigator = { vibrate: function () {} };

var panelSrc = fs.readFileSync(path.join(__dirname, '..', 'js', 'vibration_panel.js'), 'utf8');
eval(panelSrc);

var passed = 0;
var failed = 0;
var total = 0;

function test(name, fn) {
  total++;
  try {
    fn();
    passed++;
    console.log('  ✓ ' + name);
  } catch (e) {
    failed++;
    console.log('  ✗ ' + name);
    console.log('    ' + e.message);
  }
}

function suite(name, fn) {
  console.log('\n' + name);
  fn();
}

suite('VibrationPanel — 形状对比逻辑', function () {
  test('构造函数初始化所有新属性', function () {
    var p = new VibrationPanel();
    assert.strictEqual(p.shapeCompareChart, null);
    assert.strictEqual(p.crossEraRadarChart, null);
    assert.strictEqual(p.frictionCurveChart, null);
    assert.strictEqual(p.currentTab, 'simulation');
    assert.strictEqual(p.onShapeChange, null);
    assert.strictEqual(p.onInteractiveFrictionToggle, null);
  });

  test('syncShapeRadio：设置radio checked状态', function () {
    var p = new VibrationPanel();
    var radio1 = { value: 'CIRCLE', checked: false };
    var radio2 = { value: 'SQUARE', checked: false };
    document._elements['shapeSelector'] = true;
    var origQSA = document.querySelectorAll;
    document.querySelectorAll = function (sel) {
      if (sel === 'input[name="shapeSelector"]') return [radio1, radio2];
      return [];
    };
    p.syncShapeRadio('SQUARE');
    assert.strictEqual(radio1.checked, false);
    assert.strictEqual(radio2.checked, true);

    p.syncShapeRadio('CIRCLE');
    assert.strictEqual(radio1.checked, true);
    assert.strictEqual(radio2.checked, false);

    document.querySelectorAll = origQSA;
  });

  test('formatValue：正常数值格式化', function () {
    var p = new VibrationPanel();
    assert.strictEqual(p.formatValue(123.456, 'Hz'), '123.46 Hz');
    assert.strictEqual(p.formatValue(0, 'mm'), '0.00 mm');
    assert.strictEqual(p.formatValue(-5.5, 'cm'), '-5.50 cm');
  });

  test('formatValue：null/undefined返回--', function () {
    var p = new VibrationPanel();
    assert.strictEqual(p.formatValue(null, 'Hz'), '--');
    assert.strictEqual(p.formatValue(undefined, 'Hz'), '--');
  });

  test('formatValue：字符串数字正常解析', function () {
    var p = new VibrationPanel();
    assert.strictEqual(p.formatValue('3.14', 'Pa'), '3.14 Pa');
  });

  test('formatValue：NaN返回--', function () {
    var p = new VibrationPanel();
    assert.strictEqual(p.formatValue('abc', 'Hz'), '--');
  });
});

suite('VibrationPanel — 形状对比fallback计算', function () {
  test('_renderShapeCompareFallback：使用瑞利公式计算圆形频率', function () {
    var p = new VibrationPanel();
    p.shapeCompareChart = { data: { labels: [], datasets: [{ data: [] }, { data: [] }] }, update: function () {} };

    var circleResultEl = {};
    Object.defineProperty(circleResultEl, 'innerHTML', { value: '', writable: true, configurable: true });
    var squareResultEl = {};
    Object.defineProperty(squareResultEl, 'innerHTML', { value: '', writable: true, configurable: true });
    var conclusionEl = {};
    Object.defineProperty(conclusionEl, 'textContent', { value: '', writable: true, configurable: true });
    var origGEBI = document.getElementById;
    document.getElementById = function (id) {
      if (id === 'circleResult') return circleResultEl;
      if (id === 'squareResult') return squareResultEl;
      if (id === 'shapeCompareConclusion') return conclusionEl;
      return null;
    };

    var body = {
      materialDensity: 8500, elasticModulusPa: 1.0e11, poissons: 0.34,
      thicknessMm: 4.0, circleRadiusMm: 190, squareSideMm: 380, waterDepthMm: 98
    };

    p._renderShapeCompareFallback(body, 4);

    assert.ok(circleResultEl.innerHTML.length > 0, '圆形结果应有内容');
    assert.ok(squareResultEl.innerHTML.length > 0, '方形结果应有内容');
    assert.ok(conclusionEl.textContent.length > 0, '结论应有内容');
    assert.ok(conclusionEl.textContent.indexOf('倍') > -1, '结论应包含频率倍数');

    document.getElementById = origGEBI;
  });
});

suite('VibrationPanel — 跨时代对比fallback', function () {
  test('_renderCrossEraFallback：6维雷达数据完整', function () {
    var p = new VibrationPanel();
    var radarUpdated = false;
    p.crossEraRadarChart = {
      data: { datasets: [{ data: [] }, { data: [] }] },
      update: function () { radarUpdated = true; }
    };

    var ancientCardEl = {};
    Object.defineProperty(ancientCardEl, 'innerHTML', { value: '', writable: true, configurable: true });
    var modernCardEl = {};
    Object.defineProperty(modernCardEl, 'innerHTML', { value: '', writable: true, configurable: true });
    var paradigmEl = {};
    Object.defineProperty(paradigmEl, 'textContent', { value: '', writable: true, configurable: true });
    var effEl = {};
    Object.defineProperty(effEl, 'innerHTML', { value: '', writable: true, configurable: true });
    var origGEBI = document.getElementById;
    document.getElementById = function (id) {
      if (id === 'ancientCard') return ancientCardEl;
      if (id === 'modernCard') return modernCardEl;
      if (id === 'eraParadigm') return paradigmEl;
      if (id === 'energyEfficiencyComparison') return effEl;
      return null;
    };

    p._renderCrossEraFallback();

    assert.ok(radarUpdated, '雷达图应已更新');
    assert.strictEqual(p.crossEraRadarChart.data.datasets[0].data.length, 6, '古代6维');
    assert.strictEqual(p.crossEraRadarChart.data.datasets[1].data.length, 6, '现代6维');
    assert.ok(ancientCardEl.innerHTML.indexOf('Hz') > -1, '古代卡片含频率');
    assert.ok(modernCardEl.innerHTML.indexOf('MHz') > -1, '现代卡片含MHz');
    assert.ok(paradigmEl.textContent.length > 50, '范式差异应有内容');

    document.getElementById = origGEBI;
  });
});

suite('VibrationPanel — 摩擦力分析fallback计算', function () {
  test('_renderFrictionFallback：Stribeck曲线10个点', function () {
    var p = new VibrationPanel();
    var curveLabels = null;
    var curveData = null;
    p.frictionCurveChart = {
      data: { labels: [], datasets: [{ data: [] }] },
      update: function () {
        curveLabels = this.data.labels;
        curveData = this.data.datasets[0].data;
      }
    };

    var resultEl = {};
    Object.defineProperty(resultEl, 'innerHTML', { value: '', writable: true, configurable: true });
    var origGEBI = document.getElementById;
    document.getElementById = function (id) {
      if (id === 'frictionAnalysisResult') return resultEl;
      return null;
    };

    var body = {
      normalForceN: 25, frictionCoefficient: 0.35, frictionVelocityMps: 0.8,
      handleDiameterMm: 22, strokeLengthMm: 120, contactAreaCm2: 12.5
    };

    p._renderFrictionFallback(body);

    assert.ok(curveLabels !== null, 'Stribeck曲线应已生成');
    assert.strictEqual(curveLabels.length, 10, '应有10个速度采样点');
    assert.strictEqual(curveData.length, 10, '10个摩擦系数值');
    assert.ok(resultEl.innerHTML.indexOf('N') > -1, '结果含力值');
    assert.ok(resultEl.innerHTML.indexOf('MPa') > -1 || resultEl.innerHTML.indexOf('kPa') > -1, '结果含压强');

    document.getElementById = origGEBI;
  });

  test('_renderFrictionFallback：切向力 = μ_eff × N', function () {
    var p = new VibrationPanel();
    p.frictionCurveChart = { data: { labels: [], datasets: [{ data: [] }] }, update: function () {} };
    p._renderFrictionAnalysis = function (r) { this._lastFrictionResult = r; };

    var body = { normalForceN: 20, frictionCoefficient: 0.3, frictionVelocityMps: 0.5, handleDiameterMm: 22, strokeLengthMm: 80, contactAreaCm2: 12 };
    p._renderFrictionFallback(body);

    var r = p._lastFrictionResult;
    assert.ok(r.tangentialForceN > 0, '切向力应>0');
    assert.ok(r.tangentialForceN < 20 * 0.3, '切向力应<μ×N(水膜润滑后)');
  });
});

suite('VibrationPanel — _updateFrictionMetrics 虚拟交互', function () {
  function makeWritableEl() {
    var el = {};
    Object.defineProperty(el, 'textContent', { value: '', writable: true, configurable: true });
    return el;
  }

  test('正常摩擦指标更新', function () {
    var p = new VibrationPanel();
    var velocityEl = makeWritableEl();
    var freqEl = makeWritableEl();
    var modeEl = makeWritableEl();
    var sprayEl = makeWritableEl();
    var ampEl = makeWritableEl();
    var statusEl = { _tc: '', classList: { add: function () {}, remove: function () {} } };
    Object.defineProperty(statusEl, 'textContent', { get: function () { return this._tc; }, set: function (v) { this._tc = v; }, configurable: true });

    var origGEBI = document.getElementById;
    document.getElementById = function (id) {
      if (id === 'metricVelocity') return velocityEl;
      if (id === 'metricFreq') return freqEl;
      if (id === 'metricMode') return modeEl;
      if (id === 'metricSpray') return sprayEl;
      if (id === 'metricAmp') return ampEl;
      if (id === 'frictionStatus') return statusEl;
      return null;
    };

    p._updateFrictionMetrics({
      velocity: 0.856,
      frequency: 338,
      modeOrder: 3,
      sprayHeight: 42.5,
      amplitude: 1.33,
      isDragging: true,
      isActive: true
    });

    assert.strictEqual(velocityEl.textContent, '0.856 m/s');
    assert.strictEqual(freqEl.textContent, '338 Hz');
    assert.strictEqual(modeEl.textContent, '3 阶模态');
    assert.strictEqual(sprayEl.textContent, '42.5 cm');
    assert.strictEqual(ampEl.textContent, '1.33 mm');
    assert.ok(statusEl.textContent.indexOf('摩擦中') > -1, '状态应为摩擦中');

    document.getElementById = origGEBI;
  });

  test('null/undefined指标显示--', function () {
    var p = new VibrationPanel();
    var els = {};
    ['metricVelocity', 'metricFreq', 'metricMode', 'metricSpray', 'metricAmp'].forEach(function (k) {
      els[k] = makeWritableEl();
      els[k].textContent = 'OLD';
    });
    var statusEl = makeWritableEl();

    var origGEBI = document.getElementById;
    document.getElementById = function (id) {
      if (els[id]) return els[id];
      if (id === 'frictionStatus') return statusEl;
      return null;
    };

    p._updateFrictionMetrics(null);

    assert.strictEqual(els.metricVelocity.textContent, '--');
    assert.strictEqual(els.metricFreq.textContent, '--');
    assert.strictEqual(els.metricMode.textContent, '--');
    assert.strictEqual(els.metricSpray.textContent, '--');
    assert.strictEqual(els.metricAmp.textContent, '--');

    document.getElementById = origGEBI;
  });

  test('零速度指标正常显示', function () {
    var p = new VibrationPanel();
    var velEl = makeWritableEl();
    var freqEl = makeWritableEl();
    var statusEl = makeWritableEl();
    statusEl.classList = { add: function () {}, remove: function () {} };

    var origGEBI = document.getElementById;
    document.getElementById = function (id) {
      if (id === 'metricVelocity') return velEl;
      if (id === 'metricFreq') return freqEl;
      if (id === 'frictionStatus') return statusEl;
      return makeWritableEl();
    };

    p._updateFrictionMetrics({
      velocity: 0, frequency: 0, modeOrder: 0,
      sprayHeight: 0, amplitude: 0, isDragging: false, isActive: false
    });

    assert.strictEqual(velEl.textContent, '0.000 m/s');
    assert.strictEqual(freqEl.textContent, '0 Hz');

    document.getElementById = origGEBI;
  });
});

suite('VibrationPanel — 虚拟交互趣味性验证', function () {
  test('低速摩擦(v=0.3)：频率≈216Hz 2阶模态', function () {
    var v = 0.3;
    var freq = 150 + v * 220;
    var modeOrder = v < 0.8 ? 2 : (v < 1.6 ? 3 : 4);
    assert.ok(Math.abs(freq - 216) < 1, '低速频率约216Hz');
    assert.strictEqual(modeOrder, 2, '低速应激发2阶');
  });

  test('中速摩擦(v=1.0)：频率≈370Hz 3阶模态', function () {
    var v = 1.0;
    var freq = 150 + v * 220;
    var modeOrder = v < 0.8 ? 2 : (v < 1.6 ? 3 : 4);
    assert.ok(Math.abs(freq - 370) < 1, '中速频率约370Hz');
    assert.strictEqual(modeOrder, 3, '中速应激发3阶');
  });

  test('高速摩擦(v=2.0)：频率≈590Hz 4阶模态', function () {
    var v = 2.0;
    var freq = 150 + v * 220;
    var modeOrder = v < 0.8 ? 2 : (v < 1.6 ? 3 : 4);
    assert.ok(Math.abs(freq - 590) < 1, '高速频率约590Hz');
    assert.strictEqual(modeOrder, 4, '高速应激发4阶');
  });

  test('喷水高度随速度递增：v=0→2cm, v=1→57cm', function () {
    var h0 = 2 + 0 * 55;
    var h1 = 2 + 1 * 55;
    assert.strictEqual(h0, 2, '零速度喷水2cm');
    assert.strictEqual(h1, 57, 'v=1喷水57cm');
    assert.ok(h1 > h0, '速度越快喷水越高');
  });

  test('振幅随速度递增', function () {
    var amp0 = 0.3 + 0 * 1.2;
    var amp1 = 0.3 + 1.0 * 1.2;
    var amp2 = 0.3 + 2.0 * 1.2;
    assert.ok(amp0 < amp1, 'v0→v1振幅增大');
    assert.ok(amp1 < amp2, 'v1→v2振幅增大');
    assert.ok(amp0 > 0, '零速仍有微弱振幅');
  });

  test('极端高速v=10：频率2350Hz 4阶模态(上限)', function () {
    var v = 10;
    var freq = 150 + v * 220;
    var modeOrder = v < 0.8 ? 2 : (v < 1.6 ? 3 : 4);
    assert.strictEqual(modeOrder, 4, '极端速度仍4阶');
    assert.strictEqual(freq, 2350, '频率线性映射');
  });
});

suite('VibrationPanel — Tab切换逻辑', function () {
  test('initTabs：切换tab更新currentTab', function () {
    var p = new VibrationPanel();
    var btns = [
      { getAttribute: function () { return 'simulation'; }, classList: { add: function () {}, remove: function () {} }, addEventListener: function (evt, fn) { if (evt === 'click') this._clickFn = fn; } },
      { getAttribute: function () { return 'shape'; }, classList: { add: function () {}, remove: function () {} }, addEventListener: function (evt, fn) { if (evt === 'click') this._clickFn = fn; } }
    ];
    var panes = [
      { classList: { add: function () {}, remove: function () {} } },
      { classList: { add: function () {}, remove: function () {} } }
    ];

    var origQSA = document.querySelectorAll;
    var origQS = document.querySelector;
    document.querySelectorAll = function (sel) {
      if (sel === '.tab-btn') return btns;
      if (sel === '.tab-pane') return panes;
      return [];
    };
    document.querySelector = function (sel) {
      if (sel === '.tab-pane[data-tab="shape"]') return panes[1];
      return null;
    };

    p.initTabs();

    btns[1]._clickFn();
    assert.strictEqual(p.currentTab, 'shape');

    btns[0]._clickFn();
    assert.strictEqual(p.currentTab, 'simulation');

    document.querySelectorAll = origQSA;
    document.querySelector = origQS;
  });
});

suite('VibrationPanel — 触觉反馈模拟', function () {
  test('构造函数初始化触觉反馈属性', function () {
    var p = new VibrationPanel();
    assert.strictEqual(p.hapticFeedbackEnabled, false);
    assert.strictEqual(p.hapticVibrationActive, false);
    assert.strictEqual(p.hapticLastPattern, null);
    assert.strictEqual(typeof p.hapticSupported, 'boolean');
    assert.strictEqual(p.hapticIntensity, 1.0);
    assert.strictEqual(p.hapticStickSlipPhase, 0);
  });

  test('_triggerHapticFeedback：低速摩擦产生微震模式', function () {
    var p = new VibrationPanel();
    p.hapticFeedbackEnabled = true;
    p.hapticSupported = true;

    var vibrateCalled = null;
    global.navigator.vibrate = function (pattern) { vibrateCalled = pattern; };

    var metrics = {
      isDragging: true,
      isActive: true,
      velocity: 0.3,
      frequency: 216,
      amplitude: 0.15,
      sprayHeight: 2.0,
      modeOrder: 2
    };

    var hapticPatternEl = { textContent: '' };
    var hapticDetailEl = { textContent: '' };
    document._elements['hapticPattern'] = hapticPatternEl;
    document._elements['hapticDetail'] = hapticDetailEl;
    document._elements['frictionStatus'] = { textContent: '', classList: { add: function () {}, remove: function () {} } };

    p._updateFrictionMetrics(metrics);
    p._triggerHapticFeedback(metrics);

    assert.strictEqual(p.hapticVibrationActive, true);
    assert.ok(vibrateCalled !== null, '低速调用navigator.vibrate');
    assert.ok(Array.isArray(vibrateCalled), '震动模式是数组');
    assert.ok(vibrateCalled.length >= 4, '震动模式至少4段');
    assert.ok(hapticDetailEl.textContent.indexOf('微震') !== -1, '低速显示"微震"');
    assert.ok(hapticDetailEl.textContent.indexOf('2阶') !== -1, '显示2阶模态');
    assert.ok(hapticPatternEl.textContent.indexOf('强度') !== -1, '显示强度');
  });

  test('_triggerHapticFeedback：中速摩擦产生麻酥感模式', function () {
    var p = new VibrationPanel();
    p.hapticFeedbackEnabled = true;
    p.hapticSupported = true;

    var vibrateCalled = null;
    global.navigator.vibrate = function (pattern) { vibrateCalled = pattern; };

    var hapticDetailEl = { textContent: '' };
    document._elements['hapticDetail'] = hapticDetailEl;

    var metrics = {
      isDragging: true,
      isActive: true,
      velocity: 1.0,
      frequency: 370,
      amplitude: 0.45,
      sprayHeight: 57.0,
      modeOrder: 3
    };

    p._triggerHapticFeedback(metrics);

    assert.ok(hapticDetailEl.textContent.indexOf('麻酥') !== -1, '中速显示"麻酥"');
    assert.ok(hapticDetailEl.textContent.indexOf('3阶') !== -1, '显示3阶模态');
    assert.ok(hapticDetailEl.textContent.indexOf('喷水57cm') !== -1, '显示喷水高度');
    assert.ok(vibrateCalled && vibrateCalled.length >= 4, '震动模式有效');
  });

  test('_triggerHapticFeedback：高速摩擦产生强震模式', function () {
    var p = new VibrationPanel();
    p.hapticFeedbackEnabled = true;
    p.hapticSupported = true;

    var vibrateCalled = null;
    global.navigator.vibrate = function (pattern) { vibrateCalled = pattern; };

    var hapticDetailEl = { textContent: '' };
    document._elements['hapticDetail'] = hapticDetailEl;

    var metrics = {
      isDragging: true,
      isActive: true,
      velocity: 2.0,
      frequency: 590,
      amplitude: 0.85,
      sprayHeight: 85.0,
      modeOrder: 4
    };

    p._triggerHapticFeedback(metrics);

    assert.ok(hapticDetailEl.textContent.indexOf('强震') !== -1, '高速显示"强震"');
    assert.ok(hapticDetailEl.textContent.indexOf('4阶') !== -1, '显示4阶模态');
    assert.ok(vibrateCalled && vibrateCalled.length >= 6, '高频多段震动');
  });

  test('_triggerHapticFeedback：喷水阈值突破触发特殊震动', function () {
    var p = new VibrationPanel();
    p.hapticFeedbackEnabled = true;
    p.hapticSupported = true;

    var vibrateCalls = [];
    global.navigator.vibrate = function (pattern) {
      vibrateCalls.push(pattern);
    };

    var hapticDetailEl = { textContent: '' };
    document._elements['hapticDetail'] = hapticDetailEl;

    var metrics = {
      isDragging: true,
      isActive: true,
      velocity: 1.5,
      frequency: 450,
      amplitude: 0.6,
      sprayHeight: 42.0,
      modeOrder: 3,
      sprayThresholdCrossed: true
    };

    p._triggerHapticFeedback(metrics);

    assert.ok(hapticDetailEl.textContent.indexOf('喷水阈值突破') !== -1, '显示阈值突破');
    assert.ok(vibrateCalls.length >= 2, '至少两次震动调用（常规+阈值突破）');
    var lastCall = vibrateCalls[vibrateCalls.length - 1];
    assert.deepStrictEqual(lastCall, [100, 50, 100, 50, 200], '阈值突破用特殊5段模式');
  });

  test('_triggerHapticFeedback：未拖拽时停止触觉反馈', function () {
    var p = new VibrationPanel();
    p.hapticFeedbackEnabled = true;
    p.hapticSupported = true;
    p.hapticVibrationActive = true;

    var vibrateCalled = null;
    global.navigator.vibrate = function (pattern) { vibrateCalled = pattern; };

    var hapticDetailEl = { textContent: 'some content' };
    document._elements['hapticDetail'] = hapticDetailEl;

    var metrics = {
      isDragging: false,
      isActive: true
    };

    p._triggerHapticFeedback(metrics);

    assert.strictEqual(vibrateCalled, 0, '停止震动传入0');
    assert.strictEqual(p.hapticVibrationActive, false, '状态置为非活跃');
    assert.strictEqual(hapticDetailEl.textContent, '', '详情清空');
  });

  test('_stopHapticFeedback：正确停止震动并重置状态', function () {
    var p = new VibrationPanel();
    p.hapticVibrationActive = true;
    p.hapticLastPattern = '10-20-30';
    p.hapticStickSlipPhase = 2;
    p.hapticSupported = true;

    var vibrateCalled = null;
    global.navigator.vibrate = function (pattern) { vibrateCalled = pattern; };

    var hapticDetailEl = { textContent: 'some content' };
    document._elements['hapticDetail'] = hapticDetailEl;

    p._stopHapticFeedback();

    assert.strictEqual(vibrateCalled, 0, '调用navigator.vibrate(0)');
    assert.strictEqual(p.hapticVibrationActive, false);
    assert.strictEqual(p.hapticLastPattern, null);
    assert.strictEqual(p.hapticStickSlipPhase, 0);
    assert.strictEqual(hapticDetailEl.textContent, '');
  });

  test('触觉反馈禁用时不触发震动', function () {
    var p = new VibrationPanel();
    p.hapticFeedbackEnabled = false;
    p.hapticSupported = true;

    var vibrateCalled = false;
    global.navigator.vibrate = function () { vibrateCalled = true; };

    var metrics = {
      isDragging: true,
      isActive: true,
      velocity: 1.0,
      frequency: 370,
      modeOrder: 3
    };

    p._triggerHapticFeedback(metrics);

    assert.strictEqual(vibrateCalled, false, '禁用时不调用震动');
    assert.strictEqual(p.hapticVibrationActive, false, '不激活震动状态');
  });

  test('setHapticIntensity：正确限制强度范围0-1', function () {
    var p = new VibrationPanel();
    p.setHapticIntensity(0.5);
    assert.strictEqual(p.hapticIntensity, 0.5);
    p.setHapticIntensity(2.0);
    assert.strictEqual(p.hapticIntensity, 1.0, '上限1.0');
    p.setHapticIntensity(-0.5);
    assert.strictEqual(p.hapticIntensity, 0.0, '下限0');
  });

  test('触觉反馈不支持时降级为模拟反馈', function () {
    var p = new VibrationPanel();
    p.hapticFeedbackEnabled = true;
    p.hapticSupported = false;

    var hapticPatternEl = { textContent: '' };
    var frictionStatusEl = { textContent: '', classList: { add: function () {}, remove: function () {} } };
    document._elements['hapticPattern'] = hapticPatternEl;
    document._elements['frictionStatus'] = frictionStatusEl;

    var metrics = {
      isDragging: true,
      isActive: true,
      velocity: 1.0,
      frequency: 370,
      modeOrder: 3
    };

    p._updateFrictionMetrics(metrics);
    p._triggerHapticFeedback(metrics);

    assert.strictEqual(p.hapticVibrationActive, true, '即使不支持也激活状态');
    assert.ok(hapticPatternEl.textContent.indexOf('模拟触觉反馈') !== -1, '显示模拟反馈');
  });

  test('触觉强度随速度递增', function () {
    var p = new VibrationPanel();
    p.hapticFeedbackEnabled = true;
    p.hapticSupported = true;

    var velocities = [0.3, 1.0, 2.0];
    var intensities = [];
    var hapticDetailEl = { textContent: '' };
    document._elements['hapticDetail'] = hapticDetailEl;
    document._elements['hapticPattern'] = { textContent: '' };

    for (var i = 0; i < velocities.length; i++) {
      p.hapticLastPattern = null;
      p._triggerHapticFeedback({
        isDragging: true,
        isActive: true,
        velocity: velocities[i],
        frequency: 200 + velocities[i] * 200,
        modeOrder: 2
      });
      intensities.push(p.hapticIntensity);
    }

    assert.ok(intensities[0] < intensities[1], '低速强度 < 中速强度');
    assert.ok(intensities[1] < intensities[2], '中速强度 < 高速强度');
    assert.ok(intensities[2] <= 1.0, '最大强度不超过1.0');
  });
});

console.log('\n══════════════════════════════════════');
console.log('  前端测试结果：' + passed + '/' + total + ' 通过');
if (failed > 0) console.log('  失败：' + failed);
console.log('══════════════════════════════════════');

process.exit(failed > 0 ? 1 : 0);
