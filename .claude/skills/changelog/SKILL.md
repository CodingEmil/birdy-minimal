---
name: changelog
description: Pflegt CHANGELOG.md von Birdy und bereitet Releases vor. Benutzen bei jeder Änderung, die committet oder gepusht wird (neuer Eintrag unter „Unveröffentlicht“), und immer vor einer Beta oder vollen Version (alle Einträge durchgehen, kürzen, zusammenfassen, schön machen). Aus diesem Text entstehen das „Neu in Birdy“-Popup in der App, die GitHub-Release-Notes und die Google-Play-Hinweise.
---

# Changelog und Releases

`CHANGELOG.md` ist die einzige Quelle für drei Texte, die echte Nutzer lesen:

| Wo | Was davon | Erzeugt durch |
|---|---|---|
| Popup „Neu in Birdy 1.4“ nach dem Update | `### Neu`, `### Verbessert`, `### Behoben` der Version | `app/build.gradle.kts` → `assets/whatsnew.txt` → `WhatsNew.java` |
| GitHub-Release | der ganze Abschnitt | `python3 tools/changelog.py notes 1.4` |
| Google Play „Neuerungen“ (max. 500 Zeichen) | nur Punkte, die dieser Play-Track noch nicht hatte | `python3 tools/changelog.py play 1.4 ALT.md ALTE_VERSION` (CI) |

Ablauf von Betas und vollen Versionen: `docs/RELEASING.md`.

## 1. Bei jeder Änderung (vor Commit/Push)

Einen Eintrag unter `## Unveröffentlicht` in die passende Kategorie schreiben:
`### Wichtig` (muss man vor dem Update wissen), `### Neu`, `### Verbessert`, `### Behoben`, `### Intern` (nur Entwickler: CI, Tests, Refactoring).

- Gibt es zu dem Feature unter „Unveröffentlicht“ schon einen Punkt, **diesen anpassen** statt einen zweiten anzuhängen.
- Reine Interna gehören nach `### Intern`, nie nach Neu/Verbessert/Behoben; sie erscheinen nicht im Popup und nicht bei Play.

## 2. So sieht ein guter Punkt aus

```
- **Apps in Gruppen anordnen:** Lange auf eine Gruppe drücken → „Apps anordnen“, dann mit ↑ und ↓ verschieben.
```

- Format immer `- **Titel:** Text`. Das Popup zeigt den Titel weiß und fett, den Text grau darunter; Play zeigt den Titel und den **ersten Satz** des Textes.
- **Titel**: 2–5 Wörter, sagt, was man jetzt kann („Kontakte in der Suche“), kein Satz, kein Punkt, keine Fachbegriffe.
- **Text**: 1–2 kurze Sätze, höchstens ~160 Zeichen. Der erste Satz muss allein verständlich sein. Sagt, *wo* man es findet (`Lange auf eine App drücken → „Zeitlimit…“`, „Einstellung in der Birdy-App“). Details wie Grenzfälle, Berechtigungen im Hintergrund oder Implementierung weglassen.
- Du-Form, Deutsch, Menüpunkte in „…“ genau so geschrieben wie in der App.
- Behoben-Punkte aus Nutzersicht: was vorher falsch war, nicht welche Klasse geändert wurde.
- Kein Markdown außer dem fetten Titel; `Backticks` nur für IDs/Dateien (werden im Popup entfernt).

## 3. Vor jeder Beta oder vollen Version: durchgehen und schick machen

Der Abschnitt wird zum Popup. Lang und unsortiert ist er unübersichtlich – deshalb vor `changelog.py beta|release`:

1. **Alle Punkte lesen** (`## Unveröffentlicht`, bei einer vollen Version auch alle `## 1.4-beta.N`) und mit `git log` seit dem letzten Release abgleichen: fehlt etwas Sichtbares? Ist etwas falsch einsortiert?
2. **Zusammenfassen**: Mehrere Punkte zum selben Feature (z. B. Feature + spätere Verbesserung derselben Beta-Reihe) zu einem Punkt machen. Ziel: höchstens ~6 Punkte unter „Neu“, ~4 unter „Verbessert“, ~4 unter „Behoben“. Kleinkram zu einem Punkt bündeln („Kleinere Verbesserungen in der Suche: …“) oder nach Intern.
3. **Sortieren**: Innerhalb jeder Kategorie das Sichtbarste und Wichtigste zuerst.
4. **Kürzen** nach den Regeln aus Abschnitt 2.
5. Dann das Werkzeug laufen lassen: `python3 tools/changelog.py beta 1.4-beta.3` bzw. `release 1.4`. Bei `release` danach den zusammengeführten Abschnitt **noch einmal** nach 2.–4. durchgehen (Betas haben oft dasselbe Feature mehrfach).
6. **Vorschau prüfen**:
   ```
   python3 tools/changelog.py notes 1.4        # GitHub + Grundlage fürs Popup
   git fetch origin 'refs/tags/play/*:refs/tags/play/*'
   last=$(git tag -l 'play/alpha/*' | sort -t/ -k3 -n | tail -1)   # Track wie in PLAY_TRACK
   git show "$last:CHANGELOG.md" > /tmp/last.md
   python3 tools/changelog.py play 1.4 /tmp/last.md "${last#*/*/*-}"
   ```
   Die Play-Vorschau darf nur Neuerungen seit dem letzten Upload auf diesem Track enthalten und soll ohne „• und mehr“ auskommen. Wenn nicht: Punkte weiter zusammenfassen.
7. Dem Nutzer den fertigen Abschnitt und die Play-Vorschau zeigen, dann committen und pushen; Tag erst nach dessen OK setzen.

### Achtung beim Umformulieren

Die Play-Hinweise vergleichen Punkte wortweise mit dem Changelog des letzten Play-Uploads. Einen Punkt, der **schon bei Play war**, nur umzuformulieren, lässt ihn bei Play erneut als „neu“ erscheinen. Bereits veröffentlichte Abschnitte (`## 1.3`, schon hochgeladene Betas) deshalb nicht mehr ändern; beim Zusammenführen einer vollen Version den Wortlaut bereits hochgeladener Beta-Punkte möglichst behalten und nur Punkte zusammenfassen, die noch nie bei Play waren. Im Zweifel die Play-Vorschau aus Schritt 6 ansehen.
