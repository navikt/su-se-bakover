package no.nav.su.se.bakover.client

import com.github.kittinunf.fuel.core.FuelManager
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.client.dkif.DigitalKontaktinformasjon
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.kodeverk.Kodeverk
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.person.PersonOppslag

interface ClientsBuilder {
    fun build(azureConfig: Config.AzureConfig): Clients
}

data class Clients(
    val oauth: OAuth,
    val personOppslag: PersonOppslag,
    val tokenOppslag: TokenOppslag,
    val pdfGenerator: PdfGenerator,
    val dokArkiv: DokArkiv,
    val oppgaveClient: OppgaveClient,
    val kodeverk: Kodeverk,
    val simuleringClient: SimuleringClient,
    val utbetalingPublisher: UtbetalingPublisher,
    val dokDistFordeling: DokDistFordeling,
    val avstemmingPublisher: AvstemmingPublisher,
    val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
    val digitalKontaktinformasjon: DigitalKontaktinformasjon,
    val leaderPodLookup: LeaderPodLookup
) {
    init {
        // https://fuel.gitbook.io/documentation/core/fuel
        FuelManager.instance.forceMethods = true
    }
}
