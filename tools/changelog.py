#!/usr/bin/env python3
"""Keeps CHANGELOG.md in shape for betas and full releases, and prints release notes.

  changelog.py beta 1.4-beta.2   "## Unveröffentlicht" becomes "## 1.4-beta.2", a new empty one goes on top
  changelog.py release 1.4       merges "Unveröffentlicht" and every "## 1.4-beta.N" into one "## 1.4"
  changelog.py notes 1.4         prints that version's section (GitHub release text)
  changelog.py play 1.4 [OLD_CHANGELOG OLD_VERSION]
                                 prints short store notes, at most 500 characters (Google Play "What's new").
                                 With the CHANGELOG.md of the last Play upload on that track and its version, only
                                 bullets that were not in that upload yet (the CI passes them, see build.yml).

A section is "## <version>" with "### Neu", "### Verbessert", ... and "- " bullets below.
"""
import re
import sys
from pathlib import Path

FILE = Path(__file__).resolve().parent.parent / "CHANGELOG.md"
UNRELEASED = "Unveröffentlicht"
ORDER = ["Wichtig", "Neu", "Verbessert", "Behoben", "Intern"]


def split(text):
    """Returns (preamble, [(heading, body)]) for the "## " sections."""
    parts = re.split(r"^## (.+)$", text, flags=re.M)
    return parts[0], [(parts[i].strip(), parts[i + 1]) for i in range(1, len(parts), 2)]


def join(preamble, sections):
    out = preamble.rstrip() + "\n\n"
    for heading, body in sections:
        out += f"## {heading}\n\n" + (f"{body.strip()}\n\n" if body.strip() else "")
    return out.rstrip() + "\n"


def categories(body):
    """{"Neu": [bullet, ...], ...} plus loose text before the first "### " under ""."""
    cats, current = {}, ""
    for line in body.splitlines():
        m = re.match(r"^### (.+)$", line)
        if m:
            current = m.group(1).strip()
            cats.setdefault(current, [])
        elif line.startswith("- "):
            cats.setdefault(current, []).append(line)
        elif line.strip() and cats.get(current):
            cats[current][-1] += "\n" + line  # continuation of a bullet
        elif line.strip():
            cats.setdefault(current, []).append(line)
    return cats


def render(cats):
    names = [c for c in ORDER if cats.get(c)] + [c for c in cats if c not in ORDER and c and cats[c]]
    out = "\n".join(cats.get("", [])) + ("\n\n" if cats.get("") else "")
    for name in names:
        out += f"### {name}\n" + "\n".join(cats[name]) + "\n\n"
    return out.strip()


def section(sections, heading):
    for h, body in sections:
        if h == heading:
            return body
    return None


def save(preamble, sections):
    FILE.write_text(join(preamble, sections), encoding="utf-8")


def cmd_beta(version):
    preamble, sections = split(FILE.read_text(encoding="utf-8"))
    if section(sections, version) is not None:
        sys.exit(f"## {version} exists already")
    body = section(sections, UNRELEASED)
    if not body or not body.strip():
        sys.exit(f"## {UNRELEASED} is empty: nothing for {version}")
    sections = [(version if h == UNRELEASED else h, b) for h, b in sections]
    save(preamble, [(UNRELEASED, "")] + sections)


def cmd_release(version):
    preamble, sections = split(FILE.read_text(encoding="utf-8"))
    if section(sections, version) is not None:
        sys.exit(f"## {version} exists already")
    beta = re.compile(re.escape(version) + r"-beta\.\d+$")
    merged, rest, seen = {}, [], set()
    for heading, body in sections:  # newest first, so the latest wording of a duplicate wins
        if heading == UNRELEASED or beta.match(heading):
            for cat, items in categories(body).items():
                for item in items:
                    key = re.sub(r"\W+", " ", item).strip().lower()
                    if key not in seen:
                        seen.add(key)
                        merged.setdefault(cat, []).append(item)
        else:
            rest.append((heading, body))
    if not merged:
        sys.exit(f"nothing to release: no {UNRELEASED} and no {version}-beta.N sections")
    save(preamble, [(UNRELEASED, ""), (version, render(merged))] + rest)


def notes_body(version, text=None):
    _, sections = split(FILE.read_text(encoding="utf-8") if text is None else text)
    body = section(sections, version)
    if body is None and "-" in version:  # a beta tagged without its own section
        body = section(sections, UNRELEASED)
    return (body or "").strip()


def cmd_notes(version):
    print(notes_body(version))


def key(item):
    """Bullets compare by their words, so a changed bold mark or comma does not count as new."""
    return re.sub(r"\W+", " ", item).strip().lower()


def shipped(old_text, old_version):
    """Every bullet the last Play upload already had: its own notes and every released section before it."""
    _, sections = split(old_text)
    keys = set()
    bodies = [b for h, b in sections if h != UNRELEASED] + [notes_body(old_version, old_text)]
    for body in bodies:
        for items in categories(body).values():
            keys.update(key(i) for i in items)
    return keys


def play_items(version, old_text=None, old_version=None):
    """(title, first sentence of the detail) for the user-facing bullets, minus what Play users already got."""
    known = shipped(old_text, old_version) if old_text is not None else set()
    items = []
    for cat in ["Neu", "Verbessert", "Behoben"]:  # "Wichtig" and "Intern" are for GitHub readers
        for item in categories(notes_body(version)).get(cat, []):
            if key(item) in known:
                continue
            text = re.sub(r"`", "", " ".join(l.strip() for l in item.splitlines())[2:])
            m = re.match(r"\*\*(.+?)\*\*\s*(.*)$", text)
            title, detail = (m.group(1), m.group(2)) if m else (text, "")
            title = re.sub(r"\*", "", title).rstrip(":. ").strip()
            detail = re.split(r"(?<=[.!?])\s", re.sub(r"\*", "", detail).strip())[0] if detail else ""
            items.append((title, detail))
    return items


def cmd_play(version, old_changelog=None, old_version=None):
    """One "• " line per new bullet: with its first sentence if everything fits into Google's 500 characters,
    else only the bold titles, else as many titles as fit plus "• und mehr"."""
    old_text = Path(old_changelog).read_text(encoding="utf-8") if old_changelog else None
    items = play_items(version, old_text, old_version)
    if not items:
        print("Kleine Verbesserungen und Fehlerbehebungen.")
        return
    short = lambda t: t if len(t) <= 90 else t[:87].rstrip() + "…"
    full = "\n".join("• " + (f"{short(t)}: {d}" if d else short(t)) for t, d in items)
    if len(full) <= 500:
        print(full)
        return
    out = ""
    for t, _ in items:
        line = "• " + short(t)
        if len(out) + len(line) + 1 > 500 - len("• und mehr"):
            out += "• und mehr"
            break
        out += line + "\n"
    print(out.strip())


if __name__ == "__main__":
    commands = {"beta": cmd_beta, "release": cmd_release, "notes": cmd_notes, "play": cmd_play}
    args = sys.argv[1:]
    if not args or args[0] not in commands or len(args) not in ((2, 4) if args[0] == "play" else (2,)):
        sys.exit(__doc__)
    commands[args[0]](*args[1:])
