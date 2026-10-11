# Comment travailler avec Git sur ce projet

## Règles

1. **Jamais de push direct sur `main`.**
2. Une branche par personne : `feature/<prénom-nom>` (ex : `feature/ayoub-echihami`).
3. On ne modifie **que son propre dossier**. Pour toucher au dossier d'un autre (ou à `common/`), on le prévient.
4. Petits commits, messages clairs (`Ajout du LogParser`, `Correction regex URL`).
5. Ne jamais versionner : `access.log`, `target/`, mots de passe, fichiers `.env`.

## Avant tout (une seule fois par PC)

```bash
git config --global user.name "Prénom Nom"
git config --global user.email "email-de-votre-compte-github@exemple.com"
```

Acceptez aussi l'invitation de collaborateur (e-mail GitHub). Sans elle, `git push` est refusé.

## Première fois

```bash
git clone https://github.com/Bilal-Jellaoui/streaming-logs-kafka.git
cd <depot>
git checkout -b feature/<prenom-nom>   # ex : feature/ayoub-echihami
```

## Au quotidien

```bash
git checkout feature/<prenom-nom>
git pull origin main            # récupérer le travail des autres
# ... travailler dans son dossier ...
git add .
git commit -m "Message clair"
git push -u origin feature/<prenom-nom>
```

## Fusionner dans main

1. Sur GitHub : **Compare & pull request** depuis votre branche vers `main`.
2. Le **binôme relit** et approuve :
   - Mohamed ↔ Ali
   - Bilal ↔ Ayoub
   - Youssef ↔ Souhail
3. Après approbation, **Merge pull request**.
4. Chacun fait ensuite `git pull origin main` sur sa branche.

## En cas de conflit

```bash
git pull origin main
# Git indique les fichiers en conflit : les ouvrir, garder le bon contenu, supprimer <<<<<<< ======= >>>>>>>
git add <fichier>
git commit --no-edit
```
