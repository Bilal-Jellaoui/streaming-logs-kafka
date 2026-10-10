# testdata

Fichiers de référence communs à tout le groupe :

- `sample-access.log` : petit extrait (~1000 lignes) d'un vrai `access.log` du site.
- `expected.txt` : les résultats attendus (total de lignes, total 5xx, requêtes par URL), calculés avec `wc`/`awk`.

C'est le SEUL dossier où un fichier `.log` est autorisé dans Git.
