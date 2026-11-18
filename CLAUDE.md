# Aura — contexte projet pour Claude Code

Launcher Android minimaliste, gratuit, sans pub ni pistage, inspiré des launchers « liste de texte »
(type Niagara) mais avec son propre nom et son propre design. Ne pas copier la marque, les visuels ou
les textes d'un launcher existant.

## L'utilisateur

- Parle **français** : réponses, textes de l'interface et messages de commit en français.
- Teste sur son **téléphone Nothing** (Nothing OS, Android récent, 13+). Il n'est pas développeur Android :
  donner des instructions pas à pas, concrètes, avec les noms exacts des menus.
- Installe les APK depuis les **Releases GitHub** (lien direct vers le `.apk`).

## Stack

- Kotlin 2.1.0, Jetpack Compose (BOM 2024.12.01), Material 3, AGP 8.7.3, Gradle 8.11.1 (wrapper).
- `minSdk 26`, `targetSdk`/`compileSdk 35`, JDK 17.
- Paquet / applicationId : `com.aura.launcher`. Une seule activité, pas de Fragment, pas de ViewModel :
  l'état vit dans `LauncherState` (états Compose) et les actions passent par l'interface `LauncherActions`
  implémentée par `MainActivity`.

## Arborescence utile

```
app/src/main/java/com/aura/launcher/
  MainActivity.kt         Activité HOME : cycle de vie, permissions, implémente LauncherActions
  LauncherState.kt        État observable + interface LauncherActions
  AppEntry.kt             Modèle d'app (clé "paquet/activité#serialProfil", tri, normalisation accents)
  AppRepository.kt        Liste des apps via LauncherApps (profils pro inclus), icônes (cache LRU), écoute des installs
  Prefs.kt                SharedPreferences : favoris, masquées, renommages, réglages, onboardingDone
  NotificationService.kt  NotificationListenerService + NotificationStore (StateFlow) : points, aperçus, jetons média
  MediaWatcher.kt         Mini-lecteur : MediaSessionManager, avec secours via le jeton de la notification média
  LockService.kt          AccessibilityService minimal : GLOBAL_ACTION_LOCK_SCREEN (double-toucher)
  WidgetRepository.kt     Hôte de widgets (AppWidgetHost, id 0x4157) : widget par app dans la carte de l'app
  ui/
    LauncherScreen.kt     Racine : accueil + tiroir + réglages + assistant + dialogue de renommage, gestion du Retour
    HomeScreen.kt         Horloge, date, mini-lecteur, favoris ; gestes (haut = tiroir, bas = notifications,
                          appui long = réglages, double-toucher = verrouiller)
    AppDrawer.kt          Recherche (lancement auto si 1 résultat), liste alphabétique, barre de lettres A–Z
    AppRow.kt             Ligne icône + nom + point de notification + aperçu, menu d'appui long
    MediaCard.kt          Mini-lecteur (icônes dessinées en Canvas, pas de bibliothèque d'icônes)
    SettingsScreen.kt     Réglages + bloc Diagnostic (notifications / média)
    OnboardingScreen.kt   Assistant au premier lancement (écran d'accueil, accès notifications, verrouillage)
    RenameDialog.kt, Theme.kt
app/src/main/res/xml/lock_service.xml   Config du service d'accessibilité
app/aura-debug.keystore                 Clé de signature versionnée (voir plus bas)
.github/workflows/build.yml             CI : compile l'APK release et publie une Release sur main
```

## Compiler et installer

- En local : `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
  (nécessite le SDK Android, `local.properties` avec `sdk.dir=...`, ou Android Studio).
- Installer sur le téléphone branché en USB (débogage USB activé) :
  `adb install -r app/build/outputs/apk/release/app-release.apk`
- Le build release est minifié (R8). Il n'y a pas de réflexion sur les classes de l'app, à part
  l'appel `StatusBarManager.expandNotificationsPanel` (classe du framework, pas concernée par R8).

## CI / Releases

- Chaque push déclenche `.github/workflows/build.yml` (JDK 17 + `assembleRelease`).
- Sur `main`, une Release `v0.1.<run_number>` est publiée avec `Aura-<run_number>.apk`.
  `versionCode` = `GITHUB_RUN_NUMBER` : il augmente automatiquement, donc l'APK s'installe par-dessus.
- Lien à donner à l'utilisateur : `https://github.com/pierreborg41120-code/Aura/releases/latest`
- La CI a toujours réussi jusqu'ici, mais **une compilation réussie ne veut pas dire que ça marche
  sur le téléphone** : demander un retour à l'utilisateur après chaque version.

## Signature

`app/aura-debug.keystore` (mot de passe `android`, alias `androiddebugkey`) est **volontairement
versionné** : tous les APK ont la même signature, ce qui permet les mises à jour sans désinstaller.
Ne pas le changer, sinon l'utilisateur devra désinstaller Aura et perdra ses favoris. Avant une
éventuelle publication sur un store, passer à une vraie clé stockée dans les secrets GitHub.

## Pièges déjà rencontrés (important)

1. **Play Protect bloque l'installation** (« Appli bloquée pour protéger votre appareil ») depuis
   qu'Aura déclare un NotificationListenerService et un AccessibilityService. Contournement :
   Play Store → photo → Play Protect → ⚙ → désactiver « Analyser les applis », installer, **réactiver**.
   La v0.1.1, sans ces services, s'installait sans blocage.
2. **Paramètres restreints (Android 13+)** : pour un APK installé depuis le navigateur, l'accès aux
   notifications est refusé (« L'accès a été refusé à cette appli »). Solution : Paramètres → Applications →
   Aura → ⋮ → « Autoriser les paramètres restreints » (l'option n'apparaît qu'après un premier refus),
   puis activer l'accès aux notifications. **L'utilisateur a réussi cette étape.** Elle peut être à
   refaire après certaines mises à jour.
3. Une app ne peut pas demander ces deux accès par une fenêtre système : on ne peut qu'ouvrir la bonne
   page des réglages. Seul le rôle « écran d'accueil » a une vraie fenêtre (`RoleManager.ROLE_HOME`).
4. Le dépôt GitHub a été renommé `aura` → `Aura` : git affiche « This repository moved », c'est sans gravité.

## État des fonctions (retours de l'utilisateur)

| Fonction | État |
|---|---|
| Liste alphabétique, glisser, barre de lettres, recherche | ✅ testé, OK |
| Épingler à l'accueil, réordonner les favoris | ✅ testé, OK |
| Double-toucher pour verrouiller | ✅ testé, OK |
| Renommer une app | ✅ testé, OK |
| Taille du texte | ✅ testé, OK |
| Points de notification + aperçu sous les favoris | ✅ testé, OK (après « Autoriser les paramètres restreints ») |
| Mini-lecteur de musique | ✅ testé, OK |
| Assistant de configuration (v0.1.4) | ✅ utilisé avec succès par l'utilisateur |
| Batterie + prochain rendez-vous sous l'heure (v0.1.9) | ✅ premier retour positif (« pas mal ») |
| Raccourcis d'app : glisser un favori à droite, menu d'appui long (v0.1.9) | ✅ premier retour positif (« pas mal ») |
| Nouvelle icône « éclipse » + calque monochrome (v0.1.9) | ✅ premier retour positif (« pas mal ») |
| Carte de l'app (glisser un favori : notifications + widget + raccourcis) | ✅ testé, widget Tesla OK (boutons fonctionnels) ; il était écrasé → hauteur revue + réglable, **à revérifier** |
| Nouveau mini-lecteur (grande pochette, halo, progression) | ⏳ à tester ; 1re transition jugée « très moche » → « éclipse » jugée pas assez belle → transition liquide par shader AGSL (ui/LiquidTransition.kt, prototype numpy comparé à la vidéo Niagara de l'utilisateur), **à tester** |
| Finitions de design « éclipse » (tokens dans Theme.kt, ombres de texte, A–Z en loupe) | ⏳ à tester |

Si notifications ou lecteur ne marchent toujours pas : demander une capture du bloc **Diagnostic**
(Réglages d'Aura → « Notifications et musique ») ; chaque ligne ✓/✗ correspond à une étape.

## Pistes pour la suite (proposées, pas encore faites)

- Météo sous l'heure (demande internet + localisation).
- Widgets (AppWidgetHost).
- Option : deux variantes « Aura » (sans services sensibles, pas bloquée par Play Protect) et « Aura Plus ».

## Conventions

- Code commenté en français, noms Kotlin en anglais.
- Commits en français, préfixes `feat:` / `fix:` / `design:`.
- Pas de dépendance superflue : Compose + AndroidX de base seulement.
