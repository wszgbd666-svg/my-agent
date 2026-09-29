/* ============================================================
 * 3D 场景管理：室内房间 / 室外立面、墙体开洞、灯光阴影、
 * 轨道相机（OrbitControls，支持触摸旋转缩放）
 * ============================================================ */

const SceneManager = (function () {
  let container = null;
  let renderer, scene, camera, controls;
  let sun, hemi;
  let wallGroup = null, productGroup = null;
  let state = null;
  let dirty = true;
  let active = true;
  let firstFit = true;
  let overlay = null, overlayCtx = null; // 尺寸标注层

  const WALL_T = 0.24; // 墙体厚度 m

  function init(el) {
    container = el;
    try {
      renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false, preserveDrawingBuffer: true });
    } catch (e) {
      // 设备/系统 WebView 不支持 WebGL：显示友好提示
      const msg = document.createElement('div');
      msg.style.cssText = 'position:absolute;left:0;top:0;right:0;bottom:0;display:flex;' +
        'align-items:center;justify-content:center;color:#fff;font-size:14px;' +
        'text-align:center;padding:30px;z-index:10;line-height:1.8';
      msg.textContent = '⚠️ 此设备不支持 3D 渲染（WebGL 不可用）。\n' +
        '请尝试：系统设置中更新「Android System WebView」，或更换较新的手机。\n\n2D 图纸与报价功能仍可使用。';
      container.appendChild(msg);
      return;
    }
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.outputEncoding = THREE.sRGBEncoding;
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1.05;
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    container.appendChild(renderer.domElement);

    // 尺寸标注层（透明画布盖在 3D 画布上）
    overlay = document.createElement('canvas');
    overlay.style.cssText = 'position:absolute;left:0;top:0;width:100%;height:100%;pointer-events:none;';
    container.appendChild(overlay);
    overlayCtx = overlay.getContext('2d');

    scene = new THREE.Scene();

    camera = new THREE.PerspectiveCamera(42, 1, 0.05, 60);

    controls = new THREE.OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.08;
    controls.enablePan = false;
    controls.minDistance = 0.6;
    controls.maxDistance = 7;
    controls.minPolarAngle = 0.25;
    controls.maxPolarAngle = 1.45;
    controls.target.set(0, 1.1, 0);

    // 灯光
    hemi = new THREE.HemisphereLight(0xffffff, 0x8a7f6f, 0.75);
    scene.add(hemi);
    sun = new THREE.DirectionalLight(0xfff2dd, 0.95);
    sun.position.set(-2.2, 4.2, 5.5);
    sun.castShadow = true;
    sun.shadow.mapSize.set(1024, 1024);
    sun.shadow.camera.left = -4; sun.shadow.camera.right = 4;
    sun.shadow.camera.top = 5; sun.shadow.camera.bottom = -1;
    sun.shadow.camera.near = 0.5; sun.shadow.camera.far = 20;
    sun.shadow.bias = -0.0004;
    sun.shadow.radius = 4;
    scene.add(sun);

    resize();
    window.addEventListener('resize', resize);

    function loop() {
      requestAnimationFrame(loop);
      if (!active || document.hidden) return;
      if (dirty) { rebuild(); dirty = false; }
      controls.update();
      renderer.render(scene, camera);
      drawDims3D(overlayCtx, overlay.width, overlay.height, renderer.domElement.clientWidth);
    }
    loop();
  }

  function resize() {
    if (!container) return;
    const w = container.clientWidth, h = container.clientHeight;
    if (!w || !h) return;
    renderer.setSize(w, h);
    if (overlay) {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      overlay.width = w * dpr;
      overlay.height = h * dpr;
    }
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  }

  function setState(s) {
    state = s;
    if (renderer && scene) {
      // 同步重建并立即渲染：参数拖动时即时反馈
      rebuild();
      renderer.render(scene, camera);
      drawDims3D(overlayCtx, overlay.width, overlay.height, renderer.domElement.clientWidth);
    } else {
      dirty = true; // 初始化前只打标记
    }
  }
  function setActive(a) { active = a; if (a) { dirty = true; resize(); } }

  /* 清空场景中的墙体与门窗（材质有缓存，几何体需释放） */
  function clearSceneObjects() {
    [wallGroup, productGroup].forEach(g => {
      if (!g) return;
      scene.remove(g);
      g.traverse(o => {
        if (o.geometry) o.geometry.dispose();
      });
    });
    wallGroup = null; productGroup = null;
  }

  /* 墙面开洞：用四块墙板拼出洞口 */
  function buildWallWithHole(cx, wallW, wallH, holeX, holeY, holeW, holeH, mat) {
    const g = new THREE.Group();
    g.position.set(cx, 0, 0);
    const hw = wallW / 2;
    const left = { x0: -hw, x1: holeX - holeW / 2 };
    const right = { x0: holeX + holeW / 2, x1: hw };
    const bottom = { y0: 0, y1: holeY - holeH / 2 };
    const top = { y0: holeY + holeH / 2, y1: wallH };
    function piece(x0, x1, y0, y1) {
      const m = new THREE.Mesh(
        new THREE.BoxGeometry(x1 - x0, y1 - y0, WALL_T), mat);
      m.position.set((x0 + x1) / 2, (y0 + y1) / 2, 0);
      m.castShadow = true; m.receiveShadow = true;
      g.add(m);
    }
    if (left.x1 - left.x0 > 0.01) piece(left.x0, left.x1, 0, wallH);
    if (right.x1 - right.x0 > 0.01) piece(right.x0, right.x1, 0, wallH);
    if (bottom.y1 > 0.01) piece(holeX - holeW / 2, holeX + holeW / 2, 0, bottom.y1);
    if (top.y1 - top.y0 > 0.01) piece(holeX - holeW / 2, holeX + holeW / 2, top.y0, wallH);
    return g;
  }

  function wallMat(hex) {
    return new THREE.MeshStandardMaterial({ color: hex, roughness: 0.92, metalness: 0, side: THREE.FrontSide });
  }

  /* ---- 室内场景：房间 + 背墙开洞 + 窗台板 ---- */
  function buildIndoor(st) {
    const style = Models.getStyleById(st.styleId);
    const W = st.w * M, H = st.h * M, sill = style.sill * M;
    const roomW = 4.4, roomH = 2.8, roomD = 3.4;
    wallGroup = new THREE.Group();

    // 背墙（开洞）
    const holeW = W + 0.03, holeH = H + 0.03;
    wallGroup.add(buildWallWithHole(0, roomW, roomH, 0, sill + H / 2, holeW, holeH, wallMat('#e8e2d5')));

    // 地板、天花板、两侧墙
    wallGroup.add(pieceBox(0, -0.05, roomD / 2, roomW, 0.1, roomD, '#c9a06a'));          // 木地板
    wallGroup.add(pieceBox(0, roomH + 0.05, roomD / 2, roomW, 0.1, roomD, '#f4f1ea'));   // 天花板
    wallGroup.add(pieceBox(-roomW / 2, roomH / 2, roomD / 2, 0.12, roomH, roomD, '#efe9de')); // 左墙
    wallGroup.add(pieceBox(roomW / 2, roomH / 2, roomD / 2, 0.12, roomH, roomD, '#efe9de'));  // 右墙

    // 窗台板（窗款式才有）
    if (style.cat === '窗') {
      wallGroup.add(pieceBox(0, sill - 0.02, 0.14, W + 0.25, 0.04, 0.28, '#f7f4ec'));
    }

    // 点缀：绿植（简单几何）
    addPlant(wallGroup, -1.9, 1.0, roomD - 0.7);
    addPlant(wallGroup, 1.7, 1.15, roomD - 0.5);

    scene.background = new THREE.Color('#10161e');
    scene.fog = null;
    hemi.intensity = 0.7;
    sun.intensity = 0.9;
    sun.color.set('#ffedd0');
  }

  /* ---- 室外场景：立面墙 + 地面 + 天空 + 灌木 ---- */
  function buildOutdoor(st) {
    const style = Models.getStyleById(st.styleId);
    const W = st.w * M, H = st.h * M, sill = style.sill * M;
    const wallW = 5.6, wallH = 3.6;
    wallGroup = new THREE.Group();

    // 立面墙（开洞）
    const holeW = W + 0.03, holeH = H + 0.03;
    wallGroup.add(buildWallWithHole(0, wallW, wallH, 0, sill + H / 2, holeW, holeH, wallMat('#d8c9a8')));

    // 地面
    const ground = new THREE.Mesh(
      new THREE.BoxGeometry(14, 0.2, 14), wallMat('#9db078'));
    ground.position.set(0, -0.1, 0);
    ground.receiveShadow = true;
    wallGroup.add(ground);

    // 台阶（门款式：门外台阶）
    if (style.cat === '门') {
      wallGroup.add(pieceBox(0, 0.07, 0.7, W + 1.2, 0.14, 1.0, '#c2b49a'));
    }

    // 灌木点缀
    addBush(wallGroup, -2.6, 1.6);
    addBush(wallGroup, 2.6, 1.9);
    addBush(wallGroup, -2.8, 2.6);
    addBush(wallGroup, 2.8, 2.2);

    scene.background = new THREE.Color('#b7d9ec');
    scene.fog = new THREE.Fog('#c7dce8', 14, 42);
    hemi.intensity = 1.05;
    sun.intensity = 1.2;
    sun.color.set('#ffffff');
  }

  function pieceBox(cx, cy, cz, sx, sy, sz, hex) {
    const m = new THREE.Mesh(new THREE.BoxGeometry(sx, sy, sz), wallMat(hex));
    m.position.set(cx, cy, cz);
    m.castShadow = true; m.receiveShadow = true;
    return m;
  }

  /* 室内绿植：花盆 + 树冠 */
  function addPlant(g, x, y, z) {
    g.add(pieceBox(x, y + 0.12, z, 0.22, 0.24, 0.22, '#a5673f'));
    const crown = new THREE.Mesh(new THREE.SphereGeometry(0.2, 12, 10), wallMat('#5f8a4e'));
    crown.position.set(x, y + 0.42, z);
    crown.castShadow = true;
    g.add(crown);
  }

  /* 室外灌木 */
  function addBush(g, x, z) {
    const b = new THREE.Mesh(new THREE.SphereGeometry(0.38, 12, 10), wallMat('#6d9450'));
    b.position.set(x, 0.3, z);
    b.scale.y = 0.75;
    b.castShadow = true; b.receiveShadow = true;
    g.add(b);
  }

  /* ---- 重建场景（参数变化时调用） ---- */
  function rebuild() {
    if (!state) return;
    try {
      _rebuildInner();
    } catch (e) {
      console.error('场景重建失败:', e);
    }
  }

  function _rebuildInner() {
    clearSceneObjects();

    const style = Models.getStyleById(state.styleId);
    if (state.scene === 'outdoor') buildOutdoor(state);
    else buildIndoor(state);

    // 墙体与场景物件
    if (wallGroup) scene.add(wallGroup);

    // 门窗模型（group 中心即洞口中心）
    productGroup = Models.buildProduct(state);
    productGroup.position.set(0, (style.sill + state.h / 2) * M, 0);
    scene.add(productGroup);

    // 相机目标对准门窗中心
    const cy = (style.sill + state.h / 2) * M;
    controls.target.set(0, cy, 0);
    if (firstFit || fitPending) { fitCamera(); firstFit = false; fitPending = false; }
  }

  let fitPending = false;
  function requestFit() { fitPending = true; dirty = true; }

  /* 相机自动取景：按门窗尺寸计算合适距离 */
  function fitCamera() {
    const style = Models.getStyleById(state.styleId);
    const W = state.w * M, H = state.h * M;
    const cy = (style.sill + state.h / 2) * M;
    const dist = Math.max(W, H) * 1.5 + 0.9;
    const a = 0.55; // 相机相对位置角度
    let cx = dist * Math.cos(a), cz = dist * Math.sin(a), camY = cy + dist * 0.42;
    if (state.scene !== 'outdoor') {
      // 室内：把相机收进房间内，避免被侧墙/天花板挡住视线
      cx = Math.min(cx, 1.8);
      cz = Math.min(cz, 3.0);
      camY = Math.min(camY, 2.5);
    }
    camera.position.set(cx, camY, cz);
    controls.target.set(0, cy, 0);
    controls.minDistance = Math.max(0.4, Math.min(W, H) * 0.35);
    camera.lookAt(controls.target); // 立即朝向目标（同步渲染时方向正确）
  }

  /* 高分辨率导出（3D 画面 + 尺寸标注合成） */
  function renderToDataURL(pixelRatio) {
    const old = renderer.getPixelRatio();
    renderer.setPixelRatio(pixelRatio || 2);
    renderer.render(scene, camera);
    const c = document.createElement('canvas');
    c.width = renderer.domElement.width;
    c.height = renderer.domElement.height;
    const g = c.getContext('2d');
    g.drawImage(renderer.domElement, 0, 0);
    drawDims3D(g, c.width, c.height, renderer.domElement.clientWidth);
    const url = c.toDataURL('image/png');
    renderer.setPixelRatio(old);
    return url;
  }

  /* ============================================================
   * 3D 尺寸标注：长/宽/高/型材厚/墙厚 投影到屏幕绘制
   * ============================================================ */
  function drawDims3D(ctx, w, h, cssW) {
    if (!state || !camera || !ctx || !w || !h) return;
    const scale = cssW ? w / cssW : 1;
    ctx.setTransform(scale, 0, 0, scale, 0, 0);
    const W2 = cssW || w, H2 = w ? h / scale : h;

    const style = Models.getStyleById(state.styleId);
    const W = state.w * M, H = state.h * M, sill = style.sill * M;
    const ft = (state.frameThick || 60) * M;
    const wallT = 0.24;
    const bottom = sill, top = sill + H;
    const isDoor = sill === 0;
    const V3 = (x, y, z) => new THREE.Vector3(x, y, z);

    const dims = [];

    // 总宽（窗标下方，门标上方，避开地面遮挡）
    const widthY = isDoor ? top + 0.34 : bottom - 0.3;
    dims.push({
      label: '宽 ' + state.w + ' mm',
      pts: [V3(-W / 2, widthY, 0), V3(W / 2, widthY, 0)],
      ext: [
        [V3(-W / 2, widthY, 0), V3(-W / 2, isDoor ? top : bottom, 0)],
        [V3(W / 2, widthY, 0), V3(W / 2, isDoor ? top : bottom, 0)],
      ],
    });

    // 总高（左侧）
    const hx = -W / 2 - 0.38;
    dims.push({
      label: '高 ' + state.h + ' mm',
      pts: [V3(hx, bottom, 0), V3(hx, top, 0)],
      ext: [
        [V3(hx, bottom, 0), V3(-W / 2, bottom, 0)],
        [V3(hx, top, 0), V3(-W / 2, top, 0)],
      ],
    });

    // 型材厚度（窗：顶框上方；门：底框下方）
    const thickY = isDoor ? bottom - 0.16 : top + 0.16;
    dims.push({
      label: '型材厚 ' + (state.frameThick || 60) + ' mm',
      pts: [V3(-W / 2, thickY, 0), V3(-W / 2 + ft, thickY, 0)],
      ext: [
        [V3(-W / 2, thickY, 0), V3(-W / 2, isDoor ? bottom : top, 0)],
        [V3(-W / 2 + ft, thickY, 0), V3(-W / 2 + ft, isDoor ? bottom : top, 0)],
      ],
    });

    // 墙厚（洞口右下角，前后墙面之间）
    const wy = bottom + 0.24, wx = W / 2 + 0.22;
    dims.push({
      label: '墙厚 240 mm',
      pts: [V3(wx, wy, -wallT / 2), V3(wx, wy, wallT / 2)],
      ext: [],
    });

    function project(v) {
      const p = v.clone().project(camera);
      if (p.z > 1 || p.z < -1) return null;
      return { x: (p.x + 1) / 2 * W2, y: (1 - p.y) / 2 * H2 };
    }

    ctx.save();
    ctx.font = 'bold 13px "Microsoft YaHei", sans-serif';
    ctx.textBaseline = 'middle';
    ctx.textAlign = 'left';

    dims.forEach(d => {
      const s1 = project(d.pts[0]), s2 = project(d.pts[1]);
      if (!s1 || !s2) return;
      const dx = s2.x - s1.x, dy = s2.y - s1.y;
      if (Math.hypot(dx, dy) < 6) return; // 太短（正对视角的深度线）不画

      // 延长线
      ctx.strokeStyle = 'rgba(255,255,255,0.35)';
      ctx.lineWidth = 1;
      d.ext.forEach(e => {
        const e1 = project(e[0]), e2 = project(e[1]);
        if (!e1 || !e2) return;
        ctx.beginPath();
        ctx.moveTo(e1.x, e1.y);
        ctx.lineTo(e2.x, e2.y);
        ctx.stroke();
      });

      // 主尺寸线 + 45° 斜线起止符
      ctx.strokeStyle = '#4ea3ff';
      ctx.lineWidth = 1.8;
      ctx.beginPath();
      ctx.moveTo(s1.x, s1.y);
      ctx.lineTo(s2.x, s2.y);
      ctx.stroke();
      const len = Math.hypot(dx, dy) || 1;
      const ux = dx / len, uy = dy / len;
      [s1, s2].forEach(p => {
        ctx.beginPath();
        ctx.moveTo(p.x + (ux - uy) * 6, p.y + (uy + ux) * 6);
        ctx.lineTo(p.x - (ux - uy) * 6, p.y - (uy + ux) * 6);
        ctx.stroke();
      });

      // 标签（居中，深色圆角底）
      const mx = (s1.x + s2.x) / 2, my = (s1.y + s2.y) / 2;
      const tw = ctx.measureText(d.label).width;
      ctx.fillStyle = 'rgba(15,23,34,0.82)';
      roundRectPath(ctx, mx - tw / 2 - 8, my - 11, tw + 16, 22, 11);
      ctx.fill();
      ctx.fillStyle = '#ffffff';
      ctx.textAlign = 'center';
      ctx.fillText(d.label, mx, my);
      ctx.textAlign = 'left';
    });
    ctx.restore();
  }

  function roundRectPath(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  return { init, setState, setActive, requestFit, renderToDataURL, resize };
})();
