package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.Vurderingstatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.formueVilkår
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.periodeNesteMånedOgTreMånederFram
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.stønadsperiodeNesteMånedOgTreMånederFram
import no.nav.su.se.bakover.service.søknadsbehandling.testBeregning
import no.nav.su.se.bakover.service.vedtak.KunneIkkeKopiereGjeldendeVedtaksdata
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.innvilgetUførevilkårForventetInntekt12000
import no.nav.su.se.bakover.test.iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.plus
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.uføregrunnlagForventetInntekt12000
import no.nav.su.se.bakover.test.utlandsoppholdAvslag
import no.nav.su.se.bakover.test.utlandsoppholdInnvilget
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vilkårsvurderingerInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class OpprettRevurderingServiceTest {

    private val informasjonSomRevurderes = listOf(
        Revurderingsteg.Inntekt,
    )

    private val uføregrunnlag = uføregrunnlagForventetInntekt12000(periode = periodeNesteMånedOgTreMånederFram)
    private val vilkårsvurderingUføre =
        innvilgetUførevilkårForventetInntekt12000(periode = periodeNesteMånedOgTreMånederFram)

    private fun createInnvilgetBehandling() = søknadsbehandlingIverksattInnvilget(
        stønadsperiode = stønadsperiodeNesteMånedOgTreMånederFram,
        vilkårsvurderinger = vilkårsvurderingerInnvilget(
            periode = periodeNesteMånedOgTreMånederFram,
            uføre = vilkårsvurderingUføre,
        ),
    ).second

    private fun createSøknadsbehandlingVedtak() =
        VedtakSomKanRevurderes.fromSøknadsbehandling(createInnvilgetBehandling(), UUID30.randomUUID(), fixedClock)

    @Test
    fun `oppretter en revurdering`() {
        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periodeNesteMånedOgTreMånederFram,
            vedtakListe = nonEmptyListOf(createSøknadsbehandlingVedtak()),
            clock = fixedClock,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }

        val revurderingRepoMock = mock<RevurderingRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
        }

        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()

        val grunnlagServiceMock = mock<GrunnlagService>()

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            revurderingRepo = revurderingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            grunnlagService = grunnlagServiceMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
            avkortingsvarselRepo = mock() {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        ).orNull()!!

        val tilRevurdering =
            gjeldendeVedtaksdata.gjeldendeVedtakPåDato(periodeNesteMånedOgTreMånederFram.fraOgMed) as VedtakSomKanRevurderes.EndringIYtelse
        actual.let { opprettetRevurdering ->
            opprettetRevurdering.periode shouldBe periodeNesteMånedOgTreMånederFram
            opprettetRevurdering.tilRevurdering shouldBe tilRevurdering
            opprettetRevurdering.saksbehandler shouldBe saksbehandler
            opprettetRevurdering.oppgaveId shouldBe OppgaveId("oppgaveId")
            opprettetRevurdering.fritekstTilBrev shouldBe ""
            opprettetRevurdering.revurderingsårsak shouldBe Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.MELDING_FRA_BRUKER.toString(), begrunnelse = "Ny informasjon",
            )
            opprettetRevurdering.forhåndsvarsel shouldBe null
            opprettetRevurdering.vilkårsvurderinger.uføre.grunnlag.let {
                it shouldHaveSize 1
                it[0].ekvivalentMed(uføregrunnlag)
            }
            opprettetRevurdering.vilkårsvurderinger.uføre.ekvivalentMed(vilkårsvurderingUføre)
            opprettetRevurdering.informasjonSomRevurderes shouldBe InformasjonSomRevurderes.create(mapOf(Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert))

            inOrder(
                *mocks.all(),
            ) {
                verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(sakId, periodeNesteMånedOgTreMånederFram.fraOgMed)
                verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
                verify(mocks.avkortingsvarselRepo).hentUtestående(any())
                verify(oppgaveServiceMock).opprettOppgave(
                    argThat {
                        it shouldBe OppgaveConfig.Revurderingsbehandling(
                            saksnummer = saksnummer,
                            aktørId = aktørId,
                            tilordnetRessurs = null,
                            clock = fixedClock,
                        )
                    },
                )
                verify(revurderingRepoMock).defaultTransactionContext()
                verify(revurderingRepoMock).lagre(argThat { it.right() shouldBe actual.right() }, anyOrNull())
                verify(vilkårsvurderingServiceMock).lagre(actual.id, actual.vilkårsvurderinger)
                verify(grunnlagServiceMock).lagreFradragsgrunnlag(actual.id, actual.grunnlagsdata.fradragsgrunnlag)
                verify(grunnlagServiceMock).lagreBosituasjongrunnlag(actual.id, actual.grunnlagsdata.bosituasjon)
            }

            mocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kan opprette revurdering med årsak g-regulering i samme måned`() {
        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periodeNesteMånedOgTreMånederFram,
            vedtakListe = nonEmptyListOf(createSøknadsbehandlingVedtak()),
            clock = fixedClock,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }
        val revurderingRepoMock = mock<RevurderingRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
        }

        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()
        val grunnlagServiceMock = mock<GrunnlagService>()
        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            revurderingRepo = revurderingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            grunnlagService = grunnlagServiceMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
            avkortingsvarselRepo = mock() {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                årsak = "REGULER_GRUNNBELØP",
                begrunnelse = "g-regulering",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        ).getOrHandle {
            throw RuntimeException("$it")
        }

        val tilRevurdering =
            gjeldendeVedtaksdata.gjeldendeVedtakPåDato(periodeNesteMånedOgTreMånederFram.fraOgMed) as VedtakSomKanRevurderes.EndringIYtelse
        val periode =
            Periode.create(periodeNesteMånedOgTreMånederFram.fraOgMed, periodeNesteMånedOgTreMånederFram.tilOgMed)
        actual.let { opprettetRevurdering ->
            opprettetRevurdering.periode shouldBe periode
            opprettetRevurdering.tilRevurdering shouldBe tilRevurdering
            opprettetRevurdering.saksbehandler shouldBe saksbehandler
            opprettetRevurdering.oppgaveId shouldBe OppgaveId("oppgaveId")
            opprettetRevurdering.fritekstTilBrev shouldBe ""
            opprettetRevurdering.revurderingsårsak shouldBe Revurderingsårsak(
                Revurderingsårsak.Årsak.REGULER_GRUNNBELØP,
                Revurderingsårsak.Begrunnelse.create("g-regulering"),
            )
            opprettetRevurdering.forhåndsvarsel shouldBe Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles
            opprettetRevurdering.vilkårsvurderinger.uføre.grunnlag.let {
                it shouldHaveSize 1
                it[0].ekvivalentMed(uføregrunnlag.copy(periode = periode))
            }
            opprettetRevurdering.vilkårsvurderinger.uføre.ekvivalentMed(
                vilkårsvurderingUføre.copy(
                    vurderingsperioder = vilkårsvurderingUføre.vurderingsperioder.map {
                        it.copy(CopyArgs.Tidslinje.NyPeriode(periode))
                    },
                ),
            )
            opprettetRevurdering.informasjonSomRevurderes shouldBe InformasjonSomRevurderes.create(mapOf(Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert))
        }
        inOrder(
            *mocks.all(),
        ) {
            verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(sakId, periode.fraOgMed)
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(mocks.avkortingsvarselRepo).hentUtestående(any())
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Revurderingsbehandling(
                        saksnummer = saksnummer,
                        aktørId = aktørId,
                        tilordnetRessurs = null,
                        clock = fixedClock,
                    )
                },
            )
            verify(revurderingRepoMock).defaultTransactionContext()
            verify(revurderingRepoMock).lagre(argThat { it.right() shouldBe actual.right() }, anyOrNull())
            verify(vilkårsvurderingServiceMock).lagre(any(), any())
            verify(grunnlagServiceMock).lagreFradragsgrunnlag(any(), any())
            verify(grunnlagServiceMock).lagreBosituasjongrunnlag(actual.id, actual.grunnlagsdata.bosituasjon)
        }

        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan opprette revurdering med årsak g-regulering 1 kalendermåned tilbake i tid`() {
        val periode = Periode.create(
            periodeNesteMånedOgTreMånederFram.tilOgMed.minusMonths(1).startOfMonth(),
            periodeNesteMånedOgTreMånederFram.tilOgMed,
        )

        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periode,
            vedtakListe = nonEmptyListOf(createSøknadsbehandlingVedtak()),
            clock = fixedClock,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }
        val revurderingRepoMock = mock<RevurderingRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
        }
        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()
        val grunnlagServiceMock = mock<GrunnlagService>()
        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            revurderingRepo = revurderingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            grunnlagService = grunnlagServiceMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
            avkortingsvarselRepo = mock() {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periode.tilOgMed.minusMonths(1).startOfMonth(),
                årsak = "REGULER_GRUNNBELØP",
                begrunnelse = "g-regulering",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        ).getOrHandle {
            throw RuntimeException("$it")
        }
        val tilRevurdering =
            gjeldendeVedtaksdata.gjeldendeVedtakPåDato(periode.fraOgMed) as VedtakSomKanRevurderes.EndringIYtelse
        actual.let { opprettetRevurdering ->
            opprettetRevurdering.periode shouldBe periode
            opprettetRevurdering.tilRevurdering shouldBe tilRevurdering
            opprettetRevurdering.saksbehandler shouldBe saksbehandler
            opprettetRevurdering.oppgaveId shouldBe OppgaveId("oppgaveId")
            opprettetRevurdering.fritekstTilBrev shouldBe ""
            opprettetRevurdering.revurderingsårsak shouldBe Revurderingsårsak(
                Revurderingsårsak.Årsak.REGULER_GRUNNBELØP,
                Revurderingsårsak.Begrunnelse.create("g-regulering"),
            )
            opprettetRevurdering.forhåndsvarsel shouldBe Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles
            opprettetRevurdering.vilkårsvurderinger.uføre.grunnlag.let {
                it shouldHaveSize 1
                it[0].ekvivalentMed(uføregrunnlag.copy(periode = periode))
            }
            opprettetRevurdering.vilkårsvurderinger.uføre.ekvivalentMed(
                vilkårsvurderingUføre.copy(
                    vurderingsperioder = vilkårsvurderingUføre.vurderingsperioder.map {
                        it.copy(CopyArgs.Tidslinje.NyPeriode(periode))
                    },
                ),
            )
            opprettetRevurdering.informasjonSomRevurderes shouldBe InformasjonSomRevurderes.create(mapOf(Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert))
        }

        inOrder(
            *mocks.all(),
        ) {
            verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(sakId, periode.fraOgMed)
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(mocks.avkortingsvarselRepo).hentUtestående(any())
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Revurderingsbehandling(
                        saksnummer = saksnummer,
                        aktørId = aktørId,
                        tilordnetRessurs = null,
                        clock = fixedClock,
                    )
                },
            )
            verify(revurderingRepoMock).defaultTransactionContext()
            verify(revurderingRepoMock).lagre(argThat { it.right() shouldBe actual.right() }, anyOrNull())
            verify(vilkårsvurderingServiceMock).lagre(any(), any())
            verify(grunnlagServiceMock).lagreFradragsgrunnlag(any(), any())
            verify(grunnlagServiceMock).lagreBosituasjongrunnlag(actual.id, actual.grunnlagsdata.bosituasjon)
        }

        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke sak`() {
        val vedtakServiceMock = mock<VedtakService> {
            on {
                kopierGjeldendeVedtaksdata(
                    any(),
                    any(),
                )
            } doReturn KunneIkkeKopiereGjeldendeVedtaksdata.FantIkkeSak.left()
        }
        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Inntekt),
            ),
        )
        actual shouldBe KunneIkkeOppretteRevurdering.FantIkkeSak.left()
        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(sakId, periodeNesteMånedOgTreMånederFram.fraOgMed)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan ikke opprette revurdering hvis ingen vedtak eksisterer for angitt fra og med dato`() {
        val vedtakServiceMock = mock<VedtakService> {
            on {
                kopierGjeldendeVedtaksdata(
                    any(),
                    any(),
                )
            } doReturn KunneIkkeKopiereGjeldendeVedtaksdata.FantIngenVedtak.left()
        }
        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
        )

        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.FantIngenVedtakSomKanRevurderes.left()
        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(sakId, periodeNesteMånedOgTreMånederFram.fraOgMed)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `for en ny revurdering vil det tas utgangspunkt i nyeste vedtak hvor fraOgMed er inni perioden`() {
        val vedtaksperiode = Periode.create(1.januar(2021), 31.desember(2021))
        val behandlingMock = mock<IverksattRevurdering.Innvilget> {
            on { fnr } doReturn Fnr.generer()
            on { saksnummer } doReturn Saksnummer(2021)
            on { grunnlagsdata } doReturn Grunnlagsdata.create(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = vedtaksperiode,
                        begrunnelse = null,
                    ),
                ),
            )
            on { vilkårsvurderinger } doReturn Vilkårsvurderinger.Revurdering(
                vilkårsvurderingUføre,
                formueVilkår(periodeNesteMånedOgTreMånederFram),
                utlandsoppholdInnvilget(periode = periodeNesteMånedOgTreMånederFram),
            )
        }
        val vedtakForFørsteJanuarLagetNå = mock<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering> {
            on { id } doReturn UUID.randomUUID()
            on { opprettet } doReturn fixedTidspunkt
            on { periode } doReturn vedtaksperiode
            on { behandling } doReturn behandlingMock
            on { beregning } doReturn testBeregning
        }
        val vedtakForFørsteMarsLagetNå = mock<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering> {
            on { id } doReturn UUID.randomUUID()
            on { opprettet } doReturn fixedTidspunkt.plus(1, ChronoUnit.SECONDS)
            on { periode } doReturn Periode.create(1.mars(2021), 31.desember(2021))
            on { behandling } doReturn behandlingMock
            on { beregning } doReturn testBeregning
        }
        val vedtakForFørsteJanuarLagetForLengeSiden = mock<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering> {
            on { id } doReturn UUID.randomUUID()
            on { opprettet } doReturn fixedTidspunkt.instant.minus(2, ChronoUnit.HALF_DAYS).toTidspunkt()
            on { periode } doReturn vedtaksperiode
            on { behandling } doReturn behandlingMock
            on { beregning } doReturn testBeregning
        }

        val fraOgMedDatoFebruar = fixedLocalDate.plus(1, ChronoUnit.MONTHS)
        val fraOgMedDatoApril = fixedLocalDate.plus(3, ChronoUnit.MONTHS)

        val vedtakListe = nonEmptyListOf(
            vedtakForFørsteJanuarLagetNå,
            vedtakForFørsteMarsLagetNå,
            vedtakForFørsteJanuarLagetForLengeSiden,
        )

        val gjeldendeVedtaksdataFebruar = GjeldendeVedtaksdata(
            periode = Periode.create(fraOgMedDatoFebruar, vedtakListe.maxOf { it.periode.tilOgMed }),
            vedtakListe = vedtakListe,
            clock = fixedClock,
        )

        val gjeldendeVedtaksdataApril = GjeldendeVedtaksdata(
            periode = Periode.create(fraOgMedDatoApril, vedtakListe.maxOf { it.periode.tilOgMed }),
            vedtakListe = vedtakListe,
            clock = fixedClock,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(sakId, fraOgMedDatoFebruar) } doReturn gjeldendeVedtaksdataFebruar.right()
            on { kopierGjeldendeVedtaksdata(sakId, fraOgMedDatoApril) } doReturn gjeldendeVedtaksdataApril.right()
        }

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            avkortingsvarselRepo = mock {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
            revurderingRepo = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            vilkårsvurderingService = mock {
                doNothing().whenever(it).lagre(any(), any())
            },
            grunnlagService = mock {
                doNothing().whenever(it).lagreFradragsgrunnlag(any(), any())
            }
        )
        val revurderingForFebruar = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = fraOgMedDatoFebruar,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Inntekt, Revurderingsteg.Bosituasjon),
            ),
        )

        revurderingForFebruar.orNull()!!.tilRevurdering shouldBe vedtakForFørsteJanuarLagetNå

        val revurderingForApril = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = fraOgMedDatoApril,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes,
            ),
        )

        revurderingForApril.orNull()!!.tilRevurdering shouldBe vedtakForFørsteMarsLagetNå
    }

    @Test
    fun `kan revurdere en periode med eksisterende revurdering`() {
        val (sak, iverksattRevurdering) = iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak()
        val søknadsbehandlingVedtak = sak.vedtakListe.first() as VedtakSomKanRevurderes
        val revurderingVedtak = VedtakSomKanRevurderes.from(
            iverksattRevurdering,
            UUID30.randomUUID(),
            fixedClock.plus(1, ChronoUnit.SECONDS),
        )

        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periodeNesteMånedOgTreMånederFram,
            vedtakListe = nonEmptyListOf(
                søknadsbehandlingVedtak,
                revurderingVedtak,
            ),
            clock = fixedClock,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }
        val revurderingRepoMock = mock<RevurderingRepo>()

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }
        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()
        val grunnlagServiceMock = mock<GrunnlagService>()
        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            revurderingRepo = revurderingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            grunnlagService = grunnlagServiceMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
            avkortingsvarselRepo = mock() {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )

        actual.orNull()!!.also {
            it.saksnummer shouldBe saksnummer
            it.tilRevurdering.id shouldBe revurderingVedtak.id
        }

        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(sakId, periodeNesteMånedOgTreMånederFram.fraOgMed)
        verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
        verify(revurderingRepoMock).defaultTransactionContext()
        verify(revurderingRepoMock).lagre(argThat { it.right() shouldBe actual }, anyOrNull())
        verify(oppgaveServiceMock).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Revurderingsbehandling(
                    saksnummer = saksnummer,
                    aktørId = aktørId,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                )
            },
        )
        verify(mocks.avkortingsvarselRepo).hentUtestående(any())
        verify(vilkårsvurderingServiceMock).lagre(any(), any())
        verify(grunnlagServiceMock).lagreFradragsgrunnlag(any(), any())
        verify(grunnlagServiceMock).lagreBosituasjongrunnlag(any(), any())
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `ugyldig periode`() {
        val vedtakServiceMock = mock<VedtakService> {
            on {
                kopierGjeldendeVedtaksdata(
                    any(),
                    any(),
                )
            } doReturn KunneIkkeKopiereGjeldendeVedtaksdata.UgyldigPeriode(Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden)
                .left()
        }

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                // tester at fraOgMed må starte på 1.
                fraOgMed = periodeNesteMånedOgTreMånederFram.tilOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Inntekt),
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.UgyldigPeriode(Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden)
            .left()
        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(any(), any())
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke aktør id`() {
        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periodeNesteMånedOgTreMånederFram,
            vedtakListe = nonEmptyListOf(createSøknadsbehandlingVedtak()),
            clock = fixedClock,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            personService = personServiceMock,
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Inntekt),
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.FantIkkeAktørId.left()
        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(sakId, periodeNesteMånedOgTreMånederFram.fraOgMed)
        verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kunne ikke opprette oppgave`() {
        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periodeNesteMånedOgTreMånederFram,
            vedtakListe = nonEmptyListOf(createSøknadsbehandlingVedtak()),
            clock = fixedClock,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn KunneIkkeOppretteOppgave.left()
        }

        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
            avkortingsvarselRepo = mock {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )
        actual shouldBe KunneIkkeOppretteRevurdering.KunneIkkeOppretteOppgave.left()
        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(sakId, periodeNesteMånedOgTreMånederFram.fraOgMed)
        verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
        verify(mocks.avkortingsvarselRepo).hentUtestående(sakId)
        verify(oppgaveServiceMock).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Revurderingsbehandling(
                    saksnummer = saksnummer,
                    aktørId = aktørId,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                )
            },
        )
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `må velge noe som skal revurderes`() {
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val mocks = RevurderingServiceMocks(
            personService = personServiceMock,
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = emptyList(),
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.MåVelgeInformasjonSomSkalRevurderes.left()
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `støtter ikke tilfeller hvor gjeldende vedtaksdata ikke er sammenhengende i tid`() {
        val førsteVedtak = createSøknadsbehandlingVedtak()
        val periodePlussEtÅr = periodeNesteMånedOgTreMånederFram.copy(
            periodeNesteMånedOgTreMånederFram.fraOgMed.plusYears(1),
            periodeNesteMånedOgTreMånederFram.tilOgMed.plusYears(1),
        )
        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            periode = periodePlussEtÅr,
            uføregrad = Uføregrad.parse(25),
            forventetInntekt = 12000,
            opprettet = fixedTidspunkt,
        )
        val uførevilkår = Vilkår.Uførhet.Vurdert.create(
            vurderingsperioder = nonEmptyListOf(
                Vurderingsperiode.Uføre.create(
                    resultat = Resultat.Innvilget,
                    grunnlag = uføregrunnlag,
                    periode = periodePlussEtÅr,
                    begrunnelse = "ok",
                    opprettet = fixedTidspunkt,
                ),
            ),
        )
        val bosituasjon = listOf(
            Grunnlag.Bosituasjon.Fullstendig.Enslig(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periodePlussEtÅr,
                begrunnelse = null,
            ),
        )
        val andreVedtak = createSøknadsbehandlingVedtak().copy(
            periode = periodePlussEtÅr,
            behandling = createInnvilgetBehandling().copy(
                grunnlagsdata = Grunnlagsdata.create(
                    bosituasjon = bosituasjon,
                ),
                vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling(
                    uførevilkår,
                ),
                stønadsperiode = Stønadsperiode.create(periodePlussEtÅr),
            ),
        )

        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periodeNesteMånedOgTreMånederFram.copy(
                førsteVedtak.periode.fraOgMed,
                andreVedtak.periode.tilOgMed,
            ),
            vedtakListe = nonEmptyListOf(
                førsteVedtak,
                andreVedtak,
            ),
            clock = fixedClock,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )
        actual shouldBe KunneIkkeOppretteRevurdering.TidslinjeForVedtakErIkkeKontinuerlig.left()
        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(sakId, periodeNesteMånedOgTreMånederFram.fraOgMed)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `revurdering med flere bosituasjonsperioder må vurderes`() {
        val revurderingsperiode = Periode.create(
            fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed.plus(1, ChronoUnit.MONTHS),
            tilOgMed = periodeNesteMånedOgTreMånederFram.tilOgMed,
        )
        val revurdering = mock<IverksattRevurdering.Innvilget> {
            on { attestering } doReturn Attestering.Iverksatt(
                NavIdentBruker.Attestant("attestantSomIverksatte"),
                fixedTidspunkt,
            )
            on { periode } doReturn revurderingsperiode
            on { beregning } doReturn mock()
            on { simulering } doReturn mock()
            on { saksbehandler } doReturn mock()
            on { grunnlagsdata } doReturn Grunnlagsdata.create(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = revurderingsperiode,
                        begrunnelse = null,
                    ),
                    Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = revurderingsperiode,
                        begrunnelse = null,
                    ),
                ),
            )
            on { vilkårsvurderinger } doReturn Vilkårsvurderinger.Revurdering(
                uføre = vilkårsvurderingUføre,
                formue = Vilkår.Formue.IkkeVurdert,
                utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
            )
        }
        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periodeNesteMånedOgTreMånederFram,
            vedtakListe = nonEmptyListOf(
                createSøknadsbehandlingVedtak(),
                VedtakSomKanRevurderes.from(revurdering, UUID30.randomUUID(), fixedClock),
            ),
            clock = fixedClock,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Inntekt),
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.BosituasjonMedFlerePerioderMåRevurderes.left()
        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(sakId, periodeNesteMånedOgTreMånederFram.fraOgMed)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `får feilmelding dersom saken har utestående avkorting, men revurderingsperioden inneholder ikke perioden for avkortingen`() {
        val clock = TikkendeKlokke(fixedClock)
        val (sak, opphørUtenlandsopphold) = vedtakRevurdering(
            clock = clock,
            revurderingsperiode = Periode.create(1.juni(2021), 31.desember(2021)),
            vilkårOverrides = listOf(
                utlandsoppholdAvslag(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = Periode.create(1.juni(2021), 31.desember(2021)),
                ),
            ),
        )
        val nyRevurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021))
        val uteståendeAvkorting =
            (opphørUtenlandsopphold as VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering).behandling.avkorting.let {
                (it as AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarsel).avkortingsvarsel
            }

        RevurderingServiceMocks(
            vedtakService = mock {
                on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = nyRevurderingsperiode.fraOgMed,
                    clock = clock,
                ).getOrFail().right()
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            avkortingsvarselRepo = mock {
                on { hentUtestående(any()) } doReturn uteståendeAvkorting
            },
        ).let {
            it.revurderingService.opprettRevurdering(
                OpprettRevurderingRequest(
                    sakId = sakId,
                    fraOgMed = nyRevurderingsperiode.fraOgMed,
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Utenlandsopphold),
                ),
            ) shouldBe KunneIkkeOppretteRevurdering.UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(juni(2021))
                .left()
        }
    }
}
