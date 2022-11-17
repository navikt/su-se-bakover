package no.nav.su.se.bakover.domain.revurdering.beregning

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import no.nav.su.se.bakover.domain.søknadinnhold.Personopplysninger
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOpplysningsplikt
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknad.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkår
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class BeregnRevurderingStrategyDeciderTest {

    @Nested
    inner class IngenUtestående {
        @Test
        fun `velger normal dersom ingen utestående avkortinger eller grunnlag for avkorting`() {
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
        fun `velger videreføring dersom ingen utestående avkortinger men grunnlag for avkorting`() {
            val (sak, revurdering) = opprettetRevurdering(
                sakOgVedtakSomKanRevurderes = vedtakSøknadsbehandlingIverksattInnvilget(),
            )
            BeregnRevurderingStrategyDecider(
                revurdering = revurdering,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                clock = fixedClock,
                beregningStrategyFactory = BeregningStrategyFactory(fixedClock, satsFactoryTestPåDato()),
            ).decide() shouldBe beOfType<VidereførAvkorting>()
        }

        @Test
        fun `annullerer avkorting dersom vi revurderer innvilget tilbake til tidspunkt for opprettelse av avkortingsvarsel eller tidligere - opphør`() {
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
                clock = tikkendeKlokke,
                sakOgVedtakSomKanRevurderes = sak to nyStønadsperiode,
                revurderingsperiode = Periode.create(1.februar(2021), 30.juni(2022)),
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
            clock = tikkendeKlokke,
            sakOgVedtakSomKanRevurderes = sak to nyStønadsperiode,
            revurderingsperiode = Periode.create(1.februar(2021), 30.juni(2022)),
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
        fun `velger normal dersom utestående avkortinger men ingen grunnlag for avkorting`() {
            val (sak, revurdering) = opprettetRevurdering(
                avkorting = AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
                    avkortingsvarsel = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
                        objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                            sakId = UUID.randomUUID(),
                            revurderingId = UUID.randomUUID(),
                            simulering = simuleringFeilutbetaling(mai(2021), juni(2021)),
                            opprettet = Tidspunkt.now(fixedClock),
                        ),
                    ),
                ),
            )
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
        fun `velger videreføring dersom utestående avkortinger og grunnlag for avkorting`() {
            val (sak, revurdering) = opprettetRevurdering(
                sakOgVedtakSomKanRevurderes = vedtakSøknadsbehandlingIverksattInnvilget(),
                avkorting = AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
                    avkortingsvarsel = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
                        objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                            sakId = UUID.randomUUID(),
                            revurderingId = UUID.randomUUID(),
                            simulering = simuleringFeilutbetaling(mai(2021), juni(2021)),
                            opprettet = Tidspunkt.now(fixedClock),
                        ),
                    ),
                ),
            )
            BeregnRevurderingStrategyDecider(
                revurdering = revurdering,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                clock = fixedClock,
                beregningStrategyFactory = BeregningStrategyFactory(fixedClock, satsFactoryTestPåDato()),
            ).decide() shouldBe beOfType<VidereførAvkorting>()
        }

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
                    clock = tikkendeKlokke,
                    sakOgVedtakSomKanRevurderes = sak1 to opphørUtenlandsopphold,
                    revurderingsperiode = Periode.create(1.august(2021), 31.desember(2021)),
                    avkorting = AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
                        avkortingsvarsel = opphørUtenlandsopphold.hentUteståendeAvkorting().avkortingsvarsel(),
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
                    clock = tikkendeKlokke,
                    sakOgVedtakSomKanRevurderes = sak1 to opphørUtenlandsopphold,
                    revurderingsperiode = Periode.create(1.mars(2021), 31.desember(2021)),
                    avkorting = AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
                        avkortingsvarsel = opphørUtenlandsopphold.hentUteståendeAvkorting().avkortingsvarsel(),
                    ),
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
                    clock = tikkendeKlokke,
                    sakOgVedtakSomKanRevurderes = sak1 to opphørUtenlandsopphold,
                    revurderingsperiode = Periode.create(1.august(2021), 31.desember(2021)),
                    avkorting = AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
                        avkortingsvarsel = opphørUtenlandsopphold.hentUteståendeAvkorting().avkortingsvarsel(),
                    ),
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
        fun `viderefører avkortinger håndtert av tidligere søknadsbehandling`() {
            val (sak, revurdering) = opprettetRevurdering(
                sakOgVedtakSomKanRevurderes = vedtakSøknadsbehandlingIverksattInnvilget(),
                avkorting = AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
                    avkortingsvarsel = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
                        objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                            sakId = UUID.randomUUID(),
                            revurderingId = UUID.randomUUID(),
                            simulering = simuleringFeilutbetaling(mai(2021), juni(2021)),
                            opprettet = Tidspunkt.now(fixedClock),
                        ),
                    ),
                ),
            )
            BeregnRevurderingStrategyDecider(
                revurdering = revurdering,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                clock = fixedClock,
                beregningStrategyFactory = BeregningStrategyFactory(fixedClock, satsFactoryTestPåDato()),
            ).decide() shouldBe beOfType<VidereførAvkorting>()
        }

        @Test
        fun `viderefør avkorting dersom utestående avkorting og avkortingsgrunnlag - opphør`() {
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
                clock = tikkendeKlokke,
                sakOgVedtakSomKanRevurderes = sak to nyStønadsperiode,
                revurderingsperiode = Periode.create(1.februar(2021), 30.juni(2022)),
                avkorting = AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
                    avkortingsvarsel = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
                        objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                            sakId = UUID.randomUUID(),
                            revurderingId = UUID.randomUUID(),
                            simulering = simuleringFeilutbetaling(mai(2021), juni(2021)),
                            opprettet = Tidspunkt.now(fixedClock),
                        ),
                    ),
                ),
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
            ).decide() shouldBe beOfType<VidereførAvkorting>()
        }

        @Test
        fun `viderefør avkorting dersom utestående avkorting og avkortingsgrunnlag - innvilget`() {
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
                clock = tikkendeKlokke,
                sakOgVedtakSomKanRevurderes = sak to nyStønadsperiode,
                revurderingsperiode = Periode.create(1.februar(2021), 30.juni(2022)),
                vilkårOverrides = listOf(
                    utenlandsoppholdInnvilget(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = Periode.create(1.februar(2021), 30.juni(2022)),
                    ),
                ),
                avkorting = AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
                    avkortingsvarsel = Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
                        objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                            sakId = UUID.randomUUID(),
                            revurderingId = UUID.randomUUID(),
                            simulering = simuleringFeilutbetaling(mai(2021), juni(2021)),
                            opprettet = Tidspunkt.now(fixedClock),
                        ),
                    ),
                ),
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
            ).decide() shouldBe beOfType<VidereførAvkorting>()
        }

        @Test
        fun `annuller avkorting dersom utestående avkorting og ingen avkortingsgrunnlag - opphør`() {
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
                clock = tikkendeKlokke,
                sakOgVedtakSomKanRevurderes = sak to opphørUtenlandsopphold,
                revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                avkorting = AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
                    avkortingsvarsel = opphørUtenlandsopphold.hentUteståendeAvkorting().avkortingsvarsel(),
                ),
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
        fun `annuller avkorting dersom utestående avkorting og ingen avkortingsgrunnlag - innvilget`() {
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
                clock = tikkendeKlokke,
                sakOgVedtakSomKanRevurderes = sak to opphørUtenlandsopphold,
                revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                avkorting = AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting(
                    avkortingsvarsel = opphørUtenlandsopphold.hentUteståendeAvkorting().avkortingsvarsel(),
                ),
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
    }
}

private fun Vedtak.hentUteståendeAvkorting(): AvkortingVedRevurdering.Iverksatt.HarProdusertNyttAvkortingsvarsel {
    return (this as VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering).behandling.avkorting as AvkortingVedRevurdering.Iverksatt.HarProdusertNyttAvkortingsvarsel
}
