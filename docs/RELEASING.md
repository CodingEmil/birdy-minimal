# Versionen veröffentlichen

Zwei Arten von Versionen:

| | Beta | Volle Version |
|---|---|---|
| Tag | `v1.4-beta.1`, `v1.4-beta.2`, … | `v1.4` |
| GitHub | Vorabversion mit APK | Release mit APK |
| Google Play | nein (siehe unten) | ja: geschlossener Test „Alpha“, später Produktion |
| Changelog | eigener Abschnitt `## 1.4-beta.N` | ein Abschnitt `## 1.4`, aus allen Betas zusammengeführt |
| In der App | „Neu in Birdy 1.4-beta.2“ nach dem Update | „Neu in Birdy 1.4“ nach dem Update |

Ein Push auf `main` baut und testet nur, er lädt nichts hoch.

Wie Einträge aussehen und wie sie vor einem Release durchgegangen werden: `.claude/skills/changelog/SKILL.md` (Kurzform: `- **Titel:** ein bis zwei kurze Sätze`, vor jedem Release zusammenfassen und kürzen).

## Beta

1. Alles Neue steht in `CHANGELOG.md` unter `## Unveröffentlicht` (Abschnitte `### Neu`, `### Verbessert`, `### Behoben`, `### Intern`).
2. `python3 tools/changelog.py beta 1.4-beta.2`: macht daraus `## 1.4-beta.2` und legt ein neues, leeres „Unveröffentlicht“ an. Committen und nach `main` bringen.
3. Tag setzen:
   ```
   git fetch origin
   git tag v1.4-beta.2 origin/main
   git push origin v1.4-beta.2
   ```

## Volle Version

1. `python3 tools/changelog.py release 1.4`: führt „Unveröffentlicht“ und alle `## 1.4-beta.N` zu einem `## 1.4` zusammen (nach Wichtig, Neu, Verbessert, Behoben, Intern sortiert, ohne Doppelungen). Committen und nach `main` bringen.
2. Tag `v1.4` setzen (wie oben). Die CI baut, erstellt den GitHub-Release und lädt das Bundle zu Google Play hoch. Die Store-Versionshinweise (max. 500 Zeichen) erzeugt sie aus demselben Abschnitt, aber nur mit dem, was es seit dem letzten Upload auf diesen Track noch nicht gab (siehe unten).

## Play-Hinweise: nur Neuerungen

Nach jedem Play-Upload setzt die CI ein Tag `play/<track>/<lauf>-<version>` (z. B. `play/alpha/57-1.4`). Beim nächsten Upload auf denselben Track vergleicht `tools/changelog.py play` die Punkte mit dem `CHANGELOG.md` dieses Tags und schreibt nur neue Punkte in „Neuerungen“. Eine volle `1.4` nach `1.4-beta.2` auf demselben Track zeigt also nur, was nach der Beta dazukam. Ohne Tag (erster Upload auf einem Track) stehen alle Punkte der Version drin.

Uploads von vor diesem Mechanismus haben kein Tag. Für den Upload von `1.4-beta.2` einmalig nachtragen (Track anpassen, falls nicht `alpha`):
```
git tag play/alpha/0-1.4-beta.2 5a036dc
git push origin play/alpha/0-1.4-beta.2
```

## Einstellungen (GitHub → Settings → Secrets and variables → Actions → Variables)

- `PLAY_TRACK`: wohin volle Versionen gehen. Ohne Angabe `alpha` (geschlossener Test). Nach der Freigabe für die Produktion auf `production` setzen.
- `PLAY_BETA_TRACK`: leer lassen, damit Betas nicht zu Google Play gehen. Wer Betas selbst am Handy testen will, setzt `internal`; dann landen sie im internen Test (nur interne Tester), nicht bei den Alpha-Testern.

Der Upload braucht das Secret `PLAY_SERVICE_ACCOUNT_JSON` (Einrichtung: [play/README.md](play/README.md)).

## Hinweis zu Betas ohne Google Play

Die Beta-APK von GitHub lässt sich nicht über die Play-Version installieren (andere Signatur), und Play Protect blockiert APKs mit Bedienungshilfe. Zum Ausprobieren also entweder `PLAY_BETA_TRACK=internal` setzen oder die APK per ADB auf ein Testgerät ohne Play-Version spielen.
