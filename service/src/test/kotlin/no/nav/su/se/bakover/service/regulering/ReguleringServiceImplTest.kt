package no.nav.su.se.bakover.service.regulering

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.regulering.ReguleringType
import no.nav.su.se.bakover.domain.sak.SakIdSaksnummerFnr
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataEnsligUtenFradrag
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.simulertUtbetaling
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

internal class ReguleringServiceImplTest {
    private val sak = vedtakSøknadsbehandlingIverksattInnvilget().first
    private val reguleringService = lagReguleringServiceImpl(sak)

    @Test
    fun `regulerer alle saker`() {
        reguleringService.startRegulering(1.mai(2021)).size shouldBe 1
    }

    @Test
    fun `behandlinger som ikke har OffentligPensjon eller NAVytelserTilLivsopphold blir automatiskt regulert`() {
        val regulering = reguleringService.startRegulering(1.mai(2021)).first().getOrFail()
        regulering.reguleringType shouldBe ReguleringType.AUTOMATISK
    }

    @Test
    fun `OffentligPensjon gir manuell`() {
        val reguleringService = lagReguleringServiceImpl(
            vedtakSøknadsbehandlingIverksattInnvilget(
                grunnlagsdata = grunnlagsdataEnsligUtenFradrag(
                    fradragsgrunnlag = listOf(
                        Grunnlag.Fradragsgrunnlag.create(
                            opprettet = Tidspunkt.now(),
                            fradrag = FradragFactory.ny(
                                type = Fradragstype.OffentligPensjon,
                                månedsbeløp = 8000.0,
                                periode = periode2021,
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,
                            )
                        )
                    )
                )
            ).first
        )

        reguleringService.startRegulering(1.mai(2021)).single().getOrFail().reguleringType shouldBe ReguleringType.MANUELL
    }

    @Test
    fun `NAVytelserTilLivsopphold gir manuell`() {
        val reguleringService = lagReguleringServiceImpl(
            vedtakSøknadsbehandlingIverksattInnvilget(
                grunnlagsdata = grunnlagsdataEnsligUtenFradrag(
                    fradragsgrunnlag = listOf(
                        Grunnlag.Fradragsgrunnlag.create(
                            opprettet = Tidspunkt.now(),
                            fradrag = FradragFactory.ny(
                                type = Fradragstype.NAVytelserTilLivsopphold,
                                månedsbeløp = 8000.0,
                                periode = periode2021,
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,
                            )
                        )
                    )
                )
            ).first
        )

        reguleringService.startRegulering(1.mai(2021)).single().getOrFail().reguleringType shouldBe ReguleringType.MANUELL
    }

    private fun lagReguleringServiceImpl(
        sak: Sak,
    ): ReguleringServiceImpl {
        val testData = lagTestdata(sak)

        return ReguleringServiceImpl(
            reguleringRepo = mock {
                on { hentForSakId(any(), any()) } doReturn listOf()
            },
            sakRepo = mock {
                on { hentAlleIdFnrOgSaksnummer() } doReturn listOf(
                    SakIdSaksnummerFnr(
                        sakId = testData.first.sakId,
                        saksnummer = testData.first.saksnummer,
                        fnr = testData.first.fnr,
                    ),
                )
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any()) } doReturn simulertUtbetaling().right()
                on { hentGjeldendeUtbetaling(any(), any()) } doReturn testData.third.right()
            },
            vedtakService = mock {
                on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn testData.second.right()
            },
            clock = fixedClock,
        )
    }

    private fun lagTestdata(sak: Sak): Triple<SakIdSaksnummerFnr, GjeldendeVedtaksdata, UtbetalingslinjePåTidslinje> {
        val søknadsbehandling = sak.søknadsbehandlinger.single()

        return Triple(
            SakIdSaksnummerFnr(
                sakId = søknadsbehandling.sakId,
                saksnummer = søknadsbehandling.saksnummer,
                fnr = søknadsbehandling.fnr,
            ),
            sak.kopierGjeldendeVedtaksdata(
                1.mai(2021),
                fixedClock,
            ).getOrFail(),
            sak.utbetalingstidslinje().gjeldendeForDato(1.mai(2021))!!,
        )
    }
}
