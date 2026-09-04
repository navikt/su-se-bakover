---
applyTo: "**/Dockerfile"
---

# Docker

Følg repoets eksisterende JVM-image og byggemåte. Bruk godkjent Nav/Chainguard- eller
distroless-image, kjør som ikke-root, og legg aldri secrets i image-lag. Ikke innfør
Spring-spesifikke eller generiske multi-stage-oppsett når artefakten bygges utenfor
Docker.
