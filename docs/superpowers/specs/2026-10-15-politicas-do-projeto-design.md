# Políticas do projeto — spec

**Issue:** #19 · **Branch:** `feat/19-politicas-do-projeto` · **Date:** 2026-10-15
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problem:** A mesma fábrica precisa servir o projeto pessoal e a empresa; a diferença deve morar em política do projeto, não em código. Hoje o core não tem um modelo tipado dessas políticas.
**Audience:** O desenvolvedor dentro de uma empresa (entrega no PR, assinaturas, convenções) e o desenvolvedor sozinho com muitos projetos (padrões sem configurar nada).
**Acceptance criteria**
- **AC-1** — Given um factory.yml sem políticas, when as políticas são lidas, then valem os padrões: modo de entrega autônomo, fechamento com confirmação e convenções padrão de commit e branch.
- **AC-2** — Given um factory.yml com modo de entrega (autônomo ou entrega no PR), when lido, then o core expõe o modo como valor tipado (enum), não como texto livre.
- **AC-3** — Given um factory.yml que define quem assina cada efeito externo (por exemplo, importação, marco, fechamento), when lido, then o core expõe, por efeito, o signatário tipado (dono ou política que dispensa a assinatura).
- **AC-4** — Given um factory.yml com formato de commit e de branch, when lido, then o core expõe ambos tipados e disponíveis para quem monta commits e branches.
- **AC-5** — Given um valor inválido em qualquer campo de política, when lido, then a leitura falha com erro que nomeia o campo e não aplica padrão silencioso.
- **AC-6** — Given políticas parciais (só alguns campos), when lidas, then os campos ausentes assumem os padrões e os presentes são preservados.
**Out of scope:** Leitura do arquivo do disco (é do daemon); aplicar as políticas em fluxos (entrega, fechamento); UI de edição das políticas.
**Product fit:** Serve "Modo de entrega por projeto" e "Nada sai da máquina sem evento assinado pelo dono ou política explícita do projeto". Não contradiz o posicionamento; a dispensa de assinatura só existe como política explícita.
**Touches UI:** no

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

- **Layer:** só `core` (puro, sem IO, sem YAML). A entrada é a árvore já parseada (`Map<String, Any?>?`); o daemon lê o arquivo e passa a árvore.
- **Arquivos:** `core/src/main/kotlin/sfv2/core/ProjectPolicies.kt` (novo) e o teste do plano.
- **Contrato público (`sfv2.core`):**
  - `enum DeliveryMode { AUTONOMOUS, PR_HANDOFF }` (chaves YAML `autonomous`, `pr`).
  - `enum ExternalEffect { IMPORT, MILESTONE, CLOSE }` (chaves `import`, `milestone`, `close`).
  - `enum Signer { OWNER, POLICY }` (`owner`, `policy`; `policy` = política explícita que dispensa a assinatura).
  - `data class Conventions(commitFormat: String, branchFormat: String)`; padrões `{type}({scope}): {subject}` e `feat/{id}-{slug}`.
  - `data class ProjectPolicies(deliveryMode, signers: Map<ExternalEffect, Signer>, conventions)` com `ProjectPolicies.DEFAULT` e `ProjectPolicies.from(raw: Map<String, Any?>?)`.
  - `class InvalidPolicyException(val field: String, message) : RuntimeException`, mensagem com o nome do campo e o valor rejeitado.
- **Chaves lidas:** `delivery_mode`, `signers.<efeito>`, `conventions.commit_format`, `conventions.branch_format`. Chave ausente → padrão; chave presente e inválida → exceção com o caminho (ex.: `signers.close`). Chave desconhecida dentro de `signers` também é inválida.
- **Padrões de assinatura:** `import`=owner, `close`=owner (fechamento com confirmação), `milestone`=policy (decisão #37: marcos sem confirmação).
- **Validação de formato:** não vazio; só placeholders conhecidos (commit: `type`, `scope`, `subject`, `id`; branch: `id`, `slug`); commit exige `{subject}`, branch exige `{id}`.
- **Migração:** nenhuma (tipos novos, sem persistência).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | core | `core/src/test/kotlin/sfv2/core/ProjectPoliciesTest.kt` |
| AC-2 | core | `core/src/test/kotlin/sfv2/core/ProjectPoliciesTest.kt` |
| AC-3 | core | `core/src/test/kotlin/sfv2/core/ProjectPoliciesTest.kt` |
| AC-4 | core | `core/src/test/kotlin/sfv2/core/ProjectPoliciesTest.kt` |
| AC-5 | core | `core/src/test/kotlin/sfv2/core/ProjectPoliciesTest.kt` |
| AC-6 | core | `core/src/test/kotlin/sfv2/core/ProjectPoliciesTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
- Teste novo `ProjectPoliciesTest.kt`: nenhuma suíte do `core` cobre políticas (só Card, Event, Module, MoveRules).
- Entrada é `Map<String, Any?>`, não YAML: o core não tem IO nem biblioteca de parsing; o daemon faz a leitura (fora do escopo).
- Efeitos externos: IMPORT, MILESTONE, CLOSE, os três toques ao tracker da decisão #5; merge não entra, é coberto pelo modo de entrega.
- Signatário `POLICY` representa "política explícita que dispensa a assinatura" (ARCHITECTURE, invariante 7); só vale se escrito no factory.yml ou no padrão de MILESTONE.
- Peso `standard`: 6 critérios de aceitação (mais de 3) numa só camada.

## Open questions
- none
