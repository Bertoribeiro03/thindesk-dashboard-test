# Thindesk Dashboard Administrativo

Aplicação Java 21 com **Spring Boot e Thymeleaf** para acompanhar chamados técnicos, indicadores e considerações de atendimento. O projeto inclui dashboard, gestão de chamados, clientes, horários, catálogos e API JSON, servidos na mesma origem.

**A fonte padrão é SQLite local com dados de EXEMPLO (`local_sqlite`). Não há conexão ativa com o Thindesk de produção ou com o MongoDB mencionado no ZIP.** O logo, a estrutura de navegação e os fluxos de cadastro foram integrados a partir de `thindesk.zip`; veja [origem e correções](docs/MIGRACAO_SPRING.md).

## Pré-requisitos

- JDK 21 e `JAVA_HOME` apontando para o JDK.
- Internet na primeira execução para baixar Maven e dependências. O Maven Wrapper está incluído; não é necessário instalar Maven globalmente.
- Internet para Chart.js 4.4.1 via CDN. CSS, JavaScript de navegação e logo são locais.

## Executar

No PowerShell, a partir da raiz `thindesk-dashboard/`:

```powershell
.\mvnw.cmd spring-boot:run
```

Em Linux/macOS, use `sh mvnw spring-boot:run`. O backend usa, por padrão, `api/data/thindesk.db`. Se já existir, seus chamados são preservados. Se não existir, o schema é criado e o dashboard apresenta o estado vazio.

- Dashboard: <http://localhost:8080>
- Chamados: <http://localhost:8080/chamados>
- Clientes: <http://localhost:8080/clientes>
- Horários: <http://localhost:8080/ajustes-horarios>
- Documentação e consulta interativa: <http://localhost:8080/docs>
- Especificação: <http://localhost:8080/openapi.json>
- Saúde: <http://localhost:8080/api/v1/health>

Para inserir 500 exemplos **somente em uma base vazia**:

```powershell
$env:THINDESK_SEED_EXAMPLE = 'true'
.\mvnw.cmd spring-boot:run
```

O seed Java não apaga nem substitui registros existentes. Pare o servidor com `Ctrl+C`.

## Configuração

[.env.example](.env.example) descreve as variáveis. O arquivo `.env` não é carregado automaticamente: exporte os valores no terminal ou configure o processo/IDE.

| Variável | Padrão | Uso |
| --- | --- | --- |
| `THINDESK_DB_PATH` | `api/data/thindesk.db` | Caminho relativo à raiz de execução, ou absoluto |
| `THINDESK_DATA_SOURCE` | `local_sqlite` | Fonte implementada nesta versão |
| `THINDESK_INITIALIZE` | `true` | Criação idempotente de tabelas/índices e migração de campos ausentes |
| `THINDESK_SEED_EXAMPLE` | `false` | Gera exemplos apenas se não houver chamados |
| `SERVER_ADDRESS` | `127.0.0.1` | Endereço de escuta |
| `SERVER_PORT` | `8080` | Porta HTTP |
| `SERVER_CONTEXT_PATH` | vazio | Prefixo opcional, por exemplo `/central` |
| `THINDESK_SECURITY_ENABLED` | `false` | Habilita autenticação por formulário |
| `THINDESK_USERNAME` / `THINDESK_PASSWORD` | vazios | Obrigatórios quando o login é habilitado |
| `THYMELEAF_CACHE` | `true` | Cache dos templates |

Não versione segredos. A configuração do MongoDB e a senha contidas no ZIP não foram copiadas. Se habilitar login, forneça usuário/senha por ambiente e abra `/login`; a aplicação falha na inicialização se os valores estiverem ausentes. O modo sem login destina-se à demonstração local, com escuta em loopback.

CSRF está habilitado inclusive no modo local. Os formulários Thymeleaf enviam o token automaticamente. Operações JSON POST/PUT/DELETE exigem o token da sessão no header `X-CSRF-TOKEN`; o formulário HTML renderizado fornece o campo `_csrf`. Exclusão e cancelamento não são executados por GET.

## Testes e empacotamento

```powershell
.\mvnw.cmd -B -ntp verify
java -jar target/thindesk-1.1.0-SNAPSHOT.jar
```

Os testes Java usam bancos temporários próprios em `target/test-data/`; não modificam o banco da aplicação. Cobrem os indicadores, séries, filtros, erros, templates, estáticos, formulários, persistência, seed, CSRF e autenticação.

A verificação visual opcional usa Node 22+ e Chrome/Edge já instalado, sem pacotes npm:

```powershell
node docs/ferramentas/verificar_spring.mjs
```

Ela exige `mvnw verify` concluído e porta 8081 livre, inicia um JAR próprio sob `/central` e um banco isolado, testa o navegador e encerra os processos iniciados. Resultados reais da migração: [TESTES_SPRING.md](docs/TESTES_SPRING.md).

## Estrutura

```text
pom.xml, mvnw, mvnw.cmd, .mvn/wrapper/  build Maven
src/main/java/com/pfc/                backend, serviços, rotas MVC e REST
src/main/resources/templates/        layout e páginas Thymeleaf
src/main/resources/static/           CSS, JavaScript e logo
src/main/resources/api/openapi.json  contrato OpenAPI
src/test/java/com/pfc/                testes Java
docs/                                análise, API, migração e evidências
api/, dashboard/                     implementação Python/HTML anterior preservada
```

## Documentação

- [Migração, análise do ZIP e decisões técnicas](docs/MIGRACAO_SPRING.md)
- [API Spring e rotas das páginas](docs/API_SPRING.md)
- [Testes e evidências Spring](docs/TESTES_SPRING.md)
- [Análise funcional e modelagem inicial](docs/ANALISE_PROJETO.md)
- [GitFlow](docs/GITFLOW.md)
- [Roteiro de apresentação](docs/ROTEIRO_APRESENTACAO.md)

## Implementação anterior

O código Python em `api/` e o dashboard original em `dashboard/` foram preservados; as evidências datadas de 13/11/2026 referem-se a essa versão. Não é necessário iniciar FastAPI junto de Spring Boot. Para executar apenas a suíte anterior: `cd api` e `.\.venv\Scripts\python.exe -m pytest -v` (após instalar `api/requirements.txt` em um ambiente virtual).

No backend Python, caminhos relativos do banco continuam relativos a `api/`, ao contrário do backend Java. Para comparar servidores ou executar o seed antigo, use um arquivo separado; o seed Python substitui as tabelas do arquivo selecionado. Os documentos `API.md` e `TESTES.md` preservam o registro da versão anterior.

## Limitações e pendências

Integração MongoDB/Thindesk de produção depende de configuração autorizada e mapeamento dos campos/datas; o ZIP sozinho não comprova conexão real. Chart.js exige internet. O login local não implementa SSO nem múltiplos perfis de usuário. O vídeo de apresentação permanece pendente. As alterações desta migração são locais até publicação explícita no GitHub.
