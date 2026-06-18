var VRFishBasin = (function () {

  var BASE_FREQ_2ND = 216.0;
  var BASE_FREQ_3RD = 370.0;
  var BASE_FREQ_4TH = 590.0;
  var SPRAY_THRESHOLD_POWER_W = 4.5;

  function VRFishBasin() {
    this.state = {
      velocity: 0,
      frequency: 0,
      modeOrder: 2,
      sprayHeightCm: 0,
      amplitudeMm: 0,
      isDragging: false,
      isActive: false,
      sprayThresholdCrossed: false
    };
    this.hapticFeedbackEnabled = false;
    this.hapticSupported = typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function';
    this.hapticLastPattern = null;
    this.hapticStickSlipPhase = 0;
    this.hapticIntensity = 1.0;
    this.onStateChange = null;
  }

  VRFishBasin.prototype.simulateFriction = function (params) {
    params = params || {};
    var v = Math.max(0, params.velocity || 0);
    var N = Math.max(0, params.normalForce || 0);
    var isDragging = params.isDragging === true;
    var isActive = params.isActive !== false;
    var deviceId = params.deviceId;

    var mu = 0.29;
    var waterLubrication = 0.22;
    var effectiveMu = Math.max(0.08, mu * (1.0 - waterLubrication * 0.4));
    var Ft = effectiveMu * N;
    var power = isDragging ? Ft * v * 2.0 : 0.0;

    var modeOrder;
    var frequency;
    if (v < 0.4) {
      modeOrder = 2;
      frequency = BASE_FREQ_2ND + v * 80.0;
    } else if (v < 1.5) {
      modeOrder = 3;
      frequency = BASE_FREQ_3RD + (v - 0.4) * 140.0;
    } else {
      modeOrder = 4;
      frequency = BASE_FREQ_4TH + Math.min(4.0, v - 1.5) * 100.0;
    }

    var sprayHeightCm = power > 0 ? Math.pow(power / SPRAY_THRESHOLD_POWER_W, 0.6) * 10.0 : 0.0;
    var amplitudeMm = power > 0 ? Math.sqrt(power / Math.max(1.0, frequency)) * 1.5 : 0.0;
    var sprayThresholdCrossed = power >= SPRAY_THRESHOLD_POWER_W && sprayHeightCm >= 10.0;

    this.state = {
      deviceId: deviceId,
      velocity: v,
      normalForceN: N,
      effectiveFrictionCoefficient: effectiveMu,
      tangentialForceN: Ft,
      frequency: frequency,
      modeOrder: modeOrder,
      sprayHeightCm: sprayHeightCm,
      amplitudeMm: amplitudeMm,
      excitationPowerW: power,
      isDragging: isDragging,
      isActive: isActive,
      sprayThresholdCrossed: sprayThresholdCrossed,
      timestamp: Date.now()
    };

    if (typeof this.onStateChange === 'function') {
      this.onStateChange(this.state);
    }

    return this.state;
  };

  VRFishBasin.prototype.getHapticPattern = function (state) {
    var s = state || this.state;
    var v = s.velocity || 0;
    var mode = s.modeOrder || 2;
    var spray = s.sprayHeightCm || 0;
    var freq = s.frequency || 0;

    var intensity = Math.max(0.1, Math.min(1.0, v / 2.5));

    var basePulseMs = Math.max(10, Math.min(80, 120 - (freq / 20)));
    var gapMs = Math.max(5, Math.min(60, 100 - (freq / 15)));

    var pulseDuration = Math.round(basePulseMs * intensity);
    var gapDuration = Math.round(gapMs * (1.2 - intensity * 0.5));

    if (spray > 30) {
      pulseDuration = Math.round(pulseDuration * 1.5);
      gapDuration = Math.round(gapDuration * 0.6);
    } else if (spray > 10) {
      pulseDuration = Math.round(pulseDuration * 1.2);
      gapDuration = Math.round(gapDuration * 0.8);
    }

    var pattern;
    if (mode >= 4 && freq > 500) {
      pattern = [pulseDuration, gapDuration, pulseDuration, gapDuration, pulseDuration * 2, gapDuration];
    } else if (mode === 3 && freq > 300) {
      pattern = [pulseDuration, gapDuration, pulseDuration * 2, gapDuration];
    } else {
      pattern = this.hapticStickSlipPhase < 2
        ? [pulseDuration * 2, gapDuration, pulseDuration, gapDuration]
        : [pulseDuration, gapDuration, pulseDuration * 2, gapDuration];
    }
    if ((s.amplitudeMm || 0) > 0.5) {
      pattern.push(Math.round(pulseDuration * 1.5));
    }

    this.hapticStickSlipPhase = (this.hapticStickSlipPhase + 1) % 4;

    var feelDesc;
    if (v < 0.5) feelDesc = '微震';
    else if (v < 1.5) feelDesc = '麻酥感';
    else if (v < 2.5) feelDesc = '强震';
    else feelDesc = '剧烈震动';

    var description = mode + '阶-' + freq.toFixed(0) + 'Hz · ' + feelDesc + (spray > 5 ? ' · 喷水' + spray.toFixed(0) + 'cm' : '');
    if (s.sprayThresholdCrossed) description += ' · 喷水阈值突破！';

    return {
      intensity: intensity,
      vibrationPattern: pattern,
      feelDescription: feelDesc,
      fullDescription: description,
      thresholdCrossed: !!s.sprayThresholdCrossed,
      celebrationPattern: s.sprayThresholdCrossed ? [100, 50, 100, 50, 200] : null
    };
  };

  VRFishBasin.prototype.triggerHapticFeedback = function (state) {
    if (!this.hapticFeedbackEnabled) return null;
    var s = state || this.state;
    if (!s.isDragging || !s.isActive) {
      this.stopHapticFeedback();
      return null;
    }
    var pattern = this.getHapticPattern(s);
    var patternKey = pattern.vibrationPattern.join('-');
    if (patternKey !== this.hapticLastPattern) {
      this.hapticLastPattern = patternKey;
      if (this.hapticSupported) {
        try { navigator.vibrate(pattern.vibrationPattern); } catch (e) {}
      }
      if (pattern.celebrationPattern && this.hapticSupported) {
        try { navigator.vibrate(pattern.celebrationPattern); } catch (e) {}
      }
    }
    return pattern;
  };

  VRFishBasin.prototype.stopHapticFeedback = function () {
    if (this.hapticSupported) {
      try { navigator.vibrate(0); } catch (e) {}
    }
    this.hapticLastPattern = null;
    this.hapticStickSlipPhase = 0;
  };

  VRFishBasin.prototype.setHapticEnabled = function (enabled) {
    this.hapticFeedbackEnabled = enabled === true;
    if (!this.hapticFeedbackEnabled) {
      this.stopHapticFeedback();
    } else if (this.hapticSupported) {
      try { navigator.vibrate([50, 30, 50]); } catch (e) {}
    }
  };

  VRFishBasin.prototype.setIntensity = function (level) {
    this.hapticIntensity = Math.max(0, Math.min(1.0, level || 0));
  };

  VRFishBasin.prototype.renderMetrics = function (state, elements) {
    var s = state || this.state;
    elements = elements || {};
    var el = function (id) { return elements[id] || (typeof document !== 'undefined' ? document.getElementById(id) : null); };

    var vEl = el('metricVelocity');
    if (vEl) vEl.textContent = (s.velocity !== undefined && s.velocity !== null) ? s.velocity.toFixed(3) + ' m/s' : '--';
    var fEl = el('metricFreq');
    if (fEl) fEl.textContent = (s.frequency !== undefined && s.frequency !== null) ? s.frequency.toFixed(0) + ' Hz' : '--';
    var mEl = el('metricMode');
    if (mEl) mEl.textContent = s.modeOrder ? s.modeOrder + ' 阶模态' : '--';
    var sEl = el('metricSpray');
    if (sEl) sEl.textContent = (s.sprayHeightCm !== undefined && s.sprayHeightCm !== null) ? s.sprayHeightCm.toFixed(1) + ' cm' : '--';
    var aEl = el('metricAmp');
    if (aEl) aEl.textContent = (s.amplitudeMm !== undefined && s.amplitudeMm !== null) ? s.amplitudeMm.toFixed(2) + ' mm' : '--';

    var statusEl = el('frictionStatus');
    if (statusEl) {
      if (s.isDragging) {
        statusEl.textContent = '🔥 摩擦中 — 能量注入中...';
      } else if (!s.isActive) {
        statusEl.textContent = '✋ 等待摩擦动作...';
      } else {
        statusEl.textContent = '✋ 待机中...';
      }
    }

    var hapticEl = el('hapticPattern');
    if (hapticEl) {
      if (this.hapticFeedbackEnabled && s.isDragging) {
        var intensity = (s.velocity ? Math.min(1.0, s.velocity / 3.0) : 0) * 100;
        var modeText = s.modeOrder || '--';
        hapticEl.textContent = this.hapticSupported
          ? '📳 设备震动中 · 强度 ' + intensity.toFixed(0) + '% · 模式 ' + modeText + '阶'
          : '📳 模拟触觉反馈 · 强度 ' + intensity.toFixed(0) + '% · 模式 ' + modeText + '阶';
      } else {
        hapticEl.textContent = this.hapticFeedbackEnabled ? '📳 触觉反馈待机' : '';
      }
    }

    var detailEl = el('hapticDetail');
    if (detailEl && this.hapticFeedbackEnabled && s.isDragging) {
      var pat = this.getHapticPattern(s);
      detailEl.textContent = '触觉模式：' + pat.fullDescription;
    } else if (detailEl) {
      detailEl.textContent = '';
    }
  };

  VRFishBasin.calculateSprayFromPower = function (powerW) {
    if (!powerW || powerW <= 0) return 0;
    return Math.pow(powerW / SPRAY_THRESHOLD_POWER_W, 0.6) * 10.0;
  };

  VRFishBasin.estimateModeFromVelocity = function (velocity) {
    if (!velocity || velocity < 0.4) return 2;
    if (velocity < 1.5) return 3;
    return 4;
  };

  return VRFishBasin;
})();

if (typeof module !== 'undefined' && module.exports) {
  module.exports = VRFishBasin;
}
