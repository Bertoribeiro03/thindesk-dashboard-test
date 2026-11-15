# Roteiro de apresentação — até 3 minutos

**O vídeo ainda não foi gravado.** Este é o roteiro para gravação pelo responsável. As evidências existentes são arquivos de teste, respostas HTTP e capturas headless reais. Prepare a API com o seed de 500 registros, deixe o terminal do Uvicorn acessível e abra o dashboard e `/docs` antes de começar.

| Tempo | Tela / ação | Fala sugerida |
| --- | --- | --- |
| 0:00–0:20 | Dashboard e arquitetura de `MIGRACAO_SPRING.md`; apontar badge e logo | “Este dashboard usa Spring Boot, Thymeleaf e SQLite local. O logo e os fluxos de cadastro vieram do material Thindesk. Os dados são exemplos identificados na tela; a API agrega os indicadores em conexões de leitura.” |
| 0:20–0:50 | Sete cards e código de `repository.summary` | “As contagens e a média de resolução são calculadas em SQL. A taxa divide concluídos pelo total. Somamos os quatro status e conferimos com a rosca e a lista: 500 chamados. O período dos indicadores considera a abertura.” |
| 0:50–1:30 | Selecionar Tipo Rede, Agrupar por Semana, Aplicar; mostrar linha, rosca e ranking | “Os filtros são comuns às consultas. Agora vemos Rede por semana. Na linha, abertos usam a abertura e atendidos são concluídos pela data de encerramento. A rosca apresenta os quatro status. O ranking conta o volume por tipo em todos os status selecionados.” |
| 1:30–1:55 | Período 2020-01-01 a 2020-01-31; depois Limpar, parar Uvicorn e Aplicar | “Sem registros, a interface mostra o estado vazio e a média fica como um traço. Ao parar a API, surge uma mensagem de conexão e o botão Tentar novamente. Os filtros inválidos também recebem uma mensagem clara.” |
| 1:55–2:25 | Reiniciar API; `/docs`; evidências de curl para 400 e 503 | “A documentação interativa expõe as rotas. Um status inválido retorna 400 no mesmo envelope de erro. Esta resposta 503 foi obtida com outro processo apontando para um banco inexistente. O corpo público não revela caminho nem detalhes internos.” |
| 2:25–2:45 | Saída real de `mvnw verify` e relatório JUnit | “Esta é a execução real dos testes Java, incluindo templates, formulários, persistência, CSRF e login. A suíte Python anterior também foi preservada e executada. Há medições com 10 mil registros e verificações reais em Chrome headless.” |
| 2:45–3:00 | `git log --graph --oneline --all --decorate`; `GITFLOW.md` | “O histórico anterior mantém features, develop, release e a tag v1.0.0. A migração está em feature/spring-thymeleaf e sua publicação está documentada. A conexão de produção depende de acesso autorizado e mapeamento dos dados.” |

## Preparação operacional

Execute os comandos de [README.md](../README.md). Prepare a saída real de `mvnw verify` antes de gravar o trecho de 20 segundos dos testes. Mantenha as evidências Spring de `TESTES_SPRING.md` abertas. Para o 503, use a transcrição já coletada ou inicie um segundo processo em outra porta com `THINDESK_INITIALIZE=false` e `THINDESK_DB_PATH` apontando para arquivo inexistente; não apague nem renomeie o banco principal.

Faça um ensaio para caber nos tempos. Ao final da apresentação, pare os processos iniciados. Não anuncie conexão com o Thindesk real, autenticação pronta, publicação remota ou gravação de vídeo que não ocorreu.
