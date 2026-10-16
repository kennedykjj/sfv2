# Store: banco acessível só ao dono (follow-up do #12) — spec

**Issue:** #106 · **Branch:** `feat/106-store-banco-acessivel-so-ao-dono` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problema:** o banco guarda o estado e a trilha de auditoria da fábrica, mas o arquivo não tem permissão definida; em máquina compartilhada outro usuário poderia lê-lo ou alterá-lo. O socket do daemon já é `0600` (#15).
**Audiência:** o desenvolvedor dentro de uma empresa (trilha de auditoria) e o desenvolvedor sozinho em máquina compartilhada.

**Critérios de aceitação**
- AC-1: Dado um caminho sem banco, quando o store cria o arquivo, então o banco e os arquivos `-wal` e `-shm` ficam com permissão `0600`.
- AC-2: Dado um banco existente com permissão mais aberta que `0600`, quando o store abre, então falha com mensagem que cita o caminho e a permissão encontrada.
- AC-3: Dado o caso do AC-2, quando a abertura falha, então o arquivo permanece com a permissão e o conteúdo originais.
- AC-4: Dado um banco existente com permissão `0600`, quando o store abre, então abre normalmente.

**Fora do escopo:** criptografia em repouso; corrigir a permissão automaticamente de um banco existente; permissão de outros arquivos da fábrica (o socket já é tratado no #15).

**Aderência ao produto:** serve ao posicionamento "a fábrica é dona do próprio estado" e à trilha de auditoria; um dono por fábrica, numa máquina. Não contradiz posicionamento nem fora de escopo.

**Toca UI:** não

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- Contrato: `Database.open(path)` (público, mesma assinatura) passa a exigir arquivo `0600`; nova exceção `DatabaseInsecurePermissionsException(path, found)` em `store/src/main/kotlin/sfv2/store/`, `RuntimeException` com caminho e permissão encontrada (ex.: `rw-r--r--`).
- Camada: só `store` (`Database.kt` e a exceção nova). Sem migração, sem mudança de schema.
- Criação: se o arquivo não existe, `Files.createFile` com `PosixFilePermissions` `rw-------` antes de abrir o driver; arquivo vazio é banco novo para o SQLite, e `-wal`/`-shm` herdam o modo do arquivo principal.
- Existente: ler as permissões POSIX antes de abrir o driver; qualquer bit de grupo/outros lança a exceção sem abrir o driver (arquivo intocado). `0400` e `0600` passam.
- Sistema de arquivos sem POSIX: a checagem é ignorada (o projeto roda em Linux/macOS).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-2 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-3 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |
| AC-4 | store | `store/src/test/kotlin/sfv2/store/ModuleTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Banco existente com permissão aberta falha em vez de ser corrigido: o AC pede não alterar o arquivo, e corrigir em silêncio esconderia a exposição já ocorrida.
- Aceita `0400`: nada de grupo/outros; o critério é "mais aberta que `0600`".
- Testes no `ModuleTest.kt` existente: é a suíte de `Database.open`.
- Peso `standard` (e não `light` como a issue esperava): uma camada, mas 4 ACs (mais de 3).

## Open questions
- none
