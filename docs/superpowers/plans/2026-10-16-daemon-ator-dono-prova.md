# Plano: prova do dono no daemon (#115)

Spec: `docs/superpowers/specs/2026-10-16-daemon-ator-dono-prova-design.md`. Camada única: `daemon`.

## Tarefas

1. **OwnerProof** (layer daemon, agente `engineer`)
   - Novo `daemon/src/main/kotlin/sfv2/daemon/OwnerProof.kt`: `create(path, random)`, token de 32 bytes em hex, arquivo 0600 criado já com permissão (`Files.createFile` + `PosixFilePermissions`), diretório 0700, recria sem seguir symlink, falha na partida se não puder definir permissão; `matches(String?)` com `MessageDigest.isEqual`; sem valor em `toString`/`equals`/`hashCode`.
2. **Protocolo e comandos** (layer daemon, depende de 1)
   - `Protocol.kt`: `proof: String? = null` em `CardCreate` e `CardMove`, `toString` mascarado, leitura do campo `proof`.
   - `CardCommands.kt`: recebe `ownerProof`; ator dono sem prova válida → `Response.Error("owner proof missing or invalid")` antes de qualquer leitura do store, log só com cmd e id.
   - `Main.kt`: cria `OwnerProof` ao lado do socket e passa a `CardCommands`.
   - Ajustar testes existentes (`SocketServerTest.kt`, `ProtocolTest.kt`) que usam ator dono para enviar `proof`, sem editar os testes de aceitação da QA.

## Dependências
2 depende de 1. Testes de aceitação (QA) em `daemon/src/test/kotlin/sfv2/daemon/SocketServerTest.kt`.
