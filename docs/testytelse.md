# Testytelse

Målinger fra 24. og 25. november 2026.

## Endringene

| Tiltak | Hensikt |
|---|---|
| Parallelle JUnit-arbeidstråder i regresjonsmodulen | CPU-antall − 1, med samme harde maksgrense: sju på dagens CI-runner. Klasser kjører parallelt, metodene i hver klasse sekvensielt. |
| Hikari-pool per regresjonsapplikasjon | Gjenbruke databaseforbindelser, med maksimalt fire per pool. Poolen lukkes etter applikasjonen, også ved feil. |
| Hard JUnit-grense i databasemodulen | Begrense ekstra JUnit-arbeidstråder ved venting på testdatabaser til CPU-antall − 1: 15 i den lokale målingen, sju på CI-runneren. |

Testene beholder separate databaser og dagens transaksjonsgrenser.
Databasemodulen har ikke fått forbindelsespool. Produksjonskode endres ikke.

## Hele Gradle-bygget på CI

Alle kjøringene brukte `ubuntu-latest-8-cores`. Tidene gjelder Gradle,
ikke hele CI-jobben eller deployment.

| Oppsett | Varme kjøringer (min:sek) | Median |
|---|---|---:|
| Ordinære masterbygg, før endringene | 5:35, 6:32, 6:37, 6:30, 6:29 | 6:30 |
| Pool, databasetak og to JUnit-arbeidstråder i regresjon | 4:34 | 4:34 |
| Pool, databasetak og sju JUnit-arbeidstråder i regresjon | 5:33, 4:43, 3:51 | 4:43 |

Alle de nye fullmålingene bestod og brukte `build --rerun-tasks --build-cache`
med varm Gradle-cache. Alle 847 oppgaver ble utført, inkludert 50 testoppgaver og
124 kompileringsoppgaver. Ingen av testoppgavene ble hentet fra cache.
Masterbyggene brukte vanlig oppgavecache og andre commits, så sammenligningen
er ikke et kontrollert før/etter-forsøk.

Medianen med sju JUnit-arbeidstråder var 27 % kortere enn mastermedianen.
Målingene støtter pakken samlet, men variasjonen er stor, og vi har bare én
varm måling med to JUnit-arbeidstråder. Sju er derfor ikke dokumentert bedre enn to.
Med tom Gradle-cache tok byggene 6:52 med to og 6:58 med sju JUnit-arbeidstråder.

## Avgrensede lokale målinger

| Måling | Før → etter |
|---|---:|
| Regresjonskjøring med to JUnit-arbeidstråder, pool av → på | 82,78 → 65,14 s |
| Fysiske forbindelsesåpninger i målte regresjonshjelpere | 9 450 → 140 |
| Maksimalt samtidige databaseanskaffelser, med JUnit-tak | 271 → 15 |

Pooltidene er ett lokalt målepar fra arbeidsloggen; de opprinnelige rapportfilene
er senere overskrevet. JUnit-taket gir dokumentert ressurskontroll, ikke en
isolert målt tidsgevinst. Effektene fra ulike forsøk kan ikke summeres.

## Validering og avgrensning

Livssyklustestene i `TestConnectionPoolTest` dekker gjenbruk, lukking og feilflyt.
Heap, `fsync`, databaseprodusenter og opprydding endres ikke. Målekoden er fjernet.
De tidligere intermitterende Zonky-stoppfeilene lokalt er fortsatt uavklart.
De nyere CI-loggene har ingen meldinger om at Postgres ikke kunne stoppes,
men det beviser ikke at den lokale feilen er løst.

`--rerun-tasks` er fjernet fra workflowen etter målingene.
Vanlige bygg gjenbruker dermed uendrede oppgaver og cachede resultater.

Ved behov kan berørte tester og formateringssjekk kjøres med:

```sh
./gradlew :test-common:test --tests '*TestConnectionPoolTest' :web-regresjonstest:test :database:test spotlessCheck
```

CI-grunnlag for de nye fullmålingene:
https://github.com/navikt/su-se-bakover/actions/runs/45578869309
og forsøk 2–4 på https://github.com/navikt/su-se-bakover/actions/runs/45581941807.
Full forsøkshistorikk og fjernet målekode er arkivert lokalt utenfor Git.
