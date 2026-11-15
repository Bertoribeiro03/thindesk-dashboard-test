# GitFlow e publicação

## Estratégia

- `main`: versões estáveis; release marcada com tag anotada.
- `develop`: integração das funcionalidades.
- `feature/*`: trabalhos delimitados a partir de develop, integrados com `--no-ff`.
- `release/*`: estabilização a partir de develop; repetir testes, commitar a evidência e integrar em main e develop.
- `hotfix/*`: correção urgente a partir de main, com teste e merge em main e develop; não foi necessário criar hotfix nesta entrega.

## Fluxo local

O repositório é a pasta `thindesk-dashboard/`. As branches de trabalho usadas nesta entrega são `feature/api-dashboard`, `feature/dashboard-ui`, `feature/docs-testes` e `release/1.0.0`; as branches são mantidas para inspeção. Comandos usados (mensagens explícitas nos merges evitam abrir editor):

```powershell
git init
git checkout -b main
git add README.md .gitignore .env.example
git commit -m "chore: estrutura inicial"
git checkout -b develop

git checkout -b feature/api-dashboard develop
git add api/
git commit -m "feat(api): endpoints do dashboard, filtros e validações"
git checkout develop
git merge --no-ff feature/api-dashboard -m "merge: API em develop"

git checkout -b feature/dashboard-ui develop
git add dashboard/
git commit -m "feat(ui): dashboard integrado à API"
git checkout develop
git merge --no-ff feature/dashboard-ui -m "merge: dashboard em develop"

git checkout -b feature/docs-testes develop
git add docs/
git commit -m "docs: análise, API, testes e evidências"
git checkout develop
git merge --no-ff feature/docs-testes -m "merge: documentação e testes em develop"

git checkout -b release/1.0.0 develop
.\api\.venv\Scripts\python.exe docs/ferramentas/executar_pytest.py --release
.\api\.venv\Scripts\python.exe docs/ferramentas/gerar_documentacao.py
git add docs/
git commit -m "test: validação e evidências da release 1.0.0"
git checkout main
git merge --no-ff release/1.0.0 -m "release: versão 1.0.0 em main"
git tag -a v1.0.0 -m "v1.0.0"
git checkout develop
git merge --no-ff release/1.0.0 -m "merge: release 1.0.0 em develop"
```

Verificação: `git log --graph --oneline --all --decorate`, `git branch -a`, `git tag -n`, `git status --short`, `git remote -v`. O Git é local; um histórico local não equivale a publicação no GitHub. `.gitignore` exclui `.venv/`, `__pycache__/`, `*.db`, `.env`, `.pytest_cache/` e runtime. Não há segredos no projeto.

Registro da estabilização em 13/11/2026: as três features foram commitadas e integradas em develop com merges `--no-ff`; `release/1.0.0` foi criada e executou novamente **58 testes aprovados em 3,42 s**, com 187 avisos de depreciação e nenhuma falha. A [saída real da release](evidencias/pytest_2026-11-13_release.txt) e seu JUnit foram incluídos no commit de estabilização. A codificação dos acentos das mensagens dos commits locais próprios foi corrigida antes da tag, preservando arquivos, autores, datas e topologia. Nenhum histórico remoto foi alterado. O fechamento usa os merges e a tag anotada `v1.0.0` acima; os refs locais permitem conferir o resultado diretamente.

## Publicação no GitHub

O remoto atual é `https://github.com/Bertoribeiro03/thindesk-dashboard-test.git`. A consulta `git ls-remote --heads origin` em 14/11/2026 confirmou `main` no commit `b9122e8` e `develop` no commit `1b69183`, referentes à versão anterior. A migração Spring Boot/Thymeleaf está na branch local `feature/spring-thymeleaf`; as alterações desta migração não foram publicadas. O GitHub CLI não está disponível no PATH; o acesso Git ao remoto foi verificado sem alterar seus refs.

Para publicar futuramente a migração, revise as diferenças, registre os arquivos e envie a branch ao remoto existente:

```powershell
git status
git diff
git add pom.xml mvnw mvnw.cmd .mvn/ src/ README.md .env.example .gitignore docs/
git commit -m "feat: integração Spring Boot e Thymeleaf"
git push -u origin feature/spring-thymeleaf
```

Depois da revisão da branch, integre em develop, estabilize uma nova release com `mvnw verify` e faça os merges em main e develop. Preserve a tag histórica `v1.0.0`; uma nova entrega deve receber uma nova tag. A branch histórica `feature/api-dashboard` e as tags ainda podem ser publicadas com `git push origin feature/api-dashboard --tags`. Não recrie o repositório existente nem grave tokens/URLs autenticadas nas evidências. A publicação dos comandos acima permanece pendente.

## Hotfix futuro

Exemplo para uma correção efetivamente necessária, não executado nesta entrega:

```powershell
git checkout -b hotfix/1.0.1 main
# Corrigir, executar os testes e registrar as evidências.
git add <arquivos-da-correcao>
git commit -m "fix: descrição objetiva da correção"
git checkout main
git merge --no-ff hotfix/1.0.1 -m "hotfix: versão 1.0.1 em main"
git tag -a v1.0.1 -m "v1.0.1"
git checkout develop
git merge --no-ff hotfix/1.0.1 -m "merge: hotfix 1.0.1 em develop"
```

Nenhuma branch existente foi apagada e não foram feitos reset, force-push ou outras operações destrutivas.
