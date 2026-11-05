# Changelog

Neues kommt sofort unter „Unveröffentlicht“. Eine Beta (`v1.4-beta.2`) bekommt ihren eigenen Abschnitt, eine volle Version (`v1.4`) fasst alle ihre Betas zusammen. Ablauf: [docs/RELEASING.md](docs/RELEASING.md).

## Unveröffentlicht

### Neu
- **Apps in einer Gruppe anordnen:** Lange auf eine Gruppe drücken → „Apps anordnen“, dann mit ↑ und ↓ verschieben. Ohne eigene Reihenfolge bleibt die Gruppe alphabetisch.

### Verbessert
- **Übersichtlicheres „Neu in Birdy“:** Jede Neuerung hat einen kurzen Titel mit einer Zeile Erklärung darunter, nach Neu, Verbessert und Behoben getrennt.

### Intern
- Google-Play-Hinweise enthalten nur noch, was seit dem letzten Upload auf demselben Track neu ist (Tags `play/<track>/<run>-<version>`), mit dem ersten Satz jedes Punkts, wenn er in die 500 Zeichen passt.
- Projekt-Skill `.claude/skills/changelog`: wie Einträge aussehen und wie sie vor jedem Release durchgegangen, zusammengefasst und gekürzt werden.

## 1.4-beta.2

### Neu
- **„Neu in Birdy“ nach einem Update:** Einmal nach jedem Update zeigt Birdy, was sich geändert hat, direkt aus diesem Changelog.
- **Zeitlimits pro App:** Lange auf eine App drücken → „Zeitlimit…“. Ein Tageslimit (Summe ab Mitternacht) und Schonzeiten morgens und abends (von–bis, abends auch über Mitternacht). Gesperrte Apps zeigen eine Sperrseite statt der App.
- **Strenger Modus:** Als Bedienungshilfe fängt Birdy gesperrte Apps immer ab, auch wenn sie über Benachrichtigungen, „Zuletzt verwendet“ oder Links geöffnet werden, und unterbricht eine App, sobald ihr Limit aufgebraucht ist oder die Schonzeit beginnt. Braucht die Freigaben „Nutzungsdatenzugriff“ und „Bedienungshilfe“; die Birdy-App und jede Limit-Seite führen dorthin.
- **Bewusst öffnen:** Apps mit Zeitlimit fragen vor jedem Öffnen „Wie lange möchtest du jetzt hier sein?“ (5, 10, 15 oder 30 Minuten, höchstens so viel wie heute noch übrig ist). Ist die Zeit um, sperrt Birdy die App; danach gilt eine einstellbare Pause zwischen Sessions, unabhängig vom Tageslimit. Pro App abschaltbar.
- **Kontakte in der Suche** (Einstellung): Passende Kontakte erscheinen beim Suchen auf der anderen Seite als die Apps; antippen öffnet den Kontakt. Fragt beim Einschalten nach der Berechtigung.
- **Websuche, wenn nichts passt** (Einstellung, an): Findet die Suche weder App noch Kontakt, sucht Enter den Begriff in Chrome (ohne Chrome im Standardbrowser). Enter nimmt sonst den besten App-Treffer, ohne App den ersten Kontakt.
- **Startscreen anordnen:** Lange auf eine App oder Gruppe drücken → „Startscreen anordnen“, dann mit ↑ und ↓ verschieben. „Fertig“ oder Zurück beendet das Anordnen.
- **Suche in der App-Auswahl** für Zeitlimits: Suchfeld oben, Treffer am Namensanfang zuerst, Enter nimmt den ersten.
- **Zeitlimits haben eine eigene Einstellungsseite** mit allen Apps mit Limit, „App hinzufügen“ und dem Status des strengen Modus.
- **Warnung bei fehlenden Freigaben:** Wird ein Limit gesetzt, das ohne Nutzungsdatenzugriff oder strengen Modus nicht greift, sagt Birdy sofort „Achtung“, erklärt warum und führt mit „Jetzt einrichten“ Schritt für Schritt zu jeder fehlenden Einstellung, bis „Alles bereit ✓“. Fehlt später eine Freigabe (z. B. nach einem Update entzogen), warnt der Startscreen einmal.
- **Gruppen auf dem Startscreen:** Lange auf eine App drücken → „Zu Gruppe hinzufügen…“ legt sie in eine bestehende oder neue Gruppe. Die App wird kopiert und bleibt in der Liste. Antippen klappt die Gruppe auf, lange drücken wählt Apps per Checkliste aus, benennt um oder löscht die Gruppe. Gruppen zählen zu den 7 Plätzen.
- **Herunterwischen öffnet die Benachrichtigungen**, wie auf Pixel-Handys. Abschaltbar in der Birdy-App.
- **Beim Hochwischen gleich tippen** (Einstellung): Die Tastatur geht mit der App-Liste auf.
- **Suchtreffer unten** (Einstellung): Treffer stehen beim Suchen über dem Suchfeld, der beste ganz unten am Daumen.
- **Bester Suchtreffer hervorgehoben:** Er bleibt weiß, die übrigen werden grau. Enter öffnet ihn.

### Verbessert
- Suche: Apps, deren Name mit dem Suchbegriff beginnt, kommen vor Apps, bei denen nur ein späteres Wort passt („s“ → Signal, Slack, Spotify, dann Play Store).

### Wichtig
- Die App-ID ist jetzt `birdy.minimal` (passend zum Eintrag im Play Store). Android sieht diese Version als neue App: Eine bisher per APK installierte Birdy-Version wird nicht ersetzt. Neue Version installieren, als Startbildschirm festlegen und die alte deinstallieren.

### Behoben
- Nach dem Zurückkehren aus einer App (z. B. mit der Zurück-Geste) war die App-Liste noch offen, statt auf dem Startscreen zu landen.

### Intern
- Release-Ablauf mit Betas: Betas (`v1.4-beta.N`) werden GitHub-Vorabversionen mit eigenem Changelog, volle Versionen (`v1.4`) fassen alle Betas zusammen und gehen automatisch zu Google Play (geschlossener Test „Alpha“, einstellbar). `tools/changelog.py` pflegt den Changelog und erzeugt die Store-Hinweise. Siehe `docs/RELEASING.md`.
- Vorbereitung für Google Play: Hinweis mit Zustimmung, bevor Birdy zur Bedienungshilfe führt; Datenschutzerklärung (`PRIVACY.md`, verlinkt in der Birdy-App); die CI baut zusätzlich ein App Bundle (AAB); Store-Texte, Grafiken und eine Anleitung für die Play Console unter `docs/play/`.
- UI-Tests mit Robolectric, die die echte Startscreen-Activity bedienen. Die CI baut mit JDK 21.

## 1.3

Oft genutzte Apps oben in der Liste, Sortierung nach Installationsdatum, schwarzer Hintergrund für die App-Übersicht. Ältere Versionen: siehe [Releases](../../releases).
