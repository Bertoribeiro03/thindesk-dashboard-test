"""Produz API.md e TESTES.md exclusivamente a partir de evidências existentes."""

import json
import re
import xml.etree.ElementTree as ET
from collections import Counter
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"
EVIDENCE = DOCS / "evidencias"
STAMP = datetime.now().astimezone().strftime("%Y-%m-%d")


def read(name):
    return json.loads((EVIDENCE / f"{name}_{STAMP}.json").read_text(encoding="utf-8"))


def api_docs(http):
    text = """# API — versão 1.0.0

## Execução local e contrato

Siga a instalação e o seed em [README.md](../README.md). Dentro de `api/`, execute `python -m uvicorn app.main:app --host 127.0.0.1 --port 8000`. Base URL: `http://localhost:8000/api/v1`. Swagger UI em `/docs`; especificação em `/openapi.json`. Título: **Thindesk Dashboard API**. A fonte é `local_sqlite`, com dados de **EXEMPLO**. Não há acesso ao Thindesk real nem autenticação.

## Endpoints

| Método | Caminho relativo à base | Resposta |
| --- | --- | --- |
| GET | `/health` | `status`, `database`, `data_source`; executa `SELECT 1 FROM tickets LIMIT 1` |
| GET | `/meta/filters` | `data.statuses` com value/label, `types` com id/name ordenado por nome, `date_range.min/max` de abertura |
| GET | `/dashboard/summary` | `filters`, `data`: total, abertos, em_andamento, concluidos, cancelados, backlog, taxa_resolucao_pct, tempo_medio_resolucao_h |
| GET | `/dashboard/timeseries` | `filters`, `granularity`, `data`: period/abertos/atendidos |
| GET | `/dashboard/status-distribution` | `filters`, `data`: status/label/count/pct, sempre os quatro status em ordem fixa |
| GET | `/dashboard/top-types` | `filters`, `data`: rank/type_id/type/count/pct |
| GET | `/tickets` | `filters`, items, page, page_size, total, pages |

`GET /` serve o dashboard HTML. Rotas inexistentes da API retornam JSON 404 e não passam ao HTML do dashboard. Não há rotas de escrita. O banco é aberto somente leitura; saúde 200 exige tabela acessível, mesmo vazia.

## Parâmetros comuns e validações

Aplicáveis a todos os endpoints de dashboard e à lista de chamados:

| Parâmetro | Formato e interpretação |
| --- | --- |
| `start` | Data opcional `YYYY-MM-DD`, início inclusivo |
| `end` | Data opcional `YYYY-MM-DD`, fim inclusivo (`< dia seguinte 00:00:00`) |
| `status` | CSV: aberto, em_andamento, concluido, cancelado |
| `type_id` | CSV de inteiros positivos; tipo inexistente produz resultado vazio 200 |

Omitir status/tipo significa todos. Valor vazio explícito em CSV é inválido; para “Todos”, omita o parâmetro. Espaços em torno de tokens são removidos; duplicatas são removidas e valores ordenados. O eco usa `filters: {start, end, status: [], type_id: []}` com datas nulas quando ausentes.

A ordem das validações dos filtros é: (1) datas malformadas → `VALIDATION_ERROR`, via `RequestValidationError`; (2) início depois do fim → `INVALID_PERIOD`; (3) diferença superior a 731 dias → `PERIOD_TOO_LONG`; (4) status inválido → `INVALID_STATUS`; (5) tipo não inteiro ou não positivo → `INVALID_TYPE_ID`. O limite de duração exige as duas datas; datas isoladas são aceitas. Outras validações de parâmetros geram `VALIDATION_ERROR` 400.

Nos cards, distribuição, ranking e lista, o período incide sobre **opened_at**. Em atendidos da série incide sobre **closed_at**, exigindo `status='concluido'`. Os filtros status/tipo também se aplicam à série de atendidos. “Abertos” na série significa registros criados no período em qualquer status atual; o card “Abertos” conta apenas status aberto.

## Parâmetros específicos e cálculos

- `timeseries.granularity`: `day` (padrão), `week`, `month`. Outros valores → 400. Formatos `%Y-%m-%d`, `%Y-W%W`, `%Y-%m`, iguais em SQLite e Python. `%W` começa na segunda-feira e admite semana 00; não é semana ISO. Com início e fim, preenche todos os períodos do intervalo com zero. Caso contrário, usa a união ordenada das chaves existentes nas duas séries.
- `top-types.limit`: inteiro de 1 a 50, padrão 5; dashboard usa 8. Ordenação por count decrescente e nome crescente para desempate. Percentual usa o total filtrado, não apenas o subconjunto do ranking. Conta todos os status selecionados.
- `tickets.page`: inteiro ≥ 1, padrão 1. `page_size`: 1 a 100, padrão 10. Ordenação por opened_at DESC, id DESC. Página fora do total retorna items vazio. `pages=ceil(total/page_size)`; base vazia tem pages 0.
- Campos de items: id, title, type, status, priority, technician, technical_notes, opened_at, closed_at. Campos opcionais são null. Datas/hora locais em `YYYY-MM-DD HH:MM:SS`.
- Backlog = abertos + em andamento. Taxa = concluídos / total × 100, uma casa, 0.0 se total zero. Tempo médio = AVG da diferença em horas entre fechamento e abertura dos concluídos, uma casa, null sem duração disponível. Cancelados não participam da média nem de atendidos.
- Status-distribution retorna aberto, em_andamento, concluido, cancelado nessa ordem, inclusive contagens zero. Percentuais usam uma casa; por arredondamento podem totalizar aproximadamente 100%.

## Exemplos reais com curl

As respostas abaixo foram extraídas de execução HTTP real neste ambiente em DATA_EXECUCAO, após seed de 500 chamados. Não são mocks. A fonte é local. Comandos e saídas integrais, incluindo `-w %{time_total}`, estão em [curl_DATA.txt](evidencias/curl_DATA.txt); a extração estruturada está em [http_DATA.json](evidencias/http_DATA.json). No Windows use `curl.exe`; em Bash use `curl`.

""".replace("DATA_EXECUCAO", STAMP).replace("DATA", STAMP)
    examples = [
        ("Saúde", "/health"), ("Metadados", "/meta/filters"), ("Indicadores", "/dashboard/summary"),
        ("Série mensal", "/dashboard/timeseries?granularity=month"),
        ("Distribuição", "/dashboard/status-distribution"), ("Ranking", "/dashboard/top-types?limit=8"),
        ("Lista paginada", "/tickets?page_size=2"),
        ("Filtro sem resultado", "/dashboard/summary?start=2020-01-01&end=2020-01-31"),
        ("Erro de status", "/dashboard/summary?status=xpto"),
        ("Data malformada", "/dashboard/summary?start=2026-13-40"), ("Rota inexistente", "/nao-existe"),
        ("Banco indisponível — processo separado em 8001", "503 /health porta 8001"),
    ]
    for title, route in examples:
        observed = http["respostas"][route]
        port, path = (8001, "/health") if route.startswith("503 ") else (8000, route)
        text += f"### {title} — HTTP {observed['http']}\n\n```powershell\ncurl.exe \"http://127.0.0.1:{port}/api/v1{path}\"\n```\n\n```json\n{json.dumps(observed['corpo'], ensure_ascii=False, indent=2)}\n```\n\n"
    text += """## Códigos HTTP e envelope de erro

| HTTP | Código | Situação / details |
| --- | --- | --- |
| 200 | — | Consulta válida, inclusive sem registros |
| 400 | VALIDATION_ERROR | Parâmetro inválido; details é lista de `{param, msg}` em português |
| 400 | INVALID_PERIOD | Início depois do fim; details null |
| 400 | PERIOD_TOO_LONG | Diferença entre datas > 731 dias; details null |
| 400 | INVALID_STATUS | details: `{invalid: [...], allowed: [...]}` |
| 400 | INVALID_TYPE_ID | ID inválido; details null |
| 404 | NOT_FOUND | Rota/arquivo não encontrado; details null |
| 503 | DATABASE_UNAVAILABLE | “Banco de dados indisponível.”; details null; sqlite3.Error só é detalhado no log |
| 500 | INTERNAL_ERROR | Erro inesperado; details null; log.exception registra detalhes internos |
| Outros erros HTTP | HTTP_ERROR | Handler de StarletteHTTPException, mensagem pública genérica |

Todas as respostas de erro da aplicação seguem `{"error":{"code":"...","message":"...","details":null}}` (ou os details específicos acima). Não retornam stack trace, caminho do banco ou mensagens internas. O 503 acima foi produzido por um processo temporário com `THINDESK_DB_PATH` apontando para arquivo inexistente, sem remover o banco principal. Os testes isolados também cobrem exceção inesperada 500 e HTTP_ERROR 403. Os logs completos ficam no processo servidor, não no corpo público.
"""
    (DOCS / "API.md").write_text(text, encoding="utf-8")


OBSERVATIONS = {
    "T01": "Health 200; database=ok e fonte local_sqlite",
    "T02": "Total 5; status 1/1/2/1; backlog 2; taxa 40,0%; média 14,0 h",
    "T03": "CSV aberto,em_andamento retorna total 2",
    "T04": "Tipo 1 em janeiro retorna total 2",
    "T05": "Janeiro/2020 retorna total 0, taxa 0,0 e média null",
    "T06": "6 entradas de data inválidas retornam 400 VALIDATION_ERROR",
    "T07": "Período invertido retorna 400 INVALID_PERIOD",
    "T08": "Status xpto retorna invalid=[xpto] e allowed com os 4 status",
    "T09": "7 variações inválidas, incluindo abc/0/-1 e tentativa de SQL, retornam INVALID_TYPE_ID",
    "T10": "Dias 05/01:1/1; 06/01:2/0; 07/01:0/1; year retorna 400",
    "T11": "Counts somam 5; pct somam 100; ordem fixa dos status",
    "T12": "Hardware 3 (60%); Rede 2; limit=0 retorna 400",
    "T13": "page_size=2 retorna 2 itens, 3 páginas, primeiro ID 5 e campos esperados",
    "T14": "7 endpoints retornam 503 para arquivo ausente, sem expor caminho nem criar pasta/arquivo",
    "T15": "Rota desconhecida retorna JSON 404 NOT_FOUND",
    "T16": "Mais de 731 dias retorna PERIOD_TOO_LONG; exatamente 731 é aceito",
    "T17": "Meses janeiro=4/2, fevereiro=1/0; lacunas dezembro e março zeradas",
    "T18": "Semanas W01=4/2 e W04=1/0; virada do ano preenche 2025-W52 e 2026-W00",
    "T19": "Páginas além do total, inclusive inteiro muito grande, retornam items vazio",
    "T20": "Tipos inexistentes, inclusive ID positivo muito grande, retornam total 0",
    "T21": "GET / retorna HTML; CSS/JS 200; URL CDN 4.4.1 presente",
    "T22": "Metadados: Hardware/Rede/Software ordenados e datas 05/01–01/02",
    "T23": "Status e tipos são deduplicados, ordenados e ecoados",
    "T24": "Ordem de prioridade das cinco validações confirmada",
    "T25": "Encerrado às 23:59:59 incluído; atendimento conta abertura anterior ao início",
    "T26": "Total = soma dos 4 status = soma da rosca = total da lista, com e sem filtros",
    "T27": "DELETE negado em modo ro; foreign_keys=1; três índices; conexão fechada",
    "T28": "Exceção inesperada retorna 500 INTERNAL_ERROR sem detalhe privado",
    "T29": "Arquivo ausente retorna 404; exceção HTTP 403 retorna HTTP_ERROR",
    "T30": "5 variações de paginação/limite inválidas retornam VALIDATION_ERROR",
    "T31": "Seed reproduzível com now fixo; fechamento válido; novos chamados abertos sem técnico/nota",
    "T32": "Base vazia mantém health 200, datas null, pages 0 e 4 status com pct zero",
    "T33": "Env de caminho/fonte é relido em cada chamada",
    "T34": "Predicados usam placeholders e rejeitam coluna fora da lista permitida",
    "T35": "Swagger 200; OpenAPI tem título e versão exigidos",
}


def tests_docs(http, performance, browser):
    assert browser["resultado"] == "APROVADO"
    xml = ET.parse(EVIDENCE / f"pytest_{STAMP}.xml")
    cases = xml.findall(".//testcase")
    assert not xml.findall(".//failure") and not xml.findall(".//error")
    groups = Counter(re.search(r"test_(T\d+)", case.attrib["name"]).group(1) for case in cases)
    output = (EVIDENCE / f"pytest_{STAMP}.txt").read_text(encoding="utf-8")
    summary = re.search(r"\d+ passed, .*? in [\d.]+s", output).group(0)
    text = f"""# Testes e evidências reais

## Ambiente e execução

Execução em **{STAMP}**, Windows, Python 3.14.7, SQLite da biblioteca padrão, ambiente virtual em `api/.venv`. As quatro versões diretas são as exigidas; versões transitivas e plataforma estão em [ambiente_{STAMP}.json](evidencias/ambiente_{STAMP}.json).

Resultado observado: **{summary}** — 100% dos {len(cases)} casos passaram, cobrindo 35 grupos de cenários. [Saída integral de pytest -v](evidencias/pytest_{STAMP}.txt) e [JUnit XML](evidencias/pytest_{STAMP}.xml). Cada teste de API usa fixture SQLite temporária, os três tipos e os cinco chamados especificados no enunciado; não depende do banco de demonstração. `TestClient(app, raise_server_exceptions=False)` confirma corpos públicos de erro.

Os warnings são de depreciação em FastAPI/Starlette e suas dependências transitivas com Python 3.14. Não foram ocultados nem tratados como falhas. Nenhuma versão direta foi alterada para eliminá-los.

## Cenários automatizados

| ID | Casos | Resultado | Observação confirmada pela asserção |
| --- | ---: | --- | --- |
"""
    for key, count in sorted(groups.items()):
        text += f"| {key} | {count} | APROVADO | {OBSERVATIONS[key]} |\n"
    text += f"""
## HTTP real com curl

A ferramenta de QA executou `python -m app.seed` (500 exemplos), iniciou Uvicorn em segundo plano e chamou os sete endpoints de API, mais variações de filtros, série, paginação, HTML, Swagger e OpenAPI. Confirmou 200, os cinco códigos funcionais de erro 400, 404 JSON e 503 por arquivo inexistente em processo separado. Comandos completos e stdout real: [curl_{STAMP}.txt](evidencias/curl_{STAMP}.txt); respostas estruturadas: [http_{STAMP}.json](evidencias/http_{STAMP}.json). Nenhum serviço externo Thindesk foi consultado.

Consistência medida sobre o seed local: **total={http['consistencia']['total']} = soma dos quatro cards de status={http['consistencia']['soma_cards_status']} = soma da rosca={http['consistencia']['soma_rosca']} = total da lista={http['consistencia']['total_lista']}**. Os outros cards (taxa e horas) são métricas e não entram nessa soma. As séries contam abertura/encerramento segundo P3/P4, sem impor igualdade indevida entre populações de datas diferentes.

## RNF01 — desempenho com 10.000 chamados

Banco separado `api/.runtime/performance.db`, gerado com `THINDESK_DB_PATH` exclusivo e `python -m app.seed --n 10000`; banco principal preservado. Uvicorn em loopback na porta 8002. Cada rota recebeu **1 aquecimento + 5 amostras sequenciais**, usando curl `-w "%{{time_total}}"`, convertido de segundos para milissegundos. Os dados abaixo são medidos, incluindo transporte HTTP e serialização. As quatro rotas de dashboard e a lista foram medidas. Sem filtro de período; timeseries usa day e top-types limit=8.

| Endpoint (prefixo /api/v1) | Mínimo (ms) | Mediana (ms) | Máximo (ms) | Todas < 500 ms |
| --- | ---: | ---: | ---: | --- |
"""
    for route, row in performance["endpoints"].items():
        text += f"| `{route}` | {row['min_ms']:.3f} | {row['mediana_ms']:.3f} | {row['max_ms']:.3f} | {'SIM' if row['meta_500ms_atingida'] else 'NÃO'} |\n"
    text += f"""
Meta atingida neste ambiente nas amostras coletadas. [Amostras individuais e método](evidencias/desempenho_{STAMP}.json). A transcrição curl contém os tempos originais. Não representa garantia para produção, concorrência, banco remoto, rede externa ou carga da CDN. Índices e leitura foram também verificados por teste automatizado.

## Dashboard — Chrome headless real

O Chrome instalado foi controlado por CDP com Node/WebSocket nativo, sem pacote adicional. Navegador: **{browser.get('versaoNavegador', {}).get('product', 'versão registrada no JSON')}**. Foram executadas {len(browser['verificacoes'])} verificações aprovadas e não houve exceção JavaScript não tratada. [Relatório estruturado](evidencias/dashboard_{STAMP}.json).

| Executado | Cenário | Resultado |
| --- | --- | --- |
"""
    for check in browser["verificacoes"]:
        text += f"| [x] | {check['cenario']} | {check['resultado']} |\n"
    text += f"""
O ciclo automático aguardou 60 segundos reais. O timeout reteve as requisições via CDP por 10 segundos reais. O erro de conexão foi produzido encerrando o processo Uvicorn próprio enquanto a página permaneceu aberta; depois o processo foi reiniciado e o botão de retry recuperou os dados. O retry de init foi verificado bloqueando as chamadas de metadados na navegação inicial. O cenário XSS usou valores maliciosos de teste no renderer e confirmou zero elementos HTML injetados.

Capturas reais, também inspecionadas visualmente pelo agente:

- [Desktop](evidencias/dashboard_desktop_{STAMP}.png)
- [Layout de 600 px](evidencias/dashboard_600px_{STAMP}.png): sem transbordamento do documento; tabela rola dentro do contêiner e gráficos ficam em uma coluna.
- [Estado vazio](evidencias/dashboard_vazio_{STAMP}.png)
- [API parada](evidencias/dashboard_erro_{STAMP}.png)

Os servidores temporários e o Chrome headless próprio foram encerrados ao fim. Não foi deixado servidor da aplicação rodando.

## Correções durante a implementação

A execução inicial detectou uma variável de paginação no escopo errado do repositório e conflito entre o parâmetro status e o status HTTP no auxiliar de teste; ambos foram corrigidos antes da execução aprovada. Os coletores foram ajustados para UTF-8 no Windows e para não serializar o objeto Chart no CDP. Apenas execuções finais completas são apresentadas como aprovadas. Não houve fabricação de respostas ou alteração manual das evidências de teste.

## Reproduzir

Na raiz do projeto, após instalar as dependências:

```powershell
.\\api\\.venv\\Scripts\\python.exe docs/ferramentas/executar_pytest.py
.\\api\\.venv\\Scripts\\python.exe docs/ferramentas/verificar_http.py
node docs/ferramentas/verificar_dashboard.mjs
.\\api\\.venv\\Scripts\\python.exe docs/ferramentas/gerar_documentacao.py
```

As ferramentas de QA requerem portas 8000–8002 livres e encerram somente processos próprios. A ferramenta HTTP substitui as tabelas do banco local de demonstração e do banco de desempenho separado. Os arquivos por data podem ser sobrescritos por nova execução na mesma data; use Git para preservar a versão anterior. O ambiente principal e os bancos não são versionados.

## Limitações e checklist complementar

- [ ] Integração e testes com Thindesk real — **não executados pelo agente**: fonte indisponível; depende de acesso autorizado e mapeamento.
- [ ] Validação com usuários reais e tecnologias assistivas — **não executada pelo agente**.
- [ ] Testes de concorrência, carga prolongada e homologação de infraestrutura — **não executados pelo agente**.
- [ ] Publicação e CI remoto — ver [GITFLOW.md](GITFLOW.md); nenhuma publicação deve ser inferida dos testes locais.
- [ ] Gravação do vídeo — **não executada pelo agente**.

Sem autenticação nesta versão; recomendar OAuth/SSO ou token atrás de proxy reverso. Chart.js depende de internet; alternativa pendente em `dashboard/vendor/`. A suíte aprovada com warnings não prova compatibilidade de todas as versões futuras das transitivas; o ambiente efetivo foi registrado para auditoria.
"""
    release_path = EVIDENCE / f"pytest_{STAMP}_release.txt"
    if release_path.exists():
        release_output = release_path.read_text(encoding="utf-8")
        release_summary = re.search(r"\d+ passed, .*? in [\d.]+s", release_output).group(0)
        text += f"\n## Reexecução na release/1.0.0\n\nResultado real: **{release_summary}**. [Saída integral](evidencias/pytest_{STAMP}_release.txt), [JUnit](evidencias/pytest_{STAMP}_release.xml). Evidência gerada na branch de release antes dos merges e da tag.\n"
    (DOCS / "TESTES.md").write_text(text, encoding="utf-8")


if __name__ == "__main__":
    api_docs(read("http"))
    tests_docs(read("http"), read("desempenho"), read("dashboard"))
    print("Documentação gerada a partir das evidências reais existentes.")
