window.addEventListener("resize", () => {
  if (isFitMode) {
    recalculateFitZoom();
  }
});

window.addEventListener("keydown", (e) => {
  if (e.target && (e.target.tagName === "INPUT" || e.target.tagName === "SELECT")) return;
  if (e.key === "Escape" || e.key === "i" || e.key === "I") {
    setInteractionMode("INSPECT");
  } else if (e.key === "p" || e.key === "P") {
    setInteractionMode("PAINT");
  } else if (e.key === "+" || e.key === "=") {
    zoomGrid(1);
  } else if (e.key === "-" || e.key === "_") {
    zoomGrid(-1);
  } else if (e.key === "0") {
    resetZoom();
  } else if (e.key === "f" || e.key === "F") {
    fitGridToScreen();
  } else if (e.key === "1") {
    setGridDisplayMode("COMPONENTS");
  } else if (e.key === "2") {
    setGridDisplayMode("TEMP");
  } else if (e.key === "3") {
    setGridDisplayMode("HEAT_OUTPUT");
  } else if (e.key === "4") {
    setGridDisplayMode("TOTAL_FLUX");
  } else if (e.key === "5") {
    setGridDisplayMode("ABSORPTION");
  } else if (e.key === "6") {
    setGridDisplayMode("CONTROL_RODS");
  } else if (e.key === "s" || e.key === "S") {
    quickScram();
  } else if (e.key === "m" || e.key === "M") {
    const modes = ["COMPONENTS", "TEMP", "HEAT_OUTPUT", "TOTAL_FLUX", "ABSORPTION", "CONTROL_RODS"];
    const nextIdx = (modes.indexOf(gridDisplayMode) + 1) % modes.length;
    setGridDisplayMode(modes[nextIdx]);
  }
});

initTurbineSelects();
initSecondaryCoolantSelect();
initPalette();
setInteractionMode("INSPECT");

const urlParams = new URLSearchParams(window.location.search);
const overlayParam = urlParams.get("overlay") || urlParams.get("mode");
if (overlayParam) {
  setGridDisplayMode(overlayParam.toUpperCase());
}
const selX = urlParams.get("x");
const selY = urlParams.get("y");
if (selX !== null && selY !== null) {
  selectedTilePos = { x: parseInt(selX), y: parseInt(selY) };
}

/*__SECONDARY_COOLANTS__*/
initSecondaryCoolantSelect();

/*__TURBINE_MATERIALS__*/
initTurbineSelects();

/*__CEILING_PRESETS__*/
initCeilingPresets();

/*__INITIAL_STATE__*/
if (window.__INITIAL_STATE__) {
  currentState = window.__INITIAL_STATE__;
  renderUI();
}

if (window.innerWidth <= 800) {
  isFitMode = true;
}

initEngine().then((wasmLoaded) => {
  loadColorMaps();
  const urlLayout = urlParams.get("layout");
  const urlTier = urlParams.get("tier");
  const urlTurbine = urlParams.get("turbine");
  if (urlLayout && isWasmMode && wasmSim) {
    const rows = urlLayout.trim().split(";");
    const h = rows.length;
    const w = rows[0].split(",").length;
    let pipeTier = 0;
    if (urlTier) {
      const tUp = urlTier.toUpperCase();
      if (tUp === "EV") pipeTier = 0;
      else if (tUp === "IV") pipeTier = 1;
      else if (tUp === "LUV") pipeTier = 2;
      else if (tUp === "ZPM") pipeTier = 3;
      else if (tUp === "UV") pipeTier = 4;
      else if (tUp === "UHV") pipeTier = 5;
      if (typeof setVoltageTier === "function") setVoltageTier(tUp);
    }
    if (w > 0 && h > 0) {
      wasmSim.initGrid(w, h, pipeTier);
    }
    if (typeof applyTierCoolingDefaults === "function") {
      applyTierCoolingDefaults(pipeTier, { tier: urlTier || "EV" });
    }
    if (wasmSim.loadLayout) {
      wasmSim.loadLayout(urlLayout, false);
    }
    if (urlTurbine && wasmSim.setTurbine) {
      const parts = urlTurbine.split(",");
      if (parts.length >= 3) {
        const mat = parts[0].toUpperCase().replace(/-/g, "_");
        const sz = parts[1].toUpperCase();
        const fit = parts[2].toUpperCase();
        wasmSim.setTurbine(mat, sz, fit, false);
      }
    }
    currentState = wasmSim.getState();
    renderUI();
  }
  if (isFitMode) recalculateFitZoom();
});
