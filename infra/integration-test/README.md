# Tests d'intégration — SoukLine

## 1. Objectif

Ce dossier documente les tests d'intégration réalisés pour le projet **Traitement parallèle en streaming de logs Web — SoukLine**, dans le cadre du module *Calcul parallèle et systèmes distribués*.

L'objectif est de vérifier le bon fonctionnement du pipeline de traitement des logs HTTP, depuis leur génération par le site Spring Boot jusqu'à leur agrégation par deux moteurs de traitement parallèles :

- **Moteur A :** Kafka Streams
- **Moteur B :** Java Concurrency (`ExecutorService`, `ConcurrentHashMap`, `AtomicLong`, `CyclicBarrier`)

Les tests permettent également de vérifier que les deux moteurs produisent des statistiques identiques à partir des mêmes événements.

## 2. Architecture testée

```text
                 Site SoukLine
                  Spring Boot
                       |
                       v
              site/logs/access.log
                       |
                       v
                Collecteur Java
                       |
                       v
                Kafka : web-logs
                  8 partitions
                       |
              +--------+--------+
              |                 |
              v                 v
         Moteur A           Moteur B
       Kafka Streams     Java Concurrency
              |                 |
              v                 v
         stats-a             stats-b
              |                 |
              +--------+--------+
                       |
                       v
             Comparaison des résultats
```

## 3. Environnement de test

| Composant | Configuration |
|---|---|
| Système | Windows / PowerShell |
| Langage | Java |
| Gestionnaire de projet | Maven |
| Conteneurisation | Docker Compose |
| Kafka | Apache Kafka 3.7.0 |
| Cluster Kafka | 3 brokers, mode KRaft |
| Broker 1 | localhost:9098 |
| Broker 2 | localhost:9094 |
| Broker 3 | localhost:9096 |
| Topic d'entrée | web-logs |
| Topics de sortie | stats-a, stats-b |
| Site Web | Spring Boot, port 8080 |
| Fenêtre d'agrégation | 5 secondes |

Les tests ont été réalisés le **17 octobre 2026**.

## 4. Prérequis

Avant de lancer les tests :

1. Docker Desktop doit être démarré.
2. Java et Maven doivent être installés.
3. Les trois brokers Kafka doivent fonctionner.
4. Les topics `web-logs`, `stats-a` et `stats-b` doivent exister.
5. Les moteurs A et B doivent être compilés.

Toutes les commandes suivantes sont exécutées depuis la racine du projet.

### 4.1 Démarrer Kafka

```powershell
docker compose -f infra/docker-compose.yml up -d
```

Vérifier les conteneurs :

```powershell
docker compose -f infra/docker-compose.yml ps
```

### 4.2 Vérifier les topics

```powershell
docker exec soukline-kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka-1:19092 --list
```

Topics attendus :

```text
web-logs
stats-a
stats-b
```

## 5. Démarrage des composants

Chaque composant doit être exécuté dans un terminal PowerShell distinct.

### 5.1 Moteur A — Kafka Streams

```powershell
mvn -pl engine-a -am install -DskipTests
mvn -f .\engine-a\pom.xml exec:java "-Dexec.mainClass=logs.enginea.EngineA"
```

État attendu :

```text
[Moteur A] état : REBALANCING -> RUNNING
```

### 5.2 Moteur B — Java Concurrency

```powershell
mvn -f .\engine-b\pom.xml exec:java "-Dexec.mainClass=logs.engineb.EngineB"
```

Le moteur B consomme les événements du topic `web-logs` et publie ses agrégations dans `stats-b`.

### 5.3 Site SoukLine

```powershell
mvn -f .\site\pom.xml spring-boot:run
```

Adresse :

```text
http://localhost:8080/
```

Les requêtes HTTP sont enregistrées dans :

```text
site/logs/access.log
```

### 5.4 Collecteur Java

```powershell
mvn -f .\collector-display\pom.xml exec:java "-Dexec.mainClass=logs.collector.LogCollector" "-Dexec.args=site/logs/access.log"
```

Le collecteur surveille les nouvelles lignes du fichier et les transmet au topic `web-logs`.

Exemple de confirmation :

```text
[Collecteur] 5 lignes envoyées, 0 échecs
```

**Remarque :** le collecteur lit uniquement les nouvelles lignes ajoutées après son démarrage. Il ne faut pas lancer plusieurs instances pour surveiller simultanément le même fichier pendant ce test.

## 6. Test 1 — Injection contrôlée dans Kafka

### Objectif

Vérifier que les deux moteurs calculent correctement le nombre de requêtes HTTP et le nombre d'erreurs serveur 5xx.

### Procédure

Injecter cinq logs HTTP directement dans le topic `web-logs` :

- 3 requêtes avec le code HTTP `200`.
- 2 requêtes avec le code HTTP `500`.
- Même URL : `/test-ali-integration`.
- Même horodatage pour placer les événements dans une fenêtre de 5 secondes.

Commande PowerShell :

```powershell
$timestamp = Get-Date -Format "dd/MMM/yyyy:HH:mm:ss"
$logDate = "$timestamp +0000"

$logs = @(
    "127.0.0.1 - - [$logDate] `"GET /test-ali-integration HTTP/1.1`" 200 1024"
    "127.0.0.1 - - [$logDate] `"GET /test-ali-integration HTTP/1.1`" 200 1024"
    "127.0.0.1 - - [$logDate] `"GET /test-ali-integration HTTP/1.1`" 200 1024"
    "127.0.0.1 - - [$logDate] `"GET /test-ali-integration HTTP/1.1`" 500 1024"
    "127.0.0.1 - - [$logDate] `"GET /test-ali-integration HTTP/1.1`" 500 1024"
)

$logs | docker exec -i soukline-kafka-1 /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka-1:19092 --topic web-logs
```

### Résultats obtenus

**Moteur A :**

```json
{"engine":"A","windowStart":1792269770000,"url":"/test-ali-integration","requests":5,"errors5xx":2}
```

**Moteur B :**

```json
{"engine":"B","windowStart":1792269770000,"url":"/test-ali-integration","requests":5,"errors5xx":2}
```

| Critère | Attendu | Moteur A | Moteur B |
|---|---:|---:|---:|
| Nombre de requêtes | 5 | 5 | 5 |
| Erreurs HTTP 5xx | 2 | 2 | 2 |
| Fenêtre temporelle identique | Oui | Oui | Oui |

**Résultat : TEST RÉUSSI.**

Les deux moteurs produisent des statistiques identiques pour ce scénario.

## 7. Test 2 — Pipeline complet avec le site SoukLine

### Objectif

Vérifier la chaîne complète :

```text
Spring Boot -> access.log -> Collecteur -> Kafka -> Moteurs A/B
```

Contrairement au premier test, les logs sont ici générés par de vraies requêtes HTTP.

### Procédure

**Étape 1 :** démarrer Kafka, le site, le collecteur et les deux moteurs.

**Étape 2 :** envoyer cinq requêtes vers le catalogue :

```powershell
1..5 | ForEach-Object {
    curl.exe -s -o NUL http://localhost:8080/produits
}
```

**Étape 3 :** vérifier les nouvelles lignes du fichier :

```powershell
Get-Content .\site\logs\access.log -Tail 10
```

**Étape 4 :** vérifier la transmission par le collecteur :

```text
[Collecteur] 5 lignes envoyées, 0 échecs
```

**Étape 5 :** consulter les statistiques des deux moteurs.

Moteur A :

```powershell
docker exec soukline-kafka-1 /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka-1:19092 --topic stats-a --from-beginning --timeout-ms 5000 | Select-String '"url":"/produits"'
```

Moteur B :

```powershell
docker exec soukline-kafka-1 /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka-1:19092 --topic stats-b --from-beginning --timeout-ms 5000 | Select-String '"url":"/produits"'
```

### Résultats obtenus

**Moteur A :**

```json
{"engine":"A","windowStart":1792270115000,"url":"/produits","requests":5,"errors5xx":0}
```

**Moteur B :**

```json
{"engine":"B","windowStart":1792270115000,"url":"/produits","requests":5,"errors5xx":0}
```

| Critère | Moteur A | Moteur B |
|---|---:|---:|
| URL | /produits | /produits |
| Requêtes HTTP | 5 | 5 |
| Erreurs 5xx | 0 | 0 |
| Fenêtre temporelle | 1792270115000 | 1792270115000 |

**Résultat : TEST RÉUSSI.**

Les cinq requêtes générées par le site ont été enregistrées, transmises à Kafka et agrégées correctement par les deux moteurs.

## 8. Tests unitaires

Commande utilisée :

```powershell
mvn -pl engine-a,engine-b -am test
```

Résultats :

| Module | Tests réussis |
|---|---:|
| common | 9 |
| engine-a | 5 |
| engine-b | 8 |
| **Total** | **22** |

Résultat Maven :

```text
BUILD SUCCESS
```

## 9. Remarques techniques

### Timeout du consommateur Kafka

Lors de la consultation des résultats avec `--timeout-ms 5000`, le consommateur peut afficher :

```text
org.apache.kafka.common.errors.TimeoutException
```

Ce message indique que le délai d'attente configuré a expiré. Il ne signifie pas que les moteurs de traitement ont échoué.

### Fenêtres temporelles

Les moteurs agrègent les logs dans des fenêtres de 5 secondes.

Des requêtes envoyées dans des fenêtres différentes peuvent donc produire plusieurs résultats pour la même URL.

### Historique Kafka

L'option `--from-beginning` permet de relire les messages déjà présents dans un topic.

Il faut donc comparer les résultats en utilisant à la fois :

- L'URL.
- La fenêtre temporelle (`windowStart`).
- Les compteurs `requests` et `errors5xx`.

## 10. Conclusion

Les tests d'intégration réalisés ont permis de valider :

- Le fonctionnement du cluster Kafka à trois brokers.
- La génération de logs HTTP par le site SoukLine.
- La lecture du fichier `access.log` par le collecteur Java.
- La transmission des événements au topic `web-logs`.
- Le traitement des événements par Kafka Streams (moteur A).
- Le traitement concurrent des événements par Java (moteur B).
- La publication des statistiques dans `stats-a` et `stats-b`.
- La correspondance des résultats des deux moteurs pour les scénarios testés.

**Bilan : les deux tests d'intégration ont été réussis.**

Ces résultats valident le fonctionnement fonctionnel du pipeline sur les scénarios exécutés. Ils ne constituent pas, à eux seuls, une mesure comparative des performances sous forte charge ou de la tolérance aux pannes.

Les tests de charge JMeter et les benchmarks de performances sont documentés séparément dans les modules correspondants du projet.