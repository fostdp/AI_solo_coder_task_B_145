var FishWashModel = (function () {
  function FishWashModel(shape) {
    this.group = new THREE.Group();
    this.modeOrder = 2;
    this.amplitude = 0.005;
    this.originalPositions = null;
    this.basinMesh = null;
    this.animating = false;
    this.animationId = null;
    this.time = 0;
    this.shape = (shape === 'SQUARE' || shape === 'square') ? 'SQUARE' : 'CIRCLE';
    this.halfSide = 0.20;
    this.handleObjects = [];

    this.createBasin();
    this.createHandles();
    this.createFishRelief();
    this.createRimDecoration();
  }

  FishWashModel.prototype.setShape = function (shape) {
    var target = (shape === 'SQUARE' || shape === 'square') ? 'SQUARE' : 'CIRCLE';
    if (target === this.shape) return;
    this.stopVibrationAnimation();
    while (this.group.children.length > 0) {
      this.group.remove(this.group.children[0]);
    }
    this.shape = target;
    this.handleObjects = [];
    this.createBasin();
    this.createHandles();
    this.createFishRelief();
    this.createRimDecoration();
  };

  FishWashModel.prototype.getHandleMeshes = function () {
    return this.handleObjects.slice();
  };

  FishWashModel.prototype.createBasin = function () {
    if (this.shape === 'SQUARE') {
      this.createSquareBasin();
    } else {
      this.createCircleBasin();
    }
  };

  FishWashModel.prototype.createCircleBasin = function () {
    var points = [];
    points.push(new THREE.Vector2(0, 0));
    points.push(new THREE.Vector2(0.15, 0));
    points.push(new THREE.Vector2(0.18, 0.01));
    points.push(new THREE.Vector2(0.2, 0.04));
    points.push(new THREE.Vector2(0.2, 0.1));
    points.push(new THREE.Vector2(0.21, 0.12));
    points.push(new THREE.Vector2(0.22, 0.14));
    points.push(new THREE.Vector2(0.24, 0.15));

    var geometry = new THREE.LatheGeometry(points, 64);
    var material = new THREE.MeshPhongMaterial({
      color: 0xB87333,
      shininess: 80,
      specular: 0x666666,
      side: THREE.DoubleSide
    });

    this.basinMesh = new THREE.Mesh(geometry, material);
    this.basinMesh.castShadow = true;
    this.basinMesh.receiveShadow = true;

    this.originalPositions = new Float32Array(geometry.attributes.position.array);

    this.group.add(this.basinMesh);
  };

  FishWashModel.prototype.createSquareBasin = function () {
    var halfSide = this.halfSide;
    var thickness = 0.003;
    var height = 0.15;
    var cornerR = 0.03;

    var shape = new THREE.Shape();
    var outer = [];
    outer.push(new THREE.Vector2(-halfSide + cornerR, -halfSide));
    outer.push(new THREE.Vector2(halfSide - cornerR, -halfSide));
    outer.push(new THREE.Vector2(halfSide, -halfSide + cornerR));
    outer.push(new THREE.Vector2(halfSide, halfSide - cornerR));
    outer.push(new THREE.Vector2(halfSide - cornerR, halfSide));
    outer.push(new THREE.Vector2(-halfSide + cornerR, halfSide));
    outer.push(new THREE.Vector2(-halfSide, halfSide - cornerR));
    outer.push(new THREE.Vector2(-halfSide, -halfSide + cornerR));
    shape.moveTo(outer[0].x, outer[0].y);
    for (var ci = 1; ci < outer.length; ci++) {
      shape.lineTo(outer[ci].x, outer[ci].y);
    }
    shape.lineTo(outer[0].x, outer[0].y);

    var hole = new THREE.Path();
    var innerHalf = halfSide - thickness;
    hole.moveTo(-innerHalf + cornerR, -innerHalf);
    hole.lineTo(innerHalf - cornerR, -innerHalf);
    hole.lineTo(innerHalf, -innerHalf + cornerR);
    hole.lineTo(innerHalf, innerHalf - cornerR);
    hole.lineTo(innerHalf - cornerR, innerHalf);
    hole.lineTo(-innerHalf + cornerR, innerHalf);
    hole.lineTo(-innerHalf, innerHalf - cornerR);
    hole.lineTo(-innerHalf, -innerHalf + cornerR);
    hole.lineTo(-innerHalf + cornerR, -innerHalf);
    shape.holes.push(hole);

    var extrudeSettings = {
      depth: height,
      bevelEnabled: true,
      bevelThickness: 0.003,
      bevelSize: 0.003,
      bevelSegments: 2,
      curveSegments: 8,
      steps: 1
    };

    var geometry = new THREE.ExtrudeGeometry(shape, extrudeSettings);
    geometry.rotateX(-Math.PI / 2);
    geometry.translate(0, 0, 0);

    var material = new THREE.MeshPhongMaterial({
      color: 0xB87333,
      shininess: 75,
      specular: 0x555555,
      side: THREE.DoubleSide
    });

    this.basinMesh = new THREE.Mesh(geometry, material);
    this.basinMesh.castShadow = true;
    this.basinMesh.receiveShadow = true;

    this.originalPositions = new Float32Array(geometry.attributes.position.array);

    this.group.add(this.basinMesh);
  };

  FishWashModel.prototype.createHandles = function () {
    var handleMaterial = new THREE.MeshPhongMaterial({
      color: 0xB87333,
      shininess: 80,
      specular: 0x666666
    });

    var handleGeometry = new THREE.TorusGeometry(0.03, 0.008, 8, 16);
    var handlePositions;

    if (this.shape === 'SQUARE') {
      var hs = this.halfSide;
      handlePositions = [
        { x: hs + 0.025, y: 0.135, z: 0, ry: Math.PI / 2 },
        { x: -(hs + 0.025), y: 0.135, z: 0, ry: Math.PI / 2 },
        { x: 0, y: 0.135, z: hs + 0.025, ry: 0 },
        { x: 0, y: 0.135, z: -(hs + 0.025), ry: 0 }
      ];
    } else {
      handlePositions = [
        { x: 0.25, y: 0.135, z: 0, ry: Math.PI / 2 },
        { x: -0.25, y: 0.135, z: 0, ry: Math.PI / 2 }
      ];
    }

    for (var hi = 0; hi < handlePositions.length; hi++) {
      var p = handlePositions[hi];
      var handle = new THREE.Mesh(handleGeometry, handleMaterial);
      handle.position.set(p.x, p.y, p.z);
      handle.rotation.y = p.ry;
      handle.castShadow = true;
      handle.userData.isHandle = true;
      handle.userData.handleIndex = hi;
      this.group.add(handle);
      this.handleObjects.push(handle);
    }
  };

  FishWashModel.prototype.createFishRelief = function () {
    var fishMaterial = new THREE.MeshPhongMaterial({
      color: 0x8B6914,
      shininess: 60,
      specular: 0x444444,
      side: THREE.DoubleSide
    });

    var fishGeometry = new THREE.PlaneGeometry(0.06, 0.025);

    if (this.shape === 'SQUARE') {
      var hs = this.halfSide - 0.003;
      var fishY = 0.07;
      for (var si = 0; si < 4; si++) {
        var fishPositions;
        if (si === 0) {
          for (var xi = -2; xi <= 2; xi++) {
            var f = new THREE.Mesh(fishGeometry, fishMaterial);
            f.position.set(xi * 0.05, fishY, hs);
            f.rotation.x = -0.1;
            this.group.add(f);
          }
        } else if (si === 1) {
          for (var xi2 = -2; xi2 <= 2; xi2++) {
            var f2 = new THREE.Mesh(fishGeometry, fishMaterial);
            f2.position.set(xi2 * 0.05, fishY, -hs);
            f2.rotation.x = -0.1;
            f2.rotation.y = Math.PI;
            this.group.add(f2);
          }
        } else if (si === 2) {
          for (var zi = -2; zi <= 2; zi++) {
            var f3 = new THREE.Mesh(fishGeometry, fishMaterial);
            f3.position.set(hs, fishY, zi * 0.05);
            f3.rotation.y = Math.PI / 2;
            f3.rotation.x = -0.1;
            this.group.add(f3);
          }
        } else {
          for (var zi2 = -2; zi2 <= 2; zi2++) {
            var f4 = new THREE.Mesh(fishGeometry, fishMaterial);
            f4.position.set(-hs, fishY, zi2 * 0.05);
            f4.rotation.y = -Math.PI / 2;
            f4.rotation.x = -0.1;
            this.group.add(f4);
          }
        }
      }
    } else {
      var angles = [0, Math.PI / 2, Math.PI, 3 * Math.PI / 2];
      var basinWallRadius = 0.195;
      var fishY = 0.07;

      for (var i = 0; i < 4; i++) {
        var fish = new THREE.Mesh(fishGeometry, fishMaterial);
        var angle = angles[i];
        fish.position.set(
          basinWallRadius * Math.cos(angle),
          fishY,
          basinWallRadius * Math.sin(angle)
        );
        fish.rotation.y = -angle + Math.PI / 2;
        fish.rotation.x = -0.1;
        this.group.add(fish);
      }
    }
  };

  FishWashModel.prototype.createRimDecoration = function () {
    if (this.shape === 'SQUARE') {
      var rimMaterial = new THREE.MeshPhongMaterial({
        color: 0xA0682C,
        shininess: 90,
        specular: 0x888888
      });
      var hs = this.halfSide + 0.002;
      var th = 0.008;
      var barGeo1 = new THREE.BoxGeometry(hs * 2 + th * 2, 0.008, th);
      var barGeo2 = new THREE.BoxGeometry(th, 0.008, hs * 2 + th * 2);
      var y = 0.15;
      var r1 = new THREE.Mesh(barGeo1, rimMaterial); r1.position.set(0, y, hs);
      var r2 = new THREE.Mesh(barGeo1, rimMaterial); r2.position.set(0, y, -hs);
      var r3 = new THREE.Mesh(barGeo2, rimMaterial); r3.position.set(hs, y, 0);
      var r4 = new THREE.Mesh(barGeo2, rimMaterial); r4.position.set(-hs, y, 0);
      this.group.add(r1); this.group.add(r2);
      this.group.add(r3); this.group.add(r4);

      var cornerGeo = new THREE.BoxGeometry(th * 1.5, 0.008, th * 1.5);
      [[hs, hs], [hs, -hs], [-hs, hs], [-hs, -hs]].forEach(function (c) {
        var cc = new THREE.Mesh(cornerGeo, rimMaterial);
        cc.position.set(c[0], y, c[1]);
        this.group.add(cc);
      }.bind(this));
    } else {
      var rimGeometry = new THREE.TorusGeometry(0.235, 0.006, 8, 64);
      var rimMaterial2 = new THREE.MeshPhongMaterial({
        color: 0xA0682C,
        shininess: 90,
        specular: 0x888888
      });

      var rim = new THREE.Mesh(rimGeometry, rimMaterial2);
      rim.position.y = 0.15;
      rim.rotation.x = Math.PI / 2;

      this.group.add(rim);
    }
  };

  FishWashModel.prototype.applyVibrationMode = function (modeOrder, amplitude) {
    this.modeOrder = modeOrder;
    this.amplitude = amplitude;

    if (!this.basinMesh || !this.originalPositions) return;

    var positions = this.basinMesh.geometry.attributes.position.array;
    var origPos = this.originalPositions;
    var m = modeOrder;
    var n = modeOrder;

    if (this.shape === 'SQUARE') {
      var hs = this.halfSide;
      for (var i = 0; i < positions.length; i += 3) {
        var ox = origPos[i];
        var oy = origPos[i + 1];
        var oz = origPos[i + 2];
        var heightFactor = Math.max(0, Math.min(1, oy / 0.15));

        if (oy < 0.002) continue;

        var xiNorm = ox / hs;
        var ziNorm = oz / hs;
        if (Math.abs(xiNorm) > 1.05 || Math.abs(ziNorm) > 1.05) continue;

        var modalValue = Math.cos(m * Math.PI * (xiNorm + 1) / 2) *
                         Math.cos(n * Math.PI * (ziNorm + 1) / 2);

        var disp = amplitude * modalValue * 2.5;
        var normalOutwardFactor = (Math.abs(ox) > Math.abs(oz))
          ? Math.sign(ox || 1)
          : Math.sign(oz || 1);

        var absMax = Math.max(Math.abs(ox), Math.abs(oz));
        if (absMax < 1e-4) absMax = 1;

        if (Math.abs(ox) >= Math.abs(oz)) {
          positions[i] = ox + disp * 0.5 * heightFactor;
        } else {
          positions[i + 2] = oz + disp * 0.5 * heightFactor;
        }
        positions[i + 1] = oy + disp * 0.35 * heightFactor;
      }
    } else {
      for (var j = 0; j < positions.length; j += 3) {
        var ox2 = origPos[j];
        var oy2 = origPos[j + 1];
        var oz2 = origPos[j + 2];

        var r = Math.sqrt(ox2 * ox2 + oz2 * oz2);
        if (r < 0.001) continue;

        var theta = Math.atan2(oz2, ox2);
        var deformation = amplitude * Math.cos(modeOrder * theta);
        var heightFactor2 = oy2 / 0.15;

        var radialDisp = deformation * heightFactor2;
        positions[j] = ox2 + (ox2 / r) * radialDisp;
        positions[j + 1] = oy2 + deformation * 0.3 * heightFactor2;
        positions[j + 2] = oz2 + (oz2 / r) * radialDisp;
      }
    }

    this.basinMesh.geometry.attributes.position.needsUpdate = true;
    this.basinMesh.geometry.computeVertexNormals();
  };

  FishWashModel.prototype.startVibrationAnimation = function () {
    if (this.animating) return;
    this.animating = true;
    this.time = 0;
    var self = this;

    function loop() {
      if (!self.animating) return;
      self.time += 0.016;
      var dynamicAmp = self.amplitude * Math.cos(2 * Math.PI * 2.0 * self.time);
      self.applyVibrationMode(self.modeOrder, dynamicAmp);
      self.animationId = requestAnimationFrame(loop);
    }

    loop();
  };

  FishWashModel.prototype.stopVibrationAnimation = function () {
    this.animating = false;
    if (this.animationId !== null) {
      cancelAnimationFrame(this.animationId);
      this.animationId = null;
    }
    this.resetPositions();
  };

  FishWashModel.prototype.resetPositions = function () {
    if (!this.basinMesh || !this.originalPositions) return;
    var positions = this.basinMesh.geometry.attributes.position.array;
    var origPos = this.originalPositions;
    for (var i = 0; i < positions.length; i++) {
      positions[i] = origPos[i];
    }
    this.basinMesh.geometry.attributes.position.needsUpdate = true;
    this.basinMesh.geometry.computeVertexNormals();
  };

  FishWashModel.prototype.setModeOrder = function (n) {
    this.modeOrder = n;
  };

  FishWashModel.prototype.setAmplitude = function (a) {
    this.amplitude = a;
  };

  FishWashModel.prototype.getMesh = function () {
    return this.group;
  };

  return FishWashModel;
})();
