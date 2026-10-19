---
name: security-review
description: Finn og rett sikkerhetsproblemer i su-se-bakover før commit, push eller pull request
license: MIT
metadata:
  domain: security
  tags: security review kotlin ktor nais postgresql
---

# Security review

Les `AGENTS.md`, Kotlin-instruksjonen og relevant domenekontekst. Review hele den
berørte flyten, ikke bare diffen.

## Kontroller

1. **Tilgang:** Autentisering erstatter ikke person- og sakstilgang. Følg
   `AccessCheckProxy` eller `TilgangstyringService` i den aktuelle modulen.
2. **JWT:** Bevar eksisterende Ktor-provider, issuer-, audience-, gruppe- og
   rollevalidering. Kontroller spesialprovidere separat.
3. **Data:** Ikke logg fødselsnummer, token, navn eller andre sensitive data i
   ordinær logg. Kontroller etablert sikkerlogg og CEF-audit.
4. **SQL:** Bruk Kotliquery-parametre; ikke bygg verdier inn i SQL-strengen.
5. **Feilflyt:** Ikke skjul feil som `null`, `Left` eller en suksesslignende
   standardverdi. Kontroller rollback ved transaksjonsfeil.
6. **Deserialisering:** Bruk konkrete typer og kompatibel håndtering av allerede
   lagret polymorf JSON.
7. **Nais:** Kontroller minst mulige accessPolicy-regler, secrets, ingress og
   outbound hosts i alle miljømanifestene.
8. **Forsyningskjede:** Bruk bare skannere og Gradle-oppgaver som allerede finnes i
   repoet. Kontroller SHA-pinning og minimale workflow-rettigheter.

## Resultat

Oppgi alvorlighet, fil og linje, angrepsforutsetning, konsekvens, belegg og presis
retting. Når brukeren ber om å rette funn, implementer dem og kjør den minste
relevante testen. Ikke begrens deg til rapportering.

## Grenser

- Be om beslutning før auth-kontrakt, tilgangsmodell, auditflyt eller
  produksjonstilgang endres.
- Ikke kjør mot produksjon eller hent ut tokens, secrets eller persondata.
- Ikke foreslå Spring Security, JPA, Testcontainers eller verktøy repoet ikke har.
