package no.nav.su.se.bakover.web.services.fradragssjekken

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.aap.AapApiInternClient
import no.nav.su.se.bakover.client.aap.MaksimumResponseDto
import no.nav.su.se.bakover.client.pesys.AlderBeregningsperiode
import no.nav.su.se.bakover.client.pesys.AlderBeregningsperioderPerPerson
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.ResponseDtoAlder
import no.nav.su.se.bakover.client.pesys.ResponseDtoUføre
import no.nav.su.se.bakover.client.pesys.UføreBeregningsperiode
import no.nav.su.se.bakover.client.pesys.UføreBeregningsperioderPerPerson
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
import java.time.LocalDate
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
    fun `arena-vedtak uten vedtaksTypeKode regnes ikke som aktivt vedtak`() {
        val vedtak = maksimumVedtak(
            status = AapVedtakStatus.IVERK,
            kildesystem = Kildesystem.ARENA,
            vedtaksTypeKode = null,
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
            on { hentVedtakForPersonPaaDatoAlder(any(), any()) } doReturn ResponseDtoAlder(resultat = emptyList(), feilendeFnr = emptyList()).right()
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
            on { hentVedtakForPersonPaaDatoAlder(any(), any()) } doReturn ResponseDtoAlder(resultat = emptyList(), feilendeFnr = emptyList()).right()
        }

        val result = EksterneFradragsoppslagService(
            aapKlient = mock<AapApiInternClient>(),
            pesysKlient = pesysClient,
            log = mock(),
        ).hentOppslagsresultaterForYtelser(
            sjekkplaner = (1..11).map { index ->
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
        verify(pesysClient, times(1)).hentVedtakForPersonPaaDatoAlder(captor.capture(), any())
        captor.allValues.map { it.size } shouldBe listOf(11)
        captor.allValues.flatten().toSet().size shouldBe 11
        result.pesysAlder.size shouldBe 11
        result.pesysAlder.values.toSet() shouldBe setOf(EksterntOppslag.IngenTreff)
        verifyNoMoreInteractions(pesysClient)
    }

    @Test
    fun `feilende fnr fra pesys alder blir feil i fradragssjekken og slipper gjennom andre personer`() {
        val feilendeFnr = Fnr("12345678901")
        val okFnr = Fnr("12345678902")
        val pesysClient = mock<PesysClient> {
            on { hentVedtakForPersonPaaDatoAlder(any(), any()) } doReturn ResponseDtoAlder(
                resultat = listOf(
                    AlderBeregningsperioderPerPerson(fnr = feilendeFnr.toString(), perioder = emptyList()),
                    AlderBeregningsperioderPerPerson(
                        fnr = okFnr.toString(),
                        perioder = listOf(
                            AlderBeregningsperiode(
                                netto = 1234,
                                fom = LocalDate.parse("2026-03-01"),
                                tom = null,
                                grunnbelop = 130160,
                            ),
                        ),
                    ),
                ),
                feilendeFnr = listOf(feilendeFnr.toString()),
            ).right()
        }

        val result = EksterneFradragsoppslagService(
            aapKlient = mock<AapApiInternClient>(),
            pesysKlient = pesysClient,
            log = mock(),
        ).hentOppslagsresultaterForYtelser(
            sjekkplaner = listOf(
                lagSjekkplan(
                    fnr = feilendeFnr,
                    sakId = UUID.randomUUID(),
                    saksnummer = Saksnummer(2021001),
                    ytelse = EksternYtelse.PESYS_ALDER,
                ),
                lagSjekkplan(
                    fnr = okFnr,
                    sakId = UUID.randomUUID(),
                    saksnummer = Saksnummer(2021002),
                    ytelse = EksternYtelse.PESYS_ALDER,
                ),
            ),
            måned = mars(2026),
        )

        result.pesysAlder[feilendeFnr] shouldBe EksterntOppslag.Feil("Fant feilende personer i respons fra pesys-alder-oppslag")
        result.pesysAlder[okFnr] shouldBe EksterntOppslag.Funnet(1234.0)
    }

    @Test
    fun `feilende fnr fra pesys ufore blir feil i fradragssjekken og slipper gjennom andre personer`() {
        val feilendeFnr = Fnr("12345678901")
        val okFnr = Fnr("12345678902")
        val pesysClient = mock<PesysClient> {
            on { hentVedtakForPersonPaaDatoUføre(any(), any()) } doReturn ResponseDtoUføre(
                resultat = listOf(
                    UføreBeregningsperioderPerPerson(fnr = feilendeFnr.toString(), perioder = emptyList()),
                    UføreBeregningsperioderPerPerson(
                        fnr = okFnr.toString(),
                        perioder = listOf(
                            UføreBeregningsperiode(
                                netto = 1234,
                                fom = LocalDate.parse("2026-03-01"),
                                tom = null,
                                grunnbelop = 130160,
                                oppjustertInntektEtterUfore = 123,
                            ),
                        ),
                    ),
                ),
                feilendeFnr = listOf(feilendeFnr.toString()),
            ).right()
        }

        val result = EksterneFradragsoppslagService(
            aapKlient = mock<AapApiInternClient>(),
            pesysKlient = pesysClient,
            log = mock(),
        ).hentOppslagsresultaterForYtelser(
            sjekkplaner = listOf(
                lagSjekkplan(
                    fnr = feilendeFnr,
                    sakId = UUID.randomUUID(),
                    saksnummer = Saksnummer(2021001),
                    ytelse = EksternYtelse.PESYS_UFORE,
                ),
                lagSjekkplan(
                    fnr = okFnr,
                    sakId = UUID.randomUUID(),
                    saksnummer = Saksnummer(2021002),
                    ytelse = EksternYtelse.PESYS_UFORE,
                ),
            ),
            måned = mars(2026),
        )

        result.pesysUføre[feilendeFnr] shouldBe EksterntOppslag.Feil("Fant feilende personer i respons fra pesys-uføre-oppslag")
        result.pesysUføre[okFnr] shouldBe EksterntOppslag.Funnet(1234.0)
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
