---
name: tokenx-auth
description: Vurder og implementer TokenX bare når den konkrete su-se-bakover-flyten krever brukerkontekst
license: MIT
metadata:
  domain: auth
  tags: tokenx auth service-to-service nais
---

# TokenX i su-se-bakover

Gjeldende hovedflyt er Azure AD/Entra ID med on-behalf-of-token fra
`su-se-framover`. Repoet bekrefter ikke én felles mekanisme for alle integrasjoner.

Før en TokenX-endring:

1. Les `.github/domenekontekst/autentisering-og-tilgang.md`.
2. Spor den konkrete klienten og produksjonswiringen.
3. Avklar om brukerkontekst skal følge kallet. Bruk ikke client credentials når den
   skal det.
4. Gjenbruk eksisterende tokenklient/bibliotek. Ikke implementer egen
   klientassertion, signering eller tokencache.
5. Kontroller target audience, Nais-konfigurasjon og accessPolicy i alle miljøer.
6. Valider inbound token med etablert Ktor-provider og test issuer, audience og
   relevante claims med eksisterende testoppsett.

Endring av auth-mekanisme, audience, scopes eller produksjonstilgang krever en
eksplisitt beslutning. Agenten kan deretter implementere og teste endringen.
