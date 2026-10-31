function renderInspector(t) {
  const el = document.getElementById("tile-inspector");
  let extraHtml = "";
  if (t.code === "NL") {
    extraHtml = `
      <div class="stat-row"><span>Structure:</span><span class="stat-val" style="color:var(--text-muted);">Casing Wall</span></div>
      <div class="stat-row"><span>Wall Reflection:</span><span class="stat-val" style="color:#ef4444;">None (0% escaped)</span></div>
    `;
  } else if (t.type === "PASSAGE_CORE") {
    extraHtml = `
      <div class="stat-row"><span>Component:</span><span class="stat-val" style="color:#00d2ff;">High-pressure core passage</span></div>
      <div class="stat-row"><span>Heat Transfer (k):</span><span class="stat-val" style="color:#38bdf8;">0.90 (high convection)</span></div>
      <div class="stat-row"><span>Moderation / Scatter:</span><span class="stat-val" style="color:#a3e635;">85% / 80%</span></div>
      <div class="stat-row"><span>Coupling:</span><span class="stat-val" style="color:var(--success);">Direct Convective Loop</span></div>
    `;
  } else if (t.isFuel) {
    const maxDur = (t.durabilityPct > 0) ? Math.round(t.durability / (t.durabilityPct / 100)) : t.durability;
    const burnRate = t.lastDurabilityLoss || 0;
    const durPerMin = burnRate * 1200;
    const minsLeft = (burnRate > 0 && t.durability > 0)
      ? (t.durability / durPerMin).toFixed(1) + " min"
      : (t.durability <= 0 ? "0 min" : "Infinity");
    const depBadge = t.isDepleted
      ? '<span class="badge" style="color:var(--danger); background:rgba(255,71,87,0.2);">⚠️ DEPLETED</span>'
      : '<span class="badge" style="color:var(--success);">ACTIVE</span>';
    extraHtml = `
      <div class="stat-row"><span>Durability:</span><span class="stat-val">${t.durability.toLocaleString()} / ${maxDur.toLocaleString()} (${t.durabilityPct}%)</span></div>
      <div class="stat-row"><span>Rod Status:</span><span class="stat-val">${depBadge}</span></div>
      <div class="stat-row"><span>Depleted Output:</span><span class="stat-val" style="color:#34d399; font-weight:600;"><span class="badge" style="font-size:0.68rem; padding:1px 4px; color:#34d399; border-color:#059669;">${t.depletedCode || "DEP"}</span> ${t.depletedName || "Depleted Fuel Rod"}</span></div>
      <div class="stat-row"><span>Burn Rate:</span><span class="stat-val" style="color:#fbbf24;">${burnRate.toFixed(2)} / tick (${Math.round(durPerMin).toLocaleString()} / min)</span></div>
      <div class="stat-row"><span>Est. Remaining Lifespan:</span><span class="stat-val" style="color:#38bdf8;">${minsLeft}</span></div>
    `;
  } else if (t.fluidName && t.fluidName.length > 0) {
    const cap = t.fluidCapacity || 2000;
    const pct = Math.round((t.fluidAmount / cap) * 100);
    const isCoolant = t.type === "HATCH_IC2_COOLANT";
    const ratio = isCoolant ? 1 : 160;
    const dryBadge = t.wasDry
      ? `<span class="badge" style="color:var(--danger); background:rgba(255,71,87,0.2);">${isCoolant ? "⚠️ DRAINED DRY" : "⚠️ BOILED DRY"}</span>`
      : '<span class="badge" style="color:var(--success);">NORMAL</span>';
    const steamFlow = t.lastProduced || 0;
    const coolantTurnover = steamFlow > 0 ? (steamFlow / ratio).toFixed(0) : 0;
    const cumProduced = isCoolant ? (t.outputFluidAmount || 0) : (t.steamAmount || 0);
    const isLiquidFuel = t.type && t.type.startsWith("HATCH_LIQUID_FUEL_");
    let liqFuelHtml = "";
    if (isLiquidFuel) {
      const burnRateL = t.lastLiquidBurned || 0;
      liqFuelHtml = `
        <div class="stat-row"><span>Depleted Output:</span><span class="stat-val" style="color:#38bdf8; font-weight:600;">${t.depletedName || "Depleted Fuel Fluid"}</span></div>
        <div class="stat-row"><span>Fuel Consumption:</span><span class="stat-val" style="color:#f59e0b;">${burnRateL} L/t (${(burnRateL * 1200).toLocaleString()} L/min)</span></div>
      `;
    }
    extraHtml = `
      <div class="stat-row"><span>Coolant:</span><span class="stat-val">${t.fluidName}</span></div>
      <div class="stat-row"><span>Coolant Volume:</span><span class="stat-val">${t.fluidAmount} / ${cap} L (${pct}%)</span></div>
      ${liqFuelHtml}
      <div class="stat-row"><span>Live Turnover:</span><span class="stat-val" style="color:#38bdf8;">${coolantTurnover} L/t coolant &rarr; ${steamFlow} L/t ${isCoolant ? "hot coolant" : "steam"}</span></div>
      <div class="stat-row"><span>Cumulative ${isCoolant ? "Hot Coolant" : "Steam"}:</span><span class="stat-val">${cumProduced.toLocaleString()} L</span></div>
      <div class="stat-row"><span>Hatch Status:</span><span class="stat-val">${dryBadge}</span></div>
    `;
  }
  const iconHtml = (t.type && t.type !== "EMPTY" && t.code !== "NL")
    ? `<img src="${getIconUrl(t.type)}" onerror="iconFallback(this, '${t.type}')" class="inspector-icon" alt="${t.code}" />`
    : "";

  let fluxHtml = "";
  const tpc = currentState.ticksPerCycle || 5;
  const cps = 20 / tpc;
  if (t.code !== "NL" && ((t.totalFlux && t.totalFlux > 0) || (t.fastFlux && t.fastFlux > 0) || (t.thermalFlux && t.thermalFlux > 0) || (t.fastAbsorbed && t.fastAbsorbed > 0) || (t.thermalAbsorbed && t.thermalAbsorbed > 0) || t.directEU)) {
    fluxHtml = `
      <div style="background:#10141d; border:1px solid #373737; border-radius:3px; padding:6px 8px; margin-top:8px;">
        <div style="font-weight:700; font-size:0.75rem; color:#94a3b8; text-transform:uppercase; margin-bottom:4px; letter-spacing:0.5px;">Neutron kinetics</div>
        <div class="stat-row" style="margin-bottom:2px;"><span>Total flux:</span><span class="stat-val" style="color:#38bdf8;">${Math.round((t.totalFlux || 0) * cps).toLocaleString()} n/s (${(t.totalFlux || 0).toLocaleString()} /cycle)</span></div>
        <div class="stat-row" style="margin-bottom:2px;"><span>Fast flux:</span><span class="stat-val" style="color:#fbbf24;">${Math.round((t.fastFlux || 0) * cps).toLocaleString()} n/s (${(t.fastFlux || 0).toLocaleString()} /cycle)</span></div>
        <div class="stat-row" style="margin-bottom:2px;"><span>Thermal flux:</span><span class="stat-val" style="color:#4ade80;">${Math.round((t.thermalFlux || 0) * cps).toLocaleString()} n/s (${(t.thermalFlux || 0).toLocaleString()} /cycle)</span></div>
        <div class="stat-row" style="margin-bottom:2px;"><span>Fast absorbed:</span><span class="stat-val" style="color:#cbd5e1;">${Math.round((t.fastAbsorbed || 0) * cps).toLocaleString()} n/s (${(t.fastAbsorbed || 0).toLocaleString()} /cycle)</span></div>
        <div class="stat-row" style="margin-bottom:2px;"><span>Thermal absorbed:</span><span class="stat-val" style="color:#cbd5e1;">${Math.round((t.thermalAbsorbed || 0) * cps).toLocaleString()} n/s (${(t.thermalAbsorbed || 0).toLocaleString()} /cycle)</span></div>
        ${t.directEU ? `<div class="stat-row" style="margin-top:4px; padding-top:4px; border-top:1px dashed #373737;"><span>Radiovoltaic output:</span><span class="stat-val" style="color:#38bdf8;">${t.directEU.toLocaleString()} EU/t</span></div>` : ''}
      </div>
    `;
  }

  let coolingMethodHtml = "";
  const isLoopAttached = (currentState.coolantLoop && currentState.coolantLoop.attachedPoints && currentState.coolantLoop.attachedPoints.includes(t.x + "," + t.y)) || t.type === "PASSAGE_CORE";
  if (isLoopAttached) {
    coolingMethodHtml = `<div class="stat-row"><span>Cell Cooling:</span><span class="stat-val" style="color:#00d2ff; font-weight:600;">🔄 Convective Loop (${t.type === "PASSAGE_CORE" ? "Passage Tile" : "Attached Cell"})</span></div>`;
  } else if (t.fluidName && t.fluidName.length > 0) {
    const methodLabel = t.fluidName.includes("coolant")
      ? `💧 Conductive Liquid Cooling (${t.fluidName})`
      : `💧 Conductive Fluid Boiling (${t.fluidName})`;
    coolingMethodHtml = `<div class="stat-row"><span>Cell Cooling:</span><span class="stat-val" style="color:#38bdf8; font-weight:600;">${methodLabel}</span></div>`;
  } else if (t.type && t.type.startsWith("VENT_")) {
    coolingMethodHtml = `<div class="stat-row"><span>Cell Cooling:</span><span class="stat-val" style="color:#a855f7; font-weight:600;">🌬️ IC2 Component Heat Vent</span></div>`;
  } else if (t.type && t.type.startsWith("EXCHANGER_")) {
    coolingMethodHtml = `<div class="stat-row"><span>Cell Cooling:</span><span class="stat-val" style="color:#a855f7; font-weight:600;">🔄 IC2 Heat Exchanger</span></div>`;
  } else if (t.type && t.type.startsWith("COOLANT_CELL_")) {
    coolingMethodHtml = `<div class="stat-row"><span>Cell Cooling:</span><span class="stat-val" style="color:#06b6d4; font-weight:600;">🧊 IC2 Coolant Cell Storage</span></div>`;
  } else if (t.isFuel) {
    coolingMethodHtml = `<div class="stat-row"><span>Cell Cooling:</span><span class="stat-val" style="color:#f43f5e; font-weight:600;">⚠️ Uncooled fuel (passive conduction)</span></div>`;
  } else if (t.code !== "NL" && t.type !== "EMPTY") {
    coolingMethodHtml = `<div class="stat-row"><span>Cell Cooling:</span><span class="stat-val" style="color:var(--text-muted);">None (passive conduction)</span></div>`;
  }

  let controlRodHtml = "";
  if (t.code !== "NL") {
    if (isLoopAttached) {
      controlRodHtml = `
        <div style="background:#10141d; border:1px solid #373737; border-radius:3px; padding:6px 8px; margin-top:8px;">
          <div style="font-weight:700; font-size:0.75rem; color:#64748b; text-transform:uppercase; letter-spacing:0.5px;">🕹️ Control Rod Hatch</div>
          <div style="font-size:0.72rem; color:var(--warning); margin-top:4px;">⚠️ Core face occupied by convective loop passage. Control rod hatches cannot be installed on loop cells.</div>
        </div>
      `;
    } else if (!t.hasControlRod) {
      controlRodHtml = `
        <div style="background:#10141d; border:1px solid #373737; border-radius:3px; padding:6px 8px; margin-top:8px;">
          <div style="display:flex; justify-content:space-between; align-items:center;">
            <span style="font-weight:700; font-size:0.75rem; color:#94a3b8; text-transform:uppercase; letter-spacing:0.5px;">🕹️ Control Rod Hatch</span>
            <button type="button" onclick="toggleTileControlRod(${t.x}, ${t.y}, true)" style="padding:2px 8px; font-size:0.72rem; background:#1e293b; border-color:#00d2ff; color:#00d2ff;">+ Install Hatch</button>
          </div>
          <div style="font-size:0.72rem; color:var(--text-muted); margin-top:4px;">No control rod hatch installed at this coordinate.</div>
        </div>
      `;
    } else {
      const ins = Math.round(t.controlRodInsertion || 0);
      const rodType = t.controlRodType || "NONE";
      const fastAbs = t.controlRodFastAbsorbed || 0;
      const thermAbs = t.controlRodThermalAbsorbed || 0;
      controlRodHtml = `
        <div style="background:#10141d; border:1px solid #00e5ff; border-radius:3px; padding:6px 8px; margin-top:8px;">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
            <span style="font-weight:700; font-size:0.75rem; color:#00e5ff; text-transform:uppercase; letter-spacing:0.5px;">🕹️ Control Rod Hatch</span>
            <button type="button" onclick="toggleTileControlRod(${t.x}, ${t.y}, false)" style="padding:1px 6px; font-size:0.68rem; background:#29151b; border-color:var(--danger); color:var(--danger);" title="Remove hatch from this cell">Remove</button>
          </div>
          <div style="margin-bottom:6px;">
            <div style="font-size:0.72rem; color:var(--text-muted); margin-bottom:2px;">Rod Material:</div>
            <select onchange="changeTileControlRodMaterial(${t.x}, ${t.y}, this.value)" style="width:100%; font-size:0.75rem; padding:3px 6px; height:auto; min-height:28px;">
              <option value="NONE" ${rodType === 'NONE' ? 'selected' : ''}>Empty (No rod inserted)</option>
              <option value="SILVER" ${rodType === 'SILVER' ? 'selected' : ''}>Silver-Indium-Cadmium (85% F / 80% Th)</option>
              <option value="BORON" ${rodType === 'BORON' ? 'selected' : ''}>Boron Carbide (70% F / 90% Th)</option>
              <option value="CADMIUM" ${rodType === 'CADMIUM' ? 'selected' : ''}>Cadmium (50% F / 95% Th)</option>
              <option value="INDIUM" ${rodType === 'INDIUM' ? 'selected' : ''}>Indium (60% F / 85% Th)</option>
              <option value="HAFNIUM" ${rodType === 'HAFNIUM' ? 'selected' : ''}>Hafnium (80% F / 75% Th)</option>
            </select>
          </div>
          <div style="margin-bottom:6px;">
            <div style="display:flex; justify-content:space-between; font-size:0.72rem; margin-bottom:2px;">
              <span>Insertion:</span>
              <span id="lbl-rod-ins-${t.x}-${t.y}" style="font-weight:700; color:#00e5ff;">${ins}%</span>
            </div>
            <input type="range" min="0" max="100" value="${ins}"
              oninput="document.getElementById('lbl-rod-ins-${t.x}-${t.y}').innerText = this.value + '%'"
              onchange="updateTileControlRodInsertion(${t.x}, ${t.y}, parseInt(this.value))"
              style="width:100%; cursor:pointer; accent-color:#00e5ff;" />
            <div style="display:flex; gap:4px; margin-top:3px;">
              <button type="button" onclick="updateTileControlRodInsertion(${t.x}, ${t.y}, 0)" style="flex:1; padding:2px; font-size:0.68rem; min-height:24px;">0% (Retract)</button>
              <button type="button" onclick="updateTileControlRodInsertion(${t.x}, ${t.y}, 50)" style="flex:1; padding:2px; font-size:0.68rem; min-height:24px;">50%</button>
              <button type="button" onclick="updateTileControlRodInsertion(${t.x}, ${t.y}, 100)" style="flex:1; padding:2px; font-size:0.68rem; min-height:24px; background:#b91c1c; border-color:#dc2626;">100% (SCRAM)</button>
            </div>
          </div>
          ${(fastAbs > 0 || thermAbs > 0) ? `
            <div style="border-top:1px dashed #373737; padding-top:4px; margin-top:4px; font-size:0.72rem;">
              <div class="stat-row" style="margin-bottom:1px;"><span>Rod fast absorbed:</span><span class="stat-val" style="color:#fbbf24;">${Math.round(fastAbs * cps).toLocaleString()} n/s (${fastAbs.toLocaleString()} /cycle)</span></div>
              <div class="stat-row" style="margin-bottom:1px;"><span>Rod thermal absorbed:</span><span class="stat-val" style="color:#4ade80;">${Math.round(thermAbs * cps).toLocaleString()} n/s (${thermAbs.toLocaleString()} /cycle)</span></div>
            </div>
          ` : ''}
        </div>
      `;
    }
  }

  el.innerHTML = `
    <div style="display:flex; align-items:center; gap:10px; margin-bottom:10px;">
      ${iconHtml}
      <div style="flex:1;">
        <div style="font-weight:bold; font-size:1rem; color:#fff;">${t.name}</div>
        <div style="font-size:0.75rem; color:var(--text-muted); margin-bottom:4px;">Position: (${t.x}, ${t.y})</div>
        ${t.code !== "NL" ? `
          <select onchange="setTile(${t.x}, ${t.y}, this.value)" style="width:100%; font-size:0.75rem; padding:3px 6px; height:auto; min-height:26px;">
            ${PALETTE.filter(p => !p.isControlRod).map(p => `<option value="${p.type}" ${p.type === t.type ? 'selected' : ''}>${p.name}</option>`).join('')}
          </select>
        ` : ''}
      </div>
    </div>
    <div class="stat-row"><span>Temperature:</span><span class="stat-val">${t.temp} °C</span></div>
    ${t.heatOutput != null && t.heatOutput > 0 ? `<div class="stat-row"><span>Heat output:</span><span class="stat-val" style="color:#f59e0b; font-weight:600;">${(t.heatOutput * cps).toLocaleString(undefined, {minimumFractionDigits: 1, maximumFractionDigits: 1})} Hu/s (${t.heatOutput.toLocaleString(undefined, {minimumFractionDigits: 1, maximumFractionDigits: 1})} Hu/cycle)</span></div>` : ''}
    ${coolingMethodHtml}
    ${controlRodHtml}
    ${extraHtml}
    ${fluxHtml}
  `;
}

function syncParamsToUI(p) {
  if (!p) return;
  const active = document.activeElement;
  if (active && active.id && (active.id.startsWith("p-") || active.id === "sim-profile-select")) return;
  const setVal = (id, val) => {
    const el = document.getElementById(id);
    if (el && active !== el && el.value != val) el.value = val;
  };
  setVal("p-hu-per-degree", p.huPerDegree != null ? p.huPerDegree : 32.0);
  setVal("p-base-conductance", p.baseHatchConductance != null ? p.baseHatchConductance : 2.0);
  setVal("p-fission-heat", p.fissionHeatPerNeutron != null ? p.fissionHeatPerNeutron : 76.0);
  setVal("p-fiss-mult", p.globalThermalFissionMultiplier != null ? p.globalThermalFissionMultiplier : (p.thermalFissionMultiplier != null ? p.thermalFissionMultiplier : 0.350));
  setVal("p-ambient-temp", p.ambientTemp);
  setVal("p-ticks-per-cycle", p.ticksPerCycle != null ? p.ticksPerCycle : 5);
  setVal("p-hatch-cap", p.hatchCapacity);
  setVal("p-turn-curve", p.turnoverCurve);
  setVal("p-turn-dt", p.turnoverDeltaTMax);
  setVal("p-turn-exp", p.turnoverExponent);
  setVal("p-feed-rate", p.coolantFeedRate);
  setVal("p-cooling-heat", p.coolingHeatPerLiter);
  setVal("p-ic2-cooling-heat", p.ic2CoolantHeatPerLiter);
  setVal("p-hp-boil", p.hpWaterBoilingPoint);
  setVal("p-burnup-mult", p.fuelBurnupMultiplier != null ? p.fuelBurnupMultiplier : 1.0);
  setVal("p-dense-nbase-mult", p.denseNbaseMultiplier != null ? p.denseNbaseMultiplier : 0.50);
  setVal("p-dense-fiss-mult", p.denseFissionMultiplier != null ? p.denseFissionMultiplier : 1.20);
  setVal("p-excited-nbase-mult", p.excitedNbaseMultiplier != null ? p.excitedNbaseMultiplier : 1.40);
  setVal("p-excited-fiss-mult", p.excitedFissionMultiplier != null ? p.excitedFissionMultiplier : 0.80);
  setVal("p-std-nbase-mult", p.standardNbaseMultiplier != null ? p.standardNbaseMultiplier : 1.00);
  setVal("p-std-fiss-mult", p.standardFissionMultiplier != null ? p.standardFissionMultiplier : 1.00);
  setVal("p-brd-nbase-mult", p.breederNbaseMultiplier != null ? p.breederNbaseMultiplier : 1.00);
  setVal("p-brd-fiss-mult", p.breederFissionMultiplier != null ? p.breederFissionMultiplier : 1.00);
}

async function submitSimParams() {
  const huDeg = document.getElementById("p-hu-per-degree") ? document.getElementById("p-hu-per-degree").value : 32.0;
  const baseCond = document.getElementById("p-base-conductance") ? document.getElementById("p-base-conductance").value : 2.0;
  const fHeat = document.getElementById("p-fission-heat") ? document.getElementById("p-fission-heat").value : 76.0;
  const fMult = document.getElementById("p-fiss-mult") ? document.getElementById("p-fiss-mult").value : 0.350;
  const amb = document.getElementById("p-ambient-temp").value;
  const ticksCycle = document.getElementById("p-ticks-per-cycle") ? document.getElementById("p-ticks-per-cycle").value : 5;
  const cap = document.getElementById("p-hatch-cap").value;
  const curve = document.getElementById("p-turn-curve").value;
  const dt = document.getElementById("p-turn-dt").value;
  const exp = document.getElementById("p-turn-exp").value;
  const feed = document.getElementById("p-feed-rate").value;
  const ch = document.getElementById("p-cooling-heat").value;
  const ic2Ch = document.getElementById("p-ic2-cooling-heat").value;
  const hpBoil = document.getElementById("p-hp-boil").value;
  const burnup = document.getElementById("p-burnup-mult") ? document.getElementById("p-burnup-mult").value : 1.0;
  const denseNb = document.getElementById("p-dense-nbase-mult") ? document.getElementById("p-dense-nbase-mult").value : 0.50;
  const denseFiss = document.getElementById("p-dense-fiss-mult") ? document.getElementById("p-dense-fiss-mult").value : 1.20;
  const excNb = document.getElementById("p-excited-nbase-mult") ? document.getElementById("p-excited-nbase-mult").value : 1.40;
  const excFiss = document.getElementById("p-excited-fiss-mult") ? document.getElementById("p-excited-fiss-mult").value : 0.80;
  const stdNb = document.getElementById("p-std-nbase-mult") ? document.getElementById("p-std-nbase-mult").value : 1.00;
  const stdFiss = document.getElementById("p-std-fiss-mult") ? document.getElementById("p-std-fiss-mult").value : 1.00;
  const brdNb = document.getElementById("p-brd-nbase-mult") ? document.getElementById("p-brd-nbase-mult").value : 1.00;
  const brdFiss = document.getElementById("p-brd-fiss-mult") ? document.getElementById("p-brd-fiss-mult").value : 1.00;

  if (isWasmMode && wasmSim) {
    wasmSim.setParam("huPerDegree", huDeg);
    wasmSim.setParam("baseHatchConductance", baseCond);
    wasmSim.setParam("fissionHeat", fHeat);
    wasmSim.setParam("fissionMult", fMult);
    wasmSim.setParam("ambientTemp", amb);
    wasmSim.setParam("ticksPerCycle", ticksCycle);
    wasmSim.setParam("hatchCapacity", cap);
    wasmSim.setParam("turnoverCurve", curve);
    wasmSim.setParam("turnoverDeltaTMax", dt);
    wasmSim.setParam("turnoverExponent", exp);
    wasmSim.setParam("coolantFeedRate", feed);
    wasmSim.setParam("coolingHeat", ch);
    wasmSim.setParam("ic2CoolantHeat", ic2Ch);
    wasmSim.setParam("hpBoil", hpBoil);
    wasmSim.setParam("fuelBurnupMultiplier", burnup);
    wasmSim.setParam("denseNbaseMultiplier", denseNb);
    wasmSim.setParam("denseFissionMultiplier", denseFiss);
    wasmSim.setParam("excitedNbaseMultiplier", excNb);
    wasmSim.setParam("excitedFissionMultiplier", excFiss);
    wasmSim.setParam("standardNbaseMultiplier", stdNb);
    wasmSim.setParam("standardFissionMultiplier", stdFiss);
    wasmSim.setParam("breederNbaseMultiplier", brdNb);
    wasmSim.setParam("breederFissionMultiplier", brdFiss);
    currentState = wasmSim.getState();
    renderUI();
    return;
  }

  const url = `/api/set-params?huPerDegree=${huDeg}&baseHatchConductance=${baseCond}&fissionHeat=${fHeat}&fissionMult=${fMult}&ambientTemp=${amb}&ticksPerCycle=${ticksCycle}&hatchCapacity=${cap}&turnoverCurve=${curve}&turnoverDeltaTMax=${dt}&turnoverExponent=${exp}&coolantFeedRate=${feed}&coolingHeat=${ch}&ic2CoolantHeat=${ic2Ch}&hpBoil=${hpBoil}`;
  await fetch(url);
  await fetchState();
  schedulePoll();
}

async function resetSimParams() {
  if (document.getElementById("p-ticks-per-cycle")) document.getElementById("p-ticks-per-cycle").value = 5;
  if (isWasmMode && wasmSim) {
    wasmSim.setParam("reset", "true");
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  await fetch("/api/set-params?reset=true");
  await fetchState();
  schedulePoll();
}

function applyProfilePreset(profile) {
  switch (profile) {
    case "BALANCED":
      if (document.getElementById("p-base-conductance")) document.getElementById("p-base-conductance").value = 2.0;
      document.getElementById("p-hatch-cap").value = 8000;
      document.getElementById("p-turn-curve").value = "SIGMOID";
      document.getElementById("p-turn-dt").value = 100;
      document.getElementById("p-turn-exp").value = 1.0;
      document.getElementById("p-feed-rate").value = 999999;
      document.getElementById("p-cooling-heat").value = 5.0;
      document.getElementById("p-t-low").value = 800;
      document.getElementById("p-t-high").value = 3200;
      document.getElementById("p-react-pow").value = 1.2;
      document.getElementById("p-fiss-mult").value = 0.348;
      if (document.getElementById("p-fission-heat")) document.getElementById("p-fission-heat").value = 77.2;
      document.getElementById("p-hp-boil").value = 180;
      if (document.getElementById("p-burnup-mult")) document.getElementById("p-burnup-mult").value = 1.0;
      if (document.getElementById("p-dense-nbase-mult")) document.getElementById("p-dense-nbase-mult").value = 0.50;
      if (document.getElementById("p-dense-fiss-mult")) document.getElementById("p-dense-fiss-mult").value = 1.20;
      if (document.getElementById("p-excited-nbase-mult")) document.getElementById("p-excited-nbase-mult").value = 1.40;
      if (document.getElementById("p-excited-fiss-mult")) document.getElementById("p-excited-fiss-mult").value = 0.80;
      if (document.getElementById("p-std-nbase-mult")) document.getElementById("p-std-nbase-mult").value = 1.00;
      if (document.getElementById("p-std-fiss-mult")) document.getElementById("p-std-fiss-mult").value = 1.00;
      if (document.getElementById("p-brd-nbase-mult")) document.getElementById("p-brd-nbase-mult").value = 1.00;
      if (document.getElementById("p-brd-fiss-mult")) document.getElementById("p-brd-fiss-mult").value = 1.00;
      break;
    case "DANGER":
      if (document.getElementById("p-base-conductance")) document.getElementById("p-base-conductance").value = 1.5;
      document.getElementById("p-hatch-cap").value = 1000;
      document.getElementById("p-turn-curve").value = "EXPONENTIAL";
      document.getElementById("p-turn-dt").value = 80;
      document.getElementById("p-turn-exp").value = 1.8;
      document.getElementById("p-feed-rate").value = 1000;
      document.getElementById("p-cooling-heat").value = 3.0;
      document.getElementById("p-t-low").value = 950;
      document.getElementById("p-t-high").value = 3200;
      document.getElementById("p-react-pow").value = 1.6;
      document.getElementById("p-fiss-mult").value = 1.25;
      document.getElementById("p-hp-boil").value = 200;
      break;
    case "FLASH_BOIL":
      if (document.getElementById("p-base-conductance")) document.getElementById("p-base-conductance").value = 2.0;
      document.getElementById("p-hatch-cap").value = 500;
      document.getElementById("p-turn-curve").value = "SIGMOID";
      document.getElementById("p-turn-dt").value = 60;
      document.getElementById("p-turn-exp").value = 2.0;
      document.getElementById("p-feed-rate").value = 500;
      document.getElementById("p-cooling-heat").value = 4.0;
      document.getElementById("p-t-low").value = 800;
      document.getElementById("p-t-high").value = 2800;
      document.getElementById("p-react-pow").value = 1.2;
      document.getElementById("p-fiss-mult").value = 1.1;
      document.getElementById("p-hp-boil").value = 200;
      break;
    case "SAFE":
      if (document.getElementById("p-base-conductance")) document.getElementById("p-base-conductance").value = 2.5;
      document.getElementById("p-hatch-cap").value = 8000;
      document.getElementById("p-turn-curve").value = "LINEAR";
      document.getElementById("p-turn-dt").value = 150;
      document.getElementById("p-turn-exp").value = 1.0;
      document.getElementById("p-feed-rate").value = 8000;
      document.getElementById("p-cooling-heat").value = 6.0;
      document.getElementById("p-t-low").value = 650;
      document.getElementById("p-t-high").value = 2200;
      document.getElementById("p-react-pow").value = 1.0;
      document.getElementById("p-fiss-mult").value = 0.95;
      document.getElementById("p-hp-boil").value = 200;
      break;
  }
  submitSimParams();
}

let lastChartTick = -1;
function renderChart() {
  const canvas = document.getElementById("chartCanvas");
  if (!canvas) return;
  if (!currentState || !currentState.history || currentState.history.length === 0) return;
  if (currentState.currentTick === lastChartTick) return;
  lastChartTick = currentState.currentTick;

  const ctx = canvas.getContext("2d");
  const w = canvas.width;
  const h = canvas.height;
  ctx.clearRect(0, 0, w, h);

  const hist = currentState.history;
  const maxT = Math.max(100, ...hist.map(p => p.maxT));

  ctx.strokeStyle = "rgba(255,255,255,0.1)";
  ctx.beginPath();
  ctx.moveTo(0, h * 0.25); ctx.lineTo(w, h * 0.25);
  ctx.moveTo(0, h * 0.50); ctx.lineTo(w, h * 0.50);
  ctx.moveTo(0, h * 0.75); ctx.lineTo(w, h * 0.75);
  ctx.stroke();

  // Peak Temp curve (red)
  ctx.strokeStyle = "#ff4757";
  ctx.lineWidth = 2;
  ctx.beginPath();
  hist.forEach((p, idx) => {
    const x = (idx / (hist.length - 1)) * w;
    const y = h - (p.maxT / maxT) * (h - 20) - 10;
    if (idx === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });
  ctx.stroke();

  // Average Temp curve (cyan)
  ctx.strokeStyle = "#00d2ff";
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  hist.forEach((p, idx) => {
    const x = (idx / (hist.length - 1)) * w;
    const y = h - (p.avgT / maxT) * (h - 20) - 10;
    if (idx === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });
  ctx.stroke();
}

async function togglePlay() {
  if (currentState && currentState.exploded) {
    return;
  }
  if (isWasmMode && wasmSim) {
    if (currentState && currentState.haltedByIncident) {
      wasmSim.clearHaltedByIncident();
    }
    wasmSim.togglePlay();
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  await fetch("/api/play");
  await fetchState();
  schedulePoll();
}

async function stepSim(count) {
  if (currentState && currentState.exploded) {
    return;
  }
  if (isWasmMode && wasmSim) {
    if (currentState && currentState.haltedByIncident) {
      wasmSim.clearHaltedByIncident();
    }
    wasmSim.stepTicks(count);
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  await fetch(`/api/step?count=${count}`);
  await fetchState();
  schedulePoll();
}

async function resetSim() {
  lastChartTick = -1;
  if (isWasmMode && wasmSim) {
    wasmSim.reset();
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  await fetch("/api/reset");
  await fetchState();
  schedulePoll();
}
const PRESET_TO_VOLTAGE = {
  "60A_ELECTRUM_5X5": "EV",
  "60A_PLATINUM_9X9": "IV",
  "60A_OSMIUM_9X9": "LuV",
  "60A_QUANTIUM_13X13": "ZPM",
  "60A_FLUXED_13X13": "UV",
  "60A_PLUTONIUM_13X13": "UHV",
  "BEST_ELECTRUM_5X5": "EV",
  "BEST_PLATINUM_9X9": "IV",
  "BEST_OSMIUM_9X9": "LuV",
  "BEST_QUANTIUM_9X9": "ZPM",
  "BEST_QUANTIUM_13X13": "ZPM",
  "BEST_FLUXED_9X9": "UV",
  "BEST_FLUXED_13X13": "UV",
  "BEST_PLUTONIUM_9X9": "UHV",
  "BEST_PLUTONIUM_13X13": "UHV"
};

const TIER_TO_VOLTAGE = ["EV", "IV", "LuV", "ZPM", "UV", "UHV"];

function formatEuCompact(eut) {
  if (!eut || isNaN(eut)) return "0 EU/t";
  if (eut >= 1e8) return Math.round(eut / 1e6) + "M EU/t";
  if (eut >= 1e6) {
    const val = (eut / 1e6).toFixed(eut >= 1e7 ? 1 : 2).replace(/\.0+$/, '');
    return val + "M EU/t";
  }
  if (eut >= 1e3) return Math.round(eut / 1e3) + "k EU/t";
  return Math.round(eut) + " EU/t";
}

function applyTierCoolingDefaults(tierNum, p = null) {
  if (!wasmSim) return;
  const TIER_DEFAULTS = {
    0: { mode: "CONDUCTIVE", mat: "TITANIUM", size: "NORMAL", fluid: "DISTILLED_WATER", hatch: "EV", rotor: "ORIHARUKON" },
    1: { mode: "CONDUCTIVE", mat: "TUNGSTENSTEEL", size: "LARGE", fluid: "DISTILLED_WATER", hatch: "IV", rotor: "ICHORIUM" },
    2: { mode: "CONVECTIVE_LOOP", mat: "OSMIUM", size: "LARGE", fluid: "DISTILLED_WATER", hatch: "LUV", rotor: "DURANIUM" },
    3: { mode: "CONVECTIVE_LOOP", mat: "NEUTRONIUM", size: "HUGE", fluid: "HEAVY_WATER", hatch: "ZPM", rotor: "DURANIUM" },
    4: { mode: "CONVECTIVE_LOOP", mat: "NEUTRONIUM", size: "HUGE", fluid: "HEAVY_WATER", hatch: "UV", rotor: "DURANIUM" },
    5: { mode: "CONVECTIVE_LOOP", mat: "NEUTRONIUM", size: "HUGE", fluid: "HEAVY_WATER", hatch: "UHV", rotor: "INFINITY" }
  };
  const def = TIER_DEFAULTS[tierNum] || TIER_DEFAULTS[1];
  const mode = (p && p.cooling_mode) ? p.cooling_mode : def.mode;
  wasmSim.setCoolingMode(mode);
  if (mode === "CONVECTIVE_LOOP") {
    let mat = def.mat;
    let sz = def.size;
    let fl = def.fluid;
    let ht = def.hatch;
    if (p && p.default_loop && Array.isArray(p.default_loop) && p.default_loop.length >= 3) {
      mat = p.default_loop[0];
      sz = p.default_loop[1];
      fl = p.default_loop[2];
    }
    if (p && p.loop_mat) mat = p.loop_mat;
    if (p && p.loop_size) sz = p.loop_size;
    if (p && p.loop_fluid) fl = p.loop_fluid;
    if (p && p.hatch_tier) ht = p.hatch_tier;
    if (p && p.loop_hatch) ht = p.loop_hatch;
    const opt = (k, d) => (p && p[k] !== undefined && p[k] !== null) ? p[k] : d;
    wasmSim.setCoolantLoopMaterial(mat, false);
    wasmSim.setCoolantLoopPipeSize(sz, false);
    wasmSim.setCoolantLoopFluid(fl, false);
    wasmSim.setCoolantLoopHatchTier(ht, false);
    wasmSim.setCoolantLoopPumpHatches(opt("loop_hatches", 1), false);
    wasmSim.setCoolantLoopRotor(opt("loop_rotor", def.rotor), opt("loop_rotor_size", "HUGE"), false);
    wasmSim.setCoolantLoopPhe(opt("phe_count", 1), opt("phe_hatch", ht), opt("phe_hatches", 1), false);
    wasmSim.setCoolantLoopCount(opt("loop_count", 1), false);
    wasmSim.setCoolantLoopControl(opt("loop_duty", 100), opt("loop_max_flow", 0), opt("loop_max_pressure", 0), false);
    if (p && p.loop_secondary) wasmSim.setCoolantLoopSecondaryCoolant(p.loop_secondary, false);
  }
}

function loadPresetFromData(p) {
  if (!p || !p.layout) return;
  lastChartTick = -1;
  if (window.innerWidth <= 800) {
    isFitMode = true;
  }
  const tierMap = { "EV": 0, "IV": 1, "LuV": 2, "ZPM": 3, "UV": 4, "UHV": 5 };
  const pipeTier = (p.tier && tierMap[p.tier] !== undefined) ? tierMap[p.tier] : 1;
  if (p.tier) {
    setVoltageTier(p.tier);
  }
  // A design saved with its simulator options loads exactly as the calibration ran it: casing, turbine, cooling and
  // every coolant loop circuit
  if (isWasmMode && wasmSim && p.sim_options && wasmSim.loadRunOptions && wasmSim.loadRunOptions(p.sim_options, false)) {
    currentState = wasmSim.getState();
    if (isFitMode) recalculateFitZoom();
    renderUI();
    return;
  }
  if (isWasmMode && wasmSim) {
    const rows = p.layout.trim().split(";");
    const h = rows.length;
    const w = rows[0].split(",").length;
    if (w > 0 && h > 0) {
      wasmSim.initGrid(w, h, pipeTier);
    }
    applyTierCoolingDefaults(pipeTier, p);
    if (wasmSim.loadLayout) {
      wasmSim.loadLayout(p.layout, false);
    }
    if (p.turbine && p.turbine.length >= 3 && wasmSim.setTurbine) {
      const mat = p.turbine[0].toUpperCase().replace(/-/g, '_');
      const sz = p.turbine[1].toUpperCase();
      const fit = p.turbine[2].toUpperCase();
      wasmSim.setTurbine(mat, sz, fit, false);
    }
    currentState = wasmSim.getState();
    if (isFitMode) recalculateFitZoom();
    renderUI();
  }
}

window.__CEILING_PRESETS_MAP__ = {};
function initCeilingPresets() {
  const optGroup = document.getElementById("ceiling-presets-optgroup");
  if (!optGroup) return;
  if (!window.__CEILING_PRESETS__ || !Array.isArray(window.__CEILING_PRESETS__) || window.__CEILING_PRESETS__.length === 0) {
    return;
  }
  optGroup.innerHTML = "";
  window.__CEILING_PRESETS__.forEach((p, idx) => {
    const key = "CEILING_" + (p.tier ? p.tier.toUpperCase() : idx);
    window.__CEILING_PRESETS_MAP__[key] = p;
    if (p.tier) {
      window.__CEILING_PRESETS_MAP__["BEST_" + p.tier.toUpperCase()] = p;
    }
    const opt = document.createElement("option");
    opt.value = key;
    const pwr = formatEuCompact(p.peak_power_eut || p.steam_power_eut);
    const amps = p.peak_amps != null ? (p.peak_amps >= 100 ? Math.round(p.peak_amps) : p.peak_amps.toFixed(1)) + "A" : "";
    const cleanName = (p.name || "").replace(/\s*\([A-Z0-9]+\)$/, '');
    opt.textContent = `${p.tier} Peak: ${cleanName} (${pwr}${amps ? ' · ' + amps : ''})`;
    if (p.tier === "IV") {
      opt.selected = true;
    }
    optGroup.appendChild(opt);
  });
}

async function loadPreset(name) {
  lastChartTick = -1;
  if (window.innerWidth <= 800) {
    isFitMode = true;
  }
  if (window.__CEILING_PRESETS_MAP__ && window.__CEILING_PRESETS_MAP__[name]) {
    loadPresetFromData(window.__CEILING_PRESETS_MAP__[name]);
    return;
  }
  if (window.__CEILING_PRESETS_MAP__) {
    const tierMatch = name.match(/BEST_([A-Z]+)/);
    if (tierMatch) {
      const tierNameMap = {
        "ELECTRUM": "EV",
        "PLATINUM": "IV",
        "OSMIUM": "LuV",
        "QUANTIUM": "ZPM",
        "FLUXED": "UV",
        "PLUTONIUM": "UHV"
      };
      const t = tierNameMap[tierMatch[1]];
      if (t && window.__CEILING_PRESETS_MAP__["CEILING_" + t]) {
        loadPresetFromData(window.__CEILING_PRESETS_MAP__["CEILING_" + t]);
        return;
      }
    }
  }
  if (PRESET_TO_VOLTAGE[name]) {
    setVoltageTier(PRESET_TO_VOLTAGE[name]);
  }
  if (isWasmMode && wasmSim) {
    wasmSim.loadPreset(name);
    currentState = wasmSim.getState();
    if (isFitMode) recalculateFitZoom();
    renderUI();
    return;
  }
  await fetch(`/api/load-preset?name=${encodeURIComponent(name)}`);
  await fetchState();
  if (isFitMode) recalculateFitZoom();
  schedulePoll();
}

async function setTile(x, y, type) {
  if (isWasmMode && wasmSim) {
    wasmSim.setTile(x, y, type);
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  await fetch(`/api/set-tile?x=${x}&y=${y}&type=${encodeURIComponent(type)}`);
  isFetching = false;
  await fetchState();
  schedulePoll();
}

async function changeTier(tier) {
  const tierNum = parseInt(tier);
  if (TIER_TO_VOLTAGE[tierNum]) {
    setVoltageTier(TIER_TO_VOLTAGE[tierNum]);
  }
  if (isWasmMode && wasmSim) {
    wasmSim.setPipeTier(tierNum);
    applyTierCoolingDefaults(tierNum);
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  await fetch(`/api/set-tier?tier=${tier}`);
  await fetchState();
  schedulePoll();
}

async function toggleTileControlRod(x, y, hasRod) {
  if (isWasmMode && wasmSim) {
    const tile = currentState && currentState.tiles.find(t => t.x === x && t.y === y);
    const curType = (tile && tile.controlRodType && tile.controlRodType !== "NONE") ? tile.controlRodType : "BORON";
    const curIns = (tile && tile.controlRodInsertion != null) ? tile.controlRodInsertion : 0;
    wasmSim.setControlRod(x, y, hasRod, curType, curIns);
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  await fetch(`/api/set-control-rod?x=${x}&y=${y}&hasRod=${hasRod}`);
  await fetchState();
}

async function changeTileControlRodMaterial(x, y, material) {
  if (isWasmMode && wasmSim) {
    const tile = currentState && currentState.tiles.find(t => t.x === x && t.y === y);
    const curIns = (tile && tile.controlRodInsertion != null) ? tile.controlRodInsertion : 0;
    wasmSim.setControlRod(x, y, true, material, curIns);
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  await fetch(`/api/set-control-rod?x=${x}&y=${y}&hasRod=true&material=${encodeURIComponent(material)}`);
  await fetchState();
}

async function updateTileControlRodInsertion(x, y, insertion) {
  if (isWasmMode && wasmSim) {
    const tile = currentState && currentState.tiles.find(t => t.x === x && t.y === y);
    const curType = (tile && tile.controlRodType) ? tile.controlRodType : "BORON";
    wasmSim.setControlRod(x, y, true, curType, insertion);
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  await fetch(`/api/set-control-rod?x=${x}&y=${y}&hasRod=true&insertion=${insertion}`);
  await fetchState();
}

async function quickScram() {
  if (isWasmMode && wasmSim) {
    wasmSim.scram();
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  await fetch("/api/scram");
  await fetchState();
}

async function setAllControlRodsInsertion(ins) {
  if (isWasmMode && wasmSim) {
    wasmSim.setAllControlRodsInsertion(ins);
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  await fetch(`/api/set-all-control-rods?insertion=${ins}`);
  await fetchState();
}

