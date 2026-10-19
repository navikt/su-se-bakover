# Brev og dokument

**Status:** `verified`

Dokumentområdet skiller mellom å generere innhold, lagre dokumentet, journalføre og
bestille distribusjon.

## Dokumentgenerering

Et use case lager en typed `GenererDokumentCommand`. `BrevService.lagDokumentPdf`
returnerer et `Dokument.UtenMetadata`, som deretter får metadata og lagres i riktig
behandlingsflyt.

Ikke bygg brevdata som frie JSON-objekter når en typed command eller brevmodell kan
brukes.

## Lagring

`BrevService.lagreDokument` kan motta `TransactionContext` fordi lagringen krever
flere inserts. Dokument som er en del av iverksettelsen, lagres sammen med
behandling og vedtak når konsistenskravet krever det.

## Journalføring og distribusjon

```text
IkkeJournalførtEllerDistribuert
  -> Journalført
  -> JournalførtOgDistribuertBrev
```

- Distribusjon krever journalpost-ID.
- Et allerede journalført dokument journalføres ikke på nytt.
- Et allerede distribuert brev distribueres ikke på nytt.
- Distribusjonsfeil registreres med antall forsøk og backoff.

## Hendelsesbasert dokumentmodell

Migrerte flyter kan bruke `DokumentHendelseSerie` i stedet for bare den klassiske
tilstanden. Serien består av opptil tre relaterte hendelser i rekkefølge:

```text
GenerertDokumentHendelse
  -> JournalførtDokumentHendelse
  -> DistribuertDokumentHendelse
```

`Dokumenttilstand` utledes fra siste hendelse og kan også uttrykke at dokumentet ikke
skal genereres. Kontroller hvilken modell den aktuelle modulen bruker; ikke bland
persistens- eller overgangsmønstrene uten en eksplisitt migrering.

## Brevvalg

Ikke alle behandlinger sender brev. Brevvalg kan være bestemt av domenet eller
saksbehandleren, avhengig av behandlingstype. Regulering har i dagens domene
`skalSendeVedtaksbrev() == false`.

Stans og gjenopptak sender heller ikke vedtaksbrev. Domenet beskriver disse som
utbetalingsoperasjoner, ikke ekte vedtak.

## Kilder

- `dokument/domain/.../Dokument.kt`
- `dokument/domain/.../GenererDokumentCommand.kt`
- `dokument/domain/.../brev/BrevService.kt`
- `dokument/domain/.../JournalføringOgBrevdistribusjon.kt`
- `dokument/domain/.../DokumentHendelseSerie.kt`
- `dokument/domain/.../Dokumenttilstand.kt`
- `domain/.../brev/command/`
- `domain/.../revurdering/brev/`
