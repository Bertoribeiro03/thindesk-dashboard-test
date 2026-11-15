# Testes — Spring Boot e Thymeleaf

## Execução real em 2026-11-14

Backend Spring Boot 3.5.16, JDK 21 e Maven 3.9.9. O Wrapper foi testado com instalação em um cache novo dentro do workspace, incluindo caminho com espaços e checksum da distribuição. O ambiente efetivo usou Oracle JDK 21.0.11 (JAVA_HOME); nenhum JDK foi instalado ou alterado por esta integração.

`mvnw.cmd -B -ntp verify`: **BUILD SUCCESS; 63 testes, 0 falhas, 0 erros e 0 ignorados**. [Saída real consolidada em UTF-8](evidencias/maven_spring_2026-11-14.txt). A conversão de codificação do console Windows preserva o conteúdo da execução. [Wrapper](evidencias/wrapper_spring_2026-11-14.txt), [ambiente, hashes e origem do ZIP](evidencias/ambiente_spring_2026-11-14.json).

| Suíte | Casos | Falhas | Erros | Tempo da suíte (s) |
| --- | ---: | ---: | ---: | ---: |
| com.pfc.ApplicationTest | 53 | 0 | 0 | 17.649 |
| com.pfc.DatabaseUnavailableTest | 7 | 0 | 0 | 1.776 |
| com.pfc.SecurityTest | 2 | 0 | 0 | 2.227 |
| com.pfc.SeedTest | 1 | 0 | 0 | 1.515 |

Os testes cobrem indicadores, filtros CSV, datas malformadas, ordem de validação, período longo, lacunas da série diária/mensal/semanal, semana 00, fim inclusivo, atendimento pela data de encerramento, distribuição, ranking, paginação e números de página muito grandes. Também cobrem renderização de todos os templates, existência de estáticos, 404 HTML/JSON, métodos HTTP, contrato OpenAPI 400, criação/edição de chamados, clientes/horários, mensagens de validação, escape HTML, leitura somente, CSRF, login/logout, credenciais ausentes e seed sem substituição de dados.

Prioridade nula retorna validação 400; corpo com tipo de mídia não suportado preserva 415 em envelope JSON. Os bancos temporários ficam em `target/test-data/`. As rotas de leitura usam SQLite somente leitura, confirmado por tentativa de DELETE rejeitada no teste. Sete cenários verificam banco inexistente → 503 sem expor o caminho no corpo.

O JAR foi reempacotado após os ajustes finais de layout e metadados OpenAPI. A verificação HTTP e a de navegador abaixo usaram o mesmo JAR final, identificado por SHA-256 nos três relatórios. Avisos de bibliotecas/JVM, quando emitidos, estão preservados no log; não foram ocultados nem transformados em aprovação.

## Relatórios JUnit

- [com.pfc.ApplicationTest](evidencias/junit_spring_2026-11-14_TEST-com.pfc.ApplicationTest.xml)
- [com.pfc.DatabaseUnavailableTest](evidencias/junit_spring_2026-11-14_TEST-com.pfc.DatabaseUnavailableTest.xml)
- [com.pfc.SecurityTest](evidencias/junit_spring_2026-11-14_TEST-com.pfc.SecurityTest.xml)
- [com.pfc.SeedTest](evidencias/junit_spring_2026-11-14_TEST-com.pfc.SeedTest.xml)

## Regressão da implementação anterior

Resultado real da suíte Python preservada: **58 passed, 187 warnings in 4.25s**. [Saída real](evidencias/pytest_legado_2026-11-14.txt). Nenhum arquivo em `api/` ou `dashboard/` foi modificado para implementar a versão Java; os testes continuam utilizando bancos temporários próprios. Os avisos de depreciação dessa suíte já existiam com Python 3.14.

## HTTP do JAR executável

As chamadas foram executadas por curl contra processos Java próprios, com bancos separados. Foram verificados os sete endpoints do dashboard, HTML renderizado de dashboard/chamados/clientes/horários/documentação, OpenAPI, erros de validação 400, recurso inexistente 404, POST sem CSRF 403 e banco inexistente 503.

Consistência real: **total=500 = soma dos cards de status=500 = soma da rosca=500 = total da lista=500**. Taxa e duração são métricas e não entram na soma.

[Comandos e saídas reais de curl](evidencias/curl_spring_2026-11-14.txt), [respostas e medições estruturadas](evidencias/http_spring_2026-11-14.json).

## Desempenho — 10.000 chamados

Banco separado, gerado pelo seed determinístico anterior para comparar os mesmos tipos de registros. O consumidor HTTP é o JAR Spring Boot. Uma chamada de aquecimento e cinco amostras sequenciais por rota, `curl -w "%{time_total}"`, em loopback, sem concorrência de requisições de benchmark. Não é garantia de desempenho em produção nem teste de carga prolongada.

| Rota (prefixo /api/v1) | Mediana (ms) | Máximo (ms) | Todas < 500 ms |
| --- | ---: | ---: | --- |
| `/dashboard/summary` | 32.713 | 35.046 | Sim |
| `/dashboard/timeseries` | 66.366 | 86.077 | Sim |
| `/dashboard/status-distribution` | 12.254 | 14.809 | Sim |
| `/dashboard/top-types?limit=8` | 36.903 | 42.080 | Sim |
| `/tickets?page_size=100` | 20.652 | 35.483 | Sim |

## Navegador real — Chrome headless

**19 verificações aprovadas; 0 exceções JavaScript não tratadas.** O JAR foi iniciado na porta 8081 sob o context path `/central`, com banco próprio de 500 exemplos. A criação de um chamado elevou o total para 501 e a edição para concluído apareceu na listagem; o banco principal não foi alterado. [Relatório integral](evidencias/dashboard_spring_2026-11-14.json).

| Cenário | Resultado |
| --- | --- |
| Carga inicial e Chart.js real via CDN | APROVADO |
| Paginação | APROVADO |
| Filtro Rede com agrupamento semanal e retorno à página 1 | APROVADO |
| Período invertido bloqueado sem requisição HTTP | APROVADO |
| Estado vazio e valor nulo | APROVADO |
| Layout responsivo em 600 px | APROVADO |
| Menu móvel abre e fecha com Escape | APROVADO |
| Escape de conteúdo da API contra XSS | APROVADO |
| Tratamento de Chart.js indisponível | APROVADO |
| Atualização automática real após 60 s | APROVADO |
| Timeout real de 10 segundos | APROVADO |
| Erro com API realmente parada | APROVADO |
| Recuperação pelo botão Tentar novamente | APROVADO |
| Tentar novamente refaz init quando faltam metadados | APROVADO |
| Thymeleaf e recursos sob context path /central | APROVADO |
| Criar, editar e excluir cliente em formulários Thymeleaf | APROVADO |
| Validação e persistência de horário | APROVADO |
| Criação e edição de chamado refletidas no dashboard | APROVADO |
| Menu completo e consulta interativa da API | APROVADO |

O ciclo automático aguardou 60 segundos reais; o timeout reteve chamadas por 10 segundos. O processo Java foi realmente encerrado para verificar erro e recuperação. O menu móvel foi aberto/fechado, inclusive via Escape. Formulários Thymeleaf criaram/editaram/excluíram clientes, rejeitaram horários invertidos e persistiram horário válido. Links locais, scripts e logo foram conferidos sob `/central`; a página curta de clientes preenche a altura do viewport e não exibe faixa de fundo indevida.

Capturas reais revisadas visualmente:

- [Dashboard desktop](evidencias/spring_dashboard_desktop_2026-11-14.png)
- [Dashboard em 600 px](evidencias/spring_dashboard_600px_2026-11-14.png)
- [Estado vazio](evidencias/spring_dashboard_vazio_2026-11-14.png)
- [API parada](evidencias/spring_dashboard_erro_2026-11-14.png)
- [Chamados](evidencias/spring_chamados_2026-11-14.png)
- [Clientes](evidencias/spring_clientes_2026-11-14.png)
- [Horários](evidencias/spring_horarios_2026-11-14.png)

Os processos Java e o Chrome headless próprios foram encerrados pelas ferramentas ao final. As falhas encontradas durante a implementação foram corrigidas e as execuções completas finais acima foram as usadas para aprovação.

## Reproduzir

```powershell
.\mvnw.cmd -B -ntp verify
python docs/ferramentas/verificar_http_spring.py
node docs/ferramentas/verificar_spring.mjs
```

As ferramentas HTTP/navegador exigem as portas 8081–8083 livres, um JAR já empacotado e Chrome/Edge/Node para a etapa visual. O gerador Python de 10 mil registros usa somente a biblioteca padrão. Nenhum pacote npm é instalado.

## Limitações

- MongoDB/Thindesk de produção: não conectado nem testado; depende de acesso autorizado e mapeamento das datas/status.
- Outros sistemas operacionais e versões de JDK: não executados neste ambiente; foi utilizado Windows 11 com JDK 21.
- SSO, múltiplos usuários, recuperação de senha e carga prolongada: não implementados/testados nesta entrega.
- Chart.js depende de CDN; a indisponibilidade tem mensagem específica, verificada no navegador.
- Vídeo e publicação remota da migração continuam pendentes; não são inferidos dos testes locais.
