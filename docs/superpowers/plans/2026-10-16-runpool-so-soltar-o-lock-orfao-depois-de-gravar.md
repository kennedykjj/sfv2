# Plano — RunPool: só soltar o lock órfão depois de gravar o evento (#129)

Spec: `docs/superpowers/specs/2026-10-16-runpool-so-soltar-o-lock-orfao-depois-de-gravar-design.md`

## Tarefas

1. **daemon** (agente: engineer / solo, peso light) — `daemon/src/main/kotlin/sfv2/daemon/RunPool.kt`
   - Criar a interface `LockEventRecorder` (buscar o card, gravar a mudança) com adaptador padrão sobre `CardEventStore`; o construtor atual com `CardEventStore` continua valendo (sobrecarga).
   - `sweep()`: por lock morto, gravar o evento primeiro e remover o lock só depois; falha capturada por lock, log `lock_release_failed card=<id>` sem a mensagem da exceção, e segue para os demais; devolve só os liberados. Lock sem card continua removido.
   - Dependências: nenhuma.

Testes de aceitação (QA): `daemon/src/test/kotlin/sfv2/daemon/RunPoolTest.kt` (AC-1..AC-3), antes da implementação.
