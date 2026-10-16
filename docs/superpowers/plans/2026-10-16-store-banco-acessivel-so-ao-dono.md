# Plano: banco acessível só ao dono (#106)

Spec: `docs/superpowers/specs/2026-10-16-store-banco-acessivel-so-ao-dono-design.md`

## Tarefas

1. **store** (agent: engineer, solo; peso standard, uma camada)
   - Nova `DatabaseInsecurePermissionsException(path, found)` em `store/src/main/kotlin/sfv2/store/`.
   - `Database.open(path)`: se o arquivo não existe, criar com `rw-------` antes de abrir o driver; se existe, ler as permissões POSIX e lançar a exceção quando houver bit de grupo/outros, sem abrir o driver. Sem POSIX: ignora.
   - Arquivos: `Database.kt`, `DatabaseInsecurePermissionsException.kt`.

Dependências: nenhuma. Testes de aceitação (QA): `store/src/test/kotlin/sfv2/store/ModuleTest.kt`.
