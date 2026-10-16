# Daemon: ator dono não pode ser declarado pelo cliente — spec

**Issue:** #115 · **Branch:** `feat/115-daemon-actor-owner-proof` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, Cybersec
**Weight:** heavy

## Product
_Product Owner_

**Problema:** no socket, o cliente declara o ator (`"actor":"owner"`). Um agente rodando como o mesmo usuário pode se declarar dono e aprovar spec ou entrega, ferindo "a aprovação do dono é um evento assinado".

**Audiência:** o desenvolvedor dono da fábrica (sozinho ou numa empresa), que precisa confiar que só ele aprova Spec Review e Ready for Prod.

**Direção:** a prova do dono é um token gerado pelo daemon. Todo pedido que declara ator dono (`card.create` e `card.move`) exige o campo `proof` válido; senão é recusado sem gravar nada.

**Critérios de aceitação**
- AC-1: Dado um pedido `card.create` ou `card.move` que declara ator dono sem `proof` ou com `proof` inválido, quando o daemon o processa (inclusive movimentos só do dono: Spec Review → Ready for Dev, Staged → Ready for Prod, Inbox → Ready for Refinement), então recusa com motivo e nenhum card nem evento é gravado.
- AC-2: Dado um pedido que declara ator dono com `proof` válido, quando o daemon o processa, então o pedido é aceito e o evento registra o ator dono.
- AC-3: Dado um cliente que não é o dono (agente, fábrica), quando ele tenta obter a prova do dono, então o daemon não a entrega por nenhum canal que ele acesse (socket, respostas, logs ou MCP); a prova só é legível pelo dono.

**Fora do escopo:** assinatura criptográfica do evento para auditoria externa; comando da CLI/app que lê o token; servidor MCP.

**Aderência ao produto:** serve ao princípio "toda ação é um evento com ator; a aprovação do dono é um evento assinado" e a "nada sai da máquina sem evento assinado pelo dono". Não contradiz o posicionamento nem o que o produto não é.

**Toca UI:** não.

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

Camada única: `daemon`. Sem mudança em `core`, `store` nem migração.

- **Contrato do protocolo**: `card.create` e `card.move` ganham o campo opcional `proof` (string). `Request.CardCreate` e `Request.CardMove` ganham `proof: String? = null`. Nenhum comando novo devolve o token; a resposta nunca o contém.
- **`OwnerProof`** (novo, `daemon/src/main/kotlin/sfv2/daemon/OwnerProof.kt`): `OwnerProof.create(path: Path, random: SecureRandom)` gera 32 bytes aleatórios em hex, grava em `path` (arquivo `owner.token`, permissão 0600, ao lado do socket, no diretório 0700) e expõe `matches(candidate: String?): Boolean` (comparação em tempo constante com `MessageDigest.isEqual`). Sem `toString` com o valor. Token novo a cada partida do daemon.
- **`CardCommands`** recebe `ownerProof: OwnerProof`. Antes de create/move, se `actor == Actor.Owner` e `!ownerProof.matches(proof)`, responde `Response.Error("owner proof missing or invalid")`, loga `card_command_refused` só com cmd e id do card, e não grava nada. A verificação vem antes da busca do card (não revela se o id existe). Vale para qualquer pedido com ator dono, não só movimentos só do dono: o evento nunca registra dono sem prova.
- **Fiação**: `launchDaemon` (`Main.kt`) cria o `OwnerProof` no diretório do socket e o passa a `CardCommands`; `Protocol.kt` lê `proof`.
- **Arquivos**: `OwnerProof.kt` (novo), `Protocol.kt`, `CardCommands.kt`, `Main.kt`, `SocketServerTest.kt` (testes novos; os de dono existentes passam a enviar `proof`), `ProtocolTest.kt`.
- **Restrição para #28/#29**: o servidor MCP não expõe ferramenta que leia ou devolva o token, nem repassa `proof` vindo de agente com ator dono; o MCP fala sempre com ator `agent:<papel>`.
- **Fora daqui**: leitura do arquivo pela CLI/app (cards próprios).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt` |

## Security
_Cybersec_

**Ativo e ameaça:** o token do dono (`owner.token`) aprova Spec Review e Ready for Prod. A ameaça é um agente do mesmo usuário forjar o ator dono.
**Risco residual:** 0600 e diretório 0700 só barram outros usuários; um agente do mesmo UID que leia o arquivo de propósito obtém a prova. Esta prova barra o forjamento pelo protocolo, não essa leitura. Mitigação futura (fora do card): agentes em outro UID ou sandbox sem acesso ao diretório do daemon; registrar em #28/#29.

**Controles exigidos**
1. Token de `SecureRandom`, 32 bytes em hex, novo a cada partida; sem valor fixo ou derivado.
2. Arquivo criado já com 0600 (`Files.createFile` com `PosixFilePermissions`, sem chmod depois), diretório 0700 antes da gravação; arquivo existente é apagado e recriado sem seguir symlink; se a permissão não puder ser definida, o daemon falha na partida.
3. Comparação com `MessageDigest.isEqual` sobre bytes; `proof` nulo ou vazio dá recusa sem exceção.
4. Sem vazamento: erro fixo igual para ausente e inválido, sem ecoar o `proof`; log só com cmd e id; `Request` com `proof` tem `toString` mascarado; `OwnerProof` não expõe o valor em `toString`, `equals` ou `hashCode`; o token não entra em evento persistido.
5. Prova validada antes de qualquer leitura do store, também em `card.create`.
6. Nenhum comando do protocolo devolve o token; o MCP nunca repassa `proof`.

**Testar:** `proof` ausente, vazio, errado e de outro tamanho em create e move (inclusive movimentos só do dono) → recusa sem gravar; válido → aceito com ator dono; `agent:*` com `proof` válido não vira dono; id existente e inexistente dão a mesma recusa; permissões 0600/0700; token diferente a cada partida; respostas, log e `toString` do `Request` não contêm o token.

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Prova obrigatória para qualquer pedido com ator dono (create inclusive): senão um agente forjaria eventos "dono" em movimentos livres, ferindo a invariante 3.
- Token em arquivo 0600 no diretório 0700 do socket, novo a cada partida: não exige estado persistente nem comando que o devolva (AC-3).
- Verificação antes de buscar o card: a recusa não vaza se o id existe.
- Peso heavy: a seção de segurança preenchida o exige pela regra de peso, apesar do peso esperado standard na issue.
- Testes no `SocketServerTest.kt` existente: é a suíte do módulo; sem arquivo novo.

## Open questions
- **Isolamento do agente:** A (recomendada) aceitar o risco residual agora e isolar agentes (outro UID ou sandbox) em card próprio antes de #28/#29, ou B bloquear #28/#29 até o isolamento existir — o AC-3 só vale de fato contra agente do mesmo UID com o isolamento.
