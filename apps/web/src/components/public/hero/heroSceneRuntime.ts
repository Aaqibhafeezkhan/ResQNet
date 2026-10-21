import type {
  HeroLinkSpec,
  HeroNodeId,
  HeroNodeSpec,
  HeroState,
} from './heroScene';

/**
 * The WebGL runtime for the hero scene.
 *
 * Kept in its own module so the React component stays declarative and the
 * heavy Three.js work is confined to one file that the bundler can split into
 * a lazy chunk.
 *
 * The runtime owns no data of its own: the topology, the routing and the
 * chapter model are all supplied by the caller, so the animated scene, the SVG
 * fallback and the accessible transcript cannot drift apart.
 */

type Three = typeof import('three');

export interface HeroSceneCallbacks {
  /** A node was hovered. `null` when the pointer left every node. */
  onHoverNode: (id: HeroNodeId | null) => void;
  /** A node was clicked. */
  onSelectNode: (id: HeroNodeId | null) => void;
  /** A link was hovered. */
  onHoverLink: (id: string | null) => void;
}

export interface HeroSceneOptions {
  nodes: readonly HeroNodeSpec[];
  links: readonly HeroLinkSpec[];
  derive: (progress: number) => HeroState;
  reduced: boolean;
  compact: boolean;
  callbacks: HeroSceneCallbacks;
}

export interface HeroSceneHandle {
  /** Feeds scroll progress into the scene. */
  setProgress: (progress: number) => void;
  /** Called by the DOM overlay when a node is focused or selected. */
  setActiveNode: (id: HeroNodeId | null) => void;
  setActiveLink: (id: string | null) => void;
  dispose: () => void;
}

interface NodeObject {
  spec: HeroNodeSpec;
  group: InstanceType<Three['Group']>;
  halo: InstanceType<Three['Sprite']>;
  core: InstanceType<Three['Sprite']>;
  ring: InstanceType<Three['Mesh']>;
  outerRing: InstanceType<Three['Mesh']>;
  shell: InstanceType<Three['Mesh']>;
  label: InstanceType<Three['Sprite']> | null;
  baseColor: InstanceType<Three['Color']>;
  phase: number;
  drift: InstanceType<Three['Vector3']>;
}

interface LinkObject {
  spec: HeroLinkSpec;
  curve: InstanceType<Three['CatmullRomCurve3']>;
  material: InstanceType<Three['MeshBasicMaterial']>;
  haloMaterial: InstanceType<Three['MeshBasicMaterial']>;
  baseColor: InstanceType<Three['Color']>;
  flowMaterial: InstanceType<Three['MeshBasicMaterial']>;
}

interface PacketObject {
  sprite: InstanceType<Three['Sprite']>;
  material: InstanceType<Three['SpriteMaterial']>;
  trail: InstanceType<Three['Line']>;
  trailMaterial: InstanceType<Three['LineBasicMaterial']>;
  positions: InstanceType<Three['BufferAttribute']>;
  history: InstanceType<Three['Vector3']>[];
  cursor: number;
}

interface AmbientPacket {
  sprite: InstanceType<Three['Sprite']>;
  material: InstanceType<Three['SpriteMaterial']>;
  curve: InstanceType<Three['CatmullRomCurve3']>;
  offset: number;
  speed: number;
  scale: number;
}

export function createHeroScene(
  THREE: Three,
  host: HTMLDivElement,
  options: HeroSceneOptions,
): HeroSceneHandle | null {
  const { reduced, compact, callbacks, derive, nodes, links } = options;

  let renderer: InstanceType<Three['WebGLRenderer']>;
  try {
    renderer = new THREE.WebGLRenderer({
      alpha: true,
      antialias: !compact,
      powerPreference: 'high-performance',
      failIfMajorPerformanceCaveat: false,
    });
  } catch {
    return null;
  }

  let gl: WebGLRenderingContext | WebGL2RenderingContext | null = null;
  try {
    gl = renderer.getContext();
  } catch {
    gl = null;
  }
  if (!gl) {
    renderer.dispose();
    return null;
  }

  const maxPixelRatio = compact ? 1.5 : 1.75;
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, maxPixelRatio));
  renderer.setClearColor(0x000000, 0);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.domElement.style.width = '100%';
  renderer.domElement.style.height = '100%';
  renderer.domElement.style.display = 'block';
  renderer.domElement.dataset.heroCanvas = 'true';
  host.appendChild(renderer.domElement);

  const scene = new THREE.Scene();
  scene.fog = new THREE.FogExp2(0x040607, 0.026);

  const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 90);
  camera.position.set(0, 0, compact ? 17.5 : 14.4);

  const world = new THREE.Group();
  world.scale.setScalar(compact ? 0.66 : 1);
  world.position.y = compact ? 0.9 : 0;
  scene.add(world);

  const seeded = mulberry32(20261021);
  const textures: Array<{ dispose: () => void }> = [];
  const materials: Array<{ dispose: () => void }> = [];
  const geometries: Array<{ dispose: () => void }> = [];

  const glowTexture = makeGlowTexture(THREE);
  const coreTexture = makeCoreTexture(THREE);
  textures.push(glowTexture, coreTexture);

  // ---------------------------------------------------------------- nodes ---

  const nodeObjects = new Map<HeroNodeId, NodeObject>();
  const nodePositions = new Map<HeroNodeId, InstanceType<Three['Vector3']>>();

  for (const spec of nodes) {
    const position = new THREE.Vector3(...spec.position);
    nodePositions.set(spec.id, position);

    const group = new THREE.Group();
    group.position.copy(position);

    const baseColor = new THREE.Color(spec.color);

    const haloMaterial = new THREE.SpriteMaterial({
      map: glowTexture,
      color: baseColor,
      transparent: true,
      opacity: 0.46,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      depthTest: false,
    });
    materials.push(haloMaterial);
    const halo = new THREE.Sprite(haloMaterial);
    halo.scale.setScalar(2.3 * spec.scale);
    halo.renderOrder = 2;
    group.add(halo);

    const coreMaterial = new THREE.SpriteMaterial({
      map: coreTexture,
      color: baseColor,
      transparent: true,
      opacity: 0.98,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      depthTest: false,
    });
    materials.push(coreMaterial);
    const core = new THREE.Sprite(coreMaterial);
    core.scale.setScalar(0.28 * spec.scale);
    core.renderOrder = 4;
    core.userData.nodeId = spec.id;
    group.add(core);

    const shellGeometry = new THREE.IcosahedronGeometry(0.19 * spec.scale, 1);
    geometries.push(shellGeometry);
    const shellMaterial = new THREE.MeshBasicMaterial({
      color: baseColor,
      transparent: true,
      opacity: 0.16,
      wireframe: true,
      depthWrite: false,
    });
    materials.push(shellMaterial);
    const shell = new THREE.Mesh(shellGeometry, shellMaterial);
    shell.renderOrder = 3;
    group.add(shell);

    const ringGeometry = new THREE.RingGeometry(0.26 * spec.scale, 0.275 * spec.scale, 64);
    geometries.push(ringGeometry);
    const ringMaterial = new THREE.MeshBasicMaterial({
      color: baseColor,
      transparent: true,
      opacity: 0.4,
      side: THREE.DoubleSide,
      depthWrite: false,
      depthTest: false,
    });
    materials.push(ringMaterial);
    const ring = new THREE.Mesh(ringGeometry, ringMaterial);
    ring.renderOrder = 4;
    group.add(ring);

    const outerRingGeometry = new THREE.RingGeometry(
      0.4 * spec.scale,
      0.408 * spec.scale,
      48,
    );
    geometries.push(outerRingGeometry);
    const outerRingMaterial = new THREE.MeshBasicMaterial({
      color: baseColor,
      transparent: true,
      opacity: 0.14,
      side: THREE.DoubleSide,
      depthWrite: false,
      depthTest: false,
    });
    materials.push(outerRingMaterial);
    const outerRing = new THREE.Mesh(outerRingGeometry, outerRingMaterial);
    outerRing.renderOrder = 4;
    group.add(outerRing);

    let label: InstanceType<Three['Sprite']> | null = null;
    if (spec.role !== 'relay') {
      const labelTexture = makeLabelTexture(THREE, spec.name.toUpperCase(), spec.role);
      textures.push(labelTexture);
      const labelMaterial = new THREE.SpriteMaterial({
        map: labelTexture,
        transparent: true,
        opacity: 0.85,
        depthWrite: false,
        depthTest: false,
      });
      materials.push(labelMaterial);
      label = new THREE.Sprite(labelMaterial);
      label.scale.set(2.4, 0.3, 1);
      label.position.set(0, -0.56 * spec.scale, 0);
      label.renderOrder = 5;
      group.add(label);
    }

    world.add(group);
    nodeObjects.set(spec.id, {
      spec,
      group,
      halo,
      core,
      ring,
      outerRing,
      shell,
      label,
      baseColor,
      phase: seeded() * Math.PI * 2,
      drift: new THREE.Vector3(
        (seeded() - 0.5) * 0.9,
        (seeded() - 0.5) * 0.8,
        (seeded() - 0.5) * 0.6,
      ),
    });
  }

  // ---------------------------------------------------------------- links ---

  const linkObjects = new Map<string, LinkObject>();

  for (const spec of links) {
    const from = nodePositions.get(spec.from);
    const to = nodePositions.get(spec.to);
    if (!from || !to) continue;

    const midpoint = from.clone().add(to).multiplyScalar(0.5);
    const curve = new THREE.CatmullRomCurve3([
      from.clone(),
      midpoint.clone().add(new THREE.Vector3(0, 0.34, 0)),
      to.clone(),
    ]);

    const tubeGeometry = new THREE.TubeGeometry(curve, 28, 0.012, 6, false);
    geometries.push(tubeGeometry);
    const material = new THREE.MeshBasicMaterial({
      color: 0x5ce1df,
      transparent: true,
      opacity: 0.3,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    });
    materials.push(material);
    world.add(new THREE.Mesh(tubeGeometry, material));

    const haloGeometry = new THREE.TubeGeometry(curve, 28, 0.06, 6, false);
    geometries.push(haloGeometry);
    const haloMaterial = new THREE.MeshBasicMaterial({
      color: 0x5ce1df,
      transparent: true,
      opacity: 0.05,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    });
    materials.push(haloMaterial);
    world.add(new THREE.Mesh(haloGeometry, haloMaterial));

    // A brighter filament carries a travelling highlight. This is what makes
    // "data flowing through the link" readable at a glance.
    const flowGeometry = new THREE.TubeGeometry(curve, 28, 0.03, 6, false);
    geometries.push(flowGeometry);
    const flowMaterial = new THREE.MeshBasicMaterial({
      color: 0x8df6ef,
      transparent: true,
      opacity: 0,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    });
    materials.push(flowMaterial);
    const flow = new THREE.Mesh(flowGeometry, flowMaterial);
    flow.renderOrder = 2;
    world.add(flow);

    linkObjects.set(spec.id, {
      spec,
      curve,
      material,
      haloMaterial,
      baseColor: new THREE.Color(0x5ce1df),
      flowMaterial,
    });
  }

  // -------------------------------------------------------------- packets ---

  const packet = createPacket(THREE, glowTexture, 0xffb090, materials);
  world.add(packet.sprite);
  world.add(packet.trail);

  const ackPacket = createPacket(THREE, glowTexture, 0x62e6b5, materials);
  world.add(ackPacket.sprite);
  world.add(ackPacket.trail);

  // Ambient traffic.
  //
  // Before the story starts, packets are already crossing the mesh. This is
  // what makes the scene read as a live network the moment it appears, rather
  // than as a diagram that only comes alive later.
  const ambientPackets: AmbientPacket[] = [];
  const ambientRoutes = links
    .map((spec) => {
      const entry = linkObjects.get(spec.id);
 return entry;
    })
    .filter((entry): entry is LinkObject => entry !== undefined);

  const ambientPacketCount = compact ? 3 : 6;
  for (let index = 0; index < ambientPacketCount; index += 1) {
    const route = ambientRoutes[index % Math.max(1, ambientRoutes.length)];
    if (!route) break;
    const material = new THREE.SpriteMaterial({
      map: glowTexture,
      color: index % 3 === 0 ? 0xffb090 : 0x8df6ef,
      transparent: true,
      opacity: 0.7,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      depthTest: false,
    });
    materials.push(material);
    const sprite = new THREE.Sprite(material);
    const scale = index % 3 === 0 ? 0.3 : 0.2;
    sprite.scale.setScalar(scale);
    sprite.renderOrder = 6;
    world.add(sprite);
    ambientPackets.push({
      sprite,
      material,
      curve: route.curve,
      offset: seeded(),
      speed: 0.045 + seeded() * 0.05,
      scale,
    });
  }

  // ------------------------------------------------------------- ambient ---

  const ambientCount = compact ? 240 : 620;
  const ambientPositions = new Float32Array(ambientCount * 3);
  const ambientColors = new Float32Array(ambientCount * 3);
  const ambientSizes = new Float32Array(ambientCount);
  const cyan = new THREE.Color(0x5ce1df);
  const white = new THREE.Color(0xd8f5f1);
  for (let index = 0; index < ambientCount; index += 1) {
    const radius = 3.4 + seeded() * 9.5;
    const angle = seeded() * Math.PI * 2;
    ambientPositions[index * 3] = Math.cos(angle) * radius * 1.5;
    ambientPositions[index * 3 + 1] = (seeded() - 0.5) * 10;
    ambientPositions[index * 3 + 2] = -3.4 - seeded() * 13;
    const color = seeded() > 0.72 ? white : cyan;
    ambientColors[index * 3] = color.r;
    ambientColors[index * 3 + 1] = color.g;
    ambientColors[index * 3 + 2] = color.b;
    ambientSizes[index] = 0.8 + seeded() * 3;
  }

  const ambientGeometry = new THREE.BufferGeometry();
  ambientGeometry.setAttribute('position', new THREE.BufferAttribute(ambientPositions, 3));
  ambientGeometry.setAttribute('aColor', new THREE.BufferAttribute(ambientColors, 3));
  ambientGeometry.setAttribute('aSize', new THREE.BufferAttribute(ambientSizes, 1));
  geometries.push(ambientGeometry);

  const ambientUniforms = {
    uPixelRatio: { value: renderer.getPixelRatio() },
    uOpacity: { value: 0.55 },
  };
  const ambientMaterial = new THREE.ShaderMaterial({
    uniforms: ambientUniforms,
    vertexShader: `
      attribute vec3 aColor;
      attribute float aSize;
      uniform float uPixelRatio;
      varying vec3 vColor;
      varying float vDepth;
      void main() {
        vec4 mvPosition = modelViewMatrix * vec4(position, 1.0);
        gl_Position = projectionMatrix * mvPosition;
        gl_PointSize = clamp(aSize * uPixelRatio * (42.0 / max(1.0, -mvPosition.z)), 1.0, 10.0);
        vColor = aColor;
        vDepth = clamp(1.0 - ((-mvPosition.z - 4.0) / 24.0), 0.2, 1.0);
      }
    `,
    fragmentShader: `
      uniform float uOpacity;
      varying vec3 vColor;
      varying float vDepth;
      void main() {
        vec2 coord = gl_PointCoord - vec2(0.5);
        float distanceToCenter = length(coord);
        if (distanceToCenter > 0.5) discard;
        float alpha = smoothstep(0.5, 0.02, distanceToCenter);
        gl_FragColor = vec4(vColor, alpha * uOpacity * vDepth);
      }
    `,
    transparent: true,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
  });
  materials.push(ambientMaterial);
  const ambient = new THREE.Points(ambientGeometry, ambientMaterial);
  world.add(ambient);

  // Concentric orbit rings give the scene depth and a horizon to sit against.
  const orbitMaterial = new THREE.LineBasicMaterial({
    color: 0x5ce1df,
    transparent: true,
    opacity: 0.08,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
  });
  materials.push(orbitMaterial);
  const orbits: InstanceType<Three['LineLoop']>[] = [];
  for (const [radius, segments, tilt] of [
    [5.4, 180, 0.24],
    [8.2, 240, 0.18],
    [11.4, 300, 0.1],
  ] as const) {
    const points: InstanceType<Three['Vector3']>[] = [];
    for (let index = 0; index < segments; index += 1) {
      const angle = (index / segments) * Math.PI * 2;
      points.push(
        new THREE.Vector3(
          Math.cos(angle) * radius,
          Math.sin(angle) * radius * tilt,
          -3.2 - radius * 0.3,
        ),
      );
    }
    const geometry = new THREE.BufferGeometry().setFromPoints(points);
    geometries.push(geometry);
    const loop = new THREE.LineLoop(geometry, orbitMaterial);
    world.add(loop);
    orbits.push(loop);
  }

  // ------------------------------------------------------------- picking ---

  const raycaster = new THREE.Raycaster();
  const pointerNdc = new THREE.Vector2(0, 0);
  const pickTargets: InstanceType<Three['Object3D']>[] = [];
  for (const entry of nodeObjects.values()) {
    pickTargets.push(entry.core);
  }

  // -------------------------------------------------------------- runtime ---

  const pointer = { x: 0, y: 0 };
  const targetPointer = { x: 0, y: 0 };
  const cameraTarget = new THREE.Vector3();
  const cameraLook = new THREE.Vector3();
  const scratch = new THREE.Vector3();
  const scratchScale = new THREE.Vector3();
  const scratchColor = new THREE.Color();
  const gray = new THREE.Color(0x2b3533);
  const amber = new THREE.Color(0xefb861);
  const jade = new THREE.Color(0x62e6b5);
  const hot = new THREE.Color(0xffffff);

  let state: HeroState = derive(0);
  let activeNode: HeroNodeId | null = null;
  let activeLink: string | null = null;
  let hoveredNode: HeroNodeId | null = null;
  let hoveredLink: string | null = null;
  let pointerInside = false;
  let pointerDirty = false;
  let elapsed = 0;
  let lastTime = 0;
  let running = false;
  let frameId = 0;
  let stageVisible = true;
  let documentVisible = !document.hidden;

  const measure = () => {
    const width = Math.max(1, host.clientWidth);
    const height = Math.max(1, host.clientHeight);
    renderer.setSize(width, height, false);
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
    const ratio = Math.min(window.devicePixelRatio || 1, maxPixelRatio);
    renderer.setPixelRatio(ratio);
    ambientUniforms.uPixelRatio.value = ratio;
  };

  const onPointerMove = (event: PointerEvent) => {
    const rect = renderer.domElement.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return;
    pointerNdc.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    pointerNdc.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;
    targetPointer.x = pointerNdc.x;
    targetPointer.y = pointerNdc.y;
    pointerInside = true;
    pointerDirty = true;
  };

  const onPointerLeave = () => {
    pointerInside = false;
    targetPointer.x = 0;
    targetPointer.y = 0;
    pointerDirty = true;
  };

  const onPointerDown = (event: PointerEvent) => {
    const rect = renderer.domElement.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return;
    pointerNdc.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    pointerNdc.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;
    const hit = pick();
    if (hit.node) {
      callbacks.onSelectNode(hit.node);
    } else {
      callbacks.onSelectNode(null);
    }
  };

  const pick = (): { node: HeroNodeId | null; link: string | null } => {
    if (!pointerInside) return { node: null, link: null };
    raycaster.setFromCamera(pointerNdc, camera);
    const hits = raycaster.intersectObjects(pickTargets, false);
    const first = hits[0];
    if (first) {
      const found = first.object.userData.nodeId as HeroNodeId | undefined;
      if (found) return { node: found, link: null };
    }
    // Otherwise the nearest link under the pointer.
    let bestId: string | null = null;
    let bestDistance = 0.42;
    for (const entry of linkObjects.values()) {
      const distance = distanceToCurve(raycaster.ray, entry.curve);
      if (distance < bestDistance) {
        bestDistance = distance;
        bestId = entry.spec.id;
      }
    }
    return { node: null, link: bestId };
  };

  const renderFrame = (now: number) => {
    if (!running) return;
    frameId = window.requestAnimationFrame(renderFrame);
    if (!stageVisible || !documentVisible) {
      lastTime = now;
      return;
    }
    const dt = Math.min(0.05, Math.max(0.001, (now - lastTime) / 1000));
    lastTime = now;
    elapsed += dt;

    pointer.x += (targetPointer.x - pointer.x) * 0.07;
    pointer.y += (targetPointer.y - pointer.y) * 0.07;

    if (pointerDirty) {
      pointerDirty = false;
      const hit = pick();
      if (hit.node !== hoveredNode) {
        hoveredNode = hit.node;
        callbacks.onHoverNode(hit.node);
      }
      if (hit.link !== hoveredLink) {
        hoveredLink = hit.link;
        callbacks.onHoverLink(hit.link);
      }
      renderer.domElement.style.cursor = hit.node || hit.link ? 'pointer' : '';
    }

    applyState(reduced ? 1 : 0.085, dt);
    renderer.render(scene, camera);
  };

  /**
   * Applies the current chapter to the scene.
   *
   * Every visual property is derived from `state`, so the scene, the HUD and
   * the accessible transcript always agree about what is happening.
   */
  const applyState = (smoothing: number, dt: number) => {
    const chapter = state.chapterIndex;

    // ---------------------------------------------------------- camera ---
    // The camera travels along the story rather than sitting still, and the
    // pointer adds a parallax offset on top of that.
    const focusPosition =
      nodePositions.get(state.focus)?.clone() ?? new THREE.Vector3(0, 0, 0);

    const zoomTargets = [14.4, 12.4, 13.6, 12.2, 12.8];
    const zoom = zoomTargets[chapter] ?? 13.6;
    const baseZ = compact ? zoom + 3.4 : zoom;

    cameraTarget.set(
      focusPosition.x * 0.34 + pointer.x * 0.9,
      focusPosition.y * 0.3 + 0.35 - pointer.y * 0.55,
      baseZ,
    );
    camera.position.lerp(cameraTarget, smoothing);

    cameraLook.set(focusPosition.x * 0.42, focusPosition.y * 0.34, focusPosition.z * 0.2);
    camera.lookAt(cameraLook);

    world.rotation.y = pointer.x * 0.075 + Math.sin(elapsed * 0.12) * 0.035;
    world.rotation.x = -pointer.y * 0.04 + Math.sin(elapsed * 0.09) * 0.018;

    // ------------------------------------------------------------ nodes ---
    for (const entry of nodeObjects.values()) {
      const isOffline = state.offline.includes(entry.spec.id);
      const isFocus = state.focus === entry.spec.id;
      const isCarrier = state.carrier === entry.spec.id;
      const isRoute = state.route.includes(entry.spec.id);
      const isHovered = hoveredNode === entry.spec.id;
      const isActive = activeNode === entry.spec.id;

      // Idle drift. The amplitude is large enough to read as motion: roughly
      // a third of a world unit, about 30 px at the default framing.
      const bob = Math.sin(elapsed * 0.62 + entry.phase);
      const sway = Math.cos(elapsed * 0.47 + entry.phase * 1.3);
      const rise = Math.sin(elapsed * 0.81 + entry.phase * 0.7);
      entry.group.position.set(
        entry.spec.position[0] + entry.drift.x * sway,
        entry.spec.position[1] + entry.drift.y * bob,
        entry.spec.position[2] + entry.drift.z * rise,
      );

      const emphasis = isFocus ? 1 : isCarrier ? 0.7 : isRoute ? 0.4 : 0;
      const targetScale = 1 + emphasis * 0.16;
      entry.group.scale.lerp(
        scratchScale.set(targetScale, targetScale, targetScale),
        smoothing,
      );

      const haloMaterial = entry.halo.material as InstanceType<Three['SpriteMaterial']>;
      const coreMaterial = entry.core.material as InstanceType<Three['SpriteMaterial']>;
      const ringMaterial = entry.ring.material as InstanceType<Three['MeshBasicMaterial']>;
      const outerMaterial = entry.outerRing.material as InstanceType<Three['MeshBasicMaterial']>;
      const shellMaterial = entry.shell.material as InstanceType<Three['MeshBasicMaterial']>;

      let target = entry.baseColor;
      if (isOffline) {
        target = gray;
      } else if (isCarrier && state.phase !== 'ambient' && state.phase !== 'interrupted') {
        target = scratchColor.copy(entry.baseColor).lerp(amber, 0.55);
      } else if (state.phase === 'acknowledged' && entry.spec.role === 'recipient') {
        target = jade;
      } else if (isFocus) {
        target = scratchColor.copy(entry.baseColor).lerp(hot, 0.35);
      }
      haloMaterial.color.lerp(target, smoothing);
      coreMaterial.color.lerp(target, smoothing);
      ringMaterial.color.lerp(target, smoothing);
      outerMaterial.color.lerp(target, smoothing);
      shellMaterial.color.lerp(target, smoothing);

      const breathe = 1 + Math.sin(elapsed * 2.1 + entry.phase) * 0.14;
      const haloScale = 2.3 * entry.spec.scale * breathe * (isFocus ? 1.3 : 1);
      entry.halo.scale.lerp(scratchScale.setScalar(haloScale), smoothing);

      const coreScale =
        0.28 * entry.spec.scale * (1 + Math.sin(elapsed * 3.1 + entry.phase) * 0.16);
      entry.core.scale.lerp(scratchScale.setScalar(coreScale), smoothing);

      entry.ring.quaternion.copy(camera.quaternion);
      entry.outerRing.quaternion.copy(camera.quaternion);

      const offlineFade = isOffline ? 0.24 : 1;
      const hoverBoost = isHovered || isActive ? 1.7 : 1;
      haloMaterial.opacity = 0.46 * offlineFade * hoverBoost * (isFocus ? 1.15 : 1);
      coreMaterial.opacity = 0.98 * offlineFade;
      ringMaterial.opacity =
        (isFocus ? 0.75 : 0.36) * offlineFade * (isHovered || isActive ? 1.5 : 1);
      outerMaterial.opacity =
        (isFocus ? 0.3 : 0.13) * offlineFade * (isHovered || isActive ? 1.6 : 1);
      shellMaterial.opacity = (isFocus ? 0.34 : 0.13) * offlineFade;

      // The shell turns slowly, so a node reads as an object in a space
      // rather than as a flat disc.
      entry.shell.rotation.y += dt * (0.35 + emphasis * 0.8);
      entry.shell.rotation.x += dt * 0.22;

      if (entry.label) {
        entry.label.quaternion.copy(camera.quaternion);
        const labelMaterial = entry.label.material as InstanceType<Three['SpriteMaterial']>;
        labelMaterial.opacity = 0.85 * offlineFade;
      }
    }

    // ------------------------------------------------------------ links ---
    for (const entry of linkObjects.values()) {
      const isBroken = state.brokenLinks.includes(entry.spec.id);
      const isRoute = isRouteLink(entry.spec.from, entry.spec.to, state.route);
      const isHovered = hoveredLink === entry.spec.id;
      const isActive = activeLink === entry.spec.id;

      const base = isRoute ? 0.62 : 0.2;
      const hoverBoost = isHovered || isActive ? 2.1 : 1;
      // A broken link fades towards a dim dead grey rather than vanishing, so
      // the interruption is legible.
      const targetOpacity = isBroken ? 0.05 : base * hoverBoost;
      entry.material.opacity += (targetOpacity - entry.material.opacity) * smoothing;
      entry.haloMaterial.opacity +=
        ((isBroken ? 0.008 : (isRoute ? 0.11 : 0.035) * hoverBoost) -
          entry.haloMaterial.opacity) *
        smoothing;

      const targetColor = isBroken
        ? gray
        : isRoute
          ? entry.baseColor
          : scratchColor.copy(entry.baseColor).lerp(gray, 0.55);
      entry.material.color.lerp(targetColor, smoothing);
      entry.haloMaterial.color.lerp(targetColor, smoothing);

      // A travelling highlight along live links: the "data flowing" read.
      if (!isBroken) {
        const speed = isRoute ? 0.34 : 0.16;
        const t = (elapsed * speed + hashOffset(entry.spec.id)) % 1;
        const highlight = Math.max(0, 1 - Math.abs(t - 0.5) * 6);
        entry.flowMaterial.opacity = highlight * (isRoute ? 0.5 : 0.16);
        const tint = isRoute
          ? entry.baseColor
          : scratchColor.copy(entry.baseColor).lerp(gray, 0.5);
        entry.flowMaterial.color.lerp(tint, smoothing);
      } else {
        entry.flowMaterial.opacity += (0 - entry.flowMaterial.opacity) * smoothing;
      }
    }

    // ----------------------------------------------------------- packet ---
    updatePacket(packet, state, elapsed, 'forward');
    updatePacket(ackPacket, state, elapsed, 'ack');

    // Ambient traffic keeps running in every chapter, so the mesh never looks
    // frozen even when the story packet is not on screen.
    for (const ambient of ambientPackets) {
      const t = (elapsed * ambient.speed + ambient.offset) % 1;
      ambient.curve.getPoint(t, scratch);
      ambient.sprite.position.lerp(scratch, 0.4);
      const pulse = 1 + Math.sin(elapsed * 6 + ambient.offset * 12) * 0.2;
      ambient.sprite.scale.setScalar(ambient.scale * pulse);
      const visible = 1 - state.local * 0.35;
      ambient.material.opacity = 0.7 * visible;
    }

    // ---------------------------------------------------------- ambient ---
    ambientUniforms.uOpacity.value = 0.55;
    ambient.rotation.y = elapsed * 0.012;
    const orbit = orbits[0];
    const orbitSecond = orbits[1];
    const orbitThird = orbits[2];
    if (orbit) orbit.rotation.z += dt * 0.02;
    if (orbitSecond) orbitSecond.rotation.z -= dt * 0.014;
    if (orbitThird) orbitThird.rotation.z += dt * 0.009;
  };

  const updatePacket = (
    target: PacketObject,
    current: HeroState,
    time: number,
    kind: 'forward' | 'ack',
  ) => {
    const journey = current.journey;
    let progressValue = kind === 'forward' ? current.packetProgress : current.ackProgress;

    if (journey.length < 2 || progressValue <= 0) {
      target.material.opacity += (0 - target.material.opacity) * 0.12;
      target.trailMaterial.opacity += (0 - target.trailMaterial.opacity) * 0.12;
      return;
    }

    const legs = journey.length - 1;

    /*
      The story packet keeps moving on its own.

      A packet that only advances while the visitor scrolls reads as a static
      diagram. The scroll position sets where the message is in the narrative;
      a constant crawl along the path keeps the mesh visibly alive in between.
    */
    if (kind === 'forward' && current.phase === 'ambient') {
      progressValue = (time * 0.09) % legs;
    }

    const clamped = Math.min(progressValue, legs);
    const legIndex = Math.min(legs - 1, Math.floor(clamped));
    const legT = clamped - legIndex;
    const fromId = journey[legIndex];
    const toId = journey[legIndex + 1];
    if (!fromId || !toId) return;

    const from = nodePositions.get(fromId);
    const to = nodePositions.get(toId);
    if (!from || !to) return;

    // Follow a gentle arc so the packet visibly travels rather than jumps.
    scratch.lerpVectors(from, to, legT);
    scratch.y += Math.sin(legT * Math.PI) * 0.55;
    scratch.x += Math.sin(legT * Math.PI) * (fromId < toId ? 0.18 : -0.18);

    if (kind === 'ack') {
      // The acknowledgement returns along the path the message took.
      const back = 1 - Math.min(1, progressValue);
      scratch.lerpVectors(to, from, back);
      scratch.y += Math.sin(back * Math.PI) * 0.55;
    }

    target.sprite.position.lerp(scratch, 0.18);

    const visible = progressValue > 0;
    const pulse = 1 + Math.sin(time * 7) * 0.18;
    target.sprite.scale.setScalar((kind === 'forward' ? 0.46 : 0.34) * pulse);
    target.material.opacity += ((visible ? 0.95 : 0) - target.material.opacity) * 0.12;

    // Keep a short history so the packet leaves a trail behind it rather
    // than a line drawn back to the world origin.
    target.cursor = (target.cursor + 1) % target.history.length;
    const newest = target.history[target.cursor];
    if (newest) newest.copy(target.sprite.position);

    const array = target.positions.array as Float32Array;
    for (let index = 0; index < target.history.length; index += 1) {
      const point = target.history[(target.cursor - index + target.history.length) % target.history.length];
      if (!point) continue;
      array[index * 3] = point.x;
      array[index * 3 + 1] = point.y;
      array[index * 3 + 2] = point.z;
    }
    target.positions.needsUpdate = true;
    target.trailMaterial.opacity += ((visible ? 0.35 : 0) - target.trailMaterial.opacity) * 0.12;
  };

  // ----------------------------------------------------------- listeners ---

  const canvas = renderer.domElement;
  canvas.addEventListener('pointermove', onPointerMove, { passive: true });
  canvas.addEventListener('pointerleave', onPointerLeave, { passive: true });
  canvas.addEventListener('pointerdown', onPointerDown);
  window.addEventListener('resize', measure, { passive: true });
  document.addEventListener('visibilitychange', onVisibility);

  function onVisibility() {
    documentVisible = !document.hidden;
    syncVisibility();
  }

  const resizeObserver = new ResizeObserver(measure);
  resizeObserver.observe(host);

  const intersectionObserver = new IntersectionObserver(
    ([entry]) => {
      stageVisible = Boolean(entry?.isIntersecting);
      syncVisibility();
    },
    { rootMargin: '160px' },
  );
  intersectionObserver.observe(host);

  function syncVisibility() {
    if (stageVisible && documentVisible) start();
    else stop();
  }

  function start() {
    if (running) return;
    running = true;
    lastTime = performance.now();
    frameId = window.requestAnimationFrame(renderFrame);
  }

  function stop() {
    running = false;
    window.cancelAnimationFrame(frameId);
  }

  measure();
  syncVisibility();
  // Paint the first frame with the correct state rather than the default one.
  applyState(1, 0.016);

  return {
    setProgress(next: number) {
      state = derive(next);
    },
    setActiveNode(id: HeroNodeId | null) {
      activeNode = id;
    },
    setActiveLink(id: string | null) {
      activeLink = id;
    },
    dispose() {
      stop();
      resizeObserver.disconnect();
      intersectionObserver.disconnect();
      canvas.removeEventListener('pointermove', onPointerMove);
      canvas.removeEventListener('pointerleave', onPointerLeave);
      canvas.removeEventListener('pointerdown', onPointerDown);
      window.removeEventListener('resize', measure);
      document.removeEventListener('visibilitychange', onVisibility);
      for (const geometry of geometries) geometry.dispose();
      for (const material of materials) material.dispose();
      for (const texture of textures) texture.dispose();
      renderer.dispose();
      renderer.forceContextLoss();
      renderer.domElement.remove();
    },
  };
}

// --------------------------------------------------------------- helpers ---

function isRouteLink(
  from: HeroNodeId,
  to: HeroNodeId,
  route: readonly HeroNodeId[],
): boolean {
  for (let index = 0; index < route.length - 1; index += 1) {
    const a = route[index];
    const b = route[index + 1];
    if (!a || !b) continue;
    if ((a === from && b === to) || (a === to && b === from)) return true;
  }
  return false;
}

function hashOffset(id: string): number {
  let hash = 2166136261;
  for (let index = 0; index < id.length; index += 1) {
    hash ^= id.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return ((hash >>> 0) % 1000) / 1000;
}

/** Distance from a ray to a curve, sampled at 24 points. */
function distanceToCurve(
  ray: InstanceType<Three['Ray']>,
  curve: InstanceType<Three['CatmullRomCurve3']>,
): number {
  const point = new (ray.origin.constructor as new () => InstanceType<Three['Vector3']>)();
  let closest = Number.POSITIVE_INFINITY;
  for (let index = 0; index <= 24; index += 1) {
    curve.getPoint(index / 24, point);
    const toPoint = point.clone().sub(ray.origin);
    const along = toPoint.dot(ray.direction);
    if (along < 0) continue;
    const projected = ray.origin.clone().addScaledVector(ray.direction, along);
    closest = Math.min(closest, projected.distanceTo(point));
  }
  return Number.isFinite(closest) ? closest : Number.POSITIVE_INFINITY;
}

function createPacket(
  THREE: Three,
  glowTexture: InstanceType<Three['Texture']>,
  color: number,
  materials: Array<{ dispose: () => void }>,
): PacketObject {
  const material = new THREE.SpriteMaterial({
    map: glowTexture,
    color,
    transparent: true,
    opacity: 0,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
    depthTest: false,
  });
  materials.push(material);
  const sprite = new THREE.Sprite(material);
  sprite.scale.setScalar(0.46);
  sprite.renderOrder = 6;

  const trailLength = 14;
  const trailGeometry = new THREE.BufferGeometry();
  const positions = new THREE.BufferAttribute(new Float32Array(trailLength * 3), 3);
  trailGeometry.setAttribute('position', positions);
  const trailMaterial = new THREE.LineBasicMaterial({
    color,
    transparent: true,
    opacity: 0,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
  });
  materials.push(trailMaterial);
  const trail = new THREE.Line(trailGeometry, trailMaterial);
  trail.renderOrder = 5;

  const history: InstanceType<Three['Vector3']>[] = [];
  for (let index = 0; index < trailLength; index += 1) {
    history.push(new THREE.Vector3());
  }

  return { sprite, material, trail, trailMaterial, positions, history, cursor: 0 };
}

function makeGlowTexture(THREE: Three) {
  const size = 256;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const context = canvas.getContext('2d');
  if (!context) return new THREE.Texture();
  const gradient = context.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2);
  gradient.addColorStop(0, 'rgba(255,255,255,1)');
  gradient.addColorStop(0.12, 'rgba(255,255,255,0.85)');
  gradient.addColorStop(0.35, 'rgba(255,255,255,0.22)');
  gradient.addColorStop(1, 'rgba(255,255,255,0)');
  context.fillStyle = gradient;
  context.fillRect(0, 0, size, size);
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.needsUpdate = true;
  return texture;
}

function makeCoreTexture(THREE: Three) {
  const size = 128;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const context = canvas.getContext('2d');
  if (!context) return new THREE.Texture();
  const gradient = context.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2);
  gradient.addColorStop(0, 'rgba(255,255,255,1)');
  gradient.addColorStop(0.5, 'rgba(255,255,255,0.9)');
  gradient.addColorStop(1, 'rgba(255,255,255,0)');
  context.fillStyle = gradient;
  context.fillRect(0, 0, size, size);
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.needsUpdate = true;
  return texture;
}

function makeLabelTexture(THREE: Three, text: string, role: HeroNodeSpec['role']) {
  const canvas = document.createElement('canvas');
  canvas.width = 512;
  canvas.height = 64;
  const context = canvas.getContext('2d');
  if (!context) return new THREE.Texture();
  context.clearRect(0, 0, canvas.width, canvas.height);
  context.font = '600 26px "Instrument Sans", Inter, sans-serif';
  context.textAlign = 'center';
  context.textBaseline = 'middle';
  context.fillStyle =
    role === 'sender' ? '#ffb094' : role === 'recipient' ? '#9df2d1' : '#c4d3cf';
  context.fillText(text, canvas.width / 2, canvas.height / 2);
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.needsUpdate = true;
  return texture;
}

function mulberry32(seed: number): () => number {
  let value = seed;
  return () => {
    value |= 0;
    value = (value + 0x6d2b79f5) | 0;
    let result = Math.imul(value ^ (value >>> 15), 1 | value);
    result = (result + Math.imul(result ^ (result >>> 7), 61 | result)) ^ result;
    return ((result ^ (result >>> 14)) >>> 0) / 4294967296;
  };
}
