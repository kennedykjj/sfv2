# sfv2 doctor — spec

**Issue:** #59 · **Branch:** `feat/59-sfv2-doctor` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** light

## Product
_Product Owner_

**Problem:** Antes de ligar a fábrica numa máquina, o dono não tem como saber se faltam ferramentas ou se o daemon está de pé; descobre pelo primeiro run quebrado.
**Audience:** O desenvolvedor sozinho com muitos projetos (também serve ao desenvolvedor de empresa, mesma máquina).
**Acceptance criteria**
- **AC-1** — Dado uma máquina, quando rodo `sfv2 doctor`, então vejo uma linha por verificação (java, git, gh, claude, socket do daemon, banco, serviço systemd), cada uma com OK ou FAIL.
- **AC-2** — Dado uma verificação que falha, quando o doctor imprime a linha, então ela traz FAIL e uma instrução concreta de como resolver.
- **AC-3** — Dado que todas as verificações passam, quando o doctor termina, então o código de saída é 0.
- **AC-4** — Dado que ao menos uma verificação falha, quando o doctor termina, então o código de saída é diferente de 0 e as demais verificações ainda foram executadas e listadas.
- **AC-5** — Dado que o daemon não está rodando, quando rodo o doctor, então socket, banco e serviço aparecem como FAIL com a dica, sem travar nem lançar erro não tratado.
- **AC-7** — Dado `owner.token` (ao lado do socket) ou o diretório de dados com permissão mais aberta que o dono (token diferente de 0600, diretório com acesso de grupo ou outros), quando rodo o doctor, então a linha `permissões` é FAIL e diz o comando para corrigir (`chmod 600 ...`, `chmod 700 ...`); só o modo e o caminho são impressos, nunca o conteúdo do token.
- **AC-8** — Dado o serviço systemd instalado, quando rodo o doctor, então a linha `ambiente do serviço` confere que `~/.config/sfv2/service.env` existe com modo 0600 e avisa que o `PATH` do serviço é o do systemd e não o da sua shell (por isso `java`, `git`, `gh` e `claude` achados na shell podem faltar no daemon); sem imprimir o conteúdo do arquivo.
- **AC-6** — Dado qualquer saída, quando o doctor imprime, então não contém segredo, token nem conteúdo de issue ou spec.
**Out of scope:** Corrigir os problemas automaticamente; instalar dependências; verificar login/autenticação do gh e do claude além de existirem; macOS e Windows (fase 8); UI no app.
**Product fit:** Autossuficiência (fase 5, S5-02): a máquina do dono é diagnosticável sem ajuda, coerente com daemon local sempre ligado. Não contradiz posicionamento nem o que está fora de escopo.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- Contrato: novo subcomando `sfv2 doctor` (sem argumentos). Uma linha por verificação: `OK <nome>` ou `FAIL <nome> — <como resolver>`; saída em stdout, código 0 se tudo OK, 1 se alguma falhar. Todas as verificações sempre rodam.
- Verificações (todas sem IO fora do que o CLI já faz, resolvidas só por `env`): `java`, `git`, `gh`, `claude` = executável encontrado em `PATH`; `socket` = socket do daemon (convenção de `SocketLocation`) existe e aceita conexão; `banco` = arquivo `sfv2/sfv2.db` sob `XDG_DATA_HOME` (ou `HOME/.local/share`), mesma convenção do daemon; `serviço` = unit `sfv2.service` em `XDG_CONFIG_HOME/systemd/user` (ou `HOME/.config/systemd/user`); `permissões` e `ambiente do serviço` (AC-7 e AC-8) só leem o modo do arquivo (`PosixFilePermissions`), nunca o conteúdo. Sem `XDG_DATA_HOME` nem `HOME`, o `banco` usa o diretório do socket, como o `resolveDatabasePath` do daemon.
- Camada/módulo: só `cli`. Arquivos: `cli/src/main/kotlin/sfv2/cli/DoctorCommand.kt` (novo: comando e verificações) e `cli/src/main/kotlin/sfv2/cli/Main.kt` (registrar o subcomando).
- Migração: n/a. Nenhum contrato do daemon muda.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-2 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-3 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-4 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-5 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-6 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-7 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |
| AC-8 | cli | `cli/src/test/kotlin/sfv2/cli/MainTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `light`: uma camada (cli), 6 ACs mas só 2 arquivos, sem UI nem segurança; os ACs de código de saída e ausência de segredo compartilham a mesma implementação. Mantido `light` pela instrução do card.
- Verificar existência, não versão nem login: o escopo do PO exclui autenticação; versão mínima de java não consta nos critérios.
- Serviço verificado pelo arquivo da unit, sem chamar `systemctl`: o doctor roda sem processos externos e o teste é determinístico via `env`.
- Testes no `MainTest.kt` existente (já tem `runCli` com `env` e daemon falso); sem arquivo de teste novo.

## Open questions
- none
