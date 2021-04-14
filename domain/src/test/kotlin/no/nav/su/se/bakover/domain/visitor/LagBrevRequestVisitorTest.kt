package no.nav.su.se.bakover.domain.visitor

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.FnrGenerator
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.ValgtStønadsperiode
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagBrevRequest
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.withVilkårAvslått
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

internal class LagBrevRequestVisitorTest {
    @Test
    fun `responderer med feil dersom vi ikke får til å hente person`() {
        uavklart.tilVilkårsvurdert(Behandlingsinformasjon.lagTomBehandlingsinformasjon().withVilkårAvslått())
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHentePerson.left() },
                    hentNavn = { hentNavn(it) },
                    clock = clock,
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHentePerson.left()
                }
            }
    }

    @Test
    fun `responderer med feil dersom vi ikke får til å hente navn for saksbehandler eller attestant`() {
        uavklart.tilVilkårsvurdert(Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt())
            .tilBeregnet(innvilgetBeregning)
            .tilSimulert(simulering)
            .tilAttestering(saksbehandler, "Fritekst!")
            .tilIverksatt(Attestering.Iverksatt(attestant))
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left() },
                    clock = clock,
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left()
                }
            }
    }

    @Test
    fun `responderer med feil dersom det ikke er mulig å lage brev for aktuell søknadsbehandling`() {
        assertThrows<LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans> {
            uavklart.let {
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    clock = Clock.systemUTC(),
                ).apply { it.accept(this) }
            }
        }

        assertThrows<LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans> {
            uavklart.tilVilkårsvurdert(Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt())
                .let {
                    LagBrevRequestVisitor(
                        hentPerson = { person.right() },
                        hentNavn = { hentNavn(it) },
                        clock = Clock.systemUTC(),
                    ).apply { it.accept(this) }
                }
        }
    }

    @Test
    fun `lager request for vilkårsvurdert avslag`() {
        uavklart.tilVilkårsvurdert(Behandlingsinformasjon.lagTomBehandlingsinformasjon().withVilkårAvslått())
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    clock = clock,
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe AvslagBrevRequest(
                        person = person,
                        avslag = Avslag(
                            opprettet = Tidspunkt.now(clock),
                            avslagsgrunner = listOf(Avslagsgrunn.UFØRHET),
                            harEktefelle = false,
                            beregning = null,
                        ),
                        saksbehandlerNavn = "-",
                        attestantNavn = "-",
                        fritekst = "",
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for beregnet innvilget`() {
        uavklart.tilVilkårsvurdert(Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt())
            .tilBeregnet(innvilgetBeregning)
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    clock = clock,
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe LagBrevRequest.InnvilgetVedtak(
                        person = person,
                        beregning = innvilgetBeregning,
                        behandlingsinformasjon = søknadsbehandling.behandlingsinformasjon,
                        saksbehandlerNavn = "-",
                        attestantNavn = "-",
                        fritekst = "",
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for beregnet avslag`() {
        uavklart.tilVilkårsvurdert(Behandlingsinformasjon.lagTomBehandlingsinformasjon().withVilkårAvslått())
            .tilBeregnet(avslagBeregning)
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    clock = clock,
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe AvslagBrevRequest(
                        person = person,
                        avslag = Avslag(
                            opprettet = Tidspunkt.now(clock),
                            avslagsgrunner = listOf(Avslagsgrunn.UFØRHET, Avslagsgrunn.FOR_HØY_INNTEKT),
                            harEktefelle = false,
                            beregning = avslagBeregning,
                        ),
                        saksbehandlerNavn = "-",
                        attestantNavn = "-",
                        fritekst = "",
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for simulert`() {
        uavklart.tilVilkårsvurdert(Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt())
            .tilBeregnet(innvilgetBeregning)
            .tilSimulert(simulering)
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    clock = clock,
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe LagBrevRequest.InnvilgetVedtak(
                        person = person,
                        beregning = innvilgetBeregning,
                        behandlingsinformasjon = søknadsbehandling.behandlingsinformasjon,
                        saksbehandlerNavn = "-",
                        attestantNavn = "-",
                        fritekst = "",
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for avslag til attestering uten beregning`() {
        (
            uavklart.tilVilkårsvurdert(
                Behandlingsinformasjon.lagTomBehandlingsinformasjon().withVilkårAvslått(),
            ) as Søknadsbehandling.Vilkårsvurdert.Avslag
            )
            .tilAttestering(saksbehandler, "Fritekst!")
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    clock = clock,
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe AvslagBrevRequest(
                        person = person,
                        avslag = Avslag(
                            opprettet = Tidspunkt.now(clock),
                            avslagsgrunner = listOf(Avslagsgrunn.UFØRHET),
                            harEktefelle = false,
                            beregning = null,
                        ),
                        saksbehandlerNavn = saksbehandlerNavn,
                        attestantNavn = "-",
                        fritekst = "Fritekst!",
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for avslag til attestering med beregning`() {
        (
            uavklart.tilVilkårsvurdert(Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt())
                .tilBeregnet(avslagBeregning) as Søknadsbehandling.Beregnet.Avslag
            )
            .tilAttestering(saksbehandler, "Fritekst!")
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    clock = clock,
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe AvslagBrevRequest(
                        person = person,
                        avslag = Avslag(
                            opprettet = Tidspunkt.now(clock),
                            avslagsgrunner = listOf(Avslagsgrunn.FOR_HØY_INNTEKT),
                            harEktefelle = false,
                            beregning = avslagBeregning,
                        ),
                        saksbehandlerNavn = saksbehandlerNavn,
                        attestantNavn = "-",
                        fritekst = "Fritekst!",
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for innvilget til attestering`() {
        uavklart.tilVilkårsvurdert(Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt())
            .tilBeregnet(innvilgetBeregning)
            .tilSimulert(simulering)
            .tilAttestering(saksbehandler, "Fritekst!")
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    clock = clock,
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe LagBrevRequest.InnvilgetVedtak(
                        person = person,
                        beregning = innvilgetBeregning,
                        behandlingsinformasjon = søknadsbehandling.behandlingsinformasjon,
                        saksbehandlerNavn = saksbehandlerNavn,
                        attestantNavn = "-",
                        fritekst = "Fritekst!",
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for underkjent avslag uten beregning`() {
        (
            uavklart.tilVilkårsvurdert(
                Behandlingsinformasjon.lagTomBehandlingsinformasjon().withVilkårAvslått(),
            ) as Søknadsbehandling.Vilkårsvurdert.Avslag
            )
            .tilAttestering(saksbehandler, "Fritekst!")
            .tilUnderkjent(
                Attestering.Underkjent(
                    attestant,
                    Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                    "kommentar",
                ),
            )
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    clock = clock,
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe AvslagBrevRequest(
                        person = person,
                        avslag = Avslag(
                            opprettet = Tidspunkt.now(clock),
                            avslagsgrunner = listOf(Avslagsgrunn.UFØRHET),
                            harEktefelle = false,
                            beregning = null,
                        ),
                        saksbehandlerNavn = saksbehandlerNavn,
                        attestantNavn = attestantNavn,
                        fritekst = "Fritekst!",
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for underkjent avslag med beregning`() {
        (
            uavklart.tilVilkårsvurdert(Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt())
                .tilBeregnet(avslagBeregning) as Søknadsbehandling.Beregnet.Avslag
            )
            .tilAttestering(saksbehandler, "Fritekst!")
            .tilUnderkjent(
                Attestering.Underkjent(
                    attestant,
                    Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                    "kommentar",
                ),
            )
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    clock = clock,
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe AvslagBrevRequest(
                        person = person,
                        avslag = Avslag(
                            opprettet = Tidspunkt.now(clock),
                            avslagsgrunner = listOf(Avslagsgrunn.FOR_HØY_INNTEKT),
                            harEktefelle = false,
                            beregning = avslagBeregning,
                        ),
                        saksbehandlerNavn = saksbehandlerNavn,
                        attestantNavn = attestantNavn,
                        fritekst = "Fritekst!",
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for underkjent innvilgelse`() {
        uavklart.tilVilkårsvurdert(Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt())
            .tilBeregnet(innvilgetBeregning)
            .tilSimulert(simulering)
            .tilAttestering(saksbehandler, "Fritekst!")
            .tilUnderkjent(
                Attestering.Underkjent(
                    attestant,
                    Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                    "kommentar",
                ),
            )
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    clock = clock,
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe LagBrevRequest.InnvilgetVedtak(
                        person = person,
                        beregning = innvilgetBeregning,
                        behandlingsinformasjon = søknadsbehandling.behandlingsinformasjon,
                        saksbehandlerNavn = saksbehandlerNavn,
                        attestantNavn = attestantNavn,
                        fritekst = "Fritekst!",
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for iverksatt avslag uten beregning`() {
        (
            uavklart.tilVilkårsvurdert(
                Behandlingsinformasjon.lagTomBehandlingsinformasjon().withVilkårAvslått(),
            ) as Søknadsbehandling.Vilkårsvurdert.Avslag
            )
            .tilAttestering(saksbehandler, "Fritekst!")
            .tilIverksatt(
                Attestering.Iverksatt(attestant),
            )
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    clock = clock,
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe AvslagBrevRequest(
                        person = person,
                        avslag = Avslag(
                            opprettet = Tidspunkt.now(clock),
                            avslagsgrunner = listOf(Avslagsgrunn.UFØRHET),
                            harEktefelle = false,
                            beregning = null,
                        ),
                        saksbehandlerNavn = saksbehandlerNavn,
                        attestantNavn = attestantNavn,
                        fritekst = "Fritekst!",
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for iverksatt avslag med beregning`() {
        (
            uavklart.tilVilkårsvurdert(Behandlingsinformasjon.lagTomBehandlingsinformasjon().withVilkårAvslått())
                .tilBeregnet(avslagBeregning) as Søknadsbehandling.Beregnet.Avslag
            )
            .tilAttestering(saksbehandler, "Fritekst!")
            .tilIverksatt(
                Attestering.Iverksatt(attestant),
            )
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    clock = clock,
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe AvslagBrevRequest(
                        person = person,
                        avslag = Avslag(
                            opprettet = Tidspunkt.now(clock),
                            avslagsgrunner = listOf(Avslagsgrunn.UFØRHET, Avslagsgrunn.FOR_HØY_INNTEKT),
                            harEktefelle = false,
                            beregning = avslagBeregning,
                        ),
                        saksbehandlerNavn = saksbehandlerNavn,
                        attestantNavn = attestantNavn,
                        fritekst = "Fritekst!",
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for iverksatt innvilget`() {
        uavklart.tilVilkårsvurdert(Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt())
            .tilBeregnet(innvilgetBeregning)
            .tilSimulert(simulering)
            .tilAttestering(saksbehandler, "Fritekst!")
            .tilIverksatt(Attestering.Iverksatt(attestant))
            .let { søknadsbehandling ->
                LagBrevRequestVisitor(
                    hentPerson = { person.right() },
                    hentNavn = { hentNavn(it) },
                    clock = clock,
                ).apply { søknadsbehandling.accept(this) }.let {
                    it.brevRequest shouldBe LagBrevRequest.InnvilgetVedtak(
                        person = person,
                        beregning = innvilgetBeregning,
                        behandlingsinformasjon = søknadsbehandling.behandlingsinformasjon,
                        saksbehandlerNavn = saksbehandlerNavn,
                        attestantNavn = attestantNavn,
                        fritekst = "Fritekst!",
                    ).right()
                }
            }
    }

    @Test
    fun `lager request for vedtak om innvilget stønad`() {
        val utbetalingId = UUID30.randomUUID()
        val søknadsbehandling =
            uavklart.tilVilkårsvurdert(Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt())
                .tilBeregnet(innvilgetBeregning)
                .tilSimulert(simulering)
                .tilAttestering(saksbehandler, "Fritekst!")
                .tilIverksatt(Attestering.Iverksatt(attestant))

        val innvilgetVedtak = Vedtak.fromSøknadsbehandling(søknadsbehandling, utbetalingId)

        val brevSøknadsbehandling = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            clock = clock,
        ).apply { søknadsbehandling.accept(this) }

        val brevVedtak = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            clock = clock,
        ).apply { innvilgetVedtak.accept(this) }

        brevSøknadsbehandling.brevRequest shouldBe brevVedtak.brevRequest
        brevSøknadsbehandling.brevRequest shouldBe LagBrevRequest.InnvilgetVedtak(
            person = person,
            beregning = innvilgetBeregning,
            behandlingsinformasjon = søknadsbehandling.behandlingsinformasjon,
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            fritekst = "Fritekst!",
        ).right()
    }

    @Test
    fun `lager request for vedtak om avslått stønad med beregning`() {
        val søknadsbehandling = (
            uavklart.tilVilkårsvurdert(Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt())
                .tilBeregnet(avslagBeregning) as Søknadsbehandling.Beregnet.Avslag
            )
            .tilAttestering(saksbehandler, "Fritekst!")
            .tilIverksatt(Attestering.Iverksatt(attestant))

        val avslåttVedtak = Vedtak.Avslag.fromSøknadsbehandlingMedBeregning(søknadsbehandling)

        val brevSøknadsbehandling = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            clock = clock,
        ).apply { søknadsbehandling.accept(this) }

        val brevVedtak = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            clock = clock,
        ).apply { avslåttVedtak.accept(this) }

        brevSøknadsbehandling.brevRequest shouldBe brevVedtak.brevRequest
        brevSøknadsbehandling.brevRequest shouldBe AvslagBrevRequest(
            person = person,
            avslag = Avslag(
                opprettet = Tidspunkt.now(clock),
                avslagsgrunner = listOf(Avslagsgrunn.FOR_HØY_INNTEKT),
                harEktefelle = false,
                beregning = avslagBeregning,
            ),
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            fritekst = "Fritekst!",
        ).right()
    }

    @Test
    fun `lager request for vedtak om avslått stønad uten beregning`() {
        val søknadsbehandling = (
            uavklart.tilVilkårsvurdert(
                Behandlingsinformasjon.lagTomBehandlingsinformasjon().withVilkårAvslått(),
            ) as Søknadsbehandling.Vilkårsvurdert.Avslag
            )
            .tilAttestering(saksbehandler, "Fritekst!")
            .tilIverksatt(Attestering.Iverksatt(attestant))

        val avslåttVedtak = Vedtak.Avslag.fromSøknadsbehandlingUtenBeregning(søknadsbehandling)

        val brevSøknadsbehandling = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            clock = clock,
        ).apply { søknadsbehandling.accept(this) }

        val brevVedtak = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            clock = clock,
        ).apply { avslåttVedtak.accept(this) }

        brevSøknadsbehandling.brevRequest shouldBe brevVedtak.brevRequest
        brevSøknadsbehandling.brevRequest shouldBe AvslagBrevRequest(
            person = person,
            avslag = Avslag(
                opprettet = Tidspunkt.now(clock),
                avslagsgrunner = listOf(Avslagsgrunn.UFØRHET),
                harEktefelle = false,
                beregning = null,
            ),
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            fritekst = "Fritekst!",
        ).right()
    }

    @Test
    fun `lager request for vedtak om revurdering av inntekt`() {
        val utbetalingId = UUID30.randomUUID()
        val søknadsbehandling =
            uavklart.tilVilkårsvurdert(Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt())
                .tilBeregnet(innvilgetBeregning)
                .tilSimulert(simulering)
                .tilAttestering(saksbehandler, "Fritekst!")
                .tilIverksatt(Attestering.Iverksatt(attestant))

        val revurdering = IverksattRevurdering.Innvilget(
            id = UUID.randomUUID(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
            opprettet = Tidspunkt.now(clock),
            tilRevurdering = Vedtak.fromSøknadsbehandling(søknadsbehandling, utbetalingId),
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("15"),
            beregning = innvilgetBeregning,
            simulering = simulering,
            attestering = Attestering.Iverksatt(attestant),
            fritekstTilBrev = "JEPP",
            revurderingsårsak = Revurderingsårsak(
                Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
            ),
        )

        val avslåttVedtak = Vedtak.from(revurdering, utbetalingId)

        val brevRevurdering = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            clock = clock,
        ).apply { revurdering.accept(this) }

        val brevVedtak = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            clock = clock,
        ).apply { avslåttVedtak.accept(this) }

        brevRevurdering.brevRequest shouldBe brevVedtak.brevRequest
        brevRevurdering.brevRequest shouldBe LagBrevRequest.Revurdering.Inntekt(
            person = person,
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            revurdertBeregning = revurdering.beregning,
            fritekst = "JEPP",
            harEktefelle = false,
        ).right()
    }

    @Test
    fun `lager request for opphørsvedtak`() {
        val utbetalingId = UUID30.randomUUID()
        val søknadsbehandling =
            uavklart.tilVilkårsvurdert(Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt())
                .tilBeregnet(innvilgetBeregning)
                .tilSimulert(simulering)
                .tilAttestering(saksbehandler, "Fritekst!")
                .tilIverksatt(Attestering.Iverksatt(attestant))

        val revurdering = IverksattRevurdering.Opphørt(
            id = UUID.randomUUID(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
            opprettet = Tidspunkt.now(clock),
            tilRevurdering = Vedtak.fromSøknadsbehandling(søknadsbehandling, utbetalingId),
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("15"),
            beregning = innvilgetBeregning,
            simulering = simulering,
            attestering = Attestering.Iverksatt(attestant),
            fritekstTilBrev = "FRITEKST REVURDERING",
            revurderingsårsak = Revurderingsårsak(
                Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
            ),
        )

        val opphørsvedtak = Vedtak.from(revurdering, utbetalingId)

        val brevRevurdering = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            clock = clock,
        ).apply { revurdering.accept(this) }

        val brevVedtak = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            clock = clock,
        ).apply { opphørsvedtak.accept(this) }

        brevRevurdering.brevRequest shouldBe brevVedtak.brevRequest
        brevRevurdering.brevRequest shouldBe LagBrevRequest.Opphørsvedtak(
            person = person,
            beregning = revurdering.beregning,
            behandlingsinformasjon = revurdering.tilRevurdering.behandlingsinformasjon,
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            fritekst = "FRITEKST REVURDERING",
        ).right()
    }

    @Test
    fun `lager request for vedtak som ikke fører til endring`() {
        val utbetalingId = UUID30.randomUUID()
        val søknadsbehandling =
            uavklart.tilVilkårsvurdert(Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt())
                .tilBeregnet(innvilgetBeregning)
                .tilSimulert(simulering)
                .tilAttestering(saksbehandler, "Fritekst!")
                .tilIverksatt(Attestering.Iverksatt(attestant))

        val revurdering = IverksattRevurdering.IngenEndring(
            id = UUID.randomUUID(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021)),
            opprettet = Tidspunkt.now(clock),
            tilRevurdering = Vedtak.fromSøknadsbehandling(søknadsbehandling, utbetalingId),
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("15"),
            beregning = innvilgetBeregning,
            attestering = Attestering.Iverksatt(attestant),
            fritekstTilBrev = "EN FIN FRITEKST",
            revurderingsårsak = Revurderingsårsak(
                Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
            ),
            skalFøreTilBrevutsending = false,
        )

        val vedtakIngenEndring = Vedtak.from(revurdering, clock)

        val brevRevurdering = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            clock = clock,
        ).apply { revurdering.accept(this) }

        val brevVedtak = LagBrevRequestVisitor(
            hentPerson = { person.right() },
            hentNavn = { hentNavn(it) },
            clock = clock,
        ).apply { vedtakIngenEndring.accept(this) }

        val personalia = BrevInnhold.Personalia(
            dato = "01.01.2021",
            fødselsnummer = Fnr(fnr = "12345678901"),
            fornavn = "Tore",
            etternavn = "Strømøy",
        )

        brevRevurdering.brevRequest.map {
            it.right() shouldBe brevVedtak.brevRequest
            it shouldBe LagBrevRequest.VedtakIngenEndring(
                person = person,
                beregning = revurdering.beregning,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
                fritekst = "EN FIN FRITEKST",
                harEktefelle = revurdering.tilRevurdering.behandlingsinformasjon.harEktefelle(),
            )

            it.lagBrevInnhold(personalia) should beOfType<BrevInnhold.VedtakIngenEndring>()
        }
    }

    private val clock = Clock.fixed(1.januar(2021).startOfDay(zoneIdOslo).instant, ZoneOffset.UTC)
    private val person = Person(
        ident = Ident(
            fnr = FnrGenerator.random(),
            aktørId = AktørId(aktørId = "123"),
        ),
        navn = Person.Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy"),
    )

    private val saksbehandler = NavIdentBruker.Saksbehandler("Z123")
    private val saksbehandlerNavn = "saksbehandler"
    private val attestant = NavIdentBruker.Attestant("Z321")
    private val attestantNavn = "attestant"

    private fun hentNavn(navIdentBruker: NavIdentBruker): Either<LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant, String> =
        when (navIdentBruker) {
            is NavIdentBruker.Attestant -> attestantNavn.right()
            is NavIdentBruker.Saksbehandler -> saksbehandlerNavn.right()
        }

    private val innvilgetBeregning = BeregningFactory.ny(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(clock),
        periode = Periode.create(1.januar(2021), 31.desember(2021)),
        sats = Sats.HØY,
        fradrag = listOf(
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 5000.0,
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        ),
        fradragStrategy = FradragStrategy.Enslig,
    )

    private val avslagBeregning = BeregningFactory.ny(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(clock),
        periode = Periode.create(1.januar(2021), 31.desember(2021)),
        sats = Sats.HØY,
        fradrag = listOf(
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 50000.0,
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        ),
        fradragStrategy = FradragStrategy.Enslig,
    )

    private val simulering = Simulering(
        gjelderId = FnrGenerator.random(),
        gjelderNavn = "",
        datoBeregnet = 1.januar(2021),
        nettoBeløp = 0,
        periodeList = listOf(),
    )

    private val uavklart = Søknadsbehandling.Vilkårsvurdert.Uavklart(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(),
        sakId = UUID.randomUUID(),
        saksnummer = Saksnummer(123),
        søknad = mock(),
        oppgaveId = OppgaveId(""),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
        fnr = FnrGenerator.random(),
        fritekstTilBrev = "",
        stønadsperiode = ValgtStønadsperiode(Periode.create(1.januar(2021), 31.desember(2021))),
    )
}
