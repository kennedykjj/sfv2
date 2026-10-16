# Custo por estágio e por dia — plano

1. `Protocol.kt`: `Request.CostSummary` ganha `zone: ZoneId` (padrão UTC; `zone` inválido vira `Rejected`); novos `StageCost` e `DayCost`; `CostSummaryResult` ganha `stages` e `days`; `render` escreve os campos novos sem tocar nos atuais.
2. `CostSummaryQuery.kt`: agrupar os eventos de `costEventsBetween` por `payload["stage"]` (ausente = `unknown`) e por dia local no `zone`; somar com `CostSums.sum` e usar `statusOf`; garantir que a soma dos grupos é o `totalUsd`.
3. `CostSummaryQueryTest.kt` e `ProtocolTest.kt`: testes de AC-1 a AC-4 (estágios, dias em fuso não UTC, `zone` ausente/inválido, status PARTIAL/UNKNOWN, soma igual ao total).
4. `FeedSocket.kt`: enviar o `zone` do app e ler `stages`/`days`, tolerando ausência.
5. `FeedModel.kt`, `CostPanel.kt`, `Theme.kt`: linhas "Por estágio" e "Por dia" (só quando houver mais de um dia), só formatação.
6. `FeedTest.kt`: testes de AC-5 e AC-6 (formatação com parcial/desconhecido; daemon antigo sem os campos).
7. `./gradlew :daemon:test :app:test` e `./gradlew build`.
