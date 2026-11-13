# Installasjon og tilganger

1. Installer gcloud CLI: https://docs.cloud.google.com/sdk/docs/install-sdk
2. Installer homebrew med kommandoen: `/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"`
3. Installer nais CLI: https://docs.nais.io/cli/
4. Installer Colima: `brew install colima`
5. Installer docker og docker-compose: `brew install docker`, `brew install docker-compose`
6. Installer kubectl: https://kubernetes.io/docs/tasks/tools/install-kubectl-macos/
7. Be om intelliJ lisens fra slack kanal: #intellij-lisenserver
8. Konfigurer tilgang til brevmodellene i GitHub Packages (macOS).
   Bibliotekene `markup-model` og `markup-dsl` krever autentisering ved nedlasting.
   1. Opprett et eget personal access token (classic) med bare `read:packages` og
      begrenset levetid: https://github.com/settings/tokens.
      Autoriser tokenet for organisasjonens engangspålogging dersom det kreves.
   2. Lagre tokenet i macOS-nøkkelringen. Erstatt `DITT_GH_BRUKERNAVN` med
      GitHub-brukernavnet ditt. Skriv tokenet i den skjulte passordprompten, ikke
      direkte i kommandoen:
      ```sh
      security add-generic-password -a "DITT_GH_BRUKERNAVN" -s "pensjonsbrev-markup-read-token" -w
      ```
   3. Legg følgende i `~/.zshrc`, eller `~/.bashrc` dersom bash-oppsettet ditt
      bruker denne filen. Bruk samme GitHub-brukernavn som ved lagring:
      ```sh
      export GITHUB_ACTOR="DITT_GH_BRUKERNAVN"
      export GITHUB_PACKAGES_READ_TOKEN="$(security find-generic-password -a "DITT_GH_BRUKERNAVN" -s "pensjonsbrev-markup-read-token" -w)"
      ```
   4. Åpne en ny terminal før du kjører `./gradlew`.

   Selve tokenet lagres i nøkkelringen, ikke i shell-oppsettet eller repositoryet.
   Nøkkelringnavnet beskriver formålet, men begrenser ikke tokenets tilganger.
   Global eksport gjør tokenet tilgjengelig for programmer startet fra shell-et.
   Et eksisterende `gpr.token` i Gradle-egenskapene overstyrer `GITHUB_PACKAGES_READ_TOKEN`.
   Gradle bruker ikke lenger `GITHUB_TOKEN` som kilde for pakketilgang.
