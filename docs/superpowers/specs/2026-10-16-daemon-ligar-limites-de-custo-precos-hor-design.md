# Daemon: ligar limites de custo, preços, horário e habilitação explícita na composição — spec

**Issue:** #159 · **Branch:** `feat/159-daemon-ligar-limites-de-custo-precos-hor` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, Cybersec
**Weight:** heavy

## Product
_Product Owner_

**Problema:** o dispatcher liga o `claude` real assim que há projeto e card, sem teto de gasto, sem preços (todo custo `UNKNOWN`), sem horário, sem habilitação do dono, e falha com mais de um projeto registrado.
**Audiência:** o desenvolvedor sozinho com muitos projetos (até dez numa máquina) e o desenvolvedor dentro de empresa, que precisa de teto de gasto e controle sobre quando a fábrica trabalha.

**Critérios de aceitação**
- **AC-1:** Dado o daemon iniciado sem `dispatcher.enabled` verdadeiro (no `factory.yml` da `main` ou na variável de ambiente do daemon), quando há card em Ready for Refinement, então nenhum agente é lançado e o estado (habilitado/desabilitado) aparece em log.
- **AC-2:** Dado `cost.per_card_usd` e `cost.per_day_usd` configurados com o `CostBudget` como `LaunchGuard`, quando um limite é atingido, então nenhum run novo começa; sem limites configurados, o dispatcher não lança (falha fechada), e só a variável de ambiente `SFV2_COST_UNLIMITED=true` do daemon (nunca o `factory.yml`) o libera sem teto.
- **AC-3:** Dado `prices.yml` válido no diretório de dados, quando o daemon inicia, então a `PriceTable` vem dele; com o arquivo ausente ou inválido, o dispatcher não inicia e o erro nomeia o campo (nunca preço vazio em silêncio).
- **AC-4:** Dado `work_hours` no `factory.yml`, quando o dispatcher roda, então só lança dentro do horário; sem `work_hours`, vale sempre.
- **AC-5:** Dado mais de um projeto registrado, quando um card é refinado, então o projeto vem do vínculo `card_project` do card, não de `singleOrNull`.

**Fora do escopo:** desenvolver e validar (#40, #41); comandos da CLI para pausar/retomar; combinar configurações de vários projetos.

**Aderência ao produto:** serve "custo medido e limitado" (custo como estimativa de tabela de preços) e "nada sai da máquina sem decisão explícita do dono": a fábrica só trabalha quando o dono habilita. Não contradiz posicionamento nem o que está fora de escopo.

**Toca UI:** não

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Camada única: `daemon`. Nenhum contrato público (socket, MCP, store) muda.

**Arquivos**
- `daemon/src/main/kotlin/sfv2/daemon/DispatcherSettings.kt` (novo): `data class DispatcherSettings(enabled: Boolean, limits: CostLimits, unlimited: Boolean, schedule: WorkSchedule, zone: ZoneId)` e `DispatcherSettings.resolve(factoryYaml: Map<String, Any?>?, env: Map<String, String>)`. Lê `dispatcher.enabled`, `cost.per_card_usd`, `cost.per_day_usd` (`BigDecimal`, positivos) e `work_hours`; `unlimited` vem SÓ da variável `SFV2_COST_UNLIMITED` (`true` exato); uma chave `cost.unlimited` no `factory.yml` é ignorada e registrada em log como `state=invalid field=cost.unlimited` (remover o teto não pode ser um commit no repositório) (via `WorkHours.from`). `SFV2_DISPATCHER_ENABLED` (`true`/`false`) vence o `factory.yml`. Campo inválido lança `InvalidDispatcherSettingsException(field, message)`. `dispatcher.enabled` ausente = `false`. Sem `cost.per_card_usd`/`cost.per_day_usd` e sem `SFV2_COST_UNLIMITED=true` no ambiente → configuração inválida para lançar (falha fechada).
- `daemon/src/main/kotlin/sfv2/daemon/ConfiguredDispatchControl.kt` (novo): implementa `LaunchGuard` e `WorkSchedule` sobre um provedor de `DispatcherSettings` (lê o `factory.yml` da `main` do primeiro projeto registrado via `ProjectConfigSource`, a cada tick). Enquanto não há projeto, a habilitação está desligada ou a configuração é inválida: `allows` = false, `dayLimitReached` = true, `isWorkTime` = false. Com limites válidos delega a um `CostBudget` (zona de `work_hours.timezone`, senão UTC). Expõe `onRunFinished(cardId)` que delega ao `CostBudget`. Loga (`key=value`, sem texto do YAML) só na mudança de estado: `dispatcher_state state=enabled|disabled|invalid field=<campo>`.
- `ProjectRegistry.kt` não muda: o provedor lê o texto por `ProjectConfigSource.read(repo)` e faz o próprio parse YAML (mesmos limites de tamanho e aliases).
- `RefineRuntime.kt`: recebe `source: ProjectConfigSource` e `env`; carrega `PriceTable.parse` de `dataDir/prices.yml` (ausente ou inválido → `RefineRuntime.start` não inicia o dispatcher e loga `dispatcher_not_started field=prices.yml.<detalhe>`; o daemon e o MCP seguem de pé); troca `LaunchGuard.Open`, `AlwaysWorkTime` e o `PAUSE` mantido; passa o `ConfiguredDispatchControl` como `launchGuard` e `schedule`. O vínculo do projeto vem de `store.projectOf(cardId)` casado com `registry.list()` por `repo`.
- `RefinePipeline.kt` (`RefineStageLauncher`): recebe `onRunFinished: (String) -> Unit` chamado em `finally` após `pipelineFor(cardId).run(cardId)`, para o `CostBudget` bloquear o card ao estourar `cost.per_card_usd` (hoje `onRunFinished` não é chamado em produção).
- `Main.kt`: repassa `env` e `source`; só chama `dispatcher.start()` se `RefineRuntime` o permitir.

**Projeto do card:** `store.projectOf(cardId)`; sem vínculo e com exatamente um projeto registrado, usa esse e grava o vínculo com `store.assignProject` (hoje nada grava o vínculo em produção); sem vínculo e com mais de um projeto → `IllegalStateException` com o id do card (o launcher já loga `refine_failed`). Vínculo para repo não registrado → erro com o id do card.

**Migração:** nenhuma (sem schema, sem evento novo). `## Rollback`: n/a.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DispatcherSettingsTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DispatcherTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunCostTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/WorkHoursTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RefinePipelineTest.kt` |

## Security
_Cybersec_

- **A `main` não é totalmente confiável:** quem mergeia pode pôr `dispatcher.enabled`, mas NÃO pode remover o teto de custo: `unlimited` só existe no ambiente do daemon. `SFV2_DISPATCHER_ENABLED` vence sempre; só `true`/`false` exatos valem, qualquer outro valor é inválido e deixa desligado. Teste de precedência da variável de ambiente.
- **Falha fechada:** qualquer exceção ao ler, parsear ou resolver as configurações (I/O, YAML malformado, tipo errado, fuso inválido) dá `allows=false`, `dayLimitReached=true`, `isWorkTime=false`; nunca `Open` nem `unlimited`. Limites `<= 0`, não numéricos ou ausentes são inválidos; números como `BigDecimal` a partir de texto, nunca `Double`; `unlimited: "true"` (texto) e `enabled: "yes"` são inválidos.
- **YAML não confiável** (`factory.yml` e `prices.yml`): mesmos limites de tamanho e aliases do `ProjectRegistry`, só mapa na raiz. `prices.yml` fica fixo em `dataDir/prices.yml`, sem caminho vindo de config.
- **Log sem vazamento:** só `state`, `field` e id do card; nunca valor nem texto do YAML nem mensagem crua do parser (em `InvalidDispatcherSettingsException`, `dispatcher_not_started`, `refine_failed`). Teste afirma sobre a saída do log.
- **Vínculo do card:** sem fallback silencioso entre repositórios; o fallback de projeto único só vale com exatamente um projeto registrado; repo não registrado falha.
- **Custo:** `onRunFinished` roda em `finally`; custo `UNKNOWN` por modelo sem preço bloqueia (já é o comportamento do `CostBudget`), nunca conta como zero.

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Sem projeto registrado no boot (o registro é em memória e feito depois por socket), as configurações são resolvidas a cada tick pelo `ConfiguredDispatchControl`, não só na partida: até lá o dispatcher fica fechado.
- Com vários projetos, as configurações do daemon (habilitação, custo, horário) vêm do `factory.yml` do primeiro projeto registrado: custo por dia e pool são globais, e ler de todos exigiria uma regra de combinação que ninguém pediu.
- `SFV2_DISPATCHER_ENABLED` vence o `factory.yml`: permite ao dono desligar ou ligar sem commit na `main`.
- Preços inválidos ou ausentes impedem só o dispatcher, não o daemon: o socket e o MCP continuam úteis para o dono corrigir o arquivo.
- Peso `heavy` pela regra (seção `## Security` preenchida), embora seja uma camada só.
- Decisão do dono interino sobre a pergunta do cybersec: `dispatcher.enabled` pode vir do `factory.yml` (a variável vence e pode desligar), mas `cost.unlimited` NÃO pode: o teto de custo só é removido por `SFV2_COST_UNLIMITED=true` no ambiente do daemon; o `factory.yml` da `main` muda por PR e remover o teto de gasto não deve ser um commit.
- `PAUSE` segue como está (`FilePauseSwitch` em `dataDir/PAUSE`); o comando para criá-lo está fora do escopo.
- Nenhum arquivo de teste novo: cada AC cabe numa suíte existente do módulo `daemon`, exceto o carregamento das configurações, que ganha `DispatcherSettingsTest.kt` porque nenhuma suíte cobre leitura do `factory.yml` de política de dispatcher.

## Open questions
- none
