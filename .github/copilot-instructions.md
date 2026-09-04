# Copilot-instruksjoner for su-se-bakover

Følg det kanoniske regelsettet i [`../AGENTS.md`](../AGENTS.md). Denne filen er bare
Copilot-inngangen og skal ikke inneholde en kopi av de felles reglene.

Arbeid som SU-ekspert som standard. Velg relevante skills dynamisk ut fra oppgaven;
brukeren skal ikke måtte aktivere en egen hovedagent for vanlig utvikling.

## Domenekontekst

- [Struktur og vedlikehold](README.md)
- [Nav-kunnskap og avgrensninger](nav-kunnskap/README.md)
- [SU-ekspertens kunnskapshub](su-eksptert.md)
- [Saksgangen](domenekontekst/saksgangen.md)
- [Systemoversikt](domenekontekst/systemoversikt.md)
- [Behandling](domenekontekst/behandling.md)
- [Beregning](domenekontekst/beregning.md)
- [Utbetaling](domenekontekst/utbetaling.md)
- [Brev og dokument](domenekontekst/brev.md)
- [Autentisering og tilgang](domenekontekst/autentisering-og-tilgang.md)
- [Regulering](domenekontekst/regulering.md)
- [Eksterne repoer](domenekontekst/eksterne-repos.md)
- [Uavklarte påstander](domenekontekst/avklaringer.md)

Kotlin-spesifikke regler ligger i
[`instructions/kotlin.instructions.md`](instructions/kotlin.instructions.md).
SU-ekspertens prosesslæring ligger i
[`agents/su-ekspert.lessons.jsonl`](agents/su-ekspert.lessons.jsonl) og er ikke
domenedokumentasjon. Godkjente avvik og endringer i AI-oppsettet ligger under
[`ai-historikk/`](ai-historikk/).
