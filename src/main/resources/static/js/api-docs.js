"use strict";
const explorer = document.getElementById("api-explorer");
if (explorer) explorer.addEventListener("submit", async (event) => {
  event.preventDefault();
  const output = document.getElementById("api-result");
  const button = explorer.querySelector("button");
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  button.disabled = true;
  output.textContent = "Carregando…";
  try {
    const endpoint = document.getElementById("endpoint").value;
    const query = new URLSearchParams(document.getElementById("query").value.replace(/^\?/, ""));
    const response = await fetch(`${document.body.dataset.apiBase}${endpoint}?${query}`, { signal: controller.signal, headers: { Accept: "application/json" } });
    output.textContent = `HTTP ${response.status}\n${JSON.stringify(await response.json(), null, 2)}`;
  } catch (error) { output.textContent = error.name === "AbortError" ? "Tempo de resposta da API esgotado." : "Não foi possível conectar à API."; }
  finally { clearTimeout(timeout); button.disabled = false; }
});
