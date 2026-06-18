var EraComparator = (function () {

  function EraComparator() {
    this.radarChart = null;
    this.comparisonResult = null;
    this.onResult = null;
  }

  EraComparator.prototype.init = function (chartEl) {
    if (chartEl && typeof Chart !== 'undefined') {
      var ctx = chartEl.getContext ? chartEl.getContext('2d') : null;
      if (ctx) {
        this.radarChart = new Chart(ctx, {
          type: 'radar',
          data: {
            labels: ['振动频率', '喷水高度', '粒子直径', '能量效率', '工艺复杂度', '文化历史价值'],
            datasets: [
              { label: '古代鱼洗', backgroundColor: 'rgba(255,99,132,0.2)', borderColor: 'rgba(255,99,132,1)', data: [0, 0, 0, 0, 0, 0] },
              { label: '现代雾化器', backgroundColor: 'rgba(54,162,235,0.2)', borderColor: 'rgba(54,162,235,1)', data: [0, 0, 0, 0, 0, 0] }
            ]
          },
          options: { responsive: true, scales: { r: { min: 0, max: 100 } } }
        });
      }
    }
  };

  EraComparator.prototype.calculateFallback = function (ancientFreq, ancientSprayCm) {
    var freq = ancientFreq || 285.6;
    var spray = ancientSprayCm || 15.2;
    var usFreq = 1.7e6;
    var usParticle = 3.5;
    var usPower = 20.0;
    var usFlowMlMin = 350.0 / 60.0;

    var ancientParticle = Math.min(5000.0, Math.max(0.05, spray * 0.008) * 10000.0 * 1000.0);
    var ancientFlow = Math.max(1.0, spray * 0.45);
    var ancientEnergy = freq * 0.00042 / Math.max(0.1, ancientFlow);
    var modernEnergy = usPower / Math.max(0.1, usFlowMlMin);
    var effRatio = ancientEnergy / Math.max(1e-6, modernEnergy);

    var logFreqMax = Math.log10(usFreq);
    var radar = [
      { label: '振动频率', ancient: Math.log10(freq) / logFreqMax * 100.0, modern: 100.0 },
      { label: '喷水/雾化高度', ancient: Math.min(100.0, spray * 5.0), modern: 2.0 },
      { label: '粒子直径', ancient: Math.min(100.0, ancientParticle / 50.0), modern: usParticle / 50.0 * 100.0 },
      { label: '能量效率(ml/J)', ancient: Math.min(100.0, 1.0 / Math.max(0.001, ancientEnergy) * 5.0), modern: Math.min(100.0, 1.0 / Math.max(0.001, modernEnergy) * 5.0) },
      { label: '工艺复杂度', ancient: 95.0, modern: 65.0 },
      { label: '文化历史价值', ancient: 100.0, modern: 25.0 }
    ];

    return {
      ancientFishWash: {
        name: '汉代双鱼纹鱼洗',
        era: '汉代',
        frequencyHz: freq,
        particleSizeMicrons: ancientParticle,
        waterSprayHeightCm: spray,
        waterFlowRateMlMin: ancientFlow,
        energyInputW: 8.5
      },
      modernUltrasonic: {
        name: '现代家用压电陶瓷超声波雾化器',
        era: '现代消费电子',
        frequencyHz: usFreq,
        particleSizeMicrons: usParticle,
        waterSprayHeightCm: 0.1,
        waterFlowRateMlMin: usFlowMlMin,
        energyInputW: usPower
      },
      radarComparison: radar.map(function (r) {
        return { label: r.label, ancientValueNormalized: r.ancient, modernValueNormalized: r.modern, dimension: r.label.toLowerCase().replace(/\//g, '') };
      }),
      energyEfficiency: { ancientJoulesPerMl: ancientEnergy, modernJoulesPerMl: modernEnergy, efficiencyRatio: effRatio },
      eraInterpretation: '古代鱼洗以人体摩擦产生' + freq.toFixed(1) + 'Hz低频共振喷水，现代雾化器以' + (usFreq / 1e6).toFixed(1) + 'MHz高频超声雾化微米级雾粒。',
      vibrationParadigmDifference: '振动范式根本差异：鱼洗=粘滑摩擦(宽频)  vs  雾化器=压电电致伸缩(窄带纯音)；频率差幅约4个数量级。'
    };
  };

  EraComparator.prototype.render = function (result, containerEl) {
    if (!containerEl || !result) return;
    this.comparisonResult = result;

    var ancient = result.ancientFishWash || result.ancientProfile || {};
    var modern = result.modernUltrasonic || result.modernProfile || {};
    var radar = result.radarComparison || result.radarData || [];
    var eff = result.energyEfficiency || result.energyEfficiencyComparison || {};

    var ancientFreqStr = ancient.frequencyHz
      ? (ancient.frequencyHz >= 1e6 ? (ancient.frequencyHz / 1e6).toFixed(1) + ' MHz' : ancient.frequencyHz.toFixed(0) + ' Hz')
      : '--';
    var modernFreqStr = modern.frequencyHz
      ? (modern.frequencyHz >= 1e6 ? (modern.frequencyHz / 1e6).toFixed(1) + ' MHz' : modern.frequencyHz.toFixed(0) + ' Hz')
      : '--';

    containerEl.innerHTML =
      '<div class="era-compare-result">' +
      '<h4>跨时代振动技术对比</h4>' +
      '<div class="row"><div class="col-md-6"><h5>' + (ancient.name || '古代鱼洗') + ' <small>(' + (ancient.era || '') + ')</small></h5>' +
      '<p>频率：' + ancientFreqStr + '</p>' +
      '<p>粒子尺寸：' + (ancient.particleSizeMicrons != null ? ancient.particleSizeMicrons.toFixed(0) + ' μm' : '--') + '</p>' +
      '<p>喷水高度：' + (ancient.waterSprayHeightCm != null ? ancient.waterSprayHeightCm.toFixed(1) + ' cm' : '--') + '</p>' +
      '<p>能量输入：' + (ancient.energyInputW != null ? ancient.energyInputW.toFixed(1) + ' W' : '--') + '</p></div>' +
      '<div class="col-md-6"><h5>' + (modern.name || '现代雾化器') + ' <small>(' + (modern.era || '') + ')</small></h5>' +
      '<p>频率：' + modernFreqStr + '</p>' +
      '<p>粒子尺寸：' + (modern.particleSizeMicrons != null ? modern.particleSizeMicrons.toFixed(1) + ' μm' : '--') + '</p>' +
      '<p>喷水高度：' + (modern.waterSprayHeightCm != null ? modern.waterSprayHeightCm.toFixed(1) + ' cm' : '--') + '</p>' +
      '<p>能量输入：' + (modern.energyInputW != null ? modern.energyInputW.toFixed(1) + ' W' : '--') + '</p></div></div>' +
      '<div class="mt-3"><p><strong>能量效率比(古代:现代) = 1:' + (eff.efficiencyRatio ? (1.0 / Math.max(1e-3, eff.efficiencyRatio)).toFixed(2) : '--') + '</strong></p>' +
      '<p class="era-interpretation">' + (result.eraInterpretation || '') + '</p>' +
      '<pre class="paradigm-diff">' + (result.vibrationParadigmDifference || '') + '</pre></div></div>';

    if (this.radarChart && radar.length >= 6) {
      this.radarChart.data.datasets[0].data = radar.map(function (r) {
        return r.ancientValueNormalized != null ? r.ancientValueNormalized : (r.ancientValue != null ? r.ancientValue : 0);
      });
      this.radarChart.data.datasets[1].data = radar.map(function (r) {
        return r.modernValueNormalized != null ? r.modernValueNormalized : (r.modernValue != null ? r.modernValue : 0);
      });
      this.radarChart.update();
    }

    if (typeof this.onResult === 'function') {
      this.onResult(result);
    }
  };

  EraComparator.prototype.fetchAndRender = function (ancientDeviceId, containerEl) {
    var self = this;
    var deviceId = ancientDeviceId || 1;
    if (typeof fetch === 'undefined') {
      var fallback = this.calculateFallback();
      this.render(fallback, containerEl);
      return;
    }
    fetch('/api/era-comparator/compare?ancientDeviceId=' + deviceId)
      .then(function (r) { return r.json(); })
      .then(function (resp) {
        var result = resp && resp.data ? resp.data : self.calculateFallback();
        self.render(result, containerEl);
      }).catch(function () {
        var fallback = self.calculateFallback();
        self.render(fallback, containerEl);
      });
  };

  return EraComparator;
})();

if (typeof module !== 'undefined' && module.exports) {
  module.exports = EraComparator;
}
