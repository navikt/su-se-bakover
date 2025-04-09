package no.nav.su.se.bakover.service.søknad

import dokument.domain.journalføring.søknad.JournalførSøknadClient
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfGenerator
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakFactory
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.søknad.SøknadRepo
import no.nav.su.se.bakover.test.defaultMock
import no.nav.su.se.bakover.test.fixedClock
import person.domain.PersonService
import java.time.Clock

/**
 * Kjører verifyNoMoreInteractions() etter runTest
 */
internal data class SøknadServiceOgMocks(
    val søknadRepo: SøknadRepo = defaultMock(),
    val sakService: SakService = defaultMock(),
    val sakFactory: SakFactory = SakFactory(clock = fixedClock),
    val pdfGenerator: PdfGenerator = defaultMock(),
    val journalførSøknadClient: JournalførSøknadClient = defaultMock(),
    val personService: PersonService = defaultMock(),
    val oppgaveService: OppgaveService = defaultMock(),
    val clock: Clock = fixedClock,
) {
    val service = SøknadServiceImpl(
        søknadRepo = søknadRepo,
        sakService = sakService,
        sakFactory = sakFactory,
        pdfGenerator = pdfGenerator,
        journalførSøknadClient = journalførSøknadClient,
        personService = personService,
        oppgaveService = oppgaveService,
        clock = fixedClock,
        // TODO ALDER - muligens må endres når man skal teste aldersting
        kanSendeInnAlderssøknad = false,
    )

    fun allMocks() = listOf(
        søknadRepo,
        sakService,
        pdfGenerator,
        journalførSøknadClient,
        personService,
        oppgaveService,
    ).toTypedArray()

    fun verifyNoMoreInteractions() {
        org.mockito.kotlin.verifyNoMoreInteractions(
            *allMocks(),
        )
    }
}
