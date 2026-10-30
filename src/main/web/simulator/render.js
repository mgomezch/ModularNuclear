function renderUI() {
  if (!currentState) return;

  // Header badges
  document.getElementById("casing-badge").innerText = currentState.pipeTierName + " (Max " + currentState.maxSafeTemp + "°C)";
  const statusBadge = document.getElementById("status-badge");
  const playBtn = document.getElementById("btn-play");
  const alertBox = document.getElementById("alert-box");

  // Damage & Maintenance indicators
  const dmgBadge = document.getElementById("damage-badge");
  const maintBadge = document.getElementById("maint-badge");
  const repairBtn = document.getElementById("btn-quick-repair");
  const currentDmg = (currentState && currentState.reactorDamage != null) ? currentState.reactorDamage : 0.0;
  const rngDmg = document.getElementById("rng-reactor-damage");
  const lblDmg = document.getElementById("lbl-reactor-damage-val");
  if (rngDmg && document.activeElement !== rngDmg) {
    rngDmg.value = currentDmg;
  }
  if (lblDmg && document.activeElement !== rngDmg) {
    lblDmg.innerText = currentDmg.toFixed(1) + "%";
  }

  if (dmgBadge) {
    if (currentDmg > 0.001) {
      dmgBadge.style.display = "inline-block";
      dmgBadge.innerText = "Damage: " + currentDmg.toFixed(1) + "%";
    } else {
      dmgBadge.style.display = "none";
    }
  }
  if (maintBadge) {
    const effPct = (currentState.maintenanceEfficiency != null ? (currentState.maintenanceEfficiency * 100).toFixed(0) : "100");
    const issues = currentState.maintenanceIssues || 0;
    maintBadge.innerText = `Maint: ${effPct}% (${issues} issues)`;
    maintBadge.style.color = (issues === 0 && currentDmg < 0.001) ? "var(--success)" : "var(--warning)";
  }
  if (repairBtn) {
    const hasDmg = currentDmg > 0.001 || (currentState.maintenanceIssues && currentState.maintenanceIssues > 0);
    repairBtn.style.display = hasDmg ? "inline-block" : "none";
    repairBtn.disabled = false;
    repairBtn.style.opacity = "1.0";
    repairBtn.style.cursor = "pointer";
    repairBtn.title = "Repair structural damage and clear maintenance issues (Reset damage to 0%)";
  }

  // Sync Simulation Options toggles
  const chkStopInc = document.getElementById("chk-stop-incidents");
  const lblStopInc = document.getElementById("lbl-stop-incidents");
  if (chkStopInc && currentState.stopOnIncidents !== undefined) {
    chkStopInc.checked = Boolean(currentState.stopOnIncidents);
    if (lblStopInc) {
      lblStopInc.innerText = chkStopInc.checked ? "(Enabled)" : "(Off)";
      lblStopInc.style.color = chkStopInc.checked ? "var(--warning)" : "var(--text-muted)";
      lblStopInc.style.fontWeight = chkStopInc.checked ? "600" : "normal";
    }
  }

  const chkAutoSupply = document.getElementById("chk-auto-supply-fuel");
  const lblAutoSupply = document.getElementById("lbl-auto-supply-fuel");
  const chkAutoRefuel = document.getElementById("chk-auto-refuel");
  const lblAutoRefuel = document.getElementById("lbl-auto-refuel");
  const autoSupplyVal = Boolean(currentState.autoSupplyFuel !== undefined ? currentState.autoSupplyFuel : currentState.autoReplaceFuel);
  if (chkAutoSupply) {
    chkAutoSupply.checked = autoSupplyVal;
    if (lblAutoSupply) {
      lblAutoSupply.innerText = autoSupplyVal ? "(Enabled)" : "(Off)";
      lblAutoSupply.style.color = autoSupplyVal ? "#34d399" : "var(--text-muted)";
      lblAutoSupply.style.fontWeight = autoSupplyVal ? "600" : "normal";
    }
  }
  if (chkAutoRefuel) {
    chkAutoRefuel.checked = autoSupplyVal;
    if (lblAutoRefuel) {
      lblAutoRefuel.innerText = autoSupplyVal ? "Enabled" : "Disabled";
      lblAutoRefuel.style.color = autoSupplyVal ? "#34d399" : "var(--text-muted)";
    }
  }

  // Update Control Rods subsystem card
  let controlRodHatchesCount = (currentState.controlRodCount !== undefined) ? currentState.controlRodCount : 0;
  let totalRodAbsorbed = 0;
  let totalRodInsertion = 0;
  if (currentState.tiles) {
    let counted = 0;
    currentState.tiles.forEach(t => {
      if (t.hasControlRod) {
        counted++;
        totalRodAbsorbed += (t.controlRodFastAbsorbed || 0) + (t.controlRodThermalAbsorbed || 0);
        totalRodInsertion += (t.controlRodInsertion || 0);
      }
    });
    if (controlRodHatchesCount === 0) controlRodHatchesCount = counted;
  }
  const avgRodIns = controlRodHatchesCount > 0 ? Math.round(totalRodInsertion / controlRodHatchesCount) : 0;
  const rodsBadge = document.getElementById("rods-count-badge");
  if (rodsBadge) rodsBadge.innerText = `${controlRodHatchesCount} Hatches`;
  const lblGlobalRod = document.getElementById("lbl-global-rod-ins");
  if (lblGlobalRod) lblGlobalRod.innerText = `${avgRodIns}%`;
  const rngGlobalRod = document.getElementById("rng-global-rod-ins");
  if (rngGlobalRod && document.activeElement !== rngGlobalRod) rngGlobalRod.value = avgRodIns;
  const rodsAbsVal = document.getElementById("rods-total-abs-val");
  if (rodsAbsVal) rodsAbsVal.innerText = `${totalRodAbsorbed.toLocaleString()} n/s`;

  if (currentState.exploded) {
    if (isWasmMode && wasmSim && wasmSim.running) {
      wasmSim.setRunning(false);
    }
    statusBadge.innerText = "MELTDOWN / EXPLODED - STOPPED";
    statusBadge.style.color = "var(--danger)";
    playBtn.innerText = "▶ Start Simulation";
    playBtn.disabled = true;
    alertBox.className = "alert-danger flash";
    alertBox.style.display = "block";
    alertBox.innerHTML = `<strong>💥 MELTDOWN / CATASTROPHIC FAILURE:</strong> ${currentState.explosionReason} <span style="display:block; margin-top:4px; font-size:0.8rem; font-weight:normal;">Simulation automatically halted due to core destruction. Click ↺ Reset to restart.</span>`;
  } else if (currentState.structureErrors && currentState.structureErrors.length > 0) {
    if (isWasmMode && wasmSim && wasmSim.running) {
      wasmSim.setRunning(false);
    }
    statusBadge.innerText = "STRUCTURE INVALID - NOT FORMED";
    statusBadge.style.color = "var(--danger)";
    playBtn.disabled = true;
    playBtn.innerText = "▶ Start Simulation";
    alertBox.className = "alert-danger";
    alertBox.style.display = "block";
    const items = currentState.structureErrors.map(e => `<li>${e}</li>`).join("");
    alertBox.innerHTML = `<strong>🚧 THE GAME WOULD NOT FORM THIS REACTOR:</strong><ul style="margin:4px 0 0 18px; font-weight:normal;">${items}</ul>`;
  } else if (currentState.haltedByIncident) {
    if (isWasmMode && wasmSim && wasmSim.running) {
      wasmSim.setRunning(false);
    }
    statusBadge.innerText = "INCIDENT PAUSE";
    statusBadge.style.color = "var(--warning)";
    playBtn.disabled = false;
    playBtn.innerText = "▶ Resume Simulation";
    alertBox.className = "alert-warning flash";
    alertBox.style.display = "block";
    const reason = currentState.lastHaltIncidentReason || "Bad reactor condition detected";
    alertBox.innerHTML = `<strong>🛑 SIMULATION PAUSED ON PENDING INCIDENT:</strong> ${reason} <span style="display:block; margin-top:4px; font-size:0.8rem; font-weight:normal;">Halted by "Stop on Incidents" option. Click <strong>▶ Resume Simulation</strong> to continue, <strong>Step +1</strong> to advance tick-by-tick, or use the <strong>Damage Slider / 🔧 Repair</strong> to restore the core.</span>`;
  } else if (currentState.reactorDamage && currentState.reactorDamage > 0) {
    // Active structural damage warning: flashes alert banner, but fission continues
    const latestInc = (currentState.incidentLog && currentState.incidentLog.length > 0)
      ? currentState.incidentLog[currentState.incidentLog.length - 1]
      : null;
    const incidentMsg = latestInc ? ` [Tick ${latestInc.tick}: ${latestInc.message}]` : "";
    const isNewAlert = (window.__lastAlertTick !== currentState.lastIncidentTick);
    window.__lastAlertTick = currentState.lastIncidentTick;
    alertBox.className = "alert-warning" + (isNewAlert ? " flash" : "");
    alertBox.style.display = "block";
    const effPct = (currentState.maintenanceEfficiency != null ? (currentState.maintenanceEfficiency * 100).toFixed(0) : "100");
    alertBox.innerHTML = `<strong>⚠️ REACTOR DAMAGE DETECTED (${currentState.reactorDamage.toFixed(1)}% Structural Damage, Maint: ${effPct}%):</strong> Operating with maintenance penalties.${incidentMsg} <button type="button" onclick="quickRepair()" style="margin-left:8px; padding:2px 8px; font-size:0.75rem; background:#1e293b; border:1px solid var(--warning); color:var(--warning); cursor:pointer; border-radius:4px;">🔧 Repair Now</button>`;

    playBtn.disabled = false;
    if (currentState.isRunning) {
      statusBadge.innerText = `DAMAGED (${currentState.reactorDamage.toFixed(1)}%) - RUNNING`;
      statusBadge.style.color = "var(--warning)";
      playBtn.innerText = "⏸ Pause Simulation";
    } else {
      statusBadge.innerText = `DAMAGED (${currentState.reactorDamage.toFixed(1)}%) - PAUSED`;
      statusBadge.style.color = "var(--warning)";
      playBtn.innerText = "▶ Resume Simulation";
    }
  } else {
    alertBox.className = "";
    alertBox.style.display = "none";
    playBtn.disabled = false;
    if (currentState.isRunning) {
      statusBadge.innerText = "SIMULATION RUNNING";
      statusBadge.style.color = "var(--accent)";
      playBtn.innerText = "⏸ Pause Simulation";
    } else {
      statusBadge.innerText = "SIMULATION PAUSED";
      statusBadge.style.color = "var(--success)";
      playBtn.innerText = "▶ Resume Simulation";
    }
  }

  // Render Incident & Event Log
  const incidentListEl = document.getElementById("incident-log-list");
  const incidentBadgeEl = document.getElementById("incident-count-badge");
  if (incidentListEl && currentState.incidentLog) {
    const count = currentState.incidentLog.length;
    if (incidentBadgeEl) {
      incidentBadgeEl.innerText = `${count} event${count === 1 ? "" : "s"}`;
      incidentBadgeEl.style.color = count > 0 ? "var(--warning)" : "var(--text-muted)";
      incidentBadgeEl.style.borderColor = count > 0 ? "var(--warning)" : "var(--border-color)";
    }
    if (count === 0) {
      incidentListEl.innerHTML = `<div style="font-style:italic; font-size:0.75rem; color:#64748b; padding:4px 6px;">No incidents recorded. Chamber operating nominally.</div>`;
    } else {
      let html = "";
      for (let i = count - 1; i >= 0; i--) {
        const ev = currentState.incidentLog[i];
        let cls = "";
        let badgeColor = "#94a3b8";
        let badgeBg = "#1e293b";
        switch (ev.type) {
          case "THERMAL_SHOCK":
            cls = "thermal-shock";
            badgeColor = "#f59e0b";
            badgeBg = "rgba(245,158,11,0.2)";
            break;
          case "CASING_OVERHEAT":
            cls = "casing-overheat";
            badgeColor = "#ef4444";
            badgeBg = "rgba(239,68,68,0.2)";
            break;
          case "FUEL_BURNED":
            cls = "fuel-burned";
            badgeColor = "#dc2626";
            badgeBg = "rgba(220,38,38,0.2)";
            break;
          case "HATCH_VOIDED":
            cls = "hatch-voided";
            badgeColor = "#38bdf8";
            badgeBg = "rgba(56,189,248,0.2)";
            break;
          case "MELTDOWN":
            cls = "meltdown";
            badgeColor = "#f87171";
            badgeBg = "rgba(185,28,28,0.3)";
            break;
          case "PENDING_INCIDENT":
            cls = "pending-incident";
            badgeColor = "#f59e0b";
            badgeBg = "rgba(245,158,11,0.25)";
            break;
          case "REPAIR":
            cls = "repair";
            badgeColor = "#34d399";
            badgeBg = "rgba(16,185,129,0.2)";
            break;
          default:
            cls = "";
        }
        html += `<div class="incident-entry ${cls}">
          <span class="incident-tick">[Tick ${ev.tick}]</span>
          <span class="incident-badge-type" style="color:${badgeColor}; background:${badgeBg};">${ev.type}</span>
          <span style="flex:1;">${ev.message}</span>
          ${ev.damage > 0 ? `<span style="color:#f87171; font-weight:bold;">+${ev.damage.toFixed(1)}% dmg</span>` : ""}
        </div>`;
      }
      incidentListEl.innerHTML = html;
    }
  }

  // Telemetry
  document.getElementById("stat-tick").innerText = currentState.currentTick;
  document.getElementById("stat-max-temp").innerText = currentState.coreMaxTemp + " °C";
  document.getElementById("stat-avg-temp").innerText = currentState.coreAvgTemp + " °C";
  document.getElementById("stat-eff").innerText = (currentState.efficiency * 100).toFixed(1) + " %";
  document.getElementById("stat-neutrons").innerText = currentState.lastNeutrons + " / tick";
  document.getElementById("stat-thermal").innerText = currentState.thermalAbsorbed + " / tick";
  document.getElementById("stat-fast").innerText = currentState.fastAbsorbed + " / tick";
  document.getElementById("stat-steam").innerText = currentState.totalSteam.toLocaleString() + " L";
  document.getElementById("stat-deuterium").innerText = currentState.totalDeuterium.toLocaleString() + " L";
  document.getElementById("stat-tritium").innerText = currentState.totalTritium.toLocaleString() + " L";
  const oxEl = document.getElementById("stat-oxygen");
  if (oxEl) oxEl.innerText = (currentState.totalOxygen ? currentState.totalOxygen.toLocaleString() : "0") + " L";

  // Turbine config sync
  const activeEl = document.activeElement;
  const turbMat = document.getElementById("turbine-mat");
  if (turbMat && currentState.turbineMaterials) {
    const matKeys = currentState.turbineMaterials.map(m => m.id).join(",");
    if (turbMat.dataset.lastKeys !== matKeys) {
      turbMat.dataset.lastKeys = matKeys;
      const cur = turbMat.value;
      turbMat.innerHTML = "";
      currentState.turbineMaterials.forEach(m => {
        const opt = document.createElement("option");
        opt.value = m.id;
        opt.innerText = m.name;
        turbMat.appendChild(opt);
      });
      if (cur) turbMat.value = cur;
    }
  }
  if (activeEl !== turbMat && currentState.turbineMaterial) {
    const target = currentState.turbineMaterial;
    const opt = Array.from(turbMat.options).find(o => o.value.toLowerCase() === target.toLowerCase() || o.value.replace(/_/g,"").toLowerCase() === target.replace(/_/g,"").toLowerCase());
    if (opt && turbMat.value !== opt.value) {
      turbMat.value = opt.value;
    }
  }
  const turbSize = document.getElementById("turbine-size");
  if (activeEl !== turbSize && currentState.turbineSize && turbSize.value !== currentState.turbineSize) {
    turbSize.value = currentState.turbineSize;
  }
  const turbFitting = document.getElementById("turbine-fitting");
  if (activeEl !== turbFitting && currentState.turbineFitting && turbFitting.value !== currentState.turbineFitting) {
    turbFitting.value = currentState.turbineFitting;
  }
  const tierSelect = document.getElementById("tier-select");
  if (activeEl !== tierSelect && currentState.pipeTier !== undefined && parseInt(tierSelect.value) !== currentState.pipeTier) {
    tierSelect.value = currentState.pipeTier;
  }

  // Coolant loop subsystem synchronization
  const loopCard = document.getElementById("coolant-loop-card");
  if (loopCard) {
    loopCard.style.display = "block";
  }
  const tierWarn = document.getElementById("loop-tier-warning");
  if (tierWarn) {
    tierWarn.style.display = currentState.isTier2ConvectiveAllowed ? "none" : "block";
  }

  const cl = currentState.coolantLoop;
  if (cl) {
    const matSelect = document.getElementById("loop-material-select");
    if (matSelect && cl.allowedMaterials) {
      const matKeys = cl.allowedMaterials.map(m => m.id).join(",");
      if (matSelect.dataset.lastKeys !== matKeys) {
        matSelect.dataset.lastKeys = matKeys;
        matSelect.innerHTML = "";
        cl.allowedMaterials.forEach(m => {
          const opt = document.createElement("option");
          opt.value = m.id;
          opt.innerText = `${m.name} (${m.tier}, max ${m.maxPressure} bar)`;
          matSelect.appendChild(opt);
        });
      }
      if (activeEl !== matSelect && cl.material && matSelect.value !== cl.material) {
        matSelect.value = cl.material;
      }
    }

    const sizeSel = document.getElementById("loop-size-select");
    if (sizeSel && activeEl !== sizeSel && sizeSel.value !== cl.pipeSize) sizeSel.value = cl.pipeSize;
    const fluidSel = document.getElementById("loop-fluid-select");
    if (fluidSel && activeEl !== fluidSel && fluidSel.value !== cl.fluid) fluidSel.value = cl.fluid;
    const secSel = document.getElementById("loop-secondary-select");
    if (secSel && cl.secondaryCoolants) {
      const secKeys = cl.secondaryCoolants.map(m => m.id).join(",");
      if (secSel.dataset.lastKeys !== secKeys) {
        secSel.dataset.lastKeys = secKeys;
        const cur = secSel.value;
        secSel.innerHTML = "";
        cl.secondaryCoolants.forEach(sc => {
          const opt = document.createElement("option");
          opt.value = sc.id;
          opt.innerText = sc.name;
          secSel.appendChild(opt);
        });
        if (cur) secSel.value = cur;
      }
    }
    if (secSel && activeEl !== secSel && cl.secondaryCoolant && secSel.value !== cl.secondaryCoolant) {
      secSel.value = cl.secondaryCoolant;
    }
    const hatchSel = document.getElementById("loop-hatch-tier-select");
    if (hatchSel && activeEl !== hatchSel && cl.hatchTier && hatchSel.value !== cl.hatchTier) {
      hatchSel.value = cl.hatchTier;
    }
    const syncSel = (id, v) => {
      const el = document.getElementById(id);
      if (el && activeEl !== el && v !== undefined && v !== null && String(el.value) !== String(v)) el.value = String(v);
    };
    const rotorSel = document.getElementById("loop-rotor-select");
    if (rotorSel && cl.rotorMaterials) {
      const keys = cl.rotorMaterials.map(m => m.id + m.efficiency).join(",");
      if (rotorSel.dataset.lastKeys !== keys) {
        rotorSel.dataset.lastKeys = keys;
        rotorSel.innerHTML = "";
        cl.rotorMaterials.forEach(m => {
          const opt = document.createElement("option");
          opt.value = m.id;
          opt.innerText = `${m.name} (${(m.efficiency * 100).toFixed(0)}% eff)`;
          rotorSel.appendChild(opt);
        });
      }
    }
    syncSel("loop-rotor-select", cl.rotorMaterial);
    syncSel("loop-rotor-size-select", cl.rotorSize);
    syncSel("loop-pump-hatches-select", cl.pumpHatchCount >= 2 ? 2 : 1);
    syncSel("loop-phe-count-input", cl.pheCount);
    syncSel("loop-count-input", cl.loopCount);
    syncSel("loop-duty-cycle-input", Math.round(cl.dutyCyclePercent));
    syncSel("loop-max-flow-input", cl.maxFlowRateLimit > 0 ? cl.maxFlowRateLimit : "");
    syncSel("loop-max-pressure-input", cl.maxPressureLimit > 0 ? cl.maxPressureLimit : "");
    syncSel("loop-band-sensor-select", cl.tempSensor);
    if (cl.tempBandEnabled) {
      syncSel("loop-band-min-input", cl.tempBandMin);
      syncSel("loop-band-max-input", cl.tempBandMax);
    }
    const bandCheck = document.getElementById("loop-band-check");
    if (bandCheck && activeEl !== bandCheck) bandCheck.checked = !!cl.tempBandEnabled;
    const circSel = document.getElementById("loop-circuit-select");
    if (circSel && cl.circuits) {
      const keys = cl.circuits.map(c => c.code + c.fluid).join(",");
      if (circSel.dataset.lastKeys !== keys) {
        circSel.dataset.lastKeys = keys;
        circSel.innerHTML = "";
        cl.circuits.forEach(c => {
          const opt = document.createElement("option");
          opt.value = c.index;
          opt.innerText = `Circuit ${c.index + 1} (${c.code}) · ${c.fluid}`;
          circSel.appendChild(opt);
        });
      }
      syncSel("loop-circuit-select", cl.selectedCircuit);
    }
    const circTable = document.getElementById("loop-circuits-table");
    if (circTable && cl.circuits) {
      circTable.innerHTML = cl.circuits.length < 2 && !cl.limitReason ? "" :
        `<table style="width:100%; border-collapse:collapse;"><tr style="color:var(--text-muted);"><td>Circuit</td><td>Cells</td><td>Flow</td><td>Cold → Hot</td><td>Heat</td><td>Pump on</td></tr>` +
        cl.circuits.map(c => `<tr${c.index === cl.selectedCircuit ? ' style="color:#00d2ff;"' : ''}><td>${c.code} ${c.fluid}</td><td>${c.passages}</td><td>${Math.round(c.flowLPerSec)} L/s</td><td>${Math.round(c.coldLegC)} → ${Math.round(c.hotLegC)} °C</td><td>${Math.round(c.heatHut).toLocaleString()} HU/t</td><td>${Math.round(c.pumpOn * 100)}%</td></tr>`).join("") +
        `</table>` + (cl.limitReason ? `<div style="color:#fde047; margin-top:4px;">⚙️ Pump switched off by ${cl.limitReason} (${Math.round(cl.pumpOnFraction * 100)}% of ticks on)</div>` : "");
    }
    syncSel("loop-phe-hatch-tier-select", cl.pheHatchTier);
    syncSel("loop-phe-hatches-select", cl.pheHatchCount >= 2 ? 2 : 1);

    // Status Badge
    const statusBadge = document.getElementById("loop-status-badge");
    if (statusBadge) {
      if (cl.ruptured) {
        statusBadge.innerText = "💥 RUPTURED";
        statusBadge.style.color = "var(--danger)";
      } else if (cl.currentCoolantTempCelsius > cl.maxTempC) {
        statusBadge.innerText = "🔥 MELTED";
        statusBadge.style.color = "var(--danger)";
      } else if (cl.maxFlowLPerSec > 0 && cl.currentFlowRateLPerSec >= cl.maxFlowLPerSec * 0.999) {
        statusBadge.innerText = "⚙️ PIPE CAPACITY";
        statusBadge.style.color = "#38bdf8";
      } else if (cl.currentPressureBar > cl.maxPressureBar * 0.85) {
        statusBadge.innerText = "⚠️ HIGH PRESSURE";
        statusBadge.style.color = "var(--warning)";
      } else {
        statusBadge.innerText = "NORMAL";
        statusBadge.style.color = "var(--success)";
      }
    }

    const flowEl = document.getElementById("loop-flow-val");
    if (flowEl) {
      let flowTxt = `${cl.currentFlowRateLPerSec.toFixed(1)} / ${Math.round(cl.maxFlowLPerSec).toLocaleString()} L/s`;
      flowEl.innerText = flowTxt;
    }

    const pressEl = document.getElementById("loop-pressure-val");
    const pressRatio = cl.maxPressureBar > 0 ? (cl.currentPressureBar / cl.maxPressureBar) : 0;
    if (pressEl) pressEl.innerText = `${cl.currentPressureBar.toFixed(1)} / ${cl.maxPressureBar.toFixed(1)} bar (${(pressRatio * 100).toFixed(0)}%)`;

    const pressBar = document.getElementById("loop-pressure-bar");
    if (pressBar) {
      const pct = Math.min(100, Math.max(0, pressRatio * 100));
      pressBar.style.width = pct + "%";
      pressBar.style.background = pct > 90 ? "#ef4444" : (pct > 75 ? "#f59e0b" : "#22c55e");
    }

    const tempEl = document.getElementById("loop-temp-val");
    if (tempEl) tempEl.innerText = `${cl.coldLegTempCelsius.toFixed(1)} → ${cl.currentCoolantTempCelsius.toFixed(1)} °C (Max: ${cl.maxTempC.toFixed(0)} °C)`;

    const heatEl = document.getElementById("loop-heat-val");
    if (heatEl) {
      const mw = (cl.lastHeatExtractedWatts || 0) / 1000000;
      const hut = cl.lastHeatExtractedHut || 0;
      heatEl.innerText = `${Math.round(hut).toLocaleString()} Hu/s (${mw.toFixed(2)} MWth)`;
    }

    const steamEl = document.getElementById("loop-steam-val");
    if (steamEl) {
      const grades = [];
      if (cl.lastSteamLt > 0) grades.push(`steam ${Math.round(cl.lastSteamLt).toLocaleString()}`);
      if (cl.lastSuperheatedSteamLt > 0) grades.push(`superheated ${Math.round(cl.lastSuperheatedSteamLt).toLocaleString()}`);
      if (cl.lastSupercriticalSteamLt > 0) grades.push(`supercritical ${Math.round(cl.lastSupercriticalSteamLt).toLocaleString()}`);
      steamEl.innerText = `${Math.round(cl.lastSecondarySteamProducedLt).toLocaleString()} L/t` + (grades.length ? ` (${grades.join(", ")})` : "");
    }

    const pumpEl = document.getElementById("loop-pump-val");
    if (pumpEl) {
      let pText = `-${Math.round(cl.lastPumpPowerEUt).toLocaleString()} EU/t pumps, -${Math.round(cl.lastPhePowerEUt).toLocaleString()} EU/t PHEs`;
      pText += ` (${(cl.pumpMechanicalPowerW / 1000).toFixed(0)} kW into the coolant · rotor ${Math.round((cl.rotorEfficiency || 1.0) * 100)}%)`;
      pumpEl.innerText = pText;
    }

    const ptsEl = document.getElementById("loop-points-val");
    if (ptsEl) ptsEl.innerText = `${cl.attachedPoints ? cl.attachedPoints.length : 0} cell(s) connected`;

    const bypEl = document.getElementById("loop-byproduct-val");
    if (bypEl) {
      if (cl.fluid !== "DISTILLED_WATER" && cl.fluid !== "HEAVY_WATER") {
        bypEl.innerText = "None (no radiolysis)";
      } else {
        let bypStr = `${cl.totalDeuteriumProduced.toLocaleString()} L D₂ | ${cl.totalTritiumProduced.toLocaleString()} L T₂`;
        if (cl.totalOxygenProduced && cl.totalOxygenProduced > 0) {
          bypStr += ` | ${cl.totalOxygenProduced.toLocaleString()} L O₂`;
        }
        bypEl.innerText = bypStr;
      }
    }

    const warnEl = document.getElementById("loop-tier-warning");
    if (warnEl) {
      warnEl.style.display = (!currentState.isTier2ConvectiveAllowed) ? "block" : "none";
    }
  }

  // Turbine telemetry
  if (currentState.powerEstimate) {
    const pe = currentState.powerEstimate;
    let pwrDisplay = formatPowerAndAmps(pe.totalPowerEUt, false);
    if (currentState.pumpPowerEUt && currentState.pumpPowerEUt > 0) {
      pwrDisplay += ` [Gross: ${Math.round(currentState.grossPowerEUt).toLocaleString()} - Pump: ${Math.round(currentState.pumpPowerEUt).toLocaleString()}]`;
    }
    document.getElementById("stat-power-tier").innerText = formatPowerAndAmps(pe.totalPowerEUt, false);
    document.getElementById("stat-power").innerText = pwrDisplay;
    document.getElementById("turb-eff").innerText = (pe.efficiency * 100).toFixed(1) + " %";
    document.getElementById("turb-opt-flow").innerText = Math.round(pe.optFlowPerTurbine).toLocaleString() + " L/t";
    document.getElementById("turb-sc-pwr").innerText = formatPowerAndAmps(pe.xlstScPowerEUt, true) + " (" + pe.xlstScTurbinesNeeded.toFixed(2) + " units)";
    document.getElementById("turb-hp-pwr").innerText = formatPowerAndAmps(pe.xlstHpPowerEUt, true) + " (" + pe.xlstHpTurbinesNeeded.toFixed(2) + " units)";
    document.getElementById("turb-reg-pwr").innerText = formatPowerAndAmps(pe.xlstPowerEUt, true) + " (" + (pe.isLST ? pe.lstTurbinesNeeded : pe.xlstTurbinesNeeded).toFixed(2) + " units)";

    const eheRow = document.getElementById("ehe-row");
    if (pe.coolantMachine && pe.coolantMachine !== "None" && pe.coolantMachineMode && pe.coolantMachineMode !== "NONE") {
      eheRow.style.display = "flex";
      document.getElementById("turb-ehe-info").innerText = pe.coolantMachine + " [" + pe.coolantMachineMode + "] (" + Math.round(pe.coolantSteamProduced).toLocaleString() + " L/t stm, " + Math.round(pe.coolantWaterConsumed).toLocaleString() + " L/t water)";
    } else {
      eheRow.style.display = "none";
    }

    document.getElementById("turb-flows").innerText =
      `Reg: ${Math.round(currentState.flowRegularSteam || 0)} | SH: ${Math.round(currentState.flowSuperheatedSteam || 0)} | SC: ${Math.round(currentState.flowSupercriticalSteam || 0)} | HW: ${Math.round(currentState.flowHeavyWaterSteam || 0)}`;
  } else {
    const power = (currentState.history && currentState.history.length > 0)
      ? currentState.history[currentState.history.length - 1].power
      : 0;
    document.getElementById("stat-power-tier").innerText = formatPowerAndAmps(power, false);
    document.getElementById("stat-power").innerText = formatPowerAndAmps(power, false);
  }

  // Update Scenario comparison card
  if (currentState.scenarios) {
    const sc = currentState.scenarios;
    if (sc.tight) {
      document.getElementById("scen-a-desc").innerText = sc.tight.description || ((sc.tight.sizeName || sc.tight.size) + " " + (sc.tight.materialName || sc.tight.material));
      document.getElementById("scen-a-pwr").innerText = formatPowerAndAmps(sc.tight.powerEUt, true);
      document.getElementById("scen-a-turb").innerText = sc.tight.totalTurbinesNeeded.toFixed(2) + " units";
      document.getElementById("scen-a-mach").innerText = sc.tight.coolantMachine !== "None" ? (sc.tight.coolantMachineCount.toFixed(0) + "x " + sc.tight.coolantMachine) : "None";
    }
    if (sc.loose) {
      document.getElementById("scen-b-desc").innerText = sc.loose.description || ((sc.loose.sizeName || sc.loose.size) + " " + (sc.loose.materialName || sc.loose.material));
      document.getElementById("scen-b-pwr").innerText = formatPowerAndAmps(sc.loose.powerEUt, true);
      document.getElementById("scen-b-turb").innerText = sc.loose.totalTurbinesNeeded.toFixed(2) + " units";
      document.getElementById("scen-b-mach").innerText = sc.loose.coolantMachine !== "None" ? (sc.loose.coolantMachineCount.toFixed(0) + "x " + sc.loose.coolantMachine) : "None";
    }
    if (sc.tight && sc.loose) {
      const ratio = sc.loose.totalTurbinesNeeded > 0 ? (sc.tight.totalTurbinesNeeded / sc.loose.totalTurbinesNeeded).toFixed(1) : "1.0";
      const pwrPct = sc.tight.powerEUt > 0 ? ((sc.loose.powerEUt / sc.tight.powerEUt) * 100).toFixed(1) : "100.0";
      document.getElementById("scen-compare-text").innerText = `Loose mode uses ${ratio}x fewer turbines for ${pwrPct}% of Tight power output.`;
    }
  }

  // Render Machine Process Pipeline
  renderProcessPipeline();

  // Render Fuel & Coolant Byproducts
  renderByproducts();

  // Reconcile and update grid DOM without destroying elements
  updateGridDOM();

  if (selectedTilePos) {
    const tile = currentState.tiles.find(t => t.x === selectedTilePos.x && t.y === selectedTilePos.y);
    if (tile) renderInspector(tile);
  }

  if (currentState.params) {
    syncParamsToUI(currentState.params);
  }

  renderChart();
}

