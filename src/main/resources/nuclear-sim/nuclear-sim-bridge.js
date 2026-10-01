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
        if (this.onStateChange) this.onStateChange(this.getState());
    }

    stepTicks(count = 1) {
        this.inst.exports.stepTicks(count);
        if (this.onStateChange) this.onStateChange(this.getState());
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

    setParam(key, value, notify = true) {
        this.inst.exports.setParam(
            this.createJavaString(key),
            this.createJavaString(String(value))
        );
        if (notify && this.onStateChange) this.onStateChange(this.getState());
    }

    setRunning(run) {
        this.running = run;
        this.inst.exports.setRunning(run);
        if (run) {
            if (!this.ticker) {
                this.ticker = setInterval(() => {
                    const st = this.getState();
                    if (!st.exploded && this.running) {
                        this.step();
                    } else if (st.exploded) {
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
                if (!st.exploded && this.running) {
                    this.step();
                } else if (st.exploded) {
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
