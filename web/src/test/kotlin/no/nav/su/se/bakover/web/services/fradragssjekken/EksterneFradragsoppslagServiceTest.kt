package no.nav.su.se.bakover.web.services.fradragssjekken

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.aap.AapApiInternClient
import no.nav.su.se.bakover.client.aap.MaksimumResponseDto
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.ResponseDtoAlder
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.domain.regulering.AapVedtakStatus
import no.nav.su.se.bakover.domain.regulering.Kildesystem
import no.nav.su.se.bakover.domain.regulering.MaksimumPeriodeDto
import no.nav.su.se.bakover.domain.regulering.MaksimumVedtakDto
import no.nav.su.se.bakover.domain.regulering.tilMånedsbeløpForSu
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
    fun `bruker aktivt kelvin-vedtak paa dato`() {
        val vedtak = maksimumVedtak(
            status = AapVedtakStatus.LØPENDE,
            kildesystem = Kildesystem.KELVIN,
            fraOgMed = "2026-03-01",
            tilOgMed = "2026-03-31",
        )

        val result = EksterneFradragsoppslagService(
            aapKlient = mock<AapApiInternClient> {
                on { hentMaksimumUtenUtbetaling(any(), any(), any()) } doReturn MaksimumResponseDto(listOf(vedtak)).right()
            },
            pesysKlient = mock(),
            log = mock(),
        ).hentOppslagsresultaterForYtelser(
            sjekkplaner = listOf(lagAapSjekkplan(Fnr("12345678901"), UUID.randomUUID(), Saksnummer(2021001))),
            måned = mars(2026),
        )

        result.aap.values.single() shouldBe EksterntOppslag.Funnet(vedtak.tilMånedsbeløpForSu().toDouble())
    }

    @Test
    fun `arena-stans med iverk regnes ikke som aktivt vedtak`() {
        val vedtak = maksimumVedtak(
            status = AapVedtakStatus.IVERK,
            kildesystem = Kildesystem.ARENA,
            vedtaksTypeKode = "S",
            fraOgMed = "2026-03-01",
            tilOgMed = "2026-03-31",
        )

        val result = EksterneFradragsoppslagService(
            aapKlient = mock<AapApiInternClient> {
                on { hentMaksimumUtenUtbetaling(any(), any(), any()) } doReturn MaksimumResponseDto(listOf(vedtak)).right()
            },
            pesysKlient = mock(),
            log = mock(),
        ).hentOppslagsresultaterForYtelser(
            sjekkplaner = listOf(lagAapSjekkplan(Fnr("12345678901"), UUID.randomUUID(), Saksnummer(2021001))),
            måned = mars(2026),
        )

        result.aap.values.single() shouldBe EksterntOppslag.IngenTreff
    }

    @Test
    fun `manglende til-og-med-dato regnes ikke som aktivt vedtak`() {
        val vedtak = MaksimumVedtakDto(
            dagsats = 500,
            barnetillegg = 0,
            status = AapVedtakStatus.LØPENDE,
            kildesystem = Kildesystem.KELVIN,
            periode = MaksimumPeriodeDto(
                fraOgMedDato = java.time.LocalDate.parse("2026-03-01"),
                tilOgMedDato = null,
            ),
        )

        val result = EksterneFradragsoppslagService(
            aapKlient = mock<AapApiInternClient> {
                on { hentMaksimumUtenUtbetaling(any(), any(), any()) } doReturn MaksimumResponseDto(listOf(vedtak)).right()
            },
            pesysKlient = mock(),
            log = mock(),
        ).hentOppslagsresultaterForYtelser(
            sjekkplaner = listOf(lagAapSjekkplan(Fnr("12345678901"), UUID.randomUUID(), Saksnummer(2021001))),
            måned = mars(2026),
        )

        result.aap.values.single() shouldBe EksterntOppslag.IngenTreff
    }

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

    private fun lagAapSjekkplan(
        fnr: Fnr,
        sakId: UUID,
        saksnummer: Saksnummer,
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
                    fradragstype = Fradragstype.Arbeidsavklaringspenger,
                    ytelse = EksternYtelse.AAP,
                    lokaltBeløp = 1000.0,
                ),
            ),
        )
    }

    private fun maksimumVedtak(
        status: AapVedtakStatus,
        kildesystem: Kildesystem,
        fraOgMed: String,
        tilOgMed: String,
        vedtaksTypeKode: String? = null,
    ) = MaksimumVedtakDto(
        dagsats = 500,
        barnetillegg = 0,
        status = status,
        kildesystem = kildesystem,
        vedtaksTypeKode = vedtaksTypeKode,
        periode = MaksimumPeriodeDto(
            fraOgMedDato = java.time.LocalDate.parse(fraOgMed),
            tilOgMedDato = java.time.LocalDate.parse(tilOgMed),
        ),
    )
}
