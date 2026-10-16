# Tracker GitHub Issues via gh — spec

**Issue:** #53 · **Branch:** `feat/53-tracker-github-issues-via-gh` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problema.** A fábrica só tem o fake do `Tracker`. Sem um tracker real, não há como importar uma issue do GitHub para o card local. Este é o primeiro adaptador real (`GhTracker`, via `gh`), só de leitura/importação.

**Audiência.** O desenvolvedor sozinho com muitos projetos, que importa issues do GitHub sem configurar board.

**Critérios de aceitação**
- AC-1: Dado o id de uma issue existente, quando o tracker a busca, então volta título, corpo e labels, com o hash do texto (título e corpo).
- AC-2: Dado o mesmo id buscado duas vezes sem mudança na issue, então o hash do texto é igual; se título ou corpo mudam, o hash muda.
- AC-3: Dado uma busca, quando há issues que casam, então volta a lista delas (id, título, labels); sem resultados, volta lista vazia.
- AC-4: Dado qualquer texto importado (título, corpo, labels), então ele sai do adaptador já marcado como não confiável, sem caminho que o devolva como texto confiável.
- AC-5: Dado um id inexistente ou um `gh` ausente, sem autenticação ou com falha, então o erro diz o que aconteceu e o id envolvido, sem logar título nem corpo.
- AC-7: Dado uma busca cujo texto traz um qualificador de escopo do GitHub (`repo:`, `org:`, `user:`, em qualquer caixa), quando o tracker a recebe, então a recusa com `TrackerException` sem chamar o `gh` (qualificadores `repo:` somam repositórios por OU e importariam issues de repositórios que o dono não escolheu).
- AC-8: Dado uma busca que devolve exatamente o limite de 100 issues, quando o tracker a trata, então falha com `TrackerException` "busca truncada" em vez de devolver só uma parte (o `importar` do #54 trataria a lista parcial como completa).
- AC-9: Dado uma resposta do `gh` acima de 8 MiB (issue com corpo gigante), quando o tracker a lê, então falha com `TrackerException` citando a operação e o id, sem carregar a resposta inteira em memória.
- AC-6: Dado o contrato da porta `Tracker`, quando o card pede `writeMilestone` ou `close`, então o adaptador comenta na issue / fecha a issue com o comentário (`AlreadyClosed` se já estava fechada) e id inexistente vira `UnknownTrackerItemException`.

**Fora do escopo**
- Jira (fase 9).
- Decidir quando escrever no GitHub (política dos marcos, #55, e confirmação do dono antes do fechamento, #56) e sincronização contínua; o adaptador só executa o `writeMilestone` e o `close` que a porta exige (AC-6).
- Relação Blocked by (é da #52, que bloqueia esta).
- Autenticação própria: usa a sessão do `gh` da máquina.

**Aderência ao produto.** Serve "importar e fechar, sem sincronização contínua" e "texto externo entra por uma porta só, marcado como não confiável". Não contradiz o posicionamento nem o que está fora de escopo.

**Toca UI:** não.

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- Contrato: sem mudança na porta `sfv2.ports.Tracker` (`fetch`, `search`, `writeMilestone`, `close`, `TrackerItem.of`, `ExternalText`). Nova classe pública `sfv2.adapters.GhTracker(repo, gh, workDir, tmpDir, timeout, runner)`, mesmo formato do `GhForge`.
- Camada: só `adapters`. Sem migração, sem mudança de armazenamento.
- `fetch(id)`: `gh issue view <n> --repo <repo> --json number,title,body,labels,url,state`; issue inexistente (marcador "Could not resolve") → `null`. Monta `TrackerItem.of` com `ExternalText` para título, corpo e cada label; o hash vem de `TrackerItem`.
- `search(query)`: `gh issue list --repo <repo> --state all --search <query> --limit 100 --json ...`; mesma conversão por item.
- `writeMilestone`: `gh issue comment <n> --body-file <tmp>`; `close`: `gh issue close <n> --comment`, com checagem prévia do estado para devolver `AlreadyClosed`. Existem só porque a porta os exige.
- Entrada validada antes de chamar `gh`: id numérico positivo, repo `owner/name`, query sem controle, sem prefixo `-` e sem qualificador `repo:`/`org:`/`user:` (AC-7); a saída do `gh` é lida com teto de 8 MiB (AC-9); `search` que devolve o limite de 100 falha como truncada (AC-8); falha vira `TrackerException` com operação e id, nunca a saída do `gh` nem o texto da issue.
- Arquivos: `adapters/src/main/kotlin/sfv2/adapters/GhTracker.kt` (novo), `adapters/src/test/kotlin/sfv2/adapters/GhTrackerTest.kt` (novo; reusa o `GhHarness` de `GhForgeTest.kt`). Dono de ambos: `adapters`.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | adapters | `adapters/src/test/kotlin/sfv2/adapters/GhTrackerTest.kt` |
| AC-2 | adapters | `adapters/src/test/kotlin/sfv2/adapters/GhTrackerTest.kt` |
| AC-3 | adapters | `adapters/src/test/kotlin/sfv2/adapters/GhTrackerTest.kt` |
| AC-4 | adapters | `adapters/src/test/kotlin/sfv2/adapters/GhTrackerTest.kt` |
| AC-5 | adapters | `adapters/src/test/kotlin/sfv2/adapters/GhTrackerTest.kt` |
| AC-6 | adapters | `adapters/src/test/kotlin/sfv2/adapters/GhTrackerTest.kt` |
| AC-7 | adapters | `adapters/src/test/kotlin/sfv2/adapters/GhTrackerTest.kt` |
| AC-8 | adapters | `adapters/src/test/kotlin/sfv2/adapters/GhTrackerTest.kt` |
| AC-9 | adapters | `adapters/src/test/kotlin/sfv2/adapters/GhTrackerTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- `writeMilestone` e `close` entram no escopo (AC-6): a porta `Tracker` os exige e a classe não compila sem eles; fora isso, escrita no GitHub segue fora do escopo.
- Arquivo de teste novo `GhTrackerTest.kt`: nenhuma suíte cobre o `Tracker` em `adapters`; `GhForgeTest.kt` cobre outra classe, e o `GhHarness` dele é reaproveitado.
- Peso `standard`: uma camada, 6 ACs, 2 arquivos, sem UI nem segurança.

## Open questions
- none
