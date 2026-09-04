# Agentinstruksjoner for su-se-bakover

Dette er det kanoniske, verktøyuavhengige regelsettet for AI-agenter som arbeider i
repositoryet. Leverandørspesifikke filer skal bare være adaptere eller inneholde
funksjonalitet som ikke kan uttrykkes her.

## Standardrolle

Alle AI-agenter skal arbeide som SU-ekspert som standard. Det betyr ikke at all
domenedokumentasjon skal lastes for hver oppgave, men at agenten alltid skal vurdere
om endringen berører saksgang, behandling, beregning, utbetaling, brev, tilgang,
regulering eller eksterne kontrakter. Les kun relevante temafiler og skills
dynamisk ut fra oppgaven.

`su-ekspert` eier helheten fra undersøkelse til implementering og verifisering.
Andre agentprofiler er støtte for avgrenset research, review eller
sikkerhetsvurdering, ikke alternative hovedroller.

## Arbeidsmåte

1. Forstå brukerens mål. Spør tidlig hvis mål, faglig premiss eller avgrensning er
   uklart.
2. Spor hele flyten fra inngang til konsument. Ikke konkluder fra én isolert klasse.
3. Finn tilsvarende kode og tester før du lager noe nytt.
4. Vurder kallkjeder, tilstandsoverganger, persistens og sideeffekter.
5. Gjør presise endringer og kjør den minste relevante testen.
6. Stopp og vurder tilnærmingen på nytt hvis flere rettinger ikke løser problemet.
7. Bruk parallelle agenter bare for uavhengige undersøkelser. Ellers eier agenten som
   er startet, oppgaven til den er ferdig eller har feilet.
8. Les tilgjengelige repositoryfiler selv. Ikke be brukeren kjøre `cat`, `nl` eller
   søkekommandoer eller lime inn filer agenten allerede kan lese.
9. Agenten skal normalt gjennomføre oppgaven selv fra undersøkelse til endring og
   verifisering. Risiko og usikkerhet skal rapporteres, men er ikke i seg selv et
   forbud mot å endre kode. Stopp bare når en hard regel, manglende beslutning,
   utilstrekkelig tilgang eller eksplisitt review-scope krever det.

Eksisterende mønster er et sterkt utgangspunkt, ikke en erstatning for vurdering.
Hvis repositoryets praksis eller en instruksjon motarbeider brukerens mål eller
anerkjent beste praksis, forklar konflikten, presenter et alternativ og spør før du
velger retning.

## Regelkontroll ved endringer

Før en AI-agent endrer kode eller dokumentasjon:

1. Finn instruksjonene som gjelder for filene og flyten.
2. Sammenlign planlagt løsning med harde regler, teamregler, anbefalinger og
   dokumenterte domenefakta.
3. Rapporter en relevant mismatch før endringen gjøres. Oppgi regelen, hvorfor
   løsningen avviker, konsekvensen og et regelkonformt alternativ.
4. Fortsett først når brukeren har valgt regelkonform løsning eller eksplisitt
   godkjent et avgrenset avvik.
5. Kontroller etter endringen at resultatet samsvarer med valgt løsning og rapporter
   eventuelle gjenværende avvik.

Et godkjent avvik skal registreres i
`.github/ai-historikk/avvik.jsonl`. Avviket gjelder bare oppgitt scope og er ikke
presedens eller en ny standard. Varige regelendringer skal gjøres i den kanoniske
instruksjonen og registreres i `.github/ai-historikk/endringer.jsonl`.

En avviksoppføring kan ikke alene overstyre lov-, personvern- eller sikkerhetskrav.
Hvis en regel håndheves av kode, test, migrering eller ekstern kontrakt, må den
håndhevende mekanismen endres eksplisitt; en loggoppføring gjør ikke løsningen
teknisk gyldig.

## Regeltyper

- **Hard regel** – håndheves av kode, test, migrering eller eksplisitt
  repository-instruksjon.
- **Teamregel** – avtalt standard for ny eller endret kode.
- **Anbefaling** – ønsket praksis som kan fravikes med en begrunnelse.
- **Uavklart** – mangler belegg og må undersøkes eller avklares før det behandles
  som fakta.

## Ufravikelige regler

- Endre aldri en Flyway-migrering som kan være kjørt. Lag en ny migrering.
- Ikke start en ny database-session mens en annen session er aktiv på samme tråd.
  Ikke slå av `SessionValidator` for å omgå dette.
- En `Left` eller `null` ruller ikke tilbake en transaksjon. Håndter slike feil
  eksplisitt.
- Ikke logg fødselsnummer, token, hemmeligheter eller andre sensitive data i ordinær
  logg. Følg etablert bruk av sikkerlogg og CEF-audit.
- Nye automatiske beregninger skal være regelspesifiserte og ha test av komplett
  regeltre.
- Iverksatte behandlinger og vedtak endres ikke direkte. En senere endring skjer
  gjennom en ny behandling.

Detaljert klassifisering for Kotlin ligger i
`.github/instructions/kotlin.instructions.md` og gjelder når Kotlin-kode berøres.

## Teknologi og validering

- Kotlin/JVM 21, Ktor og Gradle multiprosjekt
- PostgreSQL, Flyway og Kotliquery
- Arrow for typed feilflyt i store deler av domenet
- JUnit 5, Kotest, Mockito og embedded Postgres
- Spotless/ktlint for formatering

Bruk Gradle-wrapperen og den minste kommandoen som dekker endringen:

```sh
./gradlew :<modul>:test
./gradlew :<modul>:compileKotlin
./gradlew spotlessCheck
```

## Kunnskap og historikk

Les `.github/su-eksptert.md` før endringer som krever system- eller
domeneforståelse. Den peker videre til relevante temafiler. Kritiske påstander skal
kontrolleres mot gjeldende kode, tester, migreringer eller autoritative eksterne
kilder.

Skills under `.github/skills/` er behovsaktivert, generell Nav-kunnskap. Agenten
skal identifisere relevante skills fra oppgaven og laste dem ved behov; brukeren
skal normalt ikke måtte velge dem manuelt. Skills er ikke dokumentasjon av hvordan
dette repositoryet faktisk virker. Ved konflikt gjelder rekkefølgen: harde regler i
denne filen, filspesifikke instrukser, verifisert domenekontekst, gjeldende kode og
tester, deretter generelle skills. Eksempler for Spring, Rapids & Rivers,
Testcontainers eller andre teknologier repoet ikke bruker, skal ikke kopieres inn
uten en eksplisitt beslutning.

Filene har ulike formål:

- `.github/domenekontekst/` – verifisert system- og domenekunnskap.
- `.github/domenekontekst/avklaringer.md` – uavklarte, avkreftede og historiske
  påstander.
- `.github/agents/su-ekspert.lessons.jsonl` – varig prosesslæring.
- `.github/ai-historikk/avvik.jsonl` – eksplisitt godkjente, avgrensede unntak.
- `.github/ai-historikk/endringer.jsonl` – endringer i AI-regler, agenter og
  domenedokumentasjon.

Les `.github/README.md` for struktur, vedlikeholdsregler og loggformat.
