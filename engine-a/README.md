# Moteur A : Kafka Streams

**Responsable :** Bilal Jellaoui (Membre 3)

Compte les requêtes et les erreurs 5xx par URL et par fenêtre de 5 s, en lisant `web-logs` et en publiant dans `stats-a`.

**Base de temps :** les fenêtres suivent l'heure écrite dans la ligne de log (pas l'heure d'envoi dans Kafka), comme dans le Moteur B.

Topologie : `web-logs` → parsing (`common`) → `selectKey(url)` → `groupByKey` → fenêtres de 5 s → `aggregate` (requêtes + erreurs 5xx) → JSON dans `stats-a`.

Format JSON commun avec le Moteur B : `{"engine":"A","windowStart":<ms>,"url":"/checkout","requests":2,"errors5xx":2}`.
Les totaux sont cumulatifs : le dernier message d'une (fenêtre, url) fait foi.

## Technologies utilisées
| Technologie | Rôle |
|---|---|
| Java 17 | Langage |
| Apache Kafka 3.7.0 (KRaft, 3 brokers, Docker) | Transport des logs (`web-logs` → `stats-a`) |
| Kafka Streams | Agrégation par fenêtre (`TimeWindows` 5 s, grace 5 s) |
| Module `common` (`LogParser`, `LogEntry`) | Lecture des lignes de log Tomcat |
| SLF4J (`slf4j-simple` 1.7.36) | Journaux |
| Maven (multi-modules) + JUnit 5 | Build et tests |
| GitHub Actions | CI (`mvn test`) |

Réglages principaux : fenêtres de 5 s, `commit.interval.ms = 1000`, `replication.factor = 3` pour les topics internes.

## Lancer
1. Démarrer le cluster (voir `infra/`) : `docker start soukline-kafka-1 soukline-kafka-2 soukline-kafka-3`
2. Lancer `logs.enginea.EngineA` (IntelliJ).
   Attendre le message `[Moteur A] état : REBALANCING -> RUNNING` : le moteur est prêt.
3. Tests : `mvn -pl engine-a -am test`

Options : `-Dkafka.bootstrap=localhost:9098,localhost:9094,localhost:9096` (défaut). Pour un seul broker : `-Dkafka.bootstrap=localhost:9092 -Dkafka.replication=1`.

## Commandes utilisées pour tester
Envoyer des lignes de log (producteur console) :
```
docker exec -it soukline-kafka-1 /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka-1:19092 --topic web-logs
```
Lire les résultats (consommateur console) :
```
docker exec -it soukline-kafka-1 /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka-1:19092 --topic stats-a
```
Test de panne :
```
docker stop soukline-kafka-2
docker start soukline-kafka-2
```

## Résultats
Test d'intégration avec `exemple-logs.txt` (fenêtre de 5 s), identique sur Kafka local et sur le cluster 3 brokers :

| URL | requests | errors5xx |
|---|---|---|
| `/checkout` | 3 | 2 |
| `/produits/{id}` | 1 | 0 |
| `/produits` | 1 | 0 |
| `/static` | 1 | 0 |
| `/` | 1 | 0 |

- `/produits/12` est normalisé en `/produits/{id}`.
- Les fichiers css/js sont regroupés en `/static`.
- La ligne mal formée est ignorée.

**Test de panne :** broker 2 arrêté, Moteur A continue et produit les mêmes résultats dans `stats-a`. Après redémarrage du broker, tous les ISR reviennent à 3.

**Parser validé sur de vrais logs Tomcat** (IPv6 `0:0:0:0:0:0:0:1`, POST/302, octets `-`).

## Problèmes rencontrés et solutions
| Problème | Cause | Solution |
|---|---|---|
| `Could not find artifact logs:common:jar` avec `mvn -pl engine-a test` | Le module `common` n'était pas construit | Ajouter `-am` : `mvn -pl engine-a -am test` |
| `Failed to load class StaticLoggerBinder` (logs muets) | `slf4j-simple` 2.0.x (géré par le parent) avec `slf4j-api` 1.7.x tiré par Kafka | Version explicite `slf4j-simple` **1.7.36** dans `engine-a/pom.xml` |
| Les vrais logs Tomcat produisaient des URL différentes à chaque session | Paramètre de session `;SOUKLINE_SESSION=...` dans le chemin | `LogParser.normalize` retire tout ce qui suit `;` (test ajouté) |
| Pas de résultat au démarrage | Le Streams passe par `REBALANCING` avant `RUNNING` | Attendre `REBALANCING -> RUNNING` dans la console avant d'envoyer des lignes |
| Moteur A et Moteur B pas dans les mêmes fenêtres de 5 s | Moteur A utilisait l'heure d'**envoi** du message dans Kafka, le Moteur B l'heure écrite dans le **log** | `LogTimeExtractor` : le Moteur A lit lui aussi l'heure du log avec `LogParser` (2 tests ajoutés) |
| Une URL apparaît plusieurs fois dans `stats-a` (ex. `requests:1` puis `2`) | Chaque nouvelle requête republie le total de la fenêtre | Comportement voulu : le dernier message fait foi (l'affichage garde le dernier par moteur, fenêtre, url) |
`docker stop soukline-kafka-2`), logs renvoyés : Moteur A continue et produit les mêmes résultats dans `stats-a`. Après redémarrage du broker, tous les ISR reviennent à 3.ukline-kafka-2`), logs renvoyés : Moteur A continue et produit les mêmes résultats dans `stats-a`. Après redémarrage du broker, tous les ISR reviennent à 3.