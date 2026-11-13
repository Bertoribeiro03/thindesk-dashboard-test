# Roteiro de apresentação — até 3 minutos

**O vídeo não foi gravado pelo agente.** Este é o roteiro para gravação pelo responsável. As evidências existentes são arquivos de teste, respostas HTTP e capturas headless reais. Prepare a API com o seed de 500 registros, deixe o terminal do Uvicorn acessível e abra o dashboard e `/docs` antes de começar.

| Tempo | Tela / ação | Fala sugerida |
| --- | --- | --- |
| 0:00–0:20 | Dashboard e diagrama de `ANALISE_PROJETO.md`; apontar badge | “Este dashboard administrativo usa FastAPI e SQLite local. O Thindesk real não está disponível; todos os registros são exemplos, identificados nesta tela. O navegador consulta a API, que agrega os dados no banco em modo somente leitura.” |
| 0:20–0:50 | Sete cards e código de `repository.summary` | “As contagens e a média de resolução são calculadas em SQL. A taxa divide concluídos pelo total. Somamos os quatro status e conferimos com a rosca e a lista: 500 chamados. O período dos indicadores considera a abertura.” |
| 0:50–1:30 | Selecionar Tipo Rede, Agrupar por Semana, Aplicar; mostrar linha, rosca e ranking | “Os filtros são comuns às consultas. Agora vemos Rede por semana. Na linha, abertos usam a abertura e atendidos são concluídos pela data de encerramento. A rosca apresenta os quatro status. O ranking conta o volume por tipo em todos os status selecionados.” |
| 1:30–1:55 | Período 2020-01-01 a 2020-01-31; depois Limpar, parar Uvicorn e Aplicar | “Sem registros, a interface mostra o estado vazio e a média fica como um traço. Ao parar a API, surge uma mensagem de conexão e o botão Tentar novamente. Os filtros inválidos também recebem uma mensagem clara.” |
| 1:55–2:25 | Reiniciar API; `/docs`; evidências de curl para 400 e 503 | “A documentação interativa expõe as rotas. Um status inválido retorna 400 no mesmo envelope de erro. Esta resposta 503 foi obtida com outro processo apontando para um banco inexistente. O corpo público não revela caminho nem detalhes internos.” |
| 2:25–2:45 | Terminal `python -m pytest -v` ou saída real da release | “Esta é a execução real da suíte, com 58 testes aprovados. Também medimos cinco amostras por endpoint com 10 mil registros e validamos o dashboard em Chrome headless, inclusive em 600 pixels.” |
| 2:45–3:00 | `git log --graph --oneline --all --decorate`; `GITFLOW.md` | “O histórico local preserva features, develop, release e a tag v1.0.0 em main. A situação da publicação está documentada. Os próximos passos são o adaptador autorizado para o Thindesk real e autenticação com SSO ou proxy.” |

## Preparação operacional

Execute os comandos de [README.md](../README.md). Mantenha as evidências de `curl_2026-11-13.txt`, `pytest_2026-11-13_release.txt` e `desempenho_2026-11-13.json` disponíveis para abrir diretamente. Para o 503, use a evidência já coletada ou inicie um segundo processo em outra porta com `THINDESK_DB_PATH` apontando para arquivo inexistente; não apague nem renomeie o banco principal.

Faça um ensaio para caber nos tempos. Ao final da apresentação, pare os processos iniciados. Não anuncie conexão com o Thindesk real, autenticação pronta, publicação remota ou gravação de vídeo que não ocorreu.
