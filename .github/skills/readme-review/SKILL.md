---
name: readme-review
description: Gjennomgå og oppdater README-er etter faktisk oppsett i su-se-bakover
license: MIT
metadata:
  domain: documentation
  tags: readme documentation review
---

# README-review

Les README-en og verifiser alle kommandoer, modulnavn, lenker og tekniske påstander
mot repoet.

Prioriter:

1. hva prosjektet eller modulen gjør
2. minste fungerende lokale oppsett
3. korrekte Gradle-wrapperkommandoer
4. konfigurasjon uten secrets eller interne verdier
5. lenker til dypere arkitektur-, drifts- og domenedokumentasjon

Ikke kopier `AGENTS.md`, domenekontekst, runbooks eller sikkerhetsregler inn i en
README. Ikke legg til generiske badges, `mise`-kommandoer, roadmap eller
kontaktinformasjon som ikke er verifisert. Bruk `klarsprak` ved norsk tekst.

Agenten kan oppdatere README-en direkte når brukeren ber om review og forbedring.
