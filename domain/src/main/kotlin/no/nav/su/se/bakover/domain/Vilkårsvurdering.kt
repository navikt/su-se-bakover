package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import java.time.Instant
import java.util.UUID

class Vilkårsvurdering(
    id: UUID = UUID.randomUUID(),
    opprettet: Instant = now(),
    private val vilkår: Vilkår,
    private var begrunnelse: String = "",
    private var status: Status = Status.IKKE_VURDERT
) : PersistentDomainObject<VilkårsvurderingPersistenceObserver>(id, opprettet), DtoConvertable<VilkårsvurderingDto> {

    override fun toDto() = VilkårsvurderingDto(
        id = id,
        vilkår = vilkår,
        begrunnelse = begrunnelse,
        status = status,
        opprettet = opprettet
    )

    enum class Status {
        OK,
        IKKE_OK,
        IKKE_VURDERT
    }

    fun oppdater(vilkårsvurdering: Vilkårsvurdering) {
        this.begrunnelse = vilkårsvurdering.begrunnelse
        this.status = vilkårsvurdering.status
        persistenceObserver.oppdaterVilkårsvurdering(this)
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
        vilkårsvurdering: Vilkårsvurdering
    ): Vilkårsvurdering
}

data class VilkårsvurderingDto(
    val id: UUID,
    val vilkår: Vilkår,
    val begrunnelse: String,
    val status: Vilkårsvurdering.Status,
    val opprettet: Instant
) : Comparable<VilkårsvurderingDto> {
    companion object {
        fun List<Vilkårsvurdering>.toDto() = this.map { it.toDto() }.sorted()
    }

    override fun compareTo(other: VilkårsvurderingDto) = this.vilkår.name.compareTo(other.vilkår.name)
}
