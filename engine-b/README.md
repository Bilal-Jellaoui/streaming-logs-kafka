# Moteur B : concurrence manuelle (Java)

**Responsable :** Ayoub Echihami (Membre 4), mis en place avec le chef d'équipe (Bilal Jellaoui).

Compte les requêtes et les erreurs 5xx par URL et par fenêtre de 5 s, en lisant `web-logs` et en publiant dans `stats-b`. Même résultat que le Moteur A (Kafka Streams), mais sans Kafka Streams : tout est fait avec les outils de concurrence Java.

## Technologies utilisées
| Technologie | Rôle |
|---|---|
| Java 17 | Langage |
| Apache Kafka 3.7.0 (KRaft, 3 brokers, Docker) | `web-logs` → `stats-b` |
| `KafkaConsumer` / `KafkaProducer` (client Kafka) | Lecture et publication |
| `ExecutorService` | N workers en parallèle (4 par défaut) |
| `ConcurrentHashMap` + `AtomicLong` | Compteurs partagés sans verrou |
| `CyclicBarrier` | Synchronisation des workers à la fin de chaque fenêtre |
| Module `common` (`LogParser`, `LogEntry`) | Lecture des lignes de log |
| SLF4J (`slf4j-simple` 1.7.36), Maven, JUnit 5, GitHub Actions | Journaux, build, tests, CI |

## Architecture
- **ExecutorService** : N workers, chacun avec son propre `KafkaConsumer`.
- **consumer.assign** : chaque worker prend ses partitions à la main (`partition % N == id`). Pas de groupe donc pas de rééquilibrage, donc aucune requête comptée deux fois. Avec 8 partitions et 4 workers : 2 partitions par worker.
- **ConcurrentHashMap + AtomicLong** : compteurs partagés clé `fenêtre|url` (requêtes et erreurs 5xx).
- **CyclicBarrier** : tous les workers s'arrêtent à la fin de chaque fenêtre (alignée sur l'horloge). La dernière arrivée exécute l'action de la barrière : publier les totaux des clés modifiées dans `stats-b`, puis purger les anciennes fenêtres.

## Lancer
1. Démarrer le cluster : `docker start soukline-kafka-1 soukline-kafka-2 soukline-kafka-3`
2. Lancer `logs.engineb.EngineB` (IntelliJ). La console affiche les 4 workers et leurs partitions.
3. Tests : `mvn -pl engine-b -am test`

Options : `-Dkafka.bootstrap=localhost:9098,localhost:9094,localhost:9096` (défaut), `-Dworkers=4`, `-Dwindow.seconds=5`, `-Dkeep.windows=120`, `-Dstart.from=earliest|latest`, `-Dtopic.logs=web-logs`, `-Dtopic.stats=stats-b`.

## Commandes utilisées pour tester
Envoyer des lignes de log :
```
docker exec -it soukline-kafka-1 /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka-1:19092 --topic web-logs
```
Lire les résultats :
```
docker exec -it soukline-kafka-1 /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka-1:19092 --topic stats-b
```

## Format de sortie (identique au Moteur A)
`{"engine":"B","windowStart":<ms>,"url":"/checkout","requests":2,"errors5xx":2}`
Les totaux sont cumulatifs : le dernier message d'une (fenêtre, url) fait foi.

## Résultats
**Tests unitaires** : `WindowCountersTest`, 8 tests, 0 échec : comptage par URL, 5xx vs 404, fenêtres de 5 s, republication des seules clés modifiées, 8 threads × 10 000 enregistrements = 80 000 exactement, purge des anciennes fenêtres, format JSON, échappement JSON.

**Test d'intégration** sur le cluster 3 brokers (5 lignes valides + 1 ligne cassée envoyées dans `web-logs`), dernier total reçu dans `stats-b` :

| URL | requests | errors5xx |
|---|---|---|
| `/produits/{id}` | 2 | 0 |
| `/checkout` | 2 | 2 |
| `/inexistant` | 1 | 0 |

- Le 404 n'est pas compté comme erreur 5xx.
- La ligne cassée est ignorée.
- Les valeurs sont les mêmes que celles du Moteur A.
- Une même URL peut apparaître deux fois (ex. `requests:1` puis `2`) quand les lignes tombent sur deux publications successives : le dernier total fait foi.

## Problèmes rencontrés et solutions
| Problème | Cause | Solution |
|---|---|---|
| Risque de compter une requête deux fois | Un groupe de consommateurs peut se rééquilibrer en cours de route | `consumer.assign` (partitions fixées à la main), pas de groupe |
| Compteurs faux avec plusieurs threads | Accès concurrents aux mêmes compteurs | `ConcurrentHashMap<String, AtomicLong>` ; test 8 threads × 10 000 = 80 000 exact |
| Workers pas d'accord sur la fin de fenêtre | Chaque worker lit à son rythme | `CyclicBarrier` alignée sur l'horloge : un seul worker publie, les autres attendent |
| Barrière bloquée si un worker s'arrête | `await` sans limite | `await(30 s)` puis `reset()` en cas de timeout ou de barrière cassée |
| Mémoire qui grossit | Une clé par (fenêtre, url) pour toujours | Purge : on garde 120 fenêtres (`-Dkeep.windows`) |
| Trop de messages dans `stats-b` | Republier toutes les clés à chaque fenêtre | On ne republie que les clés modifiées (totaux cumulatifs) |
| `Failed to load class StaticLoggerBinder` | `slf4j-simple` 2.0.x avec `slf4j-api` 1.7.x tiré par Kafka | Version explicite `slf4j-simple` **1.7.36** |
| `Could not find artifact logs:common:jar` | `common` non construit | `mvn -pl engine-b -am test` |
