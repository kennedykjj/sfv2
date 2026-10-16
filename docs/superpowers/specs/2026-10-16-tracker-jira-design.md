# Tracker Jira — spec

**Issue:** #85 · **Branch:** `feat/85-tracker-jira` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, Cybersec
**Weight:** heavy

## Product
_Product Owner_

**Problem:** quem trabalha numa empresa com Jira não consegue importar um ticket nem fechá-lo pela fábrica; hoje só o GitHub tem adapter da port `Tracker`.
**Audience:** o desenvolvedor dentro de uma empresa (Jira, trilha de auditoria).
**Acceptance criteria**
- **AC-1** — Dado um ticket Jira existente, quando a fábrica o busca por id, então recebe um item com título, corpo e labels como `ExternalText`, url https do ticket e estado aberto.
- **AC-2** — Dado um id que não existe no Jira, quando a fábrica busca, então o resultado é nulo, e `writeMilestone` ou `close` com esse id falham com `UnknownTrackerItemException`.
- **AC-3** — Dada uma consulta, quando a fábrica pesquisa, então recebe os tickets correspondentes; com consulta em branco recebe os tickets abertos, como no GitHub.
- **AC-4** — Dado um ticket aberto, quando a fábrica o fecha com um comentário, então o comentário é registrado no ticket, ele passa para o estado concluído e o resultado é `Closed`.
- **AC-5** — Dado um ticket já fechado, quando a fábrica o fecha de novo, então o resultado é `AlreadyClosed` e nenhum comentário duplicado é gravado.
- **AC-6** — Dado um marco opcional, quando a fábrica o grava, então ele aparece como comentário no ticket e o estado do ticket não muda.
- **AC-7** — Dado que o `acli` falha, expira, não está instalado ou devolve saída acima do limite, quando qualquer operação roda, então ela falha com `TrackerException` cuja mensagem traz a operação e o id, nunca o texto do ticket.
- **AC-9** — Dado um ticket cuja descrição vem em formato estruturado (ADF, o JSON de documento do Jira), quando o adapter o importa, então o corpo vira texto simples (só o texto dos nós, sem marcação) antes de virar `ExternalText`, e o hash é o de título e esse texto.
- **AC-8** — Dado um ticket importado, quando o texto é lido, então passa só pela `ExternalText` e o hash do texto é consistente, como no adapter do GitHub.
**Out of scope:** escolher o tracker por projeto e generalizar o `provider` hoje fixo em `github` (`CardImport`, `CardClosure`, `ExternalMilestones`) e o link do PR, que remove o sufixo `/issues/<id>` (no Jira é `/browse/PROJ-1`): sem essa ligação o `AcliTracker` não é selecionável, e ela é o follow-up deste card; fallback via MCP quando não há CLI (em discussão); sincronização contínua; criar tickets; transições de workflow customizadas por projeto; Jira em outro mecanismo que não o `acli`; relação Blocked by (#52).
**Product fit:** serve "importar e fechar, sem sincronização contínua" e o público da empresa; o Jira é tocado só na importação, em marcos opcionais e no fechamento confirmado. Não contradiz posicionamento nem o que está fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- **Contratos:** a port `Tracker` não muda. Novo `AcliTracker(site, acli = "acli", workDir, tmpDir, timeout, runner)` em `adapters`, implementando `fetch`, `search`, `writeMilestone` e `close`; falhas são `TrackerException` / `UnknownTrackerItemException` com operação e id apenas.
- **Camadas e arquivos:** só `adapters` — `adapters/src/main/kotlin/sfv2/adapters/AcliTracker.kt` (novo) e `adapters/src/test/kotlin/sfv2/adapters/AcliTrackerTest.kt` (novo). Reusa `ProcessRunner`/`ProcessRunnerImpl` e o harness de binário falso de `GhForgeTest.kt`.
- **Comportamento:** `acli jira workitem view/search/comment/transition` com saída JSON; id validado por regex de chave Jira (`PROJ-123`); consulta vira JQL com texto escapado e entre aspas; consulta em branco lista os tickets abertos (`statusCategory != Done`); `close` consulta o estado antes e devolve `AlreadyClosed` sem comentar; senão comenta e transiciona para a categoria concluída. Estado, título, corpo, labels e url mapeados para `TrackerItem.of`; url montada a partir do site validado (https).
- **Ambiente do filho:** o `ProcessRunner` ganha o modo de ambiente filtrado que o AC-7 e a seção de segurança pedem (hoje só acrescenta variáveis): o `acli` recebe só `PATH`, `HOME`, `LANG`, `LC_*` e as variáveis do próprio `acli`; variáveis cujo nome contém `TOKEN`, `SECRET`, `PASSWORD` ou `KEY` ficam de fora. Arquivos a mais: `adapters/src/main/kotlin/sfv2/adapters/ProcessRunnerImpl.kt` (a implementação) e `adapters/src/main/kotlin/sfv2/adapters/CodexRuntime.kt` (onde a interface `ProcessRunner` está declarada).
- **Mesmos limites do `GhTracker`:** saída em arquivo temporário, teto de 8 MiB, timeout, truncamento de busca no limite (falha), nenhuma credencial lida (fica no config do `acli`).
- **Migração:** nenhuma (sem banco, sem mudança de contrato).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | adapters | `adapters/src/test/kotlin/sfv2/adapters/AcliTrackerTest.kt` |
| AC-2 | adapters | `adapters/src/test/kotlin/sfv2/adapters/AcliTrackerTest.kt` |
| AC-3 | adapters | `adapters/src/test/kotlin/sfv2/adapters/AcliTrackerTest.kt` |
| AC-4 | adapters | `adapters/src/test/kotlin/sfv2/adapters/AcliTrackerTest.kt` |
| AC-5 | adapters | `adapters/src/test/kotlin/sfv2/adapters/AcliTrackerTest.kt` |
| AC-6 | adapters | `adapters/src/test/kotlin/sfv2/adapters/AcliTrackerTest.kt` |
| AC-7 | adapters | `adapters/src/test/kotlin/sfv2/adapters/AcliTrackerTest.kt` |
| AC-8 | adapters | `adapters/src/test/kotlin/sfv2/adapters/AcliTrackerTest.kt` |
| AC-9 | adapters | `adapters/src/test/kotlin/sfv2/adapters/AcliTrackerTest.kt` |

## Security
_Cybersec_

**Ameaças:** injeção de JQL e de argumento no `acli`; vazamento de credencial; texto do ticket tratado como instrução; PII/LGPD em log, erro e temporários; saída enorme ou processo pendurado.

- **Execução:** `ProcessRunner` com array de argumentos, sem shell. Stdout em arquivo temporário (permissão 600, apagado em `finally`), teto de 8 MiB e timeout.
- **Validação:** chave `^[A-Z][A-Z0-9_]{1,19}-[0-9]{1,9}\z`, site `^[a-z0-9][a-z0-9.-]{0,62}\z`; reprovado vira exceção sem executar o `acli`.
- **Argumentos:** nenhum valor externo ocupa posição lida como opção; consulta iniciando com `-` ou com caracteres de controle é recusada; valor de usuário é um argumento próprio.
- **JQL:** a consulta entra só como literal entre aspas duplas (`\` e `"` escapados) dentro de `text ~ "..."` num `AND` com a cláusula de projeto fixa montada pelo código; nunca JQL cru.
- **Comentário e marco:** texto por arquivo ou argumento isolado, nunca por shell; recusar o que comece com `-` se for argumento.
- **Segredos:** o adapter não lê nem repassa o config/token do `acli`, não aceita token por parâmetro e não herda variáveis `*TOKEN*` para o filho. Stderr/stdout do `acli` nunca vão a exceção ou log.
- **Texto não confiável:** título, corpo e comentários só como `ExternalText`; nunca viram comando, JQL, caminho ou instrução. A transição de fechamento vem de lista fechada do código e o destino é conferido na resposta do `acli` antes de executar.
- **Erros e logs:** só operação + id (`op=comment id=PROJ-12`); nunca saída bruta, consulta, corpo ou dados de pessoas.
- **LGPD:** assignee, reporter e e-mails do Jira não entram no modelo do item; o temporário com stdout é apagado após o parse.
- **Dependência:** caminho do `acli` vem de configuração; versão do binário documentada.
- **Testes de segurança (em AC-7/AC-8):** ids/site inválidos sem spawn; consultas `x" OR project = OUTRO`, `\"`, `) ORDER BY`, newline, NUL e `-x`; saída >8 MiB e timeout; canário de segredo ausente nas mensagens; comentário com `$(...)`, backticks e `--flag` chega literal; transição inexistente falha sem executar.

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `heavy`: seção `## Security` preenchida e mais de 6 critérios (o dono pode reduzir em Spec Review).
- Arquivo de teste novo `AcliTrackerTest.kt`: nenhuma suíte existente cobre o `AcliTracker`, e o `GhTrackerTest` é específico do `gh`.
- Ligar o `AcliTracker` ao daemon (escolha de tracker por projeto) fica fora: pertence aos cards #86 e #88, que dependem deste.
- Bloqueio #52 (port `Tracker`) já está fechado; o roadmap cita #62, também pré-requisito já entregue.
- Escopo do `acli` é o da conta autenticada: o limite de projeto é imposto só pelo adapter (JQL fixa + regex), risco aceito no MVP; recomendação operacional: conta de serviço restrita aos projetos necessários.
- "Estado concluído" no fechamento é a transição para a categoria Done do workflow do projeto; sem transição disponível o `close` falha com `TrackerException`.

## Open questions
- **Fallback MCP:** A (recomendado) só o `AcliTracker` neste card e o fallback MCP em card próprio, pois exige cliente MCP no `adapters` que hoje não existe — ou B os dois aqui, o que dobra o escopo.
