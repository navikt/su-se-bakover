---
name: saniter-route
description: Valider strenger i JSON-bodyen til én rute under web/routes etter klage-malen - withValidertBody, makslengde via sti(), ett svar-punkt med either/bind/merge og tester. Bruk når brukeren ber om å sanitere eller validere strenger i en rute.
license: MIT
compatibility: Kotlin/Ktor web-modulen i su-se-bakover
metadata:
  domain: backend
  tags: kotlin ktor inputvalidering sikkerhet routes
---

# Saniter strenger i en rute

Oppskrift for å flytte én rute fra `withBody` til `withValidertBody`. Regelen om at nye og
endrede ruter skal bruke `withValidertBody`, står i
[`kotlin.instructions.md`](../../instructions/kotlin.instructions.md). Fjern dette skillet når
alle ruter er migrert.

## Før du starter

1. Jobb i repositoryet og på branchen som allerede er sjekket ut. Finn roten med
   `git rev-parse --show-toplevel`, og noter branch og om arbeidstreet er rent. Er det
   uncommittede endringer, si fra før du endrer noe.
2. Finn ruten, det vil si katalogen under
   `web/src/main/kotlin/no/nav/su/se/bakover/web/routes/`, for eksempel `fritekst` eller
   `revurdering/avslutt`:
   - Bruk ruten brukeren oppgir.
   - Ellers utled den fra branchnavnet (`…routes-<rute>-<tidsstempel>`).
   - Er det fortsatt tvetydig, spør.
3. Kontroller at katalogen finnes.

Ikke opprett, gi nytt navn til eller bytt branch, og ikke commit eller push.

## Mal

Les disse før du endrer noe:

- Rutene: `web/src/main/kotlin/no/nav/su/se/bakover/web/routes/klage/KlageRoutes.kt`
- Testene: `web/src/test/kotlin/no/nav/su/se/bakover/web/routes/klage/KlageinnholdInputValidatorTest.kt`
- Valideringen: `web/src/main/kotlin/no/nav/su/se/bakover/web/inputvalidation/`
  (`withValidertBody`, `sti`, `InputFelter`)

## 1) withValidertBody for alle ruter med strengfelt i JSON-bodyen

- Bytt ut `withBody<T>` med `withValidertBody<T>(log, …)`. Det gjelder også ruter som ikke
  validerer noe i dag. Alle strengverdier i bodyen skal sjekkes for gyldige tegn, ikke bare
  fritekst.
- Fjern den manuelle `validerTekst`/`validerFritekst`, `loggInputValidering` og
  `return@withBody` som `withValidertBody` nå tar seg av, og importer som blir ubrukte.
- Makslengde:
  - Felt med en annen grense enn `InputFelter.FRITEKST` skal inn i
    `maksLengde = mapOf(sti(Dto::felt) to N)`. Bruk `sti(Ytre::felt, Indre::felt)` for
    nøstede felt.
  - Felt med standardgrensen skal ikke stå i mapen.

## 2) Ett svar-punkt per rute

Der det er mulig, skriv blokken inne i `withValidertBody` om til
`either { … .mapLeft { … }.bind() … Resultat }.merge().let { resultat -> call.svar(resultat) }`.
Ruter uten body skal ikke endres.

Gi beskrivende navn i stedet for `it` til lambda-parametre som faktisk brukes i blokkene du
skriver om (`withValidertBody`, `either`, `mapLeft`, `map`, `let`, `fold`). Bruk gjerne et navn
avledet av typen, for eksempel `kunneIkkeOppretteKlage -> kunneIkkeOppretteKlage.tilFeilmelding()`.

- Brukes ikke verdien, skal lambdaen ikke ha noe parameter:
  `.mapLeft { FeilResponser.fantIkkeFritekst }`. Ikke legg til navn eller `when` bare for å
  bruke det.
- Klage-malen har fortsatt `when (it)` noen steder. Der verdien brukes, gjelder
  navneregelen. Ikke kopier `it`.
- `it` utenfor blokkene du skriver om (for eksempel i `toDomain`) er utenfor scope.

## Tester

Utvid eksisterende rutetest eller lag `<Rute>InputValidatorTest` etter klage-malen:

- Ett ugyldig felt per case. Forvent `ugyldig_felt_input` med riktig sti og
  `verifyNoInteractions(service)`.
- Tegn utenfor tillatt tegnsett, med tegnene i meldingen.
- Makslengde: én test akkurat på grensen (godtas) og én over (avvises).
- Body som ikke er JSON, og `{}`, skal gi `ugyldig_body`.

## Kjent endring i oppførsel

Feilsvaret går fra `errorJson(..., "ugyldig_fritekst_input")` (eller en egen kode) til
`ugyldig_felt_input` med en `errors`-liste.

- Søk etter den gamle koden i `web/src/test` og `web-regresjonstest`, og oppdater testene
  som gjelder denne ruten.
- Søk også i frontend (`navikt/su-se-framover`), og si fra hvis den er avhengig av koden.
  Bruk en lokal klon som søsterkatalog til repositoryet hvis den finnes, ellers
  `gh search code --repo navikt/su-se-framover '<kode>'`.

## Utenfor scope

`toDomain`-mapping i DTO-ene, andre ruter og endringer i `inputvalidation/`. Foreslå slike
endringer, men ikke gjør dem.

## Stopp og spør hvis

- En DTO bruker `@JsonProperty` eller et annet JSON-navn enn Kotlin-navnet. `sti()`
  forutsetter at de er like.
- Bodyen ikke er JSON (multipart, `receiveText`, query- eller path-parametere).
- Ett svar-punkt ville endre statuskoder eller svar utover feilkoden over.

## Kontroll og levering

Agenten skal ikke kjøre kommandoene selv (se `AGENTS.md`). Gi brukeren kommandoene med
ruten satt inn (bytt `/` i ruten med `.` i pakkenavnet):

```sh
./gradlew :web:spotlessCheck :web:test --tests 'no.nav.su.se.bakover.web.routes.<rute>.*' -q
```

Hvis tester i `web-regresjonstest` er endret, legg til:

```sh
./gradlew :web-regresjonstest:test --tests '<berørte testklasser>' -q
```

Med `-q` vises ingenting når alt er grønt. Be brukeren om resultatet, og les antall tester og
feil fra `web/build/test-results/test/TEST-*.xml` for de berørte testklassene. Rett eventuelle
feil og gi kommandoene på nytt.

Ikke commit. Rapporter diffen, endringer i oppførsel og testresultatet. Foreslå
commitmelding: `Saniter input fra frontend - <Rute>`.
