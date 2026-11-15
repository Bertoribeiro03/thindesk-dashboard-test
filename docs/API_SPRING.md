# API e páginas — Spring Boot

Base padrão: `http://localhost:8080`. Prefixo de consulta do dashboard: `/api/v1`. Se `SERVER_CONTEXT_PATH=/central`, adicione `/central` antes de todas as rotas. Templates usam `@{...}` e o JavaScript lê a base renderizada no body; não dependem de uma implantação na raiz.

## Contrato do dashboard

O contrato de leitura foi preservado em relação à API anterior: `/health`, `/meta/filters`, `/dashboard/summary`, `/dashboard/timeseries`, `/dashboard/status-distribution`, `/dashboard/top-types` e `/tickets`, todos GET e prefixados por `/api/v1`.

Parâmetros comuns: `start`, `end` (AAAA-MM-DD), `status` (CSV) e `type_id` (CSV de inteiros positivos). Datas finais são inclusivas; intervalo máximo de 731 dias; duplicatas removidas/ordenadas. Tipo inexistente retorna resultado vazio. A ordem de validação é datas, período invertido, período longo, status e tipo.

`granularity=day|week|month`, padrão day; `limit=1..50`, padrão 5; `page>=1`, padrão 1; `page_size=1..100`, padrão 10. O ranking usa volume de todos os status filtrados e o período considera abertura; `status=concluido` restringe a concluídos. A série de atendidos usa encerramento e status concluído.

Respostas e erros estão descritos com esquemas em `/openapi.json`. `/docs` é uma página Thymeleaf com referência e explorador de consultas GET. O contrato anuncia erros de validação 400; não anuncia o antigo 422.

```powershell
curl.exe "http://localhost:8080/api/v1/health"
curl.exe "http://localhost:8080/api/v1/dashboard/summary?status=aberto,em_andamento"
curl.exe "http://localhost:8080/api/v1/dashboard/timeseries?granularity=week"
curl.exe "http://localhost:8080/api/v1/dashboard/top-types?status=concluido&limit=8"
curl.exe "http://localhost:8080/api/v1/tickets?page=1&page_size=10"
curl.exe "http://localhost:8080/api/v1/dashboard/summary?status=xpto"
```

Esses comandos são exemplos de uso; resultados reais da versão Spring estão nas evidências indicadas em [TESTES_SPRING.md](TESTES_SPRING.md). As transcrições de 13/11/2026 em `curl_2026-11-13.txt` pertencem à execução anterior em FastAPI.

## Gestão JSON

| Métodos | Caminho | Campos |
| --- | --- | --- |
| GET, POST | `/api/chamados` | descricao, status, tipo, tecnico, usuario, prioridade, consideracoesTecnicas |
| GET, PUT | `/api/chamados/{id}` | Mesmo contrato; abertura/encerramento retornados na leitura |
| GET, POST | `/api/clientes` | nome, telefone, setor |
| PUT, DELETE | `/api/clientes/{id}` | Mesmo contrato de cliente |
| GET, POST | `/api/ajustes-horarios` | setor, diaSemana, horarioInicio, horarioFim |
| PUT, DELETE | `/api/ajustes-horarios/{id}` | Mesmo contrato de horário |

Criação retorna 201, atualização 200 e exclusão 204. IDs de gestão são strings; a listagem de compatibilidade `/api/v1/tickets` mantém IDs numéricos. Campos de abertura/encerramento são controlados pelo serviço: alteração preserva abertura; concluir/cancelar registra encerramento; reabrir limpa encerramento. Status do ZIP em maiúsculas e rótulos acentuados são normalizados para os quatro valores canônicos. Tipo deve estar cadastrado. Horários são HH:mm, com início anterior ao fim e dia da semana válido.

Operações de escrita exigem CSRF. Obtenha uma página HTML e seus cookies de sessão; envie o valor do input `_csrf` no header `X-CSRF-TOKEN`, usando a mesma sessão. Não envie tokens em query strings. Quando o login está ativo, autentique a sessão antes. O OpenAPI descreve esse header nas operações de alteração.

Exemplo de corpo JSON para criação de chamado:

```json
{"descricao":"Falha de rede","status":"aberto","tipo":"Rede","prioridade":"alta","tecnico":"Ana Souza","usuario":"Solicitante","consideracoesTecnicas":"Verificar cabeamento."}
```

## Rotas MVC

| GET | POST correspondente | Resultado |
| --- | --- | --- |
| `/`, `/dashboard` | — | Dashboard Thymeleaf, alimentado pela API |
| `/chamados` | — | Lista paginada, 20 itens por página |
| `/chamados/novo` | `/chamados/novo` | Criação de chamado |
| `/chamados/atualizar/{id}` | `/chamados/atualizar/{id}` | Edição de chamado |
| — | `/chamados/cancelar/{id}` | Cancelamento |
| `/clientes`, `/clientes/novo`, `/clientes/editar/{id}` | `/clientes/novo`, `/clientes/editar/{id}`, `/clientes/deletar/{id}` | Consulta e gestão de clientes |
| `/ajustes-horarios`, `/ajustes-horarios/editar/{id}` | `/ajustes-horarios/salvar`, `/ajustes-horarios/deletar/{id}` | Horários por setor |
| `/tecnicos`, `/tipo-chamado` | — | Catálogos de consulta |
| `/recursos` | — | Redireciona à documentação |
| `/login` | `/login`, `/logout` | Login/logout quando habilitados |
| `/docs`, `/openapi.json` | — | Referência interativa e especificação |

Formulários têm CSRF automático, Bean Validation e mensagens de erro; gravações válidas redirecionam para a listagem, evitando reenvio no refresh. Exclusões por GET não são implementadas.

## Erros

Todos os erros JSON seguem `{"error":{"code":"...","message":"...","details":null}}`. Validação 400 usa lista de `{param,msg}`; INVALID_STATUS informa invalid/allowed. Códigos: VALIDATION_ERROR, INVALID_PERIOD, PERIOD_TOO_LONG, INVALID_STATUS, INVALID_TYPE_ID (400); HTTP_ERROR (401/403/405), NOT_FOUND (404), DATABASE_UNAVAILABLE (503), INTERNAL_ERROR (500). Detalhes internos ficam somente nos logs.

Para verificar 503 sem criar o arquivo inexistente, inicie um processo separado com `THINDESK_INITIALIZE=false` e `THINDESK_DB_PATH` apontando para arquivo ausente. Não remova nem renomeie o banco principal. Páginas HTML usam template de erro local com status HTTP correspondente.

## Respostas reais — 2026-11-14

Extraídas das chamadas curl ao JAR final com banco de exemplo separado. Não representam dados do Thindesk de produção.

### /api/v1/health — HTTP 200

```json
{
  "status": "ok",
  "database": "ok",
  "data_source": "local_sqlite"
}
```

### /api/v1/dashboard/summary — HTTP 200

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

### /api/v1/dashboard/summary?status=xpto — HTTP 400

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

### 503 banco ausente — HTTP 503

```json
{
  "error": {
    "code": "DATABASE_UNAVAILABLE",
    "message": "Banco de dados indisponível.",
    "details": null
  }
}
```

