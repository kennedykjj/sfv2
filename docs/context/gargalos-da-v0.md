# Gargalos da V0 observados construindo a sfv2

Diário de campo: cada vez que a V0 trava, erra ou custa caro construindo a sfv2, entra uma linha aqui, com a evidência e o que a sfv2 precisa fazer diferente. Serve de entrada para refinar os cards das fases 2 a 5 (quem refina um desses cards lê esta página antes). Lições já fechadas ficam em [v0-reference.md](v0-reference.md).

Formato: **G<n> — título.** O que aconteceu (card, data, evidência) · custo · o que a sfv2 exige · card da sfv2 que cobre (ou "sem card").

## Canal do dono com o card em andamento

- **G1 — Comentário na issue não chega ao card em andamento.** Em 2026-10-15 o auditor comentou no #9 (trocar Jackson, comentários em inglês) durante o Coding; o card foi mergeado sem nenhuma das correções. A V0 tem um canal para o dono falar com o card (a aba Live do dashboard entrega a mensagem pelo hook ao run ativo), mas só pelo dashboard e só enquanto um run está ativo; comentário na issue não é lido, e o CLAUDE.md manda tratar texto de issue como não confiável. · Custo: um PR de correção à mão (#100). · A sfv2 precisa de um pedido de mudança do dono como evento assinado, ligado ao card e entregue ao agente pelo MCP no próximo passo, haja run ativo ou não. · #29 (MCP: pedidos), #42 (aprovar e pedir mudança pela CLI).
- **G2 — Correção do dono depois da aprovação quebra o staged-head.** No #10 (2026-10-15) achou-se 🔴 já em Staged; mexer na branch faria o deploy recusar o PR ("changed after it was staged"). A saída foi aprovar e corrigir depois do merge (#104). · Custo: código errado na main por alguns minutos e um PR extra. · A sfv2 precisa de "aprovar com emenda": o dono empurra um commit e a aprovação passa a valer para o novo head, registrada como evento. · #46 (entrega autônoma), #42.

## Decisões que nenhum card deveria tomar sozinho

- **G3 — Dois cards paralelos tomam a mesma decisão de arquitetura, cada um do seu jeito.** #9 e #15 escolheram Jackson em versões diferentes (2.18.2 e 2.19.2) na mesma chave do catálogo. · Custo: conflito garantido e decisão de projeto inteiro tomada por um card light. · O refine da sfv2 precisa detectar decisão transversal (dependência nova, formato de dado, convenção) e transformá-la em pergunta ao dono antes do Spec Review, e cruzar os cards em refine que tocam os mesmos arquivos. · #36 (skill de refine), #39 (pipeline de refine); sem card para o cruzamento entre cards.
- **G4 — Caminho proibido vira gambiarra, não pedido.** Os dois cards descartaram kotlinx.serialization porque "exige mexer em `build-logic/`, que é proibido"; era uma linha que só o dono podia fazer. · O agente precisa de um jeito de pedir ao dono uma mudança em área proibida (evento "pedido de mudança fora do escopo"), em vez de contornar. · #29, #30 (gates de escopo).

## Revisão que deixa passar

- **G5 — O validate não aplica as regras "nunca" do CLAUDE.md.** O #9 entrou com comentários de código em português e o #8 com `copy()` público furando a validação; o `review-criteria.md` classifica violação de CLAUDE.md como 🔴. · A sfv2 precisa transformar em gate determinístico tudo o que dá para checar por máquina (lint/detekt para `!!`, nome qualificado, `assertEquals`, aviso do compilador tratado como erro) e deixar para o revisor LLM só o que exige julgamento. · #26 (executor de gates), #31, #38.
- **G6 — Teste tautológico passa no gate de asserção.** `MoveRulesTest` AC-5 compara a função com ela mesma; AC-6 reimplementa a regra; `EventTest` AC-1 testa o construtor da data class. O gate só conta `assertThat`. · A sfv2 precisa que a revisão de teste pergunte "este teste falharia se a implementação estivesse errada?" (mutação barata ou checagem no validate). · #26, #38.

## Contexto que o agente não alcança

- **G7 — Referência fora do repositório é invisível para o agente.** A spec do #10 diz que "a tabela M1–M7 da V0 não está acessível à fábrica" e inventou a tabela, invertendo as decisões do dono (Staged→Ready for Prod liberado à fábrica). `v0-reference.md` aponta para `~/git/shipfloor`, fora do worktree e da permissão do agente. · Custo: 🔴 de domínio que passou pelo Spec Review e foi corrigido no #104. · Contexto que o agente precisa ler tem que estar no repositório (copiado ou resumido) ou exposto por uma ferramenta MCP de leitura. · #28 (MCP: leitura); por ora, copiar para `docs/context/` o que um card precisar.

## Custo e peso

- **G8 — O PO infla critérios e o peso sobe junto.** #15 foi de 3 para 7 ACs e de standard para heavy; #101 também ficou heavy pela regra "mais de 6 ACs", com uma camada e 3 arquivos. · Peso por contagem de AC é manipulável pelo próprio pipeline. · A sfv2 deve calcular o peso por sinais objetivos (camadas, arquivos, superfície de segurança) e mostrar ao dono quando o PO acrescenta ACs. · #32 (gates de spec: plano de teste e peso).
- **G9 — Step-up de modelo por falha de gate.** #8 falhou no gate red em haiku e subiu para sonnet; #15 falhou no gate scope em sonnet e subiu para opus (dispatcher.log, 2026-10-15 22:00–22:06). · Sem saber por que falhou, subir de modelo é caro e pode não resolver. · A sfv2 deve registrar o motivo da falha do gate como evento e só subir de modelo quando o motivo for de capacidade, não de ambiente. · #35 (step-up).

## Fila e merge

- **G10 — Cards paralelos no mesmo arquivo viram conflito no deploy.** #11 e o fix do #8 mexeram no `Card.kt`; o PR #103 ficou `dirty` e sem CI. O dispatcher libera cards só por dependência declarada, não por sobreposição de arquivos. · A sfv2 precisa olhar os arquivos previstos no plano e serializar (ou avisar) cards que tocam os mesmos. · #27 (ciclo do dispatcher), #49 (conflito com a main).
- **G11 — Cada merge desatualiza os outros PRs.** Com a branch protegida exigindo estar atualizada e sem auto-merge, cada merge obriga atualizar a branch e esperar o CI de novo (#98, #100, #104). Os merges do #104 e do #105 deixaram o PR #103 do #11 atrás da main de novo, logo depois de o deploy mesclá-la (14bc287). · A sfv2 deve tratar a fila de merge como dela (merge queue do forge ou fila local com rebase e CI). · #46, #49.

## Operação do dono

- **G12 — Aprovar é mover coluna, e a ferramenta de mover recusa o dono.** `board.sh move` recusa colunas do dono de propósito; aprovar fora do dashboard exige `gh project item-edit` com ids de campo. A aprovação fica registrada como "alguém mexeu no board", sem assinatura. · A sfv2 já prevê aprovação como evento assinado; falta a CLI do dono para aprovar, devolver e liberar card sem dashboard. · #42.
- **G13 — Issue criada fora do importador fica fora do board.** O follow-up #101 nasceu sem item no board e sem Blocked by; precisou de duas chamadas à mão. · Na sfv2 importar é o único caminho de entrada e já cria card e dependências. · #54 (comando importar).

## Gates e travas

- **G14 — Stub temporário do QA trava os gates de aceitação.** No #101 o QA criou `RedStubs.kt` para o gate red compilar; o engineer o apagou como o próprio arquivo mandava. `acceptance-intact` recusou a remoção e `qa-amend` acusou "remove 1 linha de asserção" porque o comentário dizia "fail by assertion". O card parou três vezes (develop duas, validate uma) e subiu de sonnet para opus sem chance de resolver. O dono precisou de dois mecanismos para a mesma decisão: `shipfloor accept` (acceptance-intact) e o label `qa-amend-ok` (qa-amend). · Corrigido na V0 pelo PR kennedykjj/shipfloor#289 (conta só código, marcador `shipfloor:qa-stub`, sem step-up nesses gates). · A sfv2 precisa tratar o stub de red como artefato declarado do QA, com um só ponto de aprovação do dono. · #26, #40.
- **G15 — Run morto deixa lock com PID morto.** O deploy do #11 terminou com exit 143 às 23:07 e deixou `locks/deploysh-11.lock`; com TTL de 3 h o card ficaria parado. Removido à mão. · A sfv2 deve verificar o dono do lock (PID e instante de início) a cada ciclo e liberar o lock órfão como evento. · #25 (pool, slots e locks).
- **G16 — "Update branch" feito por fora bloqueia o deploy.** Dois merges da main pelo botão do GitHub (4e75193, 9fc5b97) mudaram o head do PR #103 depois do Staged; o deploy parou com "o PR mudou depois de ir para Staged" (exit 5). A branch voltou ao último merge registrado pelo deploy (14bc287) e o card andou. A interface do GitHub ainda sugeria "Rebase and merge", impossível por causa do conflito resolvido num merge, enquanto o deploy mergeia com squash. · A sfv2 deve ser a única fila de merge, tratar head alterado por fora como evento com aviso e caminho de reaprovação, e não depender do método de merge escolhido na interface. · #46, #49.
