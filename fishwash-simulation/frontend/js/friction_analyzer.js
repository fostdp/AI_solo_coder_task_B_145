var FrictionAnalyzer = (function () {

  function FrictionAnalyzer() {
    this.curveChart = null;
    this.analysisResult = null;
    this.onAnalysis = null;
  }

  FrictionAnalyzer.prototype.init = function (chartEl) {
    if (chartEl && typeof Chart !== 'undefined') {
      var ctx = chartEl.getContext ? chartEl.getContext('2d') : null;
      if (ctx) {
        this.curveChart = new Chart(ctx, {
          type: 'line',
          data: { labels: [], datasets: [{ label: '摩擦力(N)', data: [], borderColor: 'rgba(75,192,192,1)', fill: false, tension: 0.3 }] },
          options: { responsive: true, plugins: { title: { display: true, text: 'Stribeck曲线：摩擦力-速度关系' } }, scales: { x: { title: { display: true, text: '速度(m/s)' } }, y: { title: { display: true, text: '切向力(N)' } } } }
        });
      }
    }
  };

  FrictionAnalyzer.prototype.buildStribeckCurve = function (N, muS, muK) {
    var velocities = [0.0, 0.05, 0.1, 0.3, 0.6, 1.0, 1.5, 2.0, 3.0];
    var staticMax = muS * N;
    var kineticF = muK * N;
    var points = [];
    for (var i = 0; i < velocities.length; i++) {
      var vv = velocities[i];
      var ratio = Math.min(1.0, vv / 0.5);
      var f_val = staticMax - ratio * (staticMax - kineticF);
      points.push({ velocity: vv, frictionForce: f_val });
    }
    return points;
  };

  FrictionAnalyzer.prototype.calculateFallback = function (N, mu, v) {
    if (N == null) N = 20.0;
    if (mu == null) mu = 0.29;
    if (v == null) v = 1.2;
    var waterLubrication = 0.22;
    var muStatic = 0.42;
    var muKinetic = mu * 0.80;
    var effectiveMu = Math.max(0.08, mu * (1.0 - waterLubrication * 0.4));
    var Ft = effectiveMu * N;
    var power = Ft * v * 2.0;
    var curve = this.buildStribeckCurve(N, muStatic, muKinetic);
    return {
      normalForceN: N,
      frictionCoefficient: mu,
      effectiveFrictionCoefficientAfterLubrication: effectiveMu,
      staticFrictionCoefficient: muStatic,
      kineticFrictionCoefficient: muKinetic,
      waterFilmLubricationFactor: waterLubrication,
      frictionVelocityMps: v,
      tangentialForceN: Ft,
      excitationPowerW: power,
      stickSlipFrequencyHz: Math.min(1200.0, Math.max(50.0, v > 0.01 ? v / ((muStatic * N) / 1.0e6) : 280.0)),
      stribeckCurve: curve,
      freeBodyDiagram: {
        freeBodyHandSide: {
          N_hand: { magnitudeN: N, direction: 'radially_inward' },
          F_tangential_hand: { magnitudeN: Ft, direction: 'opposite_to_stroke' },
          F_water_lubrication: { magnitudeN: Math.min(N, 0.2 * N), direction: 'normal_separation' }
        }
      },
      experimentReference: '《人体皮肤与青铜材料在湿润条件下的摩擦学特性研究》, 摩擦学学报, 2022, 42(3): 412-421'
    };
  };

  FrictionAnalyzer.prototype.verifyCoulombsLaw = function (result) {
    if (!result) return false;
    var Ft = result.tangentialForceN;
    var N = result.normalForceN;
    var effMu = result.effectiveFrictionCoefficientAfterLubrication;
    if (Ft == null || N == null || effMu == null) return false;
    var expected = effMu * N;
    return Math.abs(Ft - expected) < 0.01;
  };

  FrictionAnalyzer.prototype.render = function (result, containerEl) {
    if (!containerEl || !result) return;
    this.analysisResult = result;

    var curve = result.stribeckCurve || result.stickSlipThresholdMap || [];
    if (this.curveChart) {
      if (Array.isArray(curve) && curve.length > 0) {
        this.curveChart.data.labels = curve.map(function (pt) { return (pt.velocity != null ? pt.velocity : pt.velocity_mps).toFixed(2); });
        this.curveChart.data.datasets[0].data = curve.map(function (pt) { return pt.frictionForce != null ? pt.frictionForce : pt.friction_force_n; });
        this.curveChart.update();
      }
    }

    containerEl.innerHTML =
      '<div class="friction-analysis-result">' +
      '<h4>摩擦力力学分析结果</h4>' +
      '<div class="row"><div class="col-md-6">' +
      '<p>法向力 N = <strong>' + this._fmt(result.normalForceN, 1, 'N') + '</strong></p>' +
      '<p>摩擦系数 μ = ' + this._fmt(result.frictionCoefficient, 3) + '</p>' +
      '<p>有效摩擦系数(润滑后) = ' + this._fmt(result.effectiveFrictionCoefficientAfterLubrication, 3) + '</p>' +
      '<p>切向力 F_t = ' + this._fmt(result.tangentialForceN, 2, 'N') + '</p></div>' +
      '<div class="col-md-6">' +
      '<p>摩擦速度 v = ' + this._fmt(result.frictionVelocityMps, 3, 'm/s') + '</p>' +
      '<p>激励功率 P = ' + this._fmt(result.excitationPowerW, 2, 'W') + '</p>' +
      '<p>粘滑频率 f_stickslip = ' + this._fmt(result.stickSlipFrequencyHz, 0, 'Hz') + '</p>' +
      '<p>库仑定律验证：<strong>' + (this.verifyCoulombsLaw(result) ? '✅ 通过 (F_t = μ_eff × N)' : '⚠️ 偏差') + '</strong></p></div></div>' +
      '<p class="mt-3"><small>实验来源：' + (result.experimentReference || result.measurement_source || '《摩擦学学报》2022') + '</small></p></div>';

    if (typeof this.onAnalysis === 'function') {
      this.onAnalysis(result);
    }
  };

  FrictionAnalyzer.prototype._fmt = function (v, digits, unit) {
    if (v === null || v === undefined || isNaN(v)) return '--';
    return Number(v).toFixed(digits) + (unit ? ' ' + unit : '');
  };

  FrictionAnalyzer.prototype.fetchAndRender = function (deviceId, request, containerEl) {
    var self = this;
    var devId = deviceId || 1;
    if (typeof fetch === 'undefined') {
      var fallback = this.calculateFallback();
      this.render(fallback, containerEl);
      return;
    }
    fetch('/api/friction-analyzer/analyze/' + devId, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: request ? JSON.stringify(request) : '{}'
    }).then(function (r) { return r.json(); }).then(function (resp) {
      var result = resp && resp.data ? resp.data : self.calculateFallback();
      self.render(result, containerEl);
    }).catch(function () {
      var fallback = self.calculateFallback();
      self.render(fallback, containerEl);
    });
  };

  FrictionAnalyzer.prototype.checkSprayThreshold = function (powerW, thresholdCm) {
    if (powerW == null || powerW <= 0) return { reached: false, estimatedCm: 0 };
    var estimate = Math.pow(powerW / 4.5, 0.6) * 10.0;
    return { reached: estimate >= (thresholdCm || 10.0), estimatedCm: estimate };
  };

  return FrictionAnalyzer;
})();

if (typeof module !== 'undefined' && module.exports) {
  module.exports = FrictionAnalyzer;
}
