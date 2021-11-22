package no.nav.su.se.bakover.service.klage

import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.test.fixedClock
import org.mockito.kotlin.mock

internal object KlageServiceBuilder {
    private val klageRepoMock = mock<KlageRepo>()
    private val sakRepoMock: SakRepo = mock()
    private val vedtakRepoMock: VedtakRepo = mock()
    private val brevServiceMock: BrevService = mock()
    private val personServiceMock: PersonService = mock()
    private val microsoftGraphApiMock: MicrosoftGraphApiOppslag = mock()

    fun build() = KlageServiceImpl(
        sakRepo = sakRepoMock,
        klageRepo = klageRepoMock,
        vedtakRepo = vedtakRepoMock,
        brevService = brevServiceMock,
        personService = personServiceMock,
        microsoftGraphApiClient = microsoftGraphApiMock,
        clock = fixedClock,
    )
}
