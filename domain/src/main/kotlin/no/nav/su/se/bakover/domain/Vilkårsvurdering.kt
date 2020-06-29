package no.nav.su.se.bakover.domain

class Vilkårsvurdering(
    id: Long,
    private val vilkår: Vilkår,
    private var begrunnelse: String,
    private var status: Status
) : PersistentDomainObject<VilkårsvurderingPersistenceObserver>(id) {
    //language=JSON
    fun toJson() = """
        {
            "id": $id,
            "vilkår": "$vilkår",
            "begrunnelse" : "$begrunnelse",
            "status": "$status"
        }
    """.trimIndent()

    enum class Status {
        OK,
        IKKE_OK,
        IKKE_VURDERT
    }

    fun oppdater(vilkårsvurdering: Vilkårsvurdering) {
        this.begrunnelse = vilkårsvurdering.begrunnelse
        this.status = vilkårsvurdering.status
        persistenceObserver.oppdaterVilkårsvurdering(id, begrunnelse, status)
    }

    override fun equals(other: Any?) =
        other is Vilkårsvurdering && id == other.id && vilkår == other.vilkår
}

enum class Vilkår {
    UFØRE
}

interface  lol {
    fun tull()
}

interface VilkårsvurderingPersistenceObserver : PersistenceObserver {
    fun oppdaterVilkårsvurdering(
        vilkårsvurderingsId: Long,
        begrunnelse: String,
        status: Vilkårsvurdering.Status
    ): Vilkårsvurdering
}

