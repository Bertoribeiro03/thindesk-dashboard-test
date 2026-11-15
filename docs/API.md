# API — versão 1.0.0

> Registro da implementação anterior em FastAPI. A aplicação principal agora usa Spring Boot na porta 8080; consulte [API_SPRING.md](API_SPRING.md) para a versão atual. Exemplos e evidências abaixo preservam a execução original de 13/11/2026.

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

As respostas abaixo foram extraídas de execução HTTP real neste ambiente em 2026-11-13, após seed de 500 chamados. Não são mocks. A fonte é local. Comandos e saídas integrais, incluindo `-w %{time_total}`, estão em [curl_2026-11-13.txt](evidencias/curl_2026-11-13.txt); a extração estruturada está em [http_2026-11-13.json](evidencias/http_2026-11-13.json). No Windows use `curl.exe`; em Bash use `curl`.

### Saúde — HTTP 200

```powershell
curl.exe "http://127.0.0.1:8000/api/v1/health"
```

```json
{
  "status": "ok",
  "database": "ok",
  "data_source": "local_sqlite"
}
```

### Metadados — HTTP 200

```powershell
curl.exe "http://127.0.0.1:8000/api/v1/meta/filters"
```

```json
{
  "data": {
    "statuses": [
      {
        "value": "aberto",
        "label": "Aberto"
      },
      {
        "value": "em_andamento",
        "label": "Em andamento"
      },
      {
        "value": "concluido",
        "label": "Concluído"
      },
      {
        "value": "cancelado",
        "label": "Cancelado"
      }
    ],
    "types": [
      {
        "id": 4,
        "name": "Acesso/Senha"
      },
      {
        "id": 6,
        "name": "E-mail"
      },
      {
        "id": 8,
        "name": "ERP"
      },
      {
        "id": 1,
        "name": "Hardware"
      },
      {
        "id": 5,
        "name": "Impressora"
      },
      {
        "id": 3,
        "name": "Rede"
      },
      {
        "id": 2,
        "name": "Software"
      },
      {
        "id": 7,
        "name": "Telefonia"
      }
    ],
    "date_range": {
      "min": "2026-05-18",
      "max": "2026-11-13"
    }
  }
}
```

### Indicadores — HTTP 200

```powershell
curl.exe "http://127.0.0.1:8000/api/v1/dashboard/summary"
```

```json
{
  "filters": {
    "start": null,
    "end": null,
    "status": [],
    "type_id": []
  },
  "data": {
    "total": 500,
    "abertos": 58,
    "em_andamento": 59,
    "concluidos": 352,
    "cancelados": 31,
    "tempo_medio_resolucao_h": 59.5,
    "backlog": 117,
    "taxa_resolucao_pct": 70.4
  }
}
```

### Série mensal — HTTP 200

```powershell
curl.exe "http://127.0.0.1:8000/api/v1/dashboard/timeseries?granularity=month"
```

```json
{
  "filters": {
    "start": null,
    "end": null,
    "status": [],
    "type_id": []
  },
  "granularity": "month",
  "data": [
    {
      "period": "2026-05",
      "abertos": 39,
      "atendidos": 24
    },
    {
      "period": "2026-06",
      "abertos": 80,
      "atendidos": 65
    },
    {
      "period": "2026-07",
      "abertos": 81,
      "atendidos": 49
    },
    {
      "period": "2026-08",
      "abertos": 92,
      "atendidos": 63
    },
    {
      "period": "2026-09",
      "abertos": 86,
      "atendidos": 54
    },
    {
      "period": "2026-10",
      "abertos": 89,
      "atendidos": 68
    },
    {
      "period": "2026-11",
      "abertos": 33,
      "atendidos": 29
    }
  ]
}
```

### Distribuição — HTTP 200

```powershell
curl.exe "http://127.0.0.1:8000/api/v1/dashboard/status-distribution"
```

```json
{
  "filters": {
    "start": null,
    "end": null,
    "status": [],
    "type_id": []
  },
  "data": [
    {
      "status": "aberto",
      "label": "Aberto",
      "count": 58,
      "pct": 11.6
    },
    {
      "status": "em_andamento",
      "label": "Em andamento",
      "count": 59,
      "pct": 11.8
    },
    {
      "status": "concluido",
      "label": "Concluído",
      "count": 352,
      "pct": 70.4
    },
    {
      "status": "cancelado",
      "label": "Cancelado",
      "count": 31,
      "pct": 6.2
    }
  ]
}
```

### Ranking — HTTP 200

```powershell
curl.exe "http://127.0.0.1:8000/api/v1/dashboard/top-types?limit=8"
```

```json
{
  "filters": {
    "start": null,
    "end": null,
    "status": [],
    "type_id": []
  },
  "data": [
    {
      "rank": 1,
      "type_id": 4,
      "type": "Acesso/Senha",
      "count": 104,
      "pct": 20.8
    },
    {
      "rank": 2,
      "type_id": 2,
      "type": "Software",
      "count": 104,
      "pct": 20.8
    },
    {
      "rank": 3,
      "type_id": 1,
      "type": "Hardware",
      "count": 101,
      "pct": 20.2
    },
    {
      "rank": 4,
      "type_id": 3,
      "type": "Rede",
      "count": 67,
      "pct": 13.4
    },
    {
      "rank": 5,
      "type_id": 6,
      "type": "E-mail",
      "count": 49,
      "pct": 9.8
    },
    {
      "rank": 6,
      "type_id": 5,
      "type": "Impressora",
      "count": 43,
      "pct": 8.6
    },
    {
      "rank": 7,
      "type_id": 8,
      "type": "ERP",
      "count": 17,
      "pct": 3.4
    },
    {
      "rank": 8,
      "type_id": 7,
      "type": "Telefonia",
      "count": 15,
      "pct": 3.0
    }
  ]
}
```

### Lista paginada — HTTP 200

```powershell
curl.exe "http://127.0.0.1:8000/api/v1/tickets?page_size=2"
```

```json
{
  "filters": {
    "start": null,
    "end": null,
    "status": [],
    "type_id": []
  },
  "items": [
    {
      "id": 412,
      "title": "Chamado 412 - Acesso/Senha",
      "type": "Acesso/Senha",
      "status": "concluido",
      "priority": "critica",
      "technician": "Bruno Lima",
      "technical_notes": "Cabo de rede reparado.",
      "opened_at": "2026-11-13 15:09:45",
      "closed_at": "2026-11-13 16:13:08"
    },
    {
      "id": 226,
      "title": "Chamado 226 - Software",
      "type": "Software",
      "status": "concluido",
      "priority": "baixa",
      "technician": "Bruno Lima",
      "technical_notes": "Atualização aplicada.",
      "opened_at": "2026-11-13 13:05:51",
      "closed_at": "2026-11-13 17:10:07"
    }
  ],
  "page": 1,
  "page_size": 2,
  "total": 500,
  "pages": 250
}
```

### Filtro sem resultado — HTTP 200

```powershell
curl.exe "http://127.0.0.1:8000/api/v1/dashboard/summary?start=2020-01-01&end=2020-01-31"
```

```json
{
  "filters": {
    "start": "2020-01-01",
    "end": "2020-01-31",
    "status": [],
    "type_id": []
  },
  "data": {
    "total": 0,
    "abertos": 0,
    "em_andamento": 0,
    "concluidos": 0,
    "cancelados": 0,
    "tempo_medio_resolucao_h": null,
    "backlog": 0,
    "taxa_resolucao_pct": 0.0
  }
}
```

### Erro de status — HTTP 400

```powershell
curl.exe "http://127.0.0.1:8000/api/v1/dashboard/summary?status=xpto"
```

```json
{
  "error": {
    "code": "INVALID_STATUS",
    "message": "Status inválido.",
    "details": {
      "invalid": [
        "xpto"
      ],
      "allowed": [
        "aberto",
        "em_andamento",
        "concluido",
        "cancelado"
      ]
    }
  }
}
```

### Data malformada — HTTP 400

```powershell
curl.exe "http://127.0.0.1:8000/api/v1/dashboard/summary?start=2026-13-40"
```

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Parâmetros inválidos.",
    "details": [
      {
        "param": "start",
        "msg": "Use uma data válida no formato AAAA-MM-DD."
      }
    ]
  }
}
```

### Rota inexistente — HTTP 404

```powershell
curl.exe "http://127.0.0.1:8000/api/v1/nao-existe"
```

```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "Recurso não encontrado.",
    "details": null
  }
}
```

### Banco indisponível — processo separado em 8001 — HTTP 503

```powershell
curl.exe "http://127.0.0.1:8001/api/v1/health"
```

```json
{
  "error": {
    "code": "DATABASE_UNAVAILABLE",
    "message": "Banco de dados indisponível.",
    "details": null
  }
}
```

## Códigos HTTP e envelope de erro

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
