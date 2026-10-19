---
applyTo: "**/*.{kt,kts,sql,yml,yaml}"
---

# Sikkerhetsregler

- Bruk parameteriserte SQL-spørringer.
- Logg aldri fødselsnummer, token, hemmeligheter eller andre sensitive data i
  ordinær logg.
- Autentisering er ikke tilstrekkelig tilgangskontroll. Følg etablert person- og
  sakstilgang samt CEF-audit.
- Valider issuer, audience, signatur og relevante claims etter eksisterende
  provider-mønster.
- Hardkod aldri secrets og slå aldri av TLS-validering.
- Endring av auth, accessPolicy eller audit krever kontroll av hele flyten.

Bruk `security-owasp` og `security-review` for detaljerte, behovsaktiverte
sjekklister.
