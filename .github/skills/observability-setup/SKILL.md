---
name: observability-setup
description: Utvid eksisterende metrikker, tracing, logging og health checks i su-se-bakover
license: MIT
metadata:
  domain: observability
  tags: prometheus opentelemetry health metrics
---

# Observerbarhet

Finn eksisterende meter registry, metric-navn, health routes, logging og
Nais-konfigurasjon før du legger til noe.

## Regler

- Mål observerbar adferd og forretningsutfall som er nyttige i drift.
- Bruk lave, avgrensede label-cardinaliteter. Ikke bruk person-, sak-,
  behandlings- eller dokument-ID som label.
- Logg ikke fødselsnummer, token, navn, rå payload eller andre sensitive data.
- Bevar eksisterende `/isalive`, `/isready` og `/metrics`-semantikk. Ikke legg inn
  dyre avhengighetskall i probes uten å kontrollere etablert mønster.
- Fang ikke brede exceptions for å returnere falsk suksess eller skjule rotårsaken.
- Legg tracing rundt integrasjonsgrenser og orkestrering, ikke hver trivielle
  funksjon.
- Oppdater relevante dashboards/alarmer når metric-navn eller produksjonsadferd
  endres.

Bruk referansene for metric-konvensjoner, tracing og varsling. TypeScript- og
generiske scaffolding-eksempler gjelder ikke dette repoet.
