# Gates de escopo e caminhos proibidos — spec

**Issue:** #30 · **Branch:** `feat/30-gates-de-escopo-e-caminhos-proibidos` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problema:** o agente que implementa não pode alterar o juiz (as regras da fábrica, os workflows, a config do agente) nem escapar das camadas do estágio. Sem um gate determinístico sobre o diff, um run poderia afrouxar o próprio critério de aprovação.

**Audiência:** o desenvolvedor sozinho com muitos projetos, que deixa a fábrica trabalhar sem supervisão; e o desenvolvedor em empresa, que precisa garantir que a trilha de auditoria e as convenções não são editáveis pelo agente.

**Critérios de aceitação**
- AC-1: Dado um diff que toca `.shipfloor/`, `.github/` ou `.claude/`, quando o gate de diff roda, então ele falha, mesmo que o projeto não os liste em `forbidden`.
- AC-2: Dado um diff que toca um caminho da lista `forbidden` do projeto, quando o gate roda, então ele falha nomeando os arquivos violadores.
- AC-3: Dado um diff com arquivos fora das camadas do estágio, quando o gate roda, então ele falha nomeando esses arquivos.
- AC-4: Dado um diff só com arquivos dentro das camadas do estágio e fora dos caminhos proibidos, quando o gate roda, então ele passa.
- AC-5: Dado que o worktree altera a config do projeto (`forbidden` ou camadas), quando o gate roda, então ele usa a config da main e a alteração não tem efeito.
- AC-6: Dado um arquivo renomeado ou movido de/para um caminho proibido ou fora das camadas, quando o gate roda, então ele falha (os dois caminhos contam).
- AC-7: Dado um diff que toca só `gradle/libs.versions.toml` ou arquivos sob `docs/superpowers/` (specs e planos) e arquivos das camadas do estágio, quando o gate roda, então ele passa: esses caminhos compartilhados são permitidos em qualquer estágio, além das camadas; `CLAUDE.md` na raiz conta como protegido (falha).

**Fora de escopo:** edição dos caminhos proibidos pelo dono pela UI; gates de qualidade (build, testes, lint); revisão semântica do conteúdo do diff; o que fazer com o card após a falha (roteamento pertence ao pipeline).

**Aderência ao produto:** serve o princípio "gates são scripts, não a palavra do agente". Não contradiz o posicionamento nem o que está fora de escopo.

**Toca UI:** não.

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos**
- `daemon`: `Project` ganha `forbidden: List<String>` (prefixos de caminho lidos da chave `forbidden` do `factory.yml` da main; ausente = lista vazia; item que não é texto, vazio, absoluto ou com `..` → `InvalidProjectConfigException` nomeando `forbidden`). `ProjectRegistry` continua lendo só via `ProjectConfigSource` (`git show main:`), nunca da árvore de trabalho.
- `daemon`: `ScopeGate.check(project: Project, changed: List<String>, stageLayers: Set<String>): ScopeVerdict`. `ScopeVerdict` é sealed: `Pass` | `Violations(forbidden: List<String>, outsideLayers: List<String>)`; as listas trazem os caminhos, ordenados. Os caminhos protegidos fixos `.shipfloor/`, `.github/`, `.claude/` e o arquivo `CLAUDE.md` da raiz valem sempre, somados ao `forbidden` do projeto. Os caminhos compartilhados `gradle/libs.versions.toml` (dependência nova, CLAUDE.md) e `docs/superpowers/` (spec e plano do card) são permitidos em qualquer estágio, somados às camadas (AC-7): sem eles o gate recusaria o commit de spec/plano e toda dependência nova; a V0 resolve isso com `scope` por papel no `factory.yml`. Um caminho é violador se cai em caminho protegido/proibido; senão, se não está sob o `path` de nenhuma camada em `stageLayers`. Função pura: sem IO e sem relógio.
- `daemon`: interface `ChangedPaths.between(worktree: Path, base: String): List<String>` e `GitChangedPaths` (`git diff --name-only --no-renames -z <base>`, sem shell, com timeout, saída lida de arquivo; `--no-renames` lista origem e destino, o que cobre AC-6). `base` que começa com `-` é recusado.
- O dispatcher (#27) compõe `ChangedPaths` + `ScopeGate` e registra o veredito via `GateExecutor`; isso fica fora desta issue.

**Camadas e arquivos**
- `daemon`: `ScopeGate.kt`, `GitChangedPaths.kt` (novos) e `ProjectRegistry.kt` (campo `forbidden`), em `daemon/src/main/kotlin/sfv2/daemon/`.
- Testes: `daemon/src/test/kotlin/sfv2/daemon/ScopeGateTest.kt` (novo), `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt`.

**Fluxo:** o chamador registra o projeto (config da main), obtém os caminhos alterados do worktree contra o `base` e chama `ScopeGate.check` com as camadas do estágio. Casamento por prefixo de segmento (`gradle/wrapper/` não casa `gradle/wrapperx`), após normalizar `./` e `//`; caminho com `..` no diff é violação.

**Migração:** nenhuma (expand-only; chave `forbidden` opcional no `factory.yml`).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ScopeGateTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ScopeGateTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ScopeGateTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ScopeGateTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ScopeGateTest.kt` |
| AC-7 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ScopeGateTest.kt` |

## Security
_Revisão do dono interino_
- Este gate é o juiz de "o agente tocou onde não devia": a lista protegida e a lista compartilhada ficam no código (não vêm do worktree), a lista `forbidden` vem só da `main`, e caminho com `..`, absoluto ou com caractere de controle no diff é violação.
- A saída do `git diff` é lida de arquivo com limite de tamanho; um número grande de arquivos alterados falha o gate, não derruba o daemon.

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `standard`: uma camada (`daemon`), 6 critérios, 3 arquivos de código; sem segurança nem migração destrutiva.
- Caminhos protegidos fixos no código: `forbidden` vem de um arquivo que a própria fábrica protege, e o juiz não pode depender de a lista ser mantida.
- `forbidden` entra em `Project` e é lido pelo registro existente (main-only); evita um segundo leitor de config que poderia ler o worktree.
- `--no-renames` em vez de `-M`: lista os dois caminhos de uma renomeação, sem lógica extra.
- Quais camadas cada estágio pode tocar é entrada do chamador (`stageLayers`); o mapeamento estágio→camadas pertence ao dispatcher (#27).
- `ScopeGateTest.kt` é arquivo novo porque nenhuma suíte do daemon cobre escopo de diff; AC-5 reaproveita `ProjectRegistryTest.kt`.
- AC-5 testado no registro com repositório git real: main com uma config e árvore de trabalho editada com outra; vale a da main.

## Open questions
- none
