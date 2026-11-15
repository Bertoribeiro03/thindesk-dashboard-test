// Chrome headless + CDP via WebSocket nativo do Node 22+. Nenhum pacote npm.
import { spawn } from "node:child_process";
import { once } from "node:events";
import { existsSync, mkdirSync, writeFileSync, openSync, closeSync, readFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { createServer } from "node:net";
import { dirname, resolve, join } from "node:path";
import { fileURLToPath } from "node:url";
import assert from "node:assert/strict";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const api = join(root, "api");
const runtime = join(root, ".runtime");
const evidence = join(root, "docs/evidencias");
const stamp = new Intl.DateTimeFormat("sv-SE", { timeZone: "America/Sao_Paulo" }).format(new Date());
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, "bin", process.platform === "win32" ? "java.exe" : "java") : "java";
const candidates = [process.env.CHROME_PATH, "C:/Program Files/Google/Chrome/Application/chrome.exe", "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe", "/usr/bin/google-chrome", "/usr/bin/chromium"].filter(Boolean);
const chrome = candidates.find(existsSync);
assert(chrome, "Chrome/Edge headless não encontrado; registrar checklist não executado.");
mkdirSync(runtime, { recursive: true });
mkdirSync(evidence, { recursive: true });
const logFd = openSync(join(runtime, "browser-spring-server.log"), "w");
const environment = { ...process.env, THINDESK_DB_PATH: join(runtime, `browser-spring-${Date.now()}.db`), THINDESK_DATA_SOURCE: "local_sqlite", THINDESK_INITIALIZE: "true", THINDESK_SEED_EXAMPLE: "true", THINDESK_SECURITY_ENABLED: "false" };
let server, browser, socket;
let nextId = 1;
const pending = new Map();
const events = [];
const checks = [];
const report = { data: new Date().toISOString(), navegador: chrome, jar_sha256: createHash("sha256").update(readFileSync(join(root,"target/thindesk-1.1.0-SNAPSHOT.jar"))).digest("hex"), verificacoes: checks };
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
  await new Promise((done, reject) => { listener.once("error", reject); listener.listen(8081, "127.0.0.1", done); });
  await new Promise((done) => listener.close(done));
  server = spawn(java, ["-jar", join(root,"target/thindesk-1.1.0-SNAPSHOT.jar"), "--server.port=8081", "--server.servlet.context-path=/central"], { cwd: root, env: environment, windowsHide: true, stdio: ["ignore", logFd, logFd] });
  await waitFor(async () => { if (server.exitCode !== null || server.signalCode !== null) throw new Error("O processo Java encerrou antes de iniciar a API."); try { return (await fetch("http://127.0.0.1:8081/central/api/v1/health")).ok; } catch { return false; } }, "início da API", 45000);
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
  const path = join(evidence, `spring_${name}_${stamp}.png`);
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
  await cdp("Page.navigate", { url: "http://127.0.0.1:8081/central/" });
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
  await evaluate('document.getElementById("sidebar-toggle").click()');
  assert(await evaluate('document.getElementById("sidebar").classList.contains("is-open") && document.getElementById("sidebar-toggle").getAttribute("aria-expanded") === "true" && !document.getElementById("sidebar-backdrop").hidden'));
  await evaluate('document.dispatchEvent(new KeyboardEvent("keydown",{key:"Escape"}))');
  assert(await evaluate('!document.getElementById("sidebar").classList.contains("is-open")'));
  record("Menu móvel abre e fecha com Escape", {largura:600,acessibilidade:true});

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
  const prefix = "/central";
  async function go(path, title) {
    await cdp("Page.navigate", { url: `http://127.0.0.1:8081${prefix}${path}` });
    await waitFor(() => evaluate(`document.readyState === "complete" && location.pathname === ${JSON.stringify(prefix+path)} && document.querySelector("h1")?.textContent.includes(${JSON.stringify(title)})`), `navegação ${path}`);
  }
  async function waitPath(path) { await waitFor(() => evaluate(`document.readyState === "complete" && location.pathname === ${JSON.stringify(prefix+path)}`), `redirecionamento ${path}`); }
  await go("/clientes", "Clientes");
  assert(await evaluate('document.querySelector(".page-shell").getBoundingClientRect().height >= innerHeight'));
  const contextual = await evaluate('({links:[...document.querySelectorAll("a[href]")].filter(item=>new URL(item.href).origin === location.origin).map(item=>new URL(item.href).pathname),scripts:[...document.scripts].filter(item=>item.src.includes(location.host)).map(item=>new URL(item.src).pathname),logo:document.querySelector(".sidebar-brand img").naturalWidth})');
  assert(contextual.links.length >= 7); assert(contextual.scripts.length >= 1);
  assert(contextual.links.every(path=>path.startsWith(prefix+"/"))); assert(contextual.scripts.every(path=>path.startsWith(prefix+"/"))); assert.equal(contextual.logo,500);
  record("Thymeleaf e recursos sob context path /central", contextual);
  await go("/clientes/novo", "Novo cliente");
  await evaluate('document.getElementById("nome").value="Cliente da verificação";document.getElementById("telefone").value="11999990000";document.getElementById("setor").value="Financeiro";document.querySelector("form.management-form").requestSubmit()');
  await waitPath("/clientes");
  assert(await evaluate('document.body.textContent.includes("Cliente da verificação")'));
  const editClient = await evaluate('[...document.querySelectorAll("tbody tr")].find(row=>row.textContent.includes("Cliente da verificação")).querySelector("a").getAttribute("href")');
  await go(editClient.slice(prefix.length), "Editar cliente");
  await evaluate('document.getElementById("nome").value="Cliente atualizado";document.querySelector("form.management-form").requestSubmit()'); await waitPath("/clientes");
  assert(await evaluate('document.body.textContent.includes("Cliente atualizado")'));
  await screenshot("clientes");
  await evaluate('[...document.querySelectorAll("tbody tr")].find(row=>row.textContent.includes("Cliente atualizado")).querySelector("button").click()');
  await waitFor(()=>evaluate('location.pathname.endsWith("/clientes") && !document.body.textContent.includes("Cliente atualizado")'),"exclusão de cliente");
  record("Criar, editar e excluir cliente em formulários Thymeleaf", {gravacaoReal:true,csrf:true});
  await go("/ajustes-horarios", "Horários de atendimento");
  await evaluate('document.getElementById("setor").value="Suporte";document.getElementById("diaSemana").value="Segunda-feira";document.getElementById("horarioInicio").value="18:00";document.getElementById("horarioFim").value="08:00";document.querySelector("form.management-form").requestSubmit()');
  await waitFor(()=>evaluate('document.querySelector(".form-errors")?.textContent.includes("O horário final deve ser posterior")'),"validação de horários");
  await evaluate('document.getElementById("horarioInicio").value="08:00";document.getElementById("horarioFim").value="18:00";document.querySelector("form.management-form").requestSubmit()');
  await waitFor(()=>evaluate('[...document.querySelectorAll("tbody tr")].some(row=>row.textContent.includes("Suporte") && row.textContent.includes("08:00"))'),"gravação de horário");
  await screenshot("horarios");
  record("Validação e persistência de horário",{intervaloInvalidoRejeitado:true,inicio:"08:00",fim:"18:00"});
  await go("/chamados/novo", "Novo chamado");
  await evaluate('document.getElementById("descricao").value="Chamado da verificação visual";document.getElementById("tipo").value="Rede";document.getElementById("status").value="em_andamento";document.getElementById("tecnico").value="Ana Souza";document.getElementById("usuario").value="Solicitante";document.getElementById("consideracoesTecnicas").value="Analisando a conexão.";document.querySelector("form.management-form").requestSubmit()');
  await waitPath("/chamados");
  assert(await evaluate('document.body.textContent.includes("Chamado da verificação visual")'));
  const editTicket=await evaluate('[...document.querySelectorAll("tbody tr")].find(row=>row.textContent.includes("Chamado da verificação visual")).querySelector("a").getAttribute("href")');
  await go(editTicket.slice(prefix.length),"Editar chamado");
  await evaluate('document.getElementById("status").value="concluido";document.getElementById("consideracoesTecnicas").value="Conexão restabelecida.";document.querySelector("form.management-form").requestSubmit()'); await waitPath("/chamados");
  assert(await evaluate('[...document.querySelectorAll("tbody tr")].some(row=>row.textContent.includes("Chamado da verificação visual") && row.textContent.includes("Concluído") && row.textContent.includes("Conexão restabelecida."))'));
  await screenshot("chamados");
  await go("/", "Thindesk Dashboard Administrativo"); await settled();
  assert.equal(await evaluate('document.getElementById("total").textContent'),"501");
  record("Criação e edição de chamado refletidas no dashboard", {totalAposCriacao:501,status:"concluido",consideracoes:"Conexão restabelecida."});
  for(const [path,title] of [["/tecnicos","Técnicos"],["/tipo-chamado","Tipos de chamado"],["/docs","API do Thindesk"]]) await go(path,title);
  await evaluate('document.getElementById("endpoint").value="/dashboard/summary";document.getElementById("query").value="status=xpto";document.getElementById("api-explorer").requestSubmit()');
  await waitFor(()=>evaluate('document.getElementById("api-result").textContent.includes("INVALID_STATUS")'),"explorador da API");
  record("Menu completo e consulta interativa da API",{statusEsperado:400,codigo:"INVALID_STATUS"});
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
  writeFileSync(join(evidence, `dashboard_spring_${stamp}.json`), JSON.stringify(report, null, 2), "utf8");
}
