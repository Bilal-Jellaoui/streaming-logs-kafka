# # Parser partagé (LogEntry + LogParser)

**Responsable :** Bilal Jellaoui (Membre 3)

Ce module est **utilisé par tous les moteurs** : toute modification passe par une Pull Request.

## Ce que fait ce module
- `LogParser.parse(ligne)` transforme une ligne de log Tomcat (format *common*) en objet `LogEntry` : `ip`, `timestamp`, `method`, `url`, `status`, `bytes`.
- Les lignes mal formées (ou avec une date invalide) donnent `null` : l'appelant les ignore.
- L'URL est **normalisée** pour pouvoir compter par page :
    - `/recherche?q=chaise` → `/recherche` (sans paramètres)
    - `/produits/12` → `/produits/{id}`
    - `/css/site.css`, `/js/...`, images → `/static`
    - `/produits/` → `/produits` (sans `/` final)
- `LogEntry.is5xx()` : vrai si le statut est entre 500 et 599.

## Tests
9 tests unitaires (`LogParserTest`) : ligne valide, 404 ≠ 5xx, URL sans paramètres, produit normalisé, fichiers statiques, slash final, taille `-`, ligne mal formée, date invalide.

Lancer : `mvn -pl common test`

## Problèmes rencontrés et solutions
| Problème | Solution |
|---|---|
| Le `.gitignore` contenait `logs/` et cachait le package Java `logs.common` | Remplacé par `/logs/` (uniquement le dossier à la racine) |
| Une ligne mal formée ne doit pas faire planter un moteur | `parse` retourne `null` au lieu de lancer une exception |