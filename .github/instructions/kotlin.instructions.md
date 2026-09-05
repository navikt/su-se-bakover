---
applyTo: "**/*.kt"
---

# Kotlin-instruksjoner

**Status:** Gjennomgått og avtalt 2026-09-03. Konflikt eller tvil skal fortsatt
løftes til brukeren før regelen brukes som grunnlag for et vesentlig designvalg.

## Modellering

- **Teamregel for ny og endret domenekode:** Følg eksisterende sealed typer, value
  objects og typed feil.
- **Teamregel for ny og endret domenekode:** Foretrekk `Either`/typed domenefeil når
  kalleren kan håndtere feilen. Bruk
  exceptions for brudd på programmerings- eller transaksjonsinvarianter i tråd med
  eksisterende mønster.
- **Teamregel for ny og endret kode:** Ikke legg forretningsregler i routes,
  JSON-mappere eller Postgres-repoer. Vurder eksisterende avvik før de eventuelt
  refaktoreres.
- **Anbefaling:** Unngå nullable felt når fravær kan uttrykkes som en egen
  domenetype.
- **Anbefaling:** Foretrekk typed DTO-er og persistensmodeller fremfor rå `Map` og
  `JsonNode`. Oversett eksterne råformater til typed modeller ved
  integrasjonsgrensen.
- **Avgrenset unntak:** Historisk import kan bevare rådata tapsfritt. Hold råtypen
  innenfor importgrensen og konverter før data brukes som domene.

## Database-session og transaksjon

**Hard regel:** Ikke åpne en ny session mens en session allerede er aktiv på samme
tråd. `SessionValidator.validateNotNestedSession` kaster ved nestede sessions.

- **Teamregel:** Ikke kall en callback, service eller repo-metode med ukjent
  databaseoppførsel inne i `withSession` eller `withTransactionContext`. Ved
  batchlesing skal sessionen lukkes før handleren kalles, med mindre handleren
  eksplisitt mottar og gjenbruker samme context.
- **Teamregel når atomisitet kreves:** Send samme `SessionContext` eller
  `TransactionContext` eksplisitt gjennom hele kallkjeden når flere
  databaseoperasjoner skal dele session eller transaksjon.
- **Hard regel med eksplisitte unntak:** Ikke bruk `disableSessionCounter` eller
  endre `SessionValidator` som løsning på feil transaksjonsgrense. Nye unntak må
  avklares særskilt.
- **Obligatorisk vurderingspunkt:** Kode som kombinerer databaseendringer med MQ,
  HTTP eller andre sideeffekter skal ha én synlig orkestrator. Beskriv valgt
  transaksjons-, rekkefølge- og feilstrategi. Orkestratoren kan ligge i service eller
  et annet lag når eksisterende struktur krever det.
- **Hard databasefakta:** En exception som slipper ut av den ytre
  transaksjonsblokken, ruller automatisk tilbake alle databaseendringer som bruker
  samme `TransactionContext`. `Left` og `null` er vanlige returverdier og utløser
  ikke rollback; konverter dem til et kastet avbrudd når utfallet skal rulle tilbake.
  En database-rollback kan ikke trekke tilbake et HTTP- eller MQ-kall som allerede
  er sendt.
- **Gjeldende strategi:** I ordinær iverksettelse av søknadsbehandling, revurdering
  og stans publiseres utbetalingen sist i transaksjonen.
- **Gjeldende strategiunntak:** Automatisk regulering committer vedtak og
  utbetaling før publisering. Feilet publisering markeres og håndteres av retry.
  Ikke flytt reguleringens publisering inn i transaksjonen uten en egen
  arkitekturvurdering.

## Domenetilstander

- **Teamregel for ny og endret kode:** Gjør overganger gjennom domenemetoder og
  returner den nye konkrete typen. Ikke sett behandlingsstatus direkte i route eller
  repo og omgå domenets kontroller.
- **Hard domeneregel:** Ikke konstruer eller `copy` en iverksatt behandling tilbake
  til en kunstig tidligere tilstand. Senere endring skjer gjennom en ny behandling
  eller en eksplisitt returovergang som domenet støtter.
- **Teamregel:** Match sealed hierarkier uttømmende. Bruk bare en generell `else`
  når det finnes en konkret begrunnelse; ellers kan nye tilstander bli skjult.
- **Teamregel:** Gjenbruk eksisterende periode- og tidslinjeverktøy før ny
  perioderegning lages.

## Regelspesifiserte beregninger

- **Hard repositoryregel:** Nye automatiske beregninger skal implementere
  `RegelspesifisertBeregning`.
- **Hard repositoryregel:** Bruk `Regelspesifiseringer` og
  `RegelspesifisertGrunnlag`; ikke skriv løse regelkoder i beregningskoden.
- **Hard repositoryregel:** Nye eller endrede regler skal oppdatere testen av
  komplett regeltre.
- **Hard repositoryregel:** `BeregnetUtenSpesifisering` er bare for historisk
  kompatibilitet og skal aldri brukes for nye beregninger.

## Persistens og Flyway

- **Hard repositoryregel:** Endre aldri en eksisterende migrering som kan ha kjørt.
  Rett feil med en ny migrering.
- **Hard repositoryregel:** Finn neste ledige versjon på tvers av alle relevante
  migreringsmapper, inkludert SQL under `database/src/main/resources/db/migration`
  og Kotlin/Java-migreringer under `database/src/main/kotlin/db/migration`.
- **Hard repositoryregel:** Bruk parameteriserte SQL-spørringer. Ikke bygg
  parameterverdier inn i SQL-strengen.
- **Teamregel:** Behold riktige typer i SQL-parametrene fremfor å gjøre alle verdier
  til løse strenger.
- **Hard repositoryregel:** Ved endring av allerede persistert polymorf JSON skal
  gammel data fortsatt kunne deserialiseres, eller migreres eksplisitt til det nye
  formatet før gammel støtte fjernes.
- **Teamregel for ny og endret kode:** Repoet mapper persistens til domene; det skal
  ikke avgjøre forretningsutfall.

## Feil, logging og personvern

- **Teamregel:** Fang bare `Throwable` eller brede exceptions ved en tydelig
  prosess-, HTTP- eller jobbgrense med nødvendig logging og eksplisitt mapping.
- **Hard regel:** Ikke skjul en feil som en suksesslignende standardverdi. En
  eksplisitt typed eller domenedefinert fallback er tillatt når kalleren kan skille
  den fra suksess.
- **Hard sikkerhets- og personvernregel:** Logg nok kontekst til drift, men aldri
  sensitive persondata, token eller hemmeligheter i ordinær logg.
- **Teamregel:** Verifiser og følg den aktuelle operasjonens etablerte bruk av
  sikkerlogg og CEF-audit. Ikke innfør, flytt eller fjern audit uten å kontrollere
  hele tilgangs- og auditflyten.

## Tester

- **Teamregel når domenelogikk endres:** Test domenetilstand og feiltype, ikke bare
  HTTP-status eller serialisert tekst.
- **Hard repositoryregel når databaseoppførsel endres:** Bruk
  databaseintegrasjonstest ved endring av SQL, migrering eller mapping.
- **Anbefaling:** Gjenbruk testdata-byggere og eksisterende testmønstre.
- **Teamregel:** Ikke skriv om eksisterende testoppsett samtidig med en
  funksjonsendring med mindre det er nødvendig eller avtalt.
- **Anbefaling:** Kjør den minste relevante modul- eller testkommandoen først, og
  utvid til berørte moduler eller full suite når endringens omfang eller resultatet
  krever det.
