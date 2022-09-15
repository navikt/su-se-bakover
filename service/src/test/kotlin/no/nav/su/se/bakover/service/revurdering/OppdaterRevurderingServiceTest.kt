package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.august
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.toPeriode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.Vurderingstatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.periodeNesteMånedOgTreMånederFram
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.stønadsperiodeNesteMånedOgTreMånederFram
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.nySakUføre
import no.nav.su.se.bakover.test.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.oppgaveIdRevurdering
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknadinnhold
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vilkår.avslåttFormueVilkår
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class OppdaterRevurderingServiceTest {
    @Test
    fun `ugyldig begrunnelse`() {
        val mocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn nySakUføre().first
            },
        )
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                periode = år(2021),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.UgyldigBegrunnelse.left()
        verify(mocks.sakService).hentSakForRevurdering(revurderingId)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `ugyldig årsak`() {
        val mocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn nySakUføre().first
            },
        )
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                periode = år(2021),
                årsak = "UGYLDIG_ÅRSAK",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.UgyldigÅrsak.left()
        verify(mocks.sakService).hentSakForRevurdering(revurderingId)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Fant ikke revurdering`() {
        val (sak, _) = iverksattSøknadsbehandlingUføre()
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        ).also {
            it.revurderingService.oppdaterRevurdering(
                OppdaterRevurderingRequest(
                    revurderingId = revurderingId,
                    periode = år(2021),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "gyldig begrunnelse",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
                ),
            ) shouldBe KunneIkkeOppdatereRevurdering.FeilVedOppdateringAvRevurdering(Sak.KunneIkkeOppdatereRevurdering.FantIkkeRevurdering).left()
        }
    }

    @Test
    fun `Kan ikke oppdatere sendt forhåndsvarslet revurdering`() {
        val (sak, revurdering) = simulertRevurdering(
            revurderingsperiode = år(2021),
            forhåndsvarsel = Forhåndsvarsel.UnderBehandling.Sendt,
        )
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        ).also {
            val actual = it.revurderingService.oppdaterRevurdering(
                OppdaterRevurderingRequest(
                    revurderingId = revurdering.id,
                    periode = periodeNesteMånedOgTreMånederFram.fraOgMed.rangeTo(revurdering.periode.tilOgMed).toPeriode(),
                    årsak = "REGULER_GRUNNBELØP",
                    begrunnelse = "gyldig begrunnelse",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
                ),
            )
            actual shouldBe KunneIkkeOppdatereRevurdering.FeilVedOppdateringAvRevurdering(Sak.KunneIkkeOppdatereRevurdering.KunneIkkeOppdatere(Revurdering.KunneIkkeOppdatereRevurdering.KanIkkeOppdatereRevurderingSomErForhåndsvarslet)).left()
        }
    }

    @Test
    fun `Kan ikke oppdatere besluttet forhåndsvarslet revurdering`() {
        val (sak, revurdering) = simulertRevurdering(
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag("begrunnelse"),
        )
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        ).also {
            it.revurderingService.oppdaterRevurdering(
                OppdaterRevurderingRequest(
                    revurderingId = revurdering.id,
                    periode = periodeNesteMånedOgTreMånederFram.fraOgMed.rangeTo(revurdering.periode.tilOgMed).toPeriode(),
                    årsak = "REGULER_GRUNNBELØP",
                    begrunnelse = "gyldig begrunnelse",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
                ),
            ) shouldBe KunneIkkeOppdatereRevurdering.FeilVedOppdateringAvRevurdering(Sak.KunneIkkeOppdatereRevurdering.KunneIkkeOppdatere(Revurdering.KunneIkkeOppdatereRevurdering.KanIkkeOppdatereRevurderingSomErForhåndsvarslet)).left()
        }
    }

    @Test
    fun `Oppdatering av iverksatt revurdering gir ugyldig tilstand`() {
        val (sak, iverksatt) = iverksattRevurdering()

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        ).also {
            val actual = it.revurderingService.oppdaterRevurdering(
                OppdaterRevurderingRequest(
                    revurderingId = iverksatt.id,
                    periode = periodeNesteMånedOgTreMånederFram.fraOgMed.rangeTo(iverksatt.periode.tilOgMed).toPeriode(),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "gyldig begrunnelse",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
                ),
            )
            actual shouldBe KunneIkkeOppdatereRevurdering.FeilVedOppdateringAvRevurdering(
                Sak.KunneIkkeOppdatereRevurdering.KunneIkkeOppdatere(
                    Revurdering.KunneIkkeOppdatereRevurdering.UgyldigTilstand(
                        IverksattRevurdering.Innvilget::class,
                        OpprettetRevurdering::class,
                    ),
                ),
            ).left()
            verify(it.sakService).hentSakForRevurdering(iverksatt.id)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `oppdater en revurdering`() {
        val (sak, revurdering) = opprettetRevurdering(
            revurderingsperiode = stønadsperiodeNesteMånedOgTreMånederFram.periode,
            stønadsperiode = stønadsperiodeNesteMånedOgTreMånederFram,
        )
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            revurderingRepo = mock(),
        ).also {
            val oppdatertPeriode = Periode.create(
                periodeNesteMånedOgTreMånederFram.fraOgMed.plus(1, ChronoUnit.MONTHS),
                periodeNesteMånedOgTreMånederFram.tilOgMed,
            )
            val actual = it.revurderingService.oppdaterRevurdering(
                // Bruker andre verdier enn den opprinnelige revurderingen for å se at de faktisk forandrer seg
                OppdaterRevurderingRequest(
                    revurderingId = revurdering.id,
                    periode = oppdatertPeriode.fraOgMed.rangeTo(revurdering.periode.tilOgMed).toPeriode(),
                    årsak = "ANDRE_KILDER",
                    begrunnelse = "bør bli oppdatert",
                    saksbehandler = NavIdentBruker.Saksbehandler("En ny saksbehandlinger"),
                    informasjonSomRevurderes = listOf(Revurderingsteg.Inntekt),
                ),
            ).getOrFail()

            actual.let { oppdatertRevurdering ->
                oppdatertRevurdering.periode shouldBe oppdatertPeriode
                oppdatertRevurdering.tilRevurdering shouldBe sak.vedtakListe.single().id
                oppdatertRevurdering.saksbehandler shouldBe NavIdentBruker.Saksbehandler("En ny saksbehandlinger")
                oppdatertRevurdering.oppgaveId shouldBe oppgaveIdRevurdering
                oppdatertRevurdering.fritekstTilBrev shouldBe ""
                oppdatertRevurdering.revurderingsårsak shouldBe Revurderingsårsak(
                    årsak = Revurderingsårsak.Årsak.ANDRE_KILDER,
                    begrunnelse = Revurderingsårsak.Begrunnelse.create("bør bli oppdatert"),
                )
                oppdatertRevurdering.forhåndsvarsel shouldBe null
                oppdatertRevurdering.vilkårsvurderinger.erLik(sak.søknadsbehandlinger.single().vilkårsvurderinger)
                oppdatertRevurdering.vilkårsvurderinger.vilkår.all { it.perioder == listOf(oppdatertPeriode) }
                oppdatertRevurdering.informasjonSomRevurderes shouldBe InformasjonSomRevurderes.create(
                    mapOf(
                        Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                    ),
                )
            }

            inOrder(
                *it.all(),
            ) {
                verify(it.sakService).hentSakForRevurdering(revurdering.id)
                verify(it.revurderingRepo).defaultTransactionContext()
                verify(it.revurderingRepo).lagre(argThat { it shouldBe actual }, anyOrNull())
            }
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `må velge minst ting som skal revurderes`() {
        val (sak, revurdering) = opprettetRevurdering()
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        ).also {
            it.revurderingService.oppdaterRevurdering(
                OppdaterRevurderingRequest(
                    revurderingId = revurdering.id,
                    periode = periodeNesteMånedOgTreMånederFram.fraOgMed.rangeTo(revurdering.periode.tilOgMed).toPeriode(),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = emptyList(),
                ),
            ) shouldBe KunneIkkeOppdatereRevurdering.MåVelgeInformasjonSomSkalRevurderes.left()
        }
    }

    @Test
    fun `grunnlag resettes dersom man oppdaterer revurderingen`() {
        val arbeidsinntekt = 10000.0
        val (sak, revurdering) = opprettetRevurdering(
            vilkårOverrides = listOf(
                avslåttUførevilkårUtenGrunnlag(),
                avslåttFormueVilkår(),
            ),
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = arbeidsinntekt),
            ),
        )
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            revurderingRepo = mock(),
        ).also {
            val actual = it.revurderingService.oppdaterRevurdering(
                OppdaterRevurderingRequest(
                    revurderingId = revurdering.id,
                    periode = 1.mai(2021).rangeTo(revurdering.periode.tilOgMed).toPeriode(),
                    årsak = "REGULER_GRUNNBELØP",
                    begrunnelse = "g-regulering",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
                ),
            ).getOrFail()

            actual.periode shouldBe mai(2021).rangeTo(desember(2021))
            actual.revurderingsårsak.årsak shouldBe Revurderingsårsak.Årsak.REGULER_GRUNNBELØP
            actual.revurderingsårsak.begrunnelse.toString() shouldBe "g-regulering"
            actual.vilkårsvurderinger shouldNotBe revurdering.vilkårsvurderinger
            actual.vilkårsvurderinger.vilkår.all { it.erInnvilget }
            actual.grunnlagsdata shouldNotBe revurdering.grunnlagsdata
            actual.grunnlagsdata.fradragsgrunnlag.none { it.fradrag.månedsbeløp == arbeidsinntekt }

            inOrder(
                *it.all(),
            ) {
                verify(it.sakService).hentSakForRevurdering(revurdering.id)
                verify(it.revurderingRepo).defaultTransactionContext()
                verify(it.revurderingRepo).lagre(eq(actual), anyOrNull())
            }
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kan ikke revurdere perioder hvor det ikke eksisterer vedtak for alle månedene i revurderingsperioden`() {
        val sakMedFørstegangsbehandling = iverksattSøknadsbehandlingUføre(
            stønadsperiode = Stønadsperiode.create(januar(2021).rangeTo(juli(2021))),
        )

        val sakMedNyStønadsperiode = iverksattSøknadsbehandlingUføre(
            stønadsperiode = Stønadsperiode.create(januar(2022).rangeTo(desember(2022))),
            sakOgSøknad = sakMedFørstegangsbehandling.first to nySøknadJournalførtMedOppgave(
                sakId = sakMedFørstegangsbehandling.first.id,
                søknadInnhold = søknadinnhold(
                    fnr = sakMedFørstegangsbehandling.first.fnr,
                ),
            ),
        )

        val opprettetRevurdering = opprettetRevurdering(
            sakOgVedtakSomKanRevurderes = sakMedNyStønadsperiode.first to sakMedNyStønadsperiode.third as VedtakSomKanRevurderes,
        )

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn opprettetRevurdering.first
            },
        ).also {
            // fullstendig overlapp med hull mellom stønadsperiodene
            it.revurderingService.oppdaterRevurdering(
                OppdaterRevurderingRequest(
                    revurderingId = opprettetRevurdering.second.id,
                    periode = 1.mai(2021).rangeTo(sakMedNyStønadsperiode.second.periode.tilOgMed).toPeriode(),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Test",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
                ),
            ) shouldBe KunneIkkeOppdatereRevurdering.FeilVedOppdateringAvRevurdering(
                Sak.KunneIkkeOppdatereRevurdering.GjeldendeVedtaksdataKanIkkeRevurderes(
                    Sak.GjeldendeVedtaksdataErUgyldigForRevurdering.HeleRevurderingsperiodenInneholderIkkeVedtak,
                ),
            ).left()

            // delvis overlapp med hull mellom stønadsperioder
            it.revurderingService.oppdaterRevurdering(
                OppdaterRevurderingRequest(
                    revurderingId = opprettetRevurdering.second.id,
                    periode = mai(2021).rangeTo(august(2021)),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Test",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
                ),
            ) shouldBe KunneIkkeOppdatereRevurdering.FeilVedOppdateringAvRevurdering(
                Sak.KunneIkkeOppdatereRevurdering.GjeldendeVedtaksdataKanIkkeRevurderes(
                    Sak.GjeldendeVedtaksdataErUgyldigForRevurdering.HeleRevurderingsperiodenInneholderIkkeVedtak,
                ),
            ).left()
        }
    }

    @Test
    fun `får lov til å oppdatere revurdering dersom periode overlapper opphørsvedtak for utenlandsopphold som ikke førte til avkorting`() {
        val tikkendeKlokke = TikkendeKlokke()

        val sakOgSøknadsvedtak = iverksattSøknadsbehandlingUføre(
            stønadsperiode = stønadsperiode2021,
            clock = tikkendeKlokke,
        )

        val revurderingsperiode = Periode.create(1.oktober(2021), 31.desember(2021))

        val sakOgSøknadsvedtakOgRevurderingsvedtak = vedtakRevurdering(
            clock = tikkendeKlokke,
            revurderingsperiode = revurderingsperiode,
            sakOgVedtakSomKanRevurderes = sakOgSøknadsvedtak.first to sakOgSøknadsvedtak.third as VedtakSomKanRevurderes,
            vilkårOverrides = listOf(
                utenlandsoppholdAvslag(
                    periode = revurderingsperiode,
                ),
            ),
        )
        val (sak3, opprettetRevurdering) = opprettetRevurdering(
            sakOgVedtakSomKanRevurderes = sakOgSøknadsvedtakOgRevurderingsvedtak,
        )

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak3
            },
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettetRevurdering
            },
            avkortingsvarselRepo = mock {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
        ).also {
            it.revurderingService.oppdaterRevurdering(
                oppdaterRevurderingRequest = OppdaterRevurderingRequest(
                    revurderingId = opprettetRevurdering.id,
                    periode = revurderingsperiode,
                    årsak = Revurderingsårsak.Årsak.ANDRE_KILDER.toString(),
                    begrunnelse = "lol",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Utenlandsopphold),
                ),
            ).getOrFail() shouldBe beOfType<OpprettetRevurdering>()
        }
    }

    @Test
    fun `får feilmelding dersom saken har utestående avkorting, men revurderingsperioden inneholder ikke perioden for avkortingen`() {
        val clock = TikkendeKlokke(fixedClock)
        val (sak1, opphørUtenlandsopphold) = vedtakRevurdering(
            clock = clock,
            revurderingsperiode = Periode.create(1.juni(2021), 31.desember(2021)),
            vilkårOverrides = listOf(
                utenlandsoppholdAvslag(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = Periode.create(1.juni(2021), 31.desember(2021)),
                ),
            ),
        )
        val nyRevurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021))

        val (sak2, nyRevurdering) = opprettetRevurdering(
            sakOgVedtakSomKanRevurderes = sak1 to opphørUtenlandsopphold,
        )
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak2
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
        ).let {
            it.revurderingService.oppdaterRevurdering(
                OppdaterRevurderingRequest(
                    revurderingId = nyRevurdering.id,
                    periode = nyRevurderingsperiode,
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Utenlandsopphold),
                ),
            ) shouldBe KunneIkkeOppdatereRevurdering.FeilVedOppdateringAvRevurdering(
                Sak.KunneIkkeOppdatereRevurdering.UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(juni(2021)),
            ).left()
        }
    }
}
