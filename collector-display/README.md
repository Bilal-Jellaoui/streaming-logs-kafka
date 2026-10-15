# Collecteur de logs, affichage console, alerte

**Responsable :** Youssef Benayad (Membre 5)

- Collecteur Java : lit `access.log` en continu (comme `tail -f`) et publie dans `web-logs`
- Affichage console en direct : top URLs, requêtes/s, erreurs 5xx, Moteur A et B côte à côte
- Alerte si le taux de 5xx dépasse un seuil

## Les deux programmes

| Programme | Rôle | Lit | Écrit |
|---|---|---|---|
| `logs.collector.LogCollector` | Suit `access.log` et envoie chaque ligne telle quelle (les moteurs font le parsing) | `logs/access.log` | topic `web-logs` |
| `logs.collector.DisplayApp` | Tableau de bord console + alerte | topics `stats-a` et `stats-b` | la console |

Classes de support (sans Kafka, donc testables avec de simples fichiers) :
`LogTailer` (le `tail -f`), `StatsJson` (lit le JSON `{engine, windowStart, url, requests, errors5xx}`),
`StatsStore` (mémoire des derniers résultats par moteur), `AlertMonitor` (seuil), `Dashboard` (mise en forme).

## Lancer

Depuis IntelliJ (*Run* sur la classe `main`, options à mettre dans *VM options*). Kafka doit tourner (voir `infra/`).

```
LogCollector :  -Dlog.file=logs/access.log
DisplayApp   :  -Dalert.threshold=10
```

Le collecteur peut démarrer avant le site : tant que `access.log` n'existe pas, il attend, puis lit tout le contenu du fichier dès qu'il apparaît.

### Réglages (valeurs par défaut)

| Option `-D` | Défaut | Programme | Rôle |
|---|---|---|---|
| `kafka.bootstrap` | `localhost:9098,localhost:9094,localhost:9096` | les deux | brokers Kafka (même défaut que le Moteur A) |
| `log.file` (ou 1er argument) | `logs/access.log` | collecteur | fichier à suivre |
| `topic.logs` | `web-logs` | collecteur | topic d'entrée |
| `collector.fromStart` | `false` | collecteur | `true` : relire aussi le contenu déjà présent au démarrage |
| `poll.ms` | `200` | collecteur | pause quand il n'y a rien de neuf |
| `window.seconds` | `5` | affichage | **doit être égal à celui des moteurs** |
| `display.windows` | `3` | affichage | nombre de fenêtres terminées affichées (3 x 5 s = 15 s) |
| `display.top` | `8` | affichage | nombre d'URL dans le classement |
| `refresh.seconds` | `2` | affichage | fréquence d'affichage |
| `alert.threshold` | `10.0` | affichage | seuil d'alerte, en % de requêtes 5xx |
| `alert.minRequests` | `10` | affichage | pas d'alerte en dessous de ce nombre de requêtes |
| `display.clear` | `false` | affichage | `true` : efface l'écran à chaque affichage (vrai terminal seulement, pas la console IntelliJ) |

## Comment l'affichage calcule ses chiffres

- Un moteur republie une URL d'une fenêtre à chaque mise à jour, avec le **total à jour**. L'affichage garde donc le **dernier** message par (moteur, fenêtre, URL) et ne les additionne pas.
- On additionne les `display.windows` dernières fenêtres **terminées** ; la fenêtre en cours, incomplète, est exclue pour ne pas fausser le débit.
- `requêtes/s` = total de la période / durée de la période.
- **Alerte** : le taux de 5xx du Moteur A dépasse le seuil (repli sur le Moteur B si A n'a rien reçu), avec au moins `alert.minRequests` requêtes pour éviter les fausses alertes quand il y a peu de trafic. Un bip est émis au passage de OK à ALERTE.

## Tests

34 tests unitaires JUnit (`mvn -pl collector-display -am test`) :
`LogTailerTest` (8 : nouvelles lignes seulement, ligne incomplète, `\r\n`, accent coupé entre deux lectures, fichier tronqué, fichier absent puis créé, rotation), `StatsJsonTest` (7), `StatsStoreTest` (8), `AlertMonitorTest` (6), `DashboardTest` (5).

Les deux programmes `main` (`LogCollector`, `DisplayApp`) parlent à Kafka : ils se testent à la main avec le cluster de `infra/`.

## Contrats avec les autres membres

| Avec | Ce que ce module suppose |
|---|---|
| Mohamed (site) | Le site écrit au format Tomcat *common* dans `logs/access.log` |
| Ali (infra) | Topics `web-logs`, `stats-a`, `stats-b` ; mêmes brokers que dans `infra/config.properties` |
| Bilal / Ayoub (moteurs) | JSON `{engine, windowStart, url, requests, errors5xx}` ; chaque message contient le **total à jour** de l'URL dans la fenêtre (pas un incrément). Fenêtres alignées sur l'heure (multiples de `window.seconds`) |

## Problèmes rencontrés et solutions

| Problème | Solution |
|---|---|
| Une ligne écrite à moitié (sans `\n`) serait envoyée coupée en deux | `LogTailer` garde le début de ligne en mémoire jusqu'au retour à la ligne |
| Fichier absent au démarrage : les premières lignes étaient sautées comme si c'était de l'ancien contenu | Un fichier qui apparaît après le démarrage est lu depuis le début |
| Fichier vidé ou remplacé pendant que le collecteur tourne | Taille plus petite que la position lue (ou identité du fichier changée) : on repart du début |
| Pas de bibliothèque JSON dans le projet | Le format est plat : `StatsJson` utilise des expressions régulières, ordre des champs libre |
| Fausse alerte avec très peu de trafic (1 erreur sur 2 requêtes = 50 %) | Minimum de requêtes (`alert.minRequests`) avant de juger |
