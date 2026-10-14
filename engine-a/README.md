# Moteur A : Kafka Streams

**Responsable :** Bilal Jellaoui (Membre 3)

Topologie : `web-logs` → parsing → `selectKey(url)` → `groupByKey` → fenêtres de 5 s → `aggregate` (requêtes + erreurs 5xx) → JSON dans `stats-a`.

Format JSON commun avec le Moteur B : `{engine, windowStart, url, requests, errors5xx}`.
## Test d'intégration (Kafka local)

Testé sur un Kafka local (Docker, `apache/kafka:3.7.0`).
Le `docker-compose.yml` officiel est fourni par le module infra.

Résultat avec `exemple-logs.txt` (fenêtre de 5 s) :

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

## Test sur cluster Kafka (3 brokers)

Testé avec le `docker-compose.yml` du module infra (3 brokers, KRaft, `min.insync.replicas=2`).

- Par défaut, Moteur A se connecte à `localhost:9098,localhost:9094,localhost:9096` avec `replication.factor=3` pour ses topics internes.
- Pour un seul broker : `-Dkafka.bootstrap=localhost:9092 -Dkafka.replication=1`.

**Résultat :** identique au test local (`/checkout` : 3 requêtes dont 2 erreurs 5xx, etc.).

**Test de panne :** broker 2 arrêté (`docker stop soukline-kafka-2`), logs renvoyés : Moteur A continue et produit les mêmes résultats dans `stats-a`. Après redémarrage du broker, tous les ISR reviennent à 3.