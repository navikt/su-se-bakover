# SU-ekspertens kunnskapshub

Denne filen er inngangen til verifisert kunnskap om `su-se-bakover`. Navnet
`su-eksptert.md` beholdes fordi det er repositoryets avtalte inngangspunkt.

## Slik brukes kunnskapsbasen

1. Les bare temafilene som er relevante for oppgaven.
2. Bruk dokumentasjonen som kart inn i kodebasen, ikke som erstatning for kode.
3. Verifiser påstander mot gjeldende kode og tester før du endrer oppførsel.
4. Spør brukeren hvis en viktig forutsetning fortsatt er uklar.
5. Oppdater riktig temafil når ny, varig systemkunnskap er bekreftet.

Felles agentregler og prosessen for godkjente avvik ligger i
[`../AGENTS.md`](../AGENTS.md). [README-en](README.md) forklarer hele
AI-strukturen og skillet mellom regler, fakta, læring, avklaringer og avvik.

## Kildestatus

| Status | Betydning |
|---|---|
| `verified` | Bekreftet i gjeldende kode, test, migrering eller konfigurasjon |
| `historical` | Beskriver eldre system eller historiske data, ikke nødvendigvis dagens flyt |
| `unresolved` | Ikke tilstrekkelig bekreftet; må undersøkes eller avklares |
| `rejected` | Avkreftet av nyere kode eller dokumentasjon |

Kode og tester er autoritative for faktisk systemoppførsel. Juridiske og faglige
påstander krever i tillegg en gyldig fagkilde. Nyere endringer i samme flyt veier
tyngre enn eldre commit-, PR- eller Confluence-tekst.

Ikke lagre personnavn, fødselsnummer, gruppe-ID-er, token, hemmeligheter eller
unødvendige interne adresser i kunnskapsfilene.

## Temaer

| Tema | Bruk når du skal forstå |
|---|---|
| [Systemoversikt](domenekontekst/systemoversikt.md) | moduler, lagdeling, teknologistakk og komposisjon |
| [Saksgangen](domenekontekst/saksgangen.md) | hele livsløpet fra søknad til senere endringer |
| [Behandling](domenekontekst/behandling.md) | tilstandsmaskiner, attestering, vedtak og iverksettelse |
| [Beregning](domenekontekst/beregning.md) | satser, fradrag, periodisering og regelspesifisering |
| [Utbetaling](domenekontekst/utbetaling.md) | simulering, utbetalingslinjer, Oppdrag og kvitteringer |
| [Brev](domenekontekst/brev.md) | dokumentgenerering, lagring, journalføring og distribusjon |
| [Autentisering og tilgang](domenekontekst/autentisering-og-tilgang.md) | JWT, roller, sakstilgang og audit |
| [Regulering](domenekontekst/regulering.md) | automatisk/manuell regulering og eksterne beløp |
| [Eksterne repoer](domenekontekst/eksterne-repos.md) | andre SU-applikasjoner og ansvarsgrenser |
| [Avklaringer](domenekontekst/avklaringer.md) | påstander som ikke skal behandles som gjeldende fakta |

## Eksisterende fordypning

- [`docs/historisk-import-og-revurdering.md`](../docs/historisk-import-og-revurdering.md)
  beskriver import, projeksjon, avvik og bruk av historiske Infotrygd-data.

## Instruksjoner og læring

- [`AGENTS.md`](../AGENTS.md) er det kanoniske regelsettet for alle AI-verktøy.
- [Copilot-instruksjonen](copilot-instructions.md) er en Copilot-spesifikk inngang.
- [SU-ekspertagent](agents/su-ekspert.agent.md) brukes for oppgaver som krever bred
  domene- og systemforståelse.
- [Kotlin-instruksjon](instructions/kotlin.instructions.md) gjelder Kotlin-kode.
- [`agents/su-ekspert.lessons.jsonl`](agents/su-ekspert.lessons.jsonl) inneholder
  maskinlesbare observasjoner om SU-agentens arbeidsmåte. Domenefakta skal aldri
  legges der.
- [`ai-historikk/`](ai-historikk/) skiller godkjente avvik fra endringer i
  AI-oppsettet.
