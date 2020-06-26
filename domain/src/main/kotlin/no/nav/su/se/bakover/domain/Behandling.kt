package no.nav.su.se.bakover.domain

class Behandling constructor(
    private val id: Long,
    private val vilkårsvurderinger: MutableList<Vilkårsvurdering> = mutableListOf()
) {
    private val observers: MutableList<BehandlingPersistenceObserver> = mutableListOf()
    fun addObserver(observer: BehandlingPersistenceObserver) = observers.add(observer)
    fun toJson() = """
        {
            "id": $id,
            "vilkårsvurderinger": ${vilkårsvurderingerAsJsonList()}
        }
    """.trimIndent()

    private fun vilkårsvurderingerAsJsonList(): String = "[ ${vilkårsvurderinger.joinToString(",") { it.toJson() }} ]"

    fun opprettVilkårsvurderinger(): MutableList<Vilkårsvurdering> {
        val persistent = observers.first().opprettVilkårsvurderinger(id, listOf(Vilkår.UFØRE))
        this.vilkårsvurderinger.addAll(persistent)
        return vilkårsvurderinger
    }
}

interface BehandlingPersistenceObserver {
    fun opprettVilkårsvurderinger(
        behandlingId: Long,
        vilkår: List<Vilkår>
    ): List<Vilkårsvurdering>
}
