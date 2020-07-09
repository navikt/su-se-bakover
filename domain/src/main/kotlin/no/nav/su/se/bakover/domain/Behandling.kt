package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.domain.dto.DtoConvertable
import java.time.Instant
import java.util.UUID

class Behandling constructor(
    id: UUID = UUID.randomUUID(),
    opprettet: Instant = Instant.now(),
    private val vilkårsvurderinger: MutableList<Vilkårsvurdering> = mutableListOf()
) : PersistentDomainObject<BehandlingPersistenceObserver>(id, opprettet), DtoConvertable<BehandlingDto> {

    override fun toDto() = BehandlingDto(
        id = id,
        opprettet = opprettet,
        vilkårsvurderinger = vilkårsvurderinger.map { it.toDto() }
    )

    fun opprettVilkårsvurderinger(): MutableList<Vilkårsvurdering> {
        vilkårsvurderinger.addAll(
            persistenceObserver.opprettVilkårsvurderinger(
                behandlingId = id,
                vilkårsvurderinger = Vilkår.values().map { Vilkårsvurdering(vilkår = it) })
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

    override fun equals(other: Any?) = other is Behandling && id == other.id
    override fun hashCode() = id.hashCode()
}

interface BehandlingPersistenceObserver : PersistenceObserver {
    fun opprettVilkårsvurderinger(
        behandlingId: UUID,
        vilkårsvurderinger: List<Vilkårsvurdering>
    ): List<Vilkårsvurdering>
}

data class BehandlingDto(
    val id: UUID,
    val opprettet: Instant,
    val vilkårsvurderinger: List<VilkårsvurderingDto>
)
