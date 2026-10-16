# Skin terminal no canto — spec

**Issue:** #75 · **Branch:** `feat/75-skin-terminal-no-canto` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** standard

## Product
_Product Owner_

**Problem:** o assistente da fábrica já tem frases (#74), mas nenhuma skin as mostra. Falta a primeira skin, a terminal: uma janela pequena no canto com as últimas falas, e um registro que permita trocar de skin sem reiniciar. Este card entrega o modelo da skin, sem Compose e sem IO.
**Audience:** o desenvolvedor sozinho com muitos projetos, que deixa a fábrica trabalhando e quer ver de relance o que o assistente diz.
**Acceptance criteria**
- **AC-1** — Dado um histórico de frases do assistente, quando a skin terminal monta a janela, então ela mostra só as últimas frases, no máximo N linhas e com largura máxima de M colunas (frase longa é cortada), da mais antiga para a mais recente.
- **AC-2** — Dado a skin terminal ativa, quando o dono troca para outra skin registrada, então a skin nova passa a ser a ativa sem reiniciar e já recebe o histórico de frases acumulado.
- **AC-3** — Dado uma skin ativa, quando chega uma frase nova, então só a skin ativa a recebe e a janela passa a incluir essa frase como a mais recente.
**Out of scope:** renderização Compose; conexão com o socket/daemon (#63); modo silencioso; skin pop-up (#76); decisão do cartum (#77); geração das frases (#74) e do estado (core).
**Product fit:** serve ao assistente da fase 7 (personalidade sem rosto, com skins) descrito em Brand Commitments. Não contradiz o posicionamento nem o que está fora de escopo; é só apresentação, não altera estado da fábrica.
**Touches UI:** yes (modelo da janela; a renderização fica para o #63)

## UX
_UX/UI Designer_

Este card entrega só o modelo; o #63 renderiza. O modelo carrega o que a janela mostra; a aparência abaixo é a intenção para o #63.

- **Aparência:** janela pequena fixa no canto inferior direito, sem rosto e sem avatar. Fundo `panel`, borda hairline `edge` de 1px, raio `panel` (16px), padding `md` (16px). Só tokens do DESIGN.md; o DESIGN.md não define um tema "Terminal", então a skin usa os tokens do tema ativo e nenhuma cor nova.
- **Tipo:** fonte `log` (IBM Plex Mono, 12px, linha 1.55), cor `soft`. A frase mais recente em `text`; as anteriores em `muted`. Sem `sand`: o acento é só do que espera o dono.
- **Linhas:** no máximo N linhas (padrão 5), da mais antiga (em cima) para a mais recente (embaixo); uma frase por linha, prefixo fixo `> ` em `faint`. Largura máxima M colunas (padrão 40, medida em caracteres, não em pixels); cada linha ocupa exatamente M colunas no máximo.
- **Frase longa:** cortada em M colunas com `…` no fim (o `…` conta nas M). Nunca quebra em duas linhas, nunca rola. O modelo guarda a frase inteira; o corte é só de exibição.
- **Estado vazio (sem frases):** janela com uma linha só, `> ` e o texto `aguardando…` em `faint`. Não é erro.
- **Carregando / erro:** não existem neste modelo (sem IO). Se o #63 perder a conexão, mostra o vazio acima.
- **Ordem e atualização:** frase nova entra embaixo e empurra a mais antiga para fora quando passam de N. Troca de skin mostra o mesmo histórico, sem animação obrigatória.
- **Tela pequena:** M é parâmetro; o #63 reduz M à largura disponível e mantém N. Nenhum componente novo: reaproveita o painel e o tipo `log` do DESIGN.md.
- **Texto novo (pt-BR; o projeto é só pt-BR):** `aguardando…` (estado vazio). As frases vêm do #74, sem cópia aqui.

## Architecture
_Tech Lead_

Camada `app`, pacote `sfv2.app`; só `core` como dependência (sem Compose, sem IO, sem relógio); sem contrato público existente alterado, sem migração.

- `app/src/main/kotlin/sfv2/app/AssistantSkins.kt` (novo):
  - `interface AssistantSkin { val id: String; fun show(history: List<String>) }` — a skin recebe o histórico completo de frases (da mais antiga para a mais recente) e o apresenta.
  - `data class TerminalWindow(val lines: List<String>)` e `class TerminalSkin(maxLines: Int = 5, maxColumns: Int = 40) : AssistantSkin` com `fun window(history: List<String>): TerminalWindow`: últimas `maxLines` frases, cada uma prefixada com `> ` e cortada em `maxColumns` colunas (o prefixo conta; corte termina em `…`, que conta); histórico vazio vira uma linha `> aguardando…`. O construtor recusa `maxLines < 1` e `maxColumns < 4`. `show` guarda a janela atual em `current`.
  - `class SkinRegistry(skins: List<AssistantSkin>, initial: String)` com `val active: AssistantSkin`, `fun activate(id: String)` (id desconhecido lança `UnknownSkinException` com o id), `fun publish(phrase: String)` (acrescenta ao histórico e chama `show` só na skin ativa, com o histórico inteiro) e `val history: List<String>`. A troca chama `show(history)` na skin nova. O histórico guarda no máximo 100 frases (as mais antigas saem).
- Fiação com o socket e renderização Compose ficam para o #63; o daemon ainda não expõe a frase no protocolo (ver Open questions).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | app | `app/src/test/kotlin/sfv2/app/ModuleTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/ModuleTest.kt` |
| AC-3 | app | `app/src/test/kotlin/sfv2/app/ModuleTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Testes no `ModuleTest.kt` existente: é a única suíte do módulo `app`, e arquivo novo não se justifica para 3 comportamentos pequenos.
- N=5 e M=40 como padrões, configuráveis no construtor: resposta à pergunta do UX; o #63 ajusta M à tela.
- Entrega só o modelo sem Compose: o `app` não tem Compose nem conexão (#63 aberto) e o `app` não pode depender do `daemon` (ARCHITECTURE.md); o modelo é testável sem IO.
- Histórico limitado a 100 frases: evita crescimento sem limite num processo de longa duração.
- Peso `standard`: uma camada, mas `Touches UI: yes`.

## Open questions
- **Fiação:** A (recommended) criar um card `daemon` para expor a frase do assistente no socket e deixar o #63 ligar a skin, ou B esperar o #63 para decidir — hoje a skin não recebe frases reais, e sem isso o AC do card ("as frases aparecem") só vale no modelo.
