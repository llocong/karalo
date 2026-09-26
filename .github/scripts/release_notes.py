#!/usr/bin/env python3
"""Prints a release's notes as the TV app's What's new page shows them:
{"new": [...], "improved": [...], "fixed": [...]}.

Usage: release_notes.py <version> [--notes-dir release-notes] [--changelog CHANGELOG.md]

Hand-written notes win: release-notes/<version>.md, with "## New", "## Improved" and "## Fixed"
sections of "- " bullets (see CONTRIBUTING.md). Without that file, the version's CHANGELOG.md
section from release-please is used instead: Features become New, Performance Improvements become
Improved and Bug Fixes become Fixed, with commit scopes ("**tv:**") and commit links dropped.
"""

import argparse
import json
import re
import sys
from pathlib import Path

WRITTEN_SECTIONS = {"new": "new", "improved": "improved", "fixed": "fixed"}
CHANGELOG_SECTIONS = {"features": "new", "performance improvements": "improved", "bug fixes": "fixed"}
SCOPE = re.compile(r"^\*\*[^*]+:\*\*\s*")
COMMIT_LINKS = re.compile(r"(\s*\(\[[0-9a-f]{7,40}\]\([^)]*\)\))+\s*$")


def empty():
    return {"new": [], "improved": [], "fixed": []}


def add(notes, key, text):
    text = text.strip()
    if not text:
        return
    text = text[0].upper() + text[1:]
    if text.lower() not in (item.lower() for item in notes[key]):
        notes[key].append(text)


def parse_sections(lines, heading, sections, clean):
    notes = empty()
    key = None
    for line in lines:
        match = heading.match(line)
        if match:
            key = sections.get(match.group(1).strip().lower())
            continue
        bullet = re.match(r"^\s*[-*]\s+(.*)$", line)
        if key and bullet:
            add(notes, key, clean(bullet.group(1)))
    return notes


def written_notes(path):
    return parse_sections(path.read_text().splitlines(), re.compile(r"^##\s+(.+?)\s*$"), WRITTEN_SECTIONS, lambda t: t)


def changelog_notes(path, version):
    lines = path.read_text().splitlines()
    section, inside = [], False
    for line in lines:
        if line.startswith(f"## [{version}]"):
            inside = True
            continue
        if inside and line.startswith("## "):
            break
        if inside:
            section.append(line)

    def clean(text):
        return COMMIT_LINKS.sub("", SCOPE.sub("", text))

    return parse_sections(section, re.compile(r"^###\s+(.+?)\s*$"), CHANGELOG_SECTIONS, clean)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("version")
    parser.add_argument("--notes-dir", default="release-notes")
    parser.add_argument("--changelog", default="CHANGELOG.md")
    args = parser.parse_args()
    written = Path(args.notes_dir) / f"{args.version}.md"
    notes = written_notes(written) if written.exists() else changelog_notes(Path(args.changelog), args.version)
    json.dump(notes, sys.stdout, ensure_ascii=False)
    print()


if __name__ == "__main__":
    main()
