var ShapeComparator = (function () {

  function ShapeComparator() {
    this.selectedBasinShape = 'circle';
    this.compareResult = null;
    this.chart = null;
    this.onShapeChange = null;
    this.onCompareResult = null;
  }

  ShapeComparator.prototype.init = function (chartEl) {
    if (chartEl && typeof Chart !== 'undefined') {
      var ctx = chartEl.getContext ? chartEl.getContext('2d') : null;
      if (ctx) {
        this.chart = new Chart(ctx, {
          type: 'bar',
          data: {
            labels: ['圆形湿频(Hz)', '方形湿频(Hz)', '阻尼比(×100)', '流体耦合因子(×100)'],
            datasets: [
              { label: '圆形', backgroundColor: 'rgba(54,162,235,0.6)', data: [0, 0, 0, 0] },
              { label: '方形', backgroundColor: 'rgba(255,159,64,0.6)', data: [0, 0, 0, 0] }
            ]
          },
          options: { responsive: true, plugins: { title: { display: true, text: '形状对比：圆形 vs 方形共振特性' } } }
        });
      }
    }
  };

  ShapeComparator.prototype.syncShapeRadio = function (radioEl) {
    var self = this;
    if (!radioEl) return;
    if (radioEl.checked) {
      this.selectedBasinShape = radioEl.value;
    }
    radioEl.addEventListener('change', function () {
      if (radioEl.checked) {
        self.selectedBasinShape = radioEl.value;
        if (typeof self.onShapeChange === 'function') {
          self.onShapeChange(self.selectedBasinShape);
        }
      }
    });
  };

  ShapeComparator.prototype.formatValue = function (v, digits, unit) {
    if (v === null || v === undefined || isNaN(v)) return '--';
    if (!digits) digits = 2;
    if (!unit) unit = '';
    return Number(v).toFixed(digits) + (unit ? ' ' + unit : '');
  };

  ShapeComparator.prototype.calculateFallback = function (params) {
    params = params || {};
    var modeOrder = params.modeOrder || 2;
    var E = params.elasticModulus != null ? params.elasticModulus : 1.02e11;
    var nu = params.poissonRatio != null ? params.poissonRatio : 0.34;
    var rho = params.materialDensity != null ? params.materialDensity : 8570.0;
    var t = params.thickness != null ? params.thickness : 0.0023;
    var R = params.circleRadius != null ? params.circleRadius : 0.180;
    var a = params.squareSide != null ? params.squareSide : 0.318;
    var h = params.waterDepth != null ? params.waterDepth : 0.08;

    var rhoWater = 1000.0;
    var aleTransitionRatio = 0.3;
    var D = E * Math.pow(t, 3) / (12.0 * (1.0 - nu * nu));

    var lambdaN = Math.pow(modeOrder, 2) * (Math.pow(modeOrder, 2) - 1);
    var fDry_circle = (lambdaN / (2.0 * Math.PI)) * Math.sqrt(D / (rho * t * R * R));
    var coupling_circle = 1.0 / Math.sqrt(1.0 + rhoWater * R / (rho * t * modeOrder));
    var aleFactor = 1.0 - aleTransitionRatio * 0.5;
    var fWet_circle = fDry_circle * coupling_circle * aleFactor;

    var lambdaSquare = Math.pow((modeOrder * Math.PI / a), 2) + Math.pow((modeOrder * Math.PI / a), 2);
    var lambdaMN = lambdaSquare * Math.sqrt(D / (rho * t));
    var fDry_square = lambdaMN / (2.0 * Math.PI);
    var perimeter = 4.0 * a;
    var area = a * a;
    var addedMass = rhoWater * h * perimeter / (rho * t * area * modeOrder);
    var coupling_square = 1.0 / Math.sqrt(1.0 + Math.abs(addedMass));
    var cornerStiffness = 1.0 + 0.05 * Math.log(modeOrder + 1);
    var fWet_square = fDry_square * coupling_square * aleFactor * cornerStiffness;

    var freqRatio = fWet_square / fWet_circle;
    var conclusion = freqRatio > 1.0
      ? '方形鱼洗由于角部刚度集中效应，共振频率普遍高于圆形鱼洗 (f_sq/f_cir=' + freqRatio.toFixed(3) + ')'
      : '圆形鱼洗频率高于方形鱼洗';

    return {
      circleBasin: { wetResonanceFreq: fWet_circle, dryResonanceFreq: fDry_circle, fluidCouplingFactor: coupling_circle * aleFactor, dampingRatio: 0.01 + 0.005 * modeOrder },
      squareBasin: { wetResonanceFreq: fWet_square, dryResonanceFreq: fDry_square, fluidCouplingFactor: coupling_square * aleFactor * cornerStiffness, dampingRatio: 0.012 + 0.0045 * modeOrder },
      frequencyRatio: freqRatio.toFixed(4),
      shapeEffectDescription: conclusion,
      modePhysicalInterpretation: '圆形n=' + modeOrder + '阶对应' + modeOrder + '条节径；方形(m,n)=(' + modeOrder + ',' + modeOrder + ')对应正交节线'
    };
  };

  ShapeComparator.prototype.render = function (result, containerEl) {
    if (!containerEl || !result) return;
    this.compareResult = result;

    var c = result.circleBasin || {};
    var s = result.squareBasin || {};

    containerEl.innerHTML =
      '<div class="shape-compare-result">' +
      '<h4>形状对比结果</h4>' +
      '<p>频率比 f_sq/f_cir = <strong>' + (result.frequencyRatio || '--') + '</strong></p>' +
      '<p>' + (result.shapeEffectDescription || '') + '</p>' +
      '<div class="row"><div class="col-md-6"><h5>圆形鱼洗</h5>' +
      '<p>湿频：' + this.formatValue(c.wetResonanceFreq, 1, 'Hz') + '</p>' +
      '<p>干频：' + this.formatValue(c.dryResonanceFreq, 1, 'Hz') + '</p>' +
      '<p>阻尼比：' + this.formatValue(c.dampingRatio, 4) + '</p>' +
      '<p>流体耦合：' + this.formatValue(c.fluidCouplingFactor, 4) + '</p></div>' +
      '<div class="col-md-6"><h5>方形鱼洗</h5>' +
      '<p>湿频：' + this.formatValue(s.wetResonanceFreq, 1, 'Hz') + '</p>' +
      '<p>干频：' + this.formatValue(s.dryResonanceFreq, 1, 'Hz') + '</p>' +
      '<p>阻尼比：' + this.formatValue(s.dampingRatio, 4) + '</p>' +
      '<p>流体耦合：' + this.formatValue(s.fluidCouplingFactor, 4) + '</p></div></div>' +
      '<p class="mt-3"><em>' + (result.modePhysicalInterpretation || '') + '</em></p></div>';

    if (this.chart && c.wetResonanceFreq && s.wetResonanceFreq) {
      this.chart.data.datasets[0].data = [c.wetResonanceFreq, 0, (c.dampingRatio || 0) * 100, (c.fluidCouplingFactor || 0) * 100];
      this.chart.data.datasets[1].data = [0, s.wetResonanceFreq, (s.dampingRatio || 0) * 100, (s.fluidCouplingFactor || 0) * 100];
      this.chart.update();
    }

    if (typeof this.onCompareResult === 'function') {
      this.onCompareResult(result);
    }
  };

  ShapeComparator.prototype.fetchAndRender = function (request, containerEl) {
    var self = this;
    if (typeof fetch === 'undefined') {
      var fallback = this.calculateFallback(request || {});
      this.render(fallback, containerEl);
      return;
    }
    fetch('/api/shape-comparator/compare', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request)
    }).then(function (r) { return r.json(); }).then(function (resp) {
      var result = resp && resp.data ? resp.data : self.calculateFallback(request || {});
      self.render(result, containerEl);
    }).catch(function () {
      var fallback = self.calculateFallback(request || {});
      self.render(fallback, containerEl);
    });
  };

  return ShapeComparator;
})();

if (typeof module !== 'undefined' && module.exports) {
  module.exports = ShapeComparator;
}
