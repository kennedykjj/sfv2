# Aprovar e pedir mudança pelo app — spec

**Issue:** #67 · **Branch:** `feat/67-aprovar-e-pedir-mudanca-pelo-app` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** standard

## Product
_Product Owner_

**Problem:** O dono já move cards pela esteira (#65) e vê o card em detalhe (#66), mas ao ler a spec na visão do card não consegue decidir ali: precisa voltar à esteira ou abrir a CLI para aprovar ou pedir mudança. A fase 6 tem como saída "um dia de operação sem abrir a CLI para nenhuma ação do dono".
**Audience:** o dono, ao revisar uma spec em `Spec Review`.
**Acceptance criteria**
- **AC-1** — Given um card na coluna `SPEC_REVIEW`, when o dono abre a visão do card, then a tela mostra as ações "Aprovar" e "Pedir mudança"; em qualquer outra coluna, ou com o card carregando, não mostra nenhuma das duas.
- **AC-2** — Given a visão de um card em `SPEC_REVIEW`, when o dono aciona "Aprovar", then o app envia ao daemon o comando `card.approve` do card, como ator `owner` e com a prova do dono, e a tela passa a mostrar "Aprovação enviada" até o evento do card chegar.
- **AC-3** — Given a visão de um card em `SPEC_REVIEW`, when o dono aciona "Pedir mudança", then o app pede uma nota; com a nota vazia ou só de espaços nada é enviado e o campo continua pedindo a nota; com nota, o app envia `card.request-change` com a nota sem espaços nas pontas.
- **AC-4** — Given que o daemon recusou o comando ou não pôde ser alcançado (daemon indisponível, prova do dono ausente), when a resposta chega, then a tela mostra a mensagem que diz o que aconteceu (a razão do daemon, ou "não enviado" com a causa), as ações voltam a ficar disponíveis e a coluna mostrada não muda.
- **AC-5** — Given um comando já em envio, when o dono aciona de novo "Aprovar" ou "Pedir mudança", then nada novo é enviado: um clique gera um único comando.
- **AC-6** — Given que o daemon aceitou o comando, when o evento do card chega pelo feed e a visão é atualizada, then a coluna nova aparece, as ações somem (o card saiu de `SPEC_REVIEW`) e a linha do tempo mostra a aprovação ou o pedido de mudança assinado pelo dono.
- **AC-7** — Given a janela larga ou compacta, when a visão do card é aberta, then as ações e o campo de nota ficam visíveis sem rolagem horizontal, alcançáveis por teclado e legíveis (contraste do `DESIGN.md`), e o estado "enviado", "recusado" e "não enviado" não depende só de cor.
**Out of scope:** aprovar ou pedir mudança direto da esteira (já existe pelo movimento do dono, #65); editar a spec; aprovação em lote; desfazer uma aprovação; qualquer mudança no daemon ou nos comandos `card.approve` e `card.request-change`.
**Product fit:** fecha a decisão do dono dentro do app, no lugar onde ele lê a spec, e mantém o daemon como único juiz e o evento assinado como única prova da aprovação.
**Touches UI:** yes

## UX
_UX/UI Designer_

- As ações ficam numa faixa fixa no topo da visão do card, abaixo do cabeçalho, antes da spec: "Aprovar" (ação primária) e "Pedir mudança" (secundária). Só aparecem em `SPEC_REVIEW`.
- "Pedir mudança" abre, na própria faixa, um campo de nota com "Enviar" e "Cancelar"; foco vai para o campo ao abrir. Enter com a nota preenchida envia; Esc cancela.
- Durante o envio os dois botões ficam desabilitados e a faixa diz "Enviando…" (região viva polida).
- Recusa e "não enviado" aparecem como mensagem na faixa, com ícone ou rótulo textual além da cor, e um botão "Dispensar".
- Textos novos entram em `Texts` (pt-BR); cores e componentes só do `DESIGN.md`, sem componente novo fora dele.

## Architecture
_Tech Lead_

Caminhos tocados (camada `app`):
- `app/src/main/kotlin/sfv2/app/OwnerMoves.kt` — novo `SpecReviewController`: reaproveita `OwnerCommands.move` (já mapeia `SPEC_REVIEW → READY_FOR_DEV` para `card.approve` e `SPEC_REVIEW → READY_FOR_REFINEMENT` para `card.request-change`) e `needsNote`; estado imutável (`SpecReviewState`: `idle`, `notePending`, `sending`, `sent`, `notice`); sem atualização otimista, pois a coluna só muda quando o evento chega.
- `app/src/main/kotlin/sfv2/app/CardDetailScreen.kt` — faixa de ações para `SPEC_REVIEW`; recebe o controller por parâmetro opcional (nulo sem daemon) e `onApprove`/`onRequestChange` derivados dele.
- `app/src/main/kotlin/sfv2/app/Main.kt` — `OpenCard` cria o `SpecReviewController` com o `SocketOwnerCommands` já usado pela esteira e o passa à tela.
- `app/src/main/kotlin/sfv2/app/Theme.kt` — textos novos de `Texts`.

Regras de arquitetura: o app não escreve no banco nem decide regra; só envia o comando ao daemon com a prova lida do arquivo a cada envio (`SocketOwnerCommands`, sem guardar o token). A recusa do daemon é a verdade. O envio roda fora da thread de UI (`Dispatchers.IO`). Um flag `sending` no estado impede envio duplo. Nenhuma mudança de protocolo, porta ou migração.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | app | `app/src/test/kotlin/sfv2/app/OwnerMovesTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/OwnerMovesTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/OwnerMovesTest.kt` |
| AC-4 | app | `app/src/test/kotlin/sfv2/app/OwnerMovesTest.kt` |
| AC-5 | app | `app/src/test/kotlin/sfv2/app/OwnerMovesTest.kt` |
| AC-6 | app | `app/src/test/kotlin/sfv2/app/CardDetailModelTest.kt` |
| AC-7 | manual | captura da visão do card em janela larga e compacta, com foco por teclado |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Reaproveitar `OwnerCommands.move` em vez de novo comando de socket: o mapeamento para `card.approve` e `card.request-change` já existe e está testado (#65).
- Sem atualização otimista na visão do card: a coluna é mostrada pelo detalhe vindo do daemon; mostrar "Aprovação enviada" evita afirmar um estado que o banco ainda não tem.
- Testes na suíte existente (`OwnerMovesTest.kt`, `CardDetailModelTest.kt`): o módulo já tem fakes de `OwnerCommands` e do detalhe; arquivo novo repetiria fixtures.
- Peso `standard`: uma camada (`app`), 4 caminhos, mas toca UI.
- Aprovação continua sendo evento assinado pelo dono: o app só envia ator e prova; quem grava o evento é o daemon.
- AC-7 é `manual`: layout e acessibilidade da UI Compose não têm suíte automática no módulo.

## Open questions
- none
