---
name: observability-debugging
description: Feilsøk su-se-bakover med avgrensede metrikker, logger og traces
license: MIT
metadata:
  domain: observability
  tags: debugging mimir loki tempo prometheus
---

# Feilsøking med observerbarhet

1. Avgrens symptom, miljø og tidsrom.
2. Start med metrikker for omfang og tidspunkt.
3. Bruk logger uten å søke etter eller gjengi personopplysninger.
4. Følg trace-ID for én representativ feil, og sammenlign med flere hendelser før
   du konkluderer.
5. Knytt funnet til kode, konfigurasjon eller avhengighet.
6. Implementer minste retting og verifiser med test og relevante signaler.

Bruk eksisterende Grafana/Mimir/Loki/Tempo-tilgang og teamets godkjente verktøy.
Ikke legg tokens eller interne observability-endepunkter i kommandohistorikk. Ikke
kjør produksjonsspørringer eller restart pods uten uttrykkelig godkjenning.

Se `references/query-library.md` for spørringsmønstre, men tilpass metric- og
label-navn til det som faktisk finnes.
