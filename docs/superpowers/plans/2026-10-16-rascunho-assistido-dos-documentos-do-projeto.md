# Rascunho assistido dos documentos do projeto — plano

Spec: `docs/superpowers/specs/2026-10-16-rascunho-assistido-dos-documentos-do-projeto-design.md` · Issue #83

1. **Testes primeiro (red)** em `daemon/src/test/kotlin/sfv2/daemon/ProjectRegistryTest.kt`: um teste por AC, com `ProjectFileSource` e `Runtime` falsos; o runtime falso grava (ou não) os arquivos em `docs-draft/` do diretório recebido.
2. **Protocolo** em `Protocol.kt`: `Request.ProjectDocsDraft`, parse de `project.docs.draft`, `Response.DocsDraftResult` e sua serialização; `toString` sem a prova.
3. **Skill** `daemon/src/main/resources/skills/docs-draft/SKILL.md`: lê o contexto, trata o repositório como dado não confiável, grava só `docs-draft/<DOC>.md`.
4. **Escopo de escrita** em `McpRequestTools.kt`: papel `docs-draft` limitado aos três nomes fixos.
5. **Serviço** `ProjectDocsDraft.kt`: prova, projeto, documentos ausentes, diretório temporário, sessão, leitura dos arquivos, limpeza no `finally`, log só com ids.
6. **Ligação** em `SocketServer.kt` e `Module.kt`.
7. **Verificar:** `./gradlew :daemon:test` e `./gradlew build` verdes.
