# Tela de fechamento — spec

**Issue:** #71 · **Branch:** `feat/71-tela-de-fechamento` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** standard

## Product
_Product Owner_

**Problem:** o daemon já sabe rascunhar e confirmar o fechamento da issue no tracker (`card.close.draft`, `card.close.confirm`, #56), mas o dono só alcança isso pelo socket. Sem tela, o último passo do card (a voz da fábrica na ferramenta oficial) não tem como ser lido, confirmado nem repetido depois de um erro.
**Audience:** o dono, que lê o que vai sair da máquina e assina com a confirmação.
**Acceptance criteria**
- **AC-1** — Given um card em Done aberto na visão do card, when o dono abre a tela de fechamento, then o app pede o rascunho ao daemon e mostra status, resumo e link do PR, sem enviar nenhuma confirmação.
- **AC-2** — Given o daemon recusar o rascunho (card fora de Done, sem referência externa, `no_pr_recorded`), when a tela mostra a resposta, then o motivo do daemon aparece e não há botão de confirmar.
- **AC-3** — Given um rascunho exibido, when o dono confirma, then o app envia `card.close.confirm` com ator dono, a prova lida do arquivo do token no momento do envio e o `draftHash` exibido, e mostra "fechado no tracker" quando o daemon aceita.
- **AC-4** — Given a escrita no tracker falhar (resposta de erro do daemon), when a tela mostra o resultado, then o erro aparece, o card segue em Done e há um botão "tentar de novo" que reenvia o mesmo `card.close.confirm` com o mesmo `draftHash`; se o daemon responder que o rascunho mudou, o app busca um rascunho novo e o mostra para nova confirmação.
- **AC-5** — Given o arquivo do token ausente ou o daemon fora do ar, when o dono confirma, then nada é enviado (ou nada chega ao daemon), a tela diz o que houve e como resolver, e o rascunho continua visível para nova tentativa.
- **AC-6** — Given um card cuja linha do tempo termina em `CLOSURE_WRITTEN`, then a tela mostra "fechado no tracker" e nenhum botão de confirmar; given que o último evento de fechamento é `CLOSURE_FAILED`, then a tela mostra o erro registrado e oferece "tentar de novo".
- **AC-7** — Given uma confirmação em andamento, when o dono aciona o botão de novo, then um segundo `card.close.confirm` não é enviado.
**Out of scope:** editar o texto do comentário (o rascunho é só leitura, o daemon monta o texto); fechamento de card fora de Done; marcos opcionais; mudanças no daemon, no protocolo do socket ou nos eventos; fechar por CLI.
**Product fit:** serve "a aprovação do dono é um evento assinado" e "nada sai da máquina sem evento assinado pelo dono": a tela só pede o rascunho, mostra e leva a confirmação do dono; a escrita no tracker segue sendo do daemon. Não contradiz posicionamento nem escopo.
**Touches UI:** yes

## UX
_UX/UI Designer_

- A tela abre a partir da visão do card (`CardDetailScreen`) quando a coluna é Done; fora de Done a ação não aparece.
- Conteúdo, nesta ordem: status, resumo, link do PR (texto selecionável, sem abrir navegador), aviso fixo "isto é o que será postado no tracker; o título do card não é enviado". Ação principal "Confirmar fechamento"; "Voltar" como ação secundária.
- Estados: carregando rascunho; rascunho; recusado (motivo do daemon); confirmando (botão desabilitado); fechado; erro (mensagem + "Tentar de novo"). Cores e componentes só os de `DESIGN.md` (`Theme.kt`); estado de erro e de sucesso usam as cores de estágio já definidas, nunca só cor: sempre com texto.
- Textos em português, centralizados em `Texts`; erro do daemon mostrado como veio, precedido de uma frase que diz o que aconteceu e o que fazer.
- Teclado: `Enter` confirma só com o botão em foco; `Esc` volta. Sem diálogo modal extra: a própria tela é a confirmação.
- Captura em desktop e mobile (janela estreita) na verificação visual do engenheiro.

## Architecture
_Tech Lead_

- Só `app`; nenhuma mudança em `core`, `daemon` ou protocolo. O app fala com o daemon pelo socket, como `SocketOwnerCommands`; nunca lê o banco.
- `app/src/main/kotlin/sfv2/app/OwnerCommands.kt` (editado): nova interface `ClosureCommands { fun draft(cardId): DraftOutcome; fun confirm(cardId, draftHash): ConfirmOutcome }` e `SocketClosureCommands(socket, token)`, mesmo formato de `SocketOwnerCommands`: lê o token a cada envio, nunca o guarda nem o loga; `closureDraftRequest(cardId)` e `closureConfirmRequest(cardId, draftHash, proof)` montam as linhas `card.close.draft` e `card.close.confirm` (ator `owner`). Resultados em sealed types (`Drafted`, `Refused(reason)`, `NotSent(cause)`), sem booleano que escolhe comportamento.
- `app/src/main/kotlin/sfv2/app/ClosureModel.kt` (novo): `ClosureController(commands, io)` com `StateFlow<ClosureState>` (sealed: `Loading`, `Draft`, `Refused`, `Confirming`, `Closed`, `Failed`). `confirm()` é no-op enquanto `Confirming` (AC-7). A semente do estado vem da linha do tempo de `CardDetail.events` (último `CLOSURE_*` decide `Closed`/`Failed`, AC-6); `eventsTruncated` sem evento de fechamento visível cai em pedir o rascunho, e o daemon é idempotente (já escrito devolve sucesso sem chamar o tracker).
- `app/src/main/kotlin/sfv2/app/ClosureScreen.kt` (novo, composable fino) e `CardDetailScreen.kt` (editado, ponto de entrada) mais `Main.kt` (editado, injeta `SocketClosureCommands` como já faz com `OwnerMoveController`); `Theme.kt` (editado) recebe os textos novos em `Texts`.
- Dados e segurança: logar só ids (`card=<id>`), nunca o comentário nem o título; o título do card só aparece na visão do card, não na tela de fechamento. A prova é lida do arquivo do token a cada confirmação e não vai a estado, log nem `toString`.
- Resiliência: o socket usa o timeout já adotado pelos clientes do app; erro de IO vira `NotSent(DAEMON_UNAVAILABLE)`, sem estado meio gravado (o daemon decide o que foi gravado).
- Dono dos arquivos: `app`.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | app | `app/src/test/kotlin/sfv2/app/CardDetailModelTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/CardDetailModelTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/OwnerMovesTest.kt` |
| AC-4 | app | `app/src/test/kotlin/sfv2/app/CardDetailModelTest.kt` |
| AC-5 | app | `app/src/test/kotlin/sfv2/app/OwnerMovesTest.kt` |
| AC-6 | app | `app/src/test/kotlin/sfv2/app/CardDetailModelTest.kt` |
| AC-7 | app | `app/src/test/kotlin/sfv2/app/CardDetailModelTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `standard`: uma só camada (`app`), mas mais de 3 caminhos em `## Architecture` e `Touches UI` é `yes`.
- Sem arquivo de teste novo: a suíte do `app` já cobre o módulo; o contrato do socket (requisição, prova, token ausente) vai em `OwnerMovesTest.kt`, onde já estão `ownerMoveRequest` e `SocketOwnerCommands`, e o controlador e a derivação pelo histórico vão em `CardDetailModelTest.kt`, junto do modelo do card.
- Sem mudança no daemon: `card.close.draft` e `card.close.confirm` já cobrem recusa, prova, repetição sem duplicar e erro (#56); a tela é só cliente.
- O estado inicial (fechado/falhou) vem dos eventos do `card.detail` e não de um comando novo, para não tocar o protocolo.
- Nova tentativa reenvia a confirmação com o mesmo `draftHash`: o daemon reaproveita o `CLOSURE_CONFIRMED` e não duplica o comentário (AC-6 do #56); rascunho mudado exige nova leitura do dono.
- O título do card não aparece na tela de fechamento: o texto que sai para o tracker não o contém e a tela mostra exatamente isso.
- Segurança sem conteúdo próprio (`n/a`): a tela não cria efeito novo fora da máquina; a prova do dono segue as regras já aplicadas em `SocketOwnerCommands`.

## Open questions
- none
