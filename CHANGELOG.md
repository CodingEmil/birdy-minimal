# Changelog

Neues kommt sofort unter „Unveröffentlicht“. Eine Beta (`v1.4-beta.2`) bekommt ihren eigenen Abschnitt, eine volle Version (`v1.4`) fasst alle ihre Betas zusammen. Ablauf: [docs/RELEASING.md](docs/RELEASING.md).

## Unveröffentlicht

## 1.5-beta.4

### Neu
- **Akzentfarbe Orange:** Datum und Wochentag oben leuchten orange, eine angetippte Gruppe auch, solange sie offen ist. Einstellung in der Birdy-App unter „Startbildschirm“ → „Akzentfarbe“.

## 1.5-beta.3

### Behoben
- **Zeitleiste nach Benachrichtigungen:** Kam eine Benachrichtigung, verschwand die Leiste oben und blieb weg. Jetzt kommt sie wieder, sobald das Banner weg ist.

### Intern
- README neu aufgebaut, mit neuen Screenshots in `docs/images/`.

## 1.5-beta.2

### Neu
- **Startbildschirm nach Wunsch:** App- und Gruppennamen nur in Kleinbuchstaben, und die Statusleiste lässt sich wieder einblenden. Einstellungen in der Birdy-App unter „Startbildschirm“.

## 1.5-beta.1

### Verbessert
- **Restzeit in Shorts und Reels:** Die dünne Leiste oben zeigt jetzt auch die restliche Zeit für Shorts und Reels. Im Player läuft die Zeit, die zuerst um ist, sonst die der App.

## 1.4

### Wichtig
- Die App-ID ist jetzt `birdy.minimal` (passend zum Eintrag im Play Store). Android sieht diese Version als neue App: Eine bisher per APK installierte Birdy-Version wird nicht ersetzt. Neue Version installieren, als Startbildschirm festlegen und die alte deinstallieren.

### Neu
- **Zeitlimits pro App:** Lange auf eine App drücken → „Zeitlimit…“: ein Tageslimit und Schonzeiten morgens und abends. Gesperrte Apps zeigen eine Sperrseite statt der App.
- **Bewusst öffnen:** Apps mit Zeitlimit fragen vor jedem Öffnen, wie lange du bleiben willst. Eine dünne Leiste zeigt die restliche Zeit, danach sperrt Birdy die App.
- **Strenger Modus:** Als Bedienungshilfe fängt Birdy gesperrte Apps immer ab, auch über Benachrichtigungen. Fehlt eine Freigabe, führt Birdy dich hin.
- **Shorts und Reels sperren:** YouTube und Instagram bleiben nutzbar, nur Shorts und Reels verlässt Birdy: sofort oder nach einer Zeit am Tag. Einstellen unter „Shorts & Reels“.
- **Gruppen auf dem Startscreen:** Lange auf eine App drücken → „Zu Gruppe hinzufügen…“. Gruppen und Apps lassen sich mit ↑ und ↓ anordnen.
- **Mehr in der Suche:** Kontakte, Websuche, wenn nichts passt, Treffer unten am Daumen und gleich tippen beim Hochwischen. Einstellen in der Birdy-App unter „Apps & Suche“.
- **Ruhiger Startbildschirm:** Ganz schwarz ohne Statusleiste, auf Wunsch die Temperatur neben dem Datum. Herunterwischen öffnet die Benachrichtigungen.

### Verbessert
- **Aufgeräumte Einstellungen:** Die Birdy-App zeigt nur noch eine kurze Übersicht. Startbildschirm, Apps & Suche, Zeitlimits, Shorts & Reels und die Bedienung haben jeweils eine eigene Seite.
- **„Neu in Birdy“:** Nach jedem Update zeigt Birdy, was sich geändert hat. Unter „Versionshinweise“ ganz unten in der Birdy-App jederzeit wieder.
- **Neue Sperr- und Zeitabfrage-Seite:** Mehr Abstand zum Rand, die Minuten als große Kacheln und ein ruhiges Einblenden.
- **Bessere Suchtreffer:** Apps, deren Name mit dem Suchbegriff beginnt, kommen zuerst. Der beste Treffer bleibt weiß, Enter öffnet ihn.

### Behoben
- **App-Liste blieb offen:** Nach dem Zurückkehren aus einer App, z. B. mit der Zurück-Geste, war die App-Liste noch offen. Jetzt landest du auf dem Startscreen.

### Intern
- Google-Play-Hinweise enthalten nur noch, was seit dem letzten Upload auf demselben Track neu ist (Tags `play/<track>/<run>-<version>`), mit dem ersten Satz jedes Punkts, wenn er in die 500 Zeichen passt.
- Ein von Hand gestarteter Build kann Screenshots der Test-Screens als Artefakt `birdy-screenshots` mitliefern (Option „screenshots“, lokal `-PshotsDir=…`).
- Projekt-Skill `.claude/skills/changelog`: wie Einträge aussehen und wie sie vor jedem Release durchgegangen, zusammengefasst und gekürzt werden.
- Release-Ablauf mit Betas: Betas (`v1.4-beta.N`) werden GitHub-Vorabversionen mit eigenem Changelog, volle Versionen (`v1.4`) fassen alle Betas zusammen und gehen automatisch zu Google Play (geschlossener Test „Alpha“, einstellbar). `tools/changelog.py` pflegt den Changelog und erzeugt die Store-Hinweise. Siehe `docs/RELEASING.md`.
- Vorbereitung für Google Play: Hinweis mit Zustimmung, bevor Birdy zur Bedienungshilfe führt; Datenschutzerklärung (`PRIVACY.md`, verlinkt in der Birdy-App); die CI baut zusätzlich ein App Bundle (AAB); Store-Texte, Grafiken und eine Anleitung für die Play Console unter `docs/play/`.
- UI-Tests mit Robolectric, die die echte Startscreen-Activity bedienen. Die CI baut mit JDK 21.

## 1.3

Oft genutzte Apps oben in der Liste, Sortierung nach Installationsdatum, schwarzer Hintergrund für die App-Übersicht. Ältere Versionen: siehe [Releases](../../releases).
