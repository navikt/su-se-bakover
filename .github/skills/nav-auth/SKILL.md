---
name: nav-auth
description: Azure AD, OBO, roller og sakstilgang i su-se-bakover
license: MIT
compatibility: Kotlin/Ktor application on Nais
metadata:
  domain: auth
  tags: azure-ad jwt auth ktor nais
---

# Auth i su-se-bakover

Les `.github/domenekontekst/autentisering-og-tilgang.md` og spor den konkrete
provider-en eller klienten.

## Gjeldende modell

- `su-se-framover` sender Azure AD/Entra ID on-behalf-of-token.
- Ktor validerer signatur, issuer og backendens audience.
- Ordinær brukerflyt krever minst én tillatt Azure-gruppe.
- Grupper mappes til domenets roller.
- Routes bruker eksplisitte roller.
- Rolle erstatter ikke person- og sakstilgang.
- `frikort2` er en separat provider med egen audience og rolle.

## Ved endring

1. Finn provider, route, rollemap, tilgangstjeneste, Nais-konfig og tester.
2. Bevar skillet mellom 401, 403 og domenets tilgangsfeil.
3. Følg `AccessCheckProxy` eller `TilgangstyringService` i den aktuelle modulen.
4. Kontroller CEF-audit og sikkerlogg gjennom hele flyten.
5. Kontroller audience og tokenmekanisme i den konkrete klientens
   produksjonswiring. Ikke generaliser mellom integrasjoner.
6. Test gyldig token, feil issuer/audience, manglende rolle og manglende sakstilgang
   etter eksisterende mønster.

Ikke dekod eller be om ekte token. Ikke logg claims som kan inneholde sensitive
data. Ikke implementer en parallell JWT-verifier eller egen tokenkryptografi.
Andre auth-mekanismer beskriver ikke gjeldende hovedflyt og krever en egen
beslutning før innføring.

Agenten kan implementere en avklart auth-endring, men skal stoppe hvis
tilgangsmodell, audience eller auditkontrakt mangler beslutning.
