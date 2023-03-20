package no.nav.su.se.bakover.domain.visitor

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.AktørId
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Ident
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.application.Beløp
import no.nav.su.se.bakover.common.application.MånedBeløp
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mapFourth
import no.nav.su.se.bakover.common.mapSecond
import no.nav.su.se.bakover.common.periode.august
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.brev.KunneIkkeLageBrevRequest
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.brev.LagBrevRequest.AvslagBrevRequest
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.beregning.Tilbakekreving
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.harEPS
import no.nav.su.se.bakover.domain.grunnlag.firstOrThrowIfMultipleOrEmpty
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent.KallTilMicrosoftGraphApiFeilet
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.søknadsbehandling.BeregnetSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SimulertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørMedUtbetaling
import no.nav.su.se.bakover.test.beregnetSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandling
import no.nav.su.se.bakover.test.nySøknadsbehandlingUtenStønadsperiode
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.sendBrev
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simulertSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.tilAttesteringSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.underkjentSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vilkår.flyktningVilkårAvslått
import no.nav.su.se.bakover.test.vilkår.formuevilkårAvslåttPgaBrukersformue
import no.nav.su.se.bakover.test.vilkår.institusjonsoppholdvilkårAvslag
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandling
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import vilkår.personligOppmøtevilkårAvslag
import java.time.Clock
import java.time.Year
import java.util.UUID

internal class LagBrevRequestVisitorTest {

    @Test
    fun `responderer med feil dersom vi ikke får til å hente person`() {
        vilkårsvurdertSøknadsbehandling(
            customVilkår = listOf(institusjonsoppholdvilkårAvslag()),
        ).second.let { søknadsbehandling ->
            LagBrevRequestVisitor(
                hentPerson = { KunneIkkeLageBrevRequest.KunneIkkeHentePerson.left() },
                hentNavn = { hentNavn(it) },
                hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).apply { søknadsbehandling.accept(this) }.let {
                it.brevRequest shouldBe KunneIkkeLageBrevRequest.KunneIkkeHentePerson.left()
            }
        }
    }

    @Test
    fun `responderer med feil dersom vi ikke får til å hente navn for saksbehandler eller attestant`() {
        søknadsbehandlingIverksattInnvilget().second.let { søknadsbehandling ->
            LagBrevRequestVisitor(
                hentPerson = { person.right() },
                hentNavn = {
                    KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant(
                        KallTilMicrosoftGraphApiFeilet,
                    )
                        .left()
                },
                hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).apply { søknadsbehandling.accept(this) }.let {
                it.brevRequest shouldBe KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant(
                    KallTilMicrosoftGraphApiFeilet,
                )
                    .left()
            }
        }
    }

    @Test
    fun `responderer med feil dersom det ikke er mulig å lage brev for aktuell søknadsbehandling`() {
        assertThrows<LagBrevRequestVisitor.KanIkkeLageBrevrequestForInstans> {
            vilkårsvurdertSøknadsbehandling().second.let {
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                    clock = Clock.systemUTC(),
                    satsFactory = satsFactoryTestPåDato(),
                ).apply { it.accept(this) }
            }
        }

        assertThrows<LagBrevRequestVisitor.KanIkkeLageBrevrequestForInstans> {
            nySøknadsbehandlingUtenStønadsperiode().second
                .let {
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
        vilkårsvurdertSøknadsbehandling(
            customVilkår = listOf(avslåttUførevilkårUtenGrunnlag()),
        ).let { (sak, søknadsbehandling) ->
            søknadsbehandling.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
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
                    saksbehandlerNavn = søknadsbehandling.saksbehandler.toString(),
                    attestantNavn = "-",
                    fritekst = "",
                    forventetInntektStørreEnn0 = false,
                    dagensDato = fixedLocalDate,
                    saksnummer = sak.saksnummer,
                    satsoversikt = null,
                    sakstype = Sakstype.UFØRE,
                ).right()

                it.brevRequest.map { brevRequest ->
                    brevRequest.tilDokument(fixedClock) { generertPdf.right() }
                        .map { dokument ->
                            assertDokument<Dokument.UtenMetadata.Vedtak>(dokument, brevRequest)
                        }
                }
            }
        }
    }

    @Test
    fun `lager request for vilkårsvurdert avslag pga formue`() {
        vilkårsvurdertSøknadsbehandling(
            customVilkår = listOf(formuevilkårAvslåttPgaBrukersformue()),
        ).let { (sak, søknadsbehandling) ->
            søknadsbehandling.shouldBeType<VilkårsvurdertSøknadsbehandling.Avslag>()
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
                    saksbehandlerNavn = søknadsbehandling.saksbehandler.toString(),
                    attestantNavn = "-",
                    fritekst = "",
                    forventetInntektStørreEnn0 = false,
                    dagensDato = fixedLocalDate,
                    saksnummer = sak.saksnummer,
                    satsoversikt = null,
                    sakstype = Sakstype.UFØRE,
                ).right()

                it.brevRequest.map { brevRequest ->
                    brevRequest.tilDokument(fixedClock) { generertPdf.right() }
                        .map { dokument ->
                            assertDokument<Dokument.UtenMetadata.Vedtak>(dokument, brevRequest)
                        }
                }
            }
        }
    }

    @Test
    fun `lager request for beregnet innvilget`() {
        beregnetSøknadsbehandlingUføre()
            .let { (sak, søknadsbehandling) ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                    clock = fixedClock,
                    satsFactory = satsFactoryTestPåDato(),
                ).apply { søknadsbehandling.accept(this) }.let {
                    søknadsbehandling.shouldBeType<BeregnetSøknadsbehandling.Innvilget>()
                    it.brevRequest shouldBe LagBrevRequest.InnvilgetVedtak(
                        person = person,
                        beregning = expectedInnvilgetBeregning(søknadsbehandling.beregning.getId()),
                        harEktefelle = søknadsbehandling.grunnlagsdata.bosituasjon.harEPS(),
                        saksbehandlerNavn = søknadsbehandling.saksbehandler.toString(),
                        attestantNavn = "-",
                        fritekst = "",
                        forventetInntektStørreEnn0 = false,
                        dagensDato = fixedLocalDate,
                        saksnummer = sak.saksnummer,
                        satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
                        sakstype = Sakstype.UFØRE,
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for beregnet avslag`() {
        beregnetSøknadsbehandlingUføre(
            customGrunnlag = listOf(
                fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 50000.0),
            ),
        ).let { (sak, søknadsbehandling) ->
            søknadsbehandling.shouldBeType<BeregnetSøknadsbehandling.Avslag>()
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
                    saksbehandlerNavn = søknadsbehandling.saksbehandler.toString(),
                    attestantNavn = "-",
                    fritekst = "",
                    forventetInntektStørreEnn0 = false,
                    dagensDato = fixedLocalDate,
                    saksnummer = sak.saksnummer,
                    satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
                    sakstype = Sakstype.UFØRE,
                ).right()
            }
        }
    }

    @Test
    fun `lager request for simulert`() {
        simulertSøknadsbehandlingUføre().let { (sak, søknadsbehandling) ->
            søknadsbehandling.shouldBeType<SimulertSøknadsbehandling>()
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
                    saksbehandlerNavn = søknadsbehandling.saksbehandler.toString(),
                    attestantNavn = "-",
                    fritekst = "",
                    forventetInntektStørreEnn0 = false,
                    dagensDato = fixedLocalDate,
                    saksnummer = sak.saksnummer,
                    satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
                    sakstype = Sakstype.UFØRE,
                ).right()

                it.brevRequest.map { brevRequest ->
                    brevRequest.tilDokument(fixedClock) { generertPdf.right() }
                        .map { dokument ->
                            assertDokument<Dokument.UtenMetadata.Vedtak>(dokument, brevRequest)
                        }
                }
            }
        }
    }

    @Test
    fun `lager request for avslag til attestering uten beregning`() {
        tilAttesteringSøknadsbehandlingUføre(
            customVilkår = listOf(institusjonsoppholdvilkårAvslag()),
            fritekstTilBrev = "Fritekst!",
        ).let { (sak, søknadsbehandling) ->
            søknadsbehandling.shouldBeType<SøknadsbehandlingTilAttestering.Avslag.UtenBeregning>()
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
                        avslagsgrunner = listOf(Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON),
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
                    saksnummer = sak.saksnummer,
                    satsoversikt = null,
                    sakstype = Sakstype.UFØRE,
                ).right()
            }
        }
    }

    @Test
    fun `lager request for avslag til attestering med beregning`() {
        tilAttesteringSøknadsbehandlingUføre(
            customGrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 50000.0)),
            fritekstTilBrev = "Fritekst!",
        ).let { (sak, søknadsbehandling) ->
            søknadsbehandling.shouldBeType<SøknadsbehandlingTilAttestering.Avslag.MedBeregning>()
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
                        beregning = expectedAvslagBeregning(søknadsbehandling.beregning!!.getId()),
                        formuegrunnlag = null,
                        halvtGrunnbeløpPerÅr = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
                    ),
                    saksbehandlerNavn = saksbehandlerNavn,
                    attestantNavn = "-",
                    fritekst = "Fritekst!",
                    forventetInntektStørreEnn0 = false,
                    dagensDato = fixedLocalDate,
                    saksnummer = sak.saksnummer,
                    satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
                    sakstype = Sakstype.UFØRE,
                ).right()
            }
        }
    }

    @Test
    fun `lager request for innvilget til attestering`() {
        tilAttesteringSøknadsbehandlingUføre(
            fritekstTilBrev = "Fritekst!",
        ).let { (sak, søknadsbehandling) ->
            søknadsbehandling.shouldBeType<SøknadsbehandlingTilAttestering.Innvilget>()
            LagBrevRequestVisitor(
                hentPerson = { person.right() },
                hentNavn = { hentNavn(it) },
                hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).apply { søknadsbehandling.accept(this) }.let {
                it.brevRequest shouldBe LagBrevRequest.InnvilgetVedtak(
                    person = person,
                    beregning = expectedInnvilgetBeregning(søknadsbehandling.beregning!!.getId()),
                    harEktefelle = søknadsbehandling.grunnlagsdata.bosituasjon.harEPS(),
                    saksbehandlerNavn = saksbehandlerNavn,
                    attestantNavn = "-",
                    fritekst = "Fritekst!",
                    forventetInntektStørreEnn0 = false,
                    dagensDato = fixedLocalDate,
                    saksnummer = sak.saksnummer,
                    satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
                    sakstype = Sakstype.UFØRE,
                ).right()
            }
        }
    }

    @Test
    fun `lager request for underkjent avslag uten beregning`() {
        underkjentSøknadsbehandlingUføre(
            customVilkår = listOf(institusjonsoppholdvilkårAvslag()),
            fritekstTilBrev = "Fritekst!",
        ).let { (sak, søknadsbehandling) ->
            søknadsbehandling.shouldBeType<UnderkjentSøknadsbehandling.Avslag.UtenBeregning>()
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
                        avslagsgrunner = listOf(Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON),
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
                    saksnummer = sak.saksnummer,
                    satsoversikt = null,
                    sakstype = Sakstype.UFØRE,
                ).right()
            }
        }
    }

    @Test
    fun `lager request for underkjent avslag med beregning`() {
        underkjentSøknadsbehandlingUføre(
            customGrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 50000.0)),
            fritekstTilBrev = "Fritekst!",
        ).let { (sak, søknadsbehandling) ->
            søknadsbehandling.shouldBeType<UnderkjentSøknadsbehandling.Avslag.MedBeregning>()
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
                        beregning = expectedAvslagBeregning(søknadsbehandling.beregning!!.getId()),
                        formuegrunnlag = null,
                        halvtGrunnbeløpPerÅr = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
                    ),
                    saksbehandlerNavn = saksbehandlerNavn,
                    attestantNavn = "-",
                    fritekst = "Fritekst!",
                    forventetInntektStørreEnn0 = false,
                    dagensDato = fixedLocalDate,
                    saksnummer = sak.saksnummer,
                    satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
                    sakstype = Sakstype.UFØRE,
                ).right()
            }
        }
    }

    @Test
    fun `lager request for underkjent innvilgelse`() {
        underkjentSøknadsbehandlingUføre(
            fritekstTilBrev = "Fritekst!",
        ).let { (sak, søknadsbehandling) ->
            søknadsbehandling.shouldBeType<UnderkjentSøknadsbehandling.Innvilget>()
            LagBrevRequestVisitor(
                hentPerson = { person.right() },
                hentNavn = { hentNavn(it) },
                hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).apply { søknadsbehandling.accept(this) }.let {
                it.brevRequest shouldBe LagBrevRequest.InnvilgetVedtak(
                    person = person,
                    beregning = expectedInnvilgetBeregning(søknadsbehandling.beregning!!.getId()),
                    harEktefelle = søknadsbehandling.grunnlagsdata.bosituasjon.harEPS(),
                    saksbehandlerNavn = saksbehandlerNavn,
                    attestantNavn = "-",
                    fritekst = "Fritekst!",
                    forventetInntektStørreEnn0 = false,
                    dagensDato = fixedLocalDate,
                    saksnummer = sak.saksnummer,
                    satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
                    sakstype = Sakstype.UFØRE,
                ).right()
            }
        }
    }

    @Test
    fun `lager request for iverksatt avslag uten beregning`() {
        iverksattSøknadsbehandling(
            customVilkår = listOf(
                institusjonsoppholdvilkårAvslag(),
            ),
            fritekstTilBrev = "Fritekst!",
        ).let { (sak, søknadsbehandling) ->
            søknadsbehandling.shouldBeType<IverksattSøknadsbehandling.Avslag.UtenBeregning>()
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
                        avslagsgrunner = listOf(Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON),
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
                    saksnummer = sak.saksnummer,
                    satsoversikt = null,
                    sakstype = Sakstype.UFØRE,
                ).right()
            }
        }
    }

    @Test
    fun `lager request for iverksatt avslag med beregning`() {
        iverksattSøknadsbehandling(
            customGrunnlag = listOf(
                fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 50000.0),
            ),
            fritekstTilBrev = "Fritekst!",
        ).let { (sak, søknadsbehandling) ->
            søknadsbehandling.shouldBeType<IverksattSøknadsbehandling.Avslag.MedBeregning>()
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
                        beregning = expectedAvslagBeregning(søknadsbehandling.beregning!!.getId()),
                        formuegrunnlag = null,
                        halvtGrunnbeløpPerÅr = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
                    ),
                    saksbehandlerNavn = saksbehandlerNavn,
                    attestantNavn = attestantNavn,
                    fritekst = "Fritekst!",
                    forventetInntektStørreEnn0 = false,
                    dagensDato = fixedLocalDate,
                    saksnummer = sak.saksnummer,
                    satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
                    sakstype = Sakstype.UFØRE,
                ).right()
            }
        }
    }

    @Test
    fun `lager request for iverksatt innvilget`() {
        iverksattSøknadsbehandling(fritekstTilBrev = "Fritekst!").let { (sak, søknadsbehandling, _) ->
            LagBrevRequestVisitor(
                hentPerson = { person.right() },
                hentNavn = { hentNavn(it) },
                hentGjeldendeUtbetaling = { _, _ -> 0.right() },
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).apply { søknadsbehandling.accept(this) }.let {
                it.brevRequest shouldBe LagBrevRequest.InnvilgetVedtak(
                    person = person,
                    beregning = expectedInnvilgetBeregning(søknadsbehandling.beregning!!.getId()),
                    harEktefelle = søknadsbehandling.grunnlagsdata.bosituasjon.harEPS(),
                    saksbehandlerNavn = saksbehandlerNavn,
                    attestantNavn = attestantNavn,
                    fritekst = "Fritekst!",
                    forventetInntektStørreEnn0 = false,
                    dagensDato = fixedLocalDate,
                    saksnummer = sak.saksnummer,
                    satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
                    sakstype = Sakstype.UFØRE,
                ).right()
            }
        }
    }

    @Test
    fun `lager request for vedtak om innvilget stønad`() {
        val (_, søknadsbehandling, innvilgetVedtak) = iverksattSøknadsbehandling(
            fritekstTilBrev = "Fritekst!",
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
        ).apply { innvilgetVedtak.accept(this) }

        brevSøknadsbehandling.brevRequest shouldBe brevVedtak.brevRequest
        brevSøknadsbehandling.brevRequest shouldBe LagBrevRequest.InnvilgetVedtak(
            person = person,
            beregning = expectedInnvilgetBeregning(søknadsbehandling.beregning!!.getId()),
            harEktefelle = søknadsbehandling.grunnlagsdata.bosituasjon.harEPS(),
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            fritekst = "Fritekst!",
            forventetInntektStørreEnn0 = false,
            dagensDato = fixedLocalDate,
            saksnummer = søknadsbehandling.saksnummer,
            satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
            sakstype = Sakstype.UFØRE,
        ).right()
    }

    @Test
    fun `lager request for vedtak om avslått stønad med beregning`() {
        val (_, søknadsbehandling, avslåttVedtak) = iverksattSøknadsbehandling(
            customGrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 50000.0)),
            fritekstTilBrev = "Fritekst!",
        )
        søknadsbehandling.shouldBeType<IverksattSøknadsbehandling.Avslag.MedBeregning>()

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
                beregning = expectedAvslagBeregning(søknadsbehandling.beregning!!.getId()),
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
            sakstype = Sakstype.UFØRE,
        ).right()
    }

    @Test
    fun `lager request for vedtak om avslått stønad uten beregning`() {
        val (_, søknadsbehandling, avslåttVedtak) = iverksattSøknadsbehandling(
            customVilkår = listOf(institusjonsoppholdvilkårAvslag()),
            fritekstTilBrev = "Fritekst!",
        )
        søknadsbehandling.shouldBeType<IverksattSøknadsbehandling.Avslag.UtenBeregning>()

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
                avslagsgrunner = listOf(Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON),
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
            sakstype = Sakstype.UFØRE,
        ).right()
    }

    @Test
    fun `lager request for vedtak med avslått formue`() {
        val (_, søknadsbehandling, avslåttVedtak) = iverksattSøknadsbehandling(
            customVilkår = listOf(formuevilkårAvslåttPgaBrukersformue()),
            fritekstTilBrev = "Fritekst!",
        )
        søknadsbehandling.shouldBeType<IverksattSøknadsbehandling.Avslag.UtenBeregning>()
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
            sakstype = Sakstype.UFØRE,
        ).right()
    }

    @Test
    fun `lager request for vedtak om revurdering av inntekt`() {
        val (revurdering, vedtak) = vedtakRevurdering(
            revurderingsperiode = år(2021),
            brevvalg = sendBrev(fritekst = "JEPP"),
        ).let { (_, v) ->
            v.shouldBeType<VedtakInnvilgetRevurdering>().let {
                it.behandling to it
            }
        }

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
        ).apply { vedtak.accept(this) }

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
            brevRequest.tilDokument(fixedClock) { generertPdf.right() }
                .map { dokument ->
                    assertDokument<Dokument.UtenMetadata.Vedtak>(dokument, brevRequest)
                }
        }
    }

    @Test
    fun `lager request for opphørsvedtak uten tilbakekreving`() {
        val revurderingsperiode = august(2021)..desember(2021)
        val (revurdering, opphørsvedtak) = vedtakRevurdering(
            revurderingsperiode = revurderingsperiode,
            vilkårOverrides = listOf(
                personligOppmøtevilkårAvslag(periode = revurderingsperiode),
            ),
            brevvalg = sendBrev(fritekst = "FRITEKST REVURDERING"),
        ).let { (_, v) ->
            v.shouldBeType<VedtakOpphørMedUtbetaling>().let {
                it.behandling to it
            }
        }

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
            harEktefelle = false,
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            fritekst = "FRITEKST REVURDERING",
            forventetInntektStørreEnn0 = false,
            opphørsgrunner = listOf(Opphørsgrunn.PERSONLIG_OPPMØTE),
            dagensDato = fixedLocalDate,
            saksnummer = revurdering.saksnummer,
            opphørsperiode = revurdering.periode,
            avkortingsBeløp = null,
            satsoversikt = Satsoversikt(
                perioder = listOf(
                    Satsoversikt.Satsperiode(
                        fraOgMed = "01.08.2021",
                        tilOgMed = "31.12.2021",
                        sats = "høy",
                        satsBeløp = 20946,
                        satsGrunn = "ENSLIG",
                    ),
                ),
            ),
            halvtGrunnbeløp = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
        ).right()

        brevRevurdering.brevRequest.map { brevRequest ->
            brevRequest.tilDokument(fixedClock) { generertPdf.right() }
                .map { dokument ->
                    assertDokument<Dokument.UtenMetadata.Vedtak>(dokument, brevRequest)
                }
        }
    }

    @Test
    fun `lager opphørsvedtak med opphørsgrunn for uførhet`() {
        val revurderingsperiode = august(2021)..desember(2021)
        val (_, revurdering, _, vedtak) = iverksattRevurdering(
            revurderingsperiode = revurderingsperiode,
            vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag(periode = revurderingsperiode)),
            brevvalg = sendBrev(fritekst = "FRITEKST REVURDERING"),
        ).mapSecond { it as IverksattRevurdering.Opphørt }
            .mapFourth { it as VedtakOpphørMedUtbetaling }

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
        ).apply { vedtak.accept(this) }

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
            opphørsperiode = revurdering.periode,
            avkortingsBeløp = null,
            satsoversikt = Satsoversikt(
                perioder = listOf(
                    Satsoversikt.Satsperiode(
                        fraOgMed = "01.08.2021",
                        tilOgMed = "31.12.2021",
                        sats = "høy",
                        satsBeløp = 20946,
                        satsGrunn = "ENSLIG",
                    ),
                ),
            ),
            halvtGrunnbeløp = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
        ).right()
    }

    @Test
    fun `lager opphørsvedtak med opphørsgrunn for høy inntekt`() {
        val opphørsperiode = august(2021)..desember(2021)

        val (revurdering, opphørsvedtak) = vedtakRevurdering(
            revurderingsperiode = opphørsperiode,
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    arbeidsinntekt = 150000.0,
                    periode = opphørsperiode,
                ),
            ),
            brevvalg = sendBrev(fritekst = "FRITEKST REVURDERING"),
        ).let {
            Pair(it.second.behandling as IverksattRevurdering, it.second)
        }

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
            opphørsgrunner = listOf(Opphørsgrunn.FOR_HØY_INNTEKT),
            dagensDato = fixedLocalDate,
            saksnummer = revurdering.saksnummer,
            opphørsperiode = revurdering.periode,
            avkortingsBeløp = null,
            satsoversikt = Satsoversikt(
                perioder = listOf(
                    Satsoversikt.Satsperiode(
                        fraOgMed = "01.08.2021",
                        tilOgMed = "31.12.2021",
                        sats = "høy",
                        satsBeløp = 20946,
                        satsGrunn = "ENSLIG",
                    ),
                ),
            ),
            halvtGrunnbeløp = 50676, // halvparten av grunnbeløp for 2020-05-01 som er 101351 avrundet
        ).right()
    }

    @Test
    fun `tilbakekrevingsbrev dersom tilbakekreving ved endring`() {
        val vedtak = vedtakRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = mai(2021),
                    arbeidsinntekt = 5000.0,
                ),
            ),
            utbetalingerKjørtTilOgMed = 1.juli(2021),
        ).second.shouldBeType<VedtakInnvilgetRevurdering>()

        val brevRevurdering = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 120.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { vedtak.behandling.accept(this) }.brevRequest.getOrFail()

        val brevVedtak = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 120.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { vedtak.accept(this) }.brevRequest.getOrFail()

        brevRevurdering shouldBe brevVedtak
        brevVedtak shouldBe LagBrevRequest.TilbakekrevingAvPenger(
            ordinærtRevurderingBrev = LagBrevRequest.Inntekt(
                person = person,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
                revurdertBeregning = vedtak.beregning,
                fritekst = "En deilig fritekst",
                harEktefelle = false,
                forventetInntektStørreEnn0 = false,
                dagensDato = fixedLocalDate,
                saksnummer = vedtak.behandling.saksnummer,
                satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
            ),
            tilbakekreving = Tilbakekreving(
                månedBeløp = listOf(
                    MånedBeløp(mai(2021), Beløp(5000)),
                ),
            ),
            satsoversikt = `satsoversikt2021EnsligPr01-01-21`,
        )
    }

    @Test
    fun `tilbakekrevingsbrev dersom tilbakekreving ved opphør`() {
        val vedtak = vedtakRevurdering(
            revurderingsperiode = juni(2021)..(desember(2021)),
            vilkårOverrides = listOf(
                flyktningVilkårAvslått(
                    periode = juni(2021)..(desember(2021)),
                ),
            ),
            utbetalingerKjørtTilOgMed = 1.juli(2021),
        ).second.shouldBeType<VedtakOpphørMedUtbetaling>()

        val brevRevurdering = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 120.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { vedtak.behandling.accept(this) }.brevRequest.getOrFail()

        val brevVedtak = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            hentGjeldendeUtbetaling = { _, _ -> 120.right() },
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
        ).apply { vedtak.accept(this) }.brevRequest.getOrFail()

        brevRevurdering shouldBe brevVedtak
        brevVedtak shouldBe LagBrevRequest.TilbakekrevingAvPenger(
            ordinærtRevurderingBrev = LagBrevRequest.Inntekt(
                person = person,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
                revurdertBeregning = vedtak.beregning,
                fritekst = "En deilig fritekst",
                harEktefelle = false,
                forventetInntektStørreEnn0 = false,
                dagensDato = fixedLocalDate,
                saksnummer = vedtak.behandling.saksnummer,
                satsoversikt = Satsoversikt(
                    perioder = listOf(
                        Satsoversikt.Satsperiode(
                            fraOgMed = "01.06.2021",
                            tilOgMed = "31.12.2021",
                            sats = "høy",
                            satsBeløp = 20946,
                            satsGrunn = "ENSLIG",
                        ),
                    ),
                ),
            ),
            tilbakekreving = Tilbakekreving(
                månedBeløp = listOf(
                    MånedBeløp(juni(2021), Beløp(20946)),
                ),
            ),
            satsoversikt = Satsoversikt(
                perioder = listOf(
                    Satsoversikt.Satsperiode(
                        fraOgMed = "01.06.2021",
                        tilOgMed = "31.12.2021",
                        sats = "høy",
                        satsBeløp = 20946,
                        satsGrunn = "ENSLIG",
                    ),
                ),
            ),
        )
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
        fødsel = Person.Fødsel.MedFødselsår(
            år = Year.of(1956),
        ),
    )
    private val generertPdf = "".toByteArray()

    private val saksbehandler = NavIdentBruker.Saksbehandler("Z123")
    private val saksbehandlerNavn = "saksbehandler"
    private val attestant = NavIdentBruker.Attestant("Z321")
    private val attestantNavn = "attestant"

    private fun hentNavn(navIdentBruker: NavIdentBruker): Either<KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant, String> =
        when (navIdentBruker) {
            is NavIdentBruker.Attestant -> attestantNavn.right()
            is NavIdentBruker.Saksbehandler -> saksbehandlerNavn.right()
            is NavIdentBruker.Veileder -> fail("skal ikke hente navn for en veileder")
            is NavIdentBruker.Drift -> fail("skal ikke hente navn for en drift")
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
}
