# Custo por transcrição — spec

**Issue:** #33 · **Branch:** `feat/33-custo-por-transcricao` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problema:** o custo de um run precisa vir do que de fato aconteceu (tokens nas transcrições), não do que o agente relata. Sem isso o dono não tem número confiável do que cada card consumiu.

**Audiência:** o desenvolvedor sozinho com muitos projetos (controla gasto em até dez projetos sem planilha); também o desenvolvedor em empresa, que precisa de trilha de auditoria do consumo.

**Critérios de aceitação**
- AC-1: Dado uma transcrição do claude e uma do codex com modelos presentes em `prices.yml`, quando o custo é calculado, então cada uma resulta num valor estimado derivado dos tokens da transcrição e dos preços da tabela.
- AC-2: Dado uma transcrição cujo modelo não consta em `prices.yml`, quando o custo é calculado, então o resultado é "desconhecido", distinto de zero, e o modelo sem preço fica identificável.
- AC-3: Dado uma transcrição com apenas parte dos modelos precificados, quando o custo é calculado, então o total não é apresentado como completo (custo parcial/desconhecido sinalizado), nunca subestimado em silêncio.
- AC-4: Dado o fim de um run, quando o daemon o encerra, então um evento de custo é registrado no card, com ator, id do run, tokens e valor estimado (ou desconhecido).
- AC-5: Dado um valor de custo exposto ao dono, quando é apresentado, então vem rotulado como estimativa.
- AC-6: Dado uma transcrição ausente, vazia ou malformada, quando o custo é calculado, então o resultado é desconhecido com o motivo, sem derrubar o fim do run.
- AC-7: Dado o texto do agente afirmando um custo, quando o evento é gerado, então esse relato é ignorado; só tokens da transcrição contam.
- AC-8: Dado uma transcrição com mais de 64 MiB ou com uma linha de mais de 1 MiB, quando o custo é calculado, então a leitura é em streaming (linha a linha, sem carregar o arquivo inteiro), a linha acima do limite é ignorada e o arquivo acima do limite total vira `UNKNOWN` com `reason`, sem estourar a memória do daemon e sem derrubar o fim do run.

**Fora de escopo:** exibição do custo na UI/CLI, orçamento ou limites de gasto, busca automática de preços na internet, runtimes além de claude e codex, custo agregado por projeto.

**Aderência ao produto:** serve ao princípio "Custo medido das transcrições de cada run, não do que o agente diz" e à restrição "custo é estimativa a partir de uma tabela de preços, sempre rotulada como estimativa", e a "toda ação é um evento com ator". Não contradiz posicionamento nem o fora de escopo.

**Toca UI:** não

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos**
- `core`: `EventType.RUN_COST_RECORDED` no catálogo (`EventJson` já resolve por nome). Payload (strings): `run_id`, `stage`, `runtime` (`claude`|`codex`), `status` (`KNOWN`|`PARTIAL`|`UNKNOWN`), `cost_usd` (decimal, em `KNOWN` e `PARTIAL`), `estimate=true`, `tokens` (`modelo:input/output/cache_read/cache_write_5m/cache_write_1h;...`), `unpriced_models` (lista, se houver), `reason` (só em `UNKNOWN`: transcrição ausente/vazia/malformada ou nenhum modelo com preço). Ator: `Actor.Factory("cost")`.
- `daemon`, `PriceTable`: `PriceTable.parse(yaml: String): PriceTable` (snakeyaml-engine, já no módulo) e `priceOf(model): ModelPrice?`; preços em `BigDecimal` por milhão de tokens, kinds `input`, `output`, `cache_read`, `cache_write_5m`, `cache_write_1h` (ausente = 0, como no `prices.yml` da V0).
- `daemon`, `TranscriptParsers`: `parse(runtime, transcript: Path, fallbackModel: String): Usage` com `Usage = Map<model, TokenCounts>`. Claude: linhas `assistant` com `message.usage`, deduplicadas por `message.id`, cache write por `cache_creation.ephemeral_*`. Codex (`codex exec --json`): soma de `turn.completed.usage`, `cached_input_tokens` sai de `input`; o modelo não vem no stream, usa `fallbackModel`. Ignora qualquer campo de custo relatado pelo CLI ou agente (AC-7).
- `daemon`, `RunCostCalculator.cost(usage, prices): RunCost` sealed: `Known(usd)`, `Partial(usd, unpricedModels)`, `Unknown(reason)`.
- `daemon`, `RunCostRecorder.record(cardId, runId, runtime, stage, model, result: SessionResult)`: parseia, calcula, grava e publica o evento pelo `EventRecorder` do #134 (o único ponto de escrita de eventos do daemon; nunca `store.recordChange` e `events.publish` diretos). Transcrição ausente/vazia/malformada vira `UNKNOWN` com `reason`; nunca lança. Log `key=value` só com ids.

**Camadas e módulos:** `core` (1 enum) e `daemon` (arquivos novos para tabela de preços, parsers, calculadora e recorder, mais fixtures em `daemon/src/test/resources/transcripts/` copiadas da V0). Sem mudança em `store`, `ports` nem `adapters`.

**Integração:** o daemon ainda não tem dispatcher que encerre runs; o `RunCostRecorder` é o ponto de chamada do fim do run, pronto para o dispatcher (ligá-lo fica fora do escopo). O `prices.yml` entra por parâmetro do chamador, sem ler HOME dentro do recorder.

**Migração:** nenhuma (evento novo; `payload` é mapa livre).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunCostTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunCostTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunCostTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunCostTest.kt` |
| AC-4 | core | `core/src/test/kotlin/sfv2/core/EventTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunCostTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunCostTest.kt` |
| AC-7 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunCostTest.kt` |
| AC-8 | daemon | `daemon/src/test/kotlin/sfv2/daemon/RunCostTest.kt` |

## Security
_Revisão do dono interino_
- **A transcrição é texto não confiável** (traz conversa do agente, issue, código): só os números de uso e o nome do modelo são lidos; nenhum texto dela entra em evento, log ou resumo (AC-7). Leitura em streaming com limite de linha e de tamanho total (AC-8).
- **Fixtures mínimas:** as transcrições de teste são copiadas da V0 já reduzidas às linhas de uso (`usage`, `model`, `id`), com todo o texto de conversa, caminhos e conteúdo de arquivo removido; nada de segredo, issue ou código de outros projetos no repositório.
- **Dinheiro em `BigDecimal`**, nunca `Double` (CLAUDE.md), e o valor sempre rotulado como estimativa (AC-5).
- **O evento de custo usa o `EventRecorder` do #134**, então este card depende dele.

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Evento novo `RUN_COST_RECORDED` em vez de reaproveitar um existente: o catálogo é fechado e custo não é gate nem movimento.
- Arquivo de teste novo `RunCostTest.kt`: nenhuma suíte do daemon cobre custo; fixtures em `daemon/src/test/resources/transcripts/`.
- Parser do codex lê `codex exec --json` (o que o `CodexRuntime` grava), não o rollout da V0; o modelo vem do `SessionRequest`. A fixture codex é capturada de uma execução real desse formato.
- Custo parcial (AC-3) grava `status=PARTIAL` com o subtotal e `unpriced_models`; nunca `KNOWN`.
- Peso `heavy`: duas camadas (core e daemon), apesar do `standard` esperado na issue.

## Open questions
- none
