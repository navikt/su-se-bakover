package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.toPeriode
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.august
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.oppdater.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.domain.revurdering.oppdater.OppdaterRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.steg.Vurderingstatus
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Personopplysninger
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.nySakUføre
import no.nav.su.se.bakover.test.oppgaveIdRevurdering
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.søknad.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.vilkår.avslåttFormueVilkår
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class OppdaterVilkårsvurderingerRevurderingServiceTest {
    @Test
    fun `ugyldig begrunnelse`() {
        val mocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn nySakUføre().first
            },
        )
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingCommand(
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
            OppdaterRevurderingCommand(
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
            val command = OppdaterRevurderingCommand(
                revurderingId = revurderingId,
                periode = år(2021),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
            )
            shouldThrow<IllegalArgumentException> {
                it.revurderingService.oppdaterRevurdering(
                    command,
                )
            }.message shouldBe "Fant ikke revurdering med id ${command.revurderingId}"
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
                OppdaterRevurderingCommand(
                    revurderingId = iverksatt.id,
                    periode = iverksatt.periode,
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "gyldig begrunnelse",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
                ),
            )
            actual shouldBe KunneIkkeOppdatereRevurdering.UgyldigTilstand(
                IverksattRevurdering.Innvilget::class,
                OpprettetRevurdering::class,
            ).left()
            verify(it.sakService).hentSakForRevurdering(iverksatt.id)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `oppdater en revurdering`() {
        val (sak, revurdering) = opprettetRevurdering()
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            revurderingRepo = mock(),
        ).also {
            val oppdatertPeriode = januar(2021)..mars(2021)
            val actual = it.revurderingService.oppdaterRevurdering(
                // Bruker andre verdier enn den opprinnelige revurderingen for å se at de faktisk forandrer seg
                OppdaterRevurderingCommand(
                    revurderingId = revurdering.id,
                    periode = oppdatertPeriode,
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
                oppdatertRevurdering.revurderingsårsak shouldBe Revurderingsårsak(
                    årsak = Revurderingsårsak.Årsak.ANDRE_KILDER,
                    begrunnelse = Revurderingsårsak.Begrunnelse.create("bør bli oppdatert"),
                )
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
                OppdaterRevurderingCommand(
                    revurderingId = revurdering.id,
                    periode = revurdering.periode,
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
                OppdaterRevurderingCommand(
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
        val clock = TikkendeKlokke()
        val sakMedFørstegangsbehandling = iverksattSøknadsbehandlingUføre(
            stønadsperiode = Stønadsperiode.create(januar(2021)..juli(2021)),
            clock = clock,
        )

        val sakMedNyStønadsperiode = iverksattSøknadsbehandlingUføre(
            stønadsperiode = Stønadsperiode.create(januar(2022)..desember(2022)),
            sakOgSøknad = sakMedFørstegangsbehandling.first to nySøknadJournalførtMedOppgave(
                sakId = sakMedFørstegangsbehandling.first.id,
                søknadInnhold = søknadinnholdUføre(
                    personopplysninger = Personopplysninger(sakMedFørstegangsbehandling.first.fnr),
                ),
            ),
            clock = clock,
        )

        val opprettetRevurdering = opprettetRevurdering(
            sakOgVedtakSomKanRevurderes = sakMedNyStønadsperiode.first to sakMedNyStønadsperiode.third as VedtakSomKanRevurderes,
            // Setter en lovlig periode ved opprettelse for ikke å feile allerede her.
            revurderingsperiode = januar(2021)..juli(2021),
            clock = clock,
        )

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn opprettetRevurdering.first
            },
            clock = clock,
        ).also {
            // fullstendig overlapp med hull mellom stønadsperiodene
            it.revurderingService.oppdaterRevurdering(
                OppdaterRevurderingCommand(
                    revurderingId = opprettetRevurdering.second.id,
                    periode = 1.mai(2021).rangeTo(sakMedNyStønadsperiode.second.periode.tilOgMed).toPeriode(),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Test",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
                ),
            ) shouldBe KunneIkkeOppdatereRevurdering.GjeldendeVedtaksdataKanIkkeRevurderes(
                Sak.GjeldendeVedtaksdataErUgyldigForRevurdering.HeleRevurderingsperiodenInneholderIkkeVedtak,
            ).left()

            // delvis overlapp med hull mellom stønadsperioder
            it.revurderingService.oppdaterRevurdering(
                OppdaterRevurderingCommand(
                    revurderingId = opprettetRevurdering.second.id,
                    periode = mai(2021).rangeTo(august(2021)),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Test",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
                ),
            ) shouldBe KunneIkkeOppdatereRevurdering.GjeldendeVedtaksdataKanIkkeRevurderes(
                Sak.GjeldendeVedtaksdataErUgyldigForRevurdering.HeleRevurderingsperiodenInneholderIkkeVedtak,
            ).left()
        }
    }
}
