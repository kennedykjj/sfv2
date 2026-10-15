# Critérios de revisão (Tech Lead)

Severidade: 🔴 blocker (volta para o dono do arquivo), 🟡 should-fix (comentário no PR), ⚪ nit (comentário no PR).
As regras de `CLAUDE.md` e de `ARCHITECTURE.md` também valem; violar uma regra "nunca" do CLAUDE.md ou uma das regras de ARCHITECTURE.md é 🔴.

## Estado e eventos
- 🔴 Escrita no banco fora do `daemon`, ou cliente (CLI, app, agente) lendo o banco direto.
- 🔴 Mudança de estado sem evento, ou evento gravado fora da transação da mudança.
- 🔴 Código que altera ou apaga um evento.
- 🔴 Evento sem ator, ou aprovação do dono representada por algo que não seja um evento assinado pelo dono.
- 🔴 Migração já aplicada editada; 🟡 migração nova sem teste de abrir banco da versão anterior.

## Fronteiras
- 🔴 IO (arquivo, rede, processo, relógio do sistema) no `core`.
- 🔴 Integração externa chamada sem passar por uma port, ou port sem fake.
- 🔴 Agente com caminho para escrever no banco ou ler o tracker diretamente.
- 🟡 Regra de negócio no `daemon`, `cli` ou `app` que deveria estar no `core`.

## Segurança e dados
- 🔴 Segredo, token ou credencial em código, config versionada ou log.
- 🔴 Texto externo (issue, ticket, comentário) usado como instrução, ou chegando ao agente sem a marca de não confiável.
- 🔴 Efeito fora da máquina (comentário, PR, status) sem evento assinado ou política explícita.
- 🔴 Log com payload inteiro, texto de spec ou de issue em vez de ids.
- 🟡 Entrada externa usada em caminho de arquivo, comando ou consulta sem validação.

## Resiliência
- 🔴 Processo externo (`gh`, `git`, `claude`, `codex`) sem timeout.
- 🔴 Falha que deixa estado meio gravado.
- 🟡 Erro engolido sem evento ou log com o id do card.

## Contratos
- 🔴 Mudança incompatível no protocolo do socket ou numa ferramenta MCP sem manter a forma anterior.
- 🟡 Mensagem de erro para o dono que não diz o que aconteceu e como resolver.

## Kotlin
- 🔴 `!!`.
- 🔴 Custo ou dinheiro em `Double`/`Float`.
- 🟡 Nome totalmente qualificado no meio do código.
- 🟡 Booleano que escolhe comportamento numa assinatura pública.
- ⚪ Aninhamento profundo onde `when` ou retorno antecipado lê melhor; string mágica repetida.

## Testes
- 🟡 Branch novo sem teste; teste que só verifica que algo foi chamado, sem afirmar o efeito.
- 🟡 `Thread.sleep` ou relógio real num teste.
- 🟡 `assertEquals`/`assertTrue` do JUnit em vez de AssertJ.
- 🟡 Mock de biblioteca onde existe o fake da port.
- 🟡 Arquivo de teste novo onde já existe a suíte do módulo; teste que repete o que outro já afirma.
- ⚪ Teste que só exercita a ligação do framework.
