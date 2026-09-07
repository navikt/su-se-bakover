---
name: kotlin-app-config
description: Endre eksisterende type-safe applikasjonskonfigurasjon i su-se-bakover
license: MIT
compatibility: Kotlin application
metadata:
  domain: backend
  tags: kotlin configuration environment
---

# Kotlin-konfigurasjon

Finn eksisterende `ApplicationConfig`, miljøtyper, klientkonfigurasjon og bootstrap
før du endrer noe.

- Utvid eksisterende typed config fremfor å lage en parallell hierarchy.
- Les secrets og miljøspesifikke verdier fra etablert runtime-konfigurasjon.
- Feil ved manglende obligatorisk config skal være tydelig; ikke bruk
  produksjonslignende fallback.
- Hold parsing ved bootstrap-grensen og send typed config til komponentene.
- Ikke logg secret, token, fødselsnummer, gruppe-ID eller full connection string.
- Oppdater relevante testkonfigurasjoner og alle miljømanifestene når en ny verdi
  innføres.

Ikke legg til Konfig, Koin eller en ny config-abstraksjon uten et konkret behov.
