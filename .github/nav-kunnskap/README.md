# Nav-kunnskap for agentene

Repoet har et selvstendig, kuratert utvalg fra
[`navikt/copilot`](https://github.com/navikt/copilot). Det krever ikke nav-pilot
CLI, state, sync eller runtime.

Artefaktene ligger i standardmappene under `.github/`:

- `agents/` inneholder SU-standardrollen og avgrensede støtteprofiler
- `skills/` inneholder behovsaktivert kunnskap og arbeidsflyter
- `instructions/` inneholder korte, filavgrensede regler
- `prompts/` inneholder konkrete Ktor- og Nais-oppgaver
- `hooks/` inneholder den avgrensede klarspråk-kontrollen

[`AGENTS.md`](../../AGENTS.md), filinstruksjonene, verifisert domenekontekst, kode
og tester går foran generell Nav-kunnskap.

## Manuell kompatibilitetskontroll

Utvalget er kontrollert mot Kotlin/Ktor, Kotliquery, Flyway, Arrow, embedded
Postgres, eksisterende Kafka/MQ-klienter, Azure-auth, `SessionValidator` og
repositoryets testmønstre.

| Område | Resultat |
|---|---|
| API | Spring/Bean Validation og obligatorisk RFC 7807 er fjernet; eksisterende Ktor-kontrakt gjelder |
| Database | Flyway-versjon søkes på tvers av migreringsmapper; manuelle transaksjoner og generisk Hikari-oppsett er fjernet |
| Auth | Gjeldende Azure OBO-, rolle- og sakstilgang er dokumentert; generisk JWT-implementasjon skal ikke erstatte provider-en |
| Sikkerhet | Spring/JPA/Node/Go-eksempler er erstattet med repoets Ktor-, Kotliquery- og tilgangsmønstre |
| Testing | Spring/Testcontainers-råd er utelatt; embedded Postgres og lokale testbyggere gjelder |
| Kafka | Rapids & Rivers-råd er utelatt; eksisterende Kafka- og MQ-klienter gjelder |
| Observerbarhet | PII, høy cardinalitet, stille health-fallback og direkte produksjonskall er avgrenset |
| Agenter | SU-ekspert er hovedrollen; støtteagenter brukes bare ved avgrenset research, review eller sikkerhet |

## Inkludert

- `su-ekspert` som standardrolle, med `code-review`, `research` og
  `security-champion` som støtteagenter
- Skills for API, Flyway, PostgreSQL, Nais, auth, TokenX, sikkerhet,
  observerbarhet, arkitektur, feilsøking, klarspråk og dokumentasjon
- Instruksjoner for review, GitHub Actions, Docker og OWASP
- Prompts for Ktor-endepunkt og Nais-manifest
- `klarsprak-gate` for tekst som publiseres med `git commit` eller `gh issue/pr`

## Utelatt

Spring, Rapids & Rivers, Testcontainers, Koin, Java-migrering, scaffolding,
workstation-oppsett, generisk output-stil og nav-pilot-runtime er ikke del av
pakken.

## Oppdatering

Kilden er `navikt/copilot@24ad9ba` fra 4. september 2026. Oppdater manuelt:

1. sammenlign ønsket upstream-fil med lokal variant
2. kontroller alle normative råd mot `AGENTS.md` og faktisk kode
3. porter bare relevant kunnskap
4. oppdater denne kontrolltabellen og `ai-historikk/endringer.jsonl`

Se [tredjepartsmerknaden](THIRD_PARTY_NOTICES.md) for opphav og lisens.
