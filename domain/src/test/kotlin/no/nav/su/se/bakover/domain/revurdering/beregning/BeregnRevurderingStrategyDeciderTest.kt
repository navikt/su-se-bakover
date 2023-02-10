package no.nav.su.se.bakover.domain.revurdering.beregning

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beOfType
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.august
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Personopplysninger
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOpplysningsplikt
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandling
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.sakMedUteståendeAvkorting
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.stønadsperiode2022
import no.nav.su.se.bakover.test.søknad.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkår
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

/**
 * Overlapper litt med [no.nav.su.se.bakover.domain.revurdering.RevurderingBeregnTest] som dekker casene:
 * - AvkortingVedRevurdering.Uhåndtert.IngenUtestående -> Normal og VidereførAvkorting. Mangler annuller AnnullerAvkorting (opphør og innvilgelse).
 * - AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting -> AnnullerAvkorting (innvilgelse). Mangler Normal, AnnullerAvkorting (opphør) og alle VidereførAvkorting-casene.
 *
 */
internal class BeregnRevurderingStrategyDeciderTest {

    @Nested
    inner class IngenUtestående {
        @Test
        fun `beregner uten avkorting dersom ikke aktuelt`() {
            // Dekker AvkortingVedRevurdering.Uhåndtert.IngenUtestående -> Normal
            val (sak, revurdering) = opprettetRevurdering()
            BeregnRevurderingStrategyDecider(
                revurdering = revurdering,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                clock = fixedClock,
                beregningStrategyFactory = BeregningStrategyFactory(fixedClock, satsFactoryTestPåDato()),
            ).decide() shouldBe beOfType<Normal>()
        }

        @Test
        fun `revurdering viderefører fradrag fra tidligere håndtert avkorting`() {
            // Dekker AvkortingVedRevurdering.Uhåndtert.IngenUtestående -> VidereførAvkorting
            val clock = TikkendeKlokke(fixedClock)
            val (sak, _) = sakMedUteståendeAvkorting(
                clock = clock,
                stønadsperiode = stønadsperiode2021,
                revurderingsperiode = stønadsperiode2021.periode,
                utbetalingerKjørtTilOgMed = 1.mai(2021), // feilutbetaling for jan-apr
            )

            val (medNyStønadsperiode, _, nyStønadsperiode) = iverksattSøknadsbehandling(
                stønadsperiode = stønadsperiode2022,
                sakOgSøknad = sak to nySøknadJournalførtMedOppgave(
                    clock = clock,
                    sakId = sak.id,
                    søknadInnhold = søknadinnholdUføre(),
                ),
            )

            opprettetRevurdering(
                sakOgVedtakSomKanRevurderes = medNyStønadsperiode to nyStønadsperiode as VedtakSomKanRevurderes,
                revurderingsperiode = stønadsperiode2022.periode,
            ).also { (sak, revurdering) ->
                BeregnRevurderingStrategyDecider(
                    revurdering = revurdering,
                    gjeldendeVedtaksdata = sak.hentGjeldendeVedtaksdata(
                        periode = revurdering.periode,
                        clock = clock,
                    ).getOrFail(),
                    clock = clock,
                    beregningStrategyFactory = BeregningStrategyFactory(clock, satsFactoryTestPåDato()),
                ).decide() shouldBe beOfType<VidereførAvkorting>()
            }
        }

        @Test
        fun `annullerer avkorting dersom vi revurderer innvilget tilbake til tidspunkt for opprettelse av avkortingsvarsel eller tidligere - opphør`() {
            // Dekker AvkortingVedRevurdering.Uhåndtert.IngenUtestående -> AnnullerAvkorting (opphør)
            val tikkendeKlokke = TikkendeKlokke()

            var sak: Sak

            val førsteStønadsperiode = iverksattSøknadsbehandlingUføre(
                clock = tikkendeKlokke,
                stønadsperiode = stønadsperiode2021,
            ).let {
                sak = it.first
                it.third as VedtakSomKanRevurderes
            }

            vedtakRevurdering(
                clock = tikkendeKlokke,
                stønadsperiode = stønadsperiode2021,
                revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sak to førsteStønadsperiode,
                vilkårOverrides = listOf(
                    utenlandsoppholdAvslag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = Periode.create(1.mai(2021), 31.desember(2021)),
                    ),
                ),
                utbetalingerKjørtTilOgMed = 1.juli(2021),
            ).let {
                sak = it.first
                it.second
            }

            val nyStønadsperiode = iverksattSøknadsbehandlingUføre(
                clock = tikkendeKlokke,
                stønadsperiode = Stønadsperiode.create(Periode.create(1.juli(2021), 30.juni(2022))),
                sakOgSøknad = sak to nySøknadJournalførtMedOppgave(
                    clock = tikkendeKlokke,
                    sakId = sak.id,
                    søknadInnhold = søknadinnholdUføre(
                        personopplysninger = Personopplysninger(sak.fnr),
                    ),
                ),
            ).let {
                sak = it.first
                it.third as VedtakSomKanRevurderes
            }

            val revurdering = opprettetRevurdering(
                revurderingsperiode = Periode.create(1.februar(2021), 30.juni(2022)),
                sakOgVedtakSomKanRevurderes = sak to nyStønadsperiode,
                clock = tikkendeKlokke,
            ).let {
                sak = it.first
                it.second
            }
            BeregnRevurderingStrategyDecider(
                revurdering = revurdering,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                clock = fixedClock,
                beregningStrategyFactory = BeregningStrategyFactory(fixedClock, satsFactoryTestPåDato()),
            ).decide() shouldBe beOfType<AnnullerAvkorting>()
        }
    }

    @Test
    fun `annullerer avkorting dersom vi revurderer innvilget tilbake til tidspunkt for opprettelse av avkortingsvarsel eller tidligere - innvilgelse`() {
        // Dekker AvkortingVedRevurdering.Uhåndtert.IngenUtestående -> AnnullerAvkorting (innvilgelse)
        val tikkendeKlokke = TikkendeKlokke()

        var sak: Sak

        val førsteStønadsperiode = iverksattSøknadsbehandlingUføre(
            clock = tikkendeKlokke,
            stønadsperiode = stønadsperiode2021,
        ).let {
            sak = it.first
            it.third as VedtakSomKanRevurderes
        }

        vedtakRevurdering(
            clock = tikkendeKlokke,
            stønadsperiode = stønadsperiode2021,
            revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
            sakOgVedtakSomKanRevurderes = sak to førsteStønadsperiode,
            vilkårOverrides = listOf(
                utenlandsoppholdAvslag(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = Periode.create(1.mai(2021), 31.desember(2021)),
                ),
            ),
            utbetalingerKjørtTilOgMed = 1.juli(2021),
        ).let {
            sak = it.first
            it.second
        }

        val nyStønadsperiode = iverksattSøknadsbehandlingUføre(
            clock = tikkendeKlokke,
            stønadsperiode = Stønadsperiode.create(Periode.create(1.juli(2021), 30.juni(2022))),
            sakOgSøknad = sak to nySøknadJournalførtMedOppgave(
                clock = tikkendeKlokke,
                sakId = sak.id,
                søknadInnhold = søknadinnholdUføre(
                    personopplysninger = Personopplysninger(sak.fnr),
                ),
            ),
        ).let {
            sak = it.first
            it.third as VedtakSomKanRevurderes
        }

        val revurdering = opprettetRevurdering(
            revurderingsperiode = Periode.create(1.februar(2021), 30.juni(2022)),
            sakOgVedtakSomKanRevurderes = sak to nyStønadsperiode,
            clock = tikkendeKlokke,
            vilkårOverrides = listOf(
                utenlandsoppholdInnvilget(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = Periode.create(1.februar(2021), 30.juni(2022)),
                ),
            ),
        ).let {
            sak to it.first
            it.second
        }

        BeregnRevurderingStrategyDecider(
            revurdering = revurdering,
            gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                fraOgMed = revurdering.periode.fraOgMed,
                clock = fixedClock,
            ).getOrFail(),
            clock = fixedClock,
            beregningStrategyFactory = BeregningStrategyFactory(fixedClock, satsFactoryTestPåDato()),
        ).decide() shouldBe beOfType<AnnullerAvkorting>()
    }

    @Nested
    inner class UteståendeAvkorting {

        @Test
        fun `kaster dersom revurderingsperiode ikke inneholder perioden for utestående avkorting - avslag`() {
            val tikkendeKlokke = TikkendeKlokke()
            val (sak1, opphørUtenlandsopphold) = vedtakRevurdering(
                clock = tikkendeKlokke,
                stønadsperiode = stønadsperiode2021,
                revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                vilkårOverrides = listOf(
                    utenlandsoppholdAvslag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = Periode.create(1.mai(2021), 31.desember(2021)),
                    ),
                ),
                utbetalingerKjørtTilOgMed = 1.juli(2021),
            )

            assertThrows<IllegalStateException> {
                val (sak2, revurdering) = opprettetRevurdering(
                    revurderingsperiode = Periode.create(1.august(2021), 31.desember(2021)),
                    sakOgVedtakSomKanRevurderes = sak1 to opphørUtenlandsopphold,
                    clock = tikkendeKlokke,
                )
                BeregnRevurderingStrategyDecider(
                    revurdering = revurdering,
                    gjeldendeVedtaksdata = sak2.kopierGjeldendeVedtaksdata(
                        fraOgMed = revurdering.periode.fraOgMed,
                        clock = fixedClock,
                    ).getOrFail(),
                    clock = fixedClock,
                    beregningStrategyFactory = BeregningStrategyFactory(fixedClock, satsFactoryTestPåDato()),
                ).decide()
            }.let {
                it.message shouldContain "Må revurdere hele perioden for opprinngelig avkorting ved annullering."
            }
        }

        @Test
        fun `kaster dersom dato for opphør ikke overskriver avkortinger fullstendig - avslag`() {
            val tikkendeKlokke = TikkendeKlokke()
            val (sak1, opphørUtenlandsopphold) = vedtakRevurdering(
                clock = tikkendeKlokke,
                stønadsperiode = stønadsperiode2021,
                revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                vilkårOverrides = listOf(
                    utenlandsoppholdAvslag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = Periode.create(1.mai(2021), 31.desember(2021)),
                    ),
                ),
                utbetalingerKjørtTilOgMed = 1.juli(2021),
            )

            assertThrows<IllegalStateException> {
                val (sak2, revurdering) = opprettetRevurdering(
                    revurderingsperiode = Periode.create(1.mars(2021), 31.desember(2021)),
                    sakOgVedtakSomKanRevurderes = sak1 to opphørUtenlandsopphold,
                    clock = tikkendeKlokke,
                    vilkårOverrides = listOf(
                        utenlandsoppholdInnvilget(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            periode = Periode.create(1.mars(2021), 31.desember(2021)),
                        ),
                        innvilgetUførevilkår(
                            opprettet = fixedTidspunkt,
                            periode = Periode.create(1.mars(2021), 31.desember(2021)),
                        ),
                        OpplysningspliktVilkår.Vurdert.createFromVilkårsvurderinger(
                            vurderingsperioder = nonEmptyListOf(
                                VurderingsperiodeOpplysningsplikt.create(
                                    opprettet = fixedTidspunkt,
                                    grunnlag = Opplysningspliktgrunnlag(
                                        opprettet = fixedTidspunkt,
                                        periode = Periode.create(1.mars(2021), 31.juli(2021)),
                                        beskrivelse = OpplysningspliktBeskrivelse.TilstrekkeligDokumentasjon,
                                    ),
                                    periode = Periode.create(1.mars(2021), 31.juli(2021)),
                                ),
                                VurderingsperiodeOpplysningsplikt.create(
                                    id = UUID.randomUUID(),
                                    opprettet = fixedTidspunkt,
                                    grunnlag = Opplysningspliktgrunnlag(
                                        opprettet = fixedTidspunkt,
                                        periode = Periode.create(1.august(2021), 31.desember(2021)),
                                        beskrivelse = OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon,
                                    ),
                                    periode = Periode.create(1.august(2021), 31.desember(2021)),
                                ),
                            ),
                        ),
                    ),
                )
                BeregnRevurderingStrategyDecider(
                    revurdering = revurdering,
                    gjeldendeVedtaksdata = sak2.kopierGjeldendeVedtaksdata(
                        fraOgMed = revurdering.periode.fraOgMed,
                        clock = fixedClock,
                    ).getOrFail(),
                    clock = fixedClock,
                    beregningStrategyFactory = BeregningStrategyFactory(fixedClock, satsFactoryTestPåDato()),
                ).decide()
            }.let {
                it.message shouldContain "Dato for opphør må være tidligere enn eller lik fra og med dato for opprinnelig avkorting som annulleres"
            }
        }

        @Test
        fun `kaster dersom revurderingsperiode ikke inneholder perioden for utestående avkorting - innvilgelse`() {
            val tikkendeKlokke = TikkendeKlokke()
            val (sak1, opphørUtenlandsopphold) = vedtakRevurdering(
                clock = tikkendeKlokke,
                stønadsperiode = stønadsperiode2021,
                revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                vilkårOverrides = listOf(
                    utenlandsoppholdAvslag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = Periode.create(1.mai(2021), 31.desember(2021)),
                    ),
                ),
                utbetalingerKjørtTilOgMed = 1.juli(2021),
            )

            assertThrows<IllegalStateException> {
                val (sak2, revurdering) = opprettetRevurdering(
                    revurderingsperiode = Periode.create(1.august(2021), 31.desember(2021)),
                    sakOgVedtakSomKanRevurderes = sak1 to opphørUtenlandsopphold,
                    clock = tikkendeKlokke,
                    vilkårOverrides = listOf(
                        utenlandsoppholdInnvilget(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            periode = Periode.create(1.august(2021), 31.desember(2021)),
                        ),
                    ),
                )
                BeregnRevurderingStrategyDecider(
                    revurdering = revurdering,
                    gjeldendeVedtaksdata = sak2.kopierGjeldendeVedtaksdata(
                        fraOgMed = revurdering.periode.fraOgMed,
                        clock = fixedClock,
                    ).getOrFail(),
                    clock = fixedClock,
                    beregningStrategyFactory = BeregningStrategyFactory(fixedClock, satsFactoryTestPåDato()),
                ).decide()
            }.let {
                it.message shouldContain "Må revurdere hele perioden for opprinngelig avkorting ved annullering."
            }
        }

        @Test
        fun `annuller avkorting dersom utestående avkorting og ingen avkortingsgrunnlag - innvilget`() {
            // Dekker AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting -> VidereførAvkorting (annuller innvilget)
            val tikkendeKlokke = TikkendeKlokke()
            val (sak, opphørUtenlandsopphold) = vedtakRevurdering(
                clock = tikkendeKlokke,
                stønadsperiode = stønadsperiode2021,
                revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                vilkårOverrides = listOf(
                    utenlandsoppholdAvslag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = Periode.create(1.mai(2021), 31.desember(2021)),
                    ),
                ),
                utbetalingerKjørtTilOgMed = 1.juli(2021),
            )
            val (sak2, revurdering) = opprettetRevurdering(
                revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sak to opphørUtenlandsopphold,
                clock = tikkendeKlokke,
                vilkårOverrides = listOf(
                    utenlandsoppholdInnvilget(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = Periode.create(1.mai(2021), 31.desember(2021)),
                    ),
                ),
            )

            BeregnRevurderingStrategyDecider(
                revurdering = revurdering,
                gjeldendeVedtaksdata = sak2.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                clock = fixedClock,
                beregningStrategyFactory = BeregningStrategyFactory(fixedClock, satsFactoryTestPåDato()),
            ).decide() shouldBe beOfType<AnnullerAvkorting>()
        }

        @Test
        fun `annuller avkorting dersom utestående avkorting og ingen avkortingsgrunnlag - opphør`() {
            // Dekker AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting -> Annuller (opphør))
            val tikkendeKlokke = TikkendeKlokke()

            var sak: Sak

            val førsteStønadsperiode = iverksattSøknadsbehandlingUføre(
                clock = tikkendeKlokke,
                stønadsperiode = stønadsperiode2021,
            ).let {
                sak = it.first
                it.third as VedtakSomKanRevurderes
            }

            val opphørUtenlandsopphold = vedtakRevurdering(
                clock = tikkendeKlokke,
                stønadsperiode = stønadsperiode2021,
                revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sak to førsteStønadsperiode,
                vilkårOverrides = listOf(
                    utenlandsoppholdAvslag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = Periode.create(1.mai(2021), 31.desember(2021)),
                    ),
                ),
                utbetalingerKjørtTilOgMed = 1.juli(2021),
            ).let {
                sak = it.first
                it.second
            }
            val revurdering = opprettetRevurdering(
                revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sak to opphørUtenlandsopphold,
                clock = tikkendeKlokke,
            ).let {
                sak = it.first
                it.second
            }

            BeregnRevurderingStrategyDecider(
                revurdering = revurdering,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                clock = fixedClock,
                beregningStrategyFactory = BeregningStrategyFactory(fixedClock, satsFactoryTestPåDato()),
            ).decide() shouldBe beOfType<AnnullerAvkorting>()
        }

        @Test
        fun `velger normal dersom vi revurderer sak med utestående avkorting men måned utenfor revurderingsperiode som førte til uteståendeavkorting`() {
            // Dekker AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting -> Normal
            val clock = TikkendeKlokke()

            // Lager en utestående avkorting for januar, mens februar blir et rent opphør.
            val (sakMedUteståendeAvkorting, _, revurderingsvedtak) = sakMedUteståendeAvkorting(
                clock = clock,
                stønadsperiode = Stønadsperiode.create(januar(2021)..mars(2021)),
                revurderingsperiode = januar(2021)..februar(2021),
                utbetalingerKjørtTilOgMed = 1.februar(2021),
            )
            val (sak, revurdering) = opprettetRevurdering(
                revurderingsperiode = mars(2021),
                sakOgVedtakSomKanRevurderes = sakMedUteståendeAvkorting to revurderingsvedtak,
                clock = clock,
            )
            BeregnRevurderingStrategyDecider(
                revurdering = revurdering,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = clock,
                ).getOrFail(),
                clock = clock,
                beregningStrategyFactory = BeregningStrategyFactory(clock, satsFactoryTestPåDato()),
            ).decide() shouldBe beOfType<Normal>()
        }

        @Test
        @Disabled("Må se over denne i plenum. Det er ikke sikkert vi kan trigge denne pathen.")
        fun `velger videreføring dersom utestående avkortinger og grunnlag for avkorting - innvilgelse`() {
            // Dekker AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting -> VidereførAvkorting (annuller innvilgelse)
            val clock = TikkendeKlokke()

            // Iverksetter søknadsbehandling for hele 2021 og revurderer med utenlandsopphold avslag for jan-juni 2021.
            // Jan-juni har blitt kjørt i oppdrag og får derfor tilbakekrevingssimulering (utestående avkorting).
            // juli-desember 2021 blir bare opphørt siden den er "frem i tid".
            val (sakMedUteståendeAvkorting, _) = sakMedUteståendeAvkorting(
                clock = clock,
                utbetalingerKjørtTilOgMed = 31.juli(2021),
            )

            // Ny stønadsperiode året etter. Dvs. vi får avkortingsfradrag for jan-jun 21 og utbetaling for resten.
            val (sakMedAndreStønadsperiode, _, andreStønadsperiode) = iverksattSøknadsbehandling(
                stønadsperiode = Stønadsperiode.create(år(2022)),
                clock = clock,
                sakOgSøknad = sakMedUteståendeAvkorting to nySøknadJournalførtMedOppgave(
                    clock = clock,
                    sakId = sakMedUteståendeAvkorting.id,
                    søknadInnhold = søknadinnholdUføre(
                        personopplysninger = Personopplysninger(sakMedUteståendeAvkorting.fnr),
                    ),
                ),
            )
            // Revurderer utenlandsopphold for andre stønadsperiode.
            // En del av månedene i andre stønadsperiode er låst til avkortinger (jan-juni), så disse kan ikke opphøres atm.
            // Får avkorting for juli 2022 (denne kan man ikke opphøre etter dette).
            val (sakMedRevurderingAvAndreStønadsperiode, revurderingAvAndreStønadsperiode) = vedtakRevurdering(
                clock = clock,
                revurderingsperiode = juli(2022)..august(2022),
                stønadsperiode = Stønadsperiode.create(juli(2022)..august(2022)),
                sakOgVedtakSomKanRevurderes = sakMedAndreStønadsperiode to (andreStønadsperiode as VedtakSomKanRevurderes),
                informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Utenlandsopphold)),
                vilkårOverrides = listOf(
                    utenlandsoppholdAvslag(
                        periode = juli(2022)..august(2022),
                        opprettet = Tidspunkt.now(clock),
                    ),
                ),
                utbetalingerKjørtTilOgMed = 1.august(2022),
            )

            // val (sakMedOpphørAvSep22, revurderingOpphørSep22) = vedtakRevurdering(
            //     clock = clock,
            //     revurderingsperiode = september(2022),
            //     stønadsperiode = Stønadsperiode.create(september(2022)),
            //     sakOgVedtakSomKanRevurderes = sakMedRevurderingAvAndreStønadsperiode to revurderingAvAndreStønadsperiode,
            //     informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Utenlandsopphold)),
            //     vilkårOverrides = listOf(
            //         utenlandsoppholdAvslag(
            //             periode = september(2022),
            //             opprettet = Tidspunkt.now(clock),
            //         ),
            //     ),
            //     // Vi ønsker at denne skal kun føre til opphør (ikke avkorting) så den kan innvilges igjen.
            //     utbetalingerKjørtTilOgMed = 1.august(2022),
            // )

            // Revurderer den delen av siste stønadsperiode som er innvilget, slik at vi får en ny innvilgelse.
            val (sak, tredjeRevurdering) = opprettetRevurdering(
                clock = clock,
                stønadsperiode = Stønadsperiode.create(juni(2022)), // Denne blir ikke brukt siden vi sender med sak og vedtak
                revurderingsperiode = juni(2022),
                sakOgVedtakSomKanRevurderes = sakMedRevurderingAvAndreStønadsperiode to revurderingAvAndreStønadsperiode,
                // vilkårOverrides = listOf(
                //     utenlandsoppholdInnvilget(
                //         periode = juni(2022),
                //         opprettet = Tidspunkt.now(clock),
                //     ),
                // ),
            )
            sak.uteståendeAvkorting.shouldBeInstanceOf<Avkortingsvarsel.Utenlandsopphold.SkalAvkortes>().also {
                it shouldBe Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
                    objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                        id = it.id,
                        sakId = sak.id,
                        revurderingId = revurderingAvAndreStønadsperiode.behandling.id,
                        opprettet = it.opprettet,
                        simulering = it.simulering,
                    ),
                )
            }
            BeregnRevurderingStrategyDecider(
                revurdering = tredjeRevurdering,
                gjeldendeVedtaksdata = sak.hentGjeldendeVedtaksdata(
                    periode = tredjeRevurdering.periode,
                    clock = clock,
                ).getOrFail(),
                clock = clock,
                beregningStrategyFactory = BeregningStrategyFactory(clock, satsFactoryTestPåDato()),
            ).decide() shouldBe beOfType<VidereførAvkorting>()
        }

        @Test
        fun `velger videreføring dersom utestående avkortinger og grunnlag for avkorting - opphør`() {
            // Dekker AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting -> VidereførAvkorting (annuller opphør)
            val clock = TikkendeKlokke()

            // Iverksetter søknadsbehandling for hele 2021 og revurderer med utenlandsopphold avslag for jan-juni 2021.
            // Jan-juni har blitt kjørt i oppdrag og får derfor tilbakekrevingssimulering (utestående avkorting).
            // juli-desember 2021 blir bare opphørt siden den er "frem i tid".
            val (sakMedUteståendeAvkorting, _) = sakMedUteståendeAvkorting(
                clock = clock,
                utbetalingerKjørtTilOgMed = 31.juli(2021),
            )

            // Ny stønadsperiode året etter. Dvs. vi får avkortingsfradrag for jan-jun 21 og utbetaling for resten.
            val (sakMedAndreStønadsperiode, _, andreStønadsperiode) = iverksattSøknadsbehandling(
                stønadsperiode = Stønadsperiode.create(år(2022)),
                clock = clock,
                sakOgSøknad = sakMedUteståendeAvkorting to nySøknadJournalførtMedOppgave(
                    clock = clock,
                    sakId = sakMedUteståendeAvkorting.id,
                    søknadInnhold = søknadinnholdUføre(
                        personopplysninger = Personopplysninger(sakMedUteståendeAvkorting.fnr),
                    ),
                ),
            )
            // Revurderer utenlandsopphold for andre stønadsperiode.
            // En del av månedene i andre stønadsperiode er låst til avkortinger (jan-juni), så disse kan ikke opphøres atm.
            // Får avkorting for jul-nov 2022 (disse kan man ikke opphøre etter dette).
            val (sakMedRevurderingAvAndreStønadsperiode, revurderingAvAndreStønadsperiode) = vedtakRevurdering(
                clock = clock,
                revurderingsperiode = juli(2022)..desember(2022),
                stønadsperiode = Stønadsperiode.create(juli(2022)..desember(2022)),
                sakOgVedtakSomKanRevurderes = sakMedAndreStønadsperiode to (andreStønadsperiode as VedtakSomKanRevurderes),
                informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Utenlandsopphold)),
                vilkårOverrides = listOf(
                    utenlandsoppholdAvslag(
                        periode = juli(2022)..desember(2022),
                        opprettet = Tidspunkt.now(clock),
                    ),
                ),
                utbetalingerKjørtTilOgMed = 31.desember(2022),
            )

            // Revurderer begge stønadsperiodene uten endringer og forventer videreføring og at saken har utestående for andre periode
            val (sak, tredjeRevurdering) = opprettetRevurdering(
                clock = clock,
                stønadsperiode = Stønadsperiode.create(år(2022)), // Denne blir ikke brukt siden vi sender med sak og vedtak
                revurderingsperiode = januar(2022)..desember(2022),
                sakOgVedtakSomKanRevurderes = sakMedRevurderingAvAndreStønadsperiode to revurderingAvAndreStønadsperiode,
            )
            sak.uteståendeAvkorting.shouldBeInstanceOf<Avkortingsvarsel.Utenlandsopphold.SkalAvkortes>().also {
                it shouldBe Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
                    objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                        id = it.id,
                        sakId = sak.id,
                        revurderingId = revurderingAvAndreStønadsperiode.behandling.id,
                        opprettet = it.opprettet,
                        simulering = it.simulering,
                    ),
                )
            }
            BeregnRevurderingStrategyDecider(
                revurdering = tredjeRevurdering,
                gjeldendeVedtaksdata = sak.hentGjeldendeVedtaksdata(
                    periode = tredjeRevurdering.periode,
                    clock = clock,
                ).getOrFail(),
                clock = clock,
                beregningStrategyFactory = BeregningStrategyFactory(clock, satsFactoryTestPåDato()),
            ).decide() shouldBe beOfType<VidereførAvkorting>()
        }

        @Test
        fun `velger videreføring dersom utestående avkortinger og grunnlag for avkorting - else`() {
            // Dekker AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting -> VidereførAvkorting (else)
            val clock = TikkendeKlokke()

            // Iverksetter søknadsbehandling for hele 2021 og revurderer med utenlandsopphold avslag for jan-juni 2021.
            // Jan-juni har blitt kjørt i oppdrag og får derfor tilbakekrevingssimulering (utestående avkorting).
            // juli-desember 2021 blir bare opphørt siden den er "frem i tid".
            val (sakMedUteståendeAvkorting, _) = sakMedUteståendeAvkorting(
                clock = clock,
                utbetalingerKjørtTilOgMed = 31.juli(2021),
            )

            // Ny stønadsperiode året etter. Dvs. vi får avkortingsfradrag for jan-jun 21 og utbetaling for resten.
            val (sakMedAndreStønadsperiode, _, andreStønadsperiode) = iverksattSøknadsbehandling(
                stønadsperiode = Stønadsperiode.create(år(2022)),
                clock = clock,
                sakOgSøknad = sakMedUteståendeAvkorting to nySøknadJournalførtMedOppgave(
                    clock = clock,
                    sakId = sakMedUteståendeAvkorting.id,
                    søknadInnhold = søknadinnholdUføre(
                        personopplysninger = Personopplysninger(sakMedUteståendeAvkorting.fnr),
                    ),
                ),
            )
            // Revurderer utenlandsopphold for andre stønadsperiode.
            // En del av månedene i andre stønadsperiode er låst til avkortinger (jan-juni), så disse kan ikke opphøres atm.
            // Får avkorting for juli 2022 (denne kan man ikke opphøre etter dette).
            val (sakMedRevurderingAvAndreStønadsperiode, revurderingAvAndreStønadsperiode) = vedtakRevurdering(
                clock = clock,
                revurderingsperiode = juli(2022)..august(2022),
                stønadsperiode = Stønadsperiode.create(juli(2022)..august(2022)),
                sakOgVedtakSomKanRevurderes = sakMedAndreStønadsperiode to (andreStønadsperiode as VedtakSomKanRevurderes),
                informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Utenlandsopphold)),
                vilkårOverrides = listOf(
                    utenlandsoppholdAvslag(
                        periode = juli(2022)..august(2022),
                        opprettet = Tidspunkt.now(clock),
                    ),
                ),
                utbetalingerKjørtTilOgMed = 1.august(2022),
            )

            // Revurderer den delen av siste stønadsperiode som er innvilget, slik at vi får en ny innvilgelse.
            val (sak, tredjeRevurdering) = opprettetRevurdering(
                clock = clock,
                stønadsperiode = Stønadsperiode.create(juni(2022)), // Denne blir ikke brukt siden vi sender med sak og vedtak
                revurderingsperiode = juni(2022),
                sakOgVedtakSomKanRevurderes = sakMedRevurderingAvAndreStønadsperiode to revurderingAvAndreStønadsperiode,
                vilkårOverrides = listOf(
                    utenlandsoppholdInnvilget(
                        periode = juni(2022),
                        opprettet = Tidspunkt.now(clock),
                    ),
                ),
            )
            sak.uteståendeAvkorting.shouldBeInstanceOf<Avkortingsvarsel.Utenlandsopphold.SkalAvkortes>().also {
                it shouldBe Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
                    objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                        id = it.id,
                        sakId = sak.id,
                        revurderingId = revurderingAvAndreStønadsperiode.behandling.id,
                        opprettet = it.opprettet,
                        simulering = it.simulering,
                    ),
                )
            }
            BeregnRevurderingStrategyDecider(
                revurdering = tredjeRevurdering,
                gjeldendeVedtaksdata = sak.hentGjeldendeVedtaksdata(
                    periode = tredjeRevurdering.periode,
                    clock = clock,
                ).getOrFail(),
                clock = clock,
                beregningStrategyFactory = BeregningStrategyFactory(clock, satsFactoryTestPåDato()),
            ).decide() shouldBe beOfType<VidereførAvkorting>()
        }
    }
}
