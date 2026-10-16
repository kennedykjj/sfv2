# Spike: Compose Desktop no Fedora com Wayland (#7)

**Data:** 2026-10-15 · **Máquina:** Fedora 44, GNOME Shell 50.5 em Wayland, extensão AppIndicator ligada, 16 núcleos · **Código:** `spikes/wayland/` (build separado, fora do build principal)

## Pergunta

O app da fase 6 e o assistente da fase 7 dependem de três coisas no Linux: ícone na bandeja com pop-up, uma janela pequena sempre por cima das outras e animação fluida. Compose Desktop entrega isso no GNOME com Wayland?

## Como foi testado

Uma janela Compose sem borda, transparente e `alwaysOnTop`, no canto inferior direito, com um quadrado girando continuamente e o fps medido por `withFrameNanos`. Um ícone de bandeja (`Tray` do Compose) com menu para abrir um pop-up e enviar uma notificação. Runtime: JetBrains Runtime 25.0.4.1 (b635.72), o único JDK com o backend nativo de Wayland para AWT (WLToolkit); Compose Multiplatform 1.12.1, Skiko 0.150.1, Kotlin 2.4.20.

Fps e CPU foram medidos por log e por `/proc`. Bandeja, "sempre no topo", fluidez e notificação foram conferidos pelo dono na tela.

## Resultado

### Wayland nativo (WLToolkit): falha

| Variante | Resultado |
|---|---|
| OpenGL, transparente | não abre: `IllegalStateException: Can't lock DrawingSurface` |
| OpenGL, opaca | mesma falha |
| Software, transparente | mesma falha |

Além disso, `SystemTray.isSupported()` é `false` nesse backend. Compose Desktop não roda em Wayland nativo nesta combinação de JBR e Skiko.

### XWayland (XToolkit, o padrão do AWT): funciona, com uma condição

| Variante | Fps | CPU (de um núcleo) | Memória |
|---|---|---|---|
| OpenGL, transparente | **1 a 2** (quebrado) | — | — |
| OpenGL, opaca | 62 a 67 | 7% | 291 MB |
| **Software, transparente** | **63 a 67** | **9%** | **235 MB** |

Conferido pelo dono, na variante software e transparente:

- **Bandeja:** o ícone aparece na barra superior e o menu funciona (pop-up abre). Defeito: o ícone aparece com **fundo branco**. É limitação conhecida do `TrayIcon` do AWT no X11 (protocolo XEmbed, sem canal alfa), não da imagem.
- **Sempre no topo:** o quadro continua visível sobre uma janela maximizada.
- **Animação:** lisa, cerca de 60 fps.
- **Notificação:** o dono viu a notificação normal do GNOME. Ponto não explicado: o log registrou um `UnsupportedOperationException: The system tray is not supported on the current platform` na thread do AWT, e a linha de log da notificação não apareceu. Não reproduzi a causa; tratar como não confiável.

## Recomendação

1. **Seguir com Compose Desktop, rodando em XWayland.** É o padrão do AWT; não precisa de flag. Não tentar WLToolkit até uma versão do Skiko suportar.
2. **Janelas transparentes renderizam em software** (`skiko.renderApi=SOFTWARE`); janelas opacas ficam em OpenGL. Transparente em OpenGL no XWayland cai para 1 fps.
3. **Bandeja via StatusNotifierItem (D-Bus), não via `TrayIcon` do AWT**, para ter ícone com transparência e menu nativo. A extensão AppIndicator, que o GNOME precisa para mostrar qualquer ícone de bandeja, entende esse protocolo. Avaliar uma biblioteca SNI na fase 6 (S6-08).
4. **Notificação via `org.freedesktop.Notifications`** (D-Bus ou `notify-send`), não pelo `Tray` do AWT, pelo ponto não explicado acima.
5. **Requisito do produto no GNOME:** extensão AppIndicator ligada. Sem ela não existe bandeja; o `sfv2 doctor` deve avisar.
6. **Custo aceitável:** uma janela pequena animando sem parar custa cerca de 9% de um núcleo; o assistente real só anima em mudança de estado.

## Como reproduzir

```sh
./gradlew -p spikes/wayland run -Pjvm="-Dskiko.renderApi=SOFTWARE"   # XWayland, a variante recomendada
./gradlew -p spikes/wayland run -Ptoolkit=wayland                     # Wayland nativo (falha)
./gradlew -p spikes/wayland run -Pseconds=8 -Pjvm="-Dspike.transparent=false"   # sai sozinho em 8 s
```

Se a sessão herdou um `XAUTHORITY` antigo, use o de `systemctl --user show-environment`.
