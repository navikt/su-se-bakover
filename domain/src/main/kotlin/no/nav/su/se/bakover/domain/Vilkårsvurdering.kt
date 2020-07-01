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

    fun toDto() = VilkårsvurderingDto(id, vilkår, begrunnelse, status)

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

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + vilkår.hashCode()
        return result
    }
}

enum class Vilkår {
    UFØRHET, FLYKTNING, OPPHOLDSTILLATELSE, PERSONLIG_OPPMØTE, FORMUE
}

interface VilkårsvurderingPersistenceObserver : PersistenceObserver {
    fun oppdaterVilkårsvurdering(
        vilkårsvurderingsId: Long,
        begrunnelse: String,
        status: Vilkårsvurdering.Status
    ): Vilkårsvurdering
}

data class VilkårsvurderingDto(
    val id: Long,
    val vilkår: Vilkår,
    val begrunnelse: String,
    val status: Vilkårsvurdering.Status
)
