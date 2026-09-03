# Avklaringer og uverifiserte påstander

Denne filen hindrer at innlimt, historisk eller foreløpig informasjon blir
dokumentert som gjeldende systemoppførsel.

## Statusforklaring

- `unresolved` – krever kodeundersøkelse eller svar fra bruker/fag.
- `historical` – beskriver historikk, ikke nødvendigvis dagens løsning.
- `rejected` – avkreftet av nyere kilde.
- `verified` – avklart påstand som beholdes som historikk fordi den forebygger en
  kjent feilantakelse. Gjeldende systemfakta skal også stå i riktig temafil.

## Faglige avklaringer

| Status | Påstand | Hva må avklares |
|---|---|---|
| `unresolved` | Regelspesifiseringen for automatisk grunnbeløpsregulering er ferdig godkjent | Innlimt tekst hadde statusfelt uten bekreftet endelig status. Faglig eier må bekrefte hva som er godkjent. |
| `unresolved` | IEU ved avslag på uføretrygd på grunn av kort trygdetid skal oppjusteres automatisk | Kildeteksten sa at dette avventet faglig avklaring. Ikke implementer som gjeldende regel uten ny bekreftelse. |
| `unresolved` | Fradragsendring over ti prosent skal automatisk opprette oppgave i alle aktuelle flyter | Regelutkastet beskriver ønsket oppførsel, men implementasjonsomfang og trigger må verifiseres separat. |

## Avklarte tekniske påstander

| Status | Påstand | Hva er verifisert |
|---|---|---|
| `rejected` | PostgreSQL `GREATEST` returnerer alltid `NULL` dersom én operand er `NULL` | PostgreSQL ignorerer normalt `NULL`-argumenter og returnerer `NULL` bare når alle argumentene er `NULL`. |
| `verified` | Historisk råimport kan bruke map-baserte rader | Rådata skal bevares tapsfritt. Dette er et avgrenset unntak og skal ikke normaliseres til generell praksis. |
| `verified` | Nestede database-sessions er forbudt | `SessionValidator` logger stacktrace og kaster `IllegalStateException` når en ny session åpnes på samme tråd. |

## Vedlikehold

Når en påstand avklares:

1. flytt bekreftet kunnskap til riktig temafil
2. behold eventuelt en kort `rejected`-rad dersom samme feilantakelse sannsynligvis
   vil dukke opp igjen
3. noter kode, test eller fagkilde som belegg

Ikke legg personnavn eller møtehistorikk her.
