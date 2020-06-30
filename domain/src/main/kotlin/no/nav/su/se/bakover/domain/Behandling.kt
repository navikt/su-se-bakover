package no.nav.su.se.bakover.domain

class Behandling constructor(
    id: Long,
    private val vilkårsvurderinger: MutableList<Vilkårsvurdering> = mutableListOf()
) : PersistentDomainObject<BehandlingPersistenceObserver>(id) {
    fun toJson() = """
        {
            "id": $id,
            "vilkårsvurderinger": ${vilkårsvurderingerAsJsonList()}
        }
    """.trimIndent()

    private fun vilkårsvurderingerAsJsonList(): String = "[ ${vilkårsvurderinger.joinToString(",") { it.toJson() }} ]"

    fun toDto() = BehandlingDto(
        id,
        vilkårsvurderinger.map {
            it.toDto().formatter()
        }.toMap()
    )

    fun opprettVilkårsvurderinger(): MutableList<Vilkårsvurdering> {
        vilkårsvurderinger.addAll(persistenceObserver.opprettVilkårsvurderinger(id, listOf(Vilkår.UFØRE)))
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
    val vilkårsvurderinger: Map<Vilkår, VilkårsvurderingDto.Value> = emptyMap()
)
