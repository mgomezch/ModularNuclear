function renderProcessPipeline() {
  if (!currentState || !currentState.powerEstimate) return;
  const pe = currentState.powerEstimate;
  const tier = currentState.pipeTier !== undefined ? currentState.pipeTier : 1;
  const tierBadge = document.getElementById("proc-tier-badge");
  const tierDesc = document.getElementById("proc-tier-desc");

  const stagePv = document.getElementById("proc-stage-pv");
  const stageCoolant = document.getElementById("proc-stage-coolant");
  const stageSc = document.getElementById("proc-stage-sc");
  const stageHp = document.getElementById("proc-stage-hp");
  const stageReg = document.getElementById("proc-stage-reg");

  // Stage 0: Radiovoltaics
  document.getElementById("proc-pv-val").innerText = formatPowerAndAmps(pe.directPowerEUt, true);
  document.getElementById("proc-pv-sub").innerText = pe.directPowerEUt > 0
    ? `${Math.round(pe.directPowerEUt).toLocaleString()} EU/t direct power from radiovoltaic cells`
    : "No radiovoltaic cells generating direct power";

  // Tier Policies
  if (tier <= 0) {
    // EV
    tierBadge.innerText = "EV Pipeline";
    tierBadge.style.color = "#38bdf8";
    tierDesc.innerHTML = "<strong>EV Mode:</strong> Large Heat Exchanger (1L Hot Coolant → 400L Regular Steam) + Large Steam Turbines (LST, 0.5 EU/L) + Direct Radiovoltaics. <em>XLST and Superheated/Supercritical processing unavailable at EV.</em>";

    // Stage 1: LHE
    document.getElementById("proc-coolant-title").innerText = "🔄 Stage 1: Large Heat Exchanger (LHE)";
    const hasCoolant = (currentState.flowHotCoolant || 0) > 0;
    document.getElementById("proc-coolant-count").innerText = hasCoolant ? (pe.coolantMachineCount.toFixed(0) + "x LHE") : "0x LHE";
    document.getElementById("proc-coolant-detail").innerText = hasCoolant
      ? `Input: ${Math.round(currentState.flowHotCoolant).toLocaleString()} L/t Hot Coolant → Output: ${Math.round(pe.coolantSteamProduced).toLocaleString()} L/t Regular Steam (1:400)`
      : "Chamber produces no IC2 Hot Coolant";
    document.getElementById("proc-coolant-water").innerText = hasCoolant
      ? `Distilled Water consumed: ${Math.round(pe.coolantWaterConsumed).toLocaleString()} L/t`
      : "";

    // Stage 2: Locked
    stageSc.style.opacity = "0.45";
    document.getElementById("proc-sc-title").innerText = "🔒 Stage 2: XLST-SC (Supercritical)";
    document.getElementById("proc-sc-pwr").innerText = "Locked (ZPM+)";
    document.getElementById("proc-sc-detail").innerText = "Requires ZPM or higher. Supercritical steam cannot be turbine-processed at EV.";
    document.getElementById("proc-sc-cascade").innerText = "";

    // Stage 3: Locked
    stageHp.style.opacity = "0.45";
    document.getElementById("proc-hp-title").innerText = "🔒 Stage 3: XLST-HP (Superheated)";
    document.getElementById("proc-hp-pwr").innerText = "Locked (IV+)";
    document.getElementById("proc-hp-detail").innerText = "Requires IV or higher. Superheated steam cannot be turbine-processed at EV.";
    document.getElementById("proc-hp-cascade").innerText = "";

    // Stage 4: LST
    stageReg.style.opacity = "1.0";
    document.getElementById("proc-reg-title").innerText = "⚡ Stage 4: Large Steam Turbine (LST)";
    document.getElementById("proc-reg-pwr").innerText = formatPowerAndAmps(pe.lstPowerEUt, true);
    const regFlow = Math.round((currentState.flowRegularSteam || 0) + pe.coolantSteamProduced);
    document.getElementById("proc-reg-detail").innerText = `${regFlow.toLocaleString()} L/t steam · ${pe.lstTurbinesNeeded.toFixed(2)} LST units (opt ${Math.round(pe.optFlowPerTurbine).toLocaleString()} L/t)`;
    document.getElementById("proc-reg-eff").innerText = `Base 0.5 EU/L × ${(pe.efficiency * 100).toFixed(1)}% Eff (${pe.lstPowerEUt > 0 ? (pe.lstPowerEUt / Math.max(1, regFlow)).toFixed(3) : 0} EU/L)`;

  } else if (tier === 1) {
    // IV
    tierBadge.innerText = "IV Pipeline";
    tierBadge.style.color = "#a3e635";
    tierDesc.innerHTML = "<strong>IV Mode:</strong> Thermal Boiler (1L Hot Coolant → 200L SH Steam) + XLST-HP (1.0 EU/L, cascades 1:1) + XLST (0.5 EU/L). <em>EHE and XLST-SC unavailable at IV.</em>";

    // Stage 1: Thermal Boiler
    document.getElementById("proc-coolant-title").innerText = "🔄 Stage 1: Thermal Boiler";
    const hasCoolant = (currentState.flowHotCoolant || 0) > 0;
    document.getElementById("proc-coolant-count").innerText = hasCoolant ? (pe.coolantMachineCount.toFixed(0) + "x Thermal Boiler") : "0x Thermal Boiler";
    document.getElementById("proc-coolant-detail").innerText = hasCoolant
      ? `Input: ${Math.round(currentState.flowHotCoolant).toLocaleString()} L/t Hot Coolant → Output: ${Math.round(pe.coolantSteamProduced).toLocaleString()} L/t SH Steam (1:200)`
      : "Chamber produces no IC2 Hot Coolant";
    document.getElementById("proc-coolant-water").innerText = hasCoolant
      ? `Water consumed: ${Math.round(pe.coolantWaterConsumed).toLocaleString()} L/t`
      : "";

    // Stage 2: Locked
    stageSc.style.opacity = "0.45";
    document.getElementById("proc-sc-title").innerText = "🔒 Stage 2: XLST-SC (Supercritical)";
    document.getElementById("proc-sc-pwr").innerText = "Locked (ZPM+)";
    document.getElementById("proc-sc-detail").innerText = "Requires ZPM or higher. Supercritical steam cannot be turbine-processed at IV.";
    document.getElementById("proc-sc-cascade").innerText = "";

    // Stage 3: XLST-HP
    stageHp.style.opacity = "1.0";
    document.getElementById("proc-hp-title").innerText = "⚡ Stage 3: XLST-HP (superheated steam)";
    document.getElementById("proc-hp-pwr").innerText = formatPowerAndAmps(pe.xlstHpPowerEUt, true);
    const hpFlow = Math.round((currentState.flowSuperheatedSteam || 0) + (currentState.flowHeavyWaterSteam || 0) + pe.coolantSteamProduced);
    document.getElementById("proc-hp-detail").innerText = `${hpFlow.toLocaleString()} L/t SH steam · ${pe.xlstHpTurbinesNeeded.toFixed(2)} turbines (opt ${Math.round(pe.optFlowPerTurbine).toLocaleString()} L/t)`;
    document.getElementById("proc-hp-cascade").innerText = "Cascades 1:1 into Regular Steam for Stage 4";

    // Stage 4: XLST Regular
    stageReg.style.opacity = "1.0";
    document.getElementById("proc-reg-title").innerText = "⚡ Stage 4: XLST (regular steam)";
    document.getElementById("proc-reg-pwr").innerText = formatPowerAndAmps(pe.xlstPowerEUt, true);
    const regFlow = Math.round((currentState.flowRegularSteam || 0) + hpFlow);
    document.getElementById("proc-reg-detail").innerText = `${regFlow.toLocaleString()} L/t steam (including cascade) · ${pe.xlstTurbinesNeeded.toFixed(2)} turbines`;
    document.getElementById("proc-reg-eff").innerText = `Base 0.5 EU/L × ${(pe.efficiency * 100).toFixed(1)}% Eff (${pe.xlstPowerEUt > 0 ? (pe.xlstPowerEUt / Math.max(1, regFlow)).toFixed(3) : 0} EU/L)`;

  } else if (tier === 2) {
    // LuV
    tierBadge.innerText = "LuV Pipeline";
    tierBadge.style.color = "#f59e0b";
    tierDesc.innerHTML = "<strong>LuV Mode:</strong> Extreme Heat Exchanger (1L Hot Coolant → 200L SH Steam) + XLST-HP (1.0 EU/L, cascades 1:1) + XLST (0.5 EU/L). <em>XLST-SC unlocks at ZPM.</em>";

    // Stage 1: EHE
    document.getElementById("proc-coolant-title").innerText = "🔄 Stage 1: Extreme Heat Exchanger (EHE)";
    const hasCoolant = (currentState.flowHotCoolant || 0) > 0;
    document.getElementById("proc-coolant-count").innerText = hasCoolant ? (pe.coolantMachineCount.toFixed(0) + "x EHE") : "0x EHE";
    document.getElementById("proc-coolant-detail").innerText = hasCoolant
      ? `Input: ${Math.round(currentState.flowHotCoolant).toLocaleString()} L/t Hot Coolant → Output: ${Math.round(pe.coolantSteamProduced).toLocaleString()} L/t SH Steam (1:200)`
      : "Chamber produces no IC2 Hot Coolant";
    document.getElementById("proc-coolant-water").innerText = hasCoolant
      ? `Distilled Water consumed: ${Math.round(pe.coolantWaterConsumed).toLocaleString()} L/t`
      : "";

    // Stage 2: Locked
    stageSc.style.opacity = "0.45";
    document.getElementById("proc-sc-title").innerText = "🔒 Stage 2: XLST-SC (supercritical)";
    document.getElementById("proc-sc-pwr").innerText = "Locked (ZPM+)";
    document.getElementById("proc-sc-detail").innerText = "Requires ZPM or higher for XLST-SC.";
    document.getElementById("proc-sc-cascade").innerText = "";

    // Stage 3: XLST-HP
    stageHp.style.opacity = "1.0";
    document.getElementById("proc-hp-title").innerText = "⚡ Stage 3: XLST-HP (superheated steam)";
    document.getElementById("proc-hp-pwr").innerText = formatPowerAndAmps(pe.xlstHpPowerEUt, true);
    const hpFlow = Math.round((currentState.flowSuperheatedSteam || 0) + (currentState.flowHeavyWaterSteam || 0) + pe.coolantSteamProduced);
    document.getElementById("proc-hp-detail").innerText = `${hpFlow.toLocaleString()} L/t SH steam · ${pe.xlstHpTurbinesNeeded.toFixed(2)} turbines (opt ${Math.round(pe.optFlowPerTurbine).toLocaleString()} L/t)`;
    document.getElementById("proc-hp-cascade").innerText = "Cascades 1:1 into Regular Steam for Stage 4";

    // Stage 4: XLST Regular
    stageReg.style.opacity = "1.0";
    document.getElementById("proc-reg-title").innerText = "⚡ Stage 4: XLST (regular steam)";
    document.getElementById("proc-reg-pwr").innerText = formatPowerAndAmps(pe.xlstPowerEUt, true);
    const regFlow = Math.round((currentState.flowRegularSteam || 0) + hpFlow);
    document.getElementById("proc-reg-detail").innerText = `${regFlow.toLocaleString()} L/t steam (including cascade) · ${pe.xlstTurbinesNeeded.toFixed(2)} turbines`;
    document.getElementById("proc-reg-eff").innerText = `Base 0.5 EU/L × ${(pe.efficiency * 100).toFixed(1)}% Eff`;

  } else {
    // ZPM+
    const tierName = (tier === 3) ? "ZPM" : (tier === 4) ? "UV" : "UHV";
    tierBadge.innerText = `${tierName} Full 3-Stage Cascade`;
    tierBadge.style.color = "#ec4899";
    tierDesc.innerHTML = "<strong>ZPM+ Mode:</strong> EHE (Supercritical if ≥8,000 L/s coolant, else SH) + Full 3-Stage Cascade: XLST-SC (1.0 EU/L) → XLST-HP (1.0 EU/L) → XLST (0.5 EU/L).";

    // Stage 1: EHE
    const isSC = pe.coolantMachineMode && pe.coolantMachineMode.includes("Supercritical");
    document.getElementById("proc-coolant-title").innerText = `🔄 Stage 1: Extreme Heat Exchanger (EHE) [${isSC ? "Supercritical" : "Superheated"}]`;
    const hasCoolant = (currentState.flowHotCoolant || 0) > 0;
    document.getElementById("proc-coolant-count").innerText = hasCoolant ? (pe.coolantMachineCount.toFixed(0) + "x EHE") : "0x EHE";
    document.getElementById("proc-coolant-detail").innerText = hasCoolant
      ? `Input: ${Math.round(currentState.flowHotCoolant).toLocaleString()} L/t (${Math.round(currentState.flowHotCoolant * 20).toLocaleString()} L/s) → Output: ${Math.round(pe.coolantSteamProduced).toLocaleString()} L/t ${isSC ? "SC" : "SH"} Steam`
      : "Chamber produces no IC2 Hot Coolant";
    document.getElementById("proc-coolant-water").innerText = hasCoolant
      ? `Distilled Water consumed: ${Math.round(pe.coolantWaterConsumed).toLocaleString()} L/t`
      : "";

    // Stage 2: XLST-SC
    stageSc.style.opacity = "1.0";
    document.getElementById("proc-sc-title").innerText = "⚡ Stage 2: XLST-SC (supercritical steam)";
    document.getElementById("proc-sc-pwr").innerText = formatPowerAndAmps(pe.xlstScPowerEUt, true);
    const scFlow = Math.round((currentState.flowSupercriticalSteam || 0) + (isSC ? pe.coolantSteamProduced : 0));
    document.getElementById("proc-sc-detail").innerText = `${scFlow.toLocaleString()} L/t SC steam · ${pe.xlstScTurbinesNeeded.toFixed(2)} turbines (opt ${Math.round(pe.optFlowPerTurbine).toLocaleString()} L/t)`;
    document.getElementById("proc-sc-cascade").innerText = "Cascades 1:1 into Superheated Steam for Stage 3";

    // Stage 3: XLST-HP
    stageHp.style.opacity = "1.0";
    document.getElementById("proc-hp-title").innerText = "⚡ Stage 3: XLST-HP (superheated steam)";
    document.getElementById("proc-hp-pwr").innerText = formatPowerAndAmps(pe.xlstHpPowerEUt, true);
    const hpDirect = Math.round((currentState.flowSuperheatedSteam || 0) + (currentState.flowHeavyWaterSteam || 0) + (!isSC ? pe.coolantSteamProduced : 0));
    const hpTotal = hpDirect + scFlow;
    document.getElementById("proc-hp-detail").innerText = `${hpTotal.toLocaleString()} L/t SH steam (${scFlow.toLocaleString()} from SC cascade) · ${pe.xlstHpTurbinesNeeded.toFixed(2)} turbines`;
    document.getElementById("proc-hp-cascade").innerText = "Cascades 1:1 into Regular Steam for Stage 4";

    // Stage 4: XLST Regular
    stageReg.style.opacity = "1.0";
    document.getElementById("proc-reg-title").innerText = "⚡ Stage 4: XLST (regular steam)";
    document.getElementById("proc-reg-pwr").innerText = formatPowerAndAmps(pe.xlstPowerEUt, true);
    const regDirect = Math.round(currentState.flowRegularSteam || 0);
    const regTotal = regDirect + hpTotal;
    document.getElementById("proc-reg-detail").innerText = `${regTotal.toLocaleString()} L/t steam (${hpTotal.toLocaleString()} from HP cascade) · ${pe.xlstTurbinesNeeded.toFixed(2)} turbines`;
    document.getElementById("proc-reg-eff").innerText = `Base 0.5 EU/L × ${(pe.efficiency * 100).toFixed(1)}% Eff`;
  }

  // Net Generation
  document.getElementById("proc-net-pwr").innerText = formatPowerAndAmps(pe.totalPowerEUt, false);
}

function setByproductUnit(unit) {
  byproductUnit = unit;
  const minBtn = document.getElementById("btn-byproduct-min");
  const hourBtn = document.getElementById("btn-byproduct-hour");
  if (minBtn) minBtn.classList.toggle("active", unit === "min");
  if (hourBtn) hourBtn.classList.toggle("active", unit === "hour");
  renderByproducts();
}

function onDamageSliderInput(val) {
  const lbl = document.getElementById("lbl-reactor-damage-val");
  if (lbl) lbl.innerText = parseFloat(val).toFixed(1) + "%";
}

async function onDamageSliderChange(val) {
  const dmg = Math.max(0.0, Math.min(100.0, parseFloat(val) || 0.0));
  if (isWasmMode && wasmSim) {
    wasmSim.setReactorDamage(dmg);
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  await fetch(`/api/set-params?damage=${dmg}`);
  await fetchState();
}

async function setDamagePercent(pct) {
  const rng = document.getElementById("rng-reactor-damage");
  if (rng) rng.value = pct;
  const lbl = document.getElementById("lbl-reactor-damage-val");
  if (lbl) lbl.innerText = pct.toFixed(1) + "%";
  await onDamageSliderChange(pct);
}

function toggleStopOnIncidents(checked) {
  const lbl = document.getElementById("lbl-stop-incidents");
  if (lbl) {
    lbl.innerText = checked ? "(Enabled)" : "(Off)";
    lbl.style.color = checked ? "var(--warning)" : "var(--text-muted)";
    lbl.style.fontWeight = checked ? "600" : "normal";
  }
  if (isWasmMode && wasmSim) {
    wasmSim.setStopOnIncidents(checked);
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  fetch(`/api/set-params?stopOnIncidents=${checked}`);
}

function toggleAutoSupplyFuel(checked) {
  const lblSupply = document.getElementById("lbl-auto-supply-fuel");
  if (lblSupply) {
    lblSupply.innerText = checked ? "(Enabled)" : "(Off)";
    lblSupply.style.color = checked ? "#34d399" : "var(--text-muted)";
    lblSupply.style.fontWeight = checked ? "600" : "normal";
  }
  const lblRefuel = document.getElementById("lbl-auto-refuel");
  if (lblRefuel) {
    lblRefuel.innerText = checked ? "Enabled" : "Disabled";
    lblRefuel.style.color = checked ? "#34d399" : "var(--text-muted)";
  }
  const chkSupply = document.getElementById("chk-auto-supply-fuel");
  const chkRefuel = document.getElementById("chk-auto-refuel");
  if (chkSupply && chkSupply.checked !== checked) chkSupply.checked = checked;
  if (chkRefuel && chkRefuel.checked !== checked) chkRefuel.checked = checked;

  if (isWasmMode && wasmSim) {
    wasmSim.setAutoSupplyFuel(checked);
    currentState = wasmSim.getState();
    renderUI();
    return;
  }
  fetch(`/api/set-auto-refuel?enabled=${checked}`);
}

function toggleAutoReplaceFuel(checked) {
  toggleAutoSupplyFuel(checked);
}

function renderByproducts() {
  if (!currentState) return;
  const bp = currentState.byproducts;
  if (!bp) return;

  const isHour = (byproductUnit === "hour");
  const unitLabel = isHour ? "/h" : "/min";
  const fluidUnitLabel = isHour ? "L/h" : "L/min";

  const autoChk = document.getElementById("chk-auto-refuel");
  const autoLbl = document.getElementById("lbl-auto-refuel");
  if (autoChk && bp.autoReplaceFuel !== undefined && autoChk !== document.activeElement) {
    autoChk.checked = bp.autoReplaceFuel;
    if (autoLbl) {
      autoLbl.innerText = bp.autoReplaceFuel ? "Enabled" : "Disabled";
      autoLbl.style.color = bp.autoReplaceFuel ? "#34d399" : "var(--text-muted)";
    }
  }

  const rateBadge = document.getElementById("byproduct-rate-badge");
  if (rateBadge) {
    const totalSolidRate = isHour ? bp.totalSolidItemsPerHour : bp.totalSolidItemsPerMin;
    rateBadge.innerText = `${(totalSolidRate || 0).toFixed(isHour ? 1 : 2)} items ${unitLabel}`;
  }

  const solidTotalEl = document.getElementById("byproduct-solid-total");
  const solidListEl = document.getElementById("byproduct-solid-list");
  if (solidTotalEl && solidListEl) {
    const totalSolidRate = isHour ? bp.totalSolidItemsPerHour : bp.totalSolidItemsPerMin;
    solidTotalEl.innerText = `${(totalSolidRate || 0).toFixed(isHour ? 1 : 2)} ${unitLabel}`;

    if (!bp.solidRods || bp.solidRods.length === 0) {
      solidListEl.innerHTML = `<div style="font-style:italic; color:#64748b; padding:2px 0;">No active fuel rods burning.</div>`;
    } else {
      let html = "";
      bp.solidRods.forEach(s => {
        const rate = isHour ? s.itemsPerHour : s.itemsPerMin;
        const rateStr = `${rate.toFixed(isHour ? 1 : 2)} ${unitLabel}`;
        const lifespanStr = (s.avgLifespanMin && s.avgLifespanMin !== "Infinity" && !isNaN(s.avgLifespanMin))
          ? ` · ~${parseFloat(s.avgLifespanMin).toFixed(1)}m lifespan`
          : "";
        html += `
          <div style="display:flex; justify-content:space-between; align-items:center; background:#0f172a; border-radius:4px; padding:4px 6px; font-size:0.72rem;">
            <div style="display:flex; align-items:center; gap:6px;">
              <span class="badge" style="font-size:0.65rem; padding:1px 4px; color:#34d399; border-color:#059669;">${s.depletedCode}</span>
              <span style="color:#e2e8f0; font-weight:500;">${s.depletedName}</span>
              <span style="color:var(--text-muted); font-size:0.68rem;">(${s.activeRods} rod${s.activeRods === 1 ? "" : "s"}${lifespanStr})</span>
            </div>
            <div style="text-align:right;">
              <span style="color:#4ade80; font-weight:600;">${rateStr}</span>
              ${s.totalProduced > 0 ? `<div style="font-size:0.65rem; color:var(--text-muted);">${s.totalProduced.toLocaleString()} produced</div>` : ""}
            </div>
          </div>
        `;
      });
      solidListEl.innerHTML = html;
    }
  }

  const liquidSec = document.getElementById("byproduct-liquid-section");
  const liquidTotalEl = document.getElementById("byproduct-liquid-total");
  const liquidListEl = document.getElementById("byproduct-liquid-list");
  if (liquidSec && liquidTotalEl && liquidListEl) {
    if (!bp.liquidFuels || bp.liquidFuels.length === 0) {
      liquidSec.style.display = "none";
    } else {
      liquidSec.style.display = "block";
      const totalLiqRate = isHour ? bp.totalLiquidLitersPerHour : bp.totalLiquidLitersPerMin;
      liquidTotalEl.innerText = `${Math.round(totalLiqRate || 0).toLocaleString()} ${fluidUnitLabel}`;

      let html = "";
      bp.liquidFuels.forEach(l => {
        const rate = isHour ? l.litersPerHour : l.litersPerMin;
        const rateStr = `${Math.round(rate).toLocaleString()} ${fluidUnitLabel}`;
        html += `
          <div style="display:flex; justify-content:space-between; align-items:center; background:#0f172a; border-radius:4px; padding:4px 6px; font-size:0.72rem;">
            <div style="display:flex; align-items:center; gap:6px;">
              <span class="badge" style="font-size:0.65rem; padding:1px 4px; color:#38bdf8; border-color:#0284c7;">FLUID</span>
              <span style="color:#e2e8f0; font-weight:500;">${l.displayName}</span>
              <span style="color:var(--text-muted); font-size:0.68rem;">(${l.activeHatches} hatch${l.activeHatches === 1 ? "" : "es"})</span>
            </div>
            <div style="text-align:right;">
              <span style="color:#38bdf8; font-weight:600;">${rateStr}</span>
              ${l.totalLiters > 0 ? `<div style="font-size:0.65rem; color:var(--text-muted);">${l.totalLiters.toLocaleString()} L total</div>` : ""}
            </div>
          </div>
        `;
      });
      liquidListEl.innerHTML = html;
    }
  }

  const isoSec = document.getElementById("byproduct-isotope-section");
  const isoTotalEl = document.getElementById("byproduct-isotope-total");
  const isoListEl = document.getElementById("byproduct-isotope-list");
  if (isoSec && isoTotalEl && isoListEl) {
    const hasIsotopes = bp.isotopes && bp.isotopes.some(iso => iso.totalLiters > 0 || iso.litersPerMin > 0);
    if (!hasIsotopes) {
      isoSec.style.display = "none";
    } else {
      isoSec.style.display = "block";
      let totalIsoRate = 0;
      let html = "";
      bp.isotopes.forEach(iso => {
        const rate = isHour ? iso.litersPerHour : iso.litersPerMin;
        totalIsoRate += rate;
        const rateStr = `${rate.toFixed(isHour ? 1 : 2)} ${fluidUnitLabel}`;
        const color = (iso.code === "T") ? "#d946ef" : ((iso.code === "O") ? "#60a5fa" : "#38bdf8");
        html += `
          <div style="display:flex; justify-content:space-between; align-items:center; background:#0f172a; border-radius:4px; padding:4px 6px; font-size:0.72rem;">
            <div style="display:flex; align-items:center; gap:6px;">
              <span class="badge" style="font-size:0.65rem; padding:1px 4px; color:${color}; border-color:${color};">${iso.code}</span>
              <span style="color:#e2e8f0; font-weight:500;">${iso.name}</span>
            </div>
            <div style="text-align:right;">
              <span style="color:${color}; font-weight:600;">${rateStr}</span>
              ${iso.totalLiters > 0 ? `<div style="font-size:0.65rem; color:var(--text-muted);">${iso.totalLiters.toLocaleString()} L total</div>` : ""}
            </div>
          </div>
        `;
      });
      isoTotalEl.innerText = `${totalIsoRate.toFixed(isHour ? 1 : 2)} ${fluidUnitLabel}`;
      isoListEl.innerHTML = html;
    }
  }

  const rawSec = document.getElementById("byproduct-raw-section");
  const rawListEl = document.getElementById("byproduct-raw-list");
  if (rawSec && rawListEl) {
    if (!bp.rawMaterials || bp.rawMaterials.length === 0) {
      rawListEl.innerHTML = `<div style="font-style:italic; color:#64748b; padding:2px 0;">No active raw material burn forecast.</div>`;
    } else {
      let html = "";
      bp.rawMaterials.forEach(r => {
        const consumed = isHour ? r.consumedPerHour : r.consumedPerMin;
        const produced = isHour ? r.producedPerHour : r.producedPerMin;
        const net = isHour ? r.netPerHour : r.netPerMin;
        const unitStr = `${r.unit}${unitLabel}`;
        const isBreeding = net > 0.001;
        const isDeficit = net < -0.001;
        const netSign = isBreeding ? "+" : "";
        const netColor = isBreeding ? "#4ade80" : (isDeficit ? "#f87171" : "#94a3b8");
        const badgeStyle = isBreeding ? "color:#34d399; border-color:#059669;" : (isDeficit ? "color:#f87171; border-color:#ef4444;" : "color:#94a3b8; border-color:#475569;");
        const tagText = isBreeding ? "BREEDING" : (isDeficit ? "DEFICIT" : "NEUTRAL");

        html += `
          <div style="display:flex; justify-content:space-between; align-items:center; background:#0f172a; border-radius:4px; padding:4px 6px; font-size:0.72rem;">
            <div style="display:flex; align-items:center; gap:6px;">
              <span class="badge" style="font-size:0.65rem; padding:1px 4px; ${badgeStyle}">${r.code}</span>
              <span style="color:#e2e8f0; font-weight:500;">${r.name}</span>
              <span style="color:var(--text-muted); font-size:0.68rem;">(Req: ${consumed.toFixed(isHour ? 1 : 2)}, Prod: ${produced.toFixed(isHour ? 1 : 2)})</span>
            </div>
            <div style="text-align:right;">
              <span style="color:${netColor}; font-weight:700;">${netSign}${net.toFixed(isHour ? 1 : 2)} ${unitStr}</span>
              <span class="badge" style="font-size:0.62rem; padding:0 3px; margin-left:4px; ${badgeStyle}">${tagText}</span>
            </div>
          </div>
        `;
      });
      rawListEl.innerHTML = html;
    }
  }
}

