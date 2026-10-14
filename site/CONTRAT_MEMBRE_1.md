# 📜 Contrat d'Interface — Membre 1 (Site Web SoukLine)

> **Projet :** Traitement Parallèle en Streaming de Logs Web (Groupe 1 — 6 Membres)  
> **Rôle :** Membre 1 — Site Web (Spring Boot + Tomcat Embarqué + Mode Panne + Logs)  
> **Destinataires :** Membre 2 (JMeter/Infra), Membre 3 (Parser/Kafka Streams), Membre 4 (Moteur B), Membre 5 (Collecteur & Alertes), Membre 6 (Benchmark & Rapport)

---

## 1. Contrat des Logs (Tomcat Common Log Format)

### Emplacement des Fichiers
- **Fichier de logs d'accès :** `logs/access.log` (fichier fixe, rotation désactivée, vidage immédiat non bufférisé)
- **Fichier d'erreurs/exceptions :** `logs/error.log` (exceptions et erreurs de paiement 503)

### Spécification du Format Common (CLF)
Le serveur écrit strictement selon la directive Tomcat `common` :
```text
%h %l %u %t "%r" %>s %b
```
- `%h` : IP du client (ex: `127.0.0.1` ou `0:0:0:0:0:0:0:1`)
- `%l` : RFC 1413 identity (`-`)
- `%u` : Utilisateur authentifié (`-`)
- `%t` : Date/Heure entre crochets `[jour/mois/année:heure:minute:seconde fuseau]` (ex: `[14/Oct/2026:03:23:20 +0100]`)
- `"%r"` : Première ligne de la requête HTTP entre guillemets (ex: `"GET /produits HTTP/1.1"`)
- `%>s` : Code de statut HTTP final (ex: `200`, `302`, `404`, `503`)
- `%b` : Taille de la réponse en octets hors en-têtes (ou `-` si nulle)

### Vraies Lignes de Log Produites par le Serveur (Contrat Réel)
Voici les lignes exactes générées par le site lors des tests réels :
```text
127.0.0.1 - - [14/Oct/2026:03:23:20 +0100] "GET / HTTP/1.1" 200 16112
127.0.0.1 - - [14/Oct/2026:03:23:20 +0100] "GET /produits HTTP/1.1" 200 22470
127.0.0.1 - - [14/Oct/2026:03:23:20 +0100] "GET /produits/2 HTTP/1.1" 200 10502
127.0.0.1 - - [14/Oct/2026:03:23:20 +0100] "GET /recherche?q=tajine HTTP/1.1" 200 7638
127.0.0.1 - - [14/Oct/2026:03:23:20 +0100] "GET /panier HTTP/1.1" 200 6890
127.0.0.1 - - [14/Oct/2026:03:23:20 +0100] "GET /login HTTP/1.1" 200 8080
127.0.0.1 - - [14/Oct/2026:03:23:20 +0100] "GET /admin/panne?etat=on HTTP/1.1" 200 203
127.0.0.1 - - [14/Oct/2026:03:23:20 +0100] "GET /checkout HTTP/1.1" 503 8348
127.0.0.1 - - [14/Oct/2026:03:23:20 +0100] "GET /admin/panne?etat=off HTTP/1.1" 200 194
```

### Expression Régulière Recommandée (pour Membre 3 - LogParser)
```regex
^(\S+) \S+ \S+ \[([^\]]+)\] "(\S+) (\S+) (\S+)" (\d{3}) (\S+)
```
Groupes capturés :
1. `$1` : IP client (`host`)
2. `$2` : Horodatage (`timestamp`)
3. `$3` : Méthode HTTP (`GET`, `POST`)
4. `$4` : URL demandée (`rawUrl`)
5. `$5` : Protocole HTTP (`HTTP/1.1`)
6. `$6` : Statut HTTP (`200`, `503`, etc.)
7. `$7` : Taille en octets (`bytes`)

---

## 2. Liste Complète des Routes HTTP (pour Membre 2 - Plan JMeter)

| Méthode | Route HTTP | Rôle | Statut Normal | Statut en Mode Panne |
| :--- | :--- | :--- | :---: | :---: |
| **GET** | `/` | Page d'accueil du site | `200` | `200` |
| **GET** | `/produits` | Catalogue des articles | `200` | `200` |
| **GET** | `/produits/{id}` | Fiche produit (`id` de `1` à `10`) | `200` | `200` |
| **GET** | `/recherche?q={mot}` | Recherche (`q=argan`, `q=tapis`, ...) | `200` | `200` |
| **GET** | `/panier` | Consultation du panier d'achat | `200` | `200` |
| **POST**| `/panier/ajouter` | Ajout (`productId=1&quantity=1`) | `302` (redirect) | `302` |
| **GET** | `/login` | Page de connexion utilisateur | `200` | `200` |
| **POST**| `/login` | Soumission login (`email=...`) | `302` (redirect) | `302` |
| **GET** | `/checkout` | Page de paiement / passerelle | `200` | **`503` (Erreur)** |
| **POST**| `/checkout` | Validation finale de commande | `302` (vers `/confirmation`) | **`503` (Erreur)** |
| **GET** | `/confirmation` | Écran de succès de commande | `200` | `200` |
| **GET/POST**| `/admin/panne` | Contrôle panne (`?etat=on\|off`) | `200` | `200` |

---

## 3. Protocole du Mode Panne (`/admin/panne`)

Le mode panne permet de simuler une défaillance de la passerelle de paiement en temps réel :

1. **Activer la panne :**
   ```bash
   curl "http://localhost:8080/admin/panne?etat=on"
   ```
   *Réponse JSON :*
   ```json
   {
     "status": "SUCCESS",
     "etat": "on",
     "panneActive": true,
     "httpCheckoutExpectedStatus": 503,
     "message": "Mode panne ACTIVÉ: /checkout renverra désormais HTTP 503 Service Unavailable"
   }
   ```
   *Conséquence immédiate :* Tout appel sur `/checkout` (GET et POST) renvoie un code **HTTP 503**, écrit la ligne dans `logs/access.log`, et journalise l'exception dans `logs/error.log`.

2. **Désactiver la panne :**
   ```bash
   curl "http://localhost:8080/admin/panne?etat=off"
   ```
   *Réponse JSON :*
   ```json
   {
     "status": "SUCCESS",
     "etat": "off",
     "panneActive": false,
     "httpCheckoutExpectedStatus": 200,
     "message": "Mode panne DÉSACTIVÉ: /checkout fonctionne normalement (HTTP 200)"
   }
   ```

---

## 4. Normalisation des URLs (pour Membre 3 - LogParser)

Conformément au sujet, les URLs doivent être normalisées avant agrégation dans Kafka Streams :
- `/produits/1`, `/produits/2`, etc. ➔ `/produits/{id}`
- `/recherche?q=argan` ➔ `/recherche` (suppression des paramètres `?q=...`)
- `/css/soukline.css`, `/js/soukline.js` ➔ `/static/*`

---

## 5. Démarrage Rapide

### Option 1 : Script Batch (Windows)
Double-cliquer sur le fichier `run.bat` à la racine du projet.

### Option 2 : Ligne de commande Maven Wrapper
```powershell
.\mvnw.cmd spring-boot:run
```
Le serveur écoute sur **`http://localhost:8080`**.
