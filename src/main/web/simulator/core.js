let currentState = null;
let selectedTilePos = null;
let activePaletteType = "FUEL_URANIUM_QUAD";
let interactionMode = "INSPECT";
let isWasmMode = true;
let wasmSim = null;
let byproductUnit = "min";

function getIconUrl(type) {
  return `icons/${type}.png`;
}

// Fallback for a missing static icon: try the Java dev server's /api/icon once, then hide the image.
// Static deployments have no /api, so the second failure is expected there.
function iconFallback(img, type) {
  if (img.dataset.iconFallback) {
    img.onerror = null;
    img.style.visibility = "hidden";
    return;
  }
  img.dataset.iconFallback = "1";
  img.src = "/api/icon/" + type;
}
window.iconFallback = iconFallback;

async function initEngine() {
  if (typeof NuclearSimWasm !== "undefined") {
    try {
      wasmSim = new NuclearSimWasm();
      const wasmPath = "nuclear-sim.wasm";
      await wasmSim.init(wasmPath);
      isWasmMode = true;
      const eb = document.getElementById("engine-badge");
      if (eb) {
        eb.innerText = "⚡ Client WASM (0ms)";
        eb.style.background = "#065f46";
        eb.style.borderColor = "#059669";
        eb.style.color = "#34d399";
      }
      wasmSim.onStateChange = (state) => {
        currentState = state;
        renderUI();
      };
      if (window.__INITIAL_STATE__ && window.__INITIAL_STATE__.tiles) {
        wasmSim.initGrid(window.__INITIAL_STATE__.width, window.__INITIAL_STATE__.height, window.__INITIAL_STATE__.pipeTier);
        window.__INITIAL_STATE__.tiles.forEach(t => {
          if (t.type && t.type !== "EMPTY" && t.code !== "NL") {
            wasmSim.setTile(t.x, t.y, t.type, false);
          }
          if (t.hasControlRod) {
            wasmSim.setControlRod(t.x, t.y, true, t.controlRodType || "BORON", t.controlRodInsertion || 0, false);
          }
        });
        if (window.__INITIAL_STATE__.turbineMaterial) {
          wasmSim.setTurbine(window.__INITIAL_STATE__.turbineMaterial, window.__INITIAL_STATE__.turbineSize, window.__INITIAL_STATE__.turbineFitting, false);
        }
        if (window.__INITIAL_STATE__.coolantLoop) {
          const cl = window.__INITIAL_STATE__.coolantLoop;
          if (cl.coolingMode) wasmSim.setCoolingMode(cl.coolingMode, false);
          if (cl.material) wasmSim.setCoolantLoopMaterial(cl.material, false);
          if (cl.pipeSize) wasmSim.setCoolantLoopPipeSize(cl.pipeSize, false);
          if (cl.fluidType) wasmSim.setCoolantLoopFluid(cl.fluidType, false);
          if (cl.hatchTier) wasmSim.setCoolantLoopHatchTier(cl.hatchTier, false);
          if (cl.pumpHatchCount !== undefined) wasmSim.setCoolantLoopPumpHatches(cl.pumpHatchCount, false);
          if (cl.rotorMaterial || cl.rotorSize) wasmSim.setCoolantLoopRotor(cl.rotorMaterial, cl.rotorSize, false);
          if (cl.loopCount !== undefined) wasmSim.setCoolantLoopCount(cl.loopCount, false);
          if (cl.pheCount !== undefined) wasmSim.setCoolantLoopPhe(cl.pheCount, cl.pheHatchTier, cl.pheHatchCount !== undefined ? cl.pheHatchCount : 1, false);
        } else if (window.__INITIAL_STATE__.pipeTier >= 1) {
          applyTierCoolingDefaults(window.__INITIAL_STATE__.pipeTier);
        }
      } else {
        wasmSim.initGrid(9, 9, 1);
        wasmSim.loadPreset("BEST_PLATINUM_9X9");
        applyTierCoolingDefaults(1);
      }
      currentState = wasmSim.getState();
      renderUI();
      console.log("⚡ Modular Nuclear Simulator running via WebAssembly (WASM)!");
      return true;
    } catch (err) {
      console.error("WASM initialization error:", err);
      const eb = document.getElementById("engine-badge");
      if (eb) {
        eb.innerText = "❌ WASM Error";
        eb.style.background = "#7f1d1d";
        eb.style.borderColor = "#b91c1c";
        eb.style.color = "#f87171";
      }
      return false;
    }
  }
  return false;
}

const PALETTE = [
  { type: "EMPTY", name: "Empty Slot", code: ".", color: "#1a2230" },
  { type: "FUEL_GLOWSTONE", name: "Glowstone Rod", code: "G1", color: "#facc15" },
  { type: "FUEL_LITHIUM", name: "Lithium Rod", code: "LI1", color: "#a3e635" },
  { type: "FUEL_THORIUM_SINGLE", name: "Thorium Single Rod", code: "T1", color: "#c084fc" },
  { type: "FUEL_THORIUM_DUAL", name: "Thorium Dual Rod", code: "T2", color: "#a855f7" },
  { type: "FUEL_THORIUM_QUAD", name: "Thorium Quad Rod", code: "T4", color: "#9333ea" },
  { type: "FUEL_URANIUM_SINGLE", name: "Uranium Single Rod", code: "U1", color: "#4ade80" },
  { type: "FUEL_URANIUM_DUAL", name: "Uranium Dual Rod", code: "U2", color: "#22c55e" },
  { type: "FUEL_URANIUM_QUAD", name: "Uranium Quad Rod", code: "U4", color: "#16a34a" },
  { type: "FUEL_MOX_SINGLE", name: "MOX Single Rod", code: "M1", color: "#fde047" },
  { type: "FUEL_MOX_DUAL", name: "MOX Dual Rod", code: "M2", color: "#eab308" },
  { type: "FUEL_MOX_QUAD", name: "MOX Quad Rod", code: "M4", color: "#ca8a04" },
  { type: "FUEL_HD_URANIUM_SINGLE", name: "HD Uranium Single Rod", code: "HDU1", color: "#4ade80" },
  { type: "FUEL_HD_URANIUM_DUAL", name: "HD Uranium Dual Rod", code: "HDU2", color: "#22c55e" },
  { type: "FUEL_HD_URANIUM_QUAD", name: "HD Uranium Quad Rod", code: "HDU4", color: "#16a34a" },
  { type: "FUEL_HD_PLUTONIUM_SINGLE", name: "HD Plutonium Single Rod", code: "HDP1", color: "#fb923c" },
  { type: "FUEL_HD_PLUTONIUM_DUAL", name: "HD Plutonium Dual Rod", code: "HDP2", color: "#ea580c" },
  { type: "FUEL_HD_PLUTONIUM_QUAD", name: "HD Plutonium Quad Rod", code: "HDP4", color: "#c2410c" },
  { type: "FUEL_EXCITED_URANIUM_SINGLE", name: "Excited Uranium Single Rod", code: "EXU1", color: "#34d399" },
  { type: "FUEL_EXCITED_URANIUM_DUAL", name: "Excited Uranium Dual Rod", code: "EXU2", color: "#10b981" },
  { type: "FUEL_EXCITED_URANIUM_QUAD", name: "Excited Uranium Quad Rod", code: "EXU4", color: "#059669" },
  { type: "FUEL_EXCITED_PLUTONIUM_SINGLE", name: "Excited Plutonium Single Rod", code: "EXP1", color: "#fb7185" },
  { type: "FUEL_EXCITED_PLUTONIUM_DUAL", name: "Excited Plutonium Dual Rod", code: "EXP2", color: "#f43f5e" },
  { type: "FUEL_EXCITED_PLUTONIUM_QUAD", name: "Excited Plutonium Quad Rod", code: "EXP4", color: "#e11d48" },
  { type: "FUEL_NAQUADAH_SINGLE", name: "Naquadah Single Rod", code: "NQ1", color: "#f472b6" },
  { type: "FUEL_NAQUADAH_DUAL", name: "Naquadah Dual Rod", code: "NQ2", color: "#ec4899" },
  { type: "FUEL_NAQUADAH_QUAD", name: "Naquadah Quad Rod", code: "NQ4", color: "#db2777" },
  { type: "FUEL_NAQUADRIA_SINGLE", name: "Naquadria Single Rod", code: "NQR1", color: "#e879f9" },
  { type: "FUEL_NAQUADRIA_DUAL", name: "Naquadria Dual Rod", code: "NQR2", color: "#c026d3" },
  { type: "FUEL_NAQUADRIA_QUAD", name: "Naquadria Quad Rod", code: "NQR4", color: "#a21caf" },
  { type: "FUEL_TIBERIUM_SINGLE", name: "Tiberium Single Rod", code: "TIB1", color: "#2dd4bf" },
  { type: "FUEL_TIBERIUM_DUAL", name: "Tiberium Dual Rod", code: "TIB2", color: "#14b8a6" },
  { type: "FUEL_TIBERIUM_QUAD", name: "Tiberium Quad Rod", code: "TIB4", color: "#0f766e" },
  { type: "FUEL_CORE", name: "The Core (NQ32)", code: "NQ32", color: "#dc2626" },
  { type: "HATCH_DISTILLED_WATER", name: "Distilled Water Hatch", code: "HD", color: "#38bdf8" },
  { type: "HATCH_HEAVY_WATER", name: "Heavy Water Hatch", code: "HW", color: "#6366f1" },
  { type: "HATCH_IC2_COOLANT", name: "IC2 Coolant Hatch", code: "HC", color: "#14b8a6" },
  { type: "PASSAGE_CORE", name: "Coolant Loop Passage", code: "CP", color: "#00d2ff" },
  { type: "CP2", name: "Coolant Loop Passage (circuit 2)", code: "CP2", color: "#f97316" },
  { type: "CP3", name: "Coolant Loop Passage (circuit 3)", code: "CP3", color: "#a855f7" },
  { type: "REFLECTOR_GOLD", name: "Gold Plate Reflector", code: "RG", color: "#fbbf24" },
  { type: "REFLECTOR_STAINLESS", name: "Stainless Steel Plate Reflector", code: "RS", color: "#94a3b8" },
  { type: "REFLECTOR_TUNGSTEN_CARBIDE", name: "Tungsten Carbide Plate Reflector", code: "RT", color: "#475569" },
  { type: "REFLECTOR_IC2", name: "Neutron Reflector", code: "RN", color: "#60a5fa" },
  { type: "REFLECTOR_IC2_THICK", name: "Thick Neutron Reflector", code: "RTN", color: "#3b82f6" },
  { type: "INSULATOR_NAQUARITE_FOIL", name: "Naquarite Insulator Foil", code: "INQ", color: "#c084fc" },
  { type: "MODERATOR_GRAPHITE", name: "Graphite Moderator", code: "MG", color: "#64748b" },
  { type: "MODERATOR_CARBON", name: "Carbon Moderator", code: "MC", color: "#334155" },
  { type: "MODERATOR_BERYLLIUM", name: "Beryllium Moderator", code: "MB", color: "#cbd5e1" },
  { type: "COOLANT_CELL_60K", name: "60k Coolant Cell", code: "C6", color: "#06b6d4" },
  { type: "RADIOVOLTAIC_HV", name: "Radiovoltaic Cell (HV)", code: "RH", color: "#f59e0b" },
  { type: "RADIOVOLTAIC_EV", name: "Radiovoltaic Cell (EV)", code: "RV", color: "#f97316" },
  { type: "VENT_STANDARD", name: "Heat Vent", code: "V1", color: "#38bdf8" },
  { type: "VENT_ADVANCED", name: "Advanced Heat Vent", code: "VA", color: "#0ea5e9" },
  { type: "VENT_OVERCLOCKED", name: "Overclocked Heat Vent", code: "VO", color: "#eab308" },
  { type: "VENT_COMPONENT", name: "Component Heat Vent", code: "VC", color: "#06b6d4" },
  { type: "EXCHANGER_STANDARD", name: "Heat Exchanger", code: "X1", color: "#a855f7" },
  { type: "EXCHANGER_ADVANCED", name: "Advanced Heat Exchanger", code: "XA", color: "#9333ea" },
  { type: "EXCHANGER_COMPONENT", name: "Component Heat Exchanger", code: "XC", color: "#c084fc" },
  { type: "CR_BORON", name: "Control Rod: Boron", code: "CR:B", color: "#00e5ff", isControlRod: true, material: "BORON" },
  { type: "CR_HAFNIUM", name: "Control Rod: Hafnium", code: "CR:HF", color: "#f97316", isControlRod: true, material: "HAFNIUM" },
  { type: "CR_CADMIUM", name: "Control Rod: Cadmium", code: "CR:CD", color: "#06b6d4", isControlRod: true, material: "CADMIUM" },
  { type: "CR_SILVER", name: "Control Rod: Silver", code: "CR:AG", color: "#94a3b8", isControlRod: true, material: "SILVER" },
  { type: "CR_INDIUM", name: "Control Rod: Indium", code: "CR:IN", color: "#a855f7", isControlRod: true, material: "INDIUM" },
  { type: "CR_EMPTY", name: "Control Rod: Empty Hatch", code: "CR:0", color: "#64748b", isControlRod: true, material: "NONE" },
  { type: "CR_REMOVE", name: "Remove Control Rod", code: "-CR", color: "#ef4444", isControlRod: true, remove: true }
];

const VOLTAGE_TIERS = {
  ULV: 8,
  LV: 32,
  MV: 128,
  HV: 512,
  EV: 2048,
  IV: 8192,
  LuV: 32768,
  ZPM: 131072,
  UV: 524288,
  UHV: 2097152,
  UEV: 8388608,
  UIV: 33554432,
  UMV: 134217728,
  UXV: 536870912,
  MAX: 2147483647
};

let selectedVoltageTier = "IV";

function onVoltageTierChange(val) {
  selectedVoltageTier = val;
  renderUI();
}

function setVoltageTier(val) {
  if (VOLTAGE_TIERS[val]) {
    selectedVoltageTier = val;
    const sel = document.getElementById("voltage-tier-select");
    if (sel && sel.value !== val) sel.value = val;
    renderUI();
  }
}

function formatPowerAndAmps(eut, compact = false) {
  if (eut === undefined || eut === null || isNaN(eut)) return "-";
  const voltage = VOLTAGE_TIERS[selectedVoltageTier] || 8192;
  const amps = eut / voltage;
  const euStr = Math.round(eut).toLocaleString() + " EU/t";
  const ampStr = (amps >= 100 ? amps.toFixed(1) : amps.toFixed(2)) + " A";
  if (compact) {
    return `${euStr} (${ampStr} @ ${selectedVoltageTier})`;
  }
  return `${euStr} · ${ampStr} @ ${selectedVoltageTier}`;
}

const DEFAULT_TURBINE_MATERIALS = [
  { id: "HSS_E", name: "HSS-E (175% / 116%)" },
  { id: "HSS_S", name: "HSS-S (185% / 123%)" },
  { id: "ELVEN_ELEMENTIUM", name: "Elven Elementium (175% / 116%)" },
  { id: "ORINARUKON", name: "Oriharukon (155% / 105%)" },
  { id: "SHADOW_METAL", name: "Shadow Metal (145% / 98%)" },
  { id: "ICHORIUM", name: "Ichorium (225% / 152%)" },
  { id: "DURANIUM", name: "Duranium (215% / 166%)" },
  { id: "GAIA_SPIRIT", name: "Gaia Spirit (195% / 154%)" },
  { id: "ADAMANTIUM", name: "Adamantium (180% / 158%)" },
  { id: "EXT_UNST_NAQUADAH", name: "Ext. Unst. Naquadah (190% / 166%)" },
  { id: "COSMIC_NEUTRONIUM", name: "Cosmic Neutronium (200% / 174%)" },
  { id: "INFINITY", name: "Infinity (212% / 212%)" },
  { id: "TUNGSTENSTEEL", name: "Tungstensteel (130% / 88%)" },
  { id: "TITANIUM", name: "Titanium (120% / 81%)" }
];

function initTurbineSelects() {
  const matSelect = document.getElementById("turbine-mat");
  if (!matSelect) return;
  const materials = (window.__TURBINE_MATERIALS__ && window.__TURBINE_MATERIALS__.length > 0)
    ? window.__TURBINE_MATERIALS__
    : DEFAULT_TURBINE_MATERIALS;
  const cur = matSelect.value;
  matSelect.innerHTML = "";
  materials.forEach(m => {
    const opt = document.createElement("option");
    opt.value = m.id;
    opt.innerText = m.name;
    matSelect.appendChild(opt);
  });
  if (cur) {
    const found = Array.from(matSelect.options).some(o => o.value === cur);
    if (found) matSelect.value = cur;
  }
}

function initSecondaryCoolantSelect() {
  const secSelect = document.getElementById("loop-secondary-select");
  if (!secSelect) return;
  if (!window.__SECONDARY_COOLANTS__ || !Array.isArray(window.__SECONDARY_COOLANTS__) || window.__SECONDARY_COOLANTS__.length === 0) {
    return;
  }
  const cur = secSelect.value;
  secSelect.innerHTML = "";
  window.__SECONDARY_COOLANTS__.forEach(c => {
    const opt = document.createElement("option");
    opt.value = c.id;
    opt.innerText = c.name;
    secSelect.appendChild(opt);
  });
  if (cur) {
    const found = Array.from(secSelect.options).some(o => o.value === cur);
    if (found) secSelect.value = cur;
  }
}

async function onTurbineConfigChange() {
  const mat = document.getElementById("turbine-mat").value;
  const size = document.getElementById("turbine-size").value;
  const fitting = document.getElementById("turbine-fitting").value;
  if (isWasmMode && wasmSim) {
    wasmSim.setTurbine(mat, size, fitting);
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  await fetch(`/api/set-turbine?material=${encodeURIComponent(mat)}&size=${encodeURIComponent(size)}&fitting=${encodeURIComponent(fitting)}`);
  await fetchState();
  schedulePoll();
}

async function applyScenarioPreset(mode) {
  if (!currentState || !currentState.scenarios) return;
  const cfg = (mode === 'TIGHT') ? currentState.scenarios.tight : currentState.scenarios.loose;
  if (!cfg) return;
  const matSelect = document.getElementById("turbine-mat");
  const opt = Array.from(matSelect.options).find(o => o.value.toLowerCase() === cfg.material.toLowerCase() || o.value.replace(/_/g,"").toLowerCase() === cfg.material.replace(/_/g,"").toLowerCase());
  if (opt) {
    matSelect.value = opt.value;
  } else {
    matSelect.value = cfg.material;
  }
  const sizeSelect = document.getElementById("turbine-size");
  sizeSelect.value = cfg.size.toUpperCase();
  const fitSelect = document.getElementById("turbine-fitting");
  fitSelect.value = cfg.mode.toUpperCase();
  await onTurbineConfigChange();
}

let isLoopAttachMode = false;

async function changeCoolingMode(mode) {
  if (isWasmMode && wasmSim) {
    wasmSim.setCoolingMode(mode);
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  await fetch(`/api/set-cooling-mode?mode=${encodeURIComponent(mode)}`);
  await fetchState();
  schedulePoll();
}

async function onCoolantLoopConfigChange() {
  const val = (id, dflt) => {
    const el = document.getElementById(id);
    return el && el.value !== "" ? el.value : dflt;
  };
  const mat = val("loop-material-select", "TITANIUM");
  const size = val("loop-size-select", "NORMAL");
  const fluid = val("loop-fluid-select", "DISTILLED_WATER");
  const secondary = val("loop-secondary-select", "ic2distilledwater");
  const hatch = val("loop-hatch-tier-select", "EV");
  const pumpHatches = parseInt(val("loop-pump-hatches-select", "1"), 10) || 1;
  const rotor = val("loop-rotor-select", "TUNGSTENSTEEL");
  const rotorSize = val("loop-rotor-size-select", "HUGE");
  const pheCount = Math.max(1, parseInt(val("loop-phe-count-input", "1"), 10) || 1);
  const loopCount = Math.max(1, parseInt(val("loop-count-input", "1"), 10) || 1);
  const duty = parseFloat(val("loop-duty-cycle-input", "100"));
  const maxFlow = parseFloat(val("loop-max-flow-input", "0")) || 0;
  const maxPress = parseFloat(val("loop-max-pressure-input", "0")) || 0;
  const bandCheck = document.getElementById("loop-band-check");
  const bandOn = bandCheck ? bandCheck.checked : false;
  const bandMin = parseFloat(val("loop-band-min-input", "500"));
  const bandMax = parseFloat(val("loop-band-max-input", "600"));
  const sensor = val("loop-band-sensor-select", "PASSAGE");
  const pheHatch = val("loop-phe-hatch-tier-select", hatch);
  const pheHatches = parseInt(val("loop-phe-hatches-select", "1"), 10) || 1;

  if (isWasmMode && wasmSim) {
    if (currentState && currentState.pipeTier >= 1) {
      wasmSim.setCoolingMode("CONVECTIVE_LOOP", false);
    }
    wasmSim.setCoolantLoopMaterial(mat, false);
    wasmSim.setCoolantLoopPipeSize(size, false);
    wasmSim.setCoolantLoopFluid(fluid, false);
    wasmSim.setCoolantLoopSecondaryCoolant(secondary, false);
    wasmSim.setCoolantLoopHatchTier(hatch, false);
    wasmSim.setCoolantLoopPumpHatches(pumpHatches, false);
    wasmSim.setCoolantLoopRotor(rotor, rotorSize, false);
    wasmSim.setCoolantLoopPhe(pheCount, pheHatch, pheHatches, false);
    wasmSim.setCoolantLoopCount(loopCount, false);
    wasmSim.setCoolantLoopControl(isNaN(duty) ? 100 : duty, maxFlow, maxPress, false);
    wasmSim.setCoolantLoopTempBand(bandOn, bandMin, bandMax, sensor, false);
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  const url = `/api/set-coolant-loop?material=${encodeURIComponent(mat)}&size=${encodeURIComponent(size)}&fluid=${encodeURIComponent(fluid)}&hatchTier=${encodeURIComponent(hatch)}&pumpHatches=${pumpHatches}&rotor=${encodeURIComponent(rotor)}&rotorSize=${encodeURIComponent(rotorSize)}&pheCount=${pheCount}&pheHatchTier=${encodeURIComponent(pheHatch)}&pheHatches=${pheHatches}`;
  await fetch(url);
  await fetchState();
  schedulePoll();
}

function onCoolantCircuitSelect() {
  const sel = document.getElementById("loop-circuit-select");
  if (!wasmSim || !sel) return;
  wasmSim.selectCoolantCircuit(parseInt(sel.value, 10) || 0, false);
  currentState = wasmSim.getState();
  renderUI();
}

function addCoolantCircuit() {
  if (!wasmSim || !currentState || !currentState.coolantLoop) return;
  const n = (currentState.coolantLoop.circuits || []).length;
  wasmSim.selectCoolantCircuit(n, false);
  currentState = wasmSim.getState();
  renderUI();
}

function removeCoolantCircuit() {
  if (!wasmSim) return;
  wasmSim.removeCoolantCircuit(false);
  currentState = wasmSim.getState();
  renderUI();
}

function toggleLoopAttachMode() {
  isLoopAttachMode = !isLoopAttachMode;
  const btn = document.getElementById("btn-loop-attach-mode");
  if (btn) {
    if (isLoopAttachMode) {
      btn.innerText = "🛑 Exit Attach Mode";
      btn.style.background = "#e11d48";
    } else {
      btn.innerText = "🔗 Attach Points Mode";
      btn.style.background = "#0284c7";
    }
  }
}

async function toggleLoopAttachment(x, y) {
  if (!currentState || !currentState.coolantLoop) return;
  const ptStr = x + "," + y;
  const isAttached = currentState.coolantLoop.attachedPoints && currentState.coolantLoop.attachedPoints.includes(ptStr);
  if (isWasmMode && wasmSim) {
    if (isAttached) {
      wasmSim.detachCoolantLoopPoint(x, y);
    } else {
      wasmSim.attachCoolantLoopPoint(x, y);
    }
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  const param = isAttached ? `detachX=${x}&detachY=${y}` : `attachX=${x}&attachY=${y}`;
  await fetch(`/api/set-coolant-loop?${param}`);
  await fetchState();
  schedulePoll();
}

async function clearLoopAttachments() {
  if (isWasmMode && wasmSim) {
    wasmSim.clearCoolantLoopPoints();
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  await fetch("/api/set-coolant-loop?clearPoints=true");
  await fetchState();
  schedulePoll();
}

function getVoltageTier(eut) {
  if (eut <= 0) return "OFF";
  if (eut <= 32) return "LV";
  if (eut <= 128) return "MV";
  if (eut <= 512) return "HV";
  if (eut <= 2048) return "EV";
  if (eut <= 8192) return "IV";
  if (eut <= 32768) return "LuV";
  if (eut <= 131072) return "ZPM";
  if (eut <= 524288) return "UV";
  if (eut <= 2097152) return "UHV";
  if (eut <= 8388608) return "UEV";
  if (eut <= 33554432) return "UIV";
  if (eut <= 134217728) return "UMV";
  return "UXV+";
}

function setInteractionMode(mode) {
  interactionMode = mode;
  if (isLoopAttachMode) {
    isLoopAttachMode = false;
    const loopBtn = document.getElementById("btn-loop-attach-mode");
    if (loopBtn) {
      loopBtn.innerText = "🔗 Attach Points Mode";
      loopBtn.style.background = "#0284c7";
    }
  }
  const btnInspect = document.getElementById("btn-mode-inspect");
  const btnPaint = document.getElementById("btn-mode-paint");
  const badge = document.getElementById("mode-badge");

  if (mode === "INSPECT") {
    btnInspect.className = "primary";
    btnPaint.className = "";
    badge.innerText = "INSPECT ONLY";
    badge.style.color = "var(--accent)";
    document.querySelectorAll(".palette-btn").forEach(b => b.classList.remove("active"));
  } else {
    btnInspect.className = "";
    btnPaint.className = "primary";
    badge.innerText = "PAINT MODE";
    badge.style.color = "var(--warning)";
    document.querySelectorAll(".palette-btn").forEach(b => {
      if (b.dataset.type === activePaletteType) {
        b.classList.add("active");
      } else {
        b.classList.remove("active");
      }
    });
  }
}

function initPalette() {
  const container = document.getElementById("palette-container");
  container.innerHTML = "";
  PALETTE.forEach(p => {
    const btn = document.createElement("button");
    btn.className = "palette-btn" + (interactionMode === "PAINT" && p.type === activePaletteType ? " active" : "");
    btn.dataset.type = p.type;
    btn.style.borderLeft = "4px solid " + p.color;
    const iconHtml = p.isControlRod
      ? `<div class="palette-icon" style="display:flex; align-items:center; justify-content:center; font-size:0.85rem; font-weight:bold; color:${p.color};">${p.remove ? '❌' : '🕹️'}</div>`
      : (p.type !== "EMPTY"
          ? `<img class="palette-icon" src="${getIconUrl(p.type)}" onerror="iconFallback(this, '${p.type}')" alt="${p.code}" />`
          : `<div class="palette-icon palette-empty-icon"></div>`);
    btn.innerHTML = `${iconHtml} <span>${p.name}</span>`;
    btn.onclick = (e) => {
      if (e) e.preventDefault();
      activePaletteType = p.type;
      setInteractionMode("PAINT");
    };
    container.appendChild(btn);
  });
}

let gridDisplayMode = "TEMP";
let colorMaps = null;

async function loadColorMaps() {
  if (isWasmMode && wasmSim) {
    try {
      colorMaps = wasmSim.getColorMaps();
      return;
    } catch (e) {
      console.error("Failed to load colormaps from WASM", e);
    }
  }
  if (location.protocol === "file:" || location.hostname.endsWith("github.io")) {
    return;
  }
  try {
    const res = await fetch("/api/colormaps");
    if (res.ok) {
      colorMaps = await res.json();
    }
  } catch (e) {
    console.error("Failed to load colormaps", e);
  }
}

function setGridDisplayMode(mode) {
  gridDisplayMode = mode;
  document.querySelectorAll(".display-mode-selector .seg-btn").forEach(btn => {
    btn.classList.remove("active");
  });
  const idMap = {
    "COMPONENTS": "btn-mode-comp",
    "TEMP": "btn-mode-temp",
    "HEAT_OUTPUT": "btn-mode-heat-output",
    "TOTAL_FLUX": "btn-mode-flux",
    "FAST_FLUX": "btn-mode-flux",
    "THERMAL_FLUX": "btn-mode-flux",
    "ABSORPTION": "btn-mode-absorb",
    "CONTROL_RODS": "btn-mode-rods"
  };
  const activeBtn = document.getElementById(idMap[mode]);
  if (activeBtn) activeBtn.classList.add("active");
  if (currentState) {
    updateGridDOM();
    if (selectedTilePos) {
      const idx = selectedTilePos.y * currentState.width + selectedTilePos.x;
      if (currentState.tiles && currentState.tiles[idx]) {
        renderInspector(currentState.tiles[idx]);
      }
    }
  }
}

function getControlRodColor(type) {
  switch (type) {
    case "SILVER": return "#94a3b8";
    case "BORON": return "#3b82f6";
    case "CADMIUM": return "#06b6d4";
    case "INDIUM": return "#a855f7";
    case "HAFNIUM": return "#f97316";
    default: return "#64748b";
  }
}

function getAbsorptionColor(rate) {
  if (rate <= 0) return "transparent";
  if (rate < 50) return "rgba(120, 53, 15, 0.4)";
  if (rate < 200) return "rgba(180, 83, 9, 0.5)";
  if (rate < 800) return "rgba(217, 119, 6, 0.6)";
  if (rate < 2500) return "rgba(245, 158, 11, 0.7)";
  return "rgba(251, 191, 36, 0.85)";
}

function getNeutronColor(rate) {
  if (!colorMaps || !colorMaps.neutron) {
    if (rate <= 0) return "transparent";
    if (rate < 100) return "rgba(68, 1, 84, 0.55)";
    if (rate < 500) return "rgba(59, 82, 139, 0.6)";
    if (rate < 2000) return "rgba(33, 145, 140, 0.65)";
    if (rate < 5000) return "rgba(94, 201, 98, 0.7)";
    return "rgba(253, 231, 36, 0.75)";
  }
  const maxRate = 8192.0;
  const r = Math.min(Math.max(0, rate), maxRate);
  const factor = (r <= 0) ? 0.0 : Math.log1p(10.0 * r) / Math.log1p(10.0 * maxRate);
  let u = Math.round(299.0 * Math.min(1.0, Math.max(0.0, factor)));
  if (u < 0) u = 0;
  if (u > 299) u = 299;
  return colorMaps.neutron[u];
}

function getTemperatureColor(temp, maxTemp) {
  if (!colorMaps || !colorMaps.temperature) {
    if (temp < 100) return "rgba(12, 7, 134, 0.55)";
    if (temp < 350) return "rgba(126, 3, 168, 0.6)";
    if (temp < 800) return "rgba(204, 71, 120, 0.65)";
    if (temp < 1500) return "rgba(248, 149, 64, 0.7)";
    return "rgba(240, 249, 33, 0.75)";
  }
  if (!maxTemp || maxTemp <= 0) maxTemp = 3250.0;
  let u = Math.round(299.0 * Math.min(1.0, Math.max(0.0, temp / maxTemp)));
  if (u < 0) u = 0;
  if (u > 299) u = 299;
  return colorMaps.temperature[u];
}

function getHeatOutputColor(heatRate) {
  if (!heatRate || heatRate <= 0) return "transparent";
  if (!colorMaps || !colorMaps.heatOutput) {
    if (heatRate < 500) return "rgba(60, 20, 5, 0.55)";
    if (heatRate < 2500) return "rgba(160, 45, 10, 0.6)";
    if (heatRate < 10000) return "rgba(230, 95, 15, 0.65)";
    if (heatRate < 40000) return "rgba(255, 185, 30, 0.7)";
    return "rgba(255, 245, 180, 0.75)";
  }
  const maxExpected = 100000.0;
  const clamped = Math.min(Math.max(0, heatRate), maxExpected);
  const factor = Math.log1p(20.0 * (clamped / maxExpected)) / Math.log1p(20.0);
  let u = Math.round(299.0 * Math.min(1.0, Math.max(0.0, factor)));
  if (u < 0) u = 0;
  if (u > 299) u = 299;
  return colorMaps.heatOutput[u];
}

function formatFluxValue(val) {
  if (!val || val <= 0) return "0";
  if (val >= 10000) return (val / 1000).toFixed(1) + "k";
  return val.toString();
}

let isFetching = false;
let pollTimer = null;

async function fetchState() {
  if (isWasmMode && wasmSim) {
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  if (location.protocol === "file:" || location.hostname.endsWith("github.io")) {
    return;
  }
  if (isFetching) return;
  isFetching = true;
  try {
    const res = await fetch("/api/state");
    if (res.ok) {
      currentState = await res.json();
      renderUI();
    }
  } catch (e) {
    console.error("Fetch error", e);
  } finally {
    isFetching = false;
  }
}

function schedulePoll() {
  clearTimeout(pollTimer);
  if (isWasmMode || location.protocol === "file:" || location.hostname.endsWith("github.io")) return;
  const delay = (currentState && currentState.isRunning) ? 250 : 1000;
  pollTimer = setTimeout(async () => {
    await fetchState();
    schedulePoll();
  }, delay);
}

async function quickRepair() {
  if (isWasmMode && wasmSim) {
    wasmSim.repair();
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  await fetch("/api/set-params?repair=true");
  await fetchState();
  schedulePoll();
}

async function clearIncidentLog() {
  if (isWasmMode && wasmSim) {
    wasmSim.clearIncidentLog();
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  if (currentState) {
    currentState.incidentLog = [];
    renderUI();
  }
}

