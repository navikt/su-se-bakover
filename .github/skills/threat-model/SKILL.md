---
name: threat-model
description: STRIDE-A-trusselmodellering tilpasset dataflytene i su-se-bakover
license: MIT
metadata:
  domain: security
  tags: threat-modeling stride nais architecture
---

# Trusselmodell

Bruk ved nye dataflyter, endret auth, nye integrasjoner, nye endepunkter eller
vesentlig endret behandling av personopplysninger.

## Fremgangsmåte

1. Avgrens komponenter, aktører, data, konsumenter og tillitsgrenser.
2. Tegn dataflyten fra `su-se-framover` eller annen kaller gjennom backend,
   PostgreSQL, Kafka/MQ og eksterne tjenester.
3. Klassifiser data uten å legge faktiske personopplysninger i dokumentet.
4. Vurder spoofing, tampering, repudiation, information disclosure, denial of
   service, elevation of privilege og abuse.
5. Kontroller eksisterende tiltak i kode og Nais-konfigurasjon før du foreslår nye.
6. Prioriter trusler etter utnyttbarhet og konsekvens, med eier og konkret tiltak.

## Repo-spesifikke kontrollpunkter

- Azure/Ktor JWT-provider, grupper og roller
- person- og sakstilgang via etablert tjeneste/proxy
- CEF-audit og skillet mellom ordinær logg og sikkerlogg
- lovlige behandlingsoverganger og vern av iverksatte vedtak
- transaksjonsgrenser, rollback og ikke-reversible HTTP/MQ-sideeffekter
- parameterisert Kotliquery og kompatibel persistert JSON
- Nais ingress, accessPolicy, secrets og eksterne hosts

Agenten kan implementere avtalte tiltak. Endring av auth-, tilgangs- eller
auditmodell må først være eksplisitt besluttet.
