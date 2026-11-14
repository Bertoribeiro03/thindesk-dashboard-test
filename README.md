# Thindesk Dashboard Administrativo

Dashboard interno de leitura com FastAPI, SQLite e HTML/CSS/JavaScript puro. Oferece indicadores, três gráficos, filtros de período/status/tipo e consulta paginada com considerações técnicas. Interface e API compartilham a mesma origem, sem CORS.

**Os dados são EXEMPLOS locais. Não há conexão com o Thindesk real.** A fonte utilizada é `THINDESK_DATA_SOURCE=local_sqlite`, identificada também no dashboard e em `/api/v1/health`. Não há autenticação; mantenha a execução em loopback durante a demonstração.

## Pré-requisitos

- Python 3.10 ou superior, com `sqlite3` e `venv`; Git para reproduzir o fluxo de branches.
- Internet para instalar as quatro dependências diretas e carregar Chart.js 4.4.1 pela CDN. A interface Swagger em `/docs` também usa recursos externos.
- curl para repetir as evidências HTTP. Node 22+ e Chrome/Edge headless são opcionais e usados apenas pela ferramenta de verificação do dashboard, sem pacotes npm.

## Instalação e configuração

No PowerShell, a partir desta pasta:

```powershell
cd api
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
$env:THINDESK_DB_PATH = 'data/thindesk.db'
$env:THINDESK_DATA_SOURCE = 'local_sqlite'
```

Se a política local impedir a ativação, use diretamente `.\.venv\Scripts\python.exe` nos comandos. Não é necessário alterar a política do sistema. As únicas dependências diretas estão em [requirements.txt](api/requirements.txt); o pip instala também as dependências transitivas delas.

Em Bash, a ativação é `source .venv/bin/activate` e a configuração é `export THINDESK_DB_PATH=data/thindesk.db THINDESK_DATA_SOURCE=local_sqlite`.

[.env.example](.env.example) documenta as variáveis; a aplicação **não carrega `.env` automaticamente**, pois não utiliza bibliotecas extras. Exporte as variáveis no terminal ou configure-as no processo/serviço. Caminhos relativos do banco são sempre relativos a `api/`, independentemente do diretório de execução. Não versione segredos.

## Gerar dados e executar

Dentro de `api/`, com o ambiente ativo:

```powershell
python -m app.seed
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

O seed **substitui as tabelas do banco configurado** por 500 chamados de exemplo, distribuídos em 180 dias. Use um arquivo exclusivo de demonstração. `python -m app.seed --n 10000 --days 180` permite outro volume. O gerador usa `random.Random(42)`; fixando o instante `now` na função `seed`, os registros são idênticos entre execuções. Na CLI, as datas se deslocam em relação ao instante de execução.

- Dashboard: <http://localhost:8000>
- Documentação interativa: <http://localhost:8000/docs>
- Saúde: <http://localhost:8000/api/v1/health>
- OpenAPI: <http://localhost:8000/openapi.json>

Pare com `Ctrl+C`. O banco não é criado pela API: um caminho inexistente resulta em 503 até o seed ser executado. A aplicação abre conexões de leitura com `mode=ro`.

## Testes e evidências

```powershell
# Dentro de api/, com o ambiente ativo
python -m pytest -v

# A partir da raiz, usando explicitamente o ambiente local
.\api\.venv\Scripts\python.exe docs/ferramentas/executar_pytest.py
.\api\.venv\Scripts\python.exe docs/ferramentas/verificar_http.py
node docs/ferramentas/verificar_dashboard.mjs
```

A ferramenta HTTP recria o banco de exemplo, cria um banco separado com 10.000 registros em `api/.runtime/`, usa temporariamente as portas 8000–8002 e encerra os processos que iniciou. A ferramenta de navegador também usa a porta 8000, abre somente uma instância headless própria e aguarda um ciclo real de 60 segundos. Execute essas ferramentas sem outro servidor nessas portas. Saídas reais, respostas completas, medições, JUnit e capturas estão em [docs/evidencias/](docs/evidencias/).

## Estrutura

```text
api/app/         rotas, filtros, repositório SQL, conexão, configuração e seed
api/tests/       testes isolados com SQLite temporário
dashboard/       index.html, styles.css, app.js; servido pela API
docs/            análise, contrato da API, testes, GitFlow e apresentação
docs/evidencias/ saídas reais e capturas do navegador
docs/ferramentas/ verificações reproduzíveis sem dependências adicionais
```

- [Análise e arquitetura](docs/ANALISE_PROJETO.md)
- [Contrato e exemplos reais da API](docs/API.md)
- [Testes e desempenho medido](docs/TESTES.md)
- [GitFlow e publicação](docs/GITFLOW.md)
- [Roteiro de apresentação de até 3 minutos](docs/ROTEIRO_APRESENTACAO.md)

## Pendências

Integração com o Thindesk real, autenticação (OAuth/SSO ou token atrás de proxy reverso), distribuição local de Chart.js em `dashboard/vendor/` para ambientes sem internet e gravação do vídeo. O vídeo ainda não foi gravado.
