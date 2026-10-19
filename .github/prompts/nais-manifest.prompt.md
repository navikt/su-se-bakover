---
name: nais-manifest
description: Endre Nais-manifest etter eksisterende su-se-bakover-mønster
---

Sammenlign alle miljømanifestene under `.nais/` før du endrer dem. Bevar eksisterende
struktur, variabelnavn og deploy-workflow. Kontroller ingress, accessPolicy, auth,
secrets, ressurser, health checks og Prometheus. Ikke legg til CPU-limit. Be om
beslutning før nye GCP-ressurser, produksjonstilganger eller auth-mekanismer
innføres.
