# Integração Spring Boot e Thymeleaf

## Estado e decisão de implementação

A aplicação principal passa a ser executada pelo projeto Maven na raiz, com Java 21, Spring Boot 3.5.16 e Thymeleaf. O backend Java acessa o SQLite existente e preserva as regras dos indicadores, filtros, séries e paginação. O código Python em `api/` e o frontend original em `dashboard/` permanecem disponíveis como referência histórica; não é necessário executá-los junto do servidor Java.

O conteúdo de `thindesk.zip` foi inspecionado antes da integração. Foram reaproveitados o logo original sem alteração, o Maven Wrapper com sua licença, a organização do menu e os campos/fluxos de chamados, clientes e horários. Os arquivos compilados de `target/` do ZIP, configurações particulares do NetBeans e credenciais não foram incorporados.

## Problemas identificados e correções

| Origem | Problema encontrado | Correção |
| --- | --- | --- |
| `pom.xml` do ZIP | Java 23; starters com versões individuais; Java EE `javax` e mecanismo endorsed legado | Parent do Spring Boot para dependências/plugins, Java 21 e JAR executável; sem Java EE legado, Lombok ou dependência de servidor de aplicação |
| Wrapper Windows | Caminho do executável sem aspas e falha de acesso ao renomear a pasta temporária | Caminho protegido, instalação por cópia e checksum SHA-256 da distribuição, conferido com o repositório oficial; cabeçalhos Apache preservados |
| `Cliente.java` | Anotações JPA e MongoDB misturadas | Modelos de formulário Java e repositório JDBC coerentes com o SQLite já existente |
| Configuração do ZIP | URI MongoDB e senha embutidas; propriedade de carga JSON sem implementação correspondente | Nenhum segredo copiado; configuração via ambiente; seed Java explícito e idempotente |
| Controllers REST | Métodos retornavam nomes de templates ou `redirect:` como texto | Controllers MVC para HTML/redirect e controllers REST para JSON separados |
| Rotas de formulários | Links para criar/editar/excluir sem handlers ou em prefixos incompatíveis | Rotas MVC correspondentes; alterações via POST com CSRF; APIs JSON para os mesmos serviços |
| Horários | Modelo com nome incompatível e ausência de `novoHorario` | Modelo consistente, objeto de formulário e lista de horários presentes em criação, edição e validação |
| Layout | Conteúdo fixo de home, páginas duplicadas e versões diferentes do Bootstrap | Fragmento Thymeleaf parametrizado, sidebar compartilhada e CSS local único para navegação/formulários |
| Estáticos | Listener assumia existência de botão; referências absolutas ignoravam context path | JavaScript com verificação dos elementos e URLs Thymeleaf `@{...}`; dashboard usa base da API renderizada pelo servidor |
| Navegação | Técnicos/tipos/recursos sem destino funcional | Catálogos de consulta de técnicos e tipos; recursos direciona à documentação |
| Login | Mensagens ausentes, recuperação de senha sem implementação e CSRF desativado | Login configurável, mensagens de erro/logout, BCrypt ligado ao provider e CSRF habilitado |
| Erros | Página externa incorporada por iframe e exceções genéricas | Template local em português e envelope JSON uniforme, incluindo banco indisponível e 404 da API |
| OpenAPI | Contrato automático antigo anunciava 422, enquanto a aplicação usava 400 | Especificação 3.0.3 com 400, erros, parâmetros e esquemas; `/docs` em Thymeleaf com consulta GET interativa |

## Estrutura atual

```text
pom.xml, mvnw, mvnw.cmd, .mvn/wrapper/
src/main/java/com/pfc/
  api/       endpoints JSON, filtros, erros e OpenAPI
  config/    propriedades e segurança
  data/      conexões e agregações SQL
  model/     formulários e validação
  service/   gestão e seed explícito
  web/       controllers de páginas e tratamento HTML de erros
src/main/resources/
  application.properties
  api/openapi.json
  templates/ layout, fragments, dashboard, chamados, clientes, horários, login e erros
  static/    css, js e images/logo.png
src/test/java/com/pfc/
```

## Persistência e compatibilidade

O caminho padrão é `api/data/thindesk.db`, relativo à raiz de execução. Inicializar o schema cria somente tabelas/índices ausentes e adiciona `tickets.usuario` se necessário. Nenhum chamado é apagado ou substituído. As novas tabelas são `clientes` e `horarios_atendimento`. O seed Java só insere exemplos se a tabela de chamados estiver vazia.

Consultas do dashboard continuam em conexões SQLite `mode=ro`. A criação/edição de chamados e cadastros usa transações de escrita explícitas. Valores de SQL são parametrizados. O acesso de páginas e API compartilha o mesmo serviço/banco; alterações feitas nas páginas aparecem no dashboard na consulta seguinte.

Permanecem as regras anteriores: período de abertura nos cards/distribuição/ranking/lista, fechamento nos atendidos da série, quatro status, média apenas de concluídos e ranking de volume de todos os status filtrados. A descrição do gráfico esclarece esse critério; `status=concluido` restringe o ranking a atendidos.

## Autenticação e limites

O modo local mantém `THINDESK_SECURITY_ENABLED=false`. CSRF permanece ativo inclusive nesse modo: formulários Thymeleaf inserem o token automaticamente; chamadas JSON de alteração precisam do token da sessão, no header `X-CSRF-TOKEN`. Ao habilitar login, configure `THINDESK_USERNAME` e `THINDESK_PASSWORD`; valores ausentes impedem a inicialização. Páginas protegidas redirecionam ao login e chamadas de API não autenticadas retornam JSON 401.

Não há conexão ativa com o cluster MongoDB citado no ZIP. Esta entrega usa o banco SQLite já adotado pelo projeto, e não afirma importar dados de produção do Thindesk. Para incorporar MongoDB futuramente, são necessários URI autorizada via ambiente, mapeamento dos documentos (o JSON do ZIP não possui datas necessárias aos indicadores) e testes contra o banco. As credenciais expostas no material original devem ser substituídas/rotacionadas pelo responsável; o ZIP original não foi alterado.

Chart.js 4.4.1 continua pela CDN e exige internet para os gráficos. O restante do layout e o logo são locais. O vídeo de apresentação permanece pendente. A nova branch local é `feature/spring-thymeleaf`; nenhuma publicação remota desta alteração está implícita na execução dos testes.

## Referências técnicas

[Spring Boot 3.5: requisitos](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Spring MVC com Thymeleaf](https://spring.io/guides/gs/serving-web-content/) e [fragmentos/expressões Thymeleaf 3.1](https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html).
