"use strict";

const API_BASE = document.body.dataset.apiBase || "/api/v1";
const $ = (id) => document.getElementById(id);
const formatNumber = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 });
const formatDecimal = new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const statusLabels = { aberto: "Aberto", em_andamento: "Em andamento", concluido: "Concluído", cancelado: "Cancelado" };
const priorities = { baixa: "Baixa", media: "Média", alta: "Alta", critica: "Crítica" };
const charts = {};
let metadataLoaded = false;
let loading = false;
let page = 1;
let pages = 0;
let appliedFilters = {};
let granularity = "month";
let autoTimer;

function escapeHtml(value) {
  return String(value ?? "—").replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char]);
}

async function fetchJson(path) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(`${API_BASE}${path}`, { signal: controller.signal, headers: { Accept: "application/json" }, cache: "no-store" });
    const body = await response.json();
    if (!response.ok) {
      const error = new Error(body.error?.message || "Não foi possível consultar os dados.");
      error.apiError = true;
      throw error;
    }
    return body;
  } catch (error) {
    if (error.name === "AbortError") throw new Error("Tempo de resposta da API esgotado.");
    if (error.apiError) throw error;
    throw new Error("Não foi possível conectar à API.");
  } finally {
    clearTimeout(timeout);
  }
}

function showError(message) {
  $("error-message").textContent = message;
  $("error").hidden = false;
}

function setLoading(value) {
  loading = value;
  $("loading").hidden = !value;
  $("content").classList.toggle("is-loading", value);
  $("content").setAttribute("aria-busy", String(value));
  ["apply", "clear", "retry"].forEach((id) => { $(id).disabled = value; });
  $("prev").disabled = value || page <= 1;
  $("next").disabled = value || page >= pages;
}

function readFilters() {
  const start = $("start").value;
  const end = $("end").value;
  if (start && end && start > end) {
    showError("A data inicial deve ser anterior à final.");
    return false;
  }
  appliedFilters = {};
  for (const [key, value] of [["start", start], ["end", end], ["status", $("status").value], ["type_id", $("type-id").value]]) {
    if (value) appliedFilters[key] = value;
  }
  granularity = $("granularity").value;
  page = 1;
  return true;
}

function renderSummary(data) {
  for (const key of ["total", "abertos", "em_andamento", "concluidos", "cancelados", "taxa_resolucao_pct", "tempo_medio_resolucao_h"]) {
    $(key).textContent = data[key] === null ? "—" : (key.endsWith("_pct") || key.endsWith("_h") ? formatDecimal : formatNumber).format(data[key]);
  }
  $("empty").hidden = data.total !== 0;
}

function renderTickets(data) {
  pages = data.pages;
  $("tickets-body").innerHTML = data.items.length ? data.items.map((item) => {
    const statusClass = Object.hasOwn(statusLabels, item.status) ? item.status : "";
    const date = new Date(item.opened_at.replace(" ", "T"));
    const opened = Number.isNaN(date.getTime()) ? "—" : date.toLocaleString("pt-BR");
    return `<tr><td>${escapeHtml(item.id)}</td><td>${escapeHtml(item.title)}</td><td>${escapeHtml(item.type)}</td><td><span class="status ${statusClass}">${escapeHtml(statusLabels[item.status] || item.status)}</span></td><td>${escapeHtml(priorities[item.priority] || item.priority)}</td><td>${escapeHtml(item.technician)}</td><td>${escapeHtml(item.technical_notes)}</td><td>${escapeHtml(opened)}</td></tr>`;
  }).join("") : '<tr><td colspan="8" class="table-placeholder">Nenhum chamado nesta página.</td></tr>';
  $("page-info").textContent = `Página ${data.total ? data.page : 0} de ${data.pages} · ${formatNumber.format(data.total)} chamados`;
}

function replaceChart(id, config) {
  if (charts[id]) charts[id].destroy();
  charts[id] = new Chart($(id), config);
}

function renderCharts(series, distribution, types) {
  if (typeof Chart === "undefined") {
    showError("Biblioteca de gráficos não carregada (verifique a conexão).");
    return;
  }
  Chart.defaults.font.family = "system-ui, sans-serif";
  Chart.defaults.color = "#667085";
  const options = { responsive: true, maintainAspectRatio: false, animation: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: "#f0f2f5" }, border: { display: false } }, x: { grid: { display: false }, border: { display: false }, ticks: { maxTicksLimit: 12, maxRotation: 0 } } } };
  replaceChart("timeseries-chart", { type: "line", data: { labels: series.map((row) => row.period), datasets: [
    { label: "Abertos", data: series.map((row) => row.abertos), borderColor: "#f59e0b", backgroundColor: "#f59e0b10", borderWidth: 2, pointRadius: 2, tension: 0.25, fill: true },
    { label: "Atendidos", data: series.map((row) => row.atendidos), borderColor: "#10b981", backgroundColor: "#10b98110", borderWidth: 2, pointRadius: 2, tension: 0.25, fill: true }
  ] }, options });
  replaceChart("status-chart", { type: "doughnut", data: { labels: distribution.map((row) => `${row.label} (${formatNumber.format(row.pct)}%)`), datasets: [{ data: distribution.map((row) => row.count), backgroundColor: ["#f59e0b", "#3b82f6", "#10b981", "#9ca3af"], borderWidth: 4, borderColor: "#fff", hoverOffset: 5 }] }, options: { responsive: true, maintainAspectRatio: false, animation: false, cutout: "72%", plugins: { legend: { position: "bottom", labels: { usePointStyle: true, pointStyle: "circle", boxWidth: 7, padding: 16, font: { size: 11 } } } } } });
  replaceChart("types-chart", { type: "bar", data: { labels: types.map((row) => row.type), datasets: [{ data: types.map((row) => row.count), backgroundColor: "#2563eb", borderRadius: 4, maxBarThickness: 20 }] }, options: { responsive: true, maintainAspectRatio: false, animation: false, indexAxis: "y", plugins: { legend: { display: false } }, scales: { x: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: "#f0f2f5" }, border: { display: false } }, y: { grid: { display: false }, border: { display: false } } } } });
}

async function load() {
  if (loading || !metadataLoaded) return;
  setLoading(true);
  $("error").hidden = true;
  const params = new URLSearchParams(appliedFilters);
  const query = params.toString();
  const withParams = (extra = {}) => new URLSearchParams({ ...Object.fromEntries(params), ...extra }).toString();
  try {
    const [summary, series, distribution, types, tickets] = await Promise.all([
      fetchJson(`/dashboard/summary?${query}`),
      fetchJson(`/dashboard/timeseries?${withParams({ granularity })}`),
      fetchJson(`/dashboard/status-distribution?${query}`),
      fetchJson(`/dashboard/top-types?${withParams({ limit: 8 })}`),
      fetchJson(`/tickets?${withParams({ page, page_size: 10 })}`)
    ]);
    renderSummary(summary.data);
    renderTickets(tickets);
    renderCharts(series.data, distribution.data, types.data);
    $("updated-at").textContent = `Atualizado às ${new Date().toLocaleTimeString("pt-BR")}`;
  } catch (error) {
    showError(error.message);
  } finally {
    setLoading(false);
  }
}

async function init() {
  if (loading) return;
  setLoading(true);
  $("error").hidden = true;
  try {
    const [meta, health] = await Promise.all([fetchJson("/meta/filters"), fetchJson("/health")]);
    $("status").replaceChildren(new Option("Todos", ""), ...meta.data.statuses.map((item) => new Option(item.label, item.value)));
    $("type-id").replaceChildren(new Option("Todos", ""), ...meta.data.types.map((item) => new Option(item.name, item.id)));
    $("data-source").textContent = health.data_source === "local_sqlite" ? "Dados de exemplo (local)" : `Fonte: ${health.data_source}`;
    const { min, max } = meta.data.date_range;
    const prettyDate = (value) => value ? value.split("-").reverse().join("/") : "—";
    $("date-range").textContent = min && max ? `Dados de ${prettyDate(min)} a ${prettyDate(max)}` : "Sem registros na fonte local";
    metadataLoaded = true;
  } catch (error) {
    showError(error.message);
  } finally {
    setLoading(false);
  }
  if (metadataLoaded) await load();
}

function configureAutoRefresh() {
  clearInterval(autoTimer);
  if ($("auto-refresh").checked) autoTimer = setInterval(() => { if (metadataLoaded && !loading) load(); }, 60000);
}

$("filters").addEventListener("submit", (event) => { event.preventDefault(); if (!loading && readFilters()) load(); });
$("clear").addEventListener("click", () => { $("filters").reset(); if (readFilters()) load(); });
$("retry").addEventListener("click", () => metadataLoaded ? load() : init());
$("prev").addEventListener("click", () => { if (!loading && page > 1) { page--; load(); } });
$("next").addEventListener("click", () => { if (!loading && page < pages) { page++; load(); } });
$("auto-refresh").addEventListener("change", configureAutoRefresh);
configureAutoRefresh();
init();
