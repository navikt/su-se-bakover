package no.nav.su.se.bakover.service.brev

import arrow.core.right
import dokument.domain.Brevtype
import dokument.domain.Dokument
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.mottaker.MottakerService
import no.nav.su.se.bakover.domain.mottaker.ReferanseTypeMottaker
import no.nav.su.se.bakover.test.dokumentMedMetadataVedtak
import no.nav.su.se.bakover.test.dokumentUtenMetadataInformasjonViktig
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

internal class LagreDokumentMedKopiTest {

    @Test
    fun `lagreVedtaksbrevMedKopi bruker VEDTAK i mottakeroppslag`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val tx = mock<TransactionContext>()
        val dokument = dokumentMedMetadataVedtak(
            sakId = sakId,
            vedtakId = UUID.randomUUID(),
        )

        val brevService = mock<BrevService>()
        val mottakerService = mock<MottakerService> {
            on { hentMottaker(any(), any(), any()) } doReturn null.right()
        }

        val lagre = lagreVedtaksbrevMedKopi(
            brevService = brevService,
            mottakerService = mottakerService,
            referanseType = ReferanseTypeMottaker.REVURDERING,
            referanseId = referanseId,
            sakId = sakId,
        )

        lagre(dokument, tx)

        verify(mottakerService).hentMottaker(
            argThat {
                referanseType == ReferanseTypeMottaker.REVURDERING &&
                    this.referanseId == referanseId &&
                    brevtype == Brevtype.VEDTAK
            },
            eq(sakId),
            eq(tx),
        )
    }

    @Test
    fun `lagreForhandsvarselMedKopi bruker FORHANDSVARSEL i mottakeroppslag`() {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val tx = mock<TransactionContext>()
        val dokument = dokumentUtenMetadataInformasjonViktig().leggTilMetadata(
            metadata = Dokument.Metadata(
                sakId = sakId,
                revurderingId = referanseId,
            ),
            distribueringsadresse = null,
        )

        val brevService = mock<BrevService>()
        val mottakerService = mock<MottakerService> {
            on { hentMottaker(any(), any(), any()) } doReturn null.right()
        }

        val lagre = lagreForhandsvarselMedKopi(
            brevService = brevService,
            mottakerService = mottakerService,
            referanseType = ReferanseTypeMottaker.REVURDERING,
            referanseId = referanseId,
            sakId = sakId,
        )

        lagre(dokument, tx)

        verify(mottakerService).hentMottaker(
            argThat {
                referanseType == ReferanseTypeMottaker.REVURDERING &&
                    this.referanseId == referanseId &&
                    brevtype == Brevtype.FORHANDSVARSEL
            },
            eq(sakId),
            eq(tx),
        )
    }
}
