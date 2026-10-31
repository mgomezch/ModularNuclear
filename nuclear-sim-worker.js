/**
 * Modular Nuclear Reactor Simulation - Web Worker host
 * Runs NuclearSimWasm in a Web Worker so stepping the reactor never blocks the page. The same file is both the worker
 * (new Worker("nuclear-sim-worker.js")) and, loaded with a <script> tag, the page side NuclearSimWorkerClient.
 *
 * The client has the bridge's method names, so page code written against NuclearSimWasm keeps working: setters are
 * queued and sent to the worker in one batch per task, getState() returns the latest snapshot the worker sent, and
 * onStateChange fires when a new one arrives. The worker serializes the state only when the page asks for one (after
 * it has drawn the previous snapshot), so a slow page never makes the worker build snapshots nobody draws.
 */

const IS_SIM_WORKER = typeof WorkerGlobalScope !== "undefined" && self instanceof WorkerGlobalScope;

if (IS_SIM_WORKER) {
    importScripts("nuclear-sim-bridge.js");

    const sim = new NuclearSimWasm();
    let ticker = null;
    let speedMs = 100;
    let dirty = true;
    let wanted = false;

    const postState = () => {
        dirty = false;
        wanted = false;
        self.postMessage({ op: "state", json: sim.readJavaString(sim.inst.exports.getStateJson()) });
    };
    const flush = () => {
        if (dirty && wanted) postState();
    };

    const stopTicker = () => {
        if (ticker) {
            clearInterval(ticker);
            ticker = null;
        }
    };
    const startTicker = () => {
        stopTicker();
        ticker = setInterval(() => {
            // The engine clears its running flag itself when the core explodes or an incident halts it.
            if (!sim.inst.exports.isRunning()) {
                stopTicker();
                return;
            }
            sim.inst.exports.step();
            dirty = true;
            if (!sim.inst.exports.isRunning()) stopTicker();
            flush();
        }, speedMs);
    };
    const setRunning = (run) => {
        sim.inst.exports.setRunning(Boolean(run));
        if (sim.inst.exports.isRunning()) startTicker();
        else stopTicker();
    };

    // Calls the worker handles itself instead of the bridge, so no bridge method serializes the state on its own.
    const handlers = {
        setRunning,
        togglePlay: () => setRunning(!sim.inst.exports.isRunning()),
        setSpeed: (ms) => {
            speedMs = Math.max(10, ms);
            if (ticker) startTicker();
        },
        reset: () => {
            stopTicker();
            sim.inst.exports.reset();
        },
        step: () => sim.inst.exports.step(),
        stepTicks: (count = 1) => sim.inst.exports.stepTicks(count),
        isRunning: () => sim.inst.exports.isRunning(),
    };

    const call = (method, args) => {
        if (handlers[method]) return handlers[method](...args);
        if (typeof sim[method] !== "function") throw new Error("Unknown simulator method " + method);
        return sim[method](...args);
    };

    self.onmessage = async (e) => {
        const m = e.data;
        try {
            if (m.op === "init") {
                await sim.init(m.wasmUrl);
                self.postMessage({
                    op: "ready",
                    exports: Object.keys(sim.inst.exports),
                    colorMaps: sim.getColorMaps(),
                });
            } else if (m.op === "batch") {
                for (const c of m.calls) {
                    const result = call(c.method, c.args);
                    if (c.id !== undefined) self.postMessage({ op: "result", id: c.id, result });
                }
                // Stops on an explosion or halt reached by stepping are picked up by the next tick or command.
                if (!sim.inst.exports.isRunning()) stopTicker();
                dirty = true;
                // A batch always answers with its resulting state, so the page sees its own edits promptly.
                wanted = true;
                flush();
            } else if (m.op === "want") {
                wanted = true;
                flush();
            }
        } catch (err) {
            self.postMessage({ op: "error", message: String(err && err.stack || err) });
        }
    };
} else {
    // Methods that only change the simulation; any of them is queued and answered with a fresh state.
    const SIM_SETTERS = [
        "initGrid", "step", "stepTicks", "reset", "loadPreset", "loadLayout", "setTile", "setPipeTier", "setTurbine",
        "setMachineLimits", "setTicksPerCycle", "setCoolingMode", "setCoolantLoopMaterial", "setCoolantLoopPipeSize",
        "setCoolantLoopFluid", "setCoolantLoopSecondaryCoolant", "setCoolantLoopHatchTier", "setCoolantLoopPumpHatches",
        "setCoolantLoopRotor", "selectCoolantCircuit", "removeCoolantCircuit", "setCoolantLoopControl",
        "setCoolantLoopTempBand", "setCoolantLoopPheTempBand", "setCoolantLoopPheDesign", "setCoolantLoopCount",
        "setCoolantLoopPhe", "attachCoolantLoopPoint", "detachCoolantLoopPoint", "clearCoolantLoopPoints", "setParam",
        "repair", "setReactorDamage", "setControlRod", "setAllControlRodsInsertion", "scram", "setStrictMode",
        "clearIncidentLog", "setStopOnIncidents", "clearHaltedByIncident", "setAutoSupplyFuel", "setAutoReplaceFuel",
        "setSpeed",
    ];

    class NuclearSimWorkerClient {
        constructor(workerUrl = "nuclear-sim-worker.js") {
            this.workerUrl = workerUrl;
            this.worker = null;
            this.ready = false;
            this.running = false;
            this.onStateChange = null;
            this.exports = new Set();
            this.colorMaps = null;
            this.lastState = null;
            this.queue = [];
            this.flushScheduled = false;
            this.nextId = 1;
            this.pendingResults = new Map();
            this.stateWaiters = [];
        }

        init(wasmUrl = "nuclear-sim.wasm") {
            return new Promise((resolve, reject) => {
                this.worker = new Worker(this.workerUrl);
                this.worker.onerror = (e) => {
                    if (!this.ready) reject(new Error(e.message || "Simulation worker failed to load"));
                    else console.error("Simulation worker error:", e.message || e);
                };
                this.worker.onmessage = (e) => {
                    const m = e.data;
                    if (m.op === "ready") {
                        this.ready = true;
                        this.exports = new Set(m.exports);
                        this.colorMaps = m.colorMaps;
                        resolve(this);
                    } else if (m.op === "state") {
                        this.receiveState(JSON.parse(m.json));
                    } else if (m.op === "result") {
                        const done = this.pendingResults.get(m.id);
                        this.pendingResults.delete(m.id);
                        if (done) done(m.result);
                    } else if (m.op === "error") {
                        console.error("Simulation worker:", m.message);
                        if (!this.ready) reject(new Error(m.message));
                    }
                };
                this.worker.postMessage({ op: "init", wasmUrl });
            });
        }

        receiveState(st) {
            this.lastState = st;
            this.running = Boolean(st.isRunning);
            const waiters = this.stateWaiters;
            this.stateWaiters = [];
            waiters.forEach(w => w(st));
            if (this.onStateChange) this.onStateChange(st);
        }

        /** Asks the worker for the next state; call it once the previous one has been drawn. */
        requestState() {
            if (this.worker) this.worker.postMessage({ op: "want" });
        }

        /** Resolves with the state after every queued call has run. */
        sync() {
            const p = new Promise(resolve => this.stateWaiters.push(resolve));
            this.enqueue("isRunning", []);
            return p;
        }

        enqueue(method, args, id) {
            this.queue.push(id === undefined ? { method, args } : { method, args, id });
            if (!this.flushScheduled) {
                this.flushScheduled = true;
                queueMicrotask(() => {
                    this.flushScheduled = false;
                    const calls = this.queue;
                    this.queue = [];
                    this.worker.postMessage({ op: "batch", calls });
                });
            }
        }

        callForResult(method, args) {
            const id = this.nextId++;
            return new Promise(resolve => {
                this.pendingResults.set(id, resolve);
                this.enqueue(method, args, id);
            });
        }

        // The bridge's notify flags only decide whether it reports the state; the worker always does.
        setRunning(run) {
            this.running = Boolean(run);
            this.enqueue("setRunning", [this.running]);
        }

        togglePlay() {
            this.setRunning(!this.running);
            return this.running;
        }

        loadRunOptions(line) {
            if (!this.exports.has("loadRunOptions")) return false;
            this.enqueue("loadRunOptions", [line, false]);
            return true;
        }

        isRunning() {
            return this.running;
        }

        getState() {
            return this.lastState;
        }

        getColorMaps() {
            return this.colorMaps;
        }

        getLayoutString() {
            return this.lastState ? this.lastState.layout || "" : "";
        }
    }

    SIM_SETTERS.forEach(name => {
        NuclearSimWorkerClient.prototype[name] = function (...args) {
            this.enqueue(name, args);
        };
    });

    if (typeof window !== "undefined") {
        window.NuclearSimWorkerClient = NuclearSimWorkerClient;
    }
}
