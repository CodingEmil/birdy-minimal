#!/usr/bin/env python3
"""Keeps CHANGELOG.md in shape for betas and full releases, and prints release notes.

  changelog.py beta 1.4-beta.2   "## Unveröffentlicht" becomes "## 1.4-beta.2", a new empty one goes on top
  changelog.py release 1.4       merges "Unveröffentlicht" and every "## 1.4-beta.N" into one "## 1.4"
  changelog.py notes 1.4         prints that version's section (GitHub release text)
  changelog.py play 1.4          prints short store notes, at most 500 characters (Google Play "What's new")

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


def notes_body(version):
    _, sections = split(FILE.read_text(encoding="utf-8"))
    body = section(sections, version)
    if body is None and "-" in version:  # a beta tagged without its own section
        body = section(sections, UNRELEASED)
    return (body or "").strip()


def cmd_notes(version):
    print(notes_body(version))


def cmd_play(version):
    """The bold lead of each user-facing bullet, as a short list that fits Google's 500 characters."""
    cats = categories(notes_body(version))
    lines = []
    for cat in ["Neu", "Verbessert", "Behoben"]:  # "Wichtig" and "Intern" are for GitHub readers
        for item in cats.get(cat, []):
            first = item.splitlines()[0][2:]
            m = re.match(r"\*\*(.+?)\*\*", first)
            text = (m.group(1) if m else first).rstrip(":").strip()
            text = re.sub(r"[*`]", "", text)
            lines.append("• " + (text if len(text) <= 90 else text[:87].rstrip() + "…"))
    out = ""
    for line in lines:
        if len(out) + len(line) + 1 > 500 - len("• und mehr"):
            out += "• und mehr"
            break
        out += line + "\n"
    print(out.strip())


if __name__ == "__main__":
    commands = {"beta": cmd_beta, "release": cmd_release, "notes": cmd_notes, "play": cmd_play}
    if len(sys.argv) != 3 or sys.argv[1] not in commands:
        sys.exit(__doc__)
    commands[sys.argv[1]](sys.argv[2])
