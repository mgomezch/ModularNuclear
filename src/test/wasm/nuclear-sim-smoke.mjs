// Smoke test for the TeaVM-compiled simulator: loads nuclear-sim.wasm through the real browser bridge and calls every
// bridge entry point the web page uses at start-up, then steps a small reactor. Any WebAssembly trap (for example a
// call into a Java method TeaVM did not compile) fails the run.
//
// Usage: node nuclear-sim-smoke.mjs <dist-dir>
import { readFileSync } from "node:fs";
import { join } from "node:path";

const dist = process.argv[2];
if (!dist) {
  console.error("usage: node nuclear-sim-smoke.mjs <dist-dir>");
  process.exit(2);
}

globalThis.fetch = async (url) => {
  const bytes = readFileSync(join(dist, url));
  return { arrayBuffer: async () => bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.length) };
};
WebAssembly.instantiateStreaming = undefined;

const NuclearSimWasm = new Function(readFileSync(join(dist, "nuclear-sim-bridge.js"), "utf8") + "\nreturn NuclearSimWasm;")();
const sim = await new NuclearSimWasm().init("nuclear-sim.wasm");

const checks = [
  ["initGrid", () => sim.initGrid(5, 5, 1)],
  ["setCoolingMode", () => sim.setCoolingMode("MODULAR", false)],
  ["setCoolantLoopMaterial", () => sim.setCoolantLoopMaterial("Titanium", false)],
  ["setCoolantLoopPipeSize", () => sim.inst.exports.setCoolantLoopPipeSize(sim.createJavaString("NORMAL"))],
  ["setCoolantLoopFluid", () => sim.inst.exports.setCoolantLoopFluid(sim.createJavaString("distilled water"))],
  ["setCoolantLoopHatchTier", () => sim.inst.exports.setCoolantLoopHatchTier(sim.createJavaString("EV"))],
  ["setCoolantLoopSecondaryCoolant", () => sim.inst.exports.setCoolantLoopSecondaryCoolant(sim.createJavaString("ic2distilledwater"))],
  ["setCoolantLoopRotor", () => sim.setCoolantLoopRotor("Tungstensteel", "HUGE", false)],
  ["setCoolantLoopPumpHatches", () => sim.setCoolantLoopPumpHatches(2, false)],
  ["setCoolantLoopPhe", () => sim.setCoolantLoopPhe(2, "LuV", 1, false)],
  ["setTurbine", () => sim.setTurbine("Oriharukon", "NORMAL", "TIGHT", false)],
  ["loadLayout", () => sim.loadLayout("NL,RS,M4,RS,NL;RS,M4,HC,M4,RS;M4,HC,M4,HC,M4;RS,M4,HC,M4,RS;NL,RS,M4,RS,NL", false)],
  ["stepTicks", () => sim.stepTicks(100)],
];

let failed = 0;
for (const [name, fn] of checks) {
  try {
    fn();
    console.log(`ok   ${name}`);
  } catch (err) {
    failed++;
    console.log(`FAIL ${name}: ${String(err).split("\n")[0]}`);
  }
}

if (!failed) {
  const st = sim.getState();
  const expected = "NL,RS,M4,RS,NL;RS,M4,HC,M4,RS;M4,HC,M4,HC,M4;RS,M4,HC,M4,RS;NL,RS,M4,RS,NL";
  if (st.layout !== expected) {
    failed++;
    console.log(`FAIL layout round-trip: got ${st.layout}`);
  } else if (!(st.currentTick > 0) || st.exploded) {
    failed++;
    console.log(`FAIL simulation did not advance cleanly (tick ${st.currentTick}, exploded ${st.exploded})`);
  } else {
    console.log(`ok   state (tick ${st.currentTick}, core max ${st.coreMaxTemp} °C)`);
  }
}

// Every calibrated design embedded in the page loads from its simulator options and runs. Calibrated designs run
// hot and flux-heavy, which reaches code (overheating, radiovoltaics) the small reactor above never does.
if (!failed) {
  const html = readFileSync(join(dist, "index.html"), "utf8");
  const m = html.match(/window\.__CEILING_PRESETS__\s*=\s*(\[[\s\S]*?\]);\s*\n/);
  const presets = m ? JSON.parse(m[1]) : [];
  for (const p of presets) {
    if (!p.sim_options) continue;
    try {
      sim.loadRunOptions(p.sim_options, false);
      sim.stepTicks(300);
      const st = sim.getState();
      if (st.exploded || !(st.grossPowerEUt > 0)) {
        failed++;
        console.log(`FAIL preset ${p.tier}: power ${st.grossPowerEUt}, exploded ${st.exploded}, ${st.powerFailReason || ""}`);
      } else {
        console.log(`ok   preset ${p.tier} (${Math.round(st.grossPowerEUt)} EU/t)`);
      }
    } catch (err) {
      failed++;
      console.log(`FAIL preset ${p.tier}: ${String(err).split("\n")[0]}`);
    }
  }
}

// A convective loop on an LuV (Osmium) grid runs the shared coolant loop engine
if (!failed) {
  try {
    sim.initGrid(5, 5, 2);
    sim.setCoolingMode("CONVECTIVE_LOOP", false);
    sim.loadLayout("U4,M4,U4,M4,U4;M4,U4,CP,U4,M4;U4,CP,U4,CP,U4;M4,U4,CP,U4,M4;U4,M4,U4,M4,U4", false);
    sim.stepTicks(400);
    const cl = sim.getState().coolantLoop;
    if (!cl || cl.ruptured || !(cl.currentFlowRateLPerSec > 0) || !(cl.lastHeatExtractedHut > 0)) {
      failed++;
      console.log(`FAIL coolant loop: ${JSON.stringify(cl && { flow: cl.currentFlowRateLPerSec, heat: cl.lastHeatExtractedHut, ruptured: cl.ruptured, reason: cl.ruptureReason })}`);
    } else {
      console.log(`ok   coolant loop (${cl.currentFlowRateLPerSec} L/s, ${cl.coldLegTempCelsius} -> ${cl.currentCoolantTempCelsius} °C, ${cl.lastHeatExtractedHut} HU/t)`);
    }
  } catch (err) {
    failed++;
    console.log(`FAIL coolant loop: ${String(err).split("\n")[0]}`);
  }
}

process.exit(failed ? 1 : 0);
