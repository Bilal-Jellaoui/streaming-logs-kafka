# Moteur B : concurrence manuelle (Java)

Compte les requêtes et les erreurs 5xx par URL et par fenêtre de 5 s, en lisant `web-logs` et en publiant dans `stats-b`. Même résultat que le Moteur A (Kafka Streams), mais sans Kafka Streams : tout est fait avec les outils de concurrence Java.

## Architecture
- **ExecutorService** : N workers (4 par défaut), chacun avec son propre `KafkaConsumer`.
- **consumer.assign** : chaque worker prend ses partitions à la main (`partition % N == id`). Pas de groupe donc pas de rééquilibrage, donc aucune requête comptée deux fois.
- **ConcurrentHashMap + AtomicLong** : compteurs partagés `fenêtre|url` (requêtes et erreurs 5xx), sans verrou.
- **CyclicBarrier** : tous les workers s'arrêtent à la fin de chaque fenêtre (alignée sur l'horloge). La dernière arrivée exécute l'action de la barrière : publier les totaux dans `stats-b`, puis purger les anciennes fenêtres.

## Lancer
Cluster Kafka démarré, puis lancer `logs.engineb.EngineB` (IntelliJ), ou :
`mvn -pl engine-b -am test` pour les tests.

Options : `-Dkafka.bootstrap=...`, `-Dworkers=4`, `-Dwindow.seconds=5`, `-Dkeep.windows=120`, `-Dstart.from=earliest|latest`.

## Format de sortie (identique au Moteur A)
`{"engine":"B","windowStart":<ms>,"url":"/checkout","requests":2,"errors5xx":2}`
Les totaux sont cumulatifs : le dernier message d'une (fenêtre, url) fait foi.

## Tests
- `WindowCountersTest` (8 tests) : comptage par URL, 5xx vs 404, fenêtres de 5 s, 8 threads × 10 000 = 80 000 exact, purge, JSON.
- Test d'intégration manuel sur le cluster 3 brokers : mêmes valeurs que le Moteur A.

## engine-b: Responsable : Ayoub Echihami Mis en place avec le chef d'équipe (Bilal Jellaoui)
