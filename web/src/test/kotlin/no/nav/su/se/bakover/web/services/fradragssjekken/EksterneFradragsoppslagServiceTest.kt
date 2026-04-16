package no.nav.su.se.bakover.web.services.fradragssjekken

import arrow.core.right
import io.kotest.matchers.shouldBe
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
import org.mockito.kotlin.argumentCaptor
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

    @Test
    fun `deler opp pesys alder-oppslag i batcher paa maks 50 fnr`() {
        val pesysClient = mock<PesysClient> {
            on { hentVedtakForPersonPaaDatoAlder(any(), any()) } doReturn ResponseDtoAlder(resultat = emptyList()).right()
        }

        val result = EksterneFradragsoppslagService(
            aapKlient = mock<AapApiInternClient>(),
            pesysKlient = pesysClient,
            log = mock(),
        ).hentOppslagsresultaterForYtelser(
            sjekkplaner = (1..51).map { index ->
                lagSjekkplan(
                    fnr = Fnr(index.toString().padStart(11, '0')),
                    sakId = UUID.randomUUID(),
                    saksnummer = Saksnummer((2021000 + index).toLong()),
                    ytelse = EksternYtelse.PESYS_ALDER,
                )
            },
            måned = mars(2026),
        )

        val captor = argumentCaptor<List<Fnr>>()
        verify(pesysClient, times(2)).hentVedtakForPersonPaaDatoAlder(captor.capture(), any())
        captor.allValues.map { it.size } shouldBe listOf(50, 1)
        captor.allValues.flatten().toSet().size shouldBe 51
        result.pesysAlder.size shouldBe 51
        result.pesysAlder.values.toSet() shouldBe setOf(EksterntOppslag.IngenTreff)
        verifyNoMoreInteractions(pesysClient)
    }

    private fun lagSjekkplan(
        fnr: Fnr,
        sakId: UUID,
        saksnummer: Saksnummer,
        ytelse: EksternYtelse,
    ): SjekkPlan {
        return SjekkPlan(
            sak = SakInfo(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                type = Sakstype.ALDER,
            ),
            sjekkpunkter = listOf(
                Sjekkpunkt(
                    fnr = fnr,
                    tilhører = FradragTilhører.BRUKER,
                    fradragstype = Fradragstype.Alderspensjon,
                    ytelse = ytelse,
                    lokaltBeløp = 1000.0,
                ),
            ),
        )
    }
}
