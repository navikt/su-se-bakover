package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.AVSLÅTT
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.BEREGNET
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.INNVILGET
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.OPPRETTET
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.SIMULERT
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.TIL_ATTESTERING
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.VILKÅRSVURDERT
import no.nav.su.se.bakover.domain.VilkårsvurderingDto.Companion.toDto
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningDto
import no.nav.su.se.bakover.domain.beregning.BeregningsPeriode
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag.Opprettet
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
    private var status: BehandlingsStatus = OPPRETTET
) : PersistentDomainObject<BehandlingPersistenceObserver>(), DtoConvertable<BehandlingDto> {

    private var state = resolveState(status)

    fun status() = status

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

    override fun toDto() = BehandlingDto(
        id = id,
        opprettet = opprettet,
        vilkårsvurderinger = vilkårsvurderinger.toDto(),
        søknad = søknad.toDto(),
        beregning = if (beregninger.isEmpty()) null else gjeldendeBeregning().toDto(),
        status = status,
        oppdrag = gjeldendeOppdrag()
    )

    fun gjeldendeOppdrag() = oppdrag.sortedWith(Opprettet).lastOrNull()

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

    private fun resolveState(status: BehandlingsStatus) = when (status) {
        OPPRETTET, VILKÅRSVURDERT, BEREGNET, SIMULERT, AVSLÅTT, INNVILGET -> TilBehandling() // TODO probably change some of this?
        TIL_ATTESTERING -> TilAttestering()
    }

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

        class BehandlingStateException(msg: String = "Illegal operation for state: ${BehandlingState::class.simpleName}") :
            RuntimeException(msg)
    }

    private fun transition(status: BehandlingsStatus) {
        // TODO maybe check validitiy of transition?
        this.status = persistenceObserver.oppdaterBehandlingStatus(id, status)
        state = resolveState(status)
    }

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
                if (vilkårsvurderingAvslått()) {
                    transition(AVSLÅTT)
                } else {
                    transition(VILKÅRSVURDERT)
                }
            }
            return this@Behandling
        }

        override fun addOppdrag(oppdrag: Oppdrag): Behandling {
            this@Behandling.oppdrag.add(oppdrag)
            transition(SIMULERT)
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
            transition(BEREGNET)
            return this@Behandling
        }

        override fun sendTilAttestering(): Behandling {
            transition(TIL_ATTESTERING)
            return this@Behandling
        }

        override fun attester(): Behandling {
            throw BehandlingState.BehandlingStateException()
        }
    }

    private inner class TilAttestering : BehandlingState {
        override fun opprettVilkårsvurderinger(): Behandling {
            throw BehandlingState.BehandlingStateException()
        }

        override fun oppdaterVilkårsvurderinger(oppdatertListe: List<Vilkårsvurdering>): Behandling {
            throw BehandlingState.BehandlingStateException()
        }

        override fun addOppdrag(oppdrag: Oppdrag): Behandling {
            throw BehandlingState.BehandlingStateException()
        }

        override fun opprettBeregning(fom: LocalDate, tom: LocalDate, sats: Sats, fradrag: List<Fradrag>): Behandling {
            throw BehandlingState.BehandlingStateException()
        }

        override fun sendTilAttestering(): Behandling {
            throw BehandlingState.BehandlingStateException()
        }

        override fun attester(): Behandling {
            return this@Behandling
        }
    }
}

interface BehandlingPersistenceObserver : PersistenceObserver {
    fun opprettVilkårsvurderinger(
        behandlingId: UUID,
        vilkårsvurderinger: List<Vilkårsvurdering>
    ): List<Vilkårsvurdering>

    fun opprettBeregning(behandlingId: UUID, beregning: Beregning): Beregning
    fun oppdaterBehandlingStatus(behandlingId: UUID, status: BehandlingsStatus): BehandlingsStatus
}

data class BehandlingDto(
    val id: UUID,
    val opprettet: Instant,
    val vilkårsvurderinger: List<VilkårsvurderingDto>,
    val søknad: SøknadDto,
    val beregning: BeregningDto?,
    val status: BehandlingsStatus,
    val oppdrag: Oppdrag?
)
