package no.nav.su.se.bakover.domain

import arrow.core.Either
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.VilkårsvurderingDto.Companion.toDto
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Behandling(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Instant = now(),
    private val vilkårsvurderinger: MutableList<Vilkårsvurdering> = mutableListOf(),
    private val søknad: Søknad,
    private var beregning: Beregning? = null,
    private var utbetaling: Utbetaling? = null,
    private var status: BehandlingsStatus = BehandlingsStatus.OPPRETTET,
    private var attestant: Attestant? = null,
    val sakId: UUID
) : PersistentDomainObject<BehandlingPersistenceObserver>(), DtoConvertable<BehandlingDto> {
    private var tilstand: Tilstand = resolve(status)

    fun status() = tilstand.status
    fun attestant() = attestant

    override fun toDto() = BehandlingDto(
        id = id,
        opprettet = opprettet,
        vilkårsvurderinger = vilkårsvurderinger.toDto(),
        søknad = søknad,
        beregning = beregning,
        status = tilstand.status,
        utbetaling = utbetaling,
        attestant = attestant,
        sakId = sakId
    )

    fun utbetaling() = utbetaling

    private fun resolve(status: BehandlingsStatus): Tilstand = when (status) {
        BehandlingsStatus.OPPRETTET -> Opprettet()
        BehandlingsStatus.VILKÅRSVURDERT_INNVILGET -> Vilkårsvurdert().Innvilget()
        BehandlingsStatus.VILKÅRSVURDERT_AVSLAG -> Vilkårsvurdert().Avslag()
        BehandlingsStatus.BEREGNET -> Beregnet()
        BehandlingsStatus.SIMULERT -> Simulert()
        BehandlingsStatus.TIL_ATTESTERING_INNVILGET -> TilAttestering().Innvilget()
        BehandlingsStatus.TIL_ATTESTERING_AVSLAG -> TilAttestering().Avslag()
        BehandlingsStatus.ATTESTERT_INNVILGET -> Attestert().Innvilget()
        BehandlingsStatus.ATTESTERT_AVSLAG -> Attestert().Avslag()
    }

    fun opprettVilkårsvurderinger(): Behandling {
        tilstand.opprettVilkårsvurderinger()
        return this
    }

    fun oppdaterVilkårsvurderinger(oppdatertListe: List<Vilkårsvurdering>): Behandling {
        tilstand.oppdaterVilkårsvurderinger(oppdatertListe)
        return this
    }

    fun opprettBeregning(
        fom: LocalDate,
        tom: LocalDate,
        sats: Sats = Sats.HØY,
        fradrag: List<Fradrag> = emptyList()
    ): Behandling {
        tilstand.opprettBeregning(fom, tom, sats, fradrag)
        return this
    }

    fun simuler(simuleringClient: SimuleringClient): Either<SimuleringFeilet, Behandling> {
        return tilstand.simuler(simuleringClient)
    }

    fun sendTilAttestering(aktørId: AktørId, oppgave: OppgaveClient): Either<KunneIkkeOppretteOppgave, Behandling> {
        return tilstand.sendTilAttestering(aktørId, oppgave)
    }

    fun attester(attestant: Attestant): Behandling {
        tilstand.attester(attestant)
        return this
    }

    // TODO should this be triggered by a web-endpoint, or just invoked in the background?
    fun sendTilUtbetaling(publisher: UtbetalingPublisher): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Behandling> {
        return tilstand.sendTilUtbetaling(publisher)
    }

    override fun equals(other: Any?) = other is Behandling && id == other.id
    override fun hashCode() = id.hashCode()

    private fun List<Vilkårsvurdering>.alleVurdert() = none { !it.vurdert() }
    private fun List<Vilkårsvurdering>.harAvslag() = any { it.avslått() }
    private fun List<Vilkårsvurdering>.innvilget() = alleVurdert() && !harAvslag()

    interface Tilstand {
        val status: BehandlingsStatus
        fun opprettVilkårsvurderinger() {
            throw TilstandException(status, this::opprettVilkårsvurderinger.toString())
        }

        fun oppdaterVilkårsvurderinger(oppdatertListe: List<Vilkårsvurdering>) {
            throw TilstandException(status, this::oppdaterVilkårsvurderinger.toString())
        }

        fun opprettBeregning(
            fom: LocalDate,
            tom: LocalDate,
            sats: Sats = Sats.HØY,
            fradrag: List<Fradrag>
        ) {
            throw TilstandException(status, this::opprettBeregning.toString())
        }

        fun simuler(simuleringClient: SimuleringClient): Either<SimuleringFeilet, Behandling> {
            throw TilstandException(status, this::simuler.toString())
        }

        fun sendTilAttestering(aktørId: AktørId, oppgave: OppgaveClient): Either<KunneIkkeOppretteOppgave, Behandling> {
            throw TilstandException(status, this::sendTilAttestering.toString())
        }

        fun attester(attestant: Attestant) {
            throw TilstandException(status, this::attester.toString())
        }

        fun sendTilUtbetaling(publisher: UtbetalingPublisher): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Behandling> =
            throw TilstandException(status, this::sendTilUtbetaling.toString())
    }

    private fun nyTilstand(target: Tilstand): Tilstand {
        status = persistenceObserver.oppdaterBehandlingStatus(id, target.status)
        tilstand = resolve(status)
        return tilstand
    }

    private inner class Opprettet : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.OPPRETTET

        override fun opprettVilkårsvurderinger() {
            if (vilkårsvurderinger.isNotEmpty()) throw TilstandException(
                status,
                this::opprettVilkårsvurderinger.toString()
            )
            vilkårsvurderinger.addAll(
                persistenceObserver.opprettVilkårsvurderinger(
                    behandlingId = id,
                    vilkårsvurderinger = Vilkår.values().map { Vilkårsvurdering(vilkår = it) }
                )
            )
        }

        override fun oppdaterVilkårsvurderinger(oppdatertListe: List<Vilkårsvurdering>) {
            oppdatertListe.forEach { oppdatert ->
                vilkårsvurderinger
                    .single { it == oppdatert }
                    .apply { oppdater(oppdatert) }
            }
            if (vilkårsvurderinger.innvilget()) {
                nyTilstand(Vilkårsvurdert().Innvilget())
            } else {
                if (vilkårsvurderinger.alleVurdert() && vilkårsvurderinger.harAvslag()) {
                    nyTilstand(Vilkårsvurdert().Avslag())
                }
            }
        }
    }

    private open inner class Vilkårsvurdert : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.VILKÅRSVURDERT_INNVILGET

        override fun oppdaterVilkårsvurderinger(oppdatertListe: List<Vilkårsvurdering>) {
            nyTilstand(Opprettet()).oppdaterVilkårsvurderinger(oppdatertListe)
        }

        inner class Innvilget : Vilkårsvurdert() {
            override fun opprettBeregning(fom: LocalDate, tom: LocalDate, sats: Sats, fradrag: List<Fradrag>) {
                this@Behandling.beregning = persistenceObserver.opprettBeregning(
                    behandlingId = id,
                    beregning = Beregning(
                        fom = fom,
                        tom = tom,
                        sats = sats,
                        fradrag = fradrag
                    )
                )
                nyTilstand(Beregnet())
            }
        }

        inner class Avslag : Vilkårsvurdert() {
            override val status: BehandlingsStatus = BehandlingsStatus.VILKÅRSVURDERT_AVSLAG

            override fun sendTilAttestering(
                aktørId: AktørId,
                oppgave: OppgaveClient
            ): Either<KunneIkkeOppretteOppgave, Behandling> = oppgave.opprettOppgave(
                OppgaveConfig.Attestering(
                    sakId = sakId.toString(),
                    aktørId = aktørId
                )
            ).map {
                nyTilstand(TilAttestering().Avslag())
                this@Behandling
            }
        }
    }

    private inner class Beregnet : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.BEREGNET

        override fun opprettBeregning(fom: LocalDate, tom: LocalDate, sats: Sats, fradrag: List<Fradrag>) {
            nyTilstand(Vilkårsvurdert()).opprettBeregning(fom, tom, sats, fradrag)
        }

        override fun oppdaterVilkårsvurderinger(oppdatertListe: List<Vilkårsvurdering>) {
            nyTilstand(Opprettet()).oppdaterVilkårsvurderinger(oppdatertListe)
        }

        override fun simuler(simuleringClient: SimuleringClient): Either<SimuleringFeilet, Behandling> {
            val oppdrag = persistenceObserver.hentOppdrag(sakId)
            val utbetalingTilSimulering = oppdrag.generererUtbetaling(id, beregning!!.hentPerioder())
            return simuleringClient.simulerUtbetaling(
                oppdrag,
                utbetalingTilSimulering,
                persistenceObserver.hentFnr(sakId)
            ).map { simulering ->
                this@Behandling.utbetaling = oppdrag.opprettUtbetaling(utbetalingTilSimulering).also {
                    it.addSimulering(simulering)
                }
                nyTilstand(Simulert())
                this@Behandling
            }
        }
    }

    private inner class Simulert : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.SIMULERT

        override fun sendTilAttestering(
            aktørId: AktørId,
            oppgave: OppgaveClient
        ): Either<KunneIkkeOppretteOppgave, Behandling> = oppgave.opprettOppgave(
            OppgaveConfig.Attestering(
                sakId = sakId.toString(),
                aktørId = aktørId
            )
        ).map {
            nyTilstand(TilAttestering().Innvilget())
            this@Behandling
        }

        override fun opprettBeregning(fom: LocalDate, tom: LocalDate, sats: Sats, fradrag: List<Fradrag>) {
            nyTilstand(Vilkårsvurdert().Innvilget()).opprettBeregning(fom, tom, sats, fradrag)
        }

        override fun oppdaterVilkårsvurderinger(oppdatertListe: List<Vilkårsvurdering>) {
            nyTilstand(Opprettet()).oppdaterVilkårsvurderinger(oppdatertListe)
        }

        override fun simuler(simuleringClient: SimuleringClient): Either<SimuleringFeilet, Behandling> {
            return nyTilstand(Beregnet()).simuler(simuleringClient)
        }
    }

    private open inner class TilAttestering : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.TIL_ATTESTERING_INNVILGET

        inner class Innvilget : TilAttestering() {
            override fun attester(attestant: Attestant) {
                this@Behandling.attestant = persistenceObserver.attester(id, attestant)
                nyTilstand(Attestert().Innvilget())
            }
        }

        inner class Avslag : TilAttestering() {
            override val status: BehandlingsStatus = BehandlingsStatus.TIL_ATTESTERING_AVSLAG
            override fun attester(attestant: Attestant) {
                this@Behandling.attestant = persistenceObserver.attester(id, attestant)
                nyTilstand(Attestert().Avslag())
            }
        }
    }

    private open inner class Attestert : Tilstand {
        override val status: BehandlingsStatus = BehandlingsStatus.ATTESTERT_INNVILGET

        inner class Innvilget : Attestert() {
            override fun sendTilUtbetaling(publisher: UtbetalingPublisher): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Behandling> {
                return publisher.publish(
                    oppdrag = persistenceObserver.hentOppdrag(sakId),
                    utbetaling = utbetaling!!,
                    oppdragGjelder = persistenceObserver.hentFnr(sakId)
                ).mapLeft {
                    utbetaling!!.addOppdragsmelding(
                        Oppdragsmelding(
                            Oppdragsmelding.Oppdragsmeldingstatus.FEIL,
                            it.originalMelding
                        )
                    )
                    it
                }.map {
                    utbetaling!!.addOppdragsmelding(
                        Oppdragsmelding(
                            Oppdragsmelding.Oppdragsmeldingstatus.SENDT,
                            it
                        )
                    )
                    this@Behandling
                }
            }
        }

        inner class Avslag : Attestert() {
            override val status: BehandlingsStatus = BehandlingsStatus.ATTESTERT_AVSLAG
        }
    }

    enum class BehandlingsStatus {
        OPPRETTET,
        VILKÅRSVURDERT_INNVILGET,
        VILKÅRSVURDERT_AVSLAG,
        BEREGNET,
        SIMULERT,
        TIL_ATTESTERING_INNVILGET,
        TIL_ATTESTERING_AVSLAG,
        ATTESTERT_INNVILGET,
        ATTESTERT_AVSLAG,
    }

    class TilstandException(
        val state: BehandlingsStatus,
        val operation: String,
        val msg: String = "Illegal operation: $operation for state: $state"
    ) :
        RuntimeException(msg)
}

interface BehandlingPersistenceObserver : PersistenceObserver {
    fun opprettVilkårsvurderinger(
        behandlingId: UUID,
        vilkårsvurderinger: List<Vilkårsvurdering>
    ): List<Vilkårsvurdering>

    fun opprettBeregning(behandlingId: UUID, beregning: Beregning): Beregning
    fun oppdaterBehandlingStatus(
        behandlingId: UUID,
        status: Behandling.BehandlingsStatus
    ): Behandling.BehandlingsStatus

    fun hentOppdrag(sakId: UUID): Oppdrag
    fun hentFnr(sakId: UUID): Fnr
    fun attester(behandlingId: UUID, attestant: Attestant): Attestant
}

data class BehandlingDto(
    val id: UUID,
    val opprettet: Instant,
    val vilkårsvurderinger: List<VilkårsvurderingDto>,
    val søknad: Søknad,
    val beregning: Beregning?,
    val status: Behandling.BehandlingsStatus,
    val utbetaling: Utbetaling?,
    val attestant: Attestant?,
    val sakId: UUID
)
