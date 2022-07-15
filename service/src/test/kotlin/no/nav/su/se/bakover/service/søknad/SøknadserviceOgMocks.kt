package no.nav.su.se.bakover.service.søknad

import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.sak.SaksnummerFactoryProd
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.domain.søknad.SøknadRepo
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.toggles.ToggleService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.saksnummer
import org.mockito.kotlin.mock
import java.time.Clock

/**
 * Kjører verifyNoMoreInteractions() etter runTest
 */
internal data class SøknadserviceOgMocks(
    val søknadRepo: SøknadRepo = mock(),
    val sakService: SakService = mock(),
    val sakFactory: SakFactory = SakFactory(
        clock = fixedClock,
        saksnummerFactory = SaksnummerFactoryProd() { saksnummer },
    ),
    val pdfGenerator: PdfGenerator = mock(),
    val dokArkiv: DokArkiv = mock(),
    val personService: PersonService = mock(),
    val oppgaveService: OppgaveService = mock(),
    val søknadMetrics: SøknadMetrics = mock(),
    val toggleService: ToggleService = mock(),
    val clock: Clock = fixedClock,
    val runTest: SøknadserviceOgMocks.() -> Unit,
) {
    val service = SøknadServiceImpl(
        søknadRepo = søknadRepo,
        sakService = sakService,
        sakFactory = sakFactory,
        pdfGenerator = pdfGenerator,
        dokArkiv = dokArkiv,
        personService = personService,
        oppgaveService = oppgaveService,
        søknadMetrics = søknadMetrics,
        toggleService = toggleService,
        clock = fixedClock,
    )

    init {
        runTest()
        verifyNoMoreInteractions()
    }

    fun allMocks() = listOf(
        søknadRepo,
        sakService,
        pdfGenerator,
        dokArkiv,
        personService,
        oppgaveService,
        søknadMetrics,
    ).toTypedArray()

    private fun verifyNoMoreInteractions() {
        org.mockito.kotlin.verifyNoMoreInteractions(
            *allMocks(),
        )
    }
}
