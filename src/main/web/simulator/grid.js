let currentZoom = 1.0;
let isFitMode = false;
let lastGridWidth = 0;

const ZOOM_LEVELS = [0.35, 0.45, 0.55, 0.70, 0.85, 1.0, 1.15, 1.30, 1.50];

function zoomGrid(direction) {
  isFitMode = false;
  if (direction > 0) {
    const next = ZOOM_LEVELS.find(lvl => lvl > currentZoom + 0.03);
    applyZoom(next !== undefined ? next : Math.min(2.0, currentZoom + 0.15));
  } else {
    const reversed = [...ZOOM_LEVELS].reverse();
    const prev = reversed.find(lvl => lvl < currentZoom - 0.03);
    applyZoom(prev !== undefined ? prev : Math.max(0.35, currentZoom - 0.15));
  }
}

function resetZoom() {
  isFitMode = false;
  applyZoom(1.0);
}

function fitGridToScreen() {
  isFitMode = true;
  recalculateFitZoom();
}

function recalculateFitZoom() {
  const gridElem = document.getElementById("reactor-grid");
  const wrapper = gridElem ? gridElem.parentElement : null;
  if (!gridElem || !wrapper || !currentState) return;

  const availableWidth = wrapper.clientWidth - 24;
  const width = currentState.width;
  if (width <= 0 || availableWidth <= 60) return;

  const ratio = width + (width - 1) * (2.0 / 56.0);
  const targetCellSize = Math.max(16, (availableWidth - 12) / ratio);
  const targetScale = Math.min(1.0, targetCellSize / 56.0);
  applyZoom(targetScale);
}

function applyZoom(scale) {
  currentZoom = Math.min(2.0, Math.max(0.3, scale));
  const baseSize = 56;
  const cellSize = Math.max(16, Math.round(baseSize * currentZoom));
  const gap = currentZoom < 0.5 ? 1 : 2;
  const codeSize = Math.max(8, Math.round(13 * currentZoom)) + "px";
  const tempSize = Math.max(7, Math.round(10.5 * currentZoom)) + "px";
  const hideTemp = cellSize < 32;

  const grid = document.getElementById("reactor-grid");
  if (grid) {
    grid.style.setProperty("--cell-size", cellSize + "px");
    grid.style.setProperty("--cell-gap", gap + "px");
    grid.style.setProperty("--cell-code-size", codeSize);
    grid.style.setProperty("--cell-temp-size", tempSize);
    if (currentState) {
      grid.style.gridTemplateColumns = `repeat(${currentState.width}, ${cellSize}px)`;
    }
  }

  const zoomBtn = document.getElementById("zoom-level-btn");
  if (zoomBtn) {
    zoomBtn.textContent = Math.round(currentZoom * 100) + "%";
  }

  const fitBtn = document.querySelector(".zoom-btn.fit-btn");
  if (fitBtn) {
    if (isFitMode) {
      fitBtn.classList.add("active");
    } else {
      fitBtn.classList.remove("active");
    }
  }

  const temps = document.querySelectorAll(".cell .cell-temp, .cell .cell-metric");
  for (let i = 0; i < temps.length; i++) {
    temps[i].style.display = hideTemp ? "none" : "";
  }
}
function updateGridDOM() {
  const gridElem = document.getElementById("reactor-grid");
  const width = currentState.width;
  const height = currentState.height;
  const totalCells = width * height;

  const dimLabel = document.getElementById("grid-dim-label");
  if (dimLabel) {
    dimLabel.textContent = `Chamber Grid (${width}×${height})`;
  }

  if (lastGridWidth !== width) {
    lastGridWidth = width;
    if (isFitMode) {
      recalculateFitZoom();
    } else {
      applyZoom(currentZoom);
    }
  }

  const cellSize = Math.max(16, Math.round(56 * currentZoom));
  const hideTemp = cellSize < 32;

  if (gridElem.children.length !== totalCells) {
    gridElem.innerHTML = "";
    gridElem.style.gridTemplateColumns = `repeat(${width}, ${cellSize}px)`;
    for (let y = 0; y < height; y++) {
      for (let x = 0; x < width; x++) {
        const cell = document.createElement("div");
        cell.className = "cell";
        cell.dataset.x = x;
        cell.dataset.y = y;
        cell.innerHTML = `
          <img class="cell-icon" style="display:none;" />
          <span class="cell-code" style="display:none;"></span>
          <div class="cell-overlay"></div>
          <span class="cell-metric"${hideTemp ? ' style="display:none;"' : ''}></span>
          <div class="cell-durability" style="display:none;"><div class="cell-durability-fill"></div></div>
          <span class="loop-badge">⟳</span>
          <span class="rod-badge">🕹️</span>
        `;
        let lastTrigger = 0;
        const triggerAction = (e) => {
          const now = performance.now();
          if (now - lastTrigger < 50) return;
          lastTrigger = now;
          const cx = parseInt(cell.dataset.x);
          const cy = parseInt(cell.dataset.y);
          handleCellClick(cx, cy, e);
        };
        cell.addEventListener("pointerdown", (e) => {
          if (e.pointerType === "mouse" && e.button !== 0) return;
          triggerAction(e);
        });
        cell.addEventListener("pointerenter", (e) => {
          if (interactionMode === "PAINT" && e.buttons === 1) {
            triggerAction(e);
          }
        });
        cell.addEventListener("click", (e) => {
          triggerAction(e);
        });
        gridElem.appendChild(cell);
      }
    }
  }

  currentState.tiles.forEach((t, i) => {
    const cell = gridElem.children[i];
    if (!cell) return;
    cell.dataset.x = t.x;
    cell.dataset.y = t.y;

    const isSelected = selectedTilePos && selectedTilePos.x === t.x && selectedTilePos.y === t.y;
    const imgEl = cell.children[0];
    const codeEl = cell.children[1];
    const overlayEl = cell.children[2];
    const metricEl = cell.children[3];
    const durEl = cell.children[4];
    const durFillEl = durEl ? durEl.children[0] : null;

    if (t.code === "NL") {
      cell.className = "cell wall-cell" + (isSelected ? " selected" : "");
      if (imgEl) imgEl.style.display = "none";
      if (codeEl) codeEl.style.display = "none";
      if (overlayEl) overlayEl.style.backgroundColor = "transparent";
      if (metricEl) metricEl.style.display = "none";
      if (durEl) durEl.style.display = "none";
    } else {
      const isAttached = (currentState.coolantLoop && currentState.coolantLoop.attachedPoints && currentState.coolantLoop.attachedPoints.includes(t.x + "," + t.y)) || t.type === "PASSAGE_CORE";
      let rodClass = "";
      if (t.hasControlRod) {
        rodClass = " has-rod";
        if (gridDisplayMode === "CONTROL_RODS") {
          rodClass += (t.controlRodType && t.controlRodType !== "NONE") ? " rod-mode-active" : " rod-mode-empty";
        }
      } else if (gridDisplayMode === "CONTROL_RODS") {
        rodClass = " rod-mode-dimmed";
      }
      const desiredClass = "cell" + (isSelected ? " selected" : "") + (isAttached ? " loop-attached" : "") + rodClass;
      if (cell.className !== desiredClass) {
        cell.className = desiredClass;
      }

      let overlayBg = "transparent";
      let metricStr = "";
      let tipText = "";

      const tpc = currentState.ticksPerCycle || 5;
      const cps = 20 / tpc;
      if (gridDisplayMode === "COMPONENTS") {
        overlayBg = "transparent";
        metricStr = "";
        tipText = `${t.name} (${t.x}, ${t.y})` + (t.hasControlRod ? " [Control Rod Hatch]" : "");
      } else if (gridDisplayMode === "TOTAL_FLUX") {
        const fl = t.totalFlux || 0;
        const flSec = Math.round(fl * cps);
        overlayBg = fl > 0 ? getNeutronColor(flSec) : "transparent";
        metricStr = formatFluxValue(flSec);
        tipText = `Total flux: ${flSec.toLocaleString()} n/s (${fl.toLocaleString()} /cycle) (Fast: ${Math.round((t.fastFlux||0)*cps).toLocaleString()} n/s, Thermal: ${Math.round((t.thermalFlux||0)*cps).toLocaleString()} n/s)`;
      } else if (gridDisplayMode === "FAST_FLUX") {
        const fl = t.fastFlux || 0;
        const flSec = Math.round(fl * cps);
        overlayBg = fl > 0 ? getNeutronColor(flSec) : "transparent";
        metricStr = formatFluxValue(flSec);
        tipText = `Fast flux: ${flSec.toLocaleString()} n/s (${fl.toLocaleString()} /cycle)`;
      } else if (gridDisplayMode === "THERMAL_FLUX") {
        const fl = t.thermalFlux || 0;
        const flSec = Math.round(fl * cps);
        overlayBg = fl > 0 ? getNeutronColor(flSec) : "transparent";
        metricStr = formatFluxValue(flSec);
        tipText = `Thermal flux: ${flSec.toLocaleString()} n/s (${fl.toLocaleString()} /cycle)`;
      } else if (gridDisplayMode === "ABSORPTION") {
        const abs = (t.fastAbsorbed || 0) + (t.thermalAbsorbed || 0);
        const rodAbs = (t.controlRodFastAbsorbed || 0) + (t.controlRodThermalAbsorbed || 0);
        const absSec = Math.round(abs * cps);
        overlayBg = abs > 0 ? getAbsorptionColor(absSec) : "transparent";
        metricStr = abs > 0 ? formatFluxValue(absSec) : "";
        tipText = `Absorbed: ${absSec.toLocaleString()} n/s (${abs.toLocaleString()} /cycle) (Base: ${Math.round((abs - rodAbs)*cps).toLocaleString()} n/s, Bottom rod: ${Math.round(rodAbs*cps).toLocaleString()} n/s)`;
      } else if (gridDisplayMode === "CONTROL_RODS") {
        if (t.hasControlRod) {
          if (t.controlRodType && t.controlRodType !== "NONE") {
            const ins = Math.round(t.controlRodInsertion || 0);
            const rodCol = getControlRodColor(t.controlRodType);
            overlayBg = ins > 0 ? `linear-gradient(to top, ${rodCol} ${ins}%, rgba(15, 23, 42, 0.75) ${ins}%)` : "rgba(30, 41, 59, 0.6)";
            metricStr = ins + "%";
            tipText = `Control rod: ${t.controlRodTypeName || t.controlRodType} (${ins}% inserted)`;
          } else {
            overlayBg = "rgba(30, 41, 59, 0.5)";
            metricStr = "EMPTY";
            tipText = "Control rod hatch: empty (no rod inserted)";
          }
        } else {
          overlayBg = "rgba(15, 20, 28, 0.85)";
          metricStr = "";
          tipText = "No control rod hatch installed";
        }
      } else if (gridDisplayMode === "HEAT_OUTPUT") {
        const ho = t.heatOutput || 0;
        const hoSec = ho * cps;
        overlayBg = ho > 0 ? getHeatOutputColor(hoSec) : "transparent";
        metricStr = hoSec > 0 ? (hoSec >= 10000 ? (hoSec / 1000).toFixed(1) + "k" : Math.round(hoSec).toString()) : "";
        tipText = `Heat output: ${hoSec.toLocaleString(undefined, {minimumFractionDigits: 1, maximumFractionDigits: 1})} Hu/s (${ho.toLocaleString(undefined, {minimumFractionDigits: 1, maximumFractionDigits: 1})} Hu/cycle)`;
      } else {
        overlayBg = getTemperatureColor(t.temp, currentState.maxSafeTemp);
        metricStr = Math.round(t.temp) + "°C";
        tipText = `Temperature: ${t.temp.toFixed(1)}°C (max safe: ${currentState.maxSafeTemp}°C)`;
      }

      if (gridDisplayMode !== "COMPONENTS" && gridDisplayMode !== "CONTROL_RODS" && t.temp > (currentState.maxSafeTemp || 3250) * 0.85) {
        if (Math.floor(Date.now() / 400) % 2 === 0) {
          overlayBg = "rgba(255, 0, 0, 0.45)";
        }
      }

      if (overlayEl && overlayEl.style.backgroundColor !== overlayBg) {
        overlayEl.style.backgroundColor = overlayBg;
      }

      if (t.type && t.type !== "EMPTY") {
        const iconSrc = getIconUrl(t.type);
        if (imgEl.getAttribute("src") !== iconSrc) {
          imgEl.src = iconSrc;
          imgEl.onerror = () => {
            imgEl.src = '/api/icon/' + t.type;
            imgEl.onerror = () => {
              imgEl.style.display = "none";
              if (codeEl) {
                codeEl.textContent = t.code;
                codeEl.style.display = "block";
              }
            };
          };
        }
        const isHeatmap = gridDisplayMode !== "COMPONENTS" && gridDisplayMode !== "CONTROL_RODS";
        imgEl.style.filter = isHeatmap ? "grayscale(100%)" : "none";
        if (imgEl.style.display !== "block") {
          imgEl.style.display = "block";
        }
        if (codeEl.style.display !== "none") {
          codeEl.style.display = "none";
        }
      } else {
        if (imgEl.style.display !== "none") {
          imgEl.style.display = "none";
        }
        if (codeEl.style.display !== "none") {
          codeEl.style.display = "none";
        }
      }

      if (metricEl.textContent !== metricStr) {
        metricEl.textContent = metricStr;
      }
      if (!hideTemp && metricEl.style.display === "none") {
        metricEl.style.display = "block";
      } else if (hideTemp && metricEl.style.display !== "none") {
        metricEl.style.display = "none";
      }

      cell.title = `${t.name} (${t.x}, ${t.y})
${tipText}`;

      if (t.isFuel && t.durabilityPct < 99.9) {
        if (durEl) {
          durEl.style.display = "block";
          const pct = Math.max(0, Math.min(100, t.durabilityPct));
          durFillEl.style.width = pct + "%";
          durFillEl.style.backgroundColor = pct > 50 ? "#22c55e" : (pct > 20 ? "#eab308" : "#ef4444");
        }
      } else if (durEl) {
        durEl.style.display = "none";
      }
    }
  });
}

async function handleCellClick(x, y, e) {
  if (isLoopAttachMode) {
    toggleLoopAttachment(x, y);
    return;
  }
  const tile = currentState && currentState.tiles.find(t => t.x === x && t.y === y);
  if (!tile) return;
  if (tile.code === "NL") {
    selectCell(x, y);
    return;
  }
  if (interactionMode === "INSPECT" || (e && e.shiftKey)) {
    selectCell(x, y);
  } else {
    const palItem = PALETTE.find(p => p.type === activePaletteType);
    if (palItem && palItem.isControlRod) {
      if (palItem.remove) {
        await toggleTileControlRod(x, y, false);
      } else {
        await changeTileControlRodMaterial(x, y, palItem.material);
      }
    } else {
      await setTile(x, y, activePaletteType);
    }
    selectCell(x, y);
  }
}

function selectCell(x, y) {
  selectedTilePos = { x, y };
  const tile = currentState && currentState.tiles.find(t => t.x === x && t.y === y);
  if (tile) {
    renderInspector(tile);
  }
  const gridElem = document.getElementById("reactor-grid");
  if (gridElem) {
    for (let i = 0; i < gridElem.children.length; i++) {
      const c = gridElem.children[i];
      const isSel = parseInt(c.dataset.x) === x && parseInt(c.dataset.y) === y;
      if (c.classList.contains("selected") !== isSel) c.classList.toggle("selected", isSel);
    }
  }
}

