// Side dock: the subsystem panes (<section class="dock-pane" data-tab=...>) live in up to two tab groups, A on top and
// B below. The user reorders tabs and moves them between the groups by dragging them or from the tab menu, hides and
// shows them from the Layout menu and drags the splitters; all of it is remembered in localStorage when available.
// Nodes are only moved or shown/hidden on those user actions, never because of the simulation state.

const DOCK_STORE_KEY = "mptr-sim.layout.v1";
const DOCK_SPLIT_TABS = ["log", "telemetry"];
const DOCK_LIMITS = { dockMin: 300, dockMax: 720, coreMin: 0.35, coreMax: 0.9, abMin: 0.2, abMax: 0.8 };
const DOCK_MOBILE_QUERY = "(max-width: 767px), (orientation: portrait)";

let dockTabs = [];            // tab ids in page order, from the panes in the HTML
const dockPanes = {};         // id -> pane element
const dockButtons = {};       // id -> tab button
let dockLayout = null;
let dockMobile = false;
let dockDragId = null;

function dockDefaultLayout() {
  return {
    A: dockTabs.slice(),
    B: [],
    active: { A: dockTabs[0], B: null },
    hidden: [],
    dockW: Math.round(Math.max(340, Math.min(600, window.innerWidth * 0.36))),
    core: 0.7,
    ab: 0.55
  };
}

function dockLoad() {
  let saved = null;
  try {
    const raw = window.localStorage.getItem(DOCK_STORE_KEY);
    if (raw) saved = JSON.parse(raw);
  } catch (e) {
    saved = null;
  }
  const l = dockDefaultLayout();
  if (!saved || typeof saved !== "object") return l;
  const seen = new Set();
  const clean = (arr) => (Array.isArray(arr) ? arr : []).filter(id => dockPanes[id] && !seen.has(id) && seen.add(id));
  l.A = clean(saved.A);
  l.B = clean(saved.B);
  // Panes added to the page since the layout was saved go to the end of group A.
  dockTabs.forEach(id => { if (!seen.has(id)) l.A.push(id); });
  if (saved.active) {
    if (l.A.includes(saved.active.A)) l.active.A = saved.active.A;
    if (l.B.includes(saved.active.B)) l.active.B = saved.active.B;
  }
  l.hidden = (Array.isArray(saved.hidden) ? saved.hidden : []).filter(id => dockPanes[id]);
  const num = (v, lo, hi, d) => (typeof v === "number" && isFinite(v) ? Math.min(hi, Math.max(lo, v)) : d);
  l.dockW = num(saved.dockW, DOCK_LIMITS.dockMin, DOCK_LIMITS.dockMax, l.dockW);
  l.core = num(saved.core, DOCK_LIMITS.coreMin, DOCK_LIMITS.coreMax, l.core);
  l.ab = num(saved.ab, DOCK_LIMITS.abMin, DOCK_LIMITS.abMax, l.ab);
  return l;
}

function dockSave() {
  try {
    window.localStorage.setItem(DOCK_STORE_KEY, JSON.stringify(dockLayout));
  } catch (e) {
    // No storage (private window, blocked site data): the layout just is not remembered.
  }
}

function dockGroupOf(id) {
  if (dockMobile) return "A";
  return dockLayout.B.includes(id) ? "B" : "A";
}

// The groups as shown: phones and portrait screens show a single group, A followed by B.
function dockEffective() {
  if (dockMobile) {
    const all = dockLayout.A.concat(dockLayout.B);
    const act = dockIsShownTab(dockLayout.active.A) ? dockLayout.active.A : dockLayout.active.B;
    return { A: all, B: [], active: { A: act, B: null } };
  }
  return { A: dockLayout.A, B: dockLayout.B, active: dockLayout.active };
}

function dockIsShownTab(id) {
  return !!id && !dockLayout.hidden.includes(id);
}

function dockCssVars() {
  const root = document.documentElement.style;
  const w = Math.round(dockLayout.dockW) + "px";
  if (root.getPropertyValue("--dock-w") !== w) root.setProperty("--dock-w", w);
  root.setProperty("--core-a", String(dockLayout.core));
  root.setProperty("--core-b", String(1 - dockLayout.core));
  root.setProperty("--ab-a", String(dockLayout.ab));
  root.setProperty("--ab-b", String(1 - dockLayout.ab));
}

// Puts every tab and pane where the layout says. Called on start-up and after a layout change only.
function dockApply() {
  const eff = dockEffective();
  const dock = document.getElementById("dock");
  ["A", "B"].forEach(g => {
    const group = document.getElementById("dock-group-" + g);
    const strip = group.querySelector(".dock-tabs");
    const body = group.querySelector(".dock-body");
    const ids = eff[g];
    ids.forEach((id, i) => {
      const btn = dockButtons[id];
      const pane = dockPanes[id];
      if (strip.children[i] !== btn) strip.insertBefore(btn, strip.children[i] || null);
      if (pane.parentElement !== body) body.appendChild(pane);
    });
    const shown = ids.filter(dockIsShownTab);
    let act = eff.active[g];
    if (!shown.includes(act)) act = shown[0] || null;
    if (!dockMobile) dockLayout.active[g] = act;
    ids.forEach(id => {
      const on = id === act;
      const hid = !dockIsShownTab(id);
      dockButtons[id].classList.toggle("is-active", on);
      dockButtons[id].setAttribute("aria-selected", on ? "true" : "false");
      dockButtons[id].tabIndex = on ? 0 : -1;
      dockButtons[id].hidden = hid;
      dockPanes[id].classList.toggle("is-active", on);
      dockPanes[id].hidden = hid || !on;
    });
    group.hidden = shown.length === 0;
    group.dataset.active = act || "";
  });
  const splitShown = !document.getElementById("dock-group-A").hidden && !document.getElementById("dock-group-B").hidden;
  dock.classList.toggle("is-split", splitShown);
  document.getElementById("split-dock-ab").hidden = !splitShown;
  dockCssVars();
  dockFitStrips();
  dockSyncLogSeen();
}

// Tab strips drop their icons when the labels alone would not fit; past that they scroll sideways.
function dockFitStrips() {
  document.querySelectorAll('.dock-tabs').forEach(strip => {
    if (!strip.offsetParent) return;
    strip.classList.remove('is-compact');
    if (strip.scrollWidth > strip.clientWidth + 1) strip.classList.add('is-compact');
  });
}

function dockActivate(id, opts) {
  if (!dockPanes[id] || !dockIsShownTab(id)) return;
  // On a phone all tabs share group A's strip, so the choice is remembered as A's active tab (the desktop layout
  // puts it back in its own group).
  dockLayout.active[dockGroupOf(id)] = id;
  dockApply();
  dockSave();
  // Scrolls only the tab strip sideways (scrollIntoView could also scroll the page on phones).
  const btn = dockButtons[id];
  const strip = btn.parentElement;
  if (strip && strip.scrollWidth > strip.clientWidth) {
    const l = btn.offsetLeft - strip.offsetLeft;
    if (l < strip.scrollLeft) strip.scrollLeft = l;
    else if (l + btn.offsetWidth > strip.scrollLeft + strip.clientWidth) strip.scrollLeft = l + btn.offsetWidth - strip.clientWidth;
  }
  if (opts && opts.focus) btn.focus();
}

function dockIsVisible(id) {
  const btn = dockButtons[id];
  if (!btn || btn.hidden || !btn.classList.contains("is-active")) return false;
  const group = btn.closest(".dock-group");
  return !!group && !group.hidden;
}

// Shows a pane the user just asked for indirectly (a cell click, Paint mode, Attach Points mode), unless it is already
// showing or the user hid that tab.
function dockReveal(id) {
  if (!dockLayout || dockIsVisible(id) || !dockIsShownTab(id)) return;
  dockActivate(id);
}

function dockMove(id, group, index) {
  if (!dockPanes[id] || (group !== "A" && group !== "B")) return;
  dockLayout.A = dockLayout.A.filter(t => t !== id);
  dockLayout.B = dockLayout.B.filter(t => t !== id);
  const arr = dockLayout[group];
  const at = (index == null || index < 0 || index > arr.length) ? arr.length : index;
  arr.splice(at, 0, id);
  dockLayout.active[group] = id;
  if (!dockLayout[group === "A" ? "B" : "A"].length) dockLayout.active[group === "A" ? "B" : "A"] = null;
  dockApply();
  dockSave();
}

function dockShift(id, delta) {
  const g = dockLayout.B.includes(id) ? "B" : "A";
  const arr = dockLayout[g];
  const i = arr.indexOf(id);
  const j = Math.max(0, Math.min(arr.length - 1, i + delta));
  if (i === j) return;
  arr.splice(i, 1);
  arr.splice(j, 0, id);
  dockApply();
  dockSave();
}

function dockSetHidden(id, hidden) {
  dockLayout.hidden = dockLayout.hidden.filter(t => t !== id);
  if (hidden) dockLayout.hidden.push(id);
  dockApply();
  dockSave();
}

function dockIsSplit() {
  return dockLayout.B.some(dockIsShownTab) && dockLayout.A.some(dockIsShownTab);
}

function dockSetSplit(on) {
  if (on) {
    let move = dockLayout.A.filter(id => DOCK_SPLIT_TABS.includes(id) && dockIsShownTab(id));
    const shownA = dockLayout.A.filter(dockIsShownTab);
    if (!move.length || move.length >= shownA.length) move = shownA.slice(-1);
    if (move.length >= shownA.length) return;
    move.forEach(id => dockMove(id, "B"));
    dockLayout.active.B = move[0];
  } else {
    dockLayout.A = dockLayout.A.concat(dockLayout.B);
    dockLayout.B = [];
    dockLayout.active.B = null;
  }
  dockApply();
  dockSave();
}

function dockReset() {
  dockLayout = dockDefaultLayout();
  dockApply();
  dockSave();
  if (typeof isFitMode !== "undefined") fitGridToScreen();
}

// Tabs in the order Alt+1..9 picks them: group A, then group B, hidden tabs skipped.
function dockSelectByPosition(n) {
  const eff = dockEffective();
  const order = eff.A.concat(eff.B).filter(dockIsShownTab);
  if (order[n - 1]) dockActivate(order[n - 1]);
}

function dockCycle(delta) {
  const eff = dockEffective();
  const ids = eff.A.filter(dockIsShownTab);
  if (!ids.length) return;
  const cur = ids.indexOf(dockLayout.active.A);
  dockActivate(ids[(cur + delta + ids.length) % ids.length]);
}

// ---- Badges -------------------------------------------------------------------------------------------------------

// Writes a tab's badge; the badge keeps a fixed width, so the tab strip never shifts. tone: "", "ok", "warn", "alarm".
function dockSetBadge(id, text, tone) {
  const btn = dockButtons[id];
  if (!btn || !btn.__badge) return;
  const b = btn.__badge;
  const s = text == null ? "" : String(text);
  setText(b, s);
  setClass(b, "tab-badge" + (tone ? " tone-" + tone : "") + (s ? "" : " is-empty"));
}

let dockLogSeen = 0;
let dockLogCount = 0;
function dockSyncLogSeen() {
  if (dockIsVisible("log")) {
    dockLogSeen = dockLogCount;
    dockSetBadge("log", "", "");
  }
}

// Unread incidents: entries added since the Log tab was last on screen.
function dockUpdateLogBadge(count, alarm) {
  dockLogCount = count;
  if (count < dockLogSeen) dockLogSeen = count;
  if (dockIsVisible("log")) dockLogSeen = count;
  const unread = count - dockLogSeen;
  dockSetBadge("log", unread > 0 ? (unread > 99 ? "99+" : String(unread)) : "", unread > 0 ? (alarm ? "alarm" : "warn") : "");
}

// ---- Menus --------------------------------------------------------------------------------------------------------

let dockMenuOpen = null;

function dockCloseMenu() {
  if (!dockMenuOpen) return;
  dockMenuOpen.menu.hidden = true;
  if (dockMenuOpen.anchor) dockMenuOpen.anchor.setAttribute("aria-expanded", "false");
  dockMenuOpen = null;
}

function dockOpenMenu(items, anchor, x, y) {
  dockCloseMenu();
  const menu = document.getElementById("dock-menu");
  menu.innerHTML = "";
  items.forEach(it => {
    if (it.sep) {
      const s = document.createElement("div");
      s.className = "menu-sep";
      menu.appendChild(s);
      return;
    }
    if (it.head) {
      const h = document.createElement("div");
      h.className = "menu-head";
      h.textContent = it.head;
      menu.appendChild(h);
      return;
    }
    const b = document.createElement(it.href ? "a" : "button");
    if (it.href) {
      b.href = it.href;
      b.target = "_blank";
      b.rel = "noopener";
    } else {
      b.type = "button";
    }
    b.className = "menu-item" + (it.check !== undefined ? " is-check" : "");
    b.setAttribute("role", it.check !== undefined ? "menuitemcheckbox" : "menuitem");
    if (it.check !== undefined) b.setAttribute("aria-checked", it.check ? "true" : "false");
    b.disabled = !!it.disabled;
    const mark = document.createElement("span");
    mark.className = "menu-mark";
    mark.textContent = it.check ? "✓" : "";
    const lbl = document.createElement("span");
    lbl.className = "menu-label";
    lbl.textContent = it.label;
    b.append(mark, lbl);
    if (it.key) {
      const k = document.createElement("kbd");
      k.textContent = it.key;
      b.appendChild(k);
    }
    b.addEventListener("click", () => {
      if (!it.keepOpen) dockCloseMenu();
      if (it.action) it.action();
      if (it.keepOpen) dockOpenMenu(typeof it.refresh === "function" ? it.refresh() : items, anchor, x, y);
    });
    menu.appendChild(b);
  });
  menu.hidden = false;
  const r = menu.getBoundingClientRect();
  const left = Math.max(4, Math.min(x, window.innerWidth - r.width - 4));
  const top = Math.max(4, Math.min(y, window.innerHeight - r.height - 4));
  menu.style.left = left + "px";
  menu.style.top = top + "px";
  if (anchor) anchor.setAttribute("aria-expanded", "true");
  dockMenuOpen = { menu, anchor };
  const first = menu.querySelector(".menu-item:not([disabled])");
  if (first) first.focus({ preventScroll: true });
}

function dockTabMenuItems(id) {
  const g = dockLayout.B.includes(id) ? "B" : "A";
  const arr = dockLayout[g];
  const i = arr.indexOf(id);
  const other = g === "A" ? "B" : "A";
  const label = dockButtons[id].dataset.label;
  const items = [
    { head: label },
    { label: "Move left", disabled: i <= 0, action: () => dockShift(id, -1) },
    { label: "Move right", disabled: i >= arr.length - 1, action: () => dockShift(id, 1) }
  ];
  if (!dockMobile) {
    const shownHere = arr.filter(dockIsShownTab).length;
    items.push({
      label: other === "B" ? "Move to bottom group" : "Move to top group",
      disabled: other === "B" && shownHere <= 1,
      action: () => dockMove(id, other)
    });
  }
  items.push({ label: "Hide tab", action: () => dockSetHidden(id, true) });
  return items;
}

function dockLayoutMenuItems() {
  const items = [{ head: "Tabs" }];
  dockTabs.forEach(id => {
    items.push({
      label: dockButtons[id].dataset.label,
      check: dockIsShownTab(id),
      keepOpen: true,
      refresh: dockLayoutMenuItems,
      action: () => dockSetHidden(id, dockIsShownTab(id))
    });
  });
  items.push({ sep: true });
  if (!dockMobile) {
    items.push({ label: "Split dock", check: dockIsSplit(), keepOpen: true, refresh: dockLayoutMenuItems, action: () => dockSetSplit(!dockIsSplit()) });
  }
  items.push({ label: "Fit grid", key: "f", action: () => fitGridToScreen() });
  items.push({ label: "Reset layout", action: dockReset });
  items.push({ sep: true });
  items.push({ label: "Live monitor", href: "/monitor/" });
  items.push({ sep: true });
  items.push({ head: "Keys: Space run · s SCRAM · 1–6 views · Alt+1–9 / [ ] tabs · i/p inspect/paint · f fit · +/−/0 zoom" });
  return items;
}

function openLayoutMenu(btn) {
  if (dockMenuOpen && dockMenuOpen.anchor === btn) {
    dockCloseMenu();
    return;
  }
  const r = btn.getBoundingClientRect();
  dockOpenMenu(dockLayoutMenuItems(), btn, r.right - 260, r.bottom + 4);
}

// ---- Drag and drop ------------------------------------------------------------------------------------------------

function dockDropIndex(strip, clientX, group) {
  const btns = Array.from(strip.children).filter(b => !b.hidden && b.dataset.tab !== dockDragId);
  for (const b of btns) {
    const r = b.getBoundingClientRect();
    if (clientX < r.left + r.width / 2) {
      const arr = dockLayout[group].filter(t => t !== dockDragId);
      return { index: arr.indexOf(b.dataset.tab), x: r.left };
    }
  }
  const last = btns[btns.length - 1];
  return { index: null, x: last ? last.getBoundingClientRect().right : strip.getBoundingClientRect().left };
}

function dockShowMarker(strip, x) {
  const marker = document.getElementById("dock-drop-marker");
  const r = strip.getBoundingClientRect();
  marker.style.left = Math.round(x - 1) + "px";
  marker.style.top = Math.round(r.top) + "px";
  marker.style.height = Math.round(r.height) + "px";
  marker.hidden = false;
}

function dockClearDrag() {
  dockDragId = null;
  document.getElementById("dock").classList.remove("is-dragging");
  document.getElementById("dock-drop-marker").hidden = true;
  document.getElementById("dock-drag-ghost").hidden = true;
  document.body.classList.remove("is-tab-dragging");
  document.querySelectorAll(".dock-drop-hover").forEach(el => el.classList.remove("dock-drop-hover"));
}

// Where a tab dropped at (x, y) goes: { group, index } (index null = the end), or null.
function dockDropTarget(x, y) {
  const el = document.elementFromPoint(x, y);
  if (!el) return null;
  const zone = el.closest("#dock-dropzone");
  if (zone && document.getElementById("dock").classList.contains("is-dragging")) {
    return { group: "B", index: null, el: zone, split: true };
  }
  const group = el.closest(".dock-group");
  if (!group || group.hidden) return null;
  const g = group.dataset.group;
  const strip = el.closest(".dock-tabs-row") ? group.querySelector(".dock-tabs") : null;
  if (strip) {
    const at = dockDropIndex(strip, x, g);
    return { group: g, index: at.index, strip, x: at.x };
  }
  const body = el.closest(".dock-body");
  if (body) return { group: g, index: null, el: body };
  return null;
}

// Tabs are dragged with pointer events (mouse, pen and touch alike): press, move a few pixels, drop on a tab strip
// (reorder or move there), on a group's pane (move to its end) or, with no bottom group yet, on the "split" zone.
function dockWireDrag(btn, id) {
  let press = null;
  let suppressClick = false;
  btn.addEventListener("pointerdown", (e) => {
    if (e.button !== 0 || e.pointerType === "touch") return;
    press = { x: e.clientX, y: e.clientY, pointerId: e.pointerId, dragging: false };
    // Captured from the start, so a fast drag still reports its moves to this tab.
    try { btn.setPointerCapture(e.pointerId); } catch (err) { /* pointer already gone */ }
  });
  btn.addEventListener("pointermove", (e) => {
    if (!press || e.pointerId !== press.pointerId) return;
    if (!press.dragging) {
      if (Math.abs(e.clientX - press.x) + Math.abs(e.clientY - press.y) < 6) return;
      press.dragging = true;
      dockDragId = id;
      dockCloseMenu();
      const ghost = document.getElementById("dock-drag-ghost");
      ghost.textContent = btn.dataset.label;
      ghost.hidden = false;
      document.body.classList.add("is-tab-dragging");
      if (!dockMobile) document.getElementById("dock").classList.add("is-dragging");
    }
    const ghost = document.getElementById("dock-drag-ghost");
    ghost.style.left = (e.clientX + 10) + "px";
    ghost.style.top = (e.clientY + 8) + "px";
    document.querySelectorAll(".dock-drop-hover").forEach(el => el.classList.remove("dock-drop-hover"));
    const t = dockDropTarget(e.clientX, e.clientY);
    if (t && t.strip) {
      dockShowMarker(t.strip, t.x);
    } else {
      document.getElementById("dock-drop-marker").hidden = true;
      if (t && t.el) t.el.classList.add("dock-drop-hover");
    }
  });
  const finish = (e, drop) => {
    if (!press || e.pointerId !== press.pointerId) return;
    const wasDragging = press.dragging;
    press = null;
    if (!wasDragging) return;
    suppressClick = true;
    const t = drop ? dockDropTarget(e.clientX, e.clientY) : null;
    dockClearDrag();
    if (!t) return;
    if (t.split) {
      const others = dockLayout.A.filter(x => x !== id && dockIsShownTab(x)).length;
      if (others > 0) dockMove(id, "B");
    } else if (dockMobile) {
      dockMove(id, dockLayout.B.includes(id) ? "B" : "A", t.index);
    } else {
      // The last shown tab of group A stays: moving it would leave the top of the dock empty.
      const fromA = !dockLayout.B.includes(id);
      if (fromA && t.group === "B" && dockLayout.A.filter(x => x !== id && dockIsShownTab(x)).length === 0) return;
      dockMove(id, t.group, t.index);
    }
  };
  btn.addEventListener("pointerup", (e) => finish(e, true));
  btn.addEventListener("pointercancel", (e) => finish(e, false));
  btn.addEventListener("click", (e) => {
    if (suppressClick) {
      suppressClick = false;
      e.stopImmediatePropagation();
      e.preventDefault();
    }
  }, true);
}

// ---- Splitters ----------------------------------------------------------------------------------------------------

function dockSplitter(el, { onDrag, onKey, onReset }) {
  let start = null;
  el.addEventListener("pointerdown", (e) => {
    if (e.button !== 0) return;
    e.preventDefault();
    el.setPointerCapture(e.pointerId);
    start = { x: e.clientX, y: e.clientY, layout: Object.assign({}, dockLayout) };
    el.classList.add("is-dragging");
    document.body.classList.add("is-resizing");
  });
  el.addEventListener("pointermove", (e) => {
    if (!start) return;
    onDrag(e.clientX - start.x, e.clientY - start.y, start.layout, e);
    dockCssVars();
  });
  const end = () => {
    if (!start) return;
    start = null;
    el.classList.remove("is-dragging");
    document.body.classList.remove("is-resizing");
    dockSave();
  };
  el.addEventListener("pointerup", end);
  el.addEventListener("pointercancel", end);
  el.addEventListener("dblclick", () => {
    onReset();
    dockCssVars();
    dockSave();
  });
  el.addEventListener("keydown", (e) => {
    const d = { ArrowLeft: -1, ArrowUp: -1, ArrowRight: 1, ArrowDown: 1 }[e.key];
    if (!d) return;
    e.preventDefault();
    onKey(d * (e.shiftKey ? 4 : 1));
    dockCssVars();
    dockSave();
  });
}

function dockClampW(w) {
  const max = Math.max(DOCK_LIMITS.dockMin, Math.min(DOCK_LIMITS.dockMax, window.innerWidth - 420));
  return Math.max(DOCK_LIMITS.dockMin, Math.min(max, w));
}

function dockWireSplitters() {
  const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
  dockSplitter(document.getElementById("split-dock"), {
    onDrag: (dx, dy, s) => { dockLayout.dockW = dockClampW(s.dockW - dx); },
    onKey: (d) => { dockLayout.dockW = dockClampW(dockLayout.dockW - d * 16); },
    onReset: () => { dockLayout.dockW = dockDefaultLayout().dockW; }
  });
  const core = document.getElementById("core-col");
  dockSplitter(document.getElementById("split-core"), {
    onDrag: (dx, dy, s) => {
      const h = core.getBoundingClientRect().height || 1;
      dockLayout.core = clamp(s.core + dy / h, DOCK_LIMITS.coreMin, DOCK_LIMITS.coreMax);
    },
    onKey: (d) => { dockLayout.core = clamp(dockLayout.core + d * 0.02, DOCK_LIMITS.coreMin, DOCK_LIMITS.coreMax); },
    onReset: () => { dockLayout.core = 0.7; }
  });
  const dock = document.getElementById("dock");
  dockSplitter(document.getElementById("split-dock-ab"), {
    onDrag: (dx, dy, s) => {
      const h = dock.getBoundingClientRect().height || 1;
      dockLayout.ab = clamp(s.ab + dy / h, DOCK_LIMITS.abMin, DOCK_LIMITS.abMax);
    },
    onKey: (d) => { dockLayout.ab = clamp(dockLayout.ab + d * 0.02, DOCK_LIMITS.abMin, DOCK_LIMITS.abMax); },
    onReset: () => { dockLayout.ab = 0.55; }
  });
}

// ---- Start-up -----------------------------------------------------------------------------------------------------

function dockInit() {
  const panes = document.querySelectorAll(".dock-pane[data-tab]");
  panes.forEach(p => {
    const id = p.dataset.tab;
    dockTabs.push(id);
    dockPanes[id] = p;
    p.setAttribute("role", "tabpanel");
    p.setAttribute("aria-labelledby", "tab-" + id);
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "dock-tab";
    btn.id = "tab-" + id;
    btn.dataset.tab = id;
    btn.dataset.label = p.dataset.label;
    btn.setAttribute("role", "tab");
    btn.setAttribute("aria-controls", p.id);
    btn.title = (p.dataset.title || p.dataset.label) + " (drag to reorder or move to the other group; right-click for options)";
    const icon = document.createElement("span");
    icon.className = "dock-tab-icon";
    icon.textContent = p.dataset.icon || "";
    const lbl = document.createElement("span");
    lbl.className = "dock-tab-label";
    lbl.textContent = p.dataset.label;
    btn.append(icon, lbl);
    // Only tabs that show a badge reserve room for one (data-badge on the pane).
    if (p.hasAttribute("data-badge")) {
      const badge = document.createElement("span");
      badge.className = "tab-badge is-empty";
      btn.__badge = badge;
      btn.appendChild(badge);
    }
    btn.addEventListener("click", () => dockActivate(id));
    btn.addEventListener("contextmenu", (e) => {
      e.preventDefault();
      dockOpenMenu(dockTabMenuItems(id), btn, e.clientX, e.clientY);
    });
    btn.addEventListener("keydown", (e) => {
      if (e.key === "ArrowRight" || e.key === "ArrowLeft") {
        e.preventDefault();
        const strip = btn.parentElement;
        const btns = Array.from(strip.children).filter(b => !b.hidden);
        const i = btns.indexOf(btn) + (e.key === "ArrowRight" ? 1 : -1);
        const next = btns[(i + btns.length) % btns.length];
        if (next) dockActivate(next.dataset.tab, { focus: true });
      } else if (e.key === "ContextMenu" || (e.shiftKey && e.key === "F10")) {
        e.preventDefault();
        const r = btn.getBoundingClientRect();
        dockOpenMenu(dockTabMenuItems(id), btn, r.left, r.bottom + 2);
      }
    });
    dockWireDrag(btn, id);
    dockButtons[id] = btn;
  });

  document.querySelectorAll(".dock-more").forEach(more => {
    more.addEventListener("click", () => {
      const g = more.closest(".dock-group").dataset.group;
      const id = dockMobile ? dockEffective().active.A : dockLayout.active[g];
      if (!id) return;
      const r = more.getBoundingClientRect();
      dockOpenMenu(dockTabMenuItems(id), more, r.right - 200, r.bottom + 2);
    });
  });

  document.addEventListener("pointerdown", (e) => {
    if (dockMenuOpen && !dockMenuOpen.menu.contains(e.target) && e.target !== dockMenuOpen.anchor) dockCloseMenu();
  }, true);
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && dockMenuOpen) {
      const anchor = dockMenuOpen.anchor;
      dockCloseMenu();
      if (anchor) anchor.focus();
      e.stopPropagation();
    }
  }, true);
  window.addEventListener("blur", dockCloseMenu);

  const mq = window.matchMedia ? window.matchMedia(DOCK_MOBILE_QUERY) : null;
  dockMobile = !!(mq && mq.matches);
  dockLayout = dockLoad();
  dockLayout.dockW = dockClampW(dockLayout.dockW);
  if (mq) {
    const onChange = () => {
      dockMobile = mq.matches;
      dockCloseMenu();
      dockApply();
      if (typeof isFitMode !== "undefined" && isFitMode) recalculateFitZoom();
    };
    if (mq.addEventListener) mq.addEventListener("change", onChange);
    else if (mq.addListener) mq.addListener(onChange);
  }
  window.addEventListener("resize", () => {
    const w = dockClampW(dockLayout.dockW);
    if (w !== dockLayout.dockW) {
      dockLayout.dockW = w;
      dockCssVars();
    }
  });
  if (typeof ResizeObserver !== 'undefined') {
    let queued = false;
    const ro = new ResizeObserver(() => {
      if (queued) return;
      queued = true;
      requestAnimationFrame(() => { queued = false; dockFitStrips(); });
    });
    document.querySelectorAll('.dock-tabs').forEach(s => ro.observe(s));
  }
  dockWireSplitters();
  dockApply();
  document.body.classList.add("dock-ready");
}
