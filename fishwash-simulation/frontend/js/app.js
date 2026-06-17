var FishWashApp = (function () {
  function FishWashApp() {
    this.basin3d = null;
    this.panel = null;
  }

  FishWashApp.prototype.init = function () {
    this.basin3d = new FishBasin3D();
    this.panel = new VibrationPanel();

    var container = document.getElementById('viewport3d');
    this.basin3d.init(container);

    this.panel.onSimulate = function (modeOrder) {
      this.basin3d.startSimulation(modeOrder);
    }.bind(this);

    this.panel.onFrequencyChange = function (freq) {
      this.basin3d.setFrequency(freq);
    }.bind(this);

    this.panel.onSprayHeightChange = function (heightMeters) {
      this.basin3d.setSprayHeight(heightMeters);
    }.bind(this);

    this.panel.onAmplitudeChange = function (amp) {
      this.basin3d.setAmplitude(amp);
    }.bind(this);

    this.panel.onModeChange = function (n) {
      this.basin3d.currentModeOrder = n;
      if (this.basin3d.fishWashModel) {
        this.basin3d.fishWashModel.setModeOrder(n);
      }
    }.bind(this);

    this.panel.onDeviceChange = function (deviceId, deviceData) {
      this.basin3d.stopSimulation();
      if (deviceData && deviceData.basinShape) {
        this.basin3d.setBasinShape(deviceData.basinShape);
      }
    }.bind(this);

    this.panel.onShapeChange = function (shape) {
      this.basin3d.setBasinShape(shape);
    }.bind(this);

    this.panel.onInteractiveFrictionToggle = function (enabled, cb) {
      this.basin3d.enableInteractiveFriction(enabled, cb);
    }.bind(this);

    this.panel.setBasin3dRef(this.basin3d);

    this.panel.init();

    var self = this;
    window.addEventListener('resize', function () {
      var c = document.getElementById('viewport3d');
      if (c) {
        self.basin3d.onWindowResize(c);
      }
    });
  };

  FishWashApp.prototype.destroy = function () {
    if (this.basin3d) {
      this.basin3d.destroy();
    }
    if (this.panel) {
      this.panel.destroy();
    }
  };

  return FishWashApp;
})();

document.addEventListener('DOMContentLoaded', function () {
  var app = new FishWashApp();
  app.init();
  window.fishWashApp = app;
});
