package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.AVSLÅTT
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.INNVILGET
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.VILKÅRSVURDERING
import no.nav.su.se.bakover.domain.Behandling.BehandlingsStatus.BEREGNING
import no.nav.su.se.bakover.domain.Vilkårsvurdering.Status.IKKE_OK
import no.nav.su.se.bakover.domain.Vilkårsvurdering.Status.IKKE_VURDERT
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningDto
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class Behandling constructor(
    id: UUID = UUID.randomUUID(),
    opprettet: Instant = Instant.now(),
    private val vilkårsvurderinger: MutableList<Vilkårsvurdering> = mutableListOf(),
    private val søknad: Søknad,
    private val beregninger: MutableList<Beregning> = mutableListOf()
) : PersistentDomainObject<BehandlingPersistenceObserver>(id, opprettet), DtoConvertable<BehandlingDto> {

    enum class BehandlingsStatus {
        VILKÅRSVURDERING,
        BEREGNING,
        /*SIMULERING, */
        /*VEDTAKSBREV,*/
        INNVILGET,
        AVSLÅTT
    }
    override fun toDto() = BehandlingDto(
        id = id,
        opprettet = opprettet,
        vilkårsvurderinger = vilkårsvurderinger.map { it.toDto() },
        søknad = søknad.toDto(),
        beregning = if (beregninger.isEmpty()) null else gjeldendeBeregning().toDto(),
        status = utledStatus()
    )

    private fun utledStatus(): BehandlingsStatus {
        return when {
            vilkårsvurderinger.isEmpty() -> VILKÅRSVURDERING
            vilkårsvurderinger.any { it.toDto().status == IKKE_OK } -> AVSLÅTT
            vilkårsvurderinger.any { it.toDto().status == IKKE_VURDERT } -> VILKÅRSVURDERING
            beregninger.isEmpty() -> BEREGNING
            else -> INNVILGET
        }
    }

    fun opprettVilkårsvurderinger(): MutableList<Vilkårsvurdering> {
        vilkårsvurderinger.addAll(
            persistenceObserver.opprettVilkårsvurderinger(
                behandlingId = id,
                vilkårsvurderinger = Vilkår.values().map { Vilkårsvurdering(vilkår = it) }
            )
        )
        return vilkårsvurderinger
    }

    fun oppdaterVilkårsvurderinger(oppdatertListe: List<Vilkårsvurdering>): List<Vilkårsvurdering> {
        oppdatertListe.forEach { oppdatert ->
            vilkårsvurderinger
                .single { it == oppdatert }
                .apply { oppdater(oppdatert) }
        }
        return vilkårsvurderinger
    }

    fun opprettBeregning(
        fom: LocalDate,
        tom: LocalDate,
        sats: Sats = Sats.HØY,
        fradrag: List<Fradrag> = emptyList()
    ): Beregning {
        val beregning = persistenceObserver.opprettBeregning(
            behandlingId = id,
            beregning = Beregning(
                fom = fom,
                tom = tom,
                sats = sats,
                fradrag = fradrag
            )
        )
        beregninger.add(beregning)
        return beregning
    }

    private fun gjeldendeBeregning(): Beregning = beregninger.toList()
        .sortedWith(Beregning.Opprettet)
        .last()

    override fun equals(other: Any?) = other is Behandling && id == other.id
    override fun hashCode() = id.hashCode()
}

interface BehandlingPersistenceObserver : PersistenceObserver {
    fun opprettVilkårsvurderinger(
        behandlingId: UUID,
        vilkårsvurderinger: List<Vilkårsvurdering>
    ): List<Vilkårsvurdering>

    fun opprettBeregning(behandlingId: UUID, beregning: Beregning): Beregning
}

data class BehandlingDto(
    val id: UUID,
    val opprettet: Instant,
    val vilkårsvurderinger: List<VilkårsvurderingDto>,
    val søknad: SøknadDto,
    val beregning: BeregningDto?,
    val status: BehandlingsStatus
)
