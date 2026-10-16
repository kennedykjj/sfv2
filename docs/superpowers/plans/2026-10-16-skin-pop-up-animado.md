# Skin pop-up animado — plano (#76)

Spec: `docs/superpowers/specs/2026-10-16-skin-pop-up-animado-design.md`. Peso `standard`, camada única `app`.

## Tarefas

| # | Camada | Agente | Depende de | Arquivos |
|---|---|---|---|---|
| 1 | app | QA (testes de aceitação, vermelhos) | — | `app/src/test/kotlin/sfv2/app/PopupSkinTest.kt` |
| 2 | app | Tech Lead (solo) | 1 | `app/src/main/kotlin/sfv2/app/PopupSkin.kt` |

## Notas

- Modelo puro e imutável: `SkinMode`, `MotionPreference`, `PopupPhase`, `PopupCommand`, `PopupSkin` (+ `Update`).
- Fase calculada só de `now - emittedAt`; durações em constantes nomeadas (`ENTRY` 400ms, `STAY` 4000ms, `EXIT` 260ms; `REDUCED` zera `ENTRY` e `EXIT`).
- `lastState` sobrevive a `GONE` e ao SILENT (AC-5, AC-6).
- Sem Compose, sem dependência nova.
