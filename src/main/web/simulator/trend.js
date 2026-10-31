// Trend plot, in one of three views: Thermal (core peak and average temperature on the left axis, net power on the
// right axis, mean rod insertion as 0–100 % of the plot height), Flux (the spread of neutron flux over the core's
// cells: median line, interquartile band and min–max band, total, fast or thermal, linear or log), or Both stacked on
// one time axis. Clicking a legend entry hides or shows its series. Samples are kept on the page, since the state only
// carries the last 60 history entries; when the buffer fills, it is halved (keeping each pair's peak temperature and
// flux range) and sampled half as often. The canvas follows its box's size and is redrawn at most once per animation
// frame. The view, flux kind, scale and hidden series are remembered in localStorage when available.

const TREND_CAP = 4000;
const trend = { t: [], maxT: [], avgT: [], power: [], rods: [], flux: [], lastT: -1, stride: 1, window: 0, safe: 0 };
const TREND_STORE_KEY = "mptr-sim.trend.v1";
// Offsets of the total, fast and thermal five-number summaries in a history entry's flux array.
const FLUX_KINDS = { total: 0, fast: 5, thermal: 10 };
const trendView = { view: "thermal", kind: "total", log: true, hidden: [] };
let trendDrawQueued = false;
let lastChartTick = -1;

function trendClear() {
  trend.t.length = 0;
  trend.maxT.length = 0;
  trend.avgT.length = 0;
  trend.power.length = 0;
  trend.rods.length = 0;
  trend.flux.length = 0;
  trend.lastT = -1;
  trend.stride = 1;
  lastChartTick = -1;
  trendQueueDraw();
}

// Folds two flux summaries into one: the wider range, the later quartiles.
function fluxMerge(a, b) {
  if (!a) return b;
  if (!b) return a;
  const out = b.slice();
  for (let o = 0; o < 15; o += 5) {
    out[o] = Math.min(a[o], b[o]);
    out[o + 4] = Math.max(a[o + 4], b[o + 4]);
  }
  return out;
}

function trendPush(t, maxT, avgT, power, rods, flux) {
  const n = trend.t.length;
  if (n > 0 && t - trend.t[n - 1] < trend.stride) {
    // Within the current stride: fold into the last sample, keeping its peak.
    if (maxT > trend.maxT[n - 1]) trend.maxT[n - 1] = maxT;
    trend.avgT[n - 1] = avgT;
    trend.power[n - 1] = power;
    trend.rods[n - 1] = rods;
    trend.flux[n - 1] = fluxMerge(trend.flux[n - 1], flux);
    return;
  }
  trend.t.push(t);
  trend.maxT.push(maxT);
  trend.avgT.push(avgT);
  trend.power.push(power);
  trend.rods.push(rods);
  trend.flux.push(flux || null);
  if (trend.t.length > TREND_CAP) {
    const half = (arr, pick) => {
      const out = [];
      for (let i = 0; i + 1 < arr.length; i += 2) out.push(pick(arr[i], arr[i + 1]));
      if (arr.length % 2) out.push(arr[arr.length - 1]);
      return out;
    };
    const later = (a, b) => b;
    trend.t = half(trend.t, (a) => a);
    trend.maxT = half(trend.maxT, Math.max);
    trend.avgT = half(trend.avgT, later);
    trend.power = half(trend.power, later);
    trend.rods = half(trend.rods, later);
    trend.flux = half(trend.flux, fluxMerge);
    trend.stride *= 2;
  }
}

function trendIngest(state, rodsPct) {
  if (!state) return;
  const tick = state.currentTick || 0;
  if (tick < trend.lastT) trendClear();
  trend.safe = state.maxSafeTemp || 0;
  const hist = state.history || [];
  let added = false;
  for (let i = 0; i < hist.length; i++) {
    const e = hist[i];
    if (e.t > trend.lastT) {
      trendPush(e.t, e.maxT, e.avgT, e.power || 0, rodsPct, e.flux);
      trend.lastT = e.t;
      added = true;
    }
  }
  if (!hist.length && tick > trend.lastT) {
    const p = state.powerEstimate ? state.powerEstimate.totalPowerEUt : 0;
    trendPush(tick, state.coreMaxTemp || 0, state.coreAvgTemp || 0, p || 0, rodsPct, null);
    trend.lastT = tick;
    added = true;
  }
  if (added) {
    trendLegendFlux();
    trendQueueDraw();
  }
}

// Neutrons per second from a per-cycle count.
function fluxPerSec(v) {
  const tpc = (currentState && currentState.ticksPerCycle) || 5;
  return v * 20 / tpc;
}

function trendLegendFlux() {
  let f = null;
  for (let i = trend.flux.length - 1; i >= 0 && !f; i--) f = trend.flux[i];
  const o = FLUX_KINDS[trendView.kind];
  const fmt = (v) => formatFluxValue(Math.round(fluxPerSec(v)));
  setText("tr-fmed", f ? fmt(f[o + 2]) + " n/s" : "—");
  setText("tr-fiqr", f ? fmt(f[o + 1]) + "–" + fmt(f[o + 3]) : "—");
  setText("tr-frange", f ? fmt(f[o]) + "–" + fmt(f[o + 4]) : "—");
}

function trendSave() {
  try {
    window.localStorage.setItem(TREND_STORE_KEY, JSON.stringify(trendView));
  } catch (e) { /* storage unavailable */ }
}

function trendLoad() {
  try {
    const v = JSON.parse(window.localStorage.getItem(TREND_STORE_KEY) || "null");
    if (v && typeof v === "object") {
      if (["thermal", "flux", "both"].includes(v.view)) trendView.view = v.view;
      if (v.kind in FLUX_KINDS) trendView.kind = v.kind;
      if (typeof v.log === "boolean") trendView.log = v.log;
      if (Array.isArray(v.hidden)) trendView.hidden = v.hidden.filter(k => typeof k === "string");
    }
  } catch (e) { /* storage unavailable or corrupt */ }
}

// Reflects the view settings in the header: active buttons, which legend group shows, dimmed hidden series.
function trendSyncHead() {
  const head = document.querySelector(".trend-head");
  if (!head) return;
  head.dataset.view = trendView.view;
  const panel = head.closest(".trend-panel");
  if (panel) panel.classList.toggle("trend-both", trendView.view === "both");
  head.querySelectorAll("[data-trend-view]").forEach(b => b.classList.toggle("active", b.dataset.trendView === trendView.view));
  head.querySelectorAll("[data-flux-kind]").forEach(b => b.classList.toggle("active", b.dataset.fluxKind === trendView.kind));
  const lb = document.getElementById("btn-flux-log");
  if (lb) {
    lb.classList.toggle("active", trendView.log);
    lb.textContent = trendView.log ? "log" : "lin";
  }
  head.querySelectorAll(".lg[data-series]").forEach(el => {
    const off = trendView.hidden.includes(el.dataset.series);
    el.classList.toggle("off", off);
    el.setAttribute("aria-pressed", off ? "false" : "true");
  });
}

function setTrendView(view) {
  trendView.view = view;
  trendSave();
  trendSyncHead();
  trendQueueDraw();
}

function setFluxKind(kind) {
  trendView.kind = kind;
  trendSave();
  trendSyncHead();
  trendLegendFlux();
  trendQueueDraw();
}

function toggleFluxLog() {
  trendView.log = !trendView.log;
  trendSave();
  trendSyncHead();
  trendQueueDraw();
}

function toggleTrendSeries(key) {
  const i = trendView.hidden.indexOf(key);
  if (i >= 0) trendView.hidden.splice(i, 1);
  else trendView.hidden.push(key);
  trendSave();
  trendSyncHead();
  trendQueueDraw();
}

function trendShown(key) {
  return !trendView.hidden.includes(key);
}

function setTrendWindow(ticks) {
  trend.window = ticks;
  document.querySelectorAll(".trend-window .seg-btn").forEach(b => {
    b.classList.toggle("active", Number(b.dataset.window) === ticks);
  });
  trendQueueDraw();
}

// Called from renderUI with the mean rod insertion it computed.
function renderChart(rodsPct) {
  if (!currentState) return;
  const r = (typeof rodsPct === "number") ? rodsPct : (trend.rods.length ? trend.rods[trend.rods.length - 1] : 0);
  trendIngest(currentState, r);
  lastChartTick = currentState.currentTick;
}

function trendQueueDraw() {
  if (trendDrawQueued) return;
  trendDrawQueued = true;
  const run = () => {
    if (!trendDrawQueued) return;
    trendDrawQueued = false;
    trendDraw();
  };
  requestAnimationFrame(run);
  // Frame callbacks stop for pages that are not on screen; the timer keeps the plot current anyway.
  setTimeout(run, 500);
}

function trendNiceMax(v) {
  if (!(v > 0)) return 1;
  const p = Math.pow(10, Math.floor(Math.log10(v)));
  const m = v / p;
  const step = m <= 1 ? 1 : m <= 2 ? 2 : m <= 2.5 ? 2.5 : m <= 5 ? 5 : 10;
  return step * p;
}

function trendFmtPower(v) {
  if (v >= 1e9) return (v / 1e9).toFixed(v >= 1e10 ? 0 : 1) + "G";
  if (v >= 1e6) return (v / 1e6).toFixed(v >= 1e7 ? 0 : 1) + "M";
  if (v >= 1e3) return (v / 1e3).toFixed(v >= 1e4 ? 0 : 1) + "k";
  return String(Math.round(v));
}

function trendSize(canvas) {
  const box = canvas.parentElement;
  const dpr = window.devicePixelRatio || 1;
  const w = Math.max(1, Math.round(box.clientWidth * dpr));
  const h = Math.max(1, Math.round(box.clientHeight * dpr));
  if (canvas.width !== w) canvas.width = w;
  if (canvas.height !== h) canvas.height = h;
  return { w, h, dpr };
}

function trendDraw() {
  const canvas = document.getElementById("chartCanvas");
  if (!canvas || !canvas.parentElement) return;
  const { w, h, dpr } = trendSize(canvas);
  const ctx = canvas.getContext("2d");
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.clearRect(0, 0, w, h);
  ctx.scale(dpr, dpr);
  const cw = w / dpr;
  const ch = h / dpr;
  const L = 44, R = 46, T = 8, B = 18, GAP = 14;
  const pw = Math.max(10, cw - L - R);
  const ph = Math.max(20, ch - T - B);

  const n = trend.t.length;
  let i0 = 0;
  if (n && trend.window > 0) {
    const from = trend.t[n - 1] - trend.window;
    while (i0 < n - 1 && trend.t[i0] < from) i0++;
  }
  const t0 = n ? (trend.window > 0 ? Math.max(0, trend.t[n - 1] - trend.window) : trend.t[0]) : 0;
  const t1 = n ? Math.max(t0 + 1, trend.t[n - 1]) : 1;
  const X = (t) => L + ((t - t0) / (t1 - t0)) * pw;
  const span = { n, i0, X };

  ctx.font = "10px ui-monospace, SFMono-Regular, Menlo, Consolas, monospace";
  ctx.lineWidth = 1;
  const view = trendView.view;
  if (view === "both") {
    const top = Math.max(10, Math.round((ph - GAP) * 0.55));
    trendDrawThermal(ctx, span, { x: L, y: T, w: pw, h: top });
    trendDrawFlux(ctx, span, { x: L, y: T + top + GAP, w: pw, h: Math.max(10, ph - top - GAP) });
  } else if (view === "flux") {
    trendDrawFlux(ctx, span, { x: L, y: T, w: pw, h: ph });
  } else {
    trendDrawThermal(ctx, span, { x: L, y: T, w: pw, h: ph });
  }

  ctx.fillStyle = "#7d8da3";
  ctx.textBaseline = "top";
  [0, 0.5, 1].forEach((f, k) => {
    ctx.textAlign = k === 0 ? "left" : k === 1 ? "center" : "right";
    ctx.fillText("t=" + Math.round(t0 + (t1 - t0) * f).toLocaleString(), L + pw * f, T + ph + 3);
  });
  if (n === 0) {
    ctx.fillStyle = "#64748b";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText("Run or step the simulation to plot the trend", L + pw / 2, T + ph / 2);
  }
}

// Horizontal grid lines of a panel.
function trendGrid(ctx, r, steps) {
  ctx.strokeStyle = "rgba(148,163,184,0.12)";
  ctx.lineWidth = 1;
  ctx.beginPath();
  for (let k = 0; k <= steps; k++) {
    const y = Math.round(r.y + (r.h * k) / steps) + 0.5;
    ctx.moveTo(r.x, y);
    ctx.lineTo(r.x + r.w, y);
  }
  ctx.stroke();
}

// A line through arr's samples (null samples break it).
function trendLine(ctx, span, arr, y, color, width, dash) {
  ctx.strokeStyle = color;
  ctx.lineWidth = width;
  ctx.setLineDash(dash || []);
  ctx.beginPath();
  let pen = false;
  for (let i = span.i0; i < span.n; i++) {
    const v = arr(i);
    if (v == null) { pen = false; continue; }
    const x = span.X(trend.t[i]);
    const yy = y(v);
    if (!pen) { ctx.moveTo(x, yy); pen = true; } else ctx.lineTo(x, yy);
  }
  if (span.i0 === span.n - 1 && arr(span.i0) != null) ctx.lineTo(span.X(trend.t[span.i0]) + 1, y(arr(span.i0)));
  ctx.stroke();
  ctx.setLineDash([]);
}

// A filled band between lo(i) and hi(i), in runs of non-null samples.
function trendBand(ctx, span, lo, hi, y, color) {
  ctx.fillStyle = color;
  let i = span.i0;
  while (i < span.n) {
    while (i < span.n && lo(i) == null) i++;
    const a = i;
    while (i < span.n && lo(i) != null) i++;
    if (i - a < 1) continue;
    ctx.beginPath();
    for (let k = a; k < i; k++) ctx.lineTo(span.X(trend.t[k]), y(hi(k)));
    if (i - a === 1) ctx.lineTo(span.X(trend.t[a]) + 1, y(hi(a)));
    if (i - a === 1) ctx.lineTo(span.X(trend.t[a]) + 1, y(lo(a)));
    for (let k = i - 1; k >= a; k--) ctx.lineTo(span.X(trend.t[k]), y(lo(k)));
    ctx.closePath();
    ctx.fill();
  }
}

function trendDrawThermal(ctx, span, r) {
  const { n, i0 } = span;
  let maxTemp = trend.safe || 100;
  let maxPow = 0;
  for (let i = i0; i < n; i++) {
    if (trendShown("max") && trend.maxT[i] > maxTemp) maxTemp = trend.maxT[i];
    if (trendShown("avg") && trend.avgT[i] > maxTemp) maxTemp = trend.avgT[i];
    if (trend.power[i] > maxPow) maxPow = trend.power[i];
  }
  const tempTop = trendNiceMax(maxTemp * 1.05);
  const powTop = trendNiceMax(maxPow * 1.1);
  const YT = (v) => r.y + r.h - (v / tempTop) * r.h;
  const YP = (v) => r.y + r.h - (v / powTop) * r.h;
  const steps = r.h < 60 ? 2 : 4;
  trendGrid(ctx, r, steps);
  ctx.textBaseline = "middle";
  for (let k = 0; k <= steps; k++) {
    const y = r.y + (r.h * k) / steps;
    ctx.textAlign = "right";
    ctx.fillStyle = "#f8a5a5";
    ctx.fillText(Math.round(tempTop * (1 - k / steps)) + "°", r.x - 4, y);
    if (trendShown("pwr")) {
      ctx.textAlign = "left";
      ctx.fillStyle = "#86efac";
      ctx.fillText(trendFmtPower(powTop * (1 - k / steps)), r.x + r.w + 4, y);
    }
  }
  // Casing limit
  if (trend.safe > 0 && trend.safe <= tempTop) {
    const y = Math.round(YT(trend.safe)) + 0.5;
    ctx.strokeStyle = "rgba(255,71,87,0.55)";
    ctx.setLineDash([4, 3]);
    ctx.beginPath();
    ctx.moveTo(r.x, y);
    ctx.lineTo(r.x + r.w, y);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.fillStyle = "rgba(255,120,130,0.8)";
    ctx.textAlign = "left";
    ctx.textBaseline = "bottom";
    ctx.fillText("limit " + Math.round(trend.safe) + "°C", r.x + 4, y - 1);
  }
  if (!n) return;
  ctx.save();
  ctx.beginPath();
  ctx.rect(r.x, r.y - 1, r.w, r.h + 2);
  ctx.clip();
  if (trendShown("rods")) trendLine(ctx, span, (i) => trend.rods[i], (v) => r.y + r.h - (v / 100) * r.h, "rgba(34,211,238,0.7)", 1, [2, 3]);
  if (trendShown("pwr")) trendLine(ctx, span, (i) => trend.power[i], YP, "#4ade80", 1.5);
  if (trendShown("avg")) trendLine(ctx, span, (i) => trend.avgT[i], YT, "#fb923c", 1);
  if (trendShown("max")) trendLine(ctx, span, (i) => trend.maxT[i], YT, "#f87171", 1.75);
  ctx.restore();
}

function trendDrawFlux(ctx, span, r) {
  const { n, i0 } = span;
  const o = FLUX_KINDS[trendView.kind];
  const val = (i, k) => (trend.flux[i] ? fluxPerSec(trend.flux[i][o + k]) : null);
  const showRange = trendShown("frange"), showIqr = trendShown("fiqr"), showMed = trendShown("fmed");
  // Scale over what is shown (falling back to the whole summary when every series is hidden)
  const kLo = showRange ? 0 : showIqr ? 1 : 2, kHi = showRange ? 4 : showIqr ? 3 : 2;
  let lo = Infinity, hi = 0;
  for (let i = i0; i < n; i++) {
    if (!trend.flux[i]) continue;
    for (let k = kLo; k <= kHi; k++) {
      const v = val(i, k);
      if (v > hi) hi = v;
      if (v > 0 && v < lo) lo = v;
    }
  }
  const log = trendView.log;
  let Y, ticks;
  if (log) {
    const dTop = Math.max(1, Math.ceil(Math.log10(Math.max(10, hi * 1.05))));
    let dBot = isFinite(lo) ? Math.floor(Math.log10(lo)) : dTop - 3;
    dBot = Math.max(0, Math.min(dBot, dTop - 1));
    const lg = (v) => Math.log10(Math.max(Math.pow(10, dBot), v));
    Y = (v) => r.y + r.h - ((lg(v) - dBot) / (dTop - dBot)) * r.h;
    const step = Math.max(1, Math.ceil((dTop - dBot) / (r.h < 60 ? 2 : 4)));
    ticks = [];
    for (let d = dTop; d >= dBot; d -= step) ticks.push(Math.pow(10, d));
  } else {
    const top = trendNiceMax(hi * 1.1);
    Y = (v) => r.y + r.h - (v / top) * r.h;
    const steps = r.h < 60 ? 2 : 4;
    ticks = [];
    for (let k = 0; k <= steps; k++) ticks.push(top * (1 - k / steps));
  }
  ctx.strokeStyle = "rgba(148,163,184,0.12)";
  ctx.lineWidth = 1;
  ctx.beginPath();
  ticks.forEach(v => {
    const y = Math.round(Y(v)) + 0.5;
    ctx.moveTo(r.x, y);
    ctx.lineTo(r.x + r.w, y);
  });
  ctx.stroke();
  ctx.fillStyle = "#c4b5fd";
  ctx.textAlign = "right";
  ctx.textBaseline = "middle";
  ticks.forEach(v => ctx.fillText(formatFluxValue(Math.round(v)), r.x - 4, Y(v)));
  if (!n) return;
  ctx.save();
  ctx.beginPath();
  ctx.rect(r.x, r.y - 1, r.w, r.h + 2);
  ctx.clip();
  if (showRange) trendBand(ctx, span, (i) => val(i, 0), (i) => val(i, 4), Y, "rgba(167,139,250,0.14)");
  if (showIqr) trendBand(ctx, span, (i) => val(i, 1), (i) => val(i, 3), Y, "rgba(167,139,250,0.38)");
  if (showMed) trendLine(ctx, span, (i) => val(i, 2), Y, "#ddd6fe", 1.5);
  ctx.restore();
  ctx.textAlign = "left";
  ctx.textBaseline = "top";
  ctx.fillStyle = "rgba(196,181,253,0.8)";
  ctx.fillText(trendView.kind + " flux, n/s per cell" + (log ? " (log)" : ""), r.x + 4, r.y + 2);
}

function trendInit() {
  trendLoad();
  trendSyncHead();
  const box = document.getElementById("trend-box");
  if (box && typeof ResizeObserver !== "undefined") {
    new ResizeObserver(() => trendQueueDraw()).observe(box);
  }
  window.addEventListener("resize", trendQueueDraw);
  trendQueueDraw();
}
