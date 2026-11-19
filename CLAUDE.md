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
app/src/main/res/xml/lock_service.xml   Config du service d'accessibilité (verrouillage + limiteur en durée)
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
| Carte de l'app (glisser un favori : notifications + widget + raccourcis) | ✅ testé, widget Tesla OK (boutons fonctionnels) ; il était écrasé → hauteur revue + réglable, revérifié ✅ |
| Mini-lecteur : transition liquide (shader AGSL, ui/LiquidTransition.kt) | ✅ testé, « vraiment top » |
| Mini-lecteur sans fond (posé sur le fond d'écran, bouton lecture en anneau) | ✅ testé, OK |
| Recherche universelle : apps, contacts (READ_CONTACTS), calcul, réglages Android, web (search/, ui/SearchSections.kt) | ✅ testé ; lancement auto trop rapide avec les contacts → 3 lettres + pause 700 ms, revérifié ✅ |
| Tri par utilisation dans chaque lettre + apps suggérées (UsageTracker.kt, compteur local) | ✅ testé, OK |
| Lire / répondre / effacer les notifications dans la carte de l'app (ui/NotificationList.kt) | ✅ testé, OK |
| Météo Open-Meteo, désactivée par défaut, position approximative ou ville (Weather*.kt, ui/WeatherSheet.kt) | ✅ testé, OK |
| Widget libre sur l'accueil, apps musique aux écouteurs, rotation, haptique (ui/HomeWidget*.kt, HeadphonesWatcher.kt) | ✅ testé, OK |
| Finitions de design « éclipse » (tokens dans Theme.kt, ombres de texte, A–Z en loupe) | ✅ testé, OK |
| Thème Néon (onglet Thème de l'éditeur) | ✅ testé, OK |
| Dossiers sur l'accueil (Folders.kt, ui/Folders.kt ; clé « folder:<id> » dans les favoris) | ✅ testé, OK |
| Sauvegarde / restauration des réglages en JSON (Prefs.exportJson/importJson, ui/settings/BackupSettings.kt ; sans widgets ni cache météo) | ✅ testé, OK (import compris) |
| Récap des notifications sous l'heure + feuille groupée par app (ui/NotificationRecap.kt, réglage dans NotificationSettings) | ✅ testé, OK |
| Thèmes Terminal et Aube (TerminalSpec / AubeSpec, `squareCorners`, préréglages dans withTheme) | ✅ testé, OK |
| Limiteur : « Limiter » dans le menu d'appui long, pause de 5 s (ui/LimiterPause.kt), compte du jour (UsageTracker, fichier aura_usage_daily), liste dans les réglages | ✅ testé, OK (pause) |
| Limiteur en durée par jour : « Limiter… » (pause / 5 min–2 h), LockService suit l'app au premier plan (TYPE_WINDOW_STATE_CHANGED, paquet seulement), ScreenTime.kt compte, GLOBAL_ACTION_HOME + écran « Temps écoulé » / « Encore 5 minutes » | ⏳ à tester |
| Concentration : apps mises de côté (cachées de l'accueil et de la liste, pause « Concentration » via la recherche), plage horaire ou 30 min–4 h à la main, ligne sous l'heure, geste (Schedules.kt FocusSettings, ui/Modes.kt, ui/settings/FocusSettings.kt) | ⏳ à tester |
| Bilan de la semaine : 7 barres + total par app, historique « h:aaaammjj:paquet » dans aura_screen_time (28 jours), apps suivies = limitées + concentration (ScreenTime.tracked) | ⏳ à tester |
| Actions rapides dans la recherche : « minuteur 10 », « alarme 7h30 », « sms Julie bonjour », « appeler Julie » (search/QuickActions.kt, permission SET_ALARM) | ⏳ à tester |
| Profils d'accueil Perso / Travail : favoris séparés (clé favorites_work), bascule par le nom sous l'heure, un geste ou un horaire (ProfileSettings, refreshModes toutes les 30 s) | ⏳ à tester |
| Gestes personnalisables : bas, gauche, droite, double-toucher (Gestures.kt, ui/settings/GestureSettings.kt, performGesture) | ✅ testé, OK |
| Icônes : « Thème » (ThemedIcons.kt : silhouette monochrome Android 13 / calque adaptatif / silhouette calculée, dans un cadre du thème) et packs installés (IconPacks.kt, appfilter.xml, logo du pack dans sa carte) — onglet Icônes | ✅ testé, « tout fonctionne nickel » |
| Éditeur « Apparence » : horloges (ui/ClockStyles.kt), polices (res/font), accent Auto/palette, voile, flou du tiroir, alignement (ui/AppearanceEditor.kt, ui/WallpaperLook.kt, Appearance.kt) | ✅ testé, OK (flou compris ; fenêtre rendue translucide au 1er flou via setTranslucent) |

Si notifications ou lecteur ne marchent toujours pas : demander une capture du bloc **Diagnostic**
(Réglages d'Aura → « Notifications et musique ») ; chaque ligne ✓/✗ correspond à une étape.

## Lot « Apparence » (fait le 19/11/2026)

Réglages → « Apparence » ouvre l'éditeur (accueil en aperçu en haut, panneau à onglets en bas, « Terminé »
enregistre, Retour propose d'annuler). Modèle dans `Appearance.kt`, enregistré par `Prefs.appearance`, lu via
`LocalAppearance` / `AuraAccent` dans Theme.kt (`AuraColors.Accent` est un getter sur un état Compose).
`homeFont()` = police + facteur de taille, pour l'accueil et le tiroir seulement.

## Thèmes (19/11/2026)

Onglet « Thème » de l'éditeur Apparence. `ThemeId` + préréglage `Appearance.withTheme()` (Appearance.kt) ;
`ThemeSpec` / `AuraLook.spec` dans Theme.kt : couleurs des surfaces (AuraColors = getters), coins coupés via
`auraShape()`, lignes de balayage via `Modifier.themeBackdrop()`. Premier thème : **Néon** (jaune #FCEE0A,
cyan #00E5FF, police Chakra Petch, horloge NEON avec glitch de 180 ms au changement de minute). Thème original,
« dans l'ambiance » cyberpunk demandée par l'utilisateur : ne jamais reprendre nom, logo ou personnages
d'une œuvre existante (Cyberpunk Edgerunners…). Ajouter un thème = un ThemeSpec + une ligne dans ThemeTab. Icônes : `IconLook` (clé de cache) construit par
`rememberIconLook()` ; les apps absentes d'un pack gardent leur icône (iconback/iconmask des packs pas gérés).

## Pistes pour la suite (proposées, pas encore faites)

- Réglages : chaque fonction a sa section dans `ui/settings/*.kt`, appelée par une ligne de SettingsScreen.kt.
- Thème clair « Papier » : écarté pour l'instant (le texte de l'accueil, posé sur le fond d'écran, doit rester clair).
- Option : deux variantes « Aura » (sans services sensibles, pas bloquée par Play Protect) et « Aura Plus ».

## Conventions

- Code commenté en français, noms Kotlin en anglais.
- Commits en français, préfixes `feat:` / `fix:` / `design:`.
- Pas de dépendance superflue : Compose + AndroidX de base seulement.
