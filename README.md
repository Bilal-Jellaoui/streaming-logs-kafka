# 🌊 Traitement parallèle en streaming de logs web

Chaîne complète sur un **vrai site web** : visiteurs / JMeter → site SoukLine (Spring Boot) → `access.log` → collecteur Java → **Kafka** `web-logs` → **Moteur A** (Kafka Streams) + **Moteur B** (concurrence manuelle) → `stats-a` / `stats-b` → affichage + alerte.

Master Big Data, IA & Applications Avancées, Université Ibn Tofaïl · Groupe 1 (6 membres)

## 👥 Équipe et dossiers

| Membre | Nom | Rôle | Dossier | Branche |
|---|---|---|---|---|
| Membre 1 | Mohamed Azil | Site web | `site/` | `feature/mohamed-azil` |
| Membre 2 | Ali Nouar | Infra Kafka, JMeter, intégration | `infra/` | `feature/ali-nouar` |
| Membre 3 | Bilal Jellaoui | Moteur A (Kafka Streams) + Parser | `engine-a/`, `common/` | `feature/bilal-jellaoui` |
| Membre 4 | Ayoub Echihami | Moteur B (concurrence manuelle) | `engine-b/` | `feature/ayoub-echihami` |
| Membre 5 | Youssef Benayad | Collecteur, affichage, alerte | `collector-display/` | `feature/youssef-benayad` |
| Membre 6 | Souhail El Bettachi | Vérification, benchmark, rapport | `verification-benchmark/`, `report/` | `feature/souhail-el-bettachi` |

**Binômes de relecture des Pull Requests** : Mohamed ↔ Ali, Bilal ↔ Ayoub, Youssef ↔ Souhail.

## 🧰 Prérequis (tout le monde)

- **JDK 17** et **Maven 3.9+** (`java -version`, `mvn -v`)
- **Git** (Git Bash sous Windows)
- **Docker** (pour Kafka, voir `infra/`)
- **IntelliJ** : *File, puis Open*, et choisir le `pom.xml` de la **racine** (pas celui d'un module)

## 🧱 Structure Maven

`pom.xml` à la racine = **pom parent** (versions définies une seule fois : Java 17, Kafka 3.7.0, JUnit 5.10.2). Modules : `common`, `engine-a`, `engine-b`, `collector-display`, `verification-benchmark`. `site/` (Spring Boot), `infra/` et `report/` ne sont pas des modules du parent.

```bash
mvn test                   # tous les modules, depuis la racine
mvn -pl engine-a -am test  # seulement engine-a (et common dont il dépend)
```

Une nouvelle dépendance se déclare avec sa version dans le **pom parent** (`dependencyManagement`), puis sans version dans le module. Changer le pom parent : prévenir Bilal.

## 🔀 Workflow Git

Voir [CONTRIBUTING.md](CONTRIBUTING.md). En résumé : **personne ne pousse directement sur `main`**. Chacun travaille sur sa branche, puis ouvre une Pull Request relue par son binôme.

## 🔗 Contrats entre membres

- Format des logs : pattern Tomcat *common* (Mohamed)
- Topics : `web-logs`, `stats-a`, `stats-b` (Ali)
- JSON des résultats : `{engine, windowStart, url, requests, errors5xx}` (Bilal et Ayoub)
- `config.properties` partagé (Ali)

## 🗓️ Jalons

| Plan | Objectif |
|---|---|
| J1 | Contrats figés, site v1 qui écrit un vrai `access.log`, Kafka opérationnel, `LogParser` testé |
| J2-J3 | Premier bout-en-bout site → collecteur → Kafka → Moteur A → affichage ; Moteur B autonome |
| J4-J5 | Moteur B sur Kafka, mode panne + alerte, vérificateur OK, gel du code |
| J6 | Benchmark, rapport, slides, répétition de la démo |

## Démo end-to-end (résultats réels)

Scénario exécuté sur le cluster Kafka à 3 brokers (`infra`), avec les 6 composants lancés :
site SoukLine → `access.log` → LogCollector → Kafka (`web-logs`) → Moteur A (Kafka Streams) + Moteur B (concurrence manuelle) → DisplayApp.

| Étape | Action | Résultat observé |
|---|---|---|
| 1. Trafic normal | Navigation sur le site (accueil, produits, panier) | Moteur A et Moteur B affichent les mêmes chiffres (ex. 40 requêtes / 40 requêtes), statut **OK** |
| 2. Panne | `/admin/panne?etat=on`, puis F5 répété sur `/checkout` (HTTP 503) | `/checkout` : 10 requêtes, 10 erreurs 5xx, soit **25 %** : **`!!! ALERTE`** (seuil 10 %) |
| 3. Retour à la normale | `/admin/panne?etat=off`, navigation | 0 % d'erreurs 5xx, statut **OK** |

Captures : `docs/demo/trafic-normal.png`, `docs/demo/alerte.png`, `docs/demo/retour-ok.png`.

**Remarques**
- Les deux moteurs utilisent la même base de temps (l'heure écrite dans la ligne de log), donc leurs fenêtres de 5 s sont comparables.
- Un léger décalage (quelques secondes) peut apparaître entre A et B, car ils publient leurs résultats à des instants différents ; les totaux deviennent identiques ensuite.
- Une alerte n'est émise que si la fenêtre contient au moins 10 requêtes (`alert.minRequests`), pour éviter les fausses alertes sur un faible trafic (ex. 2 erreurs sur 7 requêtes = 28,6 % mais pas d'alerte).
