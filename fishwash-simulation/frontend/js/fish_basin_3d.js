var FishBasin3D = (function () {
  function FishBasin3D() {
    this.scene = null;
    this.camera = null;
    this.renderer = null;
    this.controls = null;
    this.fishWashModel = null;
    this.waterSurface = null;
    this.sprayParticles = null;
    this.clock = new THREE.Clock();
    this.currentModeOrder = 2;
    this.currentFrequency = 3.0;
    this.currentAmplitude = 0.02;
    this.currentSprayHeight = 0.15;
    this.animating = false;
    this.animationId = null;

    this.raycaster = new THREE.Raycaster();
    this.mouse = new THREE.Vector2();
    this.draggingHandle = null;
    this.lastHandlePos = new THREE.Vector3();
    this.lastDragTime = 0;
    this.frictionVelocity = 0;
    this.interactiveMode = false;
    this.autoFrequency = true;
    this.frictionCallback = null;
    this.currentBasinShape = 'CIRCLE';
    this._tempVec = new THREE.Vector3();
    this._dragPlane = new THREE.Plane();
  }

  FishBasin3D.prototype.enableInteractiveFriction = function (enable, callback) {
    this.interactiveMode = !!enable;
    this.frictionCallback = callback || null;
    if (this.controls) {
      this.controls.enabled = !this.interactiveMode || this.draggingHandle === null;
    }
  };

  FishBasin3D.prototype.setBasinShape = function (shape) {
    var target = (shape === 'SQUARE' || shape === 'square') ? 'SQUARE' : 'CIRCLE';
    if (this.currentBasinShape === target && this.fishWashModel) return;
    this.currentBasinShape = target;
    if (this.fishWashModel && this.scene) {
      this.scene.remove(this.fishWashModel.getMesh());
      this.fishWashModel = new FishWashModel(target);
      this.scene.add(this.fishWashModel.getMesh());
    }
  };

  FishBasin3D.prototype.getFrictionMetrics = function () {
    return {
      velocity: this.frictionVelocity,
      dragging: this.draggingHandle !== null,
      handleIndex: this.draggingHandle !== null ? this.draggingHandle.userData.handleIndex : -1
    };
  };

  FishBasin3D.prototype._initFrictionListeners = function () {
    if (!this.renderer) return;
    var canvas = this.renderer.domElement;
    var self = this;

    function updateMouse(ev) {
      var rect = canvas.getBoundingClientRect();
      var px = (ev.touches && ev.touches[0] ? ev.touches[0].clientX : ev.clientX);
      var py = (ev.touches && ev.touches[0] ? ev.touches[0].clientY : ev.clientY);
      self.mouse.x = ((px - rect.left) / rect.width) * 2 - 1;
      self.mouse.y = -((py - rect.top) / rect.height) * 2 + 1;
    }

    canvas.addEventListener('mousedown', function (e) { self._onDragStart(e, updateMouse, e); });
    canvas.addEventListener('mousemove', function (e) { self._onDragMove(e, updateMouse, e); });
    window.addEventListener('mouseup', function (e) { self._onDragEnd(e); });

    canvas.addEventListener('touchstart', function (e) {
      if (e.touches && e.touches[0]) {
        self._onDragStart(e, updateMouse, e.touches[0]);
        e.preventDefault();
      }
    }, { passive: false });
    canvas.addEventListener('touchmove', function (e) {
      if (e.touches && e.touches[0]) {
        self._onDragMove(e, updateMouse, e.touches[0]);
        e.preventDefault();
      }
    }, { passive: false });
    window.addEventListener('touchend', function (e) { self._onDragEnd(e); });
  };

  FishBasin3D.prototype._intersectHandles = function () {
    this.raycaster.setFromCamera(this.mouse, this.camera);
    var handles = this.fishWashModel ? this.fishWashModel.getHandleMeshes() : [];
    var intersects = this.raycaster.intersectObjects(handles, false);
    return intersects.length > 0 ? intersects[0].object : null;
  };

  FishBasin3D.prototype._onDragStart = function (ev, updateFn, raw) {
    if (!this.interactiveMode) return;
    updateFn(raw);
    var hit = this._intersectHandles();
    if (!hit) return;
    this.draggingHandle = hit;
    this.lastDragTime = performance.now();
    hit.getWorldPosition(this.lastHandlePos);

    var camPos = new THREE.Vector3();
    this.camera.getWorldPosition(camPos);
    this._dragPlane.setFromNormalAndCoplanarPoint(
      camPos.sub(this.lastHandlePos).normalize().negate(),
      this.lastHandlePos.clone()
    );

    if (this.controls) this.controls.enabled = false;
    if (this.fishWashModel) this.fishWashModel.startVibrationAnimation();
    if (this.frictionCallback) {
      this.frictionCallback({
        type: 'start',
        handleIndex: hit.userData.handleIndex,
        velocity: 0
      });
    }
  };

  FishBasin3D.prototype._onDragMove = function (ev, updateFn, raw) {
    if (this.draggingHandle === null) return;
    updateFn(raw);

    this.raycaster.setFromCamera(this.mouse, this.camera);
    var intersectPt = new THREE.Vector3();
    if (!this.raycaster.ray.intersectPlane(this._dragPlane, intersectPt)) return;

    var now = performance.now();
    var dt = Math.max(16, now - this.lastDragTime) / 1000;
    var dist = this.lastHandlePos.distanceTo(intersectPt);
    this.frictionVelocity = Math.min(4.0, dist / dt);
    this.lastDragTime = now;
    this.lastHandlePos.copy(intersectPt);

    if (this.autoFrequency) {
      var freqHz = 150 + this.frictionVelocity * 220;
      this.currentFrequency = Math.min(20.0, freqHz / 50.0);
      this.setSprayHeight(Math.min(0.3, 0.02 + this.frictionVelocity * 0.18));
      this.currentAmplitude = Math.min(0.08, 0.008 + this.frictionVelocity * 0.04);
      var modeGuess = this.frictionVelocity < 0.8 ? 2 : (this.frictionVelocity < 1.6 ? 3 : 4);
      if (this.currentModeOrder !== modeGuess && this.fishWashModel) {
        this.currentModeOrder = modeGuess;
        this.fishWashModel.setModeOrder(modeGuess);
      }
      this.sprayParticles.setIntensity(Math.min(1.2, this.frictionVelocity * 0.8));
    }

    if (this.frictionCallback) {
      this.frictionCallback({
        type: 'move',
        handleIndex: this.draggingHandle.userData.handleIndex,
        velocity: this.frictionVelocity,
        frequencyHz: this.currentFrequency * 50,
        modeOrder: this.currentModeOrder,
        sprayHeightCm: this.currentSprayHeight * 100,
        amplitudeMicrons: this.currentAmplitude * 1e6
      });
    }
  };

  FishBasin3D.prototype._onDragEnd = function (ev) {
    if (this.draggingHandle === null) return;
    var idx = this.draggingHandle.userData.handleIndex;
    this.draggingHandle = null;
    if (this.controls) this.controls.enabled = !this.interactiveMode ? true : !this.interactiveMode === false;
    if (this.controls) this.controls.enabled = true;

    if (this.frictionCallback) {
      this.frictionCallback({
        type: 'end',
        handleIndex: idx,
        velocity: 0
      });
    }
  };

  FishBasin3D.prototype.initScene = function (container) {
    if (!container) return;

    this.scene = new THREE.Scene();
    this.scene.fog = new THREE.Fog(0x111122, 0.5, 2.0);

    var w = container.clientWidth;
    var h = container.clientHeight;

    this.camera = new THREE.PerspectiveCamera(50, w / h, 0.01, 10);
    this.camera.position.set(0.4, 0.3, 0.4);
    this.camera.lookAt(0, 0.05, 0);

    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    this.renderer.setSize(w, h);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.renderer.setClearColor(0x111122);

    container.appendChild(this.renderer.domElement);

    this.controls = new THREE.OrbitControls(this.camera, this.renderer.domElement);
    this.controls.target.set(0, 0.05, 0);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.05;
    this.controls.update();

    var ambientLight = new THREE.AmbientLight(0x404040, 1.0);
    this.scene.add(ambientLight);

    var directionalLight = new THREE.DirectionalLight(0xFFFFFF, 1.0);
    directionalLight.position.set(0.3, 0.5, 0.3);
    directionalLight.castShadow = true;
    directionalLight.shadow.mapSize.width = 1024;
    directionalLight.shadow.mapSize.height = 1024;
    this.scene.add(directionalLight);

    var pointLight = new THREE.PointLight(0xB87333, 0.5, 1.0);
    pointLight.position.set(0, 0.2, 0);
    this.scene.add(pointLight);

    var groundGeom = new THREE.PlaneGeometry(2, 2);
    var groundMat = new THREE.MeshPhongMaterial({ color: 0x222222 });
    var ground = new THREE.Mesh(groundGeom, groundMat);
    ground.rotation.x = -Math.PI / 2;
    ground.position.y = -0.01;
    ground.receiveShadow = true;
    this.scene.add(ground);

    this._initFrictionListeners();
  };

  FishBasin3D.prototype.initComponents = function () {
    this.fishWashModel = new FishWashModel(this.currentBasinShape);
    this.scene.add(this.fishWashModel.getMesh());

    this.waterSurface = new WaterSurface();
    this.scene.add(this.waterSurface.getMesh());

    this.sprayParticles = new SprayParticles();
    this.scene.add(this.sprayParticles.getMesh());
  };

  FishBasin3D.prototype.startSimulation = function (modeOrder) {
    this.currentModeOrder = modeOrder;
    this.fishWashModel.setModeOrder(modeOrder);
    this.fishWashModel.startVibrationAnimation();
    this.sprayParticles.setIntensity(0.8);
  };

  FishBasin3D.prototype.stopSimulation = function () {
    this.fishWashModel.stopVibrationAnimation();
    this.sprayParticles.setIntensity(0.0);
  };

  FishBasin3D.prototype.setFrequency = function (freq) {
    this.currentFrequency = freq / 50.0;
  };

  FishBasin3D.prototype.setSprayHeight = function (height) {
    this.currentSprayHeight = height;
  };

  FishBasin3D.prototype.setAmplitude = function (amp) {
    this.currentAmplitude = amp;
  };

  FishBasin3D.prototype.animate = function () {
    var self = this;
    this.animating = true;

    function loop() {
      if (!self.animating) return;
      self.animationId = requestAnimationFrame(loop);

      var dt = self.clock.getDelta();
      var elapsed = self.clock.getElapsedTime();

      self.waterSurface.updateWave(
        self.currentModeOrder,
        self.currentFrequency,
        self.currentAmplitude,
        elapsed
      );

      self.sprayParticles.emitSpray(
        self.currentModeOrder,
        self.currentFrequency,
        self.currentSprayHeight,
        0.2
      );
      self.sprayParticles.update(dt);

      self.controls.update();
      self.renderer.render(self.scene, self.camera);
    }

    loop();
  };

  FishBasin3D.prototype.onWindowResize = function (container) {
    if (!container) return;
    var w = container.clientWidth;
    var h = container.clientHeight;
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(w, h);
  };

  FishBasin3D.prototype.init = function (container) {
    this.initScene(container);
    this.initComponents();
    this.animate();
  };

  FishBasin3D.prototype.destroy = function () {
    this.animating = false;
    if (this.animationId) {
      cancelAnimationFrame(this.animationId);
    }
    this.fishWashModel.stopVibrationAnimation();
    this.fishWashModel = null;
    if (this.waterSurface) {
      this.waterSurface.dispose();
      this.waterSurface = null;
    }
    if (this.sprayParticles) {
      this.sprayParticles.dispose();
      this.sprayParticles = null;
    }
    this.renderer.dispose();
  };

  return FishBasin3D;
})();
