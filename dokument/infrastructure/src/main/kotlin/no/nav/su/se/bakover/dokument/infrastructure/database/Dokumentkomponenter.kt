package no.nav.su.se.bakover.dokument.infrastructure.database

import no.nav.su.se.bakover.dokument.application.DokumentServices

/**
 * Et forsøk på modularisering av [no.nav.su.se.bakover.web.services.Services] og [no.nav.su.se.bakover.domain.DatabaseRepos] der de forskjellige modulene er ansvarlige for å wire opp sine komponenter.
 */
class Dokumentkomponenter(
    val repos: DokumentRepos,
    val services: DokumentServices,
)
