package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.Vurderingstatus
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.saksbehandler
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.søknadOppgaveId
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.beregning.TestBeregningSomGirOpphør
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.service.formueVilkår
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.createRevurderingService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.periodeNesteMånedOgTreMånederFram
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.revurderingsårsak
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.attesteringUnderkjent
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.tilAttesteringRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.utlandsoppholdInnvilget
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

class RevurderingIngenEndringTest {

    @Test
    fun `Revurderingen går ikke gjennom hvis endring av utbetaling er under ti prosent`() {
        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            periode = periodeNesteMånedOgTreMånederFram,
            uføregrad = Uføregrad.parse(20),
            forventetInntekt = 12000,
            opprettet = fixedTidspunkt,
        )
        val tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second
        val opprettetRevurdering = OpprettetRevurdering(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = fixedTidspunkt,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            oppgaveId = søknadOppgaveId,
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = periodeNesteMånedOgTreMånederFram,
                        begrunnelse = null,
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            resultat = Resultat.Innvilget,
                            grunnlag = uføregrunnlag,
                            periode = periodeNesteMånedOgTreMånederFram,
                            begrunnelse = "ok2k",
                        ),
                    ),
                ),
                formue = formueVilkår(periodeNesteMånedOgTreMånederFram),
                oppholdIUtlandet = utlandsoppholdInnvilget(periode = periodeNesteMånedOgTreMånederFram),
            ),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(
                mapOf(
                    Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                ),
            ),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn opprettetRevurdering
        }

        val utbetalingMock = mock<Utbetaling> {
            on { utbetalingslinjer } doReturn nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                    tilOgMed = periodeNesteMånedOgTreMånederFram.tilOgMed,
                    forrigeUtbetalingslinjeId = null,
                    beløp = 20000,
                    uføregrad = Uføregrad.parse(50),
                ),
            )
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { hentUtbetalinger(any()) } doReturn listOf(utbetalingMock)
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).beregnOgSimuler(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
        ).orNull()!!.revurdering as BeregnetRevurdering.IngenEndring
        actual.shouldBeEqualToIgnoringFields(
            BeregnetRevurdering.IngenEndring(
                id = revurderingId,
                periode = periodeNesteMånedOgTreMånederFram,
                opprettet = fixedTidspunkt,
                tilRevurdering = tilRevurdering,
                oppgaveId = søknadOppgaveId,
                beregning = TestBeregning,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = null,
                grunnlagsdata = opprettetRevurdering.grunnlagsdata,
                vilkårsvurderinger = opprettetRevurdering.vilkårsvurderinger,
                informasjonSomRevurderes = InformasjonSomRevurderes.create(
                    mapOf(
                        Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                    ),
                ),
                attesteringer = Attesteringshistorikk.empty(),
            ),
            // beregningstypen er internal i domene modulen
            BeregnetRevurdering.IngenEndring::beregning,
            BeregnetRevurdering::grunnlagsdata,
        )

        inOrder(
            revurderingRepoMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual })
        }
        verifyNoMoreInteractions(
            revurderingRepoMock,
        )
    }

    @Test
    fun `attesterer revurdering som ikke fører til endring i ytelse`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger.Revurdering> {
            on { resultat } doReturn Vilkårsvurderingsresultat.Innvilget(emptySet())
        }
        val tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second
        val beregnetRevurdering = BeregnetRevurdering.IngenEndring(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = tilRevurdering,
            oppgaveId = søknadOppgaveId,
            beregning = TestBeregningSomGirOpphør,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingerMock,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
        )
        val endretSaksbehandler = NavIdentBruker.Saksbehandler("endretSaksbehandler")
        val revurderingTilAttestering = RevurderingTilAttestering.IngenEndring(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = tilRevurdering,
            oppgaveId = søknadOppgaveId,
            beregning = TestBeregningSomGirOpphør,
            saksbehandler = endretSaksbehandler,
            fritekstTilBrev = "endret fritekst",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            skalFøreTilBrevutsending = true,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingerMock,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
        )
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn beregnetRevurdering
            on { hentEventuellTidligereAttestering(any()) } doReturn null
        }
        val utbetalingServiceMock = mock<UtbetalingService>()
        val vedtakRepoMock = mock<VedtakRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn søknadOppgaveId.right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }
        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
            vedtakRepo = vedtakRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId,
                endretSaksbehandler,
                "endret fritekst",
                true,
            ),
        ).getOrHandle { throw RuntimeException(it.toString()) } as RevurderingTilAttestering.IngenEndring

        actual shouldBe revurderingTilAttestering

        inOrder(
            revurderingRepoMock,
            utbetalingServiceMock,
            vedtakRepoMock,
            personServiceMock,
            oppgaveServiceMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(revurderingRepoMock).hentEventuellTidligereAttestering(argThat { it shouldBe revurderingId })
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.AttesterRevurdering(
                        saksnummer = saksnummer,
                        aktørId = aktørId,
                    )
                },
            )
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe søknadOppgaveId })
            verify(revurderingRepoMock).lagre(argThat { it shouldBe revurderingTilAttestering })
        }
        verifyNoMoreInteractions(
            personServiceMock,
            oppgaveServiceMock,
            revurderingRepoMock,
            utbetalingServiceMock,
            vedtakRepoMock,
        )
    }

    @Test
    fun `underkjenn revurdering`() {
        val tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second
        val revurderingTilAttestering = RevurderingTilAttestering.IngenEndring(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = tilRevurdering,
            oppgaveId = søknadOppgaveId,
            beregning = TestBeregningSomGirOpphør,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            skalFøreTilBrevutsending = false,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
        )
        val underkjentRevurdering = UnderkjentRevurdering.IngenEndring(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = tilRevurdering,
            oppgaveId = søknadOppgaveId,
            beregning = TestBeregningSomGirOpphør,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(attesteringUnderkjent),
            skalFøreTilBrevutsending = false,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurderingTilAttestering
        }
        val utbetalingServiceMock = mock<UtbetalingService>()
        val vedtakRepoMock = mock<VedtakRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn søknadOppgaveId.right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }
        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
            vedtakRepo = vedtakRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).underkjenn(
            revurderingId,
            attesteringUnderkjent,
        ).orNull()!! as UnderkjentRevurdering.IngenEndring

        actual shouldBe underkjentRevurdering

        inOrder(
            revurderingRepoMock,
            utbetalingServiceMock,
            vedtakRepoMock,
            personServiceMock,
            oppgaveServiceMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Revurderingsbehandling(
                        saksnummer = saksnummer,
                        aktørId = aktørId,
                        tilordnetRessurs = saksbehandler,
                    )
                },
            )
            verify(revurderingRepoMock).lagre(argThat { it shouldBe underkjentRevurdering })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe søknadOppgaveId })
        }
        verifyNoMoreInteractions(
            personServiceMock,
            oppgaveServiceMock,
            revurderingRepoMock,
            utbetalingServiceMock,
            vedtakRepoMock,
        )
    }

    @Test
    fun `iverksetter revurdering som ikke fører til endring i ytelse og sender brev`() {
        val (sak, revurderingTilAttestering) = tilAttesteringRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
            skalFøreTilBrevutsending = true,
        )

        val iverksattRevurdering = revurderingTilAttestering.tilIverksatt(attestant, fixedClock).orNull()!!
        val vedtak = Vedtak.from(iverksattRevurdering, fixedClock)

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurderingTilAttestering
            },
            brevService = mock {
                on { lagDokument(any()) } doReturn Dokument.UtenMetadata.Vedtak(
                    opprettet = fixedTidspunkt,
                    tittel = "tittel1",
                    generertDokument = "brev".toByteArray(),
                    generertDokumentJson = "brev",
                ).right()
            },
            utbetalingService = mock {
                on {
                    hentGjeldendeUtbetaling(
                        any(),
                        any(),
                    )
                } doReturn sak.utbetalingstidslinje(revurderingTilAttestering.periode)
                    .gjeldendeForDato(revurderingTilAttestering.periode.fraOgMed)!!.right()
            },
        ).let {
            it.revurderingService.iverksett(
                revurderingId = revurderingId,
                attestant = attestant,
            ) shouldBe iverksattRevurdering.right()

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(revurderingTilAttestering.id)
                verify(it.brevService).lagDokument(argThat { it shouldBe beOfType<Vedtak.IngenEndringIYtelse>() })
                verify(it.vedtakRepo).lagre(argThat { it shouldBe vedtak.copy(id = it.id) })
                verify(it.brevService).lagreDokument(
                    argThat {
                        it shouldBe beOfType<Dokument.MedMetadata.Vedtak>()
                        it.generertDokument shouldNotBe null
                        it.generertDokumentJson shouldNotBe null
                        it.metadata.sakId shouldBe sak.id
                        it.metadata.vedtakId shouldNotBe null
                        it.metadata.bestillBrev shouldBe true
                    },
                )
                verify(it.revurderingRepo).lagre(iverksattRevurdering)

                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `iverksetter revurdering som ikke fører til endring i ytelse og sender ikke brev`() {
        val (_, revurderingTilAttestering) = tilAttesteringRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
            skalFøreTilBrevutsending = false,
        )

        val iverksattRevurdering = revurderingTilAttestering.tilIverksatt(attestant, fixedClock).orNull()!!
        val vedtak = Vedtak.from(iverksattRevurdering, fixedClock)

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurderingTilAttestering
        }

        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = revurderingRepoMock,
        )

        serviceAndMocks.revurderingService.iverksett(
            revurderingId,
            attestant,
        ) shouldBe iverksattRevurdering.right()

        inOrder(
            *serviceAndMocks.all(),
        ) {
            verify(revurderingRepoMock).hent(revurderingTilAttestering.id)
            verify(serviceAndMocks.vedtakRepo).lagre(argThat { it shouldBe vedtak.copy(id = it.id) })
            verify(revurderingRepoMock).lagre(any())
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `iverksetter revurdering som ikke fører til endring i ytelse svarer med feil hvis vi ikke kan hente person`() {
        val (_, revurderingTilAttestering) = tilAttesteringRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
            skalFøreTilBrevutsending = true,
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurderingTilAttestering
            },
            brevService = mock {
                on { lagDokument(any()) } doReturn KunneIkkeLageDokument.KunneIkkeHentePerson.left()
            },
        ).let {

            it.revurderingService.iverksett(
                revurderingId,
                attestant,
            ) shouldBe KunneIkkeIverksetteRevurdering.FantIkkePerson.left()

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(revurderingTilAttestering.id)
                verify(it.brevService).lagDokument(any())
                verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `iverksetter revurdering som ikke fører til endring i ytelse svarer med feil hvis vi ikke kan hente gjeldende utbetaling`() {
        val (_, revurderingTilAttestering) = tilAttesteringRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
            skalFøreTilBrevutsending = true,
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurderingTilAttestering
            },
            brevService = mock {
                on { lagDokument(any()) } doReturn KunneIkkeLageDokument.KunneIkkeFinneGjeldendeUtbetaling.left()
            },
        ).let {
            it.revurderingService.iverksett(
                revurderingId,
                attestant,
            ) shouldBe KunneIkkeIverksetteRevurdering.KunneIkkeFinneGjeldendeUtbetaling.left()

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(revurderingTilAttestering.id)
                verify(it.brevService).lagDokument(argThat { it shouldBe beOfType<Vedtak.IngenEndringIYtelse>() })
                verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `iverksetter revurdering som ikke fører til endring i ytelse svarer med feil hvis generering av brev feiler`() {
        val (_, revurderingTilAttestering) = tilAttesteringRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
            skalFøreTilBrevutsending = true,
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurderingTilAttestering
            },
            brevService = mock {
                on { lagDokument(any()) } doReturn KunneIkkeLageDokument.KunneIkkeGenererePDF.left()
            },
        ).let {
            it.revurderingService.iverksett(
                revurderingId,
                attestant,
            ) shouldBe KunneIkkeIverksetteRevurdering.KunneIkkeGenerereBrev.left()

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(revurderingTilAttestering.id)
                verify(it.brevService).lagDokument(argThat { it shouldBe beOfType<Vedtak.IngenEndringIYtelse>() })
                verifyNoMoreInteractions()
            }
        }
    }
}
