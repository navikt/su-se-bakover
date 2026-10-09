package no.nav.su.se.bakover.service.regulering.aldersfradrag

import arrow.core.Either
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.regulering.AlderspensjonFraPesys
import no.nav.su.se.bakover.domain.regulering.AlderspensjonOppslagsgrunnlag
import no.nav.su.se.bakover.domain.regulering.AlderspensjonOppslagsperson
import no.nav.su.se.bakover.domain.regulering.AlderspensjonsbeløpFraPesys
import no.nav.su.se.bakover.domain.regulering.HentingAvEksterneReguleringerFeiletForBruker
import no.nav.su.se.bakover.domain.regulering.Reguleringsresultat
import no.nav.su.se.bakover.domain.regulering.SakTilRegulering
import no.nav.su.se.bakover.domain.regulering.hentGjeldendeVedtaksdataForRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.service.regulering.ReguleringServiceImpl
import no.nav.su.se.bakover.service.regulering.ReguleringerFraPesysService
import no.nav.su.se.bakover.test.argShouldBe
import no.nav.su.se.bakover.test.bosituasjongrunnlagEpsUførFlyktning
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlag.nyFradragsgrunnlag
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkår.formuevilkårMedEps0Innvilget
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import vedtak.domain.VedtakSomKanRevurderes
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.util.UUID

internal class OmregningAlderEtterOmregningsmånedTest {

    private val omregningsmåned = januar(2021)
    private val stønadsår = år(2021)
    private val periodeEtterOmregningsmåned = februar(2021)..desember(2021)
    private val satsFactory = satsFactoryTestPåDato()

    @Nested
    inner class HentEksterneBeløper {

        @Test
        fun `feiler typet når brukers alderspensjonsfradrag endres etter omregningsmåneden`() {
            val (sak, vedtak) = vedtakMedAlderspensjon(
                fradrag = listOf(
                    alderspensjon(periode = omregningsmåned, tilhører = FradragTilhører.BRUKER, månedsbeløp = 1000.0),
                    alderspensjon(
                        periode = periodeEtterOmregningsmåned,
                        tilhører = FradragTilhører.BRUKER,
                        månedsbeløp = 1200.0,
                    ),
                ),
                medEps = false,
            )
            val pesys = pesysService(svar = emptyList())
            val sakTilRegulering = sakTilRegulering(sak, vedtak)

            val (saker, _) = HentEksterneBeløperFraOmregningAlder(pesys, satsFactory).hent(
                saker = listOf(sakTilRegulering(sak, vedtak).right()),
                fraOgMedMåned = omregningsmåned,
            )
            saker shouldBe listOf(sakTilRegulering.right())
            verify(pesys).hentReguleringerForOmregningAlder(
                argShouldBe(omregningsmåned),
                argShouldBe(
                    listOf(
                        AlderspensjonOppslagsgrunnlag(
                            brukerFnr = sak.fnr,
                            personer = listOf(AlderspensjonOppslagsperson(sak.fnr, FradragTilhører.BRUKER)),
                        ),
                    ),
                ),
                any(),
            )
        }

        @Test
        fun `slår opp i Pesys når alderspensjonsfradraget dekker omregningsmåneden`() {
            val (sak, vedtak) = vedtakMedAlderspensjon(
                fradrag = listOf(alderspensjon(periode = stønadsår, tilhører = FradragTilhører.BRUKER)),
                medEps = false,
            )
            val alderspensjonFraPesys = AlderspensjonFraPesys(
                brukerFnr = sak.fnr,
                epsFnr = null,
                beløp = listOf(
                    AlderspensjonsbeløpFraPesys(
                        tilhører = FradragTilhører.BRUKER,
                        måned = omregningsmåned,
                        beløp = BigDecimal("1000.00"),
                    ),
                ),
            )
            val pesys = pesysService(svar = listOf(alderspensjonFraPesys.right()))
            val sakTilRegulering = sakTilRegulering(sak, vedtak)

            val (saker, eksterneBeløp) = HentEksterneBeløperFraOmregningAlder(pesys, satsFactory).hent(
                saker = listOf(sakTilRegulering.right()),
                fraOgMedMåned = omregningsmåned,
            )

            saker shouldBe listOf(sakTilRegulering.right())
            eksterneBeløp shouldBe listOf(alderspensjonFraPesys)
            verify(pesys).hentReguleringerForOmregningAlder(
                argShouldBe(omregningsmåned),
                argShouldBe(
                    listOf(
                        AlderspensjonOppslagsgrunnlag(
                            brukerFnr = sak.fnr,
                            personer = listOf(AlderspensjonOppslagsperson(sak.fnr, FradragTilhører.BRUKER)),
                        ),
                    ),
                ),
                any(),
            )
        }
    }

    @Nested
    inner class HentVedtaksdata {

        @Test
        fun `feiler typet når vedtaket starter etter omregningsmåneden`() {
            val (sak, vedtak) = vedtakMedAlderspensjon(
                fradrag = listOf(
                    alderspensjon(periode = periodeEtterOmregningsmåned, tilhører = FradragTilhører.BRUKER),
                ),
                medEps = false,
                stønadsperiode = Stønadsperiode.create(periodeEtterOmregningsmåned),
            )

            val resultat = hentVedtaksdata(sak, vedtak)

            resultat shouldBe listOf(
                BleIkkeOmregnetAlder.VedtakStarterEtterOmregningsmåned(
                    omregningsmåned = omregningsmåned,
                    vedtaksperiode = periodeEtterOmregningsmåned,
                    saksnummer = sak.saksnummer,
                ).left(),
            )
        }

        @Test
        fun `går videre når vedtaket dekker omregningsmåneden`() {
            val (sak, vedtak) = vedtakMedAlderspensjon(
                fradrag = listOf(alderspensjon(periode = stønadsår, tilhører = FradragTilhører.BRUKER)),
                medEps = false,
            )

            hentVedtaksdata(sak, vedtak).single().shouldBeRight()
        }

        @Test
        fun `én åpen regulering stopper saken før vedtak hentes`() {
            val (sak, _) = vedtakMedAlderspensjon(
                fradrag = listOf(alderspensjon(periode = stønadsår, tilhører = FradragTilhører.BRUKER)),
                medEps = false,
            )
            val vedtakRepo = mock<VedtakRepo>()
            val reguleringService = mock<ReguleringServiceImpl> {
                on { hentAntallÅpneReguleringerForSaker(argShouldBe(listOf(sak.id))) } doReturn mapOf(sak.id to 1L)
            }

            val resultater = HentVedtaksdataForOmregningAlder(
                vedtakRepo,
                reguleringService,
                fixedClock,
            ).hent(listOf(sak.info()), omregningsmåned)

            resultater shouldBe listOf(
                BleIkkeOmregnetAlder.TrengerIkkeOmregne.FinnesÅpenOmregning(sak.saksnummer).left(),
            )
            verifyNoInteractions(vedtakRepo)
            verify(reguleringService, never()).hentReguleringerForSak(any())
        }

        @Test
        fun `flere åpne reguleringer gir typed feil med saksnummer mens neste sak vurderes`() {
            val (sakMedFlereÅpne, _) = vedtakMedAlderspensjon(
                fradrag = listOf(alderspensjon(periode = stønadsår, tilhører = FradragTilhører.BRUKER)),
                medEps = false,
            )
            val (sakUtenÅpne, vedtak) = vedtakMedAlderspensjon(
                fradrag = listOf(alderspensjon(periode = stønadsår, tilhører = FradragTilhører.BRUKER)),
                medEps = false,
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(12345677),
            )
            val antallÅpne = 2L
            val sakIder = listOf(sakMedFlereÅpne.id, sakUtenÅpne.id)
            val vedtakRepo = mock<VedtakRepo> {
                on {
                    hentVedtakSomKanRevurderesForSakerFraOgMed(
                        argShouldBe(listOf(sakUtenÅpne.id)),
                        argShouldBe(omregningsmåned),
                        anyOrNull(),
                    )
                } doReturn mapOf(sakUtenÅpne.id to listOf(vedtak))
            }
            val reguleringService = mock<ReguleringServiceImpl> {
                on { hentAntallÅpneReguleringerForSaker(argShouldBe(sakIder)) } doReturn mapOf(
                    sakMedFlereÅpne.id to antallÅpne,
                )
            }

            val resultater = HentVedtaksdataForOmregningAlder(
                vedtakRepo,
                reguleringService,
                fixedClock,
            ).hent(listOf(sakMedFlereÅpne.info(), sakUtenÅpne.info()), omregningsmåned)

            val feil = BleIkkeOmregnetAlder.FlereÅpneReguleringer(sakMedFlereÅpne.saksnummer, antallÅpne)
            resultater.first() shouldBe feil.left()
            resultater.last().shouldBeRight().sakInfo shouldBe sakUtenÅpne.info()
            val oppsummering = feil.left().tilReguleringsresultat()
            oppsummering.utfall shouldBe Reguleringsresultat.Utfall.FEILET
            oppsummering.saksnummer shouldBe sakMedFlereÅpne.saksnummer
            oppsummering.beskrivelse shouldBe feil.toString()
            verify(reguleringService).hentAntallÅpneReguleringerForSaker(sakIder)
            verify(reguleringService, never()).hentReguleringerForSak(any())
            verify(vedtakRepo).hentVedtakSomKanRevurderesForSakerFraOgMed(
                listOf(sakUtenÅpne.id),
                omregningsmåned,
                null,
            )
        }

        private fun hentVedtaksdata(sak: Sak, vedtak: VedtakSomKanRevurderes, antallÅpne: Long = 0L) =
            HentVedtaksdataForOmregningAlder(
                vedtakRepo = mock<VedtakRepo> {
                    on {
                        hentVedtakSomKanRevurderesForSakerFraOgMed(
                            argShouldBe(listOf(sak.id)),
                            argShouldBe(omregningsmåned),
                            anyOrNull(),
                        )
                    } doReturn mapOf(sak.id to listOf(vedtak))
                },
                reguleringService = mock<ReguleringServiceImpl> {
                    on { hentAntallÅpneReguleringerForSaker(argShouldBe(listOf(sak.id))) } doReturn mapOf(
                        sak.id to antallÅpne,
                    )
                },
                clock = fixedClock,
            ).hent(
                saker = listOf(sak.info()),
                fraOgMedMåned = omregningsmåned,
            )
    }

    private fun alderspensjon(
        periode: Periode,
        tilhører: FradragTilhører,
        månedsbeløp: Double = 1000.0,
    ): Fradragsgrunnlag = nyFradragsgrunnlag(
        type = Fradragstype.Alderspensjon,
        månedsbeløp = månedsbeløp,
        periode = periode,
        tilhører = tilhører,
    )

    private fun vedtakMedAlderspensjon(
        fradrag: List<Fradragsgrunnlag>,
        medEps: Boolean,
        stønadsperiode: Stønadsperiode = Stønadsperiode.create(stønadsår),
        sakId: UUID = no.nav.su.se.bakover.test.sakId,
        saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    ): Pair<Sak, VedtakSomKanRevurderes> {
        val bosituasjonMedEps = bosituasjongrunnlagEpsUførFlyktning(periode = stønadsperiode.periode)
        return vedtakSøknadsbehandlingIverksattInnvilget(
            sakId = sakId,
            saksnummer = saksnummer,
            stønadsperiode = stønadsperiode,
            customGrunnlag = if (medEps) listOf(bosituasjonMedEps) + fradrag else fradrag,
            customVilkår = if (medEps) {
                listOf(
                    formuevilkårMedEps0Innvilget(
                        periode = stønadsperiode.periode,
                        bosituasjon = nonEmptyListOf(bosituasjonMedEps),
                    ),
                )
            } else {
                emptyList()
            },
        )
    }

    private fun sakTilRegulering(sak: Sak, vedtak: VedtakSomKanRevurderes) = SakTilRegulering(
        sakInfo = sak.info(),
        gjeldendeVedtaksdata = hentGjeldendeVedtaksdataForRegulering(
            fraOgMedMåned = omregningsmåned,
            sakInfo = sak.info(),
            vedtakSomKanRevurderes = listOf(vedtak),
            clock = fixedClock,
        ).getOrFail(),
    )

    private fun pesysService(
        svar: List<Either<HentingAvEksterneReguleringerFeiletForBruker, AlderspensjonFraPesys>>,
    ) = mock<ReguleringerFraPesysService> {
        on { hentReguleringerForOmregningAlder(any<Måned>(), any(), any()) } doReturn svar
    }
}
