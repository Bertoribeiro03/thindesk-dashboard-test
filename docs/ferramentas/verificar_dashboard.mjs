// Chrome headless + CDP via WebSocket nativo do Node 22+. Nenhum pacote npm.
import { spawn } from "node:child_process";
import { once } from "node:events";
import { existsSync, mkdirSync, writeFileSync, openSync, closeSync } from "node:fs";
import { createServer } from "node:net";
import { dirname, resolve, join } from "node:path";
import { fileURLToPath } from "node:url";
import assert from "node:assert/strict";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const api = join(root, "api");
const runtime = join(api, ".runtime");
const evidence = join(root, "docs/evidencias");
const stamp = new Intl.DateTimeFormat("sv-SE", { timeZone: "America/Sao_Paulo" }).format(new Date());
const python = process.platform === "win32" ? join(api, ".venv/Scripts/python.exe") : join(api, ".venv/bin/python");
const candidates = [process.env.CHROME_PATH, "C:/Program Files/Google/Chrome/Application/chrome.exe", "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe", "/usr/bin/google-chrome", "/usr/bin/chromium"].filter(Boolean);
const chrome = candidates.find(existsSync);
assert(chrome, "Chrome/Edge headless não encontrado; registrar checklist não executado.");
mkdirSync(runtime, { recursive: true });
mkdirSync(evidence, { recursive: true });
const logFd = openSync(join(runtime, "browser-server.log"), "w");
const environment = { ...process.env, PYTHONUTF8: "1", THINDESK_DB_PATH: "data/thindesk.db", THINDESK_DATA_SOURCE: "local_sqlite" };
let server, browser, socket;
let nextId = 1;
const pending = new Map();
const events = [];
const checks = [];
const report = { data: new Date().toISOString(), navegador: chrome, verificacoes: checks };
const sleep = (ms) => new Promise((done) => setTimeout(done, ms));
async function freePort() { const listener = createServer(); listener.listen(0, "127.0.0.1"); await once(listener, "listening"); const port = listener.address().port; await new Promise((done) => listener.close(done)); return port; }
async function waitFor(fn, description, timeout = 20000) {
  const start = Date.now();
  while (Date.now() - start < timeout) { if (await fn()) return; await sleep(100); }
  throw new Error(`Tempo esgotado: ${description}`);
}
async function startServer() {
  // A porta fixa é intencional: corresponde à execução documentada.
  const listener = createServer();
  await new Promise((done, reject) => { listener.once("error", reject); listener.listen(8000, "127.0.0.1", done); });
  await new Promise((done) => listener.close(done));
  server = spawn(python, ["-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", "8000"], { cwd: api, env: environment, windowsHide: true, stdio: ["ignore", logFd, logFd] });
  await waitFor(async () => { try { return (await fetch("http://127.0.0.1:8000/api/v1/health")).ok; } catch { return false; } }, "início da API");
}
async function stopServer() { if (server && server.exitCode === null && server.signalCode === null) { const stopped = once(server, "exit"); server.kill(); await stopped; } }
function cdp(method, params = {}) {
  const id = nextId++;
  return new Promise((done, reject) => {
    const timeout = setTimeout(() => { pending.delete(id); reject(new Error(`CDP sem resposta: ${method}`)); }, 30000);
    pending.set(id, { done, reject, timeout });
    socket.send(JSON.stringify({ id, method, params }));
  });
}
async function evaluate(expression) {
  const result = await cdp("Runtime.evaluate", { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  return result.result.value;
}
function record(name, details) { checks.push({ cenario: name, resultado: "APROVADO", observado: details }); console.log(`APROVADO: ${name} ${JSON.stringify(details)}`); }
async function settled() { await waitFor(() => evaluate('typeof loading !== "undefined" && !loading && metadataLoaded'), "carga do dashboard"); }
async function screenshot(name) {
  const { cssContentSize } = await cdp("Page.getLayoutMetrics");
  const result = await cdp("Page.captureScreenshot", { format: "png", captureBeyondViewport: true, clip: { x: 0, y: 0, width: cssContentSize.width, height: cssContentSize.height, scale: 1 } });
  const path = join(evidence, `${name}_${stamp}.png`);
  writeFileSync(path, Buffer.from(result.data, "base64"));
  return path;
}

try {
  await startServer();
  const debugPort = await freePort();
  browser = spawn(chrome, ["--headless=new", `--remote-debugging-port=${debugPort}`, `--user-data-dir=${join(runtime, `chrome-${Date.now()}`)}`, "--no-first-run", "--no-default-browser-check", "--disable-background-networking", "--disable-component-update", "--disable-extensions", "--disable-sync", "--window-size=1440,1100", "about:blank"], { windowsHide: true, stdio: "ignore" });
  let targets;
  await waitFor(async () => { try { targets = await (await fetch(`http://127.0.0.1:${debugPort}/json/list`)).json(); return targets.some((target) => target.type === "page"); } catch { return false; } }, "início do Chrome");
  const target = targets.find((item) => item.type === "page");
  socket = new WebSocket(target.webSocketDebuggerUrl);
  await once(socket, "open");
  socket.addEventListener("message", (event) => {
    const message = JSON.parse(event.data);
    if (message.id) {
      const request = pending.get(message.id);
      if (request) { clearTimeout(request.timeout); pending.delete(message.id); message.error ? request.reject(new Error(JSON.stringify(message.error))) : request.done(message.result); }
    } else events.push(message);
  });
  await cdp("Page.enable"); await cdp("Runtime.enable"); await cdp("Network.enable");
  report.versaoNavegador = await cdp("Browser.getVersion");
  await cdp("Emulation.setDeviceMetricsOverride", { width: 1440, height: 1100, deviceScaleFactor: 1, mobile: false });
  await cdp("Page.navigate", { url: "http://127.0.0.1:8000/" });
  await settled();
  const initial = await evaluate('({total: $("total").textContent, cards: document.querySelectorAll(".indicator").length, linhas: $("tickets-body").rows.length, fonte: $("data-source").textContent, auto: $("auto-refresh").checked, atualizado: $("updated-at").textContent, chartVersion: typeof Chart === "undefined" ? null : Chart.version, graficos: Object.keys(charts).length, erro: $("error").hidden ? null : $("error-message").textContent})');
  assert.equal(initial.total, "500"); assert.equal(initial.cards, 7); assert.equal(initial.linhas, 10); assert.equal(initial.chartVersion, "4.4.1"); assert.equal(initial.graficos, 3); assert.equal(initial.erro, null); assert(initial.auto);
  record("Carga inicial e Chart.js real via CDN", initial);
  report.captura_desktop = await screenshot("dashboard_desktop");

  await evaluate('$("next").click()'); await settled();
  assert.equal(await evaluate("page"), 2);
  record("Paginação", await evaluate('({pagina: page, texto: $("page-info").textContent, anteriorHabilitado: !$("prev").disabled})'));
  await evaluate('$("type-id").value="3"; $("granularity").value="week"; $("filters").requestSubmit()'); await settled();
  const filtered = await evaluate('({pagina:page, filtros:appliedFilters, agrupamento:granularity, total:$("total").textContent, tipos: [...$("tickets-body").rows].map(row=>row.cells[2].textContent), periodos:charts["timeseries-chart"].data.labels})');
  assert.equal(filtered.pagina, 1); assert(filtered.tipos.every((value) => value === "Rede")); assert(filtered.periodos.every((value) => /^\d{4}-W\d{2}$/.test(value)));
  record("Filtro Rede com agrupamento semanal e retorno à página 1", filtered);

  const before = events.filter((event) => event.method === "Network.requestWillBeSent").length;
  await evaluate('$("start").value="2026-02-01"; $("end").value="2026-01-01"; $("filters").requestSubmit()');
  await sleep(200);
  assert.equal(await evaluate('$("error-message").textContent'), "A data inicial deve ser anterior à final.");
  assert.equal(events.filter((event) => event.method === "Network.requestWillBeSent").length, before);
  record("Período invertido bloqueado sem requisição HTTP", { mensagem: await evaluate('$("error-message").textContent'), requisicoesAdicionais: 0 });

  await evaluate('$("start").value="2020-01-01"; $("end").value="2020-01-31"; $("filters").requestSubmit()'); await settled();
  const empty = await evaluate('({total:$("total").textContent, vazioVisivel:!$("empty").hidden, media:$("tempo_medio_resolucao_h").textContent, anteriorDesabilitado:$("prev").disabled, proximoDesabilitado:$("next").disabled})');
  assert.equal(empty.total, "0"); assert(empty.vazioVisivel); assert.equal(empty.media, "—"); assert(empty.anteriorDesabilitado && empty.proximoDesabilitado);
  record("Estado vazio e valor nulo", empty);
  report.captura_vazio = await screenshot("dashboard_vazio");
  await evaluate('$("clear").click()'); await settled();
  assert.equal(await evaluate('$("total").textContent'), "500");
  await cdp("Emulation.setDeviceMetricsOverride", { width: 600, height: 1000, deviceScaleFactor: 1, mobile: false });
  await sleep(300);
  const mobile = await evaluate('({largura:innerWidth, larguraDocumento:document.documentElement.scrollWidth, colunasGraficos:getComputedStyle(document.querySelector(".charts")).gridTemplateColumns, tabelaRolavel:document.querySelector(".table-scroll").scrollWidth > document.querySelector(".table-scroll").clientWidth})');
  assert.equal(mobile.largura, 600); assert(mobile.larguraDocumento <= 600); assert(mobile.tabelaRolavel); assert.equal(mobile.colunasGraficos.split(" ").length, 1);
  record("Layout responsivo em 600 px", mobile);
  report.captura_600px = await screenshot("dashboard_600px");

  // Conteúdo malicioso é renderizado como texto; nenhum elemento é criado.
  const safe = await evaluate('renderTickets({items:[{id:1,title:"<img src=x onerror=alert(1)>",type:"<script>alert(1)</script>",status:"aberto",priority:"alta",technician:"<b>Ana</b>",technical_notes:"<svg onload=alert(1)>",opened_at:"2026-01-05 08:00:00"}],page:1,pages:1,total:1}); ({elementosInjetados:$("tickets-body").querySelectorAll("img,script,svg,b").length, texto:$("tickets-body").textContent})');
  assert.equal(safe.elementosInjetados, 0); assert(safe.texto.includes("<img")); record("Escape de conteúdo da API contra XSS", safe);
  await evaluate("load()"); await settled();
  await evaluate('window.chartOriginal=window.Chart; window.Chart=undefined; renderCharts([],[],[])');
  assert.equal(await evaluate('$("error-message").textContent'), "Biblioteca de gráficos não carregada (verifique a conexão).");
  record("Tratamento de Chart.js indisponível", { mensagem: await evaluate('$("error-message").textContent') });
  await evaluate("window.Chart=window.chartOriginal; undefined");
  await evaluate('$("auto-refresh").checked=false; $("auto-refresh").dispatchEvent(new Event("change"))');
  // Verifica ciclo real com timer de 60 s e preserva a mesma aba carregada.
  const countLoads = () => events.filter((event) => event.method === "Network.requestWillBeSent" && event.params.request.url.includes("/dashboard/summary")).length;
  const beforeAuto = countLoads();
  await evaluate('$("auto-refresh").checked=true; $("auto-refresh").dispatchEvent(new Event("change"))');
  await waitFor(async () => countLoads() > beforeAuto, "ciclo automático de 60 segundos", 65000);
  await settled();
  const autoAfter = countLoads();
  await evaluate('$("auto-refresh").checked=false; $("auto-refresh").dispatchEvent(new Event("change"))');
  record("Atualização automática real após 60 s", { consultasSummaryAntes: beforeAuto, consultasSummaryDepois: autoAfter, desligadaAoFinal: true });

  // Timeout real de 10 s: intercepta e segura todas as requisições da API.
  await cdp("Fetch.enable", { patterns: [{ urlPattern: "*/api/v1/*", requestStage: "Request" }] });
  await evaluate("void load()");
  await waitFor(() => evaluate('!loading && !$("error").hidden'), "timeout de 10 s", 13000);
  assert.equal(await evaluate('$("error-message").textContent'), "Tempo de resposta da API esgotado.");
  record("Timeout real de 10 segundos", { mensagem: await evaluate('$("error-message").textContent') });
  await cdp("Fetch.disable");

  await stopServer();
  await evaluate("void load()");
  await waitFor(() => evaluate('!loading && !$("error").hidden'), "erro com API parada");
  assert.equal(await evaluate('$("error-message").textContent'), "Não foi possível conectar à API.");
  record("Erro com API realmente parada", { mensagem: await evaluate('$("error-message").textContent'), processoApiEncerrado: server.exitCode !== null || server.signalCode !== null });
  report.captura_erro = await screenshot("dashboard_erro");
  await startServer();
  await evaluate('$("retry").click()'); await settled();
  assert(await evaluate('$("error").hidden && $("total").textContent === "500"'));
  record("Recuperação pelo botão Tentar novamente", { total: 500, erroVisivel: false });

  // Refaz navegação sem API para verificar recuperação dos próprios metadados.
  await cdp("Fetch.enable", { patterns: [{ urlPattern: "*/api/v1/*", requestStage: "Request" }] });
  const blockedHandler = (event) => { const item = JSON.parse(event.data); if (item.method === "Fetch.requestPaused") cdp("Fetch.failRequest", { requestId: item.params.requestId, errorReason: "ConnectionRefused" }).catch(() => {}); };
  socket.addEventListener("message", blockedHandler);
  await cdp("Page.reload", { ignoreCache: true });
  await waitFor(() => evaluate('typeof metadataLoaded !== "undefined" && !metadataLoaded && !loading && !$("error").hidden'), "erro inicial de metadados");
  socket.removeEventListener("message", blockedHandler); await cdp("Fetch.disable");
  await evaluate('$("retry").click()'); await settled();
  assert(await evaluate('metadataLoaded && $("error").hidden && $("total").textContent === "500"'));
  record("Tentar novamente refaz init quando faltam metadados", { metadadosCarregados: true, total: 500 });
  report.errosJavascript = events.filter((event) => event.method === "Runtime.exceptionThrown").map((event) => event.params.exceptionDetails);
  assert.equal(report.errosJavascript.length, 0);
  report.resultado = "APROVADO";
} catch (error) {
  report.resultado = "FALHOU"; report.falha = error.stack; console.error(error); process.exitCode = 1;
} finally {
  if (socket?.readyState === 1) { try { await cdp("Browser.close"); } catch {} socket.close(); }
  if (browser?.exitCode === null) { try { await Promise.race([once(browser, "exit"), sleep(3000)]); } finally { if (browser.exitCode === null) browser.kill(); } }
  await stopServer(); closeSync(logFd);
  report.servidoresEncerrados = true;
  writeFileSync(join(evidence, `dashboard_${stamp}.json`), JSON.stringify(report, null, 2), "utf8");
}
