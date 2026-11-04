# Datenschutzerklärung für Birdy

Stand: 4. November 2026

Birdy ist ein minimaler Android-Launcher (App „Birdy“, Paket `birdy.minimal`). Diese Erklärung beschreibt, welche Daten Birdy verarbeitet.

**Kurz:** Birdy sammelt keine Daten, sendet nichts und hat keinen Internetzugriff. Alles, was Birdy speichert, bleibt auf deinem Gerät.

## Was Birdy auf deinem Gerät speichert

- Deine Einstellungen: angeheftete Apps, Gruppen, Eck-Icons und Schalter in der Birdy-App.
- Zeitlimits, die du einstellst: Tageslimits, Schonzeiten, Sessions und Pausen pro App.
- Wenn „Oft genutzte Apps“ eingeschaltet ist: wann du welche App über Birdy geöffnet hast, nur die letzten 3 Tage.

Diese Daten liegen im privaten Speicher der App. Andere Apps können sie nicht lesen, und Birdy selbst überträgt sie nirgendwohin. Wie bei jeder App kann die Gerätesicherung von Android (falls du sie eingeschaltet hast) diese Einstellungen in deinem Google-Konto sichern, damit sie auf einem neuen Gerät zurückkommen. Deinstallierst du Birdy, werden sie vom Gerät gelöscht.

## Berechtigungen und wofür Birdy sie nutzt

- **Liste der installierten Apps:** um sie im Launcher anzuzeigen und zu starten.
- **Nutzungsdatenzugriff** (nur wenn du ihn erlaubst): um zu berechnen, wie lange eine App mit Tageslimit heute im Vordergrund war. Die Werte werden auf dem Gerät aus den Nutzungsdaten von Android gelesen und nicht gespeichert oder weitergegeben.
- **Bedienungshilfe / AccessibilityService „Birdy Zeitlimits“** (nur wenn du den strengen Modus einschaltest): Birdy erfährt dadurch nur, *welche App* gerade in den Vordergrund kommt (ihren Paketnamen), damit gesperrte Apps abgefangen werden können, auch wenn sie über Benachrichtigungen oder „Zuletzt verwendet“ geöffnet werden. Birdy liest keine Bildschirminhalte, keine Eingaben und keine Texte, führt keine Aktionen in anderen Apps aus und überträgt nichts. Bevor du die Bedienungshilfe einschaltest, erklärt Birdy das in einem eigenen Hinweis und fragt nach deiner Zustimmung.
- **Benachrichtigungsleiste öffnen:** für die Geste „Herunterwischen öffnet die Benachrichtigungen“.
- **Hintergrundbild setzen:** nur wenn du in der Birdy-App „Hintergrund schwarz machen“ wählst.
- **Apps deinstallieren anfragen:** um aus dem Lange-drücken-Menü den Deinstallations-Dialog von Android zu öffnen.

## Weitergabe an Dritte

Keine. Birdy enthält keine Werbung, keine Analyse- oder Tracking-Bibliotheken und keine Konten.

## Kinder

Birdy richtet sich nicht speziell an Kinder und sammelt von niemandem Daten.

## Kontakt

Entwickler: CodingEmil
E-Mail: e43399274@gmail.com
Fragen und Hinweise auch gern als Issue unter https://github.com/CodingEmil/birdy-minimal/issues

---

# Privacy policy for Birdy (English)

Birdy is a minimal Android launcher (package `birdy.minimal`). **Birdy collects no data, sends nothing and has no internet access.** Settings, groups and time limits are stored only in the app's private storage on your device (Android's own device backup may include them, as for any app, if you turned it on) and are deleted from the device when you uninstall Birdy.

- **Usage access** (only if you grant it) is used on the device to compute today's foreground time of apps that have a daily limit. Nothing is stored or shared.
- **Accessibility service "Birdy Zeitlimits"** (only if you turn on strict mode) is used solely to learn which app comes to the foreground (its package name), so blocked apps can be covered even when opened from notifications or Recents. Birdy does not read screen content, input or text, does not perform actions in other apps and transmits nothing. Birdy shows a disclosure and asks for consent before you enable it.
- No ads, no analytics, no accounts, no third parties.

Contact: e43399274@gmail.com · https://github.com/CodingEmil/birdy-minimal/issues
