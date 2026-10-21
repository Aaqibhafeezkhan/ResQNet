import { useEffect, useRef, useState, type RefObject } from 'react';

import { StaticNetwork } from './StaticNetwork';

type Three = typeof import('three');

interface HeroCanvasProps {
  stageRef: RefObject<HTMLDivElement | null>;
  reduced: boolean;
}

interface NodeSpec {
  id: string;
  role: 'sender' | 'relay' | 'recipient';
  position: [number, number, number];
  size: number;
  label: string;
  color: number;
}

interface LinkSpec {
  from: string;
  to: string;
  route: boolean;
  breakAt?: number;
}

const NODES: readonly NodeSpec[] = [
  { id: 'sender', role: 'sender', position: [-5.3, -0.9, 0.7], size: 1.55, label: 'AMINA · SENDER', color: 0xff5c35 },
  { id: 'relay-a', role: 'relay', position: [-2.8, 1.25, -0.4], size: 0.95, label: 'JONAS · RELAY', color: 0x5ce1df },
  { id: 'relay-b', role: 'relay', position: [-0.6, -1.1, 0.5], size: 0.9, label: 'MEI · RELAY', color: 0x5ce1df },
  { id: 'relay-c', role: 'relay', position: [1.9, 1.05, -0.5], size: 1, label: 'TOMAS · RELAY', color: 0x5ce1df },
  { id: 'relay-d', role: 'relay', position: [3.1, -2.1, -1.4], size: 0.85, label: 'SANA · RELAY', color: 0x5ce1df },
  { id: 'recipient', role: 'recipient', position: [5.25, 0.15, 0.6], size: 1.4, label: 'FIELD POST · RECIPIENT', color: 0x62e6b5 },
];

const LINKS: readonly LinkSpec[] = [
  { from: 'sender', to: 'relay-a', route: true, breakAt: 0.46 },
  { from: 'sender', to: 'relay-b', route: true, breakAt: 0.54 },
  { from: 'relay-a', to: 'relay-b', route: false },
  { from: 'relay-a', to: 'relay-c', route: true, breakAt: 0.62 },
  { from: 'relay-b', to: 'relay-c', route: true, breakAt: 0.68 },
  { from: 'relay-b', to: 'relay-d', route: false },
  { from: 'relay-c', to: 'relay-d', route: false },
  { from: 'relay-c', to: 'recipient', route: true, breakAt: 0.76 },
  { from: 'relay-d', to: 'recipient', route: true, breakAt: 0.82 },
];

const ROUTE = ['sender', 'relay-a', 'relay-c', 'recipient'] as const;

export function HeroCanvas({ stageRef, reduced }: HeroCanvasProps) {
  const hostRef = useRef<HTMLDivElement | null>(null);
  const [live, setLive] = useState(false);

  useEffect(() => {
    if (reduced) {
      setLive(false);
      return undefined;
    }

    const host = hostRef.current;
    const stage = stageRef.current;
    if (!host || !stage) return undefined;

    // Probe before importing Three: on a browser with WebGL disabled the
    // static composition is the finished experience, not a failed canvas.
    const probe = document.createElement('canvas');
    const context = probe.getContext('webgl2') ?? probe.getContext('webgl');
    if (!context) return undefined;

    let cancelled = false;
    let cleanup: (() => void) | undefined;

    void import('three')
      .then((THREE) => {
        if (cancelled) return;
        cleanup = initScene(THREE, host, stage, () => setLive(true));
      })
      .catch(() => {
        if (!cancelled) setLive(false);
      });

    return () => {
      cancelled = true;
      cleanup?.();
      setLive(false);
    };
  }, [reduced, stageRef]);

  return (
    <div className="absolute inset-0 overflow-hidden">
      <StaticNetwork
        className={
          live
            ? 'opacity-0 transition-opacity duration-1000'
            : 'opacity-100 transition-opacity duration-700'
        }
      />
      <div
        ref={hostRef}
        aria-hidden="true"
        className={
          'absolute inset-0 transition-opacity duration-[1400ms] ease-cinematic ' +
          (live ? 'opacity-100' : 'opacity-0')
        }
      />
      <div
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(80%_75%_at_50%_45%,transparent_0%,rgba(4,6,7,0.18)_62%,rgba(4,6,7,0.78)_100%)]"
        aria-hidden="true"
      />
    </div>
  );
}

function initScene(
  THREE: Three,
  host: HTMLDivElement,
  stage: HTMLDivElement,
  onReady: () => void,
): () => void {
  let renderer: InstanceType<Three['WebGLRenderer']>;
  try {
    renderer = new THREE.WebGLRenderer({
      alpha: true,
      antialias: true,
      powerPreference: 'high-performance',
    });
  } catch {
    return () => undefined;
  }

  if (!renderer.getContext()) return () => undefined;

  const compact = window.matchMedia('(max-width: 767px)').matches;
  const maxPixelRatio = compact ? 1.5 : 1.75;
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, maxPixelRatio));
  renderer.setClearColor(0x000000, 0);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.domElement.style.width = '100%';
  renderer.domElement.style.height = '100%';
  renderer.domElement.style.display = 'block';
  host.appendChild(renderer.domElement);

  const scene = new THREE.Scene();
  scene.fog = new THREE.FogExp2(0x040607, 0.032);
  const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 80);
  camera.position.set(0, 0, compact ? 15 : 13.2);

  const world = new THREE.Group();
  world.scale.setScalar(compact ? 0.6 : 1);
  world.position.y = compact ? 0.7 : 0;
  scene.add(world);

  const seeded = mulberry32(20261021);
  const textures: Array<{ dispose: () => void }> = [];
  const materials: Array<{ dispose: () => void }> = [];
  const geometries: Array<{ dispose: () => void }> = [];

  const glowTexture = makeGlowTexture(THREE);
  const brightTexture = makeCoreTexture(THREE);
  textures.push(glowTexture, brightTexture);

  const displayNodes = NODES.map((node) => ({
    ...node,
    position: [
      compact ? node.position[0] * 0.55 : node.position[0],
      compact ? node.position[1] * 0.95 : node.position[1],
      node.position[2],
    ] as [number, number, number],
  }));
  const nodeMap = new Map(displayNodes.map((node) => [node.id, node]));
  const nodeObjects = new Map<
    string,
    {
      halo: InstanceType<Three['Sprite']>;
      core: InstanceType<Three['Sprite']>;
      ring: InstanceType<Three['Mesh']>;
      outerRing: InstanceType<Three['Mesh']>;
      label: InstanceType<Three['Sprite']> | null;
      baseColor: InstanceType<Three['Color']>;
      baseScale: number;
      phase: number;
    }
  >();

  for (const node of displayNodes) {
    const group = new THREE.Group();
    group.position.set(...node.position);

    const baseColor = new THREE.Color(node.color);
    const haloMaterial = new THREE.SpriteMaterial({
      map: glowTexture,
      color: baseColor,
      transparent: true,
      opacity: node.role === 'relay' ? 0.52 : 0.7,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      depthTest: false,
    });
    materials.push(haloMaterial);
    const halo = new THREE.Sprite(haloMaterial);
    halo.scale.setScalar(2.5 * node.size);
    halo.renderOrder = 2;
    group.add(halo);

    const coreMaterial = new THREE.SpriteMaterial({
      map: brightTexture,
      color: baseColor,
      transparent: true,
      opacity: 0.98,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      depthTest: false,
    });
    materials.push(coreMaterial);
    const core = new THREE.Sprite(coreMaterial);
    core.scale.setScalar(0.25 * node.size);
    core.renderOrder = 3;
    group.add(core);

    const ringGeometry = new THREE.RingGeometry(0.22 * node.size, 0.235 * node.size, 64);
    geometries.push(ringGeometry);
    const ringMaterial = new THREE.MeshBasicMaterial({
      color: baseColor,
      transparent: true,
      opacity: node.role === 'relay' ? 0.32 : 0.58,
      side: THREE.DoubleSide,
      depthWrite: false,
      depthTest: false,
    });
    materials.push(ringMaterial);
    const ring = new THREE.Mesh(ringGeometry, ringMaterial);
    ring.renderOrder = 3;
    group.add(ring);

    const outerRingGeometry = new THREE.RingGeometry(0.34 * node.size, 0.345 * node.size, 48);
    geometries.push(outerRingGeometry);
    const outerRingMaterial = new THREE.MeshBasicMaterial({
      color: baseColor,
      transparent: true,
      opacity: 0.12,
      side: THREE.DoubleSide,
      depthWrite: false,
      depthTest: false,
    });
    materials.push(outerRingMaterial);
    const outerRing = new THREE.Mesh(outerRingGeometry, outerRingMaterial);
    outerRing.renderOrder = 3;
    group.add(outerRing);

    let label: InstanceType<Three['Sprite']> | null = null;
    if (node.role !== 'relay' || node.id === 'relay-a' || node.id === 'relay-c') {
      const labelTexture = makeLabelTexture(THREE, node.label, node.role);
      textures.push(labelTexture);
      const labelMaterial = new THREE.SpriteMaterial({
        map: labelTexture,
        transparent: true,
        opacity: 0.82,
        depthWrite: false,
        depthTest: false,
      });
      materials.push(labelMaterial);
      label = new THREE.Sprite(labelMaterial);
      label.scale.set(2.6, 0.325, 1);
      label.position.set(0, -0.52 * node.size, 0);
      label.renderOrder = 4;
      group.add(label);
    }

    world.add(group);
    nodeObjects.set(node.id, {
      halo,
      core,
      ring,
      outerRing,
      label,
      baseColor,
      baseScale: node.size,
      phase: seeded() * Math.PI * 2,
    });
  }

  const linkObjects: Array<{
    material: InstanceType<Three['MeshBasicMaterial']>;
    haloMaterial: InstanceType<Three['MeshBasicMaterial']>;
    route: boolean;
    breakAt: number;
  }> = [];

  for (const link of LINKS) {
    const from = nodeMap.get(link.from);
    const to = nodeMap.get(link.to);
    if (!from || !to) continue;
    const start = new THREE.Vector3(...from.position);
    const end = new THREE.Vector3(...to.position);
    const curve = new THREE.CatmullRomCurve3([start, end]);
    const tubeGeometry = new THREE.TubeGeometry(curve, 12, link.route ? 0.011 : 0.006, 5, false);
    geometries.push(tubeGeometry);
    const material = new THREE.MeshBasicMaterial({
      color: link.route ? 0x5ce1df : 0x47615f,
      transparent: true,
      opacity: link.route ? 0.45 : 0.16,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      depthTest: true,
    });
    materials.push(material);
    const tube = new THREE.Mesh(tubeGeometry, material);
    world.add(tube);

    const haloGeometry = new THREE.TubeGeometry(curve, 12, link.route ? 0.055 : 0.025, 5, false);
    geometries.push(haloGeometry);
    const haloMaterial = new THREE.MeshBasicMaterial({
      color: link.route ? 0x5ce1df : 0x8aa39f,
      transparent: true,
      opacity: link.route ? 0.055 : 0.018,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      depthTest: true,
    });
    materials.push(haloMaterial);
    world.add(new THREE.Mesh(haloGeometry, haloMaterial));

    linkObjects.push({
      material,
      haloMaterial,
      route: link.route,
      breakAt: link.breakAt ?? 0.5,
    });
  }

  const ambientCount = compact ? 280 : 620;
  const ambientPositions = new Float32Array(ambientCount * 3);
  const ambientColors = new Float32Array(ambientCount * 3);
  const ambientSizes = new Float32Array(ambientCount);
  const cyan = new THREE.Color(0x5ce1df);
  const white = new THREE.Color(0xd8f5f1);
  for (let index = 0; index < ambientCount; index += 1) {
    const radius = 3.4 + seeded() * 8.5;
    const angle = seeded() * Math.PI * 2;
    ambientPositions[index * 3] = Math.cos(angle) * radius * 1.35;
    ambientPositions[index * 3 + 1] = (seeded() - 0.5) * 9;
    ambientPositions[index * 3 + 2] = -2.8 - seeded() * 11;
    const color = seeded() > 0.75 ? white : cyan;
    ambientColors[index * 3] = color.r;
    ambientColors[index * 3 + 1] = color.g;
    ambientColors[index * 3 + 2] = color.b;
    ambientSizes[index] = 0.6 + seeded() * 2.4;
  }

  const ambientGeometry = new THREE.BufferGeometry();
  ambientGeometry.setAttribute('position', new THREE.BufferAttribute(ambientPositions, 3));
  ambientGeometry.setAttribute('aColor', new THREE.BufferAttribute(ambientColors, 3));
  ambientGeometry.setAttribute('aSize', new THREE.BufferAttribute(ambientSizes, 1));
  geometries.push(ambientGeometry);

  const ambientUniforms = {
    uPixelRatio: { value: renderer.getPixelRatio() },
    uOpacity: { value: 0.5 },
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
        gl_PointSize = clamp(aSize * uPixelRatio * (36.0 / max(1.0, -mvPosition.z)), 1.0, 9.0);
        vColor = aColor;
        vDepth = clamp(1.0 - ((-mvPosition.z - 3.5) / 22.0), 0.18, 1.0);
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

  const ringMaterial = new THREE.LineBasicMaterial({
    color: 0x5ce1df,
    transparent: true,
    opacity: 0.07,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
  });
  materials.push(ringMaterial);
  for (const [radius, segments] of [
    [5.6, 160],
    [8.4, 220],
  ] as const) {
    const points: InstanceType<Three['Vector3']>[] = [];
    for (let index = 0; index < segments; index += 1) {
      const angle = (index / segments) * Math.PI * 2;
      points.push(new THREE.Vector3(Math.cos(angle) * radius, Math.sin(angle) * radius * 0.62, -2.5));
    }
    const geometry = new THREE.BufferGeometry().setFromPoints(points);
    geometries.push(geometry);
    const loop = new THREE.LineLoop(geometry, ringMaterial);
    world.add(loop);
  }

  const routePoints: InstanceType<Three['Vector3']>[] = [];
  for (const id of ROUTE) {
    const spec = nodeMap.get(id);
    if (!spec) continue;
    routePoints.push(new THREE.Vector3(...spec.position));
  }
  const routeSegments: Array<{ start: InstanceType<Three['Vector3']>; end: InstanceType<Three['Vector3']>; length: number }> = [];
  let routeLength = 0;
  for (let index = 0; index < routePoints.length - 1; index += 1) {
    const start = routePoints[index];
    const end = routePoints[index + 1];
    if (!start || !end) continue;
    const length = start.distanceTo(end);
    routeSegments.push({ start, end, length });
    routeLength += length;
  }

  const pulseCount = compact ? 3 : 5;
  const pulses: Array<{
    sprite: InstanceType<Three['Sprite']>;
    material: InstanceType<Three['SpriteMaterial']>;
    offset: number;
    speed: number;
  }> = [];
  for (let index = 0; index < pulseCount; index += 1) {
    const material = new THREE.SpriteMaterial({
      map: glowTexture,
      color: index === 0 ? 0xffb090 : 0x8df6ef,
      transparent: true,
      opacity: 0.9,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      depthTest: false,
    });
    materials.push(material);
    const sprite = new THREE.Sprite(material);
    sprite.scale.setScalar(index === 0 ? 0.52 : 0.32);
    sprite.renderOrder = 5;
    world.add(sprite);
    pulses.push({
      sprite,
      material,
      offset: index / pulseCount,
      speed: 0.055 + seeded() * 0.02,
    });
  }

  const gray = new THREE.Color(0x33403e);
  const scratchColor = new THREE.Color();
  const scratchPosition = new THREE.Vector3();
  const pointer = { x: 0, y: 0 };
  const targetPointer = { x: 0, y: 0 };
  let stageTop = 0;
  let stageHeight = 1;

  const measure = () => {
    const rect = stage.getBoundingClientRect();
    stageTop = rect.top + window.scrollY;
    stageHeight = Math.max(1, rect.height);
  };

  const resize = () => {
    const width = Math.max(1, host.clientWidth);
    const height = Math.max(1, host.clientHeight);
    renderer.setSize(width, height, false);
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
    const ratio = Math.min(window.devicePixelRatio || 1, maxPixelRatio);
    renderer.setPixelRatio(ratio);
    ambientUniforms.uPixelRatio.value = ratio;
    measure();
  };

  const onPointerMove = (event: PointerEvent) => {
    targetPointer.x = (event.clientX / window.innerWidth) * 2 - 1;
    targetPointer.y = (event.clientY / window.innerHeight) * 2 - 1;
  };

  let stageVisible = true;
  let documentVisible = !document.hidden;
  let running = false;
  let frameId = 0;
  let lastTime = 0;
  let elapsed = 0;

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

    pointer.x += (targetPointer.x - pointer.x) * 0.045;
    pointer.y += (targetPointer.y - pointer.y) * 0.045;

    const viewport = window.innerHeight;
    const progress = clamp((window.scrollY - stageTop) / Math.max(1, stageHeight - viewport), 0, 1);
    const failure = smoothstep(0.34, 0.92, progress);
    const quiet = smoothstep(0.08, 0.5, progress) * (1 - smoothstep(0.88, 1, progress) * 0.35);

    const baseZ = compact ? 15 : 13.2;
    camera.position.x += ((-1.5 * failure + pointer.x * 0.55) - camera.position.x) * 0.035;
    camera.position.y += ((0.25 + failure * 0.65 - pointer.y * 0.32) - camera.position.y) * 0.035;
    camera.position.z += ((baseZ - failure * 3.4) - camera.position.z) * 0.025;
    camera.lookAt(0.4 * failure, 0.1 + 0.15 * failure, 0);

    world.rotation.y = pointer.x * 0.055 + Math.sin(elapsed * 0.08) * 0.02 + failure * 0.06;
    world.rotation.x = -pointer.y * 0.03 + Math.sin(elapsed * 0.06) * 0.01;

    for (const link of linkObjects) {
      const cut = link.route ? smoothstep(link.breakAt - 0.03, link.breakAt + 0.07, progress) : failure * 0.45;
      const flicker =
        link.route && progress > link.breakAt - 0.12 && progress < link.breakAt
          ? 0.55 + Math.sin(elapsed * 34 + link.breakAt * 20) * 0.45
          : 1;
      link.material.opacity = (link.route ? 0.45 : 0.16) * (1 - cut) * flicker;
      link.haloMaterial.opacity = (link.route ? 0.055 : 0.018) * (1 - cut) * flicker;
    }

    for (const node of displayNodes) {
      const entry = nodeObjects.get(node.id);
      if (!entry) continue;
      const isSender = node.role === 'sender';
      const isRecipient = node.role === 'recipient';
      const dim = isSender ? failure * 0.16 : failure * (isRecipient ? 0.55 : 0.78);
      const color = scratchColor.copy(entry.baseColor).lerp(gray, dim);
      const material = entry.halo.material as InstanceType<Three['SpriteMaterial']>;
      const coreMaterial = entry.core.material as InstanceType<Three['SpriteMaterial']>;
      const ringMaterial = entry.ring.material as InstanceType<Three['MeshBasicMaterial']>;
      const outerMaterial = entry.outerRing.material as InstanceType<Three['MeshBasicMaterial']>;
      material.color.copy(color);
      coreMaterial.color.copy(color);
      ringMaterial.color.copy(color);
      outerMaterial.color.copy(color);

      const pulse = 1 + Math.sin(elapsed * 1.6 + entry.phase) * 0.045;
      entry.halo.scale.setScalar(2.5 * entry.baseScale * pulse);
      entry.core.scale.setScalar(0.25 * entry.baseScale * (1 + Math.sin(elapsed * 2.2 + entry.phase) * 0.08));
      entry.ring.quaternion.copy(camera.quaternion);
      entry.outerRing.quaternion.copy(camera.quaternion);
      if (entry.label) {
        entry.label.quaternion.copy(camera.quaternion);
        const labelMaterial = entry.label.material as InstanceType<Three['SpriteMaterial']>;
        labelMaterial.opacity = 0.82 * (1 - failure * (isSender ? 0.15 : 0.75));
      }

      const active =
        isSender && failure > 0.4
          ? 1 + Math.sin(elapsed * 3.2) * 0.18
          : 1 + Math.sin(elapsed * 1.4 + entry.phase) * 0.06;
      ringMaterial.opacity = (node.role === 'relay' ? 0.32 : 0.58) * active * (1 - dim * 0.6);
      outerMaterial.opacity = 0.12 * active * (1 - dim * 0.6);
    }

    ambientUniforms.uOpacity.value = 0.5 * (1 - quiet * 0.55);
    ambient.rotation.y = elapsed * 0.008;

    for (const pulse of pulses) {
      const travel = (elapsed * pulse.speed + pulse.offset) % 1;
      let distance = travel * routeLength;
      let position: InstanceType<Three['Vector3']> | null = null;
      for (const segment of routeSegments) {
        if (distance <= segment.length) {
          const ratio = segment.length > 0 ? distance / segment.length : 0;
          position = scratchPosition.copy(segment.start).lerp(segment.end, ratio);
          break;
        }
        distance -= segment.length;
      }
      if (position) {
        pulse.sprite.position.copy(position);
        pulse.sprite.position.y += 0.02;
      }
      pulse.material.opacity = 0.86 * (1 - failure * 0.92);
      const pulseScale = 0.32 + Math.sin(elapsed * 3 + pulse.offset * 8) * 0.04;
      pulse.sprite.scale.setScalar(pulseScale);
    }

    renderer.render(scene, camera);
  };

  const start = () => {
    if (running) return;
    running = true;
    lastTime = performance.now();
    frameId = window.requestAnimationFrame(renderFrame);
  };

  const stop = () => {
    running = false;
    window.cancelAnimationFrame(frameId);
  };

  const syncVisibility = () => {
    if (stageVisible && documentVisible) start();
    else stop();
  };

  const onVisibility = () => {
    documentVisible = !document.hidden;
    syncVisibility();
  };

  const resizeObserver = new ResizeObserver(resize);
  resizeObserver.observe(host);
  const intersectionObserver = new IntersectionObserver(
    ([entry]) => {
      stageVisible = Boolean(entry?.isIntersecting);
      syncVisibility();
    },
    { rootMargin: '120px' },
  );
  intersectionObserver.observe(host);

  window.addEventListener('pointermove', onPointerMove, { passive: true });
  window.addEventListener('resize', resize, { passive: true });
  document.addEventListener('visibilitychange', onVisibility);

  resize();
  onReady();
  syncVisibility();

  return () => {
    stop();
    resizeObserver.disconnect();
    intersectionObserver.disconnect();
    window.removeEventListener('pointermove', onPointerMove);
    window.removeEventListener('resize', resize);
    document.removeEventListener('visibilitychange', onVisibility);
    for (const geometry of geometries) geometry.dispose();
    for (const material of materials) material.dispose();
    for (const texture of textures) texture.dispose();
    renderer.dispose();
    renderer.forceContextLoss();
    renderer.domElement.remove();
  };
}

function makeGlowTexture(THREE: Three) {
  const size = 256;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const context = canvas.getContext('2d');
  if (!context) {
    return new THREE.Texture();
  }
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

function makeLabelTexture(THREE: Three, text: string, role: NodeSpec['role']) {
  const canvas = document.createElement('canvas');
  canvas.width = 768;
  canvas.height = 96;
  const context = canvas.getContext('2d');
  if (!context) return new THREE.Texture();
  context.clearRect(0, 0, canvas.width, canvas.height);
  context.font = '600 38px "Instrument Sans", Inter, sans-serif';
  context.textAlign = 'center';
  context.textBaseline = 'middle';
  context.fillStyle = role === 'sender' ? '#ffb094' : role === 'recipient' ? '#9df2d1' : '#c4d3cf';
  context.fillText(text, canvas.width / 2, canvas.height / 2);
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.needsUpdate = true;
  return texture;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function smoothstep(edge0: number, edge1: number, value: number): number {
  const ratio = clamp((value - edge0) / Math.max(0.0001, edge1 - edge0), 0, 1);
  return ratio * ratio * (3 - 2 * ratio);
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
