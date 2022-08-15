package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.Vurderingstatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.periodeNesteMånedOgTreMånederFram
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.stønadsperiodeNesteMånedOgTreMånederFram
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.oppgaveIdRevurdering
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknadinnhold
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
            lovligOpphold = LovligOppholdVilkår.IkkeVurdert,
            flyktning = FlyktningVilkår.IkkeVurdert,
            fastOpphold = FastOppholdINorgeVilkår.IkkeVurdert,
            personligOppmøte = PersonligOppmøteVilkår.IkkeVurdert,
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
        val (sak, iverksatt) = opprettetRevurdering()

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn iverksatt
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        ).also {
            it.revurderingService.oppdaterRevurdering(
                OppdaterRevurderingRequest(
                    revurderingId = revurderingId,
                    fraOgMed = 14.juli(2021),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "gyldig begrunnelse",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
                ),
            ) shouldBe KunneIkkeOppdatereRevurdering.UgyldigPeriode(Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden).left()
        }
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
        val (sak, iverksatt) = iverksattRevurdering()

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn iverksatt
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            avkortingsvarselRepo = mock {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
        ).also {
            val actual = it.revurderingService.oppdaterRevurdering(
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
            verify(it.avkortingsvarselRepo).hentUtestående(any())
            verify(it.revurderingRepo).hent(argThat { it shouldBe revurderingId })
            verify(it.sakService).hentSakForRevurdering(revurderingId)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `oppdater en revurdering`() {
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sakOgIverksattInnvilgetSøknadsbehandlingsvedtak.first
            },
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettetRevurdering
            },
            avkortingsvarselRepo = mock {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
        ).also {
            val oppdatertPeriode = Periode.create(
                periodeNesteMånedOgTreMånederFram.fraOgMed.plus(1, ChronoUnit.MONTHS),
                periodeNesteMånedOgTreMånederFram.tilOgMed,
            )
            val actual = it.revurderingService.oppdaterRevurdering(
                // Bruker andre verdier enn den opprinnelige revurderingen for å se at de faktisk forandrer seg
                OppdaterRevurderingRequest(
                    revurderingId = revurderingId,
                    fraOgMed = oppdatertPeriode.fraOgMed,
                    årsak = "ANDRE_KILDER",
                    begrunnelse = "bør bli oppdatert",
                    saksbehandler = NavIdentBruker.Saksbehandler("En ny saksbehandlinger"),
                    informasjonSomRevurderes = listOf(Revurderingsteg.Inntekt),
                ),
            ).getOrFail()

            actual.let { oppdatertRevurdering ->
                oppdatertRevurdering.periode shouldBe oppdatertPeriode
                oppdatertRevurdering.tilRevurdering shouldBe sakOgIverksattInnvilgetSøknadsbehandlingsvedtak.second.id
                oppdatertRevurdering.saksbehandler shouldBe saksbehandler
                oppdatertRevurdering.oppgaveId shouldBe oppgaveIdRevurdering
                oppdatertRevurdering.fritekstTilBrev shouldBe ""
                oppdatertRevurdering.revurderingsårsak shouldBe Revurderingsårsak(
                    årsak = Revurderingsårsak.Årsak.ANDRE_KILDER,
                    begrunnelse = Revurderingsårsak.Begrunnelse.create("bør bli oppdatert"),
                )
                oppdatertRevurdering.forhåndsvarsel shouldBe null
                oppdatertRevurdering.vilkårsvurderinger.erLik(sakOgIverksattInnvilgetSøknadsbehandlingsvedtak.second.behandling.vilkårsvurderinger)
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
                verify(it.revurderingRepo).hent(argThat { it shouldBe revurderingId })
                verify(it.sakService).hentSakForRevurdering(revurderingId)
                verify(it.avkortingsvarselRepo).hentUtestående(any())
                verify(it.revurderingRepo).defaultTransactionContext()
                verify(it.revurderingRepo).lagre(argThat { it shouldBe actual }, anyOrNull())
            }
            it.verifyNoMoreInteractions()
        }
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
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sakOgIverksattInnvilgetSøknadsbehandlingsvedtak.first
            },
            revurderingRepo = mock<RevurderingRepo> {
                on { hent(any()) } doReturn opprettetRevurdering.copy(
                    // simuler at det er gjort endringer før oppdatering
                    grunnlagsdata = Grunnlagsdata.create(),
                    vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
                )
            },
            avkortingsvarselRepo = mock {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
        ).also {
            val actual = it.revurderingService.oppdaterRevurdering(
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
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(revurderingId)
                verify(it.sakService).hentSakForRevurdering(revurderingId)
                verify(it.avkortingsvarselRepo).hentUtestående(any())
                verify(it.revurderingRepo).defaultTransactionContext()
                verify(it.revurderingRepo).lagre(eq(actual), anyOrNull())
            }
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `støtter ikke tilfeller hvor gjeldende vedtaksdata ikke er sammenhengende i tid`() {
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

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettetRevurdering().second
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sakMedNyStønadsperiode.first
            },
        ).also {
            it.revurderingService.oppdaterRevurdering(
                OppdaterRevurderingRequest(
                    revurderingId = UUID.randomUUID(),
                    fraOgMed = 1.mai(2021),
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Test",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
                ),
            ) shouldBe KunneIkkeOppdatereRevurdering.TidslinjeForVedtakErIkkeKontinuerlig.left()
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
                    fraOgMed = 1.oktober(2021),
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
        val (_, opphørUtenlandsopphold) = vedtakRevurdering(
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

        val (sak, iverksatt) = opprettetRevurdering()
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn iverksatt
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
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
