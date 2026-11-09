# Image Laser — version Windows avec abonnement

Application Windows (.exe) de préparation d'images pour la gravure laser : tramage par matériau, recadrage, suppression du fond, export PNG avec DPI ou SVG en mm pour xTool Creative Space.

- **Gratuit** : toutes les fonctions, 3 exports par jour.
- **Pro (abonnement)** : exports illimités. Paiement et clés de licence gérés par **Lemon Squeezy**, sans serveur à héberger.

---

## 1. Créer l'abonnement sur Lemon Squeezy

1. Crée un compte sur https://www.lemonsqueezy.com puis une boutique (Store). Renseigne tes infos de paiement pour pouvoir encaisser.
2. **Products → New product**
   - Nom : *Image Laser Pro*
   - Prix : **Subscription**, par exemple 9,99 € par mois. Tu peux ajouter une variante annuelle.
   - Section **License keys** : active *Generate license keys*.
     - *Activation limit* : 2 (nombre de PC par abonné).
     - *License length* : laisse lié à l'abonnement. La clé expire quand l'abonnement s'arrête.
3. Publie le produit.
4. Note trois informations :
   - **Store ID** : *Settings → Stores*, le numéro de ta boutique.
   - **Product ID** : dans l'adresse de la page du produit, ou via *Share → API*.
   - **Lien de paiement** : bouton *Share* du produit, lien `https://…lemonsqueezy.com/buy/…`.

## 2. Configurer l'appli

Ouvre `config.json` et remplace les valeurs :

```json
{
  "storeId": 12345,
  "productId": 67890,
  "checkoutUrl": "https://ta-boutique.lemonsqueezy.com/buy/xxxxxxxx",
  "portalUrl": "https://app.lemonsqueezy.com/my-orders",
  "priceLabel": "9,99 € / mois",
  "freeExportsPerDay": 3,
  "offlineGraceDays": 7
}
```

- `storeId` et `productId` empêchent l'utilisation d'une clé venant d'une autre boutique. Ne les laisse pas à 0 avant de vendre.
- `freeExportsPerDay` fixe le nombre d'exports gratuits par jour.
- `offlineGraceDays` fixe combien de jours un abonné peut travailler sans Internet avant une nouvelle vérification.

## 3. Compiler le .exe avec GitHub

1. Crée un **dépôt privé** sur GitHub, par exemple `image-laser`.
2. **Add file → Upload files** : dépose tout le contenu du dossier, y compris le dossier `.github`.
   - Si Windows ne montre pas le dossier `.github`, fais **Add file → Create new file**, tape le nom `.github/workflows/build.yml` et colle le contenu du fichier du même nom.
3. Valide (Commit). La compilation démarre seule : onglet **Actions → Compiler Image Laser (Windows)**. Elle prend environ 5 minutes.
4. Quand la pastille est verte, ouvre la compilation et télécharge **Image-Laser-Windows** en bas de page. Le zip contient `Image-Laser-Setup-1.0.0.exe`.

Pour relancer une compilation sans rien modifier : **Actions → Compiler Image Laser (Windows) → Run workflow**.

## 4. Installer et tester

1. Lance `Image-Laser-Setup-1.0.0.exe`.
2. Windows SmartScreen peut afficher « Windows a protégé votre ordinateur » : clique **Informations complémentaires → Exécuter quand même**. Ce message vient de l'absence de certificat de signature (voir plus bas).
3. Teste la version gratuite : 3 exports, puis la fenêtre d'abonnement s'ouvre.
4. Teste l'abonnement avec le **mode test** de Lemon Squeezy (*Test mode* en bas à gauche du tableau de bord). Fais un achat avec la carte de test `4242 4242 4242 4242`, puis colle la clé reçue dans **Gratuit → Déjà abonné ? → Activer**.
5. Repasse Lemon Squeezy en mode réel avant de vendre. Les clés de test ne marchent pas en production.

## Fonctionnement de la licence

| Moment | Ce que fait l'appli |
|---|---|
| Activation | Envoie la clé à Lemon Squeezy, vérifie qu'elle vient de ta boutique et de ton produit, puis enregistre ce PC. |
| Chaque démarrage, puis toutes les 6 h | Revérifie la clé. Si l'abonnement est arrêté ou expiré, l'appli repasse en version gratuite. |
| Sans Internet | L'abonnement reste actif pendant `offlineGraceDays` jours depuis la dernière vérification. |
| Désactiver sur ce PC | Libère la place pour un autre ordinateur. |

La clé est chiffrée avec le coffre de Windows (`safeStorage`) dans `%APPDATA%\Image Laser\`.

## Bon à savoir

- **Signature de code** : pour supprimer l'alerte SmartScreen, il faut un certificat de signature (environ 100 à 300 € par an, ou Azure Trusted Signing pour moins cher). Ce n'est pas obligatoire pour démarrer.
- **Compteur gratuit** : il est stocké sur le PC. Un utilisateur averti peut le remettre à zéro. Pour une limite impossible à contourner, il faudrait un petit serveur, ce que l'on peut ajouter plus tard.
- **Polices** : elles se chargent depuis Google Fonts quand Internet est disponible. Hors ligne, l'appli utilise les polices de Windows.
- **Lancer sans compiler** (si Node.js est installé) : `npm install` puis `npm start` dans le dossier.

## Structure

```
image-laser/
├─ config.json              ← réglages boutique et abonnement
├─ package.json             ← dépendances et réglages de l'installateur
├─ build/icon.png           ← icône de l'appli
├─ src/main.js              ← fenêtre, licence, quota, enregistrement
├─ src/preload.js           ← pont sécurisé interface ↔ Windows
├─ src/renderer/index.html  ← interface et traitement d'image
└─ .github/workflows/build.yml ← compilation automatique du .exe
```
