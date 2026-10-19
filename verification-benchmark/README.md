# Vérification et benchmark

**Responsable :** Souhail El Bettachi (Membre 6)

## Ce que fait ce module

| Programme | Rôle |
|---|---|
| `StatsVerifier` | Recompte `access.log` en séquentiel (avec le `LogParser` de `common`) et compare **chaque moteur séparément** à cette référence, case par case (fenêtre de 5 s, URL). Compare aussi A et B entre eux. Lit `stats-a` / `stats-b` directement dans Kafka (`kafka:stats-a`) ou dans un fichier. |
| `SequentialBaseline` | Une passe séquentielle sur `access.log` : point de comparaison (temps, débit à 1 thread). |
| `BenchmarkRunner` | Débit en mémoire avec 1 / 2 / 4 / 8 threads. **Modèles en mémoire de deux stratégies de compteurs, pas les vrais moteurs.** Chaque résultat est vérifié contre la baseline. |
| `plot_graphs.py` | Graphiques à partir de `benchmark.csv`. |

## Deux niveaux de vérification

Le collecteur publie les lignes sans horodatage explicite : Kafka Streams fenêtre donc sur l'heure d'**envoi**, pas sur l'heure écrite dans le log. Une requête proche d'une frontière de fenêtre peut tomber dans la fenêtre voisine.

- `--mode totals` (défaut) : totaux par URL, toutes fenêtres confondues. Doit être exact quelle que soit la base de temps.
- `--mode windows` : cases (fenêtre, URL). Exact seulement si moteurs et référence utilisent la même heure.

Les deux niveaux sont toujours calculés ; `--mode` choisit celui qui décide du verdict.

## Utilisation

Une fois (et après toute modification de `common`) : `mvn -q -DskipTests install` à la racine.

```
scripts\run-verification.ps1 -AccessLog ..\site\logs\access.log                       # moteur A, lu dans Kafka
scripts\run-verification.ps1 -AccessLog ..\site\logs\access.log -StatsB kafka:stats-b # A et B
scripts\export-stats.ps1 -Topic stats-a -Out results\stats-a.txt                      # copie des résultats
```

Pour un essai propre : topics `stats-a` / `stats-b` recréés et vides, `access.log` vidé avant de lancer le collecteur, et le collecteur arrêté **après** que les moteurs ont fini de traiter.

## Tests

`mvn -pl verification-benchmark -am test`
