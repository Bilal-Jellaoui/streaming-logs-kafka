# Site web SoukLine (Spring Boot)

**Responsable :** Mohamed Azil (Membre 1)

- Routes : `/`, `/produits`, `/produits/{id}`, `/recherche`, `/panier`, `/login`, `/checkout`, `/confirmation`
- Journal d'accès Tomcat (format *common*) dans `logs/access.log`, plus `error.log`
- Mode panne : `/admin/panne?etat=on|off` (le paiement renvoie des 503)

Résultat : un site sur `localhost:8080` qui écrit une ligne dans `access.log` à chaque visite.
