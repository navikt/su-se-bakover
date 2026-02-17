package no.nav.su.se.bakover.service.brev

import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.domain.mottaker.BrevtypeMottaker
import no.nav.su.se.bakover.domain.mottaker.MottakerIdentifikator
import no.nav.su.se.bakover.domain.mottaker.MottakerService
import no.nav.su.se.bakover.domain.mottaker.ReferanseTypeMottaker
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.dokumentMedMetadataInformasjonAnnet
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class LagreDokumentMedKopiTest {

    @Test
    fun `informasjon annet henter ikke mottaker og lagrer kun original`() {
        val sakId = UUID.randomUUID()
        val tx = TestSessionFactory.transactionContext
        val dokument = dokumentMedMetadataInformasjonAnnet(sakId = sakId)
        val brevService = mock<BrevService>()
        val mottakerService = mock<MottakerService>()

        lagreDokumentMedKopi(
            brevService = brevService,
            mottakerService = mottakerService,
            mottakerIdentifikator = MottakerIdentifikator(
                referanseType = ReferanseTypeMottaker.SØKNAD,
                referanseId = UUID.randomUUID(),
                brevtype = BrevtypeMottaker.VEDTAKSBREV,
            ),
            sakId = sakId,
        )(dokument, tx)

        verifyNoInteractions(mottakerService)
        verify(brevService).lagreDokument(dokument, tx)
        verifyNoMoreInteractions(brevService)
    }
}
