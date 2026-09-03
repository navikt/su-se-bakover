# Autentisering, autorisering og hemmeligheter

**Status:** `verified` for forholdene som er eksplisitt beskrevet nedenfor.

## Overordnet modell

`su-se-framover` fungerer som frontend/BFF. Personlige brukere autentiseres med
Entra ID/Azure AD, og frontend kaller `su-se-bakover` med on-behalf-of-token.
Backend har ingen lokal brukerdatabase eller lokale passord for saksbehandlere.

## JWT-validering

Ktor-konfigurasjonen:

- verifiserer signatur med JWK-er fra konfigurert Azure issuer
- bruker konfigurert issuer
- krever audience som matcher backendens client-ID
- krever minst én tillatt Azure-gruppe for den ordinære brukerflyten

En separat `frikort2`-provider krever riktig audience og eksternrollen `frikort`.
Ikke generaliser reglene for denne provider-en til vanlige saksbehandlerkall.

## Roller

Azure-grupper mappes til:

- `Saksbehandler`
- `Attestant`
- `Veileder`
- `Drift`

Routes bruker `authorize` for å kreve minst én eksplisitt tillatt rolle. Manglende
rolle gir 403. Manglende eller ugyldig autentisering håndteres av Ktor sin
autentiseringsflyt.

Gruppe-ID-er er runtime-konfigurasjon og skal ikke kopieres inn i
kunnskapsdokumentasjon.

## Tilgang til person og sak

Rolle er ikke tilstrekkelig for alle operasjoner. Person- og sakstilgang kontrolleres
i tillegg gjennom person-/tilgangstjenestene.

Kodebasen er i overgang:

- eldre webnær kode bruker `AccessCheckProxy`
- modulert kode bruker `TilgangstyringService`

`TilgangstyringService.assertHarTilgangTilSak` finner personene og sakstypen for
saken og kontrollerer tilgang til person. Følg mønsteret i modulen du endrer; ikke
omgå tilgangssjekken ved å kalle repoet direkte.

## Audit og logging

Personoppslag auditeres gjennom etablert CEF-auditflyt. Ordinær logg skal ikke
inneholde fødselsnummer, token eller andre sensitive data. Bruk sikkerlogg bare på
samme måte som eksisterende kode.

## Hemmeligheter

Azure client secret og annen sensitiv konfigurasjon leses fra miljøvariabler ved
runtime. Hemmeligheter skal ikke ligge i kode, tester, dokumentasjon eller workflow.

Repoet bekrefter ikke at alle integrasjoner bruker samme maskin-til-maskinmekanisme.
Kontroller den konkrete klienten og produksjonswiringen før en tokenflyt beskrives.

## Kilder

- `README.md`
- `web/.../AuthenticationConfig.kt`
- `common/infrastructure/.../config/AzureConfig.kt`
- `common/infrastructure/.../brukerrolle/AzureGroupMapper.kt`
- `common/infrastructure/.../web/Authorization.kt`
- `tilgangstyring/application/.../TilgangstyringService.kt`
- `web/.../services/AccessCheckProxy.kt`
- `common/infrastructure/.../audit/CefAuditLogger.kt`
- `.nais/dev-gcp.yaml`
- `.nais/dev-gcp-q1.yaml`
- `.nais/prod-gcp.yaml`
