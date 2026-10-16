# Skill de develop via MCP — plano (#37)

**Peso:** standard · **Camada única:** `daemon` (agente `engineer`, feito pelo Tech Lead em modo solo)

## Tarefas

1. **Fonte única de ferramentas** — `daemon/src/main/kotlin/sfv2/daemon/McpServerFactory.kt`
   - `McpServerFactory.TOOL_NAMES: Set<String>` com as 7 ferramentas registradas; o registro em `serverFor` usa as constantes.
   - Independente.

2. **Steps da skill** — `daemon/src/main/kotlin/sfv2/daemon/StageSkills.kt`
   - `StageSkills.steps(stage): List<SkillStep>` (`role`, `gate`) lido do front matter `steps:` de `skills/<stage>/SKILL.md`; `load` continua devolvendo o texto.
   - Depende de 3 para ter o que ler.

3. **Texto da skill** — `daemon/src/main/resources/skills/develop/SKILL.md`
   - pt-BR, front matter `steps:` (`qa`/`red`, `engineer`/`green`), três regras `<!-- rule:... -->` iguais às do refine (write-scope adaptado: engenheiro só a sua camada, QA só testes), cita só ferramentas de `TOOL_NAMES`.
   - Manda ler `card_get`, `spec_get`, `layers_get`; QA escreve testes falhando antes; escreve só por `file_write_request`; encerra com `completion_report`; nunca move o card.

4. **Teste de contrato** — `daemon/src/test/kotlin/sfv2/daemon/DevelopSkillTest.kt` (QA); o teste do refine passa a usar `TOOL_NAMES`.

## Ordem

1 → 3 → 2. Testes de aceitação escritos pela QA antes.

## Arquivos

- Novos: `develop/SKILL.md`. Alterados: `McpServerFactory.kt`, `StageSkills.kt`. Migração: nenhuma.
