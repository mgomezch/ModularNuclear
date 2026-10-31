// Trend plot: core peak and average temperature (left axis), net power (right axis) and mean rod insertion (0–100 %
// of the plot height) over the run. Samples are kept on the page, since the state only carries the last 60 history
// entries; when the buffer fills, it is halved (keeping each pair's peak temperature) and sampled half as often.
// The canvas follows its box's size and is redrawn at most once per animation frame.

const TREND_CAP = 4000;
const trend = { t: [], maxT: [], avgT: [], power: [], rods: [], lastT: -1, stride: 1, window: 0, safe: 0 };
let trendDrawQueued = false;
let lastChartTick = -1;

function trendClear() {
  trend.t.length = 0;
  trend.maxT.length = 0;
  trend.avgT.length = 0;
  trend.power.length = 0;
  trend.rods.length = 0;
  trend.lastT = -1;
  trend.stride = 1;
  lastChartTick = -1;
  trendQueueDraw();
}

function trendPush(t, maxT, avgT, power, rods) {
  const n = trend.t.length;
  if (n > 0 && t - trend.t[n - 1] < trend.stride) {
    // Within the current stride: fold into the last sample, keeping its peak.
    if (maxT > trend.maxT[n - 1]) trend.maxT[n - 1] = maxT;
    trend.avgT[n - 1] = avgT;
    trend.power[n - 1] = power;
    trend.rods[n - 1] = rods;
    return;
  }
  trend.t.push(t);
  trend.maxT.push(maxT);
  trend.avgT.push(avgT);
  trend.power.push(power);
  trend.rods.push(rods);
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
      trendPush(e.t, e.maxT, e.avgT, e.power || 0, rodsPct);
      trend.lastT = e.t;
      added = true;
    }
  }
  if (!hist.length && tick > trend.lastT) {
    const p = state.powerEstimate ? state.powerEstimate.totalPowerEUt : 0;
    trendPush(tick, state.coreMaxTemp || 0, state.coreAvgTemp || 0, p || 0, rodsPct);
    trend.lastT = tick;
    added = true;
  }
  if (added) trendQueueDraw();
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
  const L = 44, R = 46, T = 8, B = 18;
  const pw = Math.max(10, cw - L - R);
  const ph = Math.max(10, ch - T - B);

  const n = trend.t.length;
  let i0 = 0;
  if (n && trend.window > 0) {
    const from = trend.t[n - 1] - trend.window;
    while (i0 < n - 1 && trend.t[i0] < from) i0++;
  }
  const t0 = n ? (trend.window > 0 ? Math.max(0, trend.t[n - 1] - trend.window) : trend.t[0]) : 0;
  const t1 = n ? Math.max(t0 + 1, trend.t[n - 1]) : 1;
  let maxTemp = trend.safe || 100;
  let maxPow = 0;
  for (let i = i0; i < n; i++) {
    if (trend.maxT[i] > maxTemp) maxTemp = trend.maxT[i];
    if (trend.power[i] > maxPow) maxPow = trend.power[i];
  }
  const tempTop = trendNiceMax(maxTemp * 1.05);
  const powTop = trendNiceMax(maxPow * 1.1);
  const X = (t) => L + ((t - t0) / (t1 - t0)) * pw;
  const YT = (v) => T + ph - (v / tempTop) * ph;
  const YP = (v) => T + ph - (v / powTop) * ph;

  ctx.font = "10px ui-monospace, SFMono-Regular, Menlo, Consolas, monospace";
  ctx.lineWidth = 1;
  // Grid and axis labels
  ctx.strokeStyle = "rgba(148,163,184,0.12)";
  ctx.fillStyle = "#7d8da3";
  ctx.beginPath();
  for (let k = 0; k <= 4; k++) {
    const y = Math.round(T + (ph * k) / 4) + 0.5;
    ctx.moveTo(L, y);
    ctx.lineTo(L + pw, y);
  }
  ctx.stroke();
  ctx.textBaseline = "middle";
  for (let k = 0; k <= 4; k++) {
    const y = T + (ph * k) / 4;
    ctx.textAlign = "right";
    ctx.fillStyle = "#f8a5a5";
    ctx.fillText(Math.round(tempTop * (1 - k / 4)) + "°", L - 4, y);
    ctx.textAlign = "left";
    ctx.fillStyle = "#86efac";
    ctx.fillText(trendFmtPower(powTop * (1 - k / 4)), L + pw + 4, y);
  }
  ctx.fillStyle = "#7d8da3";
  ctx.textBaseline = "top";
  [0, 0.5, 1].forEach((f, k) => {
    ctx.textAlign = k === 0 ? "left" : k === 1 ? "center" : "right";
    ctx.fillText("t=" + Math.round(t0 + (t1 - t0) * f).toLocaleString(), L + pw * f, T + ph + 3);
  });

  // Casing limit
  if (trend.safe > 0 && trend.safe <= tempTop) {
    const y = Math.round(YT(trend.safe)) + 0.5;
    ctx.strokeStyle = "rgba(255,71,87,0.55)";
    ctx.setLineDash([4, 3]);
    ctx.beginPath();
    ctx.moveTo(L, y);
    ctx.lineTo(L + pw, y);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.fillStyle = "rgba(255,120,130,0.8)";
    ctx.textAlign = "left";
    ctx.textBaseline = "bottom";
    ctx.fillText("limit " + Math.round(trend.safe) + "°C", L + 4, y - 1);
  }
  if (n === 0) {
    ctx.fillStyle = "#64748b";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText("Run or step the simulation to plot the trend", L + pw / 2, T + ph / 2);
    return;
  }

  ctx.save();
  ctx.beginPath();
  ctx.rect(L, T - 1, pw, ph + 2);
  ctx.clip();
  const line = (arr, y, color, width, dash) => {
    ctx.strokeStyle = color;
    ctx.lineWidth = width;
    ctx.setLineDash(dash || []);
    ctx.beginPath();
    for (let i = i0; i < n; i++) {
      const x = X(trend.t[i]);
      const yy = y(arr[i]);
      if (i === i0) ctx.moveTo(x, yy);
      else ctx.lineTo(x, yy);
    }
    if (i0 === n - 1) ctx.lineTo(X(trend.t[i0]) + 1, y(arr[i0]));
    ctx.stroke();
  };
  line(trend.rods, (v) => T + ph - (v / 100) * ph, "rgba(34,211,238,0.7)", 1, [2, 3]);
  line(trend.power, YP, "#4ade80", 1.5);
  line(trend.avgT, YT, "#fb923c", 1);
  line(trend.maxT, YT, "#f87171", 1.75);
  ctx.restore();
  ctx.setLineDash([]);
}

function trendInit() {
  const box = document.getElementById("trend-box");
  if (box && typeof ResizeObserver !== "undefined") {
    new ResizeObserver(() => trendQueueDraw()).observe(box);
  }
  window.addEventListener("resize", trendQueueDraw);
  trendQueueDraw();
}
