# Collecteur de logs, affichage console, alerte

**Responsable :** Youssef Benayad (Membre 5)

- Collecteur Java : lit `access.log` en continu (comme `tail -f`) et publie dans `web-logs`
- Affichage console en direct : top URLs, requêtes/s, erreurs 5xx, Moteur A et B côte à côte
- Alerte si le taux de 5xx dépasse un seuil
