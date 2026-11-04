# Birdy im Google Play Store veröffentlichen

Schritt für Schritt durch die Play Console, mit allen Antworten zum Kopieren. Texte für den Store-Eintrag: [listing.md](listing.md). Datenschutzerklärung: [PRIVACY.md](../../PRIVACY.md).

## 0. Vorher

- [ ] Branch nach `main` bringen, damit die Datenschutzerklärung unter der festen Adresse erreichbar ist:
      `https://github.com/CodingEmil/birdy-minimal/blob/main/PRIVACY.md`
- [ ] In `PRIVACY.md` unten eine E-Mail-Adresse für Datenschutzanfragen eintragen (Google verlangt einen Kontakt).
- [ ] Ein Tag setzen, z. B. `v1.4-beta.1`. Der Build dazu hat eine sinnvolle Versionsnummer („1.4-beta.1“ statt „dev“).
- [ ] Aus dem Build-Lauf das Artefakt **birdy-aab** laden (enthält `birdy.aab`). Das ist die Datei für die Play Console.

## 1. App anlegen

Play Console → **App erstellen**

| Feld | Eintrag |
|---|---|
| App-Name | Birdy – minimaler Launcher |
| Standardsprache | Deutsch – de-DE |
| App oder Spiel | App |
| Kostenlos oder kostenpflichtig | Kostenlos |

Paketname ist `dev.birdy.minimal`. Er wird beim ersten Hochladen festgelegt und lässt sich danach nicht mehr ändern.

## 2. App-Signatur (wichtig, einmalig)

Beim ersten Release fragt die Console nach der **Play App-Signatur**. Zwei Wege:

- **Empfohlen: vorhandenen Schlüssel verwenden.** „Anderen Signaturschlüssel verwenden“ → „Schlüssel aus Java-Schlüsselspeicher exportieren und hochladen“. Die Console zeigt einen Befehl mit dem PEPK-Tool; ihn auf dem Rechner mit dem Release-Keystore ausführen (Alias `birdy`, das ist derselbe Keystore, der in den GitHub-Secrets als `BIRDY_KEYSTORE_B64` liegt). Dann ist die Play-Version mit der bisher per APK installierten Version identisch signiert, und bestehende Installationen bekommen das Update nahtlos.
- **Einfacher: Google erzeugt den Schlüssel.** Dann lässt sich die per APK installierte Version nicht über den Play Store aktualisieren. Einmal deinstallieren (Startscreen-Einstellungen gehen verloren) und neu aus dem Store installieren.

Die CI signiert das App Bundle mit dem Release-Schlüssel, der damit auch als **Upload-Schlüssel** dient. Verlangt die Console einen getrennten Upload-Schlüssel, kann die CI einen zweiten Keystore aus einem weiteren Secret nutzen; das ist schnell ergänzt.

## 3. App-Inhalte (Richtlinien → App-Inhalte)

**Datenschutzerklärung**
`https://github.com/CodingEmil/birdy-minimal/blob/main/PRIVACY.md`

**Werbung:** Nein, die App enthält keine Werbung.

**App-Zugriff:** Alle Funktionen sind ohne besondere Zugriffsrechte verfügbar (keine Anmeldung). Optional als Hinweis für die Prüfer:

```
Keine Anmeldung nötig. Zum Testen der Zeitlimits: Birdy-App öffnen → Zeitlimits → App hinzufügen → App wählen. „Strenger Modus einschalten“ zeigt zuerst einen Hinweis zur Bedienungshilfe; nach „Zustimmen“ in den Bedienungshilfen „Birdy Zeitlimits“ einschalten.
```

**Einstufung des Inhalts:** Fragebogen ausfüllen, Kategorie „Dienstprogramm, Produktivität, Kommunikation oder Sonstiges“. Alle Fragen zu Gewalt, Sexualität, Glücksspiel, Nutzerinteraktion usw. mit **Nein** beantworten. Ergebnis: USK 0 / PEGI 3.

**Zielgruppe:** 18 Jahre und älter. Die App spricht Kinder nicht gezielt an. (Damit gelten die zusätzlichen Familien-Richtlinien nicht.)

**Datensicherheit**

| Frage | Antwort |
|---|---|
| Erhebt oder teilt deine App erforderliche Nutzerdatentypen? | **Nein** |

Begründung, falls nachgefragt: Birdy hat keine Internet-Berechtigung. Nutzungszeiten und Einstellungen werden nur auf dem Gerät verarbeitet und verlassen es nicht. Daten, die nur auf dem Gerät verarbeitet werden, gelten bei Google nicht als „erhoben“.

**Behörden-App, Finanzfunktionen, Gesundheit, Nachrichten:** jeweils Nein.

## 4. Bedienungshilfe deklarieren (Richtlinien → App-Inhalte → „Accessibility API“)

Die Console fragt nach, sobald das Bundle einen AccessibilityService enthält.

- **Ist deine App ein Barrierefreiheits-Tool?** Nein. (So steht es auch in der App: `isAccessibilityTool="false"`.)
- **Zweck / Beschreibung** (die Console ist meist englisch):

```
Birdy is a home screen launcher with optional digital wellbeing time limits. Users can set, per app, a daily time limit, morning and evening quiet hours, and a session length chosen before each visit. The optional "strict mode" uses the AccessibilityService only to receive TYPE_WINDOW_STATE_CHANGED events and read the package name of the app that comes to the foreground. When that app is blocked by a limit the user set, Birdy shows its own block screen over it. Without this, blocked apps could be opened from notifications or Recents, bypassing the limits.

Birdy does not retrieve window content (canRetrieveWindowContent=false), does not read text or input, does not perform gestures or actions on other apps, and has no internet permission; nothing is stored or transmitted. Before the user is sent to the accessibility settings, Birdy shows a prominent in-app disclosure explaining this, with "Zustimmen" (agree) and "Ablehnen" (decline). Strict mode is optional; all other features work without it.
```

- **Video:** Google verlangt meist ein kurzes Video (YouTube, „nicht gelistet“ reicht). Ablauf, etwa 60 Sekunden, Bildschirmaufnahme am Handy:
  1. Birdy-App öffnen → „Zeitlimits“ → „App hinzufügen“ → z. B. YouTube wählen.
  2. Schonzeit abends so einstellen, dass sie gerade läuft (oder ein Tageslimit von 5 Minuten).
  3. „Strenger Modus einschalten“ antippen → **den Hinweis zeigen** → „Zustimmen“.
  4. In den Bedienungshilfen „Birdy Zeitlimits“ einschalten.
  5. Zurück zum Startscreen, YouTube über eine Benachrichtigung oder „Zuletzt verwendet“ öffnen → Birdys Sperrseite erscheint.
  6. Kurz zeigen, dass sich der strenge Modus in den Bedienungshilfen wieder ausschalten lässt.

Weitere Berechtigungen (Nutzungsdatenzugriff, Apps deinstallieren anfragen, Benachrichtigungsleiste öffnen, Hintergrundbild) brauchen keine eigene Deklaration. Fragt die Console doch nach einer, gilt dieselbe Begründung wie in der Datenschutzerklärung.

## 5. Store-Eintrag (Store-Präsenz → Hauptdetails)

Texte aus [listing.md](listing.md), Grafiken aus diesem Ordner:

- App-Symbol: `icon-512.png`
- Feature-Grafik: `feature-1024x500.png`
- Smartphone-Screenshots: `screenshot-1-…` bis `screenshot-7-…`, in dieser Reihenfolge

Kategorie: **Personalisierung**. Kontakt-E-Mail ist Pflicht (wird öffentlich angezeigt).

## 6. Testen und veröffentlichen

1. **Testen → Geschlossener Test** → Track anlegen → `birdy.aab` hochladen → Tester hinzufügen (E-Mail-Liste oder Google-Gruppe) → zur Prüfung senden.
2. **Gilt für dich die Testpflicht?** Private Entwicklerkonten, die nach dem 13. November 2023 erstellt wurden, müssen vor der Produktion einen geschlossenen Test mit mindestens 12 Testern über 14 Tage am Stück durchführen, und zwar pro App. Ob das für dein Konto gilt, zeigt das Dashboard der App („Für die Produktion bewerben“ bzw. „Produktionszugriff beantragen“). Ältere Konten und Organisationskonten sind ausgenommen.
3. Danach **Produktion → Neuen Release erstellen** → dasselbe Bundle übernehmen → Länder wählen → zur Prüfung senden. Die erste Prüfung dauert meist einige Tage, mit Bedienungshilfe eher länger.

## Später: Updates

Jeder Build auf GitHub erhöht die Versionsnummer automatisch (`versionCode` = Nummer des CI-Laufs). Für ein Update: Tag setzen, `birdy.aab` aus dem Lauf laden, in der Console einen neuen Release anlegen. Das lässt sich später auch automatisieren (Upload per Play Developer API aus der CI).
