# Bedrock sem perfil configurado descarta o AWS_PROFILE do daemon — spec

**Issue:** #223 · **Branch:** `feat/223-bedrock-sem-perfil-preserva-aws-profile` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** light

## Product
_Product Owner_

**Problem:** Com `SFV2_PROVIDER=bedrock` e sem `SFV2_PROVIDER_PROFILE`, o `ClaudeRuntime` remove `AWS_PROFILE` do ambiente do subprocesso, porque `AWS_PROFILE` está em `PROVIDER_VARIABLES` e o Bedrock sem perfil não o repõe. Quem já exporta `AWS_PROFILE` no daemon (a cadeia padrão de credenciais da AWS, como diz a spec do #87, AC-7) perde o perfil: o `claude` cai na credencial padrão ou falha na autenticação, sem aviso.
**Audience:** o dono que roda o daemon com Bedrock e escolhe o perfil AWS pelo ambiente do daemon.
**Acceptance criteria**
- **AC-1** — Given o provedor Bedrock com região e sem perfil configurado, e o ambiente do daemon com `AWS_PROFILE=daemon-profile`, when o runtime claude inicia o subprocesso, then o subprocesso recebe `AWS_PROFILE=daemon-profile`, além de `CLAUDE_CODE_USE_BEDROCK=1` e `AWS_REGION` da configuração.
- **AC-2** — Given o provedor Bedrock com perfil configurado (`SFV2_PROVIDER_PROFILE`) e o ambiente do daemon com outro `AWS_PROFILE`, when o runtime claude inicia o subprocesso, then o subprocesso recebe o perfil configurado, não o do daemon.
- **AC-3** — Given nenhum provedor configurado (Anthropic direta), ou Vertex, ou proxy, e o ambiente do daemon com `AWS_PROFILE` definido, when o runtime claude inicia o subprocesso, then `AWS_PROFILE` não chega a ele.
- **AC-4** — Given o provedor Bedrock sem perfil e o ambiente do daemon com variáveis de outros provedores (`CLAUDE_CODE_USE_VERTEX`, `ANTHROPIC_BASE_URL`, `CLOUD_ML_REGION`, `ANTHROPIC_VERTEX_PROJECT_ID`, `ANTHROPIC_BEDROCK_BASE_URL`, `ANTHROPIC_VERTEX_BASE_URL`), when o runtime claude inicia o subprocesso, then nenhuma dessas variáveis chega a ele e o `AWS_REGION` do daemon é substituído pelo da configuração.
**Out of scope:** novas variáveis de provedor; validar se o perfil existe no `~/.aws`; credenciais (`AWS_ACCESS_KEY_ID` etc.) continuam vindo do ambiente do daemon sem tratamento; mudança de formato de `SFV2_PROVIDER_*`.
**Product fit:** a spec do #87 promete que as credenciais vêm da cadeia padrão do ambiente do daemon; descartar `AWS_PROFILE` quebra essa promessa só no caso mais comum (Bedrock com perfil exportado).
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Camada `adapters`. A causa é a remoção incondicional de `PROVIDER_VARIABLES` em `adapters/src/main/kotlin/sfv2/adapters/ClaudeRuntime.kt` (linha 44), que trata `AWS_PROFILE` como variável que o provedor sempre decide.

- `adapters/src/main/kotlin/sfv2/adapters/RuntimeProvider.kt`: o provedor passa a declarar quais variáveis do daemon ele deixa passar. Novo membro `fun inheritedVariables(): Set<String> = emptySet()` na interface; `Bedrock` sobrescreve e devolve `setOf("AWS_PROFILE")` quando `profile == null`. Com perfil, devolve vazio e `environment()` já sobrescreve o valor.
- `adapters/src/main/kotlin/sfv2/adapters/ClaudeRuntime.kt`: remover `PROVIDER_VARIABLES - provider.inheritedVariables()` antes de aplicar `providerEnvironment`. A ordem (remover, depois `putAll`) é mantida, então o perfil configurado continua vencendo (AC-2).
- Sem mudança de protocolo, de evento, de banco, de `daemon` nem de configuração: `SFV2_PROVIDER_PROFILE` continua opcional e vazio continua tratado como ausente.
- Nada é logado: `AWS_PROFILE` é nome de perfil, não segredo, e não entra em log nem em mensagem de erro (regra de AC-7 do #87 preservada).

## Test plan
_Tech Lead_
<!-- One row per AC: a layer from the config (or manual) and the full repo-relative test file path in backticks, inside that layer's tests. Checked by `gate.sh test-plan`.
     Name the module's existing test file; several ACs share it. A new test file only when no suite covers the module, with the reason in ## Decisions. -->

| AC | Layer | Test file |
|---|---|---|
| AC-1 | adapters | `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt` |
| AC-2 | adapters | `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt` |
| AC-3 | adapters | `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt` |
| AC-4 | adapters | `adapters/src/test/kotlin/sfv2/adapters/ClaudeRuntimeTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Preservar só `AWS_PROFILE`, e só no Bedrock sem perfil: as demais variáveis de provedor seguem removidas para o AC-4 do #87 (nenhum vazamento entre provedores) continuar valendo.
- Mecanismo no próprio `RuntimeProvider` (`inheritedVariables`) em vez de `if` no `ClaudeRuntime`: o provedor já é dono da regra de quais variáveis usa, e o runtime não passa a conhecer Bedrock.
- Perfil configurado vence o do daemon: configuração explícita do dono tem precedência sobre o ambiente herdado.
- Teste existente `provider AC-4 a configured provider replaces stale provider variables...` em `ClaudeRuntimeTest.kt` (linha 322) espera `AWS_PROFILE` ausente com Bedrock sem perfil; ele passa a afirmar o valor do daemon (comportamento corrigido). Reaproveita a suíte do módulo; nenhum arquivo de teste novo.

## Open questions
- none
