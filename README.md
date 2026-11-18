# Aura

Un launcher Android minimaliste, gratuit, sans pub ni pistage.

- **Accueil** : l'heure, la date et tes apps favorites en gros texte.
- **Glisser vers le haut** : toutes les apps par ordre alphabétique, avec une barre de lettres sur le bord pour sauter directement à une lettre.
- **Recherche** : filtre pendant la saisie (accents ignorés). Si une seule app correspond, elle s'ouvre toute seule (désactivable).
- **Appui long sur une app** : épingler à l'accueil, masquer, infos, désinstaller. Sur l'accueil : réordonner les favoris.
- **Glisser vers le bas sur l'accueil** : volet de notifications.
- **Appui long dans le vide sur l'accueil** : réglages (icônes, fond d'écran, apps masquées…).
- Toucher l'heure ouvre les alarmes, toucher la date ouvre l'agenda.
- **Notifications** (après avoir donné l'accès dans les réglages d'Aura) : un point sur les apps concernées et un aperçu du dernier message sous chaque favori.
- **Mini-lecteur** : titre, artiste et boutons précédent / lecture / suivant quand de la musique joue.
- **Double-toucher l'accueil** : verrouille l'écran (service d'accessibilité à activer, Android 9+).
- **Renommer** une app, **taille du texte** réglable.

> Android 13+ : pour un APK installé à la main, l'accès aux notifications et le service d'accessibilité
> peuvent être grisés (« paramètre restreint »). Va dans *Paramètres → Applications → Aura → ⋮ →
> Autoriser les paramètres restreints*, puis réessaie.

## Installer sur ton téléphone

1. Ouvre la page **Releases** du dépôt depuis ton téléphone et télécharge le dernier `Aura-XX.apk`.
2. Ouvre le fichier. Android te demandera d'autoriser l'installation depuis ton navigateur : accepte.
3. Appuie sur le bouton Accueil et choisis **Aura**, ou va dans *Paramètres → Applications → Application d'accueil par défaut*.

Les mises à jour s'installent par-dessus l'ancienne version, sans rien perdre.

## Développement

Kotlin + Jetpack Compose, minSdk 26 (Android 8). Chaque push déclenche GitHub Actions qui compile l'APK ; sur `main`, une release est publiée automatiquement.

```
./gradlew assembleRelease
```

> La clé de signature `app/aura-debug.keystore` est volontairement versionnée pour que tous les APK
> aient la même signature. Avant une publication sur un store, il faudra la remplacer par une clé secrète.
