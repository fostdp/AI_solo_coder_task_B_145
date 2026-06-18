var assert = require('assert');
var path = require('path');
var fs = require('fs');

global.navigator = { vibrate: function () {} };
if (typeof global.document === 'undefined') {
  global.document = { getElementById: function () { return null; }, createElement: function () { return null; } };
}

var ShapeComparator = require(path.join(__dirname, '..', 'js', 'shape_comparator.js'));
var EraComparator = require(path.join(__dirname, '..', 'js', 'era_comparator.js'));
var FrictionAnalyzer = require(path.join(__dirname, '..', 'js', 'friction_analyzer.js'));
var VRFishBasin = require(path.join(__dirname, '..', 'js', 'vr_fish_basin.js'));

function suite(name, fn) {
  console.log('\n' + name);
  fn();
}
function test(name, fn) {
  try { fn(); console.log('  ✓ ' + name); }
  catch (e) { console.log('  ✗ ' + name + '\n    ' + e.message); process.exitCode = 1; }
}

suite('ShapeComparator — 形状对比独立组件', function () {
  test('构造函数正确初始化', function () {
    var s = new ShapeComparator();
    assert.strictEqual(s.selectedBasinShape, 'circle');
    assert.strictEqual(s.compareResult, null);
    assert.strictEqual(s.onShapeChange, null);
    assert.strictEqual(typeof s.calculateFallback, 'function');
    assert.strictEqual(typeof s.render, 'function');
    assert.strictEqual(typeof s.formatValue, 'function');
  });

  test('formatValue：正常格式化 / null/undefined → -- / NaN → --', function () {
    var s = new ShapeComparator();
    assert.strictEqual(s.formatValue(123.456, 2), '123.46');
    assert.strictEqual(s.formatValue(123.456, 2, 'Hz'), '123.46 Hz');
    assert.strictEqual(s.formatValue(null), '--');
    assert.strictEqual(s.formatValue(undefined), '--');
    assert.strictEqual(s.formatValue(NaN), '--');
  });

  test('calculateFallback：方形频率 > 圆形频率（角部刚度集中）', function () {
    var s = new ShapeComparator();
    var r = s.calculateFallback({ modeOrder: 2 });
    assert.ok(r.circleBasin.wetResonanceFreq > 5, '圆形频率>5Hz（薄板近似）');
    assert.ok(r.squareBasin.wetResonanceFreq > r.circleBasin.wetResonanceFreq, '方形>圆形');
    var ratio = r.squareBasin.wetResonanceFreq / r.circleBasin.wetResonanceFreq;
    assert.ok(ratio > 1.0, '频率比>1.0，实际=' + ratio);
    assert.ok(r.shapeEffectDescription.indexOf('方形') !== -1);
    assert.ok(r.modePhysicalInterpretation.indexOf('节径') !== -1);
  });

  test('calculateFallback：高阶模态(4阶)频率>低阶(2阶)', function () {
    var s = new ShapeComparator();
    var low = s.calculateFallback({ modeOrder: 2 });
    var high = s.calculateFallback({ modeOrder: 4 });
    assert.ok(high.circleBasin.wetResonanceFreq > low.circleBasin.wetResonanceFreq * 2.5,
      '4阶>2.5×2阶');
    assert.ok(high.squareBasin.wetResonanceFreq > low.squareBasin.wetResonanceFreq * 2.5);
  });

  test('calculateFallback：边界值极大R频率低，极小t频率低', function () {
    var s = new ShapeComparator();
    var bigR = s.calculateFallback({ circleRadius: 0.5 });
    var smallR = s.calculateFallback({ circleRadius: 0.1 });
    assert.ok(bigR.circleBasin.wetResonanceFreq < smallR.circleBasin.wetResonanceFreq, '大R→低频');

    var thickT = s.calculateFallback({ thickness: 0.005 });
    var thinT = s.calculateFallback({ thickness: 0.001 });
    assert.ok(thinT.circleBasin.wetResonanceFreq < thickT.circleBasin.wetResonanceFreq, '薄板→低频');
  });

  test('syncShapeRadio：绑定change事件切换selectedBasinShape', function () {
    var s = new ShapeComparator();
    var fired = null;
    s.onShapeChange = function (v) { fired = v; };
    var radio = { checked: true, value: 'square', addEventListener: function (evt, fn) { this._fn = fn; } };
    s.syncShapeRadio(radio);
    assert.strictEqual(s.selectedBasinShape, 'square');
    radio.checked = true; radio.value = 'circle';
    radio._fn();
    assert.strictEqual(fired, 'circle');
    assert.strictEqual(s.selectedBasinShape, 'circle');
  });

  test('render：输出包含频率/阻尼/流体耦合关键信息', function () {
    var s = new ShapeComparator();
    var el = { innerHTML: '' };
    var r = s.calculateFallback({ modeOrder: 2 });
    s.render(r, el);
    assert.ok(el.innerHTML.indexOf('形状对比结果') !== -1);
    assert.ok(el.innerHTML.indexOf('圆形鱼洗') !== -1);
    assert.ok(el.innerHTML.indexOf('方形鱼洗') !== -1);
    assert.ok(el.innerHTML.indexOf('湿频') !== -1);
    assert.ok(el.innerHTML.indexOf('阻尼比') !== -1);
  });
});

suite('EraComparator — 跨时代对比独立组件', function () {
  test('构造函数正确初始化', function () {
    var e = new EraComparator();
    assert.strictEqual(e.comparisonResult, null);
    assert.strictEqual(typeof e.calculateFallback, 'function');
    assert.strictEqual(typeof e.render, 'function');
    assert.strictEqual(typeof e.fetchAndRender, 'function');
  });

  test('calculateFallback：现代频率为MHz级，古代为百Hz级', function () {
    var e = new EraComparator();
    var r = e.calculateFallback();
    assert.ok(r.ancientFishWash.frequencyHz >= 100 && r.ancientFishWash.frequencyHz <= 800, '古代100-800Hz');
    assert.ok(r.modernUltrasonic.frequencyHz >= 1e6, '现代≥1MHz');
    assert.ok(r.modernUltrasonic.frequencyHz / r.ancientFishWash.frequencyHz >= 1000, '频率差≥1000倍');
  });

  test('calculateFallback：6维雷达数据完整，归一化0-100', function () {
    var e = new EraComparator();
    var r = e.calculateFallback();
    assert.strictEqual(r.radarComparison.length, 6);
    r.radarComparison.forEach(function (pt) {
      assert.ok(pt.ancientValueNormalized >= 0 && pt.ancientValueNormalized <= 100, '古代值0-100，' + pt.label + '=' + pt.ancientValueNormalized);
      assert.ok(pt.modernValueNormalized >= 0 && pt.modernValueNormalized <= 100, '现代值0-100');
    });
  });

  test('calculateFallback：能量效率比 现代显著优于古代', function () {
    var e = new EraComparator();
    var r = e.calculateFallback();
    var eff = r.energyEfficiency;
    assert.ok(eff.ancientJoulesPerMl > 0);
    assert.ok(eff.modernJoulesPerMl > 0);
    assert.ok(eff.efficiencyRatio > 0 && eff.efficiencyRatio < 1,
      'efficiencyRatio=古代/现代 < 1（现代更优），实际=' + eff.efficiencyRatio);
    var modernVsAncient = 1.0 / Math.max(1e-6, eff.efficiencyRatio);
    assert.ok(modernVsAncient > 50, '现代效率/古代效率 > 50倍，实际=' + modernVsAncient.toFixed(1));
  });

  test('calculateFallback：粒子尺寸差异>100倍', function () {
    var e = new EraComparator();
    var r = e.calculateFallback();
    var ancientUm = r.ancientFishWash.particleSizeMicrons;
    var modernUm = r.modernUltrasonic.particleSizeMicrons;
    assert.ok(ancientUm / modernUm > 100, '古代粒子:现代>100倍，实际=' + ancientUm + ':' + modernUm);
  });

  test('calculateFallback：喷水高度 古代>现代', function () {
    var e = new EraComparator();
    var r = e.calculateFallback();
    assert.ok(r.ancientFishWash.waterSprayHeightCm > r.modernUltrasonic.waterSprayHeightCm * 10, '古代喷水远大于现代');
  });

  test('calculateFallback：范式差异与物理解读文本非空', function () {
    var e = new EraComparator();
    var r = e.calculateFallback();
    assert.ok(r.eraInterpretation.length > 30);
    assert.ok(r.vibrationParadigmDifference.length > 30);
    assert.ok(r.eraInterpretation.indexOf('MHz') !== -1 || r.eraInterpretation.indexOf('Hz') !== -1);
  });

  test('render：输出卡片含古代/现代关键指标', function () {
    var e = new EraComparator();
    var el = { innerHTML: '' };
    var r = e.calculateFallback();
    e.render(r, el);
    assert.ok(el.innerHTML.indexOf('跨时代振动技术对比') !== -1);
    assert.ok(el.innerHTML.indexOf('古代鱼洗') !== -1);
    assert.ok(el.innerHTML.indexOf('现代雾化器') !== -1);
    assert.ok(el.innerHTML.indexOf('能量效率比') !== -1);
  });
});

suite('FrictionAnalyzer — 摩擦力分析独立组件', function () {
  test('构造函数正确初始化', function () {
    var f = new FrictionAnalyzer();
    assert.strictEqual(f.analysisResult, null);
    assert.strictEqual(typeof f.calculateFallback, 'function');
    assert.strictEqual(typeof f.buildStribeckCurve, 'function');
    assert.strictEqual(typeof f.verifyCoulombsLaw, 'function');
    assert.strictEqual(typeof f.checkSprayThreshold, 'function');
  });

  test('calculateFallback：库仑摩擦定律 F_t = μ_eff × N', function () {
    var f = new FrictionAnalyzer();
    var r = f.calculateFallback(20, 0.29, 1.2);
    assert.ok(r.tangentialForceN > 0);
    var expected = r.effectiveFrictionCoefficientAfterLubrication * r.normalForceN;
    assert.ok(Math.abs(r.tangentialForceN - expected) < 0.01, 'F_t = μ_eff × N，实际=' + r.tangentialForceN + '，期望=' + expected);
  });

  test('verifyCoulombsLaw：正常数据返回true', function () {
    var f = new FrictionAnalyzer();
    var r = f.calculateFallback();
    assert.strictEqual(f.verifyCoulombsLaw(r), true);
    assert.strictEqual(f.verifyCoulombsLaw({ tangentialForceN: 1, normalForceN: 10, effectiveFrictionCoefficientAfterLubrication: 0.2 }), false);
  });

  test('buildStribeckCurve：返回9个速度点，摩擦力递减', function () {
    var f = new FrictionAnalyzer();
    var curve = f.buildStribeckCurve(20, 0.42, 0.23);
    assert.strictEqual(curve.length, 9);
    for (var i = 1; i < curve.length; i++) {
      assert.ok(curve[i].frictionForce <= curve[i - 1].frictionForce + 0.01, 'Stribeck曲线递减');
    }
    assert.ok(curve[0].frictionForce > curve[curve.length - 1].frictionForce, '静摩擦>动摩擦');
  });

  test('calculateFallback：法向力越大切向力越大（线性递增）', function () {
    var f = new FrictionAnalyzer();
    var r10 = f.calculateFallback(10, 0.29, 1.2);
    var r50 = f.calculateFallback(50, 0.29, 1.2);
    assert.ok(r50.tangentialForceN > r10.tangentialForceN * 4, '5倍N→约5倍F_t');
  });

  test('calculateFallback：功率正比于速度', function () {
    var f = new FrictionAnalyzer();
    var vslow = f.calculateFallback(20, 0.29, 0.1);
    var vfast = f.calculateFallback(20, 0.29, 2.5);
    assert.ok(vfast.excitationPowerW > vslow.excitationPowerW * 15, '高速功率>低速');
  });

  test('calculateFallback：粘滑频率50-1200Hz边界', function () {
    var f = new FrictionAnalyzer();
    var r = f.calculateFallback(20, 0.29, 0.001);
    assert.ok(r.stickSlipFrequencyHz >= 50, '极低速度时下限50Hz');
    var r2 = f.calculateFallback(20, 0.29, 10);
    assert.ok(r2.stickSlipFrequencyHz <= 1200, '极高速时上限1200Hz');
  });

  test('calculateFallback：有效摩擦系数 0.08 ≤ μ_eff ≤ μ_raw', function () {
    var f = new FrictionAnalyzer();
    var r = f.calculateFallback(20, 0.29, 1.2);
    assert.ok(r.effectiveFrictionCoefficientAfterLubrication >= 0.08, '≥下限0.08');
    assert.ok(r.effectiveFrictionCoefficientAfterLubrication < 0.29, '<原始μ（润滑减摩）');
    assert.ok(r.waterFilmLubricationFactor === 0.22);
  });

  test('checkSprayThreshold：功率≥4.5W喷水高度≥10cm', function () {
    var f = new FrictionAnalyzer();
    var low = f.checkSprayThreshold(2, 10);
    var high = f.checkSprayThreshold(10, 10);
    assert.strictEqual(low.reached, false);
    assert.strictEqual(high.reached, true);
    assert.ok(high.estimatedCm > low.estimatedCm);
  });

  test('render：包含库仑定律验证结果', function () {
    var f = new FrictionAnalyzer();
    var el = { innerHTML: '' };
    var r = f.calculateFallback();
    f.render(r, el);
    assert.ok(el.innerHTML.indexOf('摩擦力力学分析结果') !== -1);
    assert.ok(el.innerHTML.indexOf('库仑定律验证') !== -1);
    assert.ok(el.innerHTML.indexOf('通过') !== -1);
  });
});

suite('VRFishBasin — 虚拟交互独立组件', function () {
  test('构造函数正确初始化', function () {
    var v = new VRFishBasin();
    assert.strictEqual(v.state.velocity, 0);
    assert.strictEqual(v.state.frequency, 0);
    assert.strictEqual(v.state.modeOrder, 2);
    assert.strictEqual(v.hapticFeedbackEnabled, false);
    assert.strictEqual(typeof v.simulateFriction, 'function');
    assert.strictEqual(typeof v.getHapticPattern, 'function');
    assert.strictEqual(typeof v.renderMetrics, 'function');
  });

  test('simulateFriction：低速→2阶216Hz左右', function () {
    var v = new VRFishBasin();
    var s = v.simulateFriction({ velocity: 0.3, normalForce: 20, isDragging: true });
    assert.strictEqual(s.modeOrder, 2);
    assert.ok(s.frequency >= 200 && s.frequency <= 250, '216±34Hz，实际=' + s.frequency);
    assert.strictEqual(s.isDragging, true);
  });

  test('simulateFriction：中速→3阶370Hz基础+速度修正', function () {
    var v = new VRFishBasin();
    var s = v.simulateFriction({ velocity: 1.0, normalForce: 20, isDragging: true });
    assert.strictEqual(s.modeOrder, 3);
    assert.ok(s.frequency >= 360 && s.frequency <= 470, '370-470Hz，实际=' + s.frequency);
  });

  test('simulateFriction：高速→4阶590Hz基础+速度修正', function () {
    var v = new VRFishBasin();
    var s = v.simulateFriction({ velocity: 2.0, normalForce: 20, isDragging: true });
    assert.strictEqual(s.modeOrder, 4);
    assert.ok(s.frequency >= 580 && s.frequency <= 700, '580-700Hz，实际=' + s.frequency);
  });

  test('simulateFriction：喷水高度随速度递增', function () {
    var v = new VRFishBasin();
    var noMove = v.simulateFriction({ velocity: 0, normalForce: 20, isDragging: true });
    var mid = v.simulateFriction({ velocity: 1.0, normalForce: 20, isDragging: true });
    var high = v.simulateFriction({ velocity: 2.5, normalForce: 20, isDragging: true });
    assert.ok(noMove.sprayHeightCm < 1, '静止几乎不喷水');
    assert.ok(mid.sprayHeightCm > 10, '中速>10cm，实际=' + mid.sprayHeightCm);
    assert.ok(high.sprayHeightCm > 20, '高速>20cm，实际=' + high.sprayHeightCm);
    assert.ok(high.sprayHeightCm > mid.sprayHeightCm);
  });

  test('simulateFriction：振幅随速度递增', function () {
    var v = new VRFishBasin();
    var low = v.simulateFriction({ velocity: 0.3, normalForce: 20, isDragging: true });
    var high = v.simulateFriction({ velocity: 2.0, normalForce: 20, isDragging: true });
    assert.ok(high.amplitudeMm > low.amplitudeMm, '高速振幅>低速');
  });

  test('simulateFriction：未拖拽时无功率无喷水', function () {
    var v = new VRFishBasin();
    var s = v.simulateFriction({ velocity: 2.0, normalForce: 20, isDragging: false });
    assert.strictEqual(s.excitationPowerW, 0);
    assert.strictEqual(s.sprayHeightCm, 0);
    assert.strictEqual(s.tangentialForceN, s.effectiveFrictionCoefficient * s.normalForceN);
  });

  test('simulateFriction：阈值突破检测', function () {
    var v = new VRFishBasin();
    var low = v.simulateFriction({ velocity: 0.3, normalForce: 10, isDragging: true });
    var high = v.simulateFriction({ velocity: 2.0, normalForce: 30, isDragging: true });
    assert.strictEqual(low.sprayThresholdCrossed, false);
    assert.strictEqual(high.sprayThresholdCrossed, true);
  });

  test('getHapticPattern：低速→微震，中速→麻酥，高速→强震', function () {
    var v = new VRFishBasin();
    var slow = v.simulateFriction({ velocity: 0.3, normalForce: 20, isDragging: true });
    var pSlow = v.getHapticPattern(slow);
    assert.strictEqual(pSlow.feelDescription, '微震');
    assert.ok(Array.isArray(pSlow.vibrationPattern));

    var mid = v.simulateFriction({ velocity: 1.0, normalForce: 20, isDragging: true });
    var pMid = v.getHapticPattern(mid);
    assert.strictEqual(pMid.feelDescription, '麻酥感');
    assert.ok(pMid.vibrationPattern.length >= 4);

    var fast = v.simulateFriction({ velocity: 2.0, normalForce: 20, isDragging: true });
    var pFast = v.getHapticPattern(fast);
    assert.strictEqual(pFast.feelDescription, '强震');
    assert.ok(pFast.vibrationPattern.length >= 6);
  });

  test('getHapticPattern：阈值突破带庆祝模式', function () {
    var v = new VRFishBasin();
    var s = v.simulateFriction({ velocity: 2.5, normalForce: 40, isDragging: true });
    var p = v.getHapticPattern(s);
    assert.strictEqual(p.thresholdCrossed, true);
    assert.deepStrictEqual(p.celebrationPattern, [100, 50, 100, 50, 200]);
    assert.ok(p.fullDescription.indexOf('阈值突破') !== -1);
  });

  test('getHapticPattern：强度随速度递增', function () {
    var v = new VRFishBasin();
    var intensities = [];
    [0.3, 1.0, 2.0, 2.5].forEach(function (vel) {
      var s = v.simulateFriction({ velocity: vel, normalForce: 20, isDragging: true });
      intensities.push(v.getHapticPattern(s).intensity);
    });
    for (var i = 1; i < intensities.length; i++) {
      assert.ok(intensities[i] >= intensities[i - 1] - 0.01, '强度递增');
    }
    assert.ok(intensities[0] >= 0.1, '最小强度≥0.1');
    assert.ok(intensities[intensities.length - 1] <= 1.0, '最大强度≤1.0');
  });

  test('triggerHapticFeedback：启用时触发navigator.vibrate', function () {
    var called = null;
    var origVibrate = global.navigator.vibrate;
    global.navigator.vibrate = function (pat) { called = pat; };
    var v = new VRFishBasin();
    v.hapticFeedbackEnabled = true;
    var s = v.simulateFriction({ velocity: 1.0, normalForce: 20, isDragging: true });
    v.triggerHapticFeedback(s);
    assert.ok(Array.isArray(called), '调用了震动，called=' + JSON.stringify(called));
    global.navigator.vibrate = origVibrate;
  });

  test('triggerHapticFeedback：禁用时不触发', function () {
    var called = false;
    var origVibrate = global.navigator.vibrate;
    global.navigator.vibrate = function () { called = true; };
    var v = new VRFishBasin();
    v.hapticFeedbackEnabled = false;
    var s = v.simulateFriction({ velocity: 1.0, normalForce: 20, isDragging: true });
    var result = v.triggerHapticFeedback(s);
    assert.strictEqual(called, false);
    assert.strictEqual(result, null);
    global.navigator.vibrate = origVibrate;
  });

  test('triggerHapticFeedback：未拖拽时停止震动', function () {
    var called = null;
    var origVibrate = global.navigator.vibrate;
    global.navigator.vibrate = function (pat) { called = pat; };
    var v = new VRFishBasin();
    v.hapticFeedbackEnabled = true;
    var s = v.simulateFriction({ velocity: 1.0, normalForce: 20, isDragging: false });
    v.triggerHapticFeedback(s);
    assert.strictEqual(called, 0);
    global.navigator.vibrate = origVibrate;
  });

  test('setHapticEnabled：启用时提示震动', function () {
    var called = null;
    var origVibrate = global.navigator.vibrate;
    global.navigator.vibrate = function (pat) { called = pat; };
    var v = new VRFishBasin();
    v.setHapticEnabled(true);
    assert.deepStrictEqual(called, [50, 30, 50]);
    assert.strictEqual(v.hapticFeedbackEnabled, true);
    v.setHapticEnabled(false);
    assert.strictEqual(called, 0);
    assert.strictEqual(v.hapticFeedbackEnabled, false);
    global.navigator.vibrate = origVibrate;
  });

  test('renderMetrics：正确更新DOM文本，速度=0正常显示', function () {
    var v = new VRFishBasin();
    var els = {
      metricVelocity: { textContent: '' },
      metricFreq: { textContent: '' },
      metricMode: { textContent: '' },
      metricSpray: { textContent: '' },
      metricAmp: { textContent: '' },
      frictionStatus: { textContent: '' },
      hapticPattern: { textContent: '' },
      hapticDetail: { textContent: '' }
    };
    var s = {
      velocity: 0,
      frequency: 0,
      modeOrder: 2,
      sprayHeightCm: 0,
      amplitudeMm: 0,
      isDragging: false,
      isActive: true
    };
    v.renderMetrics(s, els);
    assert.strictEqual(els.metricVelocity.textContent, '0.000 m/s', '零速度正常显示而非--');
    assert.strictEqual(els.metricFreq.textContent, '0 Hz');
    assert.ok(els.frictionStatus.textContent.indexOf('待机') !== -1 || els.frictionStatus.textContent.indexOf('等待') !== -1);
  });

  test('calculateSprayFromPower / estimateModeFromVelocity 静态方法', function () {
    assert.strictEqual(VRFishBasin.calculateSprayFromPower(0), 0);
    assert.strictEqual(VRFishBasin.calculateSprayFromPower(null), 0);
    assert.ok(VRFishBasin.calculateSprayFromPower(10) > 15, '10W→>15cm');
    assert.strictEqual(VRFishBasin.estimateModeFromVelocity(0), 2);
    assert.strictEqual(VRFishBasin.estimateModeFromVelocity(0.3), 2);
    assert.strictEqual(VRFishBasin.estimateModeFromVelocity(1.0), 3);
    assert.strictEqual(VRFishBasin.estimateModeFromVelocity(3.0), 4);
  });

  test('setIntensity：限制0-1范围', function () {
    var v = new VRFishBasin();
    v.setIntensity(0.5);
    assert.strictEqual(v.hapticIntensity, 0.5);
    v.setIntensity(2);
    assert.strictEqual(v.hapticIntensity, 1.0);
    v.setIntensity(-1);
    assert.strictEqual(v.hapticIntensity, 0.0);
  });
});

console.log('\n═══════════════════════════════════════════════');
console.log('  新前端组件测试结果：' + (process.exitCode ? '部分失败' : '全部通过'));
console.log('═══════════════════════════════════════════════');
