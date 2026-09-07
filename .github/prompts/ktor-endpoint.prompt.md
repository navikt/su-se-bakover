---
name: ktor-endpoint
description: Legg til et Ktor-endepunkt etter mønstrene i su-se-bakover
---

Les `AGENTS.md`, Kotlin-instruksjonen og autentiseringskonteksten. Finn en
tilsvarende route og spor hele flyten før du endrer kode.

Implementer endepunktet med typed request/response, eksplisitt rolle- og
sakstilgang, mapping ved integrasjonsgrensen og domenelogikk utenfor routen. Følg
eksisterende feilformat; innfør ikke RFC 7807 eller ny API-versjon uten en eksplisitt
kontraktbeslutning. Legg til den minste testen som dekker domeneutfall, auth og
serialisering som endringen berører.
