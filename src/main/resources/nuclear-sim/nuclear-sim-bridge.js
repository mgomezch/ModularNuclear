/**
 * Modular Nuclear Reactor Simulation - WebAssembly Bridge
 * Provides in-browser WebAssembly execution of the core Java simulation engine
 * with exact mathematical and physical fidelity to the in-game mechanics.
 */

class NuclearSimWasm {
    constructor() {
        this.inst = null;
        this.ready = false;
        this.running = false;
        this.ticker = null;
        this.simSpeedMs = 100;
        this.onStateChange = null;
    }

    async init(wasmUrl = "nuclear-sim.wasm") {
        const importObj = {
            teavm: {
                currentTimeMillis: () => Date.now(),
                nanoTime: () => performance.now(),
                putwcharsOut: () => {},
                putwcharsErr: () => {},
                getNativeOffset: () => 0,
                logString: () => {},
                logInt: () => {},
                logOutOfMemory: () => console.error("WASM Out of Memory"),
                teavm_interrupt: () => {},
                dateToString: () => 0
            },
            teavmMath: Math,
            teavmHeapTrace: {
                allocate: () => {}, free: () => {}, assertFree: () => {}, markStarted: () => {},
                mark: () => {}, reportDirtyRegion: () => {}, markCompleted: () => {}, move: () => {},
                gcStarted: () => {}, sweepStarted: () => {}, sweepCompleted: () => {},
                defragStarted: () => {}, defragCompleted: () => {}, gcCompleted: () => {}, init: () => {}
            }
        };

        if (typeof WebAssembly.instantiateStreaming === "function") {
            try {
                const res = await fetch(wasmUrl);
                const obj = await WebAssembly.instantiateStreaming(res, importObj);
                this.inst = obj.instance;
            } catch (e) {
                const res = await fetch(wasmUrl);
                const bytes = await res.arrayBuffer();
                const obj = await WebAssembly.instantiate(bytes, importObj);
                this.inst = obj.instance;
            }
        } else {
            const res = await fetch(wasmUrl);
            const bytes = await res.arrayBuffer();
            const obj = await WebAssembly.instantiate(bytes, importObj);
            this.inst = obj.instance;
        }

        this.ready = true;
        return this;
    }

    createJavaString(str) {
        if (!str) return 0;
        const inst = this.inst;
        const stringRef = inst.exports.teavm_allocateString(str.length);
        const address = inst.exports.teavm_charArrayData(inst.exports.teavm_stringData(stringRef));
        const view = new Uint16Array(inst.exports.memory.buffer, address, str.length);
        for (let i = 0; i < str.length; i++) view[i] = str.charCodeAt(i);
        return stringRef;
    }

    readJavaString(stringRef) {
        if (!stringRef) return null;
        const inst = this.inst;
        const memory = inst.exports.memory.buffer;
        const arrayPtr = inst.exports.teavm_stringData(stringRef);
        const length = inst.exports.teavm_arrayLength(arrayPtr);
        const arrayData = new Uint16Array(memory, inst.exports.teavm_charArrayData(arrayPtr), length);
        let result = "";
        for (let i = 0; i < length; i++) result += String.fromCharCode(arrayData[i]);
        return result;
    }

    initGrid(width, height, pipeTier) {
        this.inst.exports.initGrid(width, height, pipeTier);
    }

    step() {
        this.inst.exports.step();
        const st = this.getState();
        if ((st.exploded || (st.stopOnIncidents && st.haltedByIncident)) && this.running) {
            this.setRunning(false);
        }
        if (this.onStateChange) this.onStateChange(st);
    }

    stepTicks(count = 1) {
        this.inst.exports.stepTicks(count);
        const st = this.getState();
        if ((st.exploded || (st.stopOnIncidents && st.haltedByIncident)) && this.running) {
            this.setRunning(false);
        }
        if (this.onStateChange) this.onStateChange(st);
    }

    reset() {
        this.running = false;
        if (this.ticker) {
            clearInterval(this.ticker);
            this.ticker = null;
        }
        this.inst.exports.reset();
        if (this.onStateChange) this.onStateChange(this.getState());
    }

    loadPreset(presetName) {
        this.inst.exports.loadPreset(this.createJavaString(presetName));
        if (this.onStateChange) this.onStateChange(this.getState());
    }

    loadLayout(layout, notify = true) {
        if (this.inst.exports.loadLayout) {
            this.inst.exports.loadLayout(this.createJavaString(layout));
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    getLayoutString() {
        if (this.inst.exports.getLayoutString) {
            return this.readJavaString(this.inst.exports.getLayoutString());
        }
        const st = this.getState();
        return st ? st.layout || "" : "";
    }

    setTile(x, y, type, notify = true) {
        this.inst.exports.setTile(x, y, this.createJavaString(type));
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    setPipeTier(tier, notify = true) {
        this.inst.exports.setPipeTier(tier);
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    setTurbine(material, size, fitting, notify = true) {
        this.inst.exports.setTurbine(
            this.createJavaString(material),
            this.createJavaString(size),
            this.createJavaString(fitting)
        );
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    setMachineLimits(maxThermalBoilers = -1, maxLhe = -1, maxEhe = -1, maxLst = -1, maxXlst = -1, maxXlstHp = -1, maxXlstSc = -1, maxTurbines = -1, notify = true) {
        if (this.inst.exports.setMachineLimits) {
            this.inst.exports.setMachineLimits(maxThermalBoilers, maxLhe, maxEhe, maxLst, maxXlst, maxXlstHp, maxXlstSc, maxTurbines);
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    setTicksPerCycle(ticks, notify = true) {
        if (this.inst.exports.setTicksPerCycle) {
            this.inst.exports.setTicksPerCycle(ticks);
        } else {
            this.setParam("ticksPerCycle", ticks, false);
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    setCoolingMode(mode, notify = true) {
        if (this.inst.exports.setCoolingMode) {
            this.inst.exports.setCoolingMode(this.createJavaString(mode));
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    setCoolantLoopMaterial(material, notify = true) {
        if (this.inst.exports.setCoolantLoopMaterial) {
            this.inst.exports.setCoolantLoopMaterial(this.createJavaString(material));
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    setCoolantLoopPipeSize(pipeSize, notify = true) {
        if (this.inst.exports.setCoolantLoopPipeSize) {
            this.inst.exports.setCoolantLoopPipeSize(this.createJavaString(pipeSize));
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    setCoolantLoopFluid(fluid, notify = true) {
        if (this.inst.exports.setCoolantLoopFluid) {
            this.inst.exports.setCoolantLoopFluid(this.createJavaString(fluid));
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    setCoolantLoopSecondaryCoolant(secondary, notify = true) {
        if (this.inst.exports.setCoolantLoopSecondaryCoolant) {
            this.inst.exports.setCoolantLoopSecondaryCoolant(this.createJavaString(secondary));
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    setCoolantLoopHatchTier(hatchTier, notify = true) {
        if (this.inst.exports.setCoolantLoopHatchTier) {
            this.inst.exports.setCoolantLoopHatchTier(this.createJavaString(hatchTier));
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    // Energy hatches on the pump: one draws the tier's voltage, two or more draw four times it.
    setCoolantLoopPumpHatches(count, notify = true) {
        if (this.inst.exports.setCoolantLoopPumpHatches) {
            this.inst.exports.setCoolantLoopPumpHatches(count | 0);
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    setCoolantLoopRotor(material, size, notify = true) {
        if (this.inst.exports.setCoolantLoopRotor) {
            this.inst.exports.setCoolantLoopRotor(this.createJavaString(material || ""), this.createJavaString(size || ""));
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    // Independent loops, each with its own pump and PHEs, that the passages are split among.
    setCoolantLoopCount(count, notify = true) {
        if (this.inst.exports.setCoolantLoopCount) {
            this.inst.exports.setCoolantLoopCount(count | 0);
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    // Heat exchangers in parallel on the loop, and the tier and number of energy hatches on each.
    setCoolantLoopPhe(count, hatchTier, hatchCount, notify = true) {
        if (this.inst.exports.setCoolantLoopPhe) {
            this.inst.exports.setCoolantLoopPhe(count | 0, this.createJavaString(hatchTier || ""), hatchCount | 0);
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    attachCoolantLoopPoint(x, y, notify = true) {
        if (this.inst.exports.attachCoolantLoopPoint) {
            this.inst.exports.attachCoolantLoopPoint(x, y);
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    detachCoolantLoopPoint(x, y, notify = true) {
        if (this.inst.exports.detachCoolantLoopPoint) {
            this.inst.exports.detachCoolantLoopPoint(x, y);
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    clearCoolantLoopPoints(notify = true) {
        if (this.inst.exports.clearCoolantLoopPoints) {
            this.inst.exports.clearCoolantLoopPoints();
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    setParam(key, value, notify = true) {
        this.inst.exports.setParam(
            this.createJavaString(key),
            this.createJavaString(String(value))
        );
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    repair(notify = true) {
        this.setParam("repair", "true", notify);
    }

    setReactorDamage(damage, notify = true) {
        this.setParam("damage", String(damage), notify);
    }

    setControlRod(x, y, hasRod, typeStr, insertion, notify = true) {
        if (this.inst.exports.setControlRod) {
            this.inst.exports.setControlRod(
                x,
                y,
                Boolean(hasRod),
                this.createJavaString(typeStr),
                Math.round(insertion)
            );
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    setAllControlRodsInsertion(insertion, notify = true) {
        if (this.inst.exports.setAllControlRodsInsertion) {
            this.inst.exports.setAllControlRodsInsertion(Math.round(insertion));
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    scram(notify = true) {
        if (this.inst.exports.scram) {
            this.inst.exports.scram();
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    setStrictMode(strict, notify = true) {
        if (this.inst.exports.setStrictMode) {
            this.inst.exports.setStrictMode(Boolean(strict));
        } else {
            this.setParam("strict", String(Boolean(strict)), false);
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    clearIncidentLog(notify = true) {
        if (this.inst.exports.clearIncidentLog) {
            this.inst.exports.clearIncidentLog();
        } else {
            this.setParam("clearIncidentLog", "true", false);
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    setStopOnIncidents(stop, notify = true) {
        if (this.inst.exports.setStopOnIncidents) {
            this.inst.exports.setStopOnIncidents(Boolean(stop));
        } else {
            this.setParam("stopOnIncidents", String(Boolean(stop)), false);
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    clearHaltedByIncident(notify = true) {
        if (this.inst.exports.clearHaltedByIncident) {
            this.inst.exports.clearHaltedByIncident();
        } else {
            this.setParam("clearHaltedByIncident", "true", false);
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    setAutoSupplyFuel(autoSupply, notify = true) {
        if (this.inst.exports.setAutoSupplyFuel) {
            this.inst.exports.setAutoSupplyFuel(Boolean(autoSupply));
        } else {
            this.setParam("autoSupplyFuel", String(Boolean(autoSupply)), false);
        }
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    setAutoReplaceFuel(autoReplace, notify = true) {
        this.setAutoSupplyFuel(autoReplace, notify);
    }

    setRunning(run) {
        this.running = run;
        this.inst.exports.setRunning(run);
        if (run) {
            if (!this.ticker) {
                this.ticker = setInterval(() => {
                    const st = this.getState();
                    if (!st.exploded && !(st.stopOnIncidents && st.haltedByIncident) && this.running) {
                        this.step();
                    } else if (st.exploded || (st.stopOnIncidents && st.haltedByIncident)) {
                        this.setRunning(false);
                    }
                }, this.simSpeedMs);
            }
        } else {
            if (this.ticker) {
                clearInterval(this.ticker);
                this.ticker = null;
            }
        }
    }

    togglePlay() {
        this.setRunning(!this.running);
        return this.running;
    }

    setSpeed(delayMs) {
        this.simSpeedMs = Math.max(10, delayMs);
        if (this.running) {
            clearInterval(this.ticker);
            this.ticker = setInterval(() => {
                const st = this.getState();
                if (!st.exploded && !(st.stopOnIncidents && st.haltedByIncident) && this.running) {
                    this.step();
                } else if (st.exploded || (st.stopOnIncidents && st.haltedByIncident)) {
                    this.setRunning(false);
                }
            }, this.simSpeedMs);
        }
    }

    getState() {
        const strRef = this.inst.exports.getStateJson();
        const jsonStr = this.readJavaString(strRef);
        return JSON.parse(jsonStr);
    }

    getColorMaps() {
        const strRef = this.inst.exports.getColorMapsJson();
        const jsonStr = this.readJavaString(strRef);
        return JSON.parse(jsonStr);
    }
}

if (typeof window !== "undefined") {
    window.NuclearSimWasm = NuclearSimWasm;
}
if (typeof module !== "undefined" && module.exports) {
    module.exports = NuclearSimWasm;
}
