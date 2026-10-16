# Configurações — spec

**Issue:** #69 · **Branch:** `feat/69-configuracoes` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead, UX/UI
**Weight:** heavy

## Product
_Product Owner_

**Problema:** o dono não tem como ajustar a fábrica (tamanho do pool, horário de trabalho, limites de custo, notificações) sem editar arquivos; e uma mudança pela metade não pode afetar o ciclo em andamento.
**Audiência:** o desenvolvedor sozinho com muitos projetos (e, igualmente, o dentro de empresa), dono único da máquina.

**Critérios de aceite**
- AC-1: Dado a tela Configurações aberta, quando o dono altera um valor sem salvar, então o valor fica só como rascunho e o dispatcher segue usando os valores salvos.
- AC-2: Dado um rascunho alterado, quando o dono descarta, então a tela volta aos valores salvos e nada é gravado.
- AC-3: Dado um rascunho válido e a prova do dono, quando o dono salva, então os valores persistem no daemon e valem a partir do próximo ciclo do dispatcher; um ciclo já em andamento mantém os valores antigos.
- AC-4: Dado um valor inválido (ex.: pool zero, horário com fim antes do início, limite de custo negativo), quando o dono salva, então o salvamento é recusado com mensagem que nomeia o campo e nada é gravado.
- AC-5: Dado um salvamento sem a prova do dono, quando o daemon o recebe, então é recusado e os valores salvos não mudam.
- AC-6: Dado o daemon inacessível, quando o dono salva, então o rascunho é mantido na tela com um erro visível e pode ser salvo depois.
- AC-7: Dado preferências de notificação (quais tipos, horário silencioso) salvas, quando a tela é reaberta ou o app reiniciado, então os valores salvos são exibidos; nenhuma notificação é entregue por esta entrega.
- AC-8: Dado outra máquina ou outro usuário, quando as configurações são lidas, então são as da própria máquina (por máquina, não por projeto).

**Fora de escopo:** entrega das notificações (#70); edição do `factory.yml` por projeto; remover a flag ilimitada só por ambiente.
**Aderência ao produto:** serve "o dono aparece só onde a decisão é dele" e "nada muda sem evento assinado pelo dono"; não contradiz o posicionamento (um dono, uma máquina, daemon único escritor).
**Toca UI:** sim.

## UX
_UX/UI Designer_

Idioma do app: só pt-BR (strings em `Texts`, como `ConnectionScreen`). Tokens do objeto `Tokens` (ground, panel, edge, text, soft, muted, ok, warn; `bad` e `sand` entram ali, pois já são do DESIGN.md); nenhuma cor nova, nenhum componente novo fora de painel, linha, stepper, interruptor e barra de salvar (todos já descritos em DESIGN.md > Settings).

- **Entrada e estrutura:** a tela Configurações abre pela navegação do shell, depois de "Conectado ao daemon". Uma coluna de grupos-painel (raio 16, borda `edge`, título 17px/600), na ordem: Pool de sessões, Horário de trabalho, Limites de custo, Notificações. Cada linha: rótulo (14px `text`), descrição (13px `muted`, até 64ch) e controle à direita. Números em tabular; horário em mono.
- **Pool de sessões:** um stepper inteiro (mín. 1) com o total de sessões simultâneas do pool; sem divisão por estágio (o pool do daemon é um só).
- **Horário de trabalho:** interruptor "Restringir ao horário" (rótulo de estado "Ligado"/"Desligado" à esquerda da trilha), campos "Início" e "Fim" (HH:MM, mono) e dias da semana como chips alternáveis (`aria-pressed`, estado também por texto/borda, não só cor). Desligado, os campos ficam desabilitados e `faint`.
- **Limites de custo:** campos numéricos "Limite por card" e "Limite por dia" com prefixo `US$` e sufixo "est."; vazio significa sem limite, dito na descrição ("Vazio: sem limite"). Todo valor de custo leva "est." (Estimate Rule).
- **Notificações:** interruptor por tipo (decisão espera por você, run falhou, fábrica parou) e "Horário silencioso" (início/fim). Nota 13px `muted` sob o título do grupo: "Só guarda as preferências. As notificações ainda não são entregues." (AC-7).
- **Rascunho:** ao alterar um controle, o valor passa a `text`; uma barra de salvar fixa no rodapé (raio 12, borda `edge-hi`, `panel-hi`) conta as mudanças e mostra "2 alterações não salvas", **Descartar** (ghost) e **Salvar** (botão cheio `text`, sem `sand`: a barra não é decisão pendente). Sem mudança, a barra não aparece. Descartar volta aos valores salvos sem confirmar (nada foi gravado). Salvar vira "Salvando…" e desabilita os dois botões e os controles.
- **Erro de campo (AC-4):** a validação roda no Salvar e a recusa do daemon usa a mesma exibição: o campo ganha borda `bad` e, logo abaixo, uma linha 12px `bad` que nomeia o campo, por exemplo "Horário de trabalho: o fim deve ser depois do início.", "Pool de sessões: use ao menos 1.", "Limite por dia: não pode ser negativo." Um resumo na barra ("Corrija 1 campo para salvar") leva o foco ao primeiro campo inválido; o rascunho fica intacto.
- **Daemon inacessível (AC-6):** faixa de alerta acima da barra, ponto e texto `bad`: "Não consegui falar com o daemon. Seu rascunho continua aqui." com **Tentar de novo** (ghost). O rascunho e a barra permanecem; Salvar volta a habilitar. Sem prova do dono (AC-5) a recusa usa a mesma faixa: "O daemon recusou o salvamento. Nada foi alterado."
- **Carregando e valores salvos:** na abertura, "Lendo as configurações…" (`muted`, sem skeleton) e controles desabilitados; falha de leitura mostra a faixa de daemon inacessível com Tentar de novo e sem controles editáveis. Depois de salvar, a barra some, o texto de estado `ok` "Salvo. Vale a partir do próximo ciclo." aparece 5s (região `status`, polite) e os controles mostram os valores salvos; reabrir a tela ou o app mostra os mesmos valores.
- **Janela e telas pequenas:** mínimo 360x240; padrão 960x640. Acima de 600px: painéis com no máx. 720px de largura, centralizados, rótulo/descrição à esquerda e controle à direita. Abaixo de 600px: linhas empilhadas (controle sob a descrição, stepper alinhado à esquerda), campos de hora lado a lado só se couberem, senão um sob o outro; a barra de salvar vira duas linhas (contagem, depois os botões lado a lado em largura total). O conteúdo rola dentro da janela; a barra fica fixa e a altura de 240px mostra ao menos a barra e uma linha de grupo (rolagem cobre o resto).
- **Teclado e acessibilidade:** ordem de foco segue a página; foco visível de 2px `sand`, offset 2; todo alvo ≥ 24x24px; Enter num campo salva, Esc descarta o campo em edição (não o rascunho todo); erros ligados ao campo por descrição acessível e anunciados de forma polite.

## Architecture
_Tech Lead_

- `core` (puro): `FactorySettings` (data class imutável: `poolSize: Int`, `workHours: WorkHoursSetting?` com dias, início, fim e zona, `cost: CostSetting` com `perCardUsd`/`perDayUsd` `BigDecimal?`, `notifications: NotificationSetting` com tipos ligados e horário silencioso) e `FactorySettingsRules.validate(settings): List<SettingsIssue>` (`field`, `reason`); `FactorySettings.DEFAULT`. Novo `EventType` `SETTINGS_SAVED` (ator dono; payload só com os nomes dos campos alterados, nunca valores de custo em log).
- `daemon`: `SettingsStore(path, clock)` grava `<dataDir>/settings.json` (modo 0600, escrita atômica por arquivo temporário + rename; arquivo ausente = `DEFAULT`). Dois comandos em `Protocol.kt`: `settings.get` (`Request.SettingsGet`, só leitura) e `settings.save` (`Request.SettingsSave(actor, settings, proof)`), respostas `Response.Settings(settings)` e recusa com `field`. `SettingsCommands` valida (AC-4), confere a prova do dono (AC-5), grava o arquivo e depois o evento; inválido ou sem prova não grava nada. Roteados em `SocketServer`, fora de `CardCommands`.
- "Próximo ciclo" (AC-3): `ConfiguredDispatchControl` passa a ler o `SettingsStore` a cada chamada e as configurações salvas ganham do `factory.yml` nos campos que elas cobrem (horário, limites); `unlimited` continua só no ambiente. `RunPool` troca `capacity: Int` por `capacity: () -> Int` lido em cada `tryStart`/`hasRoom`; sessões já em andamento não são interrompidas, só a admissão seguinte muda. O ciclo em curso decide com o valor lido no início dele.
- `app`: `SettingsDraft` (estado puro: valores salvos, rascunho, `changeCount`, `discard()`, `issues`), `SettingsClient` (interface) e `SocketSettingsClient` (JSON por linha no socket do daemon, como `SocketDaemonProbe`; lê o token do dono no arquivo do daemon como a CLI), `SettingsScreen` (Compose, textos em `Texts`, tokens `bad` e `sand` adicionados a `Tokens`) e entrada na navegação do shell. Falha de conexão mantém o rascunho (AC-6).
- Sem migração: arquivo novo, ausência = defaults, o `factory.yml` segue valendo onde não há valor salvo.
- Arquivos: `core/src/main/kotlin/sfv2/core/FactorySettings.kt` (novo), `Event.kt`; `daemon/.../SettingsStore.kt`, `SettingsCommands.kt` (novos), `Protocol.kt`, `SocketServer.kt`, `ConfiguredDispatchControl.kt`, `RunPool.kt`, `RefineRuntime.kt`, `Main.kt`; `app/.../SettingsDraft.kt`, `SettingsClient.kt`, `SettingsScreen.kt` (novos), `Main.kt`, `Module.kt`, `ConnectionScreen.kt` (se precisar de navegação); testes abaixo. Donos: `core`, `daemon`, `app`.

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | app | `app/src/test/kotlin/sfv2/app/SettingsDraftTest.kt` |
| AC-2 | app | `app/src/test/kotlin/sfv2/app/SettingsDraftTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SettingsCommandsTest.kt` |
| AC-4 | core | `core/src/test/kotlin/sfv2/core/FactorySettingsTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SettingsCommandsTest.kt` |
| AC-6 | app | `app/src/test/kotlin/sfv2/app/SettingsDraftTest.kt` |
| AC-7 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SettingsCommandsTest.kt` |
| AC-8 | daemon | `daemon/src/test/kotlin/sfv2/daemon/SettingsCommandsTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Configurações por máquina ficam num arquivo do daemon, não no `factory.yml`: o `factory.yml` é commit na `main` do projeto, e a issue pede valer por máquina e só ao salvar.
- Valores salvos ganham do `factory.yml` em horário e limites; sem valor salvo, o `factory.yml` continua valendo (compatível com projetos atuais).
- Pool é um tamanho único: o daemon tem um `RunPool` só, então o stepper por estágio proposto pelo UX foi removido.
- App só em pt-BR nesta entrega (resposta do UX: strings em `Texts`, sem catálogo de idiomas).
- Notificações: só preferências guardadas; entrega é a #70.
- Peso `heavy` (a issue esperava `standard`): toca 3 camadas (`core`, `daemon`, `app`).
- Arquivos de teste novos (`SettingsDraftTest`, `SettingsCommandsTest`, `FactorySettingsTest`): nenhuma suíte cobre configurações; AC-7 e AC-8 são verificados no daemon (persistência e escopo por máquina), pois o app só exibe o que o daemon devolve.
- Uma única fonte de teste do app testa o estado do rascunho em processo (sem renderizar Compose).

## Open questions
- none
