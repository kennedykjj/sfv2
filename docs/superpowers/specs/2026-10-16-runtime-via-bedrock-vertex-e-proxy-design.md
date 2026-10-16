# Runtime via Bedrock, Vertex e proxy — spec

**Issue:** #87 · **Branch:** `feat/87-runtime-via-bedrock-vertex-e-proxy` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problema.** Na empresa o modelo é contratado via Bedrock, Vertex ou um proxy interno; o runtime claude só fala com a Anthropic direta. O dono precisa apontar a fábrica para o contrato da empresa sem perder a medição de custo.

**Público.** O desenvolvedor dentro de uma empresa.

**Critérios de aceitação**
- AC-1: Dado o provedor Bedrock configurado com região (e perfil opcional), quando o runtime claude inicia o subprocesso, então ele recebe `CLAUDE_CODE_USE_BEDROCK=1` e `AWS_REGION` (e `AWS_PROFILE` se configurado).
- AC-2: Dado o provedor Vertex com região e projeto, quando o subprocesso inicia, então recebe `CLAUDE_CODE_USE_VERTEX=1`, `CLOUD_ML_REGION` e `ANTHROPIC_VERTEX_PROJECT_ID`.
- AC-3: Dado um proxy com URL http(s), quando o subprocesso inicia, então recebe `ANTHROPIC_BASE_URL` com essa URL.
- AC-4: Dado nenhum provedor configurado (Anthropic direta), quando o subprocesso inicia, então nenhuma dessas variáveis chega a ele, mesmo que o ambiente do daemon as tenha.
- AC-5: Dado configuração inválida (provedor desconhecido, região ou projeto ausente, URL que não é http(s)), quando o runtime é montado ou iniciado, então há recusa que nomeia o campo, sem processo e sem transcrição.
- AC-6: Dada a transcrição de uma run por provedor, quando o custo é calculado, então um modelo presente na tabela de preços tem custo Known; um id ausente da tabela dá Partial/Unknown visível nomeando o modelo, nunca zero.
- AC-7: Dado qualquer provedor, quando a configuração é gravada ou o runtime loga, então credenciais não aparecem; elas vêm da cadeia padrão do ambiente do daemon.

**Fora de escopo.** Outros runtimes (codex); provedor por estágio; gestão ou rotação de credenciais; UI para configurar o provedor; corrigir a dependência do roadmap (#62).

**Aderência ao produto.** Atende "custo medido das transcrições" (preço da tabela, rotulado como estimativa) e o público "desenvolvedor dentro de uma empresa". Não contradiz o posicionamento nem o que está fora de escopo.

**Toca UI:** não.

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- **Contrato novo (`adapters`):** `sealed interface RuntimeProvider` com `Anthropic` (padrão), `Bedrock(region, profile: String? = null)`, `Vertex(region, projectId)` e `Proxy(baseUrl)`; `RuntimeProvider.fromEnv(env: Map<String, String>)` lê `SFV2_PROVIDER` (`anthropic|bedrock|vertex|proxy`), `SFV2_PROVIDER_REGION`, `SFV2_PROVIDER_PROFILE`, `SFV2_PROVIDER_PROJECT`, `SFV2_PROVIDER_URL`; `RuntimeProvider.environment(): Map<String, String>` devolve as variáveis do claude. `InvalidRuntimeProviderException(field)` (RuntimeException) com mensagem que nomeia o campo e nunca o valor.
- **`ClaudeRuntime`:** novo parâmetro `provider: RuntimeProvider = RuntimeProvider.Anthropic`. Antes de iniciar o processo, remove do ambiente do subprocesso `CLAUDE_CODE_USE_BEDROCK`, `CLAUDE_CODE_USE_VERTEX`, `ANTHROPIC_BASE_URL`, `AWS_REGION`, `AWS_PROFILE`, `CLOUD_ML_REGION`, `ANTHROPIC_VERTEX_PROJECT_ID`, `ANTHROPIC_BEDROCK_BASE_URL` e `ANTHROPIC_VERTEX_BASE_URL` (bases de URL próprias de cada provedor) e aplica `provider.environment()`. Credenciais (cadeia padrão AWS/GCP, `ANTHROPIC_AUTH_TOKEN`) seguem herdadas do ambiente do daemon, sem passar pela configuração.
- **Validação:** feita em `fromEnv`/construção, antes de qualquer processo ou arquivo; URL só `http`/`https` com host, sem credenciais embutidas (`usuario:senha@`), sem query e sem fragmento (AC-5 recusa nomeando `SFV2_PROVIDER_URL`; a URL pode ir para o ambiente do subprocesso, mas nunca para log nem para mensagem de erro).
- **`daemon`:** uma linha em `RefineRuntime.kt` passa `RuntimeProvider.fromEnv(env)` ao `ClaudeRuntime`; configuração inválida falha a subida, nomeando o campo. Custo: sem mudança de código; `RunCostCalculator` já precifica pelo id do modelo da transcrição e devolve Partial/Unknown quando o id não está na tabela (AC-6 trava esse comportamento com teste).
- **Camadas/módulos:** `adapters` (novo `adapters/src/main/kotlin/sfv2/adapters/RuntimeProvider.kt`, `ClaudeRuntime.kt`, `ClaudeRuntimeTest.kt`), `daemon` (`RefineRuntime.kt`, `RunCostTest.kt`).
- **Migração:** nenhuma (sem banco, sem contrato externo alterado; o padrão `Anthropic` mantém o comportamento atual).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | adapters | `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt` |
| AC-2 | adapters | `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt` |
| AC-3 | adapters | `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt` |
| AC-4 | adapters | `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt` |
| AC-5 | adapters | `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunCostTest.kt` |
| AC-7 | adapters | `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `heavy`: toca 2 camadas (`adapters`, `daemon`) e tem mais de 6 critérios; a nota da issue previa `standard` só com `adapters`.
- A configuração do provedor vem de variáveis `SFV2_PROVIDER*` no ambiente do daemon (já passado a `RefineRuntime`): não há arquivo de configuração de runtime hoje e o critério exige "a configuração do provedor" funcionando de ponta a ponta.
- Bloqueio: a issue cita #21 (fechada); a tabela do roadmap cita #62 (aberta), mas nada aqui depende da virada. Segue com #21.
- Sem arquivo de teste novo: `ClaudeRuntimeTest` cobre `fromEnv` (mesmo módulo, mesma fixture de binário falso) e `RunCostTest` cobre o custo.
- Credenciais ficam fora da configuração: a cadeia padrão (AWS/GCP, `ANTHROPIC_AUTH_TOKEN`) é herdada do ambiente do daemon.
- Sem seção Security: a issue não tem o label `cybersec`.

## Open questions
- none
