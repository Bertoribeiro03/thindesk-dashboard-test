# GitFlow e publicação

## Estratégia

- `main`: versões estáveis; release marcada com tag anotada.
- `develop`: integração das funcionalidades.
- `feature/*`: trabalhos delimitados a partir de develop, integrados com `--no-ff`.
- `release/*`: estabilização a partir de develop; repetir testes, commitar a evidência e integrar em main e develop.
- `hotfix/*`: correção urgente a partir de main, com teste e merge em main e develop; não foi necessário criar hotfix nesta entrega.

## Fluxo local

O repositório é a pasta `thindesk-dashboard/`. As branches de trabalho previstas nesta entrega são `feature/api-dashboard`, `feature/dashboard-ui`, `feature/docs-testes` e `release/1.0.0`; as branches são mantidas para inspeção. O registro do resultado local é acrescentado na estabilização da release. Comandos usados (mensagens explícitas nos merges evitam abrir editor):

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

## Publicação no GitHub

O `gh` não está disponível no PATH deste ambiente e o projeto começou sem repositório/remoto. Não há autenticação do CLI verificada. A publicação deve ocorrer somente quando `gh auth status` estiver OK ou houver remoto autorizado configurado; **não foi publicada nenhuma cópia no GitHub pelo agente**.

Para publicar futuramente, com GitHub CLI instalado e autenticado, execute na raiz do repositório:

```powershell
gh auth status
# O fluxo termina em develop; selecione main antes de criar o remoto.
git checkout main
gh repo create thindesk-dashboard --private --source=. --push
git push -u origin main develop --tags
git checkout develop
```

Se já houver remoto configurado, confira o destino com `git remote -v` e a autorização de acesso, não crie outro repositório; use `git push -u origin main develop --tags`. O comando `gh repo create` só faz sentido quando não existe o remoto de destino. Nenhum token ou URL autenticada deve ser gravado em arquivos de evidência.

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
