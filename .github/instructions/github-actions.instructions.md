---
applyTo: ".github/workflows/*.{yml,yaml}"
---

# GitHub Actions

Følg eksisterende reusable workflows og actions i repoet før nye mønstre innføres.
Bruk minst mulige eksplisitte `permissions`, unngå `pull_request_target` med
uklarert PR-kode, og ikke skriv secrets til output. Behold eksisterende SHA-pinning
og versjonskommentarer.
