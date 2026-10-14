# 🛍️ Site web SoukLine (Spring Boot)

**Responsable :** Mohamed Azil (Membre 1)  
**Binôme de relecture :** Ali Nouar (Membre 2 — `infra/`)

---

## 🎯 Périmètre et Missions Réalisées

- **Catalogue e-commerce en mémoire :** Gestion complète des articles artisanaux marocains et du panier en session HTTP (aucune base de données requise).
- **Routes complètes :**
  - `/` : Accueil avec bannières, catégories et best-sellers
  - `/produits` : Catalogue complet avec filtres et tri par prix
  - `/produits/{id}` : Fiche produit détaillée avec sélecteur de quantité
  - `/recherche?q=...` : Recherche multi-critères
  - `/panier` : Gestion du panier d'achat
  - `/login` : Connexion client
  - `/checkout` : Étape de paiement (renvoie réellement HTTP 503 en mode panne)
  - `/confirmation` : Confirmation de commande avec reçu et identifiant
  - `/admin` : Interface de pilotage du mode panne
  - `/admin/panne?etat=on|off` : Endpoint pour JMeter / scripts pour basculer le mode panne
- **Journal d'accès Tomcat (format Common) :** Écrit en continu dans `logs/access.log` sans rotation et de manière non bufférisée (parfait pour le `tail -f` du Membre 5).
- **Journal des erreurs :** `logs/error.log` enregistrant les exceptions et alertes 503.

---

## 🚀 Lancement

Depuis ce dossier `site/` :

```powershell
# Option 1 : Script rapide (Windows)
.\run.bat

# Option 2 : Maven Wrapper
.\mvnw.cmd spring-boot:run
```

Le site est accessible sur : **`http://localhost:8080/`**

---

## 📜 Contrat pour les autres membres

Consultez le fichier [CONTRAT_MEMBRE_1.md](CONTRAT_MEMBRE_1.md) pour :
- Les vraies lignes de log produites par Tomcat.
- L'expression régulière (Regex) pour le `LogParser` (Membre 3).
- La liste des routes et paramètres pour le plan JMeter (Membre 2).
