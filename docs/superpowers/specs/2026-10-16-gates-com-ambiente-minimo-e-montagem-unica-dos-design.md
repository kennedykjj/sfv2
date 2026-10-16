# Gates com ambiente mínimo e montagem única dos estágios no RefineRuntime — spec

**Issue:** #163 · **Branch:** `feat/163-gates-com-ambiente-minimo-e-montagem-unica-dos` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problema.** Gates de camada rodam `./gradlew` com testes escritos por agente, e o processo filho herda o ambiente inteiro do daemon (tokens e segredos incluídos). Além disso, `maxRounds` (padrão 3) está repetido em três pipelines e a montagem dos estágios tem lançador de thread/lock duplicado no Validate.

**Audiência.** O desenvolvedor dentro de uma empresa (segredos do daemon não podem vazar para código de agente); também o desenvolvedor sozinho com muitos projetos.

**Critérios de aceitação**
- AC-1: Dado um gate de camada que executa um processo filho, quando o `ProcessGateRunner` o inicia, então o filho recebe apenas PATH, HOME, JAVA_HOME e variáveis `GRADLE_*`.
- AC-2: Dado o daemon com uma variável de token ou segredo no ambiente, quando um gate roda, então o filho não enxerga essa variável.
- AC-3: Dado um gate `./gradlew` com PATH, HOME e JAVA_HOME definidos, quando roda com o ambiente mínimo, então ele executa normalmente (sem regressão).
- AC-4: Dado Refine, Develop e Validate sem configuração de rodadas, quando cada um decide o limite de rodadas, então os três usam o mesmo valor 3, vindo de uma única definição.
- AC-5: Dado o `maxRounds` configurado uma vez, quando o valor muda nessa única fonte, então os três pipelines passam a usar o novo valor.
- AC-6: Dado o `RefineRuntime` montado, quando se inspecionam os estágios, então há um único `StageRouter` e todos os estágios (Refine, Develop, Validate) usam o mesmo `PipelineStageLauncher`; não resta lançador próprio de thread/lock no Validate.
- AC-7: Dado um card percorrendo Refine, Develop e Validate, quando os estágios rodam, então o comportamento observável (colunas, eventos, bloqueio por lock) é igual ao de antes.
- AC-8: Dado a spec do develop (#40), quando lida, então diz que `maxRounds` vem da fonte única, sem "padrão 3, sem configuração nova" contraditório.

**Fora de escopo**
- Configuração de `maxRounds` por projeto ou por card pela UI/CLI.
- Lista de variáveis de ambiente configurável pelo dono.
- Sandbox de processo além do ambiente (rede, sistema de arquivos).
- Mudar o valor padrão (continua 3) ou a lógica das rodadas.

**Adequação ao produto.** Serve "gates são scripts, não a palavra do agente" e "nada sai da máquina sem assinatura": o código escrito por agente não alcança segredos do daemon. Não contradiz posicionamento nem fora de escopo.

**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Camada tocada: `daemon` apenas. Nenhum contrato externo (endpoint, evento, DB) muda; sem migração.

- `daemon/src/main/kotlin/sfv2/daemon/ProcessGateRunner.kt`: antes de `start()`, `ProcessBuilder.environment()` é limpo e repovoado só com `PATH`, `HOME`, `JAVA_HOME` e as chaves que começam com `GRADLE_`, copiadas de `System.getenv()`. A função de filtragem é pura e recebe o mapa de origem (`minimalEnvironment(source: Map<String, String>): Map<String, String>`), para teste sem mexer no ambiente real.
- `daemon/src/main/kotlin/sfv2/daemon/RefinePipeline.kt`: `PipelineStageLauncher` continua sendo o único launcher; ganha a constante pública `const val DEFAULT_MAX_ROUNDS = 3` (nível de arquivo, única definição) e `RefinePipeline` usa-a como default de `maxRounds`.
- `daemon/src/main/kotlin/sfv2/daemon/DevelopPipeline.kt` e `ValidatePipeline.kt`: o default de `maxRounds` passa a `DEFAULT_MAX_ROUNDS`.
- `daemon/src/main/kotlin/sfv2/daemon/ValidatePipeline.kt`: `ValidateStageLauncher` passa a delegar a `PipelineStageLauncher("validate", ...)`, como Refine e Develop; somem a thread, o `execute` e o `awaitLock` próprios.
- `daemon/src/main/kotlin/sfv2/daemon/RefineRuntime.kt`: sem mudança de montagem; já tem um só `StageRouter` e um só `pool`.
- `docs/superpowers/specs/2026-10-16-pipeline-de-develop-design.md`: texto de `maxRounds` (fora de escopo e linha de arquitetura) alinhado à fonte única `DEFAULT_MAX_ROUNDS`.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProcessGateRunnerTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProcessGateRunnerTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProcessGateRunnerTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DevelopPipelineTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/DevelopPipelineTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |
| AC-7 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ValidatePipelineTest.kt` |
| AC-8 | manual | |

| AC | Layer | Test file |
|---|---|---|

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `heavy`: a regra dispara por mais de 6 critérios (8), embora a mudança toque só a camada `daemon`; o dono pode baixar para `standard`.
- Fonte única de `maxRounds` é uma constante `DEFAULT_MAX_ROUNDS = 3` em código: o #40 já decidiu "sem configuração nova", e configuração por projeto está fora de escopo.
- AC-5 ("mudar na fonte muda os três") é verificado por teste que compara o default de cada pipeline com `DEFAULT_MAX_ROUNDS`; uma constante não muda em runtime.
- `StageRouter` já é único no `RefineRuntime` e Refine/Develop já usam `PipelineStageLauncher`; só `ValidateStageLauncher` ainda duplica thread/lock, e é o que o AC-6 elimina.
- Arquivo de teste novo `ProcessGateRunnerTest.kt`: nenhuma suite cobre `ProcessGateRunner` (o `GateExecutorTest` usa runner fake). O teste roda um processo real (`env`) com o mapa de origem injetado, sem `Thread.sleep`.
- AC-8 é `manual`: verificado na revisão lendo o diff da spec do #40.
- Lista do ambiente mínimo fixa (PATH, HOME, JAVA_HOME, `GRADLE_*`), como pedido na issue; configurável fica fora de escopo.

## Open questions
- none
