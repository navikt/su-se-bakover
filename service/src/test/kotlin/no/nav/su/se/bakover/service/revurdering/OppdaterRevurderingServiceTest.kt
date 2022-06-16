package no.nav.su.se.bakover.service.revurdering

import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.fail
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingFerdigbehandlet
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.Vurderingstatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.periodeNesteMånedOgTreMånederFram
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.stønadsperiodeNesteMånedOgTreMånederFram
import no.nav.su.se.bakover.service.vedtak.KunneIkkeKopiereGjeldendeVedtaksdata
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.oppgaveIdRevurdering
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkår.formuevilkårIkkeVurdert
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkårForventetInntekt12000
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

    private val sakOgIverksattInnvilgetSøknadsbehandlingsvedtak = vedtakSøknadsbehandlingIverksattInnvilget(
        stønadsperiode = stønadsperiodeNesteMånedOgTreMånederFram,
    )

    // Merk at søknadsbehandlingens uførevilkår ikke er likt som OpprettetRevurdering før vi kaller oppdater-funksjonen, men vi forventer at de er like etter oppdateringa.
    private val vilkårsvurderingUføre =
        innvilgetUførevilkårForventetInntekt12000(periode = periodeNesteMånedOgTreMånederFram)

    // TODO jah: Vi burde ha en domeneklasse/factory som oppretter en revurdering fra et Vedtak, så slipper vi å gjøre disse antagelsene i testene
    private val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
        stønadsperiode = stønadsperiodeNesteMånedOgTreMånederFram,
        revurderingsperiode = periodeNesteMånedOgTreMånederFram,
        sakOgVedtakSomKanRevurderes = sakOgIverksattInnvilgetSøknadsbehandlingsvedtak,
    ).second.copy(
        grunnlagsdata = Grunnlagsdata.create(
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = periodeNesteMånedOgTreMånederFram,
                ),
            ),
        ),
        vilkårsvurderinger = Vilkårsvurderinger.Revurdering.Uføre(
            uføre = vilkårsvurderingUføre,
            formue = formuevilkårIkkeVurdert(),
            utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
            opplysningsplikt = OpplysningspliktVilkår.IkkeVurdert,
        ),
        informasjonSomRevurderes = InformasjonSomRevurderes.create(mapOf(Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert)),
    )

    @Test
    fun `ugyldig begrunnelse`() {
        val mocks = RevurderingServiceMocks()
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = fixedLocalDate,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.UgyldigBegrunnelse.left()
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `ugyldig årsak`() {
        val mocks = RevurderingServiceMocks()
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = fixedLocalDate,
                årsak = "UGYLDIG_ÅRSAK",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.UgyldigÅrsak.left()
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
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            revurderingRepo = revurderingRepoMock,
        )
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = fixedLocalDate,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
            ),
        )

        actual shouldBe KunneIkkeOppdatereRevurdering.UgyldigPeriode(Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden)
            .left()
        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(any(), any())
        verify(revurderingRepoMock).hent(any())
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Fant ikke revurdering`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn null
        }
        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = fixedLocalDate,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.FantIkkeRevurdering.left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Kan ikke oppdatere sendt forhåndsvarslet revurdering`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering.copy(
                forhåndsvarsel = Forhåndsvarsel.UnderBehandling.Sendt,
            )
        }
        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed.plus(1, ChronoUnit.DAYS),
                årsak = "REGULER_GRUNNBELØP",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.KanIkkeOppdatereRevurderingSomErForhåndsvarslet.left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Kan ikke oppdatere besluttet forhåndsvarslet revurdering`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering.copy(
                forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag("begrunnelse"),
            )
        }
        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed.plus(1, ChronoUnit.DAYS),
                årsak = "REGULER_GRUNNBELØP",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.KanIkkeOppdatereRevurderingSomErForhåndsvarslet.left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Oppdatering av iverksatt revurdering gir ugyldig tilstand`() {
        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periodeNesteMånedOgTreMånederFram,
            vedtakListe = nonEmptyListOf(sakOgIverksattInnvilgetSøknadsbehandlingsvedtak.second),
            clock = fixedClock,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering.let {
                IverksattRevurdering.Innvilget(
                    id = it.id,
                    periode = it.periode,
                    opprettet = it.opprettet,
                    tilRevurdering = it.tilRevurdering,
                    saksbehandler = it.saksbehandler,
                    oppgaveId = it.oppgaveId,
                    fritekstTilBrev = it.fritekstTilBrev,
                    revurderingsårsak = it.revurderingsårsak,
                    beregning = mock(),
                    attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(
                        Attestering.Iverksatt(
                            attestant = NavIdentBruker.Attestant("navIdent"),
                            opprettet = fixedTidspunkt,
                        ),
                    ),
                    simulering = mock(),
                    forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
                    grunnlagsdata = Grunnlagsdata.create(
                        bosituasjon = listOf(
                            Grunnlag.Bosituasjon.Fullstendig.Enslig(
                                id = UUID.randomUUID(),
                                opprettet = fixedTidspunkt,
                                periode = it.periode,
                            ),
                        ),
                    ),
                    vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
                    informasjonSomRevurderes = it.informasjonSomRevurderes,
                    avkorting = AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående,
                    tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingFerdigbehandlet,
                )
            }
        }

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            revurderingRepo = revurderingRepoMock,
            avkortingsvarselRepo = mock {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
        )
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
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
        verify(mocks.avkortingsvarselRepo).hentUtestående(any())
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(
            opprettetRevurdering.sakId,
            opprettetRevurdering.periode.fraOgMed,
        )
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `oppdater en revurdering`() {
        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periodeNesteMånedOgTreMånederFram,
            vedtakListe = nonEmptyListOf(sakOgIverksattInnvilgetSøknadsbehandlingsvedtak.second),
            clock = fixedClock,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            revurderingRepo = revurderingRepoMock,
            avkortingsvarselRepo = mock {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
        )
        val oppdatertPeriode = Periode.create(
            periodeNesteMånedOgTreMånederFram.fraOgMed.plus(1, ChronoUnit.MONTHS),
            periodeNesteMånedOgTreMånederFram.tilOgMed,
        )
        val actual = mocks.revurderingService.oppdaterRevurdering(
            // Bruker andre verdier enn den opprinnelige revurderingen for å se at de faktisk forandrer seg
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = oppdatertPeriode.fraOgMed,
                årsak = "ANDRE_KILDER",
                begrunnelse = "bør bli oppdatert",
                saksbehandler = NavIdentBruker.Saksbehandler("En ny saksbehandlinger"),
                informasjonSomRevurderes = listOf(Revurderingsteg.Inntekt),
            ),
        ).getOrHandle {
            fail("$it")
        }

        actual.let { oppdatertRevurdering ->
            oppdatertRevurdering.periode shouldBe periodeNesteMånedOgTreMånederFram
            oppdatertRevurdering.tilRevurdering shouldBe sakOgIverksattInnvilgetSøknadsbehandlingsvedtak.second
            oppdatertRevurdering.saksbehandler shouldBe saksbehandler
            oppdatertRevurdering.oppgaveId shouldBe oppgaveIdRevurdering
            oppdatertRevurdering.fritekstTilBrev shouldBe ""
            oppdatertRevurdering.revurderingsårsak shouldBe Revurderingsårsak(
                årsak = Revurderingsårsak.Årsak.ANDRE_KILDER,
                begrunnelse = Revurderingsårsak.Begrunnelse.create("bør bli oppdatert"),
            )
            oppdatertRevurdering.forhåndsvarsel shouldBe null
            oppdatertRevurdering.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag.let {
                it shouldHaveSize 1
                it[0].ekvivalentMed(
                    sakOgIverksattInnvilgetSøknadsbehandlingsvedtak.second.behandling.vilkårsvurderinger.uføreVilkår()
                        .getOrFail().grunnlag.first(),
                )
            }
            oppdatertRevurdering.vilkårsvurderinger.uføreVilkår().getOrFail()
                .ekvivalentMed(
                    sakOgIverksattInnvilgetSøknadsbehandlingsvedtak.second.behandling.vilkårsvurderinger.uføreVilkår()
                        .getOrFail() as Vilkår.Uførhet.Vurdert,
                )
            oppdatertRevurdering.informasjonSomRevurderes shouldBe InformasjonSomRevurderes.create(mapOf(Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert))
        }

        inOrder(
            *mocks.all(),
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(
                sakId = argThat { it shouldBe sakId },
                fraOgMed = argThat {
                    it shouldBe oppdatertPeriode.fraOgMed
                },
            )
            verify(mocks.avkortingsvarselRepo).hentUtestående(any())
            verify(revurderingRepoMock).defaultTransactionContext()
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual }, anyOrNull())
        }
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `må velge minst ting som skal revurderes`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }

        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = emptyList(),
            ),
        ) shouldBe KunneIkkeOppdatereRevurdering.MåVelgeInformasjonSomSkalRevurderes.left()
    }

    @Test
    fun `grunnlag resettes dersom man oppdaterer revurderingen`() {
        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periodeNesteMånedOgTreMånederFram,
            vedtakListe = nonEmptyListOf(sakOgIverksattInnvilgetSøknadsbehandlingsvedtak.second),
            clock = fixedClock,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering.copy(
                // simuler at det er gjort endringer før oppdatering
                grunnlagsdata = Grunnlagsdata.create(),
                vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            )
        }

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            revurderingRepo = revurderingRepoMock,
            avkortingsvarselRepo = mock {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
        )

        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                årsak = "REGULER_GRUNNBELØP",
                begrunnelse = "g-regulering",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
            ),
        ).getOrHandle { fail("$it") }

        actual.periode.fraOgMed shouldBe periodeNesteMånedOgTreMånederFram.fraOgMed
        actual.revurderingsårsak.årsak shouldBe Revurderingsårsak.Årsak.REGULER_GRUNNBELØP
        actual.revurderingsårsak.begrunnelse.toString() shouldBe "g-regulering"

        inOrder(
            *mocks.all(),
        ) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(
                opprettetRevurdering.sakId,
                opprettetRevurdering.periode.fraOgMed,
            )
            verify(mocks.avkortingsvarselRepo).hentUtestående(any())
            verify(revurderingRepoMock).defaultTransactionContext()
            verify(revurderingRepoMock).lagre(eq(actual), anyOrNull())
        }
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `støtter ikke tilfeller hvor gjeldende vedtaksdata ikke er sammenhengende i tid`() {
        val (førsteSak, førsteVedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(år(2021)),
        )

        val (andreSak, andreVedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(februar(2022)),
        )

        // TODO jah: Burde kunne gjøre mer av dette via sak (her blir ikke sakId og fnr like)
        val sak = førsteSak.copy(
            utbetalinger = førsteSak.utbetalinger + andreSak.utbetalinger,
            søknadsbehandlinger = førsteSak.søknadsbehandlinger + andreSak.søknadsbehandlinger,
            vedtakListe = førsteSak.vedtakListe + andreSak.vedtakListe,
            søknader = førsteSak.søknader + andreSak.søknader,
        )

        val gjeldendeVedtaksdata = sak.hentGjeldendeVedtaksdata(
            periode = Periode.create(
                fraOgMed = førsteVedtak.periode.fraOgMed,
                tilOgMed = andreVedtak.periode.tilOgMed,
            ),
            clock = fixedClock,
        ).orNull()!!

        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            revurderingRepo = revurderingRepoMock,
        )
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = opprettetRevurdering.id,
                fraOgMed = opprettetRevurdering.periode.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Test",
                saksbehandler = opprettetRevurdering.saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.TidslinjeForVedtakErIkkeKontinuerlig.left()
        verify(revurderingRepoMock).hent(opprettetRevurdering.id)
        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(sakId, periodeNesteMånedOgTreMånederFram.fraOgMed)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `får lov til å oppdatere revurdering dersom periode overlapper opphørsvedtak for utenlandsopphold som ikke førte til avkorting`() {
        val tikkendeKlokke = TikkendeKlokke()

        val sakOgSøknadsvedtak = vedtakSøknadsbehandlingIverksattInnvilget(
            saksnummer = saksnummer,
            stønadsperiode = stønadsperiode2021,
            clock = tikkendeKlokke,
        )

        val revurderingsperiode = Periode.create(1.oktober(2021), 31.desember(2021))
        val sakOgSøknadsvedtakOgRevurderingsvedtak = vedtakRevurdering(
            clock = tikkendeKlokke,
            revurderingsperiode = revurderingsperiode,
            sakOgVedtakSomKanRevurderes = sakOgSøknadsvedtak,
            vilkårOverrides = listOf(
                utenlandsoppholdAvslag(
                    periode = revurderingsperiode,
                ),
            ),
        )
        val (sak3, opprettetRevurdering) = opprettetRevurdering(
            sakOgVedtakSomKanRevurderes = sakOgSøknadsvedtakOgRevurderingsvedtak,
        )

        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettetRevurdering
            },
            vedtakService = mock {
                on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn GjeldendeVedtaksdata(
                    periode = revurderingsperiode,
                    vedtakListe = NonEmptyList.fromListUnsafe(sak3.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()),
                    clock = fixedClock,
                ).right()
            },
            avkortingsvarselRepo = mock {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
        )

        serviceAndMocks.revurderingService.oppdaterRevurdering(
            oppdaterRevurderingRequest = OppdaterRevurderingRequest(
                revurderingId = opprettetRevurdering.id,
                fraOgMed = 1.oktober(2021),
                årsak = Revurderingsårsak.Årsak.ANDRE_KILDER.toString(),
                begrunnelse = "lol",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Utenlandsopphold),
            ),
        ).getOrFail() shouldBe beOfType<OpprettetRevurdering>()
    }

    @Test
    fun `får feilmelding dersom saken har utestående avkorting, men revurderingsperioden inneholder ikke perioden for avkortingen`() {
        val clock = TikkendeKlokke(fixedClock)
        val (sak, opphørUtenlandsopphold) = vedtakRevurdering(
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
        val uteståendeAvkorting =
            (opphørUtenlandsopphold as VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering).behandling.avkorting.let {
                (it as AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarsel).avkortingsvarsel
            }

        val opprettetRevurdering = opprettetRevurdering().second

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettetRevurdering
            },
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
            it.revurderingService.oppdaterRevurdering(
                OppdaterRevurderingRequest(
                    revurderingId = sakId,
                    fraOgMed = nyRevurderingsperiode.fraOgMed,
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Utenlandsopphold),
                ),
            ) shouldBe KunneIkkeOppdatereRevurdering.UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(juni(2021))
                .left()
        }
    }
}
