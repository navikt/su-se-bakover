package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.domain.dto.DtoConvertable

class Vilkårsvurdering(
    id: Long,
    private val vilkår: Vilkår,
    private var begrunnelse: String,
    private var status: Status
) : PersistentDomainObject<VilkårsvurderingPersistenceObserver>(id), DtoConvertable<VilkårsvurderingDto> {

    override fun toDto() = VilkårsvurderingDto(id, vilkår, begrunnelse, status)

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
    UFØRHET, FLYKTNING, OPPHOLDSTILLATELSE, PERSONLIG_OPPMØTE, FORMUE, BOR_OG_OPPHOLDER_SEG_I_NORGE
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
) {
    companion object {
        fun List<Vilkårsvurdering>.toDto() = this.map { it.toDto() }
    }
}
