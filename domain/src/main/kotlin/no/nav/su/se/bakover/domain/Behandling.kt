package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.now

import no.nav.su.se.bakover.domain.VilkårsvurderingDto.Companion.toDto
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningDto
import no.nav.su.se.bakover.domain.beregning.BeregningsPeriode
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Behandling constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Instant = now(),
    private val vilkårsvurderinger: MutableList<Vilkårsvurdering> = mutableListOf(),
    private val søknad: Søknad,
    private val beregninger: MutableList<Beregning> = mutableListOf(),
    private val oppdrag: MutableList<Oppdrag> = mutableListOf(),
    private var status: Status.BehandlingsStatus = Status.BehandlingsStatus.OPPRETTET
) : PersistentDomainObject<BehandlingPersistenceObserver>(), DtoConvertable<BehandlingDto> {
    private val stateMachine = StateMachine()
    private var state: BehandlingState = stateMachine.init(status)

    fun status() = status

    override fun toDto() = BehandlingDto(
        id = id,
        opprettet = opprettet,
        vilkårsvurderinger = vilkårsvurderinger.toDto(),
        søknad = søknad.toDto(),
        beregning = if (beregninger.isEmpty()) null else gjeldendeBeregning().toDto(),
        status = status,
        oppdrag = gjeldendeOppdrag()
    )

    fun gjeldendeOppdrag() = oppdrag.sortedWith(Oppdrag.Opprettet).lastOrNull()

    fun opprettVilkårsvurderinger() = state.opprettVilkårsvurderinger()

    fun oppdaterVilkårsvurderinger(oppdatertListe: List<Vilkårsvurdering>) =
        state.oppdaterVilkårsvurderinger(oppdatertListe)

    fun addOppdrag(oppdrag: Oppdrag) = state.addOppdrag(oppdrag)

    fun opprettBeregning(
        fom: LocalDate,
        tom: LocalDate,
        sats: Sats = Sats.HØY,
        fradrag: List<Fradrag> = emptyList()
    ): Behandling = state.opprettBeregning(fom, tom, sats, fradrag)

    // TODO stuff
    fun sendTilAttestering() = state.sendTilAttestering()

    internal data class BehandlingOppdragsinformasjon(
        val behandlingId: UUID,
        val perioder: List<BeregningsPeriode>
    )

    internal fun genererOppdragsinformasjon() = BehandlingOppdragsinformasjon(
        behandlingId = id,
        perioder = gjeldendeBeregning().hentPerioder()
    )

    private fun gjeldendeBeregning(): Beregning = beregninger.toList()
        .sortedWith(Beregning.Opprettet)
        .last()

    override fun equals(other: Any?) = other is Behandling && id == other.id
    override fun hashCode() = id.hashCode()

    private fun vilkårsvurderingComplete() = vilkårsvurderinger.none { !it.vurdert() }
    private fun vilkårsvurderingAvslått() = vilkårsvurderinger.any { it.avslått() }

    internal interface BehandlingState {
        fun opprettVilkårsvurderinger(): Behandling
        fun oppdaterVilkårsvurderinger(oppdatertListe: List<Vilkårsvurdering>): Behandling
        fun addOppdrag(oppdrag: Oppdrag): Behandling
        fun opprettBeregning(
            fom: LocalDate,
            tom: LocalDate,
            sats: Sats = Sats.HØY,
            fradrag: List<Fradrag>
        ): Behandling

        fun sendTilAttestering(): Behandling
        fun attester(): Behandling
    }

    class BehandlingStateException(msg: String = "Illegal operation for state: ${BehandlingState::class.simpleName}") :
        RuntimeException(msg)

    private inner class TilBehandling : BehandlingState {
        override fun opprettVilkårsvurderinger(): Behandling {
            vilkårsvurderinger.addAll(
                persistenceObserver.opprettVilkårsvurderinger(
                    behandlingId = id,
                    vilkårsvurderinger = Vilkår.values().map { Vilkårsvurdering(vilkår = it) }
                )
            )
            return this@Behandling
        }

        override fun oppdaterVilkårsvurderinger(oppdatertListe: List<Vilkårsvurdering>): Behandling {
            oppdatertListe.forEach { oppdatert ->
                vilkårsvurderinger
                    .single { it == oppdatert }
                    .apply { oppdater(oppdatert) }
            }
            if (vilkårsvurderingComplete()) {
                stateMachine.transition(Status.Vilkårsvurdert)
                if (vilkårsvurderingAvslått()) {
                    stateMachine.transition(Status.Avslått)
                }
            }
            return this@Behandling
        }

        override fun addOppdrag(oppdrag: Oppdrag): Behandling {
            this@Behandling.oppdrag.add(oppdrag)
            stateMachine.transition(Status.Simulert)
            return this@Behandling
        }

        override fun opprettBeregning(
            fom: LocalDate,
            tom: LocalDate,
            sats: Sats,
            fradrag: List<Fradrag>
        ): Behandling {
            beregninger.add(
                persistenceObserver.opprettBeregning(
                    behandlingId = id,
                    beregning = Beregning(
                        fom = fom,
                        tom = tom,
                        sats = sats,
                        fradrag = fradrag
                    )
                )
            )
            stateMachine.transition(Status.Beregnet)
            return this@Behandling
        }

        override fun sendTilAttestering(): Behandling {
            stateMachine.transition(Status.TilAttestering)
            return this@Behandling
        }

        override fun attester(): Behandling {
            throw BehandlingStateException()
        }
    }

    private inner class TilAttestering : BehandlingState {
        override fun opprettVilkårsvurderinger(): Behandling {
            throw BehandlingStateException()
        }

        override fun oppdaterVilkårsvurderinger(oppdatertListe: List<Vilkårsvurdering>): Behandling {
            throw BehandlingStateException()
        }

        override fun addOppdrag(oppdrag: Oppdrag): Behandling {
            throw BehandlingStateException()
        }

        override fun opprettBeregning(fom: LocalDate, tom: LocalDate, sats: Sats, fradrag: List<Fradrag>): Behandling {
            throw BehandlingStateException()
        }

        override fun sendTilAttestering(): Behandling {
            throw BehandlingStateException()
        }

        override fun attester(): Behandling {
            return this@Behandling
        }
    }

    private inner class StateMachine {
        fun init(status: Status.BehandlingsStatus) = fromStatus(fromEnumStatus(status))

        private fun fromStatus(status: Status) = when (status) {
            Status.Opprettet, Status.Vilkårsvurdert, Status.Beregnet, Status.Simulert, Status.Innvilget, Status.Avslått -> TilBehandling() // TODO probably change some of this?
            Status.TilAttestering -> TilAttestering()
        }

        private fun fromEnumStatus(status: Status.BehandlingsStatus) = when (status) {
            Status.BehandlingsStatus.OPPRETTET -> Status.Opprettet
            Status.BehandlingsStatus.VILKÅRSVURDERT -> Status.Vilkårsvurdert
            Status.BehandlingsStatus.BEREGNET -> Status.Beregnet
            Status.BehandlingsStatus.SIMULERT -> Status.Simulert
            Status.BehandlingsStatus.TIL_ATTESTERING -> Status.TilAttestering
            Status.BehandlingsStatus.INNVILGET -> Status.Innvilget
            Status.BehandlingsStatus.AVSLÅTT -> Status.Avslått
        }

        fun transition(other: Status) {
            if (fromEnumStatus(status).validTransition(other)) {
                status = persistenceObserver.oppdaterBehandlingStatus(id, other.status)
                state = init(status)
            } else {
                throw BehandlingStateException("Invalid status transition (from: $status to:${other.status})")
            }
        }
    }

    sealed class Status {

        enum class BehandlingsStatus {
            OPPRETTET,
            VILKÅRSVURDERT,
            BEREGNET,
            SIMULERT,

            /*VEDTAKSBREV,*/
            INNVILGET,
            AVSLÅTT,
            TIL_ATTESTERING
        }

        abstract val status: BehandlingsStatus
        abstract val transitions: Set<BehandlingsStatus>
        fun validTransition(other: Status) = transitions.contains(other.status)

        object Opprettet : Status() {
            override val status: BehandlingsStatus = BehandlingsStatus.OPPRETTET
            override val transitions: Set<BehandlingsStatus> = setOf(BehandlingsStatus.VILKÅRSVURDERT)
        }

        object Vilkårsvurdert : Status() {
            override val status: BehandlingsStatus = BehandlingsStatus.VILKÅRSVURDERT
            override val transitions: Set<BehandlingsStatus> = setOf(
                BehandlingsStatus.OPPRETTET,
                BehandlingsStatus.BEREGNET, BehandlingsStatus.AVSLÅTT
            )
        }

        object Beregnet : Status() {
            override val status: BehandlingsStatus = BehandlingsStatus.BEREGNET
            override val transitions: Set<BehandlingsStatus> = setOf(
                BehandlingsStatus.VILKÅRSVURDERT,
                BehandlingsStatus.SIMULERT
            )
        }

        object Simulert : Status() {
            override val status: BehandlingsStatus = BehandlingsStatus.SIMULERT
            override val transitions: Set<BehandlingsStatus> = setOf(
                BehandlingsStatus.BEREGNET,
                BehandlingsStatus.TIL_ATTESTERING
            )
        }

        object TilAttestering : Status() {
            override val status: BehandlingsStatus = BehandlingsStatus.TIL_ATTESTERING
            override val transitions: Set<BehandlingsStatus> = setOf()
        }

        object Innvilget : Status() {
            override val status: BehandlingsStatus = BehandlingsStatus.INNVILGET
            override val transitions: Set<BehandlingsStatus> = setOf()
        }

        object Avslått : Status() {
            override val status: BehandlingsStatus = BehandlingsStatus.AVSLÅTT
            override val transitions: Set<BehandlingsStatus> = setOf(BehandlingsStatus.VILKÅRSVURDERT)
        }
    }
}

interface BehandlingPersistenceObserver : PersistenceObserver {
    fun opprettVilkårsvurderinger(
        behandlingId: UUID,
        vilkårsvurderinger: List<Vilkårsvurdering>
    ): List<Vilkårsvurdering>

    fun opprettBeregning(behandlingId: UUID, beregning: Beregning): Beregning
    fun oppdaterBehandlingStatus(
        behandlingId: UUID,
        status: Behandling.Status.BehandlingsStatus
    ): Behandling.Status.BehandlingsStatus
}

data class BehandlingDto(
    val id: UUID,
    val opprettet: Instant,
    val vilkårsvurderinger: List<VilkårsvurderingDto>,
    val søknad: SøknadDto,
    val beregning: BeregningDto?,
    val status: Behandling.Status.BehandlingsStatus,
    val oppdrag: Oppdrag?
)
