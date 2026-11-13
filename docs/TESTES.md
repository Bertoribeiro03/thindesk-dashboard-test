# Testes e evidências reais

## Ambiente e execução

Execução em **2026-11-13**, Windows, Python 3.14.7, SQLite da biblioteca padrão, ambiente virtual em `api/.venv`. As quatro versões diretas são as exigidas; versões transitivas e plataforma estão em [ambiente_2026-11-13.json](evidencias/ambiente_2026-11-13.json).

Resultado observado: **58 passed, 187 warnings in 3.47s** — 100% dos 58 casos passaram, cobrindo 35 grupos de cenários. [Saída integral de pytest -v](evidencias/pytest_2026-11-13.txt) e [JUnit XML](evidencias/pytest_2026-11-13.xml). Cada teste de API usa fixture SQLite temporária, os três tipos e os cinco chamados especificados no enunciado; não depende do banco de demonstração. `TestClient(app, raise_server_exceptions=False)` confirma corpos públicos de erro.

Os warnings são de depreciação em FastAPI/Starlette e suas dependências transitivas com Python 3.14. Não foram ocultados nem tratados como falhas. Nenhuma versão direta foi alterada para eliminá-los.

## Cenários automatizados

| ID | Casos | Resultado | Observação confirmada pela asserção |
| --- | ---: | --- | --- |
| T01 | 1 | APROVADO | Health 200; database=ok e fonte local_sqlite |
| T02 | 1 | APROVADO | Total 5; status 1/1/2/1; backlog 2; taxa 40,0%; média 14,0 h |
| T03 | 1 | APROVADO | CSV aberto,em_andamento retorna total 2 |
| T04 | 1 | APROVADO | Tipo 1 em janeiro retorna total 2 |
| T05 | 1 | APROVADO | Janeiro/2020 retorna total 0, taxa 0,0 e média null |
| T06 | 6 | APROVADO | 6 entradas de data inválidas retornam 400 VALIDATION_ERROR |
| T07 | 1 | APROVADO | Período invertido retorna 400 INVALID_PERIOD |
| T08 | 1 | APROVADO | Status xpto retorna invalid=[xpto] e allowed com os 4 status |
| T09 | 7 | APROVADO | 7 variações inválidas, incluindo abc/0/-1 e tentativa de SQL, retornam INVALID_TYPE_ID |
| T10 | 1 | APROVADO | Dias 05/01:1/1; 06/01:2/0; 07/01:0/1; year retorna 400 |
| T11 | 1 | APROVADO | Counts somam 5; pct somam 100; ordem fixa dos status |
| T12 | 1 | APROVADO | Hardware 3 (60%); Rede 2; limit=0 retorna 400 |
| T13 | 1 | APROVADO | page_size=2 retorna 2 itens, 3 páginas, primeiro ID 5 e campos esperados |
| T14 | 7 | APROVADO | 7 endpoints retornam 503 para arquivo ausente, sem expor caminho nem criar pasta/arquivo |
| T15 | 1 | APROVADO | Rota desconhecida retorna JSON 404 NOT_FOUND |
| T16 | 1 | APROVADO | Mais de 731 dias retorna PERIOD_TOO_LONG; exatamente 731 é aceito |
| T17 | 1 | APROVADO | Meses janeiro=4/2, fevereiro=1/0; lacunas dezembro e março zeradas |
| T18 | 1 | APROVADO | Semanas W01=4/2 e W04=1/0; virada do ano preenche 2025-W52 e 2026-W00 |
| T19 | 2 | APROVADO | Páginas além do total, inclusive inteiro muito grande, retornam items vazio |
| T20 | 2 | APROVADO | Tipos inexistentes, inclusive ID positivo muito grande, retornam total 0 |
| T21 | 1 | APROVADO | GET / retorna HTML; CSS/JS 200; URL CDN 4.4.1 presente |
| T22 | 1 | APROVADO | Metadados: Hardware/Rede/Software ordenados e datas 05/01–01/02 |
| T23 | 1 | APROVADO | Status e tipos são deduplicados, ordenados e ecoados |
| T24 | 1 | APROVADO | Ordem de prioridade das cinco validações confirmada |
| T25 | 1 | APROVADO | Encerrado às 23:59:59 incluído; atendimento conta abertura anterior ao início |
| T26 | 1 | APROVADO | Total = soma dos 4 status = soma da rosca = total da lista, com e sem filtros |
| T27 | 1 | APROVADO | DELETE negado em modo ro; foreign_keys=1; três índices; conexão fechada |
| T28 | 1 | APROVADO | Exceção inesperada retorna 500 INTERNAL_ERROR sem detalhe privado |
| T29 | 1 | APROVADO | Arquivo ausente retorna 404; exceção HTTP 403 retorna HTTP_ERROR |
| T30 | 5 | APROVADO | 5 variações de paginação/limite inválidas retornam VALIDATION_ERROR |
| T31 | 1 | APROVADO | Seed reproduzível com now fixo; fechamento válido; novos chamados abertos sem técnico/nota |
| T32 | 1 | APROVADO | Base vazia mantém health 200, datas null, pages 0 e 4 status com pct zero |
| T33 | 1 | APROVADO | Env de caminho/fonte é relido em cada chamada |
| T34 | 1 | APROVADO | Predicados usam placeholders e rejeitam coluna fora da lista permitida |
| T35 | 1 | APROVADO | Swagger 200; OpenAPI tem título e versão exigidos |

## HTTP real com curl

A ferramenta de QA executou `python -m app.seed` (500 exemplos), iniciou Uvicorn em segundo plano e chamou os sete endpoints de API, mais variações de filtros, série, paginação, HTML, Swagger e OpenAPI. Confirmou 200, os cinco códigos funcionais de erro 400, 404 JSON e 503 por arquivo inexistente em processo separado. Comandos completos e stdout real: [curl_2026-11-13.txt](evidencias/curl_2026-11-13.txt); respostas estruturadas: [http_2026-11-13.json](evidencias/http_2026-11-13.json). Nenhum serviço externo Thindesk foi consultado.

Consistência medida sobre o seed local: **total=500 = soma dos quatro cards de status=500 = soma da rosca=500 = total da lista=500**. Os outros cards (taxa e horas) são métricas e não entram nessa soma. As séries contam abertura/encerramento segundo P3/P4, sem impor igualdade indevida entre populações de datas diferentes.

## RNF01 — desempenho com 10.000 chamados

Banco separado `api/.runtime/performance.db`, gerado com `THINDESK_DB_PATH` exclusivo e `python -m app.seed --n 10000`; banco principal preservado. Uvicorn em loopback na porta 8002. Cada rota recebeu **1 aquecimento + 5 amostras sequenciais**, usando curl `-w "%{time_total}"`, convertido de segundos para milissegundos. Os dados abaixo são medidos, incluindo transporte HTTP e serialização. As quatro rotas de dashboard e a lista foram medidas. Sem filtro de período; timeseries usa day e top-types limit=8.

| Endpoint (prefixo /api/v1) | Mínimo (ms) | Mediana (ms) | Máximo (ms) | Todas < 500 ms |
| --- | ---: | ---: | ---: | --- |
| `/dashboard/summary` | 8.649 | 10.021 | 10.453 | SIM |
| `/dashboard/timeseries` | 25.686 | 27.015 | 27.909 | SIM |
| `/dashboard/status-distribution` | 3.930 | 4.494 | 5.933 | SIM |
| `/dashboard/top-types?limit=8` | 10.564 | 11.026 | 11.047 | SIM |
| `/tickets?page_size=100` | 6.574 | 6.730 | 7.816 | SIM |

Meta atingida neste ambiente nas amostras coletadas. [Amostras individuais e método](evidencias/desempenho_2026-11-13.json). A transcrição curl contém os tempos originais. Não representa garantia para produção, concorrência, banco remoto, rede externa ou carga da CDN. Índices e leitura foram também verificados por teste automatizado.

## Dashboard — Chrome headless real

O Chrome instalado foi controlado por CDP com Node/WebSocket nativo, sem pacote adicional. Navegador: **Chrome/157.0.8103.51**. Foram executadas 13 verificações aprovadas e não houve exceção JavaScript não tratada. [Relatório estruturado](evidencias/dashboard_2026-11-13.json).

| Executado | Cenário | Resultado |
| --- | --- | --- |
| [x] | Carga inicial e Chart.js real via CDN | APROVADO |
| [x] | Paginação | APROVADO |
| [x] | Filtro Rede com agrupamento semanal e retorno à página 1 | APROVADO |
| [x] | Período invertido bloqueado sem requisição HTTP | APROVADO |
| [x] | Estado vazio e valor nulo | APROVADO |
| [x] | Layout responsivo em 600 px | APROVADO |
| [x] | Escape de conteúdo da API contra XSS | APROVADO |
| [x] | Tratamento de Chart.js indisponível | APROVADO |
| [x] | Atualização automática real após 60 s | APROVADO |
| [x] | Timeout real de 10 segundos | APROVADO |
| [x] | Erro com API realmente parada | APROVADO |
| [x] | Recuperação pelo botão Tentar novamente | APROVADO |
| [x] | Tentar novamente refaz init quando faltam metadados | APROVADO |

O ciclo automático aguardou 60 segundos reais. O timeout reteve as requisições via CDP por 10 segundos reais. O erro de conexão foi produzido encerrando o processo Uvicorn próprio enquanto a página permaneceu aberta; depois o processo foi reiniciado e o botão de retry recuperou os dados. O retry de init foi verificado bloqueando as chamadas de metadados na navegação inicial. O cenário XSS usou valores maliciosos de teste no renderer e confirmou zero elementos HTML injetados.

Capturas reais, também inspecionadas visualmente pelo agente:

- [Desktop](evidencias/dashboard_desktop_2026-11-13.png)
- [Layout de 600 px](evidencias/dashboard_600px_2026-11-13.png): sem transbordamento do documento; tabela rola dentro do contêiner e gráficos ficam em uma coluna.
- [Estado vazio](evidencias/dashboard_vazio_2026-11-13.png)
- [API parada](evidencias/dashboard_erro_2026-11-13.png)

Os servidores temporários e o Chrome headless próprio foram encerrados ao fim. Não foi deixado servidor da aplicação rodando.

## Correções durante a implementação

A execução inicial detectou uma variável de paginação no escopo errado do repositório e conflito entre o parâmetro status e o status HTTP no auxiliar de teste; ambos foram corrigidos antes da execução aprovada. Os coletores foram ajustados para UTF-8 no Windows e para não serializar o objeto Chart no CDP. Apenas execuções finais completas são apresentadas como aprovadas. Não houve fabricação de respostas ou alteração manual das evidências de teste.

## Reproduzir

Na raiz do projeto, após instalar as dependências:

```powershell
.\api\.venv\Scripts\python.exe docs/ferramentas/executar_pytest.py
.\api\.venv\Scripts\python.exe docs/ferramentas/verificar_http.py
node docs/ferramentas/verificar_dashboard.mjs
.\api\.venv\Scripts\python.exe docs/ferramentas/gerar_documentacao.py
```

As ferramentas de QA requerem portas 8000–8002 livres e encerram somente processos próprios. A ferramenta HTTP substitui as tabelas do banco local de demonstração e do banco de desempenho separado. Os arquivos por data podem ser sobrescritos por nova execução na mesma data; use Git para preservar a versão anterior. O ambiente principal e os bancos não são versionados.

## Limitações e checklist complementar

- [ ] Integração e testes com Thindesk real — **não executados pelo agente**: fonte indisponível; depende de acesso autorizado e mapeamento.
- [ ] Validação com usuários reais e tecnologias assistivas — **não executada pelo agente**.
- [ ] Testes de concorrência, carga prolongada e homologação de infraestrutura — **não executados pelo agente**.
- [ ] Publicação e CI remoto — ver [GITFLOW.md](GITFLOW.md); nenhuma publicação deve ser inferida dos testes locais.
- [ ] Gravação do vídeo — **não executada pelo agente**.

Sem autenticação nesta versão; recomendar OAuth/SSO ou token atrás de proxy reverso. Chart.js depende de internet; alternativa pendente em `dashboard/vendor/`. A suíte aprovada com warnings não prova compatibilidade de todas as versões futuras das transitivas; o ambiente efetivo foi registrado para auditoria.

## Reexecução na release/1.0.0

Resultado real: **58 passed, 187 warnings in 3.42s**. [Saída integral](evidencias/pytest_2026-11-13_release.txt), [JUnit](evidencias/pytest_2026-11-13_release.xml). Evidência gerada na branch de release antes dos merges e da tag.
