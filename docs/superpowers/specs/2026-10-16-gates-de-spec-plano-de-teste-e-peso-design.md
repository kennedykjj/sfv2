# Gates de spec: plano de teste e peso — spec

**Issue:** #32 · **Branch:** `feat/32-gates-de-spec-plano-de-teste-e-peso` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** light

## Product
_Product Owner_

**Problem:** hoje nada garante que a spec chegue ao dono executável: um critério pode ficar sem teste planejado e o peso pode ser declarado menor do que o trabalho real, o que faz a fábrica subdimensionar a revisão. O dono perderia tempo aprovando spec que a fábrica não consegue executar.
**Audience:** o desenvolvedor sozinho com muitos projetos, que aprova a spec em Spec Review e precisa confiar que o que chega até ele já passou por gate, não por palavra de agente.
**Acceptance criteria**
- **AC-1** — Dado uma spec com critérios `AC-n`, quando o gate de spec roda, então cada critério tem uma linha no plano de teste com camada existente no projeto e caminho de teste; critério sem linha, com camada inexistente ou sem caminho vira um achado que nomeia o AC.
- **AC-2** — Dado uma spec, quando o gate calcula o peso, então o resultado é `light`, `standard` ou `heavy`, e é `heavy` quando os arquivos citados em Architecture tocam duas camadas ou mais, a seção Security está preenchida ou há migração destrutiva; a contagem de ACs não entra no cálculo.
- **AC-3** — Dado um peso declarado ausente, fora de `light|standard|heavy` ou abaixo do calculado, quando o gate roda, então a spec é recusada com um achado que informa o peso declarado e o calculado.
**Out of scope:** escrever ou corrigir a spec (o gate só devolve achados); rodar os testes do plano ou checar que o arquivo de teste existe no disco; mover o card ou notificar o dono (é do pipeline que consome o gate); recusar peso declarado acima do calculado.
**Product fit:** serve ao princípio "gates são scripts, não a palavra do agente" e ao propósito de o dono aparecer só onde a decisão é dele. Não contradiz o posicionamento nem o que está fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Camada única: `daemon`. Função pura (sem IO, sem relógio): recebe o texto da spec e as camadas do projeto.

**Contratos** (em `daemon/src/main/kotlin/sfv2/daemon/SpecGates.kt`, novo)
- `SpecLayer(name: String, path: String, tests: List<String>)`: camada do projeto; `path` é o prefixo (`daemon/`) e `tests` são globs repo-relativos (`daemon/src/test/**`). Quem consome monta a lista a partir do `factory.yml`.
- `SpecWeight` enum: `LIGHT`, `STANDARD`, `HEAVY`.
- `SpecFinding(gate: SpecGate, ac: String?, message: String)`; `SpecGate` enum: `TEST_PLAN`, `WEIGHT`.
- `SpecCheck(weight: SpecWeight, findings: List<SpecFinding>)`; `SpecGates.check(specText: String, layers: List<SpecLayer>): SpecCheck`. Sem achados = spec aprovada pelo gate.

**Regras**
- Plano de teste: os ACs são os `**AC-n**` da seção Product; cada um precisa de uma linha na tabela de `## Test plan` com camada em `layers` (ou `manual`) e, para camada real, caminho entre crases que case com um glob de `tests` da camada. Linha de `manual` não exige caminho.
- Peso calculado: `HEAVY` com 2 ou mais camadas nos caminhos entre crases de `## Architecture` (camada pelo prefixo `path`), ou `## Security` preenchida (diferente de `n/a`), ou `## Rollback` diferente de `n/a`, ou mais de 8 caminhos; `LIGHT` com 1 camada, no máximo 3 caminhos, `Touches UI: no` e sem os sinais acima; `STANDARD` no resto. Contagem de ACs não entra (G8 de `gargalos-da-v0.md`).
- Peso declarado: lido da linha `**Weight:**` do cabeçalho; ausente, inválido ou de ordem menor que o calculado gera achado `WEIGHT` com os dois valores.

**Arquivos:** `daemon/src/main/kotlin/sfv2/daemon/SpecGates.kt` (novo); teste `daemon/src/test/kotlin/sfv2/daemon/SpecGatesTest.kt` (novo).

**Migração:** nenhuma.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SpecGatesTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SpecGatesTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SpecGatesTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `light`: uma camada, 2 arquivos, sem UI nem segurança; bate com a nota da issue.
- Função pura que recebe `SpecLayer`: o gate não lê `factory.yml` nem disco; quem chamar (dispatcher, #27) injeta as camadas, e o teste não precisa de fixture de arquivo.
- Peso por sinais objetivos (camadas, caminhos, segurança, migração destrutiva), não por contagem de ACs: G8 mostra que o PO infla critérios.
- Peso declarado acima do calculado é aceito: o dono pode subir o peso em Spec Review.
- Arquivo de teste novo `SpecGatesTest.kt`: nenhuma suíte do daemon cobre a spec.
- Conferir referências a decisões contra `decisions.md` (G7) fica fora deste card: não está nos ACs da issue.

## Open questions
- none
