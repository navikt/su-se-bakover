package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.domain.dto.DtoConvertable

class Behandling constructor(
    id: Long,
    private val vilkårsvurderinger: MutableList<Vilkårsvurdering> = mutableListOf()
) : PersistentDomainObject<BehandlingPersistenceObserver>(id), DtoConvertable<BehandlingDto> {

    override fun toDto() = BehandlingDto(id, vilkårsvurderinger.map { it.toDto() })

    fun opprettVilkårsvurderinger(): MutableList<Vilkårsvurdering> {
        vilkårsvurderinger.addAll(persistenceObserver.opprettVilkårsvurderinger(id, Vilkår.values().toList()))
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
        behandlingId: Long,
        vilkår: List<Vilkår>
    ): List<Vilkårsvurdering>
}

data class BehandlingDto(
    val id: Long,
    val vilkårsvurderinger: List<VilkårsvurderingDto>
)
