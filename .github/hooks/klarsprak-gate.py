#!/usr/bin/env python3
"""Avvis norske AI-markører i tekst som publiseres med git eller gh."""

import json
import re
import shlex
import sys

COMMAND_KEYS = ("command", "commandLine", "cmd", "script")
SHELL_SEPARATOR = re.compile(r"&&|\|\||[;\n]")
GH_PUBLISH_ACTIONS = {"create", "comment", "edit", "review"}
MARKERS = [
    r"banebrytende",
    r"revolusjonerende",
    r"sømløs\w*",
    r"holistisk\w*",
    r"helhetlig\w*",
    r"paradigmeskifte\w*",
    r"digital\w*\s+transformasjon\w*",
    r"spiller\s+en\s+avgjørende\s+rolle",
    r"et\s+betydelig\s+skritt\s+fram?over",
    r"understreker\s+behovet\s+for",
    r"tatt\s+verden\s+med\s+storm",
    r"et\s+vitnesbyrd\s+om",
    r"det\s+er\s+verdt\s+å\s+merke\s+seg",
    r"det\s+er\s+viktig\s+å\s+påpeke",
    r"i\s+dagens\s+verden",
    r"i\s+en\s+(tid|verden)\s+der",
    r"la\s+oss\s+(dykke\s+ned\s+i|utforske)",
    r"oppsummert\s+kan\s+man\s+si",
    r"avslutningsvis",
    r"taler\s+for\s+seg\s+selv",
    r"fremtiden\s+ser\s+lys\s+ut",
    r"håper\s+dette\s+hjelper",
    r"fordype\s+seg\s+i",
    r"sette\s+brukeren\s+i\s+sentrum",
    r"ikke\s+bare\b[^.\n]{0,80}\bmen\s+også",
    r"handler\s+ikke\s+om\b[^.\n]{0,80}\bmen\s+om",
]
MARKER_RE = re.compile(
    "|".join(r"(?<![\wæøå])(?:%s)" % marker for marker in MARKERS),
    re.IGNORECASE,
)


def commands_of(value):
    commands = []
    if isinstance(value, dict):
        for key, nested in value.items():
            if key in COMMAND_KEYS and isinstance(nested, str):
                commands.append(nested)
            else:
                commands.extend(commands_of(nested))
    elif isinstance(value, list):
        for nested in value:
            commands.extend(commands_of(nested))
    return commands


def publishes(command):
    for segment in SHELL_SEPARATOR.split(command):
        try:
            tokens = shlex.split(segment)
        except ValueError:
            tokens = segment.split()

        for index, token in enumerate(tokens):
            if token == "git" and "commit" in tokens[index + 1 :]:
                return True
            if token != "gh":
                continue

            remainder = tokens[index + 1 :]
            for resource in ("issue", "pr"):
                if resource not in remainder:
                    continue
                resource_index = remainder.index(resource)
                if any(
                    action in GH_PUBLISH_ACTIONS
                    for action in remainder[resource_index + 1 :]
                ):
                    return True
    return False


def reason_to_deny(payload):
    arguments = payload.get("toolArgs", payload.get("tool_input", {}))
    for command in commands_of(arguments):
        if not publishes(command):
            continue
        markers = sorted(
            {match.group(0).lower() for match in MARKER_RE.finditer(command)},
        )
        if markers:
            found = ", ".join(f"«{marker}»" for marker in markers)
            return (
                "Teksten som publiseres inneholder norske AI-markører: "
                f"{found}. Skriv konkret og i aktiv form, følg "
                ".github/skills/klarsprak/SKILL.md, og prøv igjen."
            )
    return None


def main():
    try:
        payload = json.load(sys.stdin)
        reason = reason_to_deny(payload) if isinstance(payload, dict) else None
    except (json.JSONDecodeError, OSError, TypeError):
        reason = None

    if reason:
        json.dump(
            {
                "permissionDecision": "deny",
                "permissionDecisionReason": reason,
                "hookSpecificOutput": {
                    "hookEventName": "preToolUse",
                    "permissionDecision": "deny",
                    "permissionDecisionReason": reason,
                },
            },
            sys.stdout,
        )


if __name__ == "__main__":
    main()
