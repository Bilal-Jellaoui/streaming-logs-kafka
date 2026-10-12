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