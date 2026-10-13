# Infra Kafka, trafic JMeter, scripts

**Responsable :** Ali Nouar (Membre 2, intégrateur)

- `docker-compose` Kafka, topics `web-logs` (8 partitions), `stats-a`, `stats-b`
- Plan JMeter en 3 phases : normal, pic sur `/panier` et `/checkout`, panne
- `config.properties` partagé, scripts de lancement, README « comment lancer »

# SoukLine — Infrastructure Kafka, Tests et JMeter

## 1. Présentation du projet

**SoukLine** est un projet de traitement parallèle et en temps réel des logs d'un site web.

L'objectif est de collecter les requêtes HTTP effectuées par les visiteurs, de transmettre ces informations à Apache Kafka, puis de les analyser à l'aide de deux moteurs de traitement parallèles.

Le projet est réalisé en équipe dans le cadre de notre Master.

Ce dossier `infra/` contient la première version du prototype d'infrastructure, comprenant :

- Un cluster Apache Kafka composé de trois brokers.
- La configuration commune des topics Kafka.
- Des scripts PowerShell de création et de vérification de l'infrastructure.
- Des tests de messagerie, de réplication et de tolérance aux pannes.
- Des outils Java permettant de produire et de consommer un grand volume de messages Kafka.
- Un scénario Apache JMeter destiné à simuler les visiteurs du site web.

**État actuel :** l'infrastructure Kafka et ses tests sont opérationnels. L'intégration complète avec le site Spring Boot de Mohamed reste à effectuer.

---

## 2. Architecture générale du projet

Le fonctionnement prévu de SoukLine est le suivant :

```text
Visiteurs / Apache JMeter
            |
            v
    Site web Spring Boot
            |
            v
     Tomcat access.log
            |
            v
     Collecteur de logs
            |
            v
     Kafka : web-logs
            |
      +-----+-----+
      |           |
      v           v
   Engine A    Engine B
 Kafka Streams  Java Threads
      |           |
      v           v
   stats-a      stats-b
      |           |
      +-----+-----+
            |
            v
  Affichage et alertes
```

### Explication

1. **Apache JMeter** simule des visiteurs envoyant des requêtes HTTP au site.
2. **Spring Boot / Tomcat** reçoit ces requêtes et les enregistre dans un fichier `access.log`.
3. **Le collecteur** lit les nouveaux logs et les publie dans Kafka.
4. **Kafka** distribue les messages au moyen du topic `web-logs`.
5. **Engine A et Engine B** analysent les mêmes logs avec deux approches de parallélisme.
6. Les résultats sont publiés dans les topics `stats-a` et `stats-b`.
7. Un composant final récupère les statistiques pour les afficher et déclencher des alertes.

Notre partie concerne principalement **Kafka, JMeter et l'intégration de l'infrastructure**.

---

## 3. Organisation du dossier `infra/`

```text
infra/
|
|-- README.md
|-- docker-compose.yml
|-- config.properties
|
|-- scripts/
|   |-- create-topics.ps1
|   |-- check-kafka.ps1
|   |-- test-messaging.ps1
|   |-- test-replication.ps1
|   |-- test-failover.ps1
|
|-- performance-tests/
|   |-- pom.xml
|   |-- src/main/java/soukline/
|       |-- KafkaConfig.java
|       |-- KafkaLoadProducer.java
|       |-- KafkaLoadConsumer.java
|
|-- jmeter/
    |-- SoukLine-Web-Load-Test.jmx
```

Chaque élément a un rôle précis, expliqué ci-dessous.

---

## 4. Infrastructure Kafka — `docker-compose.yml`

Le fichier `docker-compose.yml` permet de démarrer notre infrastructure Kafka avec Docker Compose.

### Configuration réalisée

- **3 brokers Kafka** : `soukline-kafka-1`, `soukline-kafka-2` et `soukline-kafka-3`.
- **Apache Kafka 3.7.0**.
- Fonctionnement en mode **KRaft**, sans ZooKeeper.
- Ports accessibles depuis Windows : `9098`, `9094` et `9096`.
- Communication interne entre les brokers au moyen des ports Kafka configurés dans Docker.
- Limite de mémoire Java configurée à environ **512 Mo par broker**.

### Pourquoi trois brokers ?

Un broker Kafka est un serveur qui stocke et distribue les messages.

L'utilisation de trois brokers permet de :

- Répartir les partitions entre plusieurs serveurs.
- Répliquer les données pour améliorer leur disponibilité.
- Continuer à fonctionner lorsqu'un broker devient temporairement indisponible, dans les limites de la configuration de réplication.

### Pourquoi KRaft ?

KRaft permet à Kafka de gérer ses métadonnées sans dépendre d'un service ZooKeeper séparé.

Cela simplifie l'architecture de notre prototype.

### Commandes principales

Depuis le dossier `infra/` :

```powershell
docker compose up -d
```

Cette commande démarre les conteneurs en arrière-plan.

Pour consulter leur état :

```powershell
docker compose ps
```

Pour arrêter les conteneurs :

```powershell
docker compose down
```

**Attention :** ne pas utiliser `docker compose down -v` sans raison, car cette commande peut supprimer les volumes de données Kafka.

---

## 5. Configuration partagée — `config.properties`

Ce fichier centralise les paramètres Kafka utilisés par les différents composants.

```properties
bootstrap.servers=localhost:9098,localhost:9094,localhost:9096

topic.logs=web-logs
topic.stats.a=stats-a
topic.stats.b=stats-b

producer.acks=all
consumer.auto.offset.reset=earliest

topic.partitions=8
topic.replication.factor=3
```

### Explication des propriétés

**`bootstrap.servers`**

Contient les adresses des trois brokers Kafka accessibles depuis la machine locale.

Un client Kafka peut utiliser ces adresses pour découvrir le cluster.

**`topic.logs=web-logs`**

Topic destiné à recevoir les logs HTTP du site.

**`topic.stats.a=stats-a`**

Topic prévu pour les statistiques calculées par Engine A.

**`topic.stats.b=stats-b`**

Topic prévu pour les statistiques calculées par Engine B.

**`producer.acks=all`**

Demande au producteur d'attendre l'acquittement des réplicas synchronisés requis avant de considérer un message comme confirmé.

Cette configuration améliore la fiabilité de l'envoi.

**`consumer.auto.offset.reset=earliest`**

Permet à un consommateur qui ne possède pas encore de position enregistrée de commencer à lire depuis les premiers messages encore disponibles.

**`topic.partitions=8`**

Chaque topic est configuré avec huit partitions.

Les partitions permettent de répartir les messages et de faciliter leur traitement parallèle.

**`topic.replication.factor=3`**

Chaque partition possède trois copies réparties entre les brokers.

La réplication améliore la disponibilité des données.

---

## 6. Scripts PowerShell de validation

Le dossier `scripts/` contient les scripts permettant de préparer et de vérifier le cluster Kafka.

### 6.1. `create-topics.ps1`

**Objectif :** créer les topics nécessaires au projet.

Topics concernés :

- `web-logs`
- `stats-a`
- `stats-b`

Chaque topic est configuré avec :

- **8 partitions**
- **Facteur de réplication : 3**

Ce script permet de préparer l'infrastructure avant le démarrage des autres composants.

### 6.2. `check-kafka.ps1`

**Objectif :** vérifier que l'infrastructure Kafka est accessible et correctement configurée.

Il sert à contrôler l'état général du cluster et à repérer les problèmes de configuration avant les tests.

### 6.3. `test-messaging.ps1`

**Objectif :** vérifier le fonctionnement de la communication Kafka.

Le test utilise **100 messages distincts**.

Il permet de vérifier que les messages peuvent être envoyés et récupérés par un consommateur.

### 6.4. `test-replication.ps1`

**Objectif :** vérifier la réplication des partitions Kafka.

Avec trois topics de huit partitions chacun :

```text
3 topics × 8 partitions = 24 partitions
```

Avec trois réplicas par partition :

```text
24 partitions × 3 réplicas = 72 réplicas attendus
```

Le script permet de contrôler que les partitions disposent des réplicas synchronisés attendus.

### 6.5. `test-failover.ps1`

**Objectif :** tester la tolérance aux pannes du cluster.

Le scénario consiste à :

1. Arrêter temporairement le broker Kafka numéro 2.
2. Vérifier que le cluster continue à accepter les opérations prévues.
3. Envoyer et recevoir **20 messages** pendant cette interruption.
4. Redémarrer le broker arrêté.

Ce test vérifie le comportement de l'infrastructure lorsqu'un broker devient indisponible.

### Résultat de la première validation

Les scripts de validation de l'infrastructure ont été exécutés avec succès pendant la préparation du prototype.

Ils devront être relancés lors des tests d'intégration du projet complet.

---

## 7. Tests Java de charge Kafka

Le dossier `performance-tests/` contient un petit projet Java indépendant destiné à tester la capacité de Kafka à recevoir et à restituer un grand nombre de messages.

Il utilise :

- **Java 17**
- **Apache Maven**
- **Kafka Clients 3.7.0**

### 7.1. `pom.xml`

Ce fichier définit les dépendances Maven et la configuration de compilation du projet Java.

Il permet notamment d'utiliser la bibliothèque officielle des clients Kafka.

### 7.2. `KafkaConfig.java`

Cette classe regroupe la configuration commune utilisée par les programmes Java de test.

Son rôle est d'éviter de répéter les mêmes paramètres de connexion dans le producteur et le consommateur.

### 7.3. `KafkaLoadProducer.java`

Cette classe joue le rôle de **producteur Kafka**.

Elle génère des messages identifiables et les envoie dans le topic `web-logs`.

Format des messages de test :

```text
SOUKLINE-LOAD|identifiant-du-test|numero-du-message
```

Chaque message contient :

- Un préfixe indiquant qu'il s'agit d'un message de test.
- Un identifiant permettant de distinguer plusieurs exécutions.
- Un numéro de séquence permettant de vérifier les messages reçus.

Le producteur mesure notamment :

- Le nombre de messages envoyés.
- Le nombre de messages confirmés.
- Les erreurs éventuelles.
- La durée de l'envoi.
- Le débit de production.
- Certaines statistiques de latence.

### 7.4. `KafkaLoadConsumer.java`

Cette classe joue le rôle de **consommateur Kafka**.

Elle récupère les messages correspondant à un identifiant de test et vérifie :

- Le nombre de messages uniques reçus.
- Les messages manquants.
- Les doublons.
- Les messages invalides.
- La durée de lecture.

Ce contrôle permet de vérifier la cohérence des messages reçus après un test de charge.

### 7.5. Résultats obtenus

Deux volumes ont été testés.

**Test de 1 000 messages**

- Production réussie.
- Consommation réussie.
- Aucun message manquant.
- Aucun doublon.

**Test de 100 000 messages**

| Mesure | Résultat |
|---|---|
| Messages confirmés par le producteur | 100 000 |
| Erreurs de production | 0 |
| Durée de production | 2,618 secondes |
| Débit de production observé | Environ 38 202 messages/s |
| Latence moyenne observée | Environ 1 090 ms |
| Latence P95 | Environ 1 452 ms |
| Latence P99 | Environ 1 462 ms |
| Messages uniques vérifiés par le consommateur | 100 000 |
| Messages manquants | 0 |
| Doublons | 0 |
| Messages invalides | 0 |
| Durée de lecture observée | 2,097 secondes |
| Débit de lecture observé | Environ 47 693 messages/s |

Les deux programmes ont terminé avec `BUILD SUCCESS`.

**Interprétation :** ces résultats confirment le bon fonctionnement des outils de test sur notre environnement local. Ils constituent une première mesure indicative, et non un benchmark scientifique définitif du système complet.

En particulier, la lecture peut porter sur des messages déjà présents dans Kafka : les débits de production et de consommation ne doivent donc pas être interprétés comme une mesure simultanée du pipeline de bout en bout.

---

## 8. Simulation des visiteurs avec Apache JMeter

Le dossier `jmeter/` contient :

```text
SoukLine-Web-Load-Test.jmx
```

Ce fichier représente notre premier scénario de test HTTP.

### Pourquoi utiliser JMeter ?

Apache JMeter permet de simuler plusieurs utilisateurs qui visitent un site web.

Dans SoukLine, il servira à générer du trafic HTTP pour que Tomcat produise des lignes dans le fichier `access.log`.

Ces logs seront ensuite collectés et envoyés dans Kafka.

### Configuration initiale

| Paramètre | Valeur |
|---|---|
| Outil | Apache JMeter 5.6.3 |
| Utilisateurs virtuels | 10 |
| Ramp-up | 10 secondes |
| Nombre de boucles | 5 |
| Méthode HTTP | GET |
| Adresse provisoire | `http://localhost:8080/` |

### Structure du scénario

```text
Test Plan
|
|-- SoukLine - Visiteurs Web
    |
    |-- SoukLine - GET Homepage
    |
    |-- SoukLine - HTTP Results
    |
    |-- SoukLine - Summary Report
```

**Thread Group — SoukLine - Visiteurs Web**

Définit le nombre d'utilisateurs virtuels, la vitesse à laquelle ils démarrent et le nombre de répétitions.

**HTTP Request — SoukLine - GET Homepage**

Envoie une requête HTTP GET vers la page d'accueil du site.

**View Results Tree — SoukLine - HTTP Results**

Permet de consulter les requêtes exécutées et les réponses reçues, notamment pour diagnostiquer les erreurs.

**Summary Report — SoukLine - Summary Report**

Affiche des statistiques globales sur les requêtes : nombre d'échantillons, temps de réponse, erreurs et débit.

### État actuel de JMeter

Le scénario est créé et sauvegardé.

Cependant, **le test HTTP réel du site SoukLine n'a pas encore été validé**, car nous attendons les routes définitives et la disponibilité du site Spring Boot développé par Mohamed.

L'adresse `localhost:8080` reste donc provisoire.

---

## 9. Tests réalisés et limites actuelles

### Éléments validés

- Démarrage du cluster Kafka local à trois brokers.
- Création et vérification des topics.
- Vérification de l'envoi et de la réception de messages.
- Vérification de la réplication.
- Test de tolérance à l'arrêt temporaire d'un broker.
- Tests Java de 1 000 et 100 000 messages.
- Vérification de l'absence de pertes et de doublons lors de ces tests.
- Création et sauvegarde du plan JMeter.
- Publication du prototype sur la branche GitHub dédiée.

### Éléments restant à valider

- Accessibilité du site web Spring Boot.
- Routes HTTP définitives.
- Génération du véritable fichier Tomcat `access.log`.
- Lecture des logs par le collecteur Java.
- Publication des vrais logs HTTP dans `web-logs`.
- Traitement par les moteurs A et B.
- Réception des statistiques dans `stats-a` et `stats-b`.
- Affichage final et alertes.
- Test de charge et mesure de performance du pipeline complet.

Il est important de distinguer **les tests d'infrastructure déjà réussis** des **tests d'intégration de bout en bout qui restent à réaliser**.

---

## 10. Prochaines étapes d'intégration

Lorsque le site Spring Boot sera disponible :

1. Récupérer la dernière version du site de Mohamed.
2. Confirmer son port et ses routes HTTP.
3. Adapter le fichier JMeter aux véritables pages et endpoints.
4. Démarrer le site et vérifier la génération de `access.log`.
5. Vérifier que le collecteur transmet les logs vers Kafka.
6. Vérifier que les moteurs A et B consomment les logs.
7. Contrôler les statistiques produites.
8. Exécuter un scénario JMeter représentatif.
9. Vérifier la stabilité du système complet et documenter les résultats.

---

## 11. Gestion Git et collaboration

Le développement de cette partie est effectué sur la branche :

```text
feature/ali-nouar
```

Le premier commit du prototype est :

```text
bad6251
```

Message du commit :

```text
feat(infra): initial SoukLine prototype with Kafka cluster, tests and JMeter setup pending website integration
```

Le code a été envoyé sur GitHub sans modifier directement la branche principale `main`.

Les futures modifications pourront être proposées à l'équipe par une Pull Request.

---

## 12. Conclusion

Cette première version de SoukLine fournit une infrastructure Kafka locale fonctionnelle et plusieurs outils permettant d'en vérifier la communication, la réplication et la résistance à une panne de broker.

Les tests Java ont permis de confirmer l'envoi et la réception de 100 000 messages sans perte ni doublon observés.

Un premier scénario Apache JMeter a également été préparé pour simuler le trafic des visiteurs.

**La prochaine phase consistera à connecter cette infrastructure au site Spring Boot de Mohamed, puis à valider le traitement complet des logs web en streaming.**