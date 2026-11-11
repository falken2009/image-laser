# Falken IPTV pour Fire TV

Version Fire TV (Android) de Falken IPTV, pilotable entièrement à la télécommande.

## Fonctions

- Intro animée « FALKEN IPTV » avec le faucon avec son d'ouverture
- Connexion **Xtream Codes** (serveur, utilisateur, mot de passe) ou **lien M3U** (chaînes en direct uniquement)
- Réseau robuste : redirections http ↔ https, nouvelles tentatives automatiques, identifiant d'appli réglable (Falken IPTV, IPTV Smarters, VLC, TiviMate), messages d'erreur clairs
- Accueil noir et rouge : TV en direct, Guide, Films, Séries + Favoris, Replay, Recherche, Compte, Réglages
- **Profils** (« Qui regarde ? »), **plusieurs abonnements**, **choix du thème**
- **Continuer à regarder** sur l'accueil, **Ma liste**, **filtres** des films (année, note, genre)
- **Grille du guide** sur 3 heures et **rappels** (notification 5 min avant)
- **Lecteur** : épisode suivant automatique ; en direct, haut/bas pour zapper, boutons ▲ ▼ à droite quand le menu de la vidéo est affiché ; ≡ pour le minuteur de veille (30 / 60 / 90 min)
- **État de la connexion** dans Réglages, **images de fond** dans Films et Séries, **derniers ajouts**, une seule copie par titre (la meilleure qualité)
- **Contrôle parental** (code à 4 chiffres, ≡ sur une catégorie pour la verrouiller) et **multi-écran** (2 ou 4 chaînes)
- **TV en direct** : catégories à gauche, chaînes au centre, programme en cours et suivant à droite
- **Guide** : chaque chaîne avec son programme en cours (barre de progression) et le suivant
- **Favoris** : bouton ≡ (menu) de la télécommande sur une chaîne
- **Replay** : programmes déjà diffusés, par jour
- **Films et Séries en rangées** façon Netflix : une rangée d'affiches par catégorie (Derniers ajouts en premier), image de fond et titre de l'affiche sélectionnée en haut, « Tout voir » au bout de chaque rangée
- **Sagas et jaquettes depuis Internet (TMDB)** : avec une clé gratuite de themoviedb.org (Réglages)
- **Films** : tri A→Z / Z→A / notes, sagas regroupées, fiche, reprise de lecture
- **Séries** : saisons, épisodes, reprise automatique, épisodes vus cochés
- **Recherche** dans tout le catalogue, au clavier
- **Lecteur** ExoPlayer : MP4, MKV, AVI, TS, HLS ; langues et sous-titres (bouton réglages du lecteur) ;
  commandes vocales Alexa « Alexa, pause », « Alexa, reprends », « Alexa, avance de 30 secondes »

## Compilation (GitHub, gratuit)

1. Crée un **nouveau dépôt** sur GitHub, par exemple `falken-iptv-fire`.
   - **Public** : le Fire Stick peut télécharger l'APK tout seul avec l'appli Downloader (le plus simple).
   - **Privé** : il faudra passer par le PC et adb.
2. Envoie le contenu de ce dossier (`app`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`).
3. Crée le fichier `.github/workflows/build.yml` avec **Add file → Create new file**
   (le navigateur n'envoie pas les dossiers qui commencent par un point) et colle son contenu.
4. Onglet **Actions** → **Build Fire TV APK** : environ 5 à 10 minutes.
5. L'APK `Falken-IPTV.apk` est publié dans **Releases** (à droite de la page du dépôt),
   et aussi en bas de la page du build (**Falken-IPTV-apk**).

Le fichier `app/king365.keystore` signe toujours l'appli avec la même clé :
les mises à jour s'installent par-dessus sans perdre tes réglages. Garde-le.

## Mises à jour automatiques

Une fois l'appli installée, plus besoin de Downloader : à chaque envoi sur GitHub, une nouvelle version
est compilée et publiée. Au démarrage, Falken IPTV la détecte et propose **Installer** (réglages conservés).
La première fois, autorise Falken IPTV à installer des applis (Options pour les développeurs →
Installer des applis inconnues). Réglages → **Rechercher une mise à jour** pour vérifier à la main.

## Installation sur le Fire Stick sans PC (dépôt public)

1. Sur le Fire Stick : **Paramètres → Ma Fire TV → À propos**, clique 7 fois sur le nom de l'appareil
   pour afficher les **Options pour les développeurs**.
2. Installe l'appli **Downloader** (gratuite, dans l'Appstore Amazon).
3. **Paramètres → Ma Fire TV → Options pour les développeurs → Installer des applis inconnues** : autorise **Downloader**.
4. Dans Downloader, tape l'adresse :
   `https://github.com/falken2009/falken-iptv-fire/releases/latest/download/Falken-IPTV.apk`
   puis **Installer**. Pour mettre à jour plus tard : même adresse, elle donne toujours la dernière version.

## Installation depuis Windows avec adb (dépôt privé)

1. Options pour les développeurs (voir ci-dessus) : active **Débogage ADB**.
2. Note l'adresse IP : **Paramètres → Ma Fire TV → À propos → Réseau**.
3. Sur le PC, télécharge **SDK Platform-Tools** pour Windows (developer.android.com),
   décompresse-le, ouvre le dossier, tape `cmd` dans la barre d'adresse puis Entrée.
4. Tape :
   ```
   adb connect 192.168.1.XX
   adb install -r C:\chemin\vers\Falken-IPTV.apk
   ```
   La première fois, accepte l'autorisation qui s'affiche sur la télé.
5. Falken IPTV apparaît dans **Applications**.

## Télécommande

- Flèches et OK : se déplacer, ouvrir
- ≡ (menu) sur une chaîne : ajouter ou retirer des favoris
- Retour : écran précédent
- Lecture/pause et avance/retour de la télécommande : pilotent la vidéo

## Version Premium (Amazon Appstore)

Chaque compilation produit deux applis :
- **Falken-IPTV.apk** (version perso) : tout est débloqué, mises à jour depuis GitHub. C'est celle de Downloader.
- **Falken-IPTV-Store.apk** (version boutique, onglet Actions → Falken-IPTV-Store-apk) : à envoyer sur l'Amazon Appstore.
  - 7 jours d'essai gratuit avec tout débloqué, puis version gratuite + Premium par abonnement (achats intégrés Amazon).
  - Gratuit : direct, films et séries (liste de catégories), recherche, favoris, reprise, 1 profil, 1 abonnement, contrôle parental, lecteur VLC de secours.
  - Premium : profils et abonnements multiples, guide en grille et rappels, replay, multi-écran, rangées façon Netflix,
    épisode suivant automatique, minuteur de veille, thèmes de couleur.
  - TMDB est désactivé (la clé gratuite est réservée à l'usage non commercial) ; mises à jour par l'Appstore, pas par GitHub.

À faire dans la console développeur Amazon (developer.amazon.com, compte gratuit) :
1. Créer l'appli, puis dans **In-App Items** un abonnement parent (ex. `falken.premium`) avec deux durées :
   `falken.premium.mensuel` (1 mois) et `falken.premium.annuel` (1 an) — les références doivent être identiques.
2. Télécharger la clé **AppstoreAuthenticationKey.pem** (rubrique Appstore SDK) et la placer dans
   `app/src/store/assets/` avant de compiler la version boutique.
3. Tester les achats avec l'appli **Amazon App Tester** avant de soumettre.
