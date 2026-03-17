const WS_URL = "ws://localhost:8080/ws/metrics";

const connectionStatusEl = document.getElementById("connectionStatus");
const currentDateEl = document.getElementById("currentDate");
const themeToggleBtn = document.getElementById("themeToggle");
const totalCpuValueEl = document.getElementById("totalCpuValue");
const responsivenessValueEl = document.getElementById("responsivenessValue");
const memoryTextEl = document.getElementById("memoryText");
const memoryBarFillEl = document.getElementById("memoryBarFill");
const coresRowEl = document.getElementById("coresRow");
const processTableBodyEl = document.querySelector("#processTable tbody");
const viewMainEl = document.getElementById("viewMain");
const viewProcessesEl = document.getElementById("viewProcesses");
const allProcessesTableBodyEl = document.querySelector("#allProcessesTable tbody");
const allProcessesSearchEl = document.getElementById("allProcessesSearch");

let socket = null;
let lastProcessesData = null;

const PRIORITY_ORDER = ["idle", "below normal", "normal", "above normal", "high", "realtime"];
let processSortBy = "name";
let processSortDir = "asc";

function priorityRank(p) {
  let s = String(p || "").toLowerCase().replace(/_/g, " ").trim();
  if (s === "real time") s = "realtime";
  const i = PRIORITY_ORDER.indexOf(s);
  return i >= 0 ? i : PRIORITY_ORDER.length;
}

function compareProcesses(a, b) {
  let va, vb;
  switch (processSortBy) {
    case "name":
      va = (a.name || "").toLowerCase();
      vb = (b.name || "").toLowerCase();
      return processSortDir === "asc" ? va.localeCompare(vb) : vb.localeCompare(va);
    case "owner":
      va = (a.owner || "").toLowerCase();
      vb = (b.owner || "").toLowerCase();
      return processSortDir === "asc" ? va.localeCompare(vb) : vb.localeCompare(va);
    case "pid":
      va = Number(a.pid) || 0;
      vb = Number(b.pid) || 0;
      return processSortDir === "asc" ? va - vb : vb - va;
    case "priority":
      va = priorityRank(a.priority);
      vb = priorityRank(b.priority);
      return processSortDir === "asc" ? va - vb : vb - va;
    case "cpu_percent":
      va = Number(a.cpu_percent) || 0;
      vb = Number(b.cpu_percent) || 0;
      return processSortDir === "asc" ? va - vb : vb - va;
    case "cpu_time_100ns":
      va = Number(a.cpu_time_100ns) || 0;
      vb = Number(b.cpu_time_100ns) || 0;
      return processSortDir === "asc" ? va - vb : vb - va;
    case "location":
      va = (a.location || "").toLowerCase();
      vb = (b.location || "").toLowerCase();
      return processSortDir === "asc" ? va.localeCompare(vb) : vb.localeCompare(va);
    default:
      return 0;
  }
}

function sortProcesses(list) {
  return list.slice().sort(compareProcesses);
}

function updateSortUI() {
  document.querySelectorAll(".processTable thead th[data-sort]").forEach((th) => {
    const key = th.getAttribute("data-sort");
    const chevron = th.querySelector(".sort-chevron");
    if (!chevron) return;
    if (key === processSortBy) {
      chevron.textContent = processSortDir === "asc" ? " ▲" : " ▼";
      chevron.classList.add("active");
    } else {
      chevron.textContent = " ▲▼";
      chevron.classList.remove("active");
    }
  });
}
let cpuHistory = [];
let memoryHistory = [];
let responsivenessHistory = [];
const MAX_POINTS = 30; // ~25-30 seconds at 1s updates

function setConnectionStatus(text, cssClass) {
  connectionStatusEl.textContent = text;
  connectionStatusEl.className = cssClass;
}

function setCurrentDate() {
  if (!currentDateEl) return;
  const now = new Date();
  const options = {
    weekday: "short",
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  };
  currentDateEl.textContent = now.toLocaleString(undefined, options);
}

const THEME_STORAGE_KEY = "wasp-system-monitor-theme";

function applyTheme(theme) {
  const root = document.body;
  if (!root) return;
  if (theme === "light") {
    root.classList.add("light-theme");
  } else {
    root.classList.remove("light-theme");
  }
}

function initTheme() {
  let stored = null;
  try {
    stored = window.localStorage.getItem(THEME_STORAGE_KEY);
  } catch (_) {}

  if (stored === "light" || stored === "dark") {
    applyTheme(stored);
    return;
  }

  const prefersLight =
    window.matchMedia &&
    window.matchMedia("(prefers-color-scheme: light)").matches;
  applyTheme(prefersLight ? "light" : "dark");
}

function toggleTheme() {
  const isLight = document.body.classList.toggle("light-theme");
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, isLight ? "light" : "dark");
  } catch (_) {}
}

function connectWebSocket() {
  socket = new WebSocket(WS_URL);

  socket.onopen = () => {
    setConnectionStatus("Connected", "status-ok");
  };

  socket.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      updateDashboard(data);
    } catch (err) {
      console.error("Failed to parse JSON message", err);
    }
  };

  socket.onclose = () => {
    setConnectionStatus("Disconnected – retrying…", "status-bad");
    setTimeout(connectWebSocket, 5000);
  };

  socket.onerror = () => {
    setConnectionStatus("Error – retrying…", "status-bad");
    socket.close();
  };
}

function updateDashboard(data) {
  updateSummaryPanels(data);
  updateCores(data);
  updateProcesses(data);
  if (Array.isArray(data.processes)) lastProcessesData = data;
  updateAllProcesses(lastProcessesData);
  updateCpuHistory(data);
}

function updateSummaryPanels(data) {
  if (data.cpu) {
    if (typeof data.cpu.cpu_usage_percent === "number") {
      totalCpuValueEl.textContent = `${data.cpu.cpu_usage_percent.toFixed(1)}%`;
    }
    if (typeof data.cpu.system_responsiveness_percent === "number") {
      responsivenessValueEl.textContent = `${data.cpu.system_responsiveness_percent.toFixed(
        1
      )}%`;
    }
  }

  if (data.memory && typeof data.memory.memory_usage_percent === "number") {
    const usedPercent = data.memory.memory_usage_percent;
    const totalBytes = data.memory.total_bytes;
    const usedBytes = data.memory.used_bytes;

    if (memoryTextEl) {
      if (typeof totalBytes === "number" && typeof usedBytes === "number") {
        const bytesToGiB = (bytes) => bytes / (1024 * 1024 * 1024);
        const usedGiB = bytesToGiB(usedBytes);
        const totalGiB = bytesToGiB(totalBytes);
        memoryTextEl.textContent = `${usedPercent.toFixed(0)}% (${usedGiB.toFixed(
          1
        )} / ${totalGiB.toFixed(1)} GiB)`;
      } else {
        memoryTextEl.textContent = `${usedPercent.toFixed(0)}%`;
      }
    }

    if (memoryBarFillEl) {
      const clamped = Math.max(0, Math.min(100, usedPercent));
      memoryBarFillEl.style.width = `${clamped}%`;
    }
  }
}

function updateCores(data) {
  if (!Array.isArray(data.cpu_cores)) return;

  coresRowEl.innerHTML = "";
  data.cpu_cores.forEach((core) => {
    const usage = typeof core.core_usage_percent === "number" ? core.core_usage_percent : 0;
    const coreEl = document.createElement("div");
    coreEl.className = "core";
    coreEl.innerHTML = `
      <div class="core-label">CPU ${core.core_index}</div>
      <div class="core-gauge">
        <div class="core-gauge-fill" style="--usage:${usage};"></div>
        <div class="core-gauge-text">${usage.toFixed(0)}%</div>
      </div>
    `;
    coresRowEl.appendChild(coreEl);
  });
}

function updateProcesses(data) {
  if (!Array.isArray(data.processes)) return;

  const filtered = data.processes.filter((p) => (p.cpu_percent || 0) > 0);
  const sorted = sortProcesses(filtered);

  processTableBodyEl.innerHTML = "";
  sorted.forEach((proc) => {
    const tr = document.createElement("tr");
    if ((proc.cpu_percent || 0) > 50) {
      tr.classList.add("high-usage");
    }
    tr.innerHTML = `
      <td>${proc.name || ""}</td>
      <td>${proc.owner || ""}</td>
      <td>${proc.pid || ""}</td>
      <td>${proc.priority || ""}</td>
      <td>${(proc.cpu_percent || 0).toFixed(1)}</td>
      <td>${proc.cpu_time_100ns || ""}</td>
      <td>${proc.location || ""}</td>
    `;
    processTableBodyEl.appendChild(tr);
  });
}

function updateAllProcesses(data) {
  if (!allProcessesTableBodyEl || !Array.isArray(data?.processes)) return;

  const searchTerm = (allProcessesSearchEl?.value || "").toLowerCase();
  const filtered = data.processes.filter((p) => {
    const haystack = `${p.name || ""} ${p.owner || ""} ${p.pid || ""} ${p.location || ""}`.toLowerCase();
    return haystack.includes(searchTerm);
  });
  const sorted = sortProcesses(filtered);

  allProcessesTableBodyEl.innerHTML = "";
  sorted.forEach((proc) => {
    const tr = document.createElement("tr");
    if ((proc.cpu_percent || 0) > 50) {
      tr.classList.add("high-usage");
    }
    tr.innerHTML = `
      <td>${proc.name || ""}</td>
      <td>${proc.owner || ""}</td>
      <td>${proc.pid || ""}</td>
      <td>${proc.priority || ""}</td>
      <td>${(proc.cpu_percent || 0).toFixed(1)}</td>
      <td>${proc.cpu_time_100ns || ""}</td>
      <td>${proc.location || ""}</td>
    `;
    allProcessesTableBodyEl.appendChild(tr);
  });
}

function updateCpuHistory(data) {
  if (data.cpu && typeof data.cpu.cpu_usage_percent === "number") {
    cpuHistory.push(data.cpu.cpu_usage_percent);
    if (cpuHistory.length > MAX_POINTS) {
      cpuHistory.shift();
    }
  }

  if (data.memory && typeof data.memory.memory_usage_percent === "number") {
    memoryHistory.push(data.memory.memory_usage_percent);
    if (memoryHistory.length > MAX_POINTS) {
      memoryHistory.shift();
    }
  }

  if (
    data.cpu &&
    typeof data.cpu.system_responsiveness_percent === "number"
  ) {
    responsivenessHistory.push(data.cpu.system_responsiveness_percent);
    if (responsivenessHistory.length > MAX_POINTS) {
      responsivenessHistory.shift();
    }
  }

  if (
    cpuHistory.length === 0 &&
    memoryHistory.length === 0 &&
    responsivenessHistory.length === 0
  )
    return;
  drawCpuHistory();
}

function drawCpuHistory() {
  const canvas = document.getElementById("cpuUsageChart");
  if (!canvas) return;
  const ctx = canvas.getContext("2d");

  const width = (canvas.width = canvas.clientWidth || 600);
  const height = (canvas.height = canvas.clientHeight || 200);

  ctx.clearRect(0, 0, width, height);

  const maxLen = Math.max(
    cpuHistory.length,
    memoryHistory.length,
    responsivenessHistory.length
  );
  if (maxLen < 2) return;

  // CPU line (orange)
  if (cpuHistory.length > 0) {
    ctx.strokeStyle = "#f39c12";
    ctx.lineWidth = 2;
    ctx.beginPath();

    cpuHistory.forEach((value, index) => {
      const x = (index / (MAX_POINTS - 1)) * width;
      const y = height - (value / 100) * height;
      if (index === 0) {
        ctx.moveTo(x, y);
      } else {
        ctx.lineTo(x, y);
      }
    });

    ctx.stroke();
  }

  // Memory line (blue)
  if (memoryHistory.length > 0) {
    ctx.strokeStyle = "#4da3ff";
    ctx.lineWidth = 2;
    ctx.beginPath();

    memoryHistory.forEach((value, index) => {
      const x = (index / (MAX_POINTS - 1)) * width;
      const y = height - (value / 100) * height;
      if (index === 0) {
        ctx.moveTo(x, y);
      } else {
        ctx.lineTo(x, y);
      }
    });

    ctx.stroke();
  }

  // Responsiveness line (dotted red)
  if (responsivenessHistory.length > 0) {
    ctx.strokeStyle = "#ff5252";
    ctx.lineWidth = 1.5;
    ctx.setLineDash([4, 3]);
    ctx.beginPath();

    responsivenessHistory.forEach((value, index) => {
      const x = (index / (MAX_POINTS - 1)) * width;
      const y = height - (value / 100) * height;
      if (index === 0) {
        ctx.moveTo(x, y);
      } else {
        ctx.lineTo(x, y);
      }
    });

    ctx.stroke();
    ctx.setLineDash([]);
  }

  // Legend in top-left
  const legendX = 8;
  let legendY = 8;
  const legendGap = 14;

  ctx.font = "11px system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
  ctx.textBaseline = "top";

  if (cpuHistory.length > 0) {
    ctx.fillStyle = "#f39c12";
    ctx.fillRect(legendX, legendY + 4, 12, 3);
    ctx.fillStyle = "#ffffff";
    ctx.fillText("CPU %", legendX + 18, legendY);
    legendY += legendGap;
  }

  if (memoryHistory.length > 0) {
    ctx.fillStyle = "#4da3ff";
    ctx.fillRect(legendX, legendY + 4, 12, 3);
    ctx.fillStyle = "#ffffff";
    ctx.fillText("Memory %", legendX + 18, legendY);
    legendY += legendGap;
  }

  if (responsivenessHistory.length > 0) {
    ctx.fillStyle = "#ff5252";
    ctx.fillRect(legendX, legendY + 4, 12, 3);
    ctx.fillStyle = "#ffffff";
    ctx.fillText("Responsiveness %", legendX + 18, legendY);
  }
}

if (themeToggleBtn) {
  themeToggleBtn.addEventListener("click", toggleTheme);
}

document.querySelectorAll(".nav-item[data-view]").forEach((btn) => {
  btn.addEventListener("click", () => {
    const view = btn.getAttribute("data-view");
    if (view === "memory" || view === "export") {
      return;
    }
    document.querySelectorAll(".nav-item[data-view]").forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    if (viewMainEl) viewMainEl.classList.toggle("active", view === "main");
    if (viewProcessesEl) viewProcessesEl.classList.toggle("active", view === "processes");
    if (view === "main") {
      requestAnimationFrame(() => drawCpuHistory());
    }
  });
});

document.querySelectorAll(".processTable").forEach((table) => {
  table.addEventListener("click", (e) => {
    const th = e.target.closest("th.sortable[data-sort]");
    if (!th) return;
    const key = th.getAttribute("data-sort");
    if (processSortBy === key) {
      processSortDir = processSortDir === "asc" ? "desc" : "asc";
    } else {
      processSortBy = key;
      processSortDir = "asc";
    }
    updateSortUI();
    if (lastProcessesData) {
      updateProcesses(lastProcessesData);
      updateAllProcesses(lastProcessesData);
    }
  });
});

if (allProcessesSearchEl) {
  allProcessesSearchEl.addEventListener("input", () => {
    if (lastProcessesData) updateAllProcesses(lastProcessesData);
  });
}

updateSortUI();
initTheme();
setCurrentDate();
setInterval(setCurrentDate, 60 * 1000);
connectWebSocket();

