package no.nav.su.se.bakover.service.regulering

import arrow.core.right
import arrow.core.rightIfNotNull
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.regulering.ReguleringType
import no.nav.su.se.bakover.domain.sak.SakIdSaksnummerFnr
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.service.utbetaling.FantIkkeGjeldendeUtbetaling
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataEnsligUtenFradrag
import no.nav.su.se.bakover.test.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.simulertUtbetaling
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vedtakRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.LocalDate

internal class ReguleringServiceImplTest {

    @Test
    fun `regulerer alle saker`() {
        val sak = vedtakSøknadsbehandlingIverksattInnvilget().first
        val reguleringService = lagReguleringServiceImpl(sak)

        reguleringService.startRegulering(1.mai(2021)).size shouldBe 1
    }

    @Nested
    inner class UtledRegulertypeTest {
        private val reguleringService = lagReguleringServiceImpl(
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
                            ),
                        ),
                    ),
                ),
            ).first,
        )

        @Test
        fun `behandlinger som ikke har OffentligPensjon eller NAVytelserTilLivsopphold blir automatiskt regulert`() {
            val sak = vedtakSøknadsbehandlingIverksattInnvilget().first
            val reguleringService = lagReguleringServiceImpl(sak)

            val regulering = reguleringService.startRegulering(1.mai(2021)).first().getOrFail()
            regulering.reguleringType shouldBe ReguleringType.AUTOMATISK
        }

        @Test
        fun `OffentligPensjon gir manuell`() {
            reguleringService.startRegulering(1.mai(2021)).single()
                .getOrFail().reguleringType shouldBe ReguleringType.MANUELL
        }

        @Test
        fun `NAVytelserTilLivsopphold gir manuell`() {
            reguleringService.startRegulering(1.mai(2021)).single()
                .getOrFail().reguleringType shouldBe ReguleringType.MANUELL
        }

        @Test
        fun `Stans må håndteres manuellt`() {
            val stansAvYtelse = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak().first
            val reguleringService = lagReguleringServiceImpl(stansAvYtelse)

            reguleringService.startRegulering(1.mai(2021)).single()
                .getOrFail().reguleringType shouldBe ReguleringType.MANUELL
        }

        @Test
        fun `En periode med opphør må behandles manuellt`() {
            val revurdertSak = vedtakRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak().first

            val reguleringService = lagReguleringServiceImpl(revurdertSak)

            val regulering = reguleringService.startRegulering(1.mai(2021)).first().getOrFail()
            regulering.reguleringType shouldBe ReguleringType.MANUELL
        }

        @Test
        fun `en behandling med delvis opphør skal reguleres manuellt`() {
            val revurdertSak =
                vedtakRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
                    revurderingsperiode = Periode.create(
                        1.september(
                            2021,
                        ),
                        31.desember(2021),
                    ),
                ).first

            val reguleringService = lagReguleringServiceImpl(revurdertSak)

            val regulering = reguleringService.startRegulering(1.mai(2021)).first().getOrFail()
            regulering.reguleringType shouldBe ReguleringType.MANUELL
        }
    }

    @Nested
    inner class PeriodeTester {

        @Test
        fun `reguleringen kan ikke starte tidligere enn reguleringsdatoen`() {
            val sak =
                vedtakSøknadsbehandlingIverksattInnvilget(stønadsperiode = Stønadsperiode.create(periode2021)).first
            val reguleringService = lagReguleringServiceImpl(sak)

            val regulering = reguleringService.startRegulering(1.mai(2021)).first().getOrFail()
            regulering.periode.fraOgMed shouldBe 1.mai(2021)
        }

        @Test
        fun `reguleringen starter fra søknadsbehandlingens dato hvis den er etter reguleringsdatoen`() {
            val periodeEtterReguleringsdato = Periode.create(1.juni(2021), 31.desember(2021))
            val sak = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(
                    periodeEtterReguleringsdato,
                ),
            ).first
            val reguleringService = lagReguleringServiceImpl(sak)

            val regulering = reguleringService.startRegulering(1.mai(2021)).first().getOrFail()
            regulering.periode.fraOgMed shouldBe 1.juni(2021)
        }
    }

    private fun lagReguleringServiceImpl(
        sak: Sak,
    ): ReguleringServiceImpl {
        val testData = lagTestdata(sak)
        val utbetaling = oversendtUtbetalingUtenKvittering()

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
                on {
                    hentGjeldendeUtbetaling(
                        any(),
                        any(),
                    )
                } doReturn testData.third.rightIfNotNull { FantIkkeGjeldendeUtbetaling }
                on { utbetal(any()) } doReturn utbetaling.right()
            },
            vedtakService = mock {
                on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn testData.second.right()
            },
            clock = fixedClock,
        )
    }

    private fun lagTestdata(
        sak: Sak,
        reguleringsdato: LocalDate = 1.mai(2021),
    ): Triple<SakIdSaksnummerFnr, GjeldendeVedtaksdata, UtbetalingslinjePåTidslinje?> {
        val søknadsbehandling = sak.søknadsbehandlinger.single()

        return Triple(
            SakIdSaksnummerFnr(
                sakId = søknadsbehandling.sakId,
                saksnummer = søknadsbehandling.saksnummer,
                fnr = søknadsbehandling.fnr,
            ),

            sak.kopierGjeldendeVedtaksdata(
                reguleringsdato,
                fixedClock,
            ).getOrFail(),
            sak.utbetalingstidslinje().gjeldendeForDato(reguleringsdato),
        )
    }
}
