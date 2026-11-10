# Laser Pix — version Windows avec abonnement

Application Windows (.exe) de préparation d'images pour tous les graveurs laser (diode, CO2, fibre) : tramage par matériau, recadrage, gomme, suppression du fond, export PNG avec DPI ou SVG en mm, et envoi direct vers xTool Creative Space, LightBurn, LaserGRBL ou un autre logiciel.

- **Gratuit** : toutes les fonctions, 3 exports par jour.
- **Pro (abonnement)** : exports illimités. Paiement et clés de licence gérés par **Lemon Squeezy**, sans serveur à héberger.

---

## 1. Créer l'abonnement sur Lemon Squeezy

1. Crée un compte sur https://www.lemonsqueezy.com puis une boutique (Store). Renseigne tes infos de paiement pour pouvoir encaisser.
2. **Products → New product**
   - Nom : *Laser Pix Pro*
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

1. Crée un **dépôt privé** sur GitHub, par exemple `laser-pix`.
2. **Add file → Upload files** : dépose tout le contenu du dossier, y compris le dossier `.github`.
   - Si Windows ne montre pas le dossier `.github`, fais **Add file → Create new file**, tape le nom `.github/workflows/build.yml` et colle le contenu du fichier du même nom.
3. Valide (Commit). La compilation démarre seule : onglet **Actions → Compiler Laser Pix (Windows)**. Elle prend environ 5 minutes.
4. Quand la pastille est verte, ouvre la compilation et télécharge **Laser-Pix-Windows** en bas de page. Le zip contient `Laser-Pix-Setup-1.0.0.exe`.

Pour relancer une compilation sans rien modifier : **Actions → Compiler Laser Pix (Windows) → Run workflow**.

## 4. Installer et tester

1. Lance `Laser-Pix-Setup-1.0.0.exe`.
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

La clé est chiffrée avec le coffre de Windows (`safeStorage`) dans `%APPDATA%\Laser Pix\`.

## Bibliothèque

Le bouton **Bibliothèque**, en haut, contient deux rubriques :

- **Mes supports** : tes réglages enregistrés par support (matériau, machine, vitesse, puissance, résolution, réglages d'image, avec une note). Enregistre-les avec « ★ Enregistrer ces réglages comme support » sous le bouton d'export. Retrouve-les aussi dans la liste en haut de la carte Matériau.
- **Mes gravures** : une copie de chaque fichier exporté, avec son aperçu, sa photo d'origine et ses réglages. « Rouvrir » recharge tout pour retoucher. « Enregistrer le fichier » le réenregistre sans compter un nouvel export.

Tout est gardé sur le PC, dans `%APPDATA%\Laser Pix\`.

## Envoi vers le logiciel du graveur

Sous le bouton d'enregistrement, choisis ton logiciel puis touche **Envoyer vers le logiciel** :

- Laser Pix enregistre le fichier dans `Images\Laser Pix\`, ouvre le logiciel avec ce fichier et affiche le fichier dans l'Explorateur.
- Laser Pix cherche xTool Creative Space, LightBurn et LaserGRBL dans leurs dossiers d'installation habituels. S'il ne les trouve pas, il te demande où est leur fichier `.exe`, une seule fois.
- **Autre logiciel…** permet d'ajouter n'importe quel logiciel : celui d'Atomstack, de Glowforge, RDWorks…
- Si le logiciel s'ouvre sans afficher l'image, glisse le fichier depuis l'Explorateur dans sa fenêtre. Certains logiciels n'acceptent pas l'ouverture directe.

Un envoi compte comme un export dans la version gratuite. La puissance, la vitesse et le lancement de la gravure restent dans le logiciel du graveur.

## Modification IA

La carte « Modification IA » fonctionne de deux façons :

- **Assistant intégré** : gratuit, hors ligne, pour tout le monde. Il comprend les demandes courantes (« enlève le fond », « recadre en rond », « plus de contraste », « plus clair », « logo en SVG », « largeur 120 mm », un nom de matériau…) et repère le sujet de la photo pour le recadrage.
- **IA qui voit la photo** : réservée aux abonnés Pro. Elle comprend des demandes libres et peut placer la gomme toute seule. Pour l'activer, colle ta clé API Anthropic dans `config.json` :

```json
"anthropicApiKey": "sk-ant-...",
"aiModel": "claude-sonnet-5-5"
```

Crée la clé sur https://console.anthropic.com, rubrique *API Keys*, et ajoute du crédit. Chaque demande coûte environ un centime. Sans clé, l'appli utilise seulement l'assistant intégré.

⚠️ Une clé placée dans le .exe peut être extraite par une personne qui fouille le fichier. Pour une vente à grande échelle, il faut faire passer les demandes par un petit serveur à toi qui garde la clé. Limite aussi les dépenses dans la console Anthropic (*Limits*).

## Licences à vie (gratuites)

L'appli accepte aussi des **licences à vie** qui commencent par `LP1-`. Elles sont vérifiées sans Internet ni Lemon Squeezy et ne s'arrêtent jamais.

- Elles sont signées avec une **clé privée** que seul le propriétaire possède (`cle-privee-NE-PAS-PARTAGER.pem`). L'appli ne contient que la clé publique, qui sert à vérifier la signature mais pas à en créer.
- Pour en créer une nouvelle, par exemple pour un ami : `node generer-cle.js "Prénom"`, dans le dossier qui contient la clé privée.
- **Ne mets jamais la clé privée sur GitHub.** Si elle fuite, change la paire de clés : génère-en une nouvelle et remplace `LIFETIME_PUBLIC_KEY` dans `src/main.js`. Les anciennes licences ne marcheront plus.
- Une licence à vie partagée publiquement marchera chez tout le monde. Ne la donne qu'aux personnes de confiance.

## Bon à savoir

- **Signature de code** : pour supprimer l'alerte SmartScreen, il faut un certificat de signature (environ 100 à 300 € par an, ou Azure Trusted Signing pour moins cher). Ce n'est pas obligatoire pour démarrer.
- **Compteur gratuit** : il est stocké sur le PC. Un utilisateur averti peut le remettre à zéro. Pour une limite impossible à contourner, il faudrait un petit serveur, ce que l'on peut ajouter plus tard.
- **Polices** : elles se chargent depuis Google Fonts quand Internet est disponible. Hors ligne, l'appli utilise les polices de Windows.
- **Lancer sans compiler** (si Node.js est installé) : `npm install` puis `npm start` dans le dossier.

## Structure

```
laser-pix/
├─ config.json              ← réglages boutique et abonnement
├─ package.json             ← dépendances et réglages de l'installateur
├─ build/icon.png           ← icône de l'appli
├─ src/main.js              ← fenêtre, licence, quota, enregistrement
├─ src/preload.js           ← pont sécurisé interface ↔ Windows
├─ src/renderer/index.html  ← interface et traitement d'image
└─ .github/workflows/build.yml ← compilation automatique du .exe
```
