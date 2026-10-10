# Moteur A : Kafka Streams

**Responsable :** Bilal Jellaoui (Membre 3)

Topologie : `web-logs` → parsing → `selectKey(url)` → `groupByKey` → fenêtres de 5 s → `aggregate` (requêtes + erreurs 5xx) → JSON dans `stats-a`.

Format JSON commun avec le Moteur B : `{engine, windowStart, url, requests, errors5xx}`.
