package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.now
import java.time.Instant
import java.util.UUID

data class Vilkårsvurdering(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Instant = now(),
    val vilkår: Vilkår,
    private var begrunnelse: String = "",
    private var status: Status = Status.IKKE_VURDERT
) : PersistentDomainObject<VilkårsvurderingPersistenceObserver>() {

    fun begrunnelse() = begrunnelse
    fun status() = status

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

    fun vurdert() = status != Status.IKKE_VURDERT
    fun oppfylt() = status == Status.OK
    fun avslått() = status == Status.IKKE_OK

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
