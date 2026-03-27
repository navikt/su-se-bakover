package no.nav.su.se.bakover.web.services.fradragssjekken

import arrow.core.right
import no.nav.su.se.bakover.client.aap.AapApiInternClient
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.ResponseDtoAlder
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.mars
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.util.UUID

internal class EksterneFradragsoppslagServiceTest {

    @Test
    fun `kaller ikke pesys ufore når det ikke finnes ufore-fnr`() {
        val pesysClient = mock<PesysClient> {
            on { hentVedtakForPersonPaaDatoAlder(any(), any()) } doReturn ResponseDtoAlder(resultat = emptyList()).right()
        }

        EksterneFradragsoppslagService(
            aapKlient = mock<AapApiInternClient>(),
            pesysKlient = pesysClient,
            log = mock(),
        ).hentOppslagsresultaterForYtelser(
            sjekkplaner = listOf(
                SjekkPlan(
                    sak = SakInfo(
                        sakId = UUID.randomUUID(),
                        saksnummer = Saksnummer(2021001),
                        fnr = Fnr("12345678901"),
                        type = Sakstype.ALDER,
                    ),
                    sjekkpunkter = listOf(
                        Sjekkpunkt(
                            fnr = Fnr("12345678901"),
                            tilhører = FradragTilhører.BRUKER,
                            fradragstype = Fradragstype.Alderspensjon,
                            ytelse = EksternYtelse.PESYS_ALDER,
                            lokaltBeløp = 1000.0,
                        ),
                    ),
                ),
            ),
            måned = mars(2026),
        )

        verify(pesysClient, times(1)).hentVedtakForPersonPaaDatoAlder(any(), any())
        verifyNoMoreInteractions(pesysClient)
    }
}
