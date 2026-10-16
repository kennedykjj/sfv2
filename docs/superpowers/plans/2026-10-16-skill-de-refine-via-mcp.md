# Skill de refine via MCP — plano (#36)

**Peso:** light · **Camada única:** `daemon` (agente `engineer`, feito pelo Tech Lead em modo solo)

## Tarefas

1. **Texto da skill** — `daemon/src/main/resources/skills/refine/SKILL.md`
   - Português, cabeçalhos da spec em inglês, reescrito a partir de `skills/refine` da V0.
   - Passos: ler card (`card_get`), critérios (`review_criteria_get`), camadas (`layers_get`) e políticas (`policies_get`) pelo MCP; escrever a spec com `file_write_request`; encerrar com `completion_report`.
   - Três regras de segurança, cada uma com marca `<!-- rule:... -->` seguida do texto: `rule:untrusted-data`, `rule:no-shell`, `rule:write-scope` (AC-3).
   - Só cita ferramentas registradas em `McpServerFactory.serverFor` (AC-1).
   - Dependências: nenhuma. Bloqueia 2, 3, 4, 5, 6 (teste de contrato lê o texto).

2. **Template da spec** — `daemon/src/main/resources/skills/refine/spec-template.md`
   - Cópia do template que a skill manda seguir; o teste de AC-2 compara o formato com o que `SpecGates` lê.
   - Depende de 1.

3. **Loader** — `daemon/src/main/kotlin/sfv2/daemon/StageSkills.kt`
   - `object StageSkills { fun load(stage: String): String }`: lê `skills/<stage>/SKILL.md` do classpath; falha com `IllegalStateException` que diz o estágio quando o recurso não existe.
   - Sem IO além do classpath. Independente de 1 e 2.

4. **Teste AC-1** — `daemon/src/test/kotlin/sfv2/daemon/McpServerFactoryTest.kt`
   - Extrai do texto da skill os nomes entre crases em `snake_case` com sufixo `_get`/`_request`/`_report`; confere contra os nomes registrados em `serverFor(session)`.
   - Depende de 1 e 3.

5. **Teste AC-3** — mesmo arquivo `McpServerFactoryTest.kt`
   - Lê a skill e exige as três marcas `<!-- rule:untrusted-data -->`, `<!-- rule:no-shell -->`, `<!-- rule:write-scope -->`, cada uma seguida de texto não vazio.
   - Depende de 1 e 3.

6. **Teste AC-2** — `daemon/src/test/kotlin/sfv2/daemon/SpecGatesTest.kt`
   - Agente roteirizado privado no arquivo de teste: preenche o `spec-template.md` e grava por `McpRequestTools.fileWriteRequest`; o teste lê o arquivo gravado e roda `SpecGates.check`, exigindo zero achados e peso reportado.
   - Usa `FakeRuntime` de `ports` para o lançamento (script de saída 0). Depende de 2 e 3.

## Ordem

1 → 2 → 3 → (4, 5, 6). Os testes de AC são escritos pela QA antes da implementação.

## Arquivos

- Novos: `SKILL.md`, `spec-template.md`, `StageSkills.kt`.
- Alterados: `McpServerFactoryTest.kt`, `SpecGatesTest.kt` (testes).
- Migração: nenhuma.
