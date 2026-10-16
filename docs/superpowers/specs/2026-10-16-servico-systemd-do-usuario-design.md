# Serviço systemd do usuário — spec

**Issue:** #58 · **Branch:** `feat/58-servico-systemd-do-usuario` · **Date:** 2026-10-16
**Roles:** PO, Tech Lead
**Weight:** heavy

## Product
_Product Owner_

**Problema:** a fábrica precisa rodar com o terminal fechado e voltar sozinha depois de um reboot.
**Audiência:** o desenvolvedor sozinho com muitos projetos (daemon sempre ligado, sem tarefa manual).

**Critérios de aceitação**
- AC-1: Dado Linux sem o serviço instalado, quando rodo `sfv2 service install`, então o daemon roda como unit systemd do usuário (`systemd --user`), habilitada para subir no login/boot (`WantedBy=default.target`).
- AC-2: Dado o serviço rodando, quando o processo do daemon cai com falha, então o systemd o reinicia (`Restart=on-failure`).
- AC-3: Dado o serviço já instalado, quando rodo `sfv2 service install` de novo, então termina sem erro e deixa uma única unit, igual à anterior.
- AC-4: Dado o serviço instalado, quando rodo `sfv2 service uninstall`, então o serviço é parado, desabilitado e a unit removida, sem arquivo da unit restante.
- AC-5: Dado o serviço não instalado, quando rodo `sfv2 service uninstall`, então termina com sucesso, sem erro e sem alterar nada.
- AC-6: Dado o serviço rodando, quando consulto o journal do usuário por `SyslogIdentifier=sfv2`, então vejo os logs do daemon.
- AC-8: Dado o serviço instalado pela primeira vez, quando rodo `sfv2 service install`, então existe `~/.config/sfv2/service.env` (modo 0600, só comentários explicando `SFV2_DISPATCHER_ENABLED`, `PATH` e `JAVA_HOME`), a unit o lê com `EnvironmentFile=-` e uma segunda instalação nunca o sobrescreve nem o altera: sem ele o dono não teria como ligar o dispatcher sob o systemd, já que a habilitação e o teto de custo só vêm de variável de ambiente.
- AC-9: Dado a unit gerada, quando a leio, então traz `NoNewPrivileges=true` e `UMask=0077` (arquivos e pastas de dados do daemon nascem só do dono) e nenhum segredo, token ou valor de ambiente.
- AC-10: Dado um caminho de launcher com espaço, quebra de linha, `%`, `$` ou aspas, quando o instalador renderiza a unit, então recusa com `Failed` sem escrever nada (o caminho entra no `ExecStart` e quebraria ou injetaria linhas na unit).
- AC-11: Dado o usuário sem `linger` (`loginctl show-user --property=Linger` diferente de `yes`), quando termino o `service install`, então a saída avisa que o serviço só sobe no login e como habilitar (`loginctl enable-linger`), sem habilitar sozinho.
- AC-7: Dado um sistema que não é Linux, quando rodo `sfv2 service install`, então o comando recusa com mensagem clara de que só Linux é suportado.

**Fora do escopo:** macOS (launchd) e Windows (fase 8); serviço de sistema (root); rotação ou configuração do journal; migração de estado.

**Aderência ao produto:** atende "um daemon por máquina, sempre ligado" e a fábrica trabalhando com app fechado e fora do horário do dono. Não contradiz posicionamento nem exclusões (Linux primeiro).

**Toca UI:** não

## UX
_UX/UI Designer_ — n/a

## Architecture
_Tech Lead_

**Contratos:** sem mudança em protocolo, eventos ou banco. Novos, em `daemon`:
- `ServiceUnit.render(launcher: Path): String` (pura): gera a unit com `ExecStart=<launcher>`, `Restart=on-failure`, `RestartSec=5`, `SyslogIdentifier=sfv2`, `NoNewPrivileges=true`, `UMask=0077`, `EnvironmentFile=-%h/.config/sfv2/service.env`, `WantedBy=default.target`. `launcher` precisa ser absoluto e sem espaço, quebra de linha, `%`, `$` ou aspas, senão `render` lança e o `install` devolve `Failed` (AC-10).
- `interface SystemctlRunner { fun run(vararg args: String): SystemctlResult }` (`SystemctlResult(exitCode, output)`), com implementação por processo (`systemctl --user ...`) e fake nos testes.
- `ServiceInstaller(unitDir: Path, launcher: Path, systemctl: SystemctlRunner, osName: String)` com `install()` e `uninstall()`, ambos retornando `ServiceOutcome` (sealed: `Installed`, `AlreadyInstalled`, `Removed`, `NotInstalled`, `Unsupported(os)`, `Failed(reason)`).
- `runServiceCommand(args, env, out, err): Int` — interpreta `service install|uninstall` e devolve o exit code.

**Camadas e arquivos (só `daemon`):**
- `daemon/src/main/kotlin/sfv2/daemon/ServiceUnit.kt` (novo)
- `daemon/src/main/kotlin/sfv2/daemon/ServiceInstaller.kt` (novo; inclui `SystemctlRunner` e a versão por processo)
- `daemon/src/main/kotlin/sfv2/daemon/Main.kt` (`main(args)` desvia `service ...` para `runServiceCommand`)
- `daemon/build.gradle.kts` (`applicationDefaultJvmArgs` com `-Dsfv2.launcher` para o launcher saber o próprio caminho)

**Comportamento:** `install` recusa fora de Linux (AC-7); escreve a unit em `$XDG_CONFIG_HOME/systemd/user/sfv2.service` (ou `~/.config/systemd/user`) de forma atômica; se o conteúdo já for idêntico não regrava e o resultado é `AlreadyInstalled` (AC-3); depois `daemon-reload` e `enable --now sfv2.service` (AC-1). `uninstall` sem unit retorna `NotInstalled` sem chamar o systemctl (AC-5); com unit: `disable --now`, remove o arquivo, `daemon-reload` (AC-4). Reinício e journal vêm só do texto da unit (AC-2, AC-6). Falha do systemctl vira `Failed` com exit code 1; log `key=value` sem conteúdo de ambiente.

**Migração:** n/a (sem banco).

## Test plan
_Tech Lead_

| AC | Layer | Test file |
|---|---|---|
| AC-1 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ServiceInstallerTest.kt` |
| AC-2 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ServiceInstallerTest.kt` |
| AC-3 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ServiceInstallerTest.kt` |
| AC-4 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ServiceInstallerTest.kt` |
| AC-5 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ServiceInstallerTest.kt` |
| AC-6 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ServiceInstallerTest.kt` |
| AC-7 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ServiceInstallerTest.kt` |
| AC-8 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ServiceInstallerTest.kt` |
| AC-9 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ServiceInstallerTest.kt` |
| AC-10 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ServiceInstallerTest.kt` |
| AC-11 | daemon | `daemon/src/test/kotlin/sfv2/daemon/ServiceInstallerTest.kt` |

## Security
_Cybersec_ — n/a

## Rollback
n/a

## Decisions
_What the pipeline decided on its own; one line each, with the reason._
- Peso `heavy`: o spec tem 7 critérios (mais de 6), apesar de uma só camada.
- Arquivo de teste novo `ServiceInstallerTest.kt`: nenhuma suíte do `daemon` cobre instalação de serviço.
- Testes em processo com `SystemctlRunner` fake e diretório temporário; systemd real não roda no gate, então reinício e journal (AC-2, AC-6) são verificados pelo texto da unit.
- `Restart=on-failure` e `RestartSec=5`: reinicia em queda, respeita `systemctl stop`.
- Sem `loginctl enable-linger` automático: altera config do sistema; `WantedBy=default.target` basta para subir no login. Ficar de pé sem login fica documentado, não feito.

## Open questions
- **Onde mora o comando:** A (recomendado) o launcher do daemon hospeda `service install|uninstall` agora e a `cli` encaminha depois, sem mudar a tabela de dependências — ou B: o comando entra na `cli` e a tabela de módulos muda à mão para `cli` depender de `daemon` (ou da lógica ir para `core`, que deve ser sem IO). Hoje `cli` só depende de `core`, então `sfv2 service install` literal exige B.
