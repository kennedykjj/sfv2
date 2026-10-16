# Plano — Botão Recarregar no quadro (#253)

1. `BoardMonitorTest`: escrever os testes de AC-1 a AC-4 com `FakeBoardSource` (snapshot trocado, evento durante a leitura, `IOException`, segunda chamada concorrente).
2. `BoardMonitor.resync()`: reter eventos, ler o snapshot, aplicar, drenar; devolver `Reloaded`, `Failed` ou `Busy`.
3. `BoardScreen`: botão Recarregar ao lado de Configurações, desabilitado sem conexão e durante a recarga.
4. `Main.kt`: ligar o botão a `monitor.resync()` e mostrar o aviso de falha.
5. `./gradlew :app:test` e `./gradlew build`; conferir o botão em captura desktop e mobile (AC-5).
