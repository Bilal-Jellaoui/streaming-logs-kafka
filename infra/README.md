# Infra Kafka, trafic JMeter, scripts

**Responsable :** Ali Nouar (Membre 2, intégrateur)

**Consignes de la répartition du travail — conservées :**

- `docker-compose` Kafka, topics `web-logs` (8 partitions), `stats-a`, `stats-b`.
- Plan JMeter en 3 phases : trafic normal, pic sur `/panier` et `/checkout`, panne.
- `config.properties` partagé, scripts de lancement, README « comment lancer ».

---

# SoukLine — Infrastructure Kafka, tests et JMeter

## 1. Présentation du projet

**SoukLine** est un projet de traitement parallèle en streaming des logs d'un site web. Les requêtes HTTP des visiteurs sont enregistrées dans `access.log`, puis doivent être publiées dans Apache Kafka et analysées par deux moteurs de traitement parallèles.

Ce dossier `infra/` contient :

- Un cluster Kafka local de **3 brokers**, en mode **KRaft**.
- La configuration des topics et des clients Kafka.
- Des scripts PowerShell de création, vérification, messagerie, réplication et tolérance aux pannes.
- Des outils Java de charge Kafka (producteur et consommateur).
- Un plan JMeter simulant trois phases de trafic HTTP sur le **site réel SoukLine**.

**État vérifié au 14 octobre 2026 :** le cluster Kafka, les tests Kafka et les trois scénarios JMeter ont été exécutés avec succès. Le site Spring Boot génère de vrais logs Tomcat, dont **5 000 réponses HTTP 503** observées pendant la simulation de panne. **La transmission de ces logs vers Kafka par le collecteur, puis leur traitement par les moteurs, n'ont pas encore été validés de bout en bout.**

## 2. Architecture générale

```text
Visiteurs / Apache JMeter
          |
          v
Site web Spring Boot (localhost:8080)
          |
          v
site/logs/access.log (Tomcat)
          |
          v
Collecteur de logs (module d'équipe ; intégration à valider)
          |
          v
Kafka : web-logs (8 partitions)
          |
      +---+---+
      |       |
      v       v
  Engine A  Engine B
      |       |
      v       v
   stats-a  stats-b
      |       |
      +---+---+
          |
          v
Affichage et alertes
```

**Périmètre de ce dossier :** configuration et validation de Kafka, génération de trafic JMeter et préparation des tests d'intégration. Le collecteur, les moteurs et l'affichage sont développés par d'autres membres de l'équipe.

## 3. Organisation du dossier `infra/`

```text
infra/
|-- README.md
|-- docker-compose.yml
|-- config.properties
|-- scripts/
|   |-- create-topics.ps1
|   |-- check-kafka.ps1
|   |-- test-messaging.ps1
|   |-- test-replication.ps1
|   `-- test-failover.ps1
|-- performance-tests/
|   |-- pom.xml
|   `-- src/main/java/soukline/
|       |-- KafkaConfig.java
|       |-- KafkaLoadProducer.java
|       `-- KafkaLoadConsumer.java
`-- jmeter/
    |-- SoukLine-Web-Load-Test.jmx
    |-- phase1-normal.jmx
    |-- phase2-pic.jmx
    |-- phase3-panne.jmx
    |-- run-jmeter.ps1
    `-- results/  # Dossiers horodates avec phase1.jtl, phase2.jtl, phase3.jtl
```

## 4. Infrastructure Kafka — `docker-compose.yml`

### Configuration

- **3 brokers** : `soukline-kafka-1`, `soukline-kafka-2`, `soukline-kafka-3`.
- **Apache Kafka 3.7.0**, mode **KRaft** (sans ZooKeeper pour SoukLine).
- Ports exposés sur Windows : **9098, 9094, 9096**.
- **3 topics**, chacun avec **8 partitions**, facteur de réplication **3** et `min.insync.replicas=2`.
- Mémoire Java limitée à environ **512 Mo par broker** dans la configuration du prototype.

Les trois brokers répartissent les partitions et permettent leur réplication. KRaft gère les métadonnées du cluster sans ZooKeeper. La réplication améliore la disponibilité, sans garantir à elle seule l'absence de perte dans toutes les situations.

### Comment démarrer et arrêter Kafka

Depuis la racine du dépôt :

```powershell
docker compose -f .\infra\docker-compose.yml up -d
docker compose -f .\infra\docker-compose.yml ps
```

Pour arrêter les conteneurs SoukLine :

```powershell
docker compose -f .\infra\docker-compose.yml down
```

**Attention :** éviter `docker compose down -v` : cette option peut supprimer les volumes et les données Kafka. Ne pas arrêter les conteneurs d'autres projets Docker.

### Vérifier les brokers et les topics

Depuis la racine du dépôt :

```powershell
.\infra\scripts\check-kafka.ps1
```

**Résultat constaté le 14/10/2026 :** 3 brokers disponibles ; quorum KRaft avec membres `[1,2,3]` ; topics `web-logs`, `stats-a` et `stats-b` présents ; 8 partitions par topic ; facteur de réplication 3 ; réplicas ISR présents sur toutes les partitions affichées.

**Remarque réseau :** `localhost:9098,9094,9096` désigne les ports publiés sur **Windows**. Une commande exécutée **à l'intérieur** d'un conteneur ne doit pas supposer que ces mêmes ports `localhost` pointent vers les autres brokers. Préférer les scripts du projet pour les contrôles.

## 5. Configuration partagée — `config.properties`

```properties
# Configuration Kafka - Projet SoukLine

# Adresses des trois brokers depuis Windows
bootstrap.servers=localhost:9098,localhost:9094,localhost:9096

# Topics du projet
topic.logs=web-logs
topic.stats.a=stats-a
topic.stats.b=stats-b

# Configuration du producer
producer.acks=all

# Configuration du consumer
consumer.auto.offset.reset=earliest

# Nombre de partitions et de replicas
topic.partitions=8
topic.replication.factor=3
```

### Explication

- `bootstrap.servers` : points d'entrée Kafka pour les applications exécutées sur Windows.
- `topic.logs` : topic prévu pour les logs HTTP du site.
- `topic.stats.a` / `topic.stats.b` : topics prévus pour les résultats des moteurs A et B.
- `producer.acks=all` : demande la confirmation des réplicas synchronisés requis avant de considérer un envoi comme confirmé.
- `consumer.auto.offset.reset=earliest` : pour un groupe sans offset valide, commence aux premiers messages **encore disponibles** ; cela ne force pas une relecture à chaque démarrage.
- `topic.partitions=8` : permet le partitionnement et le parallélisme.
- `topic.replication.factor=3` : trois copies de chaque partition, réparties sur les brokers.

**Important :** ce fichier contient des propriétés de projet. Chaque programme Java doit effectivement les charger et appliquer les paramètres correspondants ; Kafka ne les lit pas automatiquement depuis ce fichier.

## 6. Scripts PowerShell de validation Kafka

Les commandes ci-dessous s'exécutent depuis la racine du dépôt, avec Docker démarré.

| Script | Objectif | Résultat déjà obtenu |
|---|---|---|
| `create-topics.ps1` | Créer `web-logs`, `stats-a`, `stats-b` (8 partitions, réplication 3) | Topics créés et vérifiés |
| `check-kafka.ps1` | Vérifier brokers, quorum KRaft, topics et réplicas | Réussi le 14/10/2026 |
| `test-messaging.ps1` | Tester l'envoi et la réception de **100 messages distincts** | Réussi lors des validations précédentes |
| `test-replication.ps1` | Contrôler les réplicas des partitions | Réussi lors des validations précédentes |
| `test-failover.ps1` | Arrêter temporairement le broker 2, tester **20 messages**, puis le redémarrer | Réussi lors des validations précédentes |

### Commandes

```powershell
.\infra\scripts\create-topics.ps1
.\infra\scripts\check-kafka.ps1
.\infra\scripts\test-messaging.ps1
.\infra\scripts\test-replication.ps1
.\infra\scripts\test-failover.ps1
```

**Détail de réplication :** 3 topics × 8 partitions = **24 partitions logiques** ; 24 × 3 = **72 répliques de partitions** attendues. Le script contrôle la présence des réplicas synchronisés.

**Prudence :** `test-failover.ps1` interrompt volontairement un broker. Ne pas le lancer pendant un autre test de charge ou une démonstration non prévue.

Ces scripts testent **Kafka**, pas la génération des logs HTTP par le site ni leur transfert par le collecteur.

## 7. Tests Java de charge Kafka

Le dossier `performance-tests/` contient un projet **Java 17 / Maven / Kafka Clients 3.7.0** :

- `pom.xml` : dépendances et compilation.
- `KafkaConfig.java` : configuration commune du producteur et du consommateur.
- `KafkaLoadProducer.java` : envoi de messages identifiables dans `web-logs` et mesures d'envoi.
- `KafkaLoadConsumer.java` : lecture et vérification du nombre de messages uniques, manquants, dupliqués ou invalides.

Les messages de test ont la forme :

```text
SOUKLINE-LOAD|identifiant-du-test|numero-du-message
```

### Résultats mesurés

| Mesure | Test 1 000 messages | Test 100 000 messages |
|---|---:|---:|
| Production | Réussie | 100 000 confirmés |
| Erreurs de production | Aucune observée | 0 |
| Messages manquants | 0 | 0 |
| Doublons | 0 | 0 |
| Messages invalides | Aucun observé | 0 |
| Durée de production | — | 2,618 s |
| Débit de production | — | ~38 202 messages/s |
| Latence moyenne observée | — | ~1 090 ms |
| Latence P95 | — | ~1 452 ms |
| Latence P99 | — | ~1 462 ms |
| Durée de lecture | — | 2,097 s |
| Débit de lecture | — | ~47 693 messages/s |

Les exécutions de référence se sont terminées avec `BUILD SUCCESS`.

**Interprétation :** ces chiffres sont des mesures indicatives sur un environnement local, et **non** un benchmark définitif du pipeline complet. La lecture peut porter sur des messages déjà stockés ; les débits producteur et consommateur ne constituent donc pas une mesure simultanée de bout en bout. Les messages `SOUKLINE-LOAD` ne sont pas des logs HTTP réels.

## 8. Simulation des visiteurs avec Apache JMeter

### Outil et prérequis

- **Apache JMeter 5.6.3**.
- Site Spring Boot SoukLine démarré sur **`http://localhost:8080`**.
- Plan : `infra/jmeter/SoukLine-Web-Load-Test.jmx`.
- Les requêtes génèrent de véritables lignes dans `site/logs/access.log`.

Pour démarrer le site depuis la racine du dépôt, dans **un terminal séparé** :

```powershell
cd .\site
.\mvnw.cmd spring-boot:run
```

Laisser le terminal du site ouvert pendant les tests.

### Phase 1 — Trafic normal

- **10 utilisateurs virtuels**, ramp-up **10 s**, **5 boucles**.
- Parcours : `GET /`, `GET /produits`, `GET /produits/2`, `GET /recherche?q=tajine`, `POST /panier/ajouter`, `GET /panier`, `GET /checkout`.
- Ajout au panier : formulaire `productId=2`, `quantity=1`.
- **Résultat : 350 échantillons, 0 % d'erreurs** (7 requêtes × 10 utilisateurs × 5 boucles).
- La page de paiement a été validée avec un panier et le service opérationnel.

### Phase 2 — Pic de trafic

- **100 utilisateurs virtuels**, ramp-up **30 s**, **50 boucles**.
- Un `Once Only Controller` ajoute un produit au panier **une fois par utilisateur**.
- Les requêtes `GET /panier` et `GET /checkout` sont répétées.

| Requête | Échantillons | Erreurs |
|---|---:|---:|
| `POST /panier/ajouter` | 100 | 0 % |
| `GET /panier` | 5 000 | 0 % |
| `GET /checkout` | 5 000 | 0 % |
| **Total** | **10 100** | **0 %** |

**Débit total observé : ~332,7 requêtes/s ; temps de réponse moyen global : ~6 ms.**

### Phase 3 — Simulation de panne du paiement

- **100 utilisateurs virtuels**, ramp-up **30 s**, **50 boucles**.
- Un **setUp Thread Group** appelle `GET /admin/panne?etat=on` pour activer la panne.
- Un `Once Only Controller` prépare le panier, puis `GET /panier` et `GET /checkout` sont répétés.
- Un **tearDown Thread Group** appelle `GET /admin/panne?etat=off` pour rétablir le service après le test.

| Requête | Échantillons | Erreurs |
|---|---:|---:|
| `POST /panier/ajouter` | 100 | 0 % |
| `GET /panier` | 5 000 | 0 % |
| `GET /checkout` | 5 000 | **100 % (HTTP 503 attendus)** |
| **Total** | **10 100** | **49,50 % (panne simulée)** |

**Débit total observé : ~330,9 requêtes/s ; temps de réponse moyen global : ~6 ms.**

Le code **HTTP 503** a été constaté dans *View Results Tree*. La commande suivante a confirmé **exactement 5 000 lignes** de checkout en erreur dans le fichier Tomcat :

```powershell
(Select-String -Path .\site\logs\access.log -Pattern '"GET /checkout HTTP/1.1" 503 ').Count
```

**Résultat observé : `5000`.** Le retour de la page de paiement après le test a également confirmé la désactivation de la panne. Ce comptage concerne le fichier de logs tel qu'il existait au moment de la vérification ; il ne mesure pas à lui seul le nombre de messages transmis à Kafka.

### Comment exécuter le plan dans l'interface JMeter

1. Démarrer le site Spring Boot.
2. Ouvrir `infra/jmeter/SoukLine-Web-Load-Test.jmx` dans JMeter.
3. Activer **uniquement** le Thread Group de la phase à tester ; désactiver les autres.
4. Pour la **phase 3**, activer également les groupes **setUp** (panne `on`) et **tearDown** (panne `off`). Pour les phases 1 et 2, désactiver ces groupes pour éviter d'activer la panne par erreur.
5. Lancer le test et consulter `Summary Report` / `View Results Tree`.
6. Sauvegarder le plan après modification (`Ctrl+S`).

**Attention :** le plan original reste utilisable dans l'interface JMeter, phase par phase. Pour l'automatisation, utiliser les trois fichiers indépendants : cela empêche l'activation de la panne pendant les phases 1 et 2.

### Exécution JMeter automatisée en CLI — validée le 14/10/2026

**Prérequis :** JMeter 5.6.3 accessible avec `jmeter` dans le PATH, SoukLine démarré sur `http://localhost:8080`, et terminal ouvert à la racine du projet.

**Commande unique :**

```powershell
.\infra\jmeter\run-jmeter.ps1
```

Le script vérifie que JMeter et le site sont accessibles, crée un dossier de résultats horodaté sous `infra/jmeter/results/`, puis lance successivement :

1. `phase1-normal.jmx` — trafic normal, 10 utilisateurs, 10 s de ramp-up, 5 boucles.
2. `phase2-pic.jmx` — pic de trafic, 100 utilisateurs, 30 s de ramp-up, 50 boucles.
3. `phase3-panne.jmx` — panne de paiement, 100 utilisateurs, 30 s de ramp-up, 50 boucles ; groupes `setUp` et `tearDown` actifs pour activer puis désactiver la panne.

Les résultats sont enregistrés dans `phase1.jtl`, `phase2.jtl` et `phase3.jtl` dans un dossier propre à chaque lancement. Le script tente aussi de désactiver la panne dans un bloc `finally`, même si une phase échoue. Après une interruption brutale, vérifier manuellement que le paiement est rétabli.

**Exécution individuelle possible :**

```powershell
jmeter -n -t .\infra\jmeter\phase1-normal.jmx -l .\infra\jmeter\phase1-results.jtl
jmeter -n -t .\infra\jmeter\phase2-pic.jmx -l .\infra\jmeter\phase2-results.jtl
jmeter -n -t .\infra\jmeter\phase3-panne.jmx -l .\infra\jmeter\phase3-results.jtl
```

Pour relancer ces commandes, choisir de nouveaux noms `.jtl` afin d'éviter les collisions avec les résultats existants.

**Résultats mesurés lors du lancement automatique du 14 octobre 2026 :**

| Phase | Échantillons | Erreurs | Débit | Temps moyen |
|---|---:|---:|---:|---:|
| 1 — Normal | 350 | 0 (0 %) | 38,0 req/s | 5 ms |
| 2 — Pic | 10 100 | 0 (0 %) | 335,7 req/s | 4 ms |
| 3 — Panne | 10 102 | 5 000 (49,50 %) | 333,5 req/s | 4 ms |
| **Total** | **20 552** | **5 000 erreurs attendues** | — | — |

Les 10 102 échantillons de phase 3 incluent les deux requêtes administratives d'activation/désactivation. Les 5 000 erreurs correspondent à la panne HTTP 503 volontaire sur `/checkout`. La console a affiché `Les trois phases sont terminees !` et `Mode panne desactive.`. Une vérification séparée après un test individuel a confirmé `GET /checkout` avec HTTP 200.

**Exemple de dossier généré :** `infra/jmeter/results/20261014-134142/`. Les `.jtl` et les copies de sauvegarde `.jmx` sont des artefacts locaux : ne pas les ajouter automatiquement à Git. Éviter les écouteurs graphiques gourmands en mémoire pour des charges plus importantes.

## 9. Tests réalisés et limites actuelles

### Validé

- Cluster Kafka à 3 brokers, quorum KRaft, 3 topics de 8 partitions, réplication 3.
- Envoi/réception Kafka, réplication et failover dans les scripts de validation.
- Tests Java de 1 000 et 100 000 messages ; aucune perte ni aucun doublon observés dans les exécutions de référence.
- Site Spring Boot accessible sur `localhost:8080` ; routes HTTP réellement testées.
- Création de vraies lignes Tomcat dans `site/logs/access.log`.
- JMeter : **3 phases exécutées individuellement en CLI puis enchaînées automatiquement avec `run-jmeter.ps1`** ; 20 552 échantillons dans le test complet.
- Simulation de panne : **5 000 HTTP 503** sur `/checkout`, confirmés dans `access.log` ; retour du paiement après désactivation.

### Non encore validé / à compléter

- Lecture de `access.log` par le collecteur Java de l'équipe.
- Publication des **vrais logs HTTP** dans Kafka `web-logs`.
- Traitement de ces logs par les moteurs A et B.
- Production effective des statistiques dans `stats-a` et `stats-b`.
- Affichage, alertes et benchmark **de bout en bout**.

**Ne pas confondre** la présence d'un topic Kafka avec la preuve qu'il contient les vrais logs du site. Ne pas confondre non plus les messages synthétiques du test Java avec les logs HTTP produits par Tomcat.

## 10. Prochaines étapes d'intégration

1. Partager avec l'équipe les trois fichiers JMX, `run-jmeter.ps1` et les résultats de référence.
2. Récupérer et démarrer le collecteur Java lorsqu'il sera disponible.
3. Vérifier la publication des vrais logs de `site/logs/access.log` dans le topic `web-logs`.
4. Vérifier le traitement par les moteurs A et B et les sorties `stats-a` et `stats-b`.
5. Vérifier l'affichage, les alertes et les mesures de bout en bout.

Les étapes 2 à 5 dépendent de l'intégration des modules des autres membres.

## 11. Gestion Git et collaboration

Le développement de cette partie est réalisé sur la branche :

```text
feature/ali-nouar
```

Premier commit du prototype documenté : `bad6251`.

```text
feat(infra): initial SoukLine prototype with Kafka cluster, tests and JMeter setup pending website integration
```

Le prototype initial a été publié sur la branche dédiée sans modifier directement `main`. Les nouvelles modifications devront être vérifiées puis proposées à l'équipe par Pull Request.

Pour enregistrer uniquement les fichiers de sa partie, depuis la racine du dépôt :

```powershell
git status
git add infra/README.md infra/jmeter/SoukLine-Web-Load-Test.jmx infra/jmeter/phase1-normal.jmx infra/jmeter/phase2-pic.jmx infra/jmeter/phase3-panne.jmx infra/jmeter/run-jmeter.ps1
git commit -m "feat(infra): automate three JMeter traffic phases"
```

**Ne pas lancer `git add .` sans examiner les modifications des autres membres.** La commande `git commit` ci-dessus est une proposition à exécuter seulement après vérification des fichiers et des résultats.

## 12. Conclusion

L'infrastructure Kafka locale de SoukLine est opérationnelle et a été soumise à des tests de messagerie, de réplication, de tolérance aux pannes et de charge. Le site web a également été testé avec JMeter en trafic normal, en pic et en panne de paiement. Les **5 000 réponses HTTP 503** ont été vérifiées dans les vrais logs Tomcat.

**L’automatisation CLI JMeter est validée. La prochaine étape est la validation du transfert des vrais logs vers Kafka et du traitement complet par les composants de l’équipe.**

---

## 13. Historique détaillé des changements du README — 14/10/2026

Cette section documente les modifications apportées par rapport au README initial, pour faciliter la revue par l'équipe.

| Section | Changement | Raison / preuve |
|---|---|---|
| En-tête | **Consignes et responsable conservés** | Préserver explicitement le périmètre attribué au membre 2. |
| 1. Présentation | Statut actualisé : site et JMeter testés ; pipeline complet non validé | Le README initial disait que l'intégration au site restait entièrement à faire. |
| 2. Architecture | Ajout du chemin réel `site/logs/access.log` et du port `8080` ; séparation entre modules validés et prévus | Distinguer le fonctionnement observé de l'architecture cible. |
| 3. Organisation | Arborescence conservée et clarifiée | Faciliter la navigation sans changer la structure du dépôt. |
| 4. Kafka | Ajout du résultat de `check-kafka.ps1` du 14/10 et de `min.insync.replicas=2` ; rappel sur les ports Docker | Vérification réussie des trois brokers et des réplicas ; éviter l'erreur de commande dans un conteneur. |
| 5. Configuration | Contenu `config.properties` conservé, commentaires explicités | Configuration confirmée ; rappel que les programmes doivent charger les propriétés. |
| 6. Scripts | Commandes de lancement rassemblées et portée des tests précisée | Les scripts testent Kafka, pas le site. |
| 7. Performance | Résultats 1 000 / 100 000 messages conservés ; limites d'interprétation précisées | Éviter de présenter les débits locaux comme des mesures du pipeline complet. |
| 8. JMeter | **Remplacement du scénario provisoire par les trois phases réellement exécutées** | Résultats JMeter : phase 1 = 350 requêtes ; phases 2 et 3 = 10 100 chacune. |
| 8. Panne | Ajout de la procédure `etat=on/off`, des 5 000 HTTP 503 et de la commande de vérification | Vérification directe dans `access.log`, puis retour de la page de paiement. |
| 8. Lancement | Instructions GUI mises à jour ; syntaxe CLI ajoutée **sans prétendre qu'elle est validée** | Éviter que setUp/tearDown active la panne pendant les phases normales. |
| 9. État du projet | Déplacement du site et de `access.log` vers « Validé » ; collecteur/moteurs laissés « À valider » | Alignement sur les preuves disponibles. |
| 10. Étapes suivantes | Suppression des étapes désormais réalisées ; priorité à CLI puis intégration | Ne pas demander de refaire les tests déjà passés. |
| 11. Git | Consignes de collaboration conservées et exemple de commit ciblé ajouté | Préserver le travail des autres membres. |
| 12. Conclusion | Résumé actualisé des réalisations et limites | Présenter un bilan fidèle à l'état actuel. |

**Aucun fichier de configuration, script PowerShell, programme Java ou plan JMeter n'a été modifié par cette mise à jour du README.**


### Complément du 14/10/2026 — automatisation CLI achevée

- Documentation des trois plans JMeter indépendants et du script `run-jmeter.ps1`.
- Ajout de la commande de lancement unique, de la structure des rapports et des mesures observées (20 552 échantillons, dont 5 000 erreurs 503 attendues).
- Mise à jour des sections « validé », « prochaines étapes », « conclusion » et de la commande Git.
- Le collecteur, les moteurs et l'affichage Kafka restent à intégrer et valider.

**Ce document met à jour uniquement le README ; il ne modifie pas les fichiers de code locaux.**
