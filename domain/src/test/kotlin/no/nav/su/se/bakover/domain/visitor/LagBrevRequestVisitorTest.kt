package no.nav.su.se.bakover.domain.visitor

import arrow.core.Either
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.withAvslåttFlyktning
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.brev.LagBrevRequest.AvslagBrevRequest
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.harEPS
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.firstOrThrowIfMultipleOrEmpty
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingFerdigbehandlet
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingUnderBehandling
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUføre
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.oppgaveIdRevurdering
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulerNyUtbetaling
import no.nav.su.se.bakover.test.simulertUtbetalingOpphør
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.vilkår.formuevilkårAvslåttPgrBrukersformue
import no.nav.su.se.bakover.test.vilkår.innvilgetFormueVilkår
import no.nav.su.se.bakover.test.vilkår.lovligOppholdVilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.tilstrekkeligDokumentert
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.vilkårsvurderingerSøknadsbehandlingInnvilget
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import vilkår.personligOppmøtevilkårInnvilget
import java.time.Clock
import java.util.UUID

internal class LagBrevRequestVisitorTest {

    @Test
    fun `responderer med feil dersom vi ikke får til å hente person`() {
        vilkårsvurdertInnvilget.leggTilVilkårFraBehandlingsinformasjon(
            Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAvslåttFlyktning(),
            clock = fixedClock,
        ).getOrFail().let { søknadsbehandling ->
            LagBrevRequestVisitor(
                hentPerson = { LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHentePerson.left() },
                hentNavn = { hentNavn(it) },
                hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).apply { søknadsbehandling.accept(this) }.let {
                it.brevRequest shouldBe LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHentePerson.left()
            }
        }
    }

    @Test
    fun `responderer med feil dersom vi ikke får til å hente navn for saksbehandler eller attestant`() {
        vilkårsvurdertInnvilget.beregn(
            begrunnelse = null,
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).getOrFail()
            .simuler(
                saksbehandler = saksbehandler,
            ) {
                simulerNyUtbetaling(
                    sak = sak,
                    request = it,
                    clock = fixedClock,
                )
            }.getOrFail()
            .tilAttestering(saksbehandler, "Fritekst!")
            .tilIverksatt(Attestering.Iverksatt(attestant, fixedTidspunkt))
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left() },
                    hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                    clock = fixedClock,
                    satsFactory = satsFactoryTestPåDato(),
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left()
                }
            }
    }

    @Test
    fun `responderer med feil dersom det ikke er mulig å lage brev for aktuell søknadsbehandling`() {
        assertThrows<LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans> {
            vilkårsvurdertInnvilget.let {
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                    clock = Clock.systemUTC(),
                    satsFactory = satsFactoryTestPåDato(),
                ).apply { it.accept(this) }
            }
        }

        assertThrows<LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans> {
            vilkårsvurdertInnvilget.leggTilVilkårFraBehandlingsinformasjon(
                Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
                clock = fixedClock,
            ).getOrFail().let {
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                    clock = Clock.systemUTC(),
                    satsFactory = satsFactoryTestPåDato(),
                ).apply { it.accept(this) }
            }
        }
    }

    @Test
    fun `lager request for vilkårsvurdert avslag`() {
        vilkårsvurdertInnvilget.leggTilUførevilkår(
            uførhet = avslåttUførevilkårUtenGrunnlag(),
            clock = fixedClock,
        ).getOrFail().let { søknadsbehandling ->
            LagBrevRequestVisitor(
                hentPerson = { person.right() },
                hentNavn = { hentNavn(it) },
                hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).apply { søknadsbehandling.accept(this) }.let {
                it.brevRequest shouldBe AvslagBrevRequest(
                    person = person,
                    avslag = Avslag(
                        opprettet = fixedTidspunkt,
                        avslagsgrunner = listOf(Avslagsgrunn.UFØRHET),
                        harEktefelle = false,
                        beregning = null,
                        formuegrunnlag = null,
                        halvtGrunnbeløpPerÅr = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
                    ),
                    saksbehandlerNavn = "-",
                    attestantNavn = "-",
                    fritekst = "",
                    forventetInntektStørreEnn0 = false,
                    dagensDato = fixedLocalDate,
                    saksnummer = vilkårsvurdertInnvilget.saksnummer,
                    satsoversikt = null,
                    sakstype = Sakstype.UFØRE
                ).right()

                it.brevRequest.map { brevRequest ->
                    brevRequest.tilDokument { generertPdf.right() }
                        .map { dokument ->
                            assertDokument<Dokument.UtenMetadata.Vedtak>(dokument, brevRequest)
                        }
                }
            }
        }
    }

    @Test
    fun `lager request for vilkårsvurdert avslag pga formue`() {
        val vilkårsvurdertAvslagPgaFormue: Søknadsbehandling.Vilkårsvurdert.Avslag =
            søknadsbehandlingVilkårsvurdertInnvilget().second
                .leggTilFormuevilkår(
                    vilkår = formuevilkårAvslåttPgrBrukersformue(),
                    clock = fixedClock,
                ).getOrFail() as Søknadsbehandling.Vilkårsvurdert.Avslag
        vilkårsvurdertAvslagPgaFormue.let { søknadsbehandling ->
            LagBrevRequestVisitor(
                hentPerson = { person.right() },
                hentNavn = { hentNavn(it) },
                hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).apply { søknadsbehandling.accept(this) }.let {
                it.brevRequest shouldBe AvslagBrevRequest(
                    person = person,
                    avslag = Avslag(
                        opprettet = fixedTidspunkt,
                        avslagsgrunner = listOf(Avslagsgrunn.FORMUE),
                        harEktefelle = false,
                        beregning = null,
                        formuegrunnlag = Formuegrunnlag.create(
                            id = søknadsbehandling.vilkårsvurderinger.formue.grunnlag.firstOrThrowIfMultipleOrEmpty().id,
                            periode = søknadsbehandling.vilkårsvurderinger.formue.grunnlag.firstOrThrowIfMultipleOrEmpty().periode,
                            opprettet = fixedTidspunkt,
                            epsFormue = null,
                            søkersFormue = Formuegrunnlag.Verdier.create(
                                verdiIkkePrimærbolig = 0,
                                verdiEiendommer = 0,
                                verdiKjøretøy = 0,
                                innskudd = 1000000,
                                verdipapir = 0,
                                pengerSkyldt = 0,
                                kontanter = 0,
                                depositumskonto = 0,
                            ),
                            behandlingsPeriode = søknadsbehandling.periode,
                            bosituasjon = søknadsbehandling.grunnlagsdata.bosituasjon.first() as Grunnlag.Bosituasjon.Fullstendig,
                        ),
                        halvtGrunnbeløpPerÅr = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
                    ),
                    saksbehandlerNavn = "-",
                    attestantNavn = "-",
                    fritekst = "",
                    forventetInntektStørreEnn0 = false,
                    dagensDato = fixedLocalDate,
                    saksnummer = vilkårsvurdertInnvilget.saksnummer,
                    satsoversikt = null,
                    sakstype = Sakstype.UFØRE
                ).right()

                it.brevRequest.map { brevRequest ->
                    brevRequest.tilDokument { generertPdf.right() }
                        .map { dokument ->
                            assertDokument<Dokument.UtenMetadata.Vedtak>(dokument, brevRequest)
                        }
                }
            }
        }
    }

    @Test
    fun `lager request for beregnet innvilget`() {
        vilkårsvurdertInnvilget.beregn(
            begrunnelse = null,
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).getOrFail()
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                    clock = fixedClock,
                    satsFactory = satsFactoryTestPåDato(),
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe LagBrevRequest.InnvilgetVedtak(
                        person = person,
                        beregning = expectedInnvilgetBeregning(søknadsbehandling.beregning.getId()),
                        harEktefelle = søknadsbehandling.grunnlagsdata.bosituasjon.harEPS(),
                        saksbehandlerNavn = "-",
                        attestantNavn = "-",
                        fritekst = "",
                        forventetInntektStørreEnn0 = false,
                        dagensDato = fixedLocalDate,
                        saksnummer = vilkårsvurdertInnvilget.saksnummer,
                        satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
                        sakstype = Sakstype.UFØRE
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for beregnet avslag`() {
        vilkårsvurdertInnvilget.leggTilFradragsgrunnlag(
            fradragsgrunnlag = listOf(
                Grunnlag.Fradragsgrunnlag.create(
                    opprettet = fixedTidspunkt,
                    fradrag = FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 50000.0,
                        periode = år(2021),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ),
            clock = fixedClock,
        ).getOrFail().beregn(
            begrunnelse = null,
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).getOrFail().let { søknadsbehandling ->
            LagBrevRequestVisitor(
                hentPerson = { person.right() },
                hentNavn = { hentNavn(it) },
                hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).apply { søknadsbehandling.accept(this) }.let {
                it.brevRequest shouldBe AvslagBrevRequest(
                    person = person,
                    avslag = Avslag(
                        opprettet = fixedTidspunkt,
                        avslagsgrunner = listOf(Avslagsgrunn.FOR_HØY_INNTEKT),
                        harEktefelle = false,
                        beregning = expectedAvslagBeregning(søknadsbehandling.beregning.getId()),
                        formuegrunnlag = null,
                        halvtGrunnbeløpPerÅr = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
                    ),
                    saksbehandlerNavn = "-",
                    attestantNavn = "-",
                    fritekst = "",
                    forventetInntektStørreEnn0 = false,
                    dagensDato = fixedLocalDate,
                    saksnummer = vilkårsvurdertInnvilget.saksnummer,
                    satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
                    sakstype = Sakstype.UFØRE
                ).right()
            }
        }
    }

    @Test
    fun `lager request for simulert`() {
        vilkårsvurdertInnvilget.beregn(
            begrunnelse = null,
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).getOrFail()
            .simuler(
                saksbehandler = saksbehandler,
            ) {
                simulerNyUtbetaling(
                    sak = sak,
                    request = it,
                    clock = fixedClock,
                )
            }.getOrFail().let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                    clock = fixedClock,
                    satsFactory = satsFactoryTestPåDato(),
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe LagBrevRequest.InnvilgetVedtak(
                        person = person,
                        beregning = expectedInnvilgetBeregning(søknadsbehandling.beregning.getId()),
                        harEktefelle = søknadsbehandling.grunnlagsdata.bosituasjon.harEPS(),
                        saksbehandlerNavn = "-",
                        attestantNavn = "-",
                        fritekst = "",
                        forventetInntektStørreEnn0 = false,
                        dagensDato = fixedLocalDate,
                        saksnummer = vilkårsvurdertInnvilget.saksnummer,
                        satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
                        sakstype = Sakstype.UFØRE
                    ).right()

                    it.brevRequest.map { brevRequest ->
                        brevRequest.tilDokument { generertPdf.right() }
                            .map { dokument ->
                                assertDokument<Dokument.UtenMetadata.Vedtak>(dokument, brevRequest)
                            }
                    }
                }
            }
    }

    @Test
    fun `lager request for avslag til attestering uten beregning`() {
        (
            vilkårsvurdertInnvilget.leggTilVilkårFraBehandlingsinformasjon(
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAvslåttFlyktning(),
                clock = fixedClock,
            ).getOrFail() as Søknadsbehandling.Vilkårsvurdert.Avslag
            )
            .tilAttestering(saksbehandler, "Fritekst!")
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                    clock = fixedClock,
                    satsFactory = satsFactoryTestPåDato(),
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe AvslagBrevRequest(
                        person = person,
                        avslag = Avslag(
                            opprettet = fixedTidspunkt,
                            avslagsgrunner = listOf(Avslagsgrunn.FLYKTNING),
                            harEktefelle = false,
                            beregning = null,
                            formuegrunnlag = null,
                            halvtGrunnbeløpPerÅr = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
                        ),
                        saksbehandlerNavn = saksbehandlerNavn,
                        attestantNavn = "-",
                        fritekst = "Fritekst!",
                        forventetInntektStørreEnn0 = false,
                        dagensDato = fixedLocalDate,
                        saksnummer = vilkårsvurdertInnvilget.saksnummer,
                        satsoversikt = null,
                        sakstype = Sakstype.UFØRE
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for avslag til attestering med beregning`() {
        (
            vilkårsvurdertInnvilget.leggTilFradragsgrunnlag(
                fradragsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag.create(
                        opprettet = fixedTidspunkt,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 50000.0,
                            periode = år(2021),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
                clock = fixedClock,
            ).getOrFail().beregn(
                begrunnelse = null,
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail() as Søknadsbehandling.Beregnet.Avslag
            )
            .tilAttestering(saksbehandler, "Fritekst!")
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                    clock = fixedClock,
                    satsFactory = satsFactoryTestPåDato(),
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe AvslagBrevRequest(
                        person = person,
                        avslag = Avslag(
                            opprettet = fixedTidspunkt,
                            avslagsgrunner = listOf(Avslagsgrunn.FOR_HØY_INNTEKT),
                            harEktefelle = false,
                            beregning = expectedAvslagBeregning(søknadsbehandling.beregning.getId()),
                            formuegrunnlag = null,
                            halvtGrunnbeløpPerÅr = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
                        ),
                        saksbehandlerNavn = saksbehandlerNavn,
                        attestantNavn = "-",
                        fritekst = "Fritekst!",
                        forventetInntektStørreEnn0 = false,
                        dagensDato = fixedLocalDate,
                        saksnummer = vilkårsvurdertInnvilget.saksnummer,
                        satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
                        sakstype = Sakstype.UFØRE
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for innvilget til attestering`() {
        vilkårsvurdertInnvilget.beregn(
            begrunnelse = null,
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).getOrFail()
            .simuler(
                saksbehandler = saksbehandler,
            ) {
                simulerNyUtbetaling(
                    sak = sak,
                    request = it,
                    clock = fixedClock,
                )
            }.getOrFail()
            .tilAttestering(saksbehandler, "Fritekst!")
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                    clock = fixedClock,
                    satsFactory = satsFactoryTestPåDato(),
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe LagBrevRequest.InnvilgetVedtak(
                        person = person,
                        beregning = expectedInnvilgetBeregning(søknadsbehandling.beregning.getId()),
                        harEktefelle = søknadsbehandling.grunnlagsdata.bosituasjon.harEPS(),
                        saksbehandlerNavn = saksbehandlerNavn,
                        attestantNavn = "-",
                        fritekst = "Fritekst!",
                        forventetInntektStørreEnn0 = false,
                        dagensDato = fixedLocalDate,
                        saksnummer = vilkårsvurdertInnvilget.saksnummer,
                        satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
                        sakstype = Sakstype.UFØRE
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for underkjent avslag uten beregning`() {
        (
            vilkårsvurdertInnvilget.leggTilVilkårFraBehandlingsinformasjon(
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAvslåttFlyktning(),
                clock = fixedClock,
            ).getOrFail() as Søknadsbehandling.Vilkårsvurdert.Avslag
            )
            .tilAttestering(saksbehandler, "Fritekst!")
            .tilUnderkjent(
                Attestering.Underkjent(
                    attestant = attestant,
                    grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                    kommentar = "kommentar",
                    opprettet = fixedTidspunkt,
                ),
            )
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                    clock = fixedClock,
                    satsFactory = satsFactoryTestPåDato(),
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe AvslagBrevRequest(
                        person = person,
                        avslag = Avslag(
                            opprettet = fixedTidspunkt,
                            avslagsgrunner = listOf(Avslagsgrunn.FLYKTNING),
                            harEktefelle = false,
                            beregning = null,
                            formuegrunnlag = null,
                            halvtGrunnbeløpPerÅr = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
                        ),
                        saksbehandlerNavn = saksbehandlerNavn,
                        attestantNavn = attestantNavn,
                        fritekst = "Fritekst!",
                        forventetInntektStørreEnn0 = false,
                        dagensDato = fixedLocalDate,
                        saksnummer = vilkårsvurdertInnvilget.saksnummer,
                        satsoversikt = null,
                        sakstype = Sakstype.UFØRE
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for underkjent avslag med beregning`() {
        (
            vilkårsvurdertInnvilget.leggTilFradragsgrunnlag(
                fradragsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag.create(
                        opprettet = fixedTidspunkt,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 50000.0,
                            periode = år(2021),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
                clock = fixedClock,
            ).getOrFail().beregn(
                begrunnelse = null,
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail() as Søknadsbehandling.Beregnet.Avslag
            )
            .tilAttestering(saksbehandler, "Fritekst!")
            .tilUnderkjent(
                Attestering.Underkjent(
                    attestant = attestant,
                    grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                    kommentar = "kommentar",
                    opprettet = fixedTidspunkt,

                ),
            )
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                    clock = fixedClock,
                    satsFactory = satsFactoryTestPåDato(),
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe AvslagBrevRequest(
                        person = person,
                        avslag = Avslag(
                            opprettet = fixedTidspunkt,
                            avslagsgrunner = listOf(Avslagsgrunn.FOR_HØY_INNTEKT),
                            harEktefelle = false,
                            beregning = expectedAvslagBeregning(søknadsbehandling.beregning.getId()),
                            formuegrunnlag = null,
                            halvtGrunnbeløpPerÅr = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
                        ),
                        saksbehandlerNavn = saksbehandlerNavn,
                        attestantNavn = attestantNavn,
                        fritekst = "Fritekst!",
                        forventetInntektStørreEnn0 = false,
                        dagensDato = fixedLocalDate,
                        saksnummer = vilkårsvurdertInnvilget.saksnummer,
                        satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
                        sakstype = Sakstype.UFØRE
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for underkjent innvilgelse`() {
        vilkårsvurdertInnvilget.beregn(
            begrunnelse = null,
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).getOrFail()
            .simuler(
                saksbehandler = saksbehandler,
            ) {
                simulerNyUtbetaling(
                    sak = sak,
                    request = it,
                    clock = fixedClock,
                )
            }.getOrFail()
            .tilAttestering(saksbehandler, "Fritekst!")
            .tilUnderkjent(
                Attestering.Underkjent(
                    attestant = attestant,
                    grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                    kommentar = "kommentar",
                    opprettet = fixedTidspunkt,
                ),
            )
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                    clock = fixedClock,
                    satsFactory = satsFactoryTestPåDato(),
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe LagBrevRequest.InnvilgetVedtak(
                        person = person,
                        beregning = expectedInnvilgetBeregning(søknadsbehandling.beregning.getId()),
                        harEktefelle = søknadsbehandling.grunnlagsdata.bosituasjon.harEPS(),
                        saksbehandlerNavn = saksbehandlerNavn,
                        attestantNavn = attestantNavn,
                        fritekst = "Fritekst!",
                        forventetInntektStørreEnn0 = false,
                        dagensDato = fixedLocalDate,
                        saksnummer = vilkårsvurdertInnvilget.saksnummer,
                        satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
                        sakstype = Sakstype.UFØRE
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for iverksatt avslag uten beregning`() {
        (
            vilkårsvurdertInnvilget.leggTilVilkårFraBehandlingsinformasjon(
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAvslåttFlyktning(),
                clock = fixedClock,
            ).getOrFail() as Søknadsbehandling.Vilkårsvurdert.Avslag
            )
            .tilAttestering(saksbehandler, "Fritekst!")
            .tilIverksatt(
                Attestering.Iverksatt(attestant, fixedTidspunkt),
            )
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                    clock = fixedClock,
                    satsFactory = satsFactoryTestPåDato(),
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe AvslagBrevRequest(
                        person = person,
                        avslag = Avslag(
                            opprettet = fixedTidspunkt,
                            avslagsgrunner = listOf(Avslagsgrunn.FLYKTNING),
                            harEktefelle = false,
                            beregning = null,
                            formuegrunnlag = null,
                            halvtGrunnbeløpPerÅr = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
                        ),
                        saksbehandlerNavn = saksbehandlerNavn,
                        attestantNavn = attestantNavn,
                        fritekst = "Fritekst!",
                        forventetInntektStørreEnn0 = false,
                        dagensDato = fixedLocalDate,
                        saksnummer = vilkårsvurdertInnvilget.saksnummer,
                        satsoversikt = null,
                        sakstype = Sakstype.UFØRE
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for iverksatt avslag med beregning`() {
        (
            vilkårsvurdertInnvilget.leggTilFradragsgrunnlag(
                fradragsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag.create(
                        opprettet = fixedTidspunkt,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 50000.0,
                            periode = år(2021),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
                clock = fixedClock,
            ).getOrFail()
                .beregn(
                    begrunnelse = null,
                    clock = fixedClock,
                    satsFactory = satsFactoryTestPåDato(),
                ).getOrFail() as Søknadsbehandling.Beregnet.Avslag
            )
            .tilAttestering(saksbehandler, "Fritekst!")
            .tilIverksatt(
                Attestering.Iverksatt(attestant, fixedTidspunkt),
            )
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                    clock = fixedClock,
                    satsFactory = satsFactoryTestPåDato(),
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe AvslagBrevRequest(
                        person = person,
                        avslag = Avslag(
                            opprettet = fixedTidspunkt,
                            avslagsgrunner = listOf(Avslagsgrunn.FOR_HØY_INNTEKT),
                            harEktefelle = false,
                            beregning = expectedAvslagBeregning(søknadsbehandling.beregning.getId()),
                            formuegrunnlag = null,
                            halvtGrunnbeløpPerÅr = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
                        ),
                        saksbehandlerNavn = saksbehandlerNavn,
                        attestantNavn = attestantNavn,
                        fritekst = "Fritekst!",
                        forventetInntektStørreEnn0 = false,
                        dagensDato = fixedLocalDate,
                        saksnummer = vilkårsvurdertInnvilget.saksnummer,
                        satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
                        sakstype = Sakstype.UFØRE
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for iverksatt innvilget`() {
        vilkårsvurdertInnvilget.beregn(
            begrunnelse = null,
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).getOrFail()
            .simuler(
                saksbehandler = saksbehandler,
            ) {
                simulerNyUtbetaling(
                    sak = sak,
                    request = it,
                    clock = fixedClock,
                )
            }.getOrFail()
            .tilAttestering(saksbehandler, "Fritekst!")
            .tilIverksatt(Attestering.Iverksatt(attestant, fixedTidspunkt))
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                    clock = fixedClock,
                    satsFactory = satsFactoryTestPåDato(),
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe LagBrevRequest.InnvilgetVedtak(
                        person = person,
                        beregning = expectedInnvilgetBeregning(søknadsbehandling.beregning.getId()),
                        harEktefelle = søknadsbehandling.grunnlagsdata.bosituasjon.harEPS(),
                        saksbehandlerNavn = saksbehandlerNavn,
                        attestantNavn = attestantNavn,
                        fritekst = "Fritekst!",
                        forventetInntektStørreEnn0 = false,
                        dagensDato = fixedLocalDate,
                        saksnummer = vilkårsvurdertInnvilget.saksnummer,
                        satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
                        sakstype = Sakstype.UFØRE
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for vedtak om innvilget stønad`() {
        val utbetalingId = UUID30.randomUUID()
        val søknadsbehandling = vilkårsvurdertInnvilget
            .beregn(
                begrunnelse = null,
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail()
            .simuler(
                saksbehandler = saksbehandler,
            ) {
                simulerNyUtbetaling(
                    sak = sak,
                    request = it,
                    clock = fixedClock,
                )
            }.getOrFail()
            .tilAttestering(saksbehandler, "Fritekst!")
            .tilIverksatt(Attestering.Iverksatt(attestant, fixedTidspunkt))

        val innvilgetVedtak = VedtakSomKanRevurderes.fromSøknadsbehandling(søknadsbehandling, utbetalingId, fixedClock)

        val brevSøknadsbehandling = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 0.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { søknadsbehandling.accept(this) }

        val brevVedtak = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 0.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { innvilgetVedtak.accept(this) }

        brevSøknadsbehandling.brevRequest shouldBe brevVedtak.brevRequest
        brevSøknadsbehandling.brevRequest shouldBe LagBrevRequest.InnvilgetVedtak(
            person = person,
            beregning = expectedInnvilgetBeregning(innvilgetVedtak.beregning.getId()),
            harEktefelle = søknadsbehandling.grunnlagsdata.bosituasjon.harEPS(),
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            fritekst = "Fritekst!",
            forventetInntektStørreEnn0 = false,
            dagensDato = fixedLocalDate,
            saksnummer = søknadsbehandling.saksnummer,
            satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
            sakstype = Sakstype.UFØRE
        ).right()
    }

    @Test
    fun `lager request for vedtak om avslått stønad med beregning`() {
        val søknadsbehandling = (
            vilkårsvurdertInnvilget.leggTilFradragsgrunnlag(
                fradragsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag.create(
                        opprettet = fixedTidspunkt,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 50000.0,
                            periode = år(2021),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
                clock = fixedClock,
            ).getOrFail().beregn(
                begrunnelse = null,
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail() as Søknadsbehandling.Beregnet.Avslag
            )
            .tilAttestering(saksbehandler, "Fritekst!")
            .tilIverksatt(Attestering.Iverksatt(attestant, fixedTidspunkt))

        val avslåttVedtak = Avslagsvedtak.fromSøknadsbehandlingMedBeregning(søknadsbehandling, fixedClock)

        val brevSøknadsbehandling = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 0.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { søknadsbehandling.accept(this) }

        val brevVedtak = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 0.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { avslåttVedtak.accept(this) }

        brevSøknadsbehandling.brevRequest shouldBe brevVedtak.brevRequest
        brevSøknadsbehandling.brevRequest shouldBe AvslagBrevRequest(
            person = person,
            avslag = Avslag(
                opprettet = fixedTidspunkt,
                avslagsgrunner = listOf(Avslagsgrunn.FOR_HØY_INNTEKT),
                harEktefelle = false,
                beregning = expectedAvslagBeregning(søknadsbehandling.beregning.getId()),
                formuegrunnlag = null,
                halvtGrunnbeløpPerÅr = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
            ),
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            fritekst = "Fritekst!",
            forventetInntektStørreEnn0 = false,
            dagensDato = fixedLocalDate,
            saksnummer = søknadsbehandling.saksnummer,
            satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
            sakstype = Sakstype.UFØRE
        ).right()
    }

    @Test
    fun `lager request for vedtak om avslått stønad uten beregning`() {
        val søknadsbehandling = (
            vilkårsvurdertInnvilget.leggTilVilkårFraBehandlingsinformasjon(
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAvslåttFlyktning(),
                clock = fixedClock,
            ).getOrFail() as Søknadsbehandling.Vilkårsvurdert.Avslag
            )
            .tilAttestering(saksbehandler, "Fritekst!")
            .tilIverksatt(Attestering.Iverksatt(attestant, fixedTidspunkt))

        val avslåttVedtak = Avslagsvedtak.fromSøknadsbehandlingUtenBeregning(
            avslag = søknadsbehandling,
            clock = fixedClock,
        )

        val brevSøknadsbehandling = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 0.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { søknadsbehandling.accept(this) }

        val brevVedtak = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 0.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { avslåttVedtak.accept(this) }

        brevSøknadsbehandling.brevRequest shouldBe brevVedtak.brevRequest
        brevSøknadsbehandling.brevRequest shouldBe AvslagBrevRequest(
            person = person,
            avslag = Avslag(
                opprettet = fixedTidspunkt,
                avslagsgrunner = listOf(Avslagsgrunn.FLYKTNING),
                harEktefelle = false,
                beregning = null,
                formuegrunnlag = null,
                halvtGrunnbeløpPerÅr = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
            ),
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            fritekst = "Fritekst!",
            forventetInntektStørreEnn0 = false,
            dagensDato = fixedLocalDate,
            saksnummer = søknadsbehandling.saksnummer,
            satsoversikt = null,
            sakstype = Sakstype.UFØRE
        ).right()
    }

    @Test
    fun `lager request for vedtak med avslått formue`() {
        val søknadsbehandling = (
            søknadsbehandlingVilkårsvurdertInnvilget().second
                .leggTilFormuevilkår(
                    vilkår = formuevilkårAvslåttPgrBrukersformue(),
                    clock = fixedClock,
                ).getOrFail() as Søknadsbehandling.Vilkårsvurdert.Avslag
            )
            .tilAttestering(saksbehandler, "Fritekst!")
            .tilIverksatt(Attestering.Iverksatt(attestant, fixedTidspunkt))

        val avslåttVedtak = Avslagsvedtak.fromSøknadsbehandlingUtenBeregning(
            avslag = søknadsbehandling,
            clock = fixedClock,
        )

        val brevSøknadsbehandling = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 0.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { søknadsbehandling.accept(this) }

        val brevVedtak = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 0.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { avslåttVedtak.accept(this) }

        brevSøknadsbehandling.brevRequest shouldBe brevVedtak.brevRequest
        brevSøknadsbehandling.brevRequest shouldBe AvslagBrevRequest(
            person = person,
            avslag = Avslag(
                opprettet = fixedTidspunkt,
                avslagsgrunner = listOf(Avslagsgrunn.FORMUE),
                harEktefelle = false,
                beregning = null,
                formuegrunnlag = Formuegrunnlag.create(
                    id = søknadsbehandling.vilkårsvurderinger.formue.grunnlag.firstOrThrowIfMultipleOrEmpty().id,
                    opprettet = søknadsbehandling.vilkårsvurderinger.formue.grunnlag.firstOrThrowIfMultipleOrEmpty().opprettet,
                    periode = søknadsbehandling.vilkårsvurderinger.formue.grunnlag.firstOrThrowIfMultipleOrEmpty().periode,
                    epsFormue = null,
                    søkersFormue = Formuegrunnlag.Verdier.create(
                        verdiIkkePrimærbolig = 0,
                        verdiEiendommer = 0,
                        verdiKjøretøy = 0,
                        innskudd = 1000000,
                        verdipapir = 0,
                        pengerSkyldt = 0,
                        kontanter = 0,
                        depositumskonto = 0,
                    ),
                    bosituasjon = søknadsbehandling.grunnlagsdata.bosituasjon.first() as Grunnlag.Bosituasjon.Fullstendig,
                    behandlingsPeriode = søknadsbehandling.periode,
                ),
                halvtGrunnbeløpPerÅr = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
            ),
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            fritekst = "Fritekst!",
            forventetInntektStørreEnn0 = false,
            dagensDato = fixedLocalDate,
            saksnummer = søknadsbehandling.saksnummer,
            satsoversikt = null,
            sakstype = Sakstype.UFØRE
        ).right()
    }

    @Test
    fun `lager request for vedtak om revurdering av inntekt`() {
        val utbetalingId = UUID30.randomUUID()
        val søknadsbehandling =
            vilkårsvurdertInnvilget.beregn(
                begrunnelse = null,
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail()
                .simuler(
                    saksbehandler = saksbehandler,
                ) {
                    simulerNyUtbetaling(
                        sak = sak,
                        request = it,
                        clock = fixedClock,
                    )
                }.getOrFail()
                .tilAttestering(saksbehandler, "Fritekst!")
                .tilIverksatt(Attestering.Iverksatt(attestant, fixedTidspunkt))

        val revurderingsperiode = år(2021)
        val revurdering = IverksattRevurdering.Innvilget(
            id = UUID.randomUUID(),
            periode = revurderingsperiode,
            opprettet = fixedTidspunkt,
            tilRevurdering = VedtakSomKanRevurderes.fromSøknadsbehandling(søknadsbehandling, utbetalingId, fixedClock),
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("15"),
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = revurderingsperiode,
                    ),
                ),
            ),
            beregning = expectedInnvilgetBeregning(søknadsbehandling.beregning.getId()),
            simulering = simulering,
            attesteringer = Attesteringshistorikk.empty()
                .leggTilNyAttestering(Attestering.Iverksatt(attestant, fixedTidspunkt)),
            fritekstTilBrev = "JEPP",
            revurderingsårsak = Revurderingsårsak(
                Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
            ),
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            avkorting = AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående,
            tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingFerdigbehandlet,
        )

        val avslåttVedtak = VedtakSomKanRevurderes.from(revurdering, utbetalingId, fixedClock)

        val brevRevurdering = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 0.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { revurdering.accept(this) }

        val brevVedtak = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 0.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { avslåttVedtak.accept(this) }

        brevRevurdering.brevRequest shouldBe brevVedtak.brevRequest
        brevRevurdering.brevRequest shouldBe LagBrevRequest.Inntekt(
            person = person,
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            revurdertBeregning = revurdering.beregning,
            fritekst = "JEPP",
            harEktefelle = false,
            forventetInntektStørreEnn0 = false,
            dagensDato = fixedLocalDate,
            saksnummer = revurdering.saksnummer,
            satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
        ).right()

        brevRevurdering.brevRequest.map { brevRequest ->
            brevRequest.tilDokument { generertPdf.right() }
                .map { dokument ->
                    assertDokument<Dokument.UtenMetadata.Vedtak>(dokument, brevRequest)
                }
        }
    }

    @Test
    fun `lager request for opphørsvedtak`() {
        val utbetalingId = UUID30.randomUUID()
        val søknadsbehandling =
            vilkårsvurdertInnvilget
                .beregn(
                    begrunnelse = null,
                    clock = fixedClock,
                    satsFactory = satsFactoryTestPåDato(),
                ).getOrFail()
                .simuler(
                    saksbehandler = saksbehandler,
                ) {
                    simulerNyUtbetaling(
                        sak = sak,
                        request = it,
                        clock = fixedClock,
                    )
                }.getOrFail()
                .tilAttestering(saksbehandler, "Fritekst!")
                .tilIverksatt(Attestering.Iverksatt(attestant, fixedTidspunkt))

        val revurderingsperiode = år(2021)
        val revurdering = IverksattRevurdering.Opphørt(
            id = UUID.randomUUID(),
            periode = revurderingsperiode,
            opprettet = fixedTidspunkt,
            tilRevurdering = VedtakSomKanRevurderes.fromSøknadsbehandling(søknadsbehandling, utbetalingId, fixedClock),
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("15"),
            beregning = expectedInnvilgetBeregning(søknadsbehandling.beregning.getId()),
            simulering = simulering,
            attesteringer = Attesteringshistorikk.empty()
                .leggTilNyAttestering(Attestering.Iverksatt(attestant, fixedTidspunkt)),
            fritekstTilBrev = "FRITEKST REVURDERING",
            revurderingsårsak = Revurderingsårsak(
                Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
            ),
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = revurderingsperiode,
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.Uføre(
                uføre = UføreVilkår.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        VurderingsperiodeUføre.create(
                            vurdering = Vurdering.Innvilget,
                            grunnlag = null,
                            periode = revurderingsperiode,
                            opprettet = fixedTidspunkt,
                        ),
                    ),
                ),
                formue = innvilgetFormueVilkår(periode = revurderingsperiode),
                utenlandsopphold = utenlandsoppholdInnvilget(periode = revurderingsperiode),
                opplysningsplikt = tilstrekkeligDokumentert(periode = revurderingsperiode),
                lovligOpphold = lovligOppholdVilkårInnvilget(),
                personligOppmøte = personligOppmøtevilkårInnvilget(periode = revurderingsperiode),
            ),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            avkorting = AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående,
            tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingFerdigbehandlet,
        )

        val opphørsvedtak = VedtakSomKanRevurderes.from(revurdering, utbetalingId, fixedClock)

        val brevRevurdering = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 0.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { revurdering.accept(this) }

        val brevVedtak = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 0.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { opphørsvedtak.accept(this) }

        brevRevurdering.brevRequest shouldBe brevVedtak.brevRequest
        brevRevurdering.brevRequest shouldBe LagBrevRequest.Opphørsvedtak(
            person = person,
            beregning = revurdering.beregning,
            harEktefelle = revurdering.grunnlagsdata.bosituasjon.harEPS(),
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            fritekst = "FRITEKST REVURDERING",
            forventetInntektStørreEnn0 = false,
            opphørsgrunner = emptyList(),
            dagensDato = fixedLocalDate,
            saksnummer = revurdering.saksnummer,
            opphørsdato = revurdering.periode.fraOgMed,
            avkortingsBeløp = null,
            satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
            halvtGrunnbeløp = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
        ).right()

        brevRevurdering.brevRequest.map { brevRequest ->
            brevRequest.tilDokument { generertPdf.right() }
                .map { dokument ->
                    assertDokument<Dokument.UtenMetadata.Vedtak>(dokument, brevRequest)
                }
        }
    }

    @Test
    fun `lager opphørsvedtak med opphørsgrunn for uførhet`() {
        val utbetalingId = UUID30.randomUUID()

        val (_, revurdering) = iverksattRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
            fritekstTilBrev = "FRITEKST REVURDERING",
        )
        val opphørsvedtak = VedtakSomKanRevurderes.from(revurdering, utbetalingId, fixedClock)
        val brevRevurdering = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 0.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { revurdering.accept(this) }

        val brevVedtak = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 0.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { opphørsvedtak.accept(this) }

        brevRevurdering.brevRequest shouldBe brevVedtak.brevRequest
        brevRevurdering.brevRequest shouldBe LagBrevRequest.Opphørsvedtak(
            person = person,
            beregning = revurdering.beregning,
            harEktefelle = revurdering.grunnlagsdata.bosituasjon.harEPS(),
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            fritekst = "FRITEKST REVURDERING",
            forventetInntektStørreEnn0 = false,
            opphørsgrunner = listOf(Opphørsgrunn.UFØRHET),
            dagensDato = fixedLocalDate,
            saksnummer = revurdering.saksnummer,
            opphørsdato = revurdering.periode.fraOgMed,
            avkortingsBeløp = null,
            satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
            halvtGrunnbeløp = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
        ).right()
    }

    @Test
    fun `lager opphørsvedtak med opphørsgrunn for høy inntekt`() {
        val utbetalingId = UUID30.randomUUID()
        val opphørsperiode = år(2021)

        val (sak, revurdering) = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            revurderingsperiode = opphørsperiode,
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling(
                grunnlagsdata = Grunnlagsdata.create(
                    fradragsgrunnlag = listOf(
                        fradragsgrunnlagArbeidsinntekt(
                            arbeidsinntekt = 150000.0,
                            periode = opphørsperiode,
                        ),
                    ),
                    bosituasjon = listOf(
                        bosituasjongrunnlagEnslig(periode = opphørsperiode),
                    ),
                ),
                vilkårsvurderinger = vilkårsvurderingerSøknadsbehandlingInnvilget(
                    periode = opphørsperiode,
                ),
            ),
        )

        val attestert = revurdering.beregn(
            eksisterendeUtbetalinger = sak.utbetalinger,
            clock = fixedClock,
            gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                fraOgMed = revurdering.periode.fraOgMed,
                clock = fixedClock,
            ).getOrFail(),
            satsFactory = satsFactoryTestPåDato(),
        ).getOrFail().let {
            (it as BeregnetRevurdering.Opphørt).toSimulert(
                { sakId, _, opphørsdato ->
                    simulertUtbetalingOpphør(
                        sakId = sakId,
                        opphørsdato = opphørsdato,
                        eksisterendeUtbetalinger = sak.utbetalinger,
                    )
                },
                false,
            ).getOrFail()
        }.ikkeSendForhåndsvarsel().getOrFail()
            .oppdaterTilbakekrevingsbehandling(
                tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingUnderBehandling,
            )
            .tilAttestering(
                attesteringsoppgaveId = oppgaveIdRevurdering,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "FRITEKST REVURDERING",
            ).getOrFail()
            .tilIverksatt(
                attestant = attestant,
                hentOpprinneligAvkorting = { null },
                clock = fixedClock,
            )
            .getOrFail()

        val opphørsvedtak = VedtakSomKanRevurderes.from(attestert, utbetalingId, fixedClock)

        val brevRevurdering = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 0.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { attestert.accept(this) }

        val brevVedtak = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 0.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { opphørsvedtak.accept(this) }

        brevRevurdering.brevRequest shouldBe brevVedtak.brevRequest
        brevRevurdering.brevRequest shouldBe LagBrevRequest.Opphørsvedtak(
            person = person,
            beregning = attestert.beregning,
            harEktefelle = attestert.grunnlagsdata.bosituasjon.harEPS(),
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            fritekst = "FRITEKST REVURDERING",
            forventetInntektStørreEnn0 = false,
            opphørsgrunner = listOf(Opphørsgrunn.FOR_HØY_INNTEKT),
            dagensDato = fixedLocalDate,
            saksnummer = revurdering.saksnummer,
            opphørsdato = revurdering.periode.fraOgMed,
            avkortingsBeløp = null,
            satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
            halvtGrunnbeløp = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
        ).right()
    }

    @Test
    fun `lager request for vedtak som ikke fører til endring`() {
        val utbetalingId = UUID30.randomUUID()
        val søknadsbehandling =
            vilkårsvurdertInnvilget.beregn(
                begrunnelse = null,
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail()
                .simuler(
                    saksbehandler = saksbehandler,
                ) {
                    simulerNyUtbetaling(
                        sak = sak,
                        request = it,
                        clock = fixedClock,
                    )
                }.getOrFail()
                .tilAttestering(saksbehandler, "Fritekst!")
                .tilIverksatt(Attestering.Iverksatt(attestant, fixedTidspunkt))

        val revurderingsperiode = år(2021)
        val revurdering = IverksattRevurdering.IngenEndring(
            id = UUID.randomUUID(),
            periode = revurderingsperiode,
            opprettet = fixedTidspunkt,
            tilRevurdering = VedtakSomKanRevurderes.fromSøknadsbehandling(søknadsbehandling, utbetalingId, fixedClock),
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("15"),
            beregning = expectedInnvilgetBeregning(søknadsbehandling.beregning.getId()),
            attesteringer = Attesteringshistorikk.empty()
                .leggTilNyAttestering(Attestering.Iverksatt(attestant, fixedTidspunkt)),
            fritekstTilBrev = "EN FIN FRITEKST",
            revurderingsårsak = Revurderingsårsak(
                Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
            ),
            skalFøreTilUtsendingAvVedtaksbrev = false,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = revurderingsperiode,
                    ),
                ),
            ),
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            avkorting = AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående,
        )

        val vedtakIngenEndring = VedtakSomKanRevurderes.from(revurdering, fixedClock)

        val brevRevurdering = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 120.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { revurdering.accept(this) }

        val brevVedtak = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 120.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { vedtakIngenEndring.accept(this) }

        brevRevurdering.brevRequest.map {
            it.right() shouldBe brevVedtak.brevRequest
            it shouldBe LagBrevRequest.VedtakIngenEndring(
                person = person,
                beregning = revurdering.beregning,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
                fritekst = "EN FIN FRITEKST",
                harEktefelle = revurdering.grunnlagsdata.bosituasjon.harEPS(),
                forventetInntektStørreEnn0 = false,
                gjeldendeMånedsutbetaling = 120,
                dagensDato = fixedLocalDate,
                saksnummer = revurdering.saksnummer,
                satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
            )

            it.brevInnhold should beOfType<BrevInnhold.VedtakIngenEndring>()
        }
    }

    private inline fun <reified T> assertDokument(
        dokument: Dokument.UtenMetadata,
        brevRequest: LagBrevRequest,
    ) {
        (dokument is T) shouldBe true
        dokument.tittel shouldBe brevRequest.brevInnhold.brevTemplate.tittel()
        dokument.generertDokument shouldBe generertPdf
        dokument.generertDokumentJson shouldBe brevRequest.brevInnhold.toJson()
    }

    private val person = Person(
        ident = Ident(
            fnr = Fnr.generer(),
            aktørId = AktørId(aktørId = "123"),
        ),
        navn = Person.Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy"),
    )
    private val generertPdf = "".toByteArray()

    private val saksbehandler = NavIdentBruker.Saksbehandler("Z123")
    private val saksbehandlerNavn = "saksbehandler"
    private val attestant = NavIdentBruker.Attestant("Z321")
    private val attestantNavn = "attestant"

    private fun hentNavn(navIdentBruker: NavIdentBruker): Either<LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant, String> =
        when (navIdentBruker) {
            is NavIdentBruker.Attestant -> attestantNavn.right()
            is NavIdentBruker.Saksbehandler -> saksbehandlerNavn.right()
            is NavIdentBruker.Veileder -> fail("skal ikke hente navn for en veileder")
        }

    private fun expectedInnvilgetBeregning(id: UUID): Beregning {
        return BeregningFactory(clock = fixedClock).ny(
            id = id,
            opprettet = fixedTidspunkt,
            fradrag = listOf(
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = år(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = år(2021),
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        )
    }

    private fun expectedAvslagBeregning(id: UUID): Beregning {
        return BeregningFactory(clock = fixedClock).ny(
            id = id,
            opprettet = fixedTidspunkt,
            fradrag = listOf(
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 50000.0,
                    periode = år(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = år(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = år(2021),
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        )
    }

    private val simulering = Simulering(
        gjelderId = Fnr.generer(),
        gjelderNavn = "",
        datoBeregnet = 1.januar(2021),
        nettoBeløp = 0,
        periodeList = listOf(),
    )

    private val `satsoversikt2021EnsligPr01-01-21` = Satsoversikt(
        perioder = listOf(
            Satsoversikt.Satsperiode(
                fraOgMed = "01.01.2021",
                tilOgMed = "31.12.2021",
                sats = "høy",
                satsBeløp = 20946,
                satsGrunn = "ENSLIG",
            ),
        ),
    )

    private val sakOgInnvilget = søknadsbehandlingVilkårsvurdertInnvilget()
    private val sak = sakOgInnvilget.first
    private val vilkårsvurdertInnvilget = sakOgInnvilget.second
}
