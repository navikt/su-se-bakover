package no.nav.su.se.bakover.domain.behandling

import arrow.core.getOrHandle
import arrow.core.nonEmptyListOf
import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.grunnlag.FastOppholdINorgeGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.FlyktningGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.InstitusjonsoppholdGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFastOppholdINorge
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFlyktning
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeInstitusjonsopphold
import java.time.Clock
import java.util.UUID

data class Behandlingsinformasjon(
    val flyktning: Flyktning? = null,
    val fastOppholdINorge: FastOppholdINorge? = null,
    val institusjonsopphold: Institusjonsopphold? = null,
) {
    @JsonIgnore
    val vilkår = listOf(
        flyktning,
        fastOppholdINorge,
        institusjonsopphold,
    )

    fun patch(
        b: Behandlingsinformasjon,
    ) = Behandlingsinformasjon(
        flyktning = b.flyktning ?: this.flyktning,
        fastOppholdINorge = b.fastOppholdINorge ?: this.fastOppholdINorge,
        institusjonsopphold = b.institusjonsopphold ?: this.institusjonsopphold,
    )

    abstract class Base {
        abstract fun erVilkårOppfylt(): Boolean
        abstract fun erVilkårIkkeOppfylt(): Boolean
    }

    data class Flyktning(
        val status: Status,
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            Uavklart
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt

        fun tilVilkår(
            stønadsperiode: Stønadsperiode,
            clock: Clock,
        ): FlyktningVilkår {
            return when (status) {
                Status.VilkårOppfylt,
                Status.VilkårIkkeOppfylt,
                -> {
                    FlyktningVilkår.Vurdert.tryCreate(
                        vurderingsperioder = nonEmptyListOf(
                            VurderingsperiodeFlyktning.tryCreate(
                                id = UUID.randomUUID(),
                                opprettet = Tidspunkt.now(clock),
                                vurdering = when (erVilkårOppfylt()) {
                                    true -> Vurdering.Innvilget
                                    false -> Vurdering.Avslag
                                },
                                grunnlag = FlyktningGrunnlag.tryCreate(
                                    id = UUID.randomUUID(),
                                    opprettet = Tidspunkt.now(clock),
                                    periode = stønadsperiode.periode,
                                ).getOrHandle {
                                    throw IllegalArgumentException("Kunne ikke instansiere ${FlyktningGrunnlag::class.simpleName}. Melding: $it")
                                },
                                vurderingsperiode = stønadsperiode.periode,
                            ).getOrHandle {
                                throw IllegalArgumentException("Kunne ikke instansiere ${VurderingsperiodeFlyktning::class.simpleName}. Melding: $it")
                            },
                        ),
                    ).getOrHandle {
                        throw IllegalArgumentException("Kunne ikke instansiere ${FlyktningVilkår.Vurdert::class.simpleName}. Melding: $it")
                    }
                }
                Status.Uavklart -> FlyktningVilkår.IkkeVurdert
            }
        }
    }

    data class FastOppholdINorge(
        val status: Status,
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            Uavklart
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt

        fun tilVilkår(
            stønadsperiode: Stønadsperiode,
            clock: Clock,
        ): FastOppholdINorgeVilkår {
            return when (status) {
                Status.VilkårOppfylt,
                Status.VilkårIkkeOppfylt,
                -> {
                    FastOppholdINorgeVilkår.Vurdert.tryCreate(
                        vurderingsperioder = nonEmptyListOf(
                            VurderingsperiodeFastOppholdINorge.tryCreate(
                                id = UUID.randomUUID(),
                                opprettet = Tidspunkt.now(clock),
                                vurdering = when (erVilkårOppfylt()) {
                                    true -> Vurdering.Innvilget
                                    false -> Vurdering.Avslag
                                },
                                grunnlag = FastOppholdINorgeGrunnlag.tryCreate(
                                    id = UUID.randomUUID(),
                                    opprettet = Tidspunkt.now(clock),
                                    periode = stønadsperiode.periode,
                                ).getOrHandle {
                                    throw IllegalArgumentException("Kunne ikke instansiere ${FastOppholdINorgeGrunnlag::class.simpleName}. Melding: $it")
                                },
                                vurderingsperiode = stønadsperiode.periode,
                            ).getOrHandle {
                                throw IllegalArgumentException("Kunne ikke instansiere ${VurderingsperiodeFastOppholdINorge::class.simpleName}. Melding: $it")
                            },
                        ),
                    ).getOrHandle {
                        throw IllegalArgumentException("Kunne ikke instansiere ${FastOppholdINorgeVilkår.Vurdert::class.simpleName}. Melding: $it")
                    }
                }
                Status.Uavklart -> FastOppholdINorgeVilkår.IkkeVurdert
            }
        }
    }

    data class Institusjonsopphold(
        val status: Status,
    ) : Base() {
        enum class Status {
            VilkårOppfylt,
            VilkårIkkeOppfylt,
            Uavklart,
        }

        override fun erVilkårOppfylt(): Boolean = status == Status.VilkårOppfylt
        override fun erVilkårIkkeOppfylt(): Boolean = status == Status.VilkårIkkeOppfylt

        fun tilVilkår(
            stønadsperiode: Stønadsperiode,
            clock: Clock,
        ): InstitusjonsoppholdVilkår {
            return when (status) {
                Status.VilkårOppfylt,
                Status.VilkårIkkeOppfylt,
                -> {
                    InstitusjonsoppholdVilkår.Vurdert.tryCreate(
                        vurderingsperioder = nonEmptyListOf(
                            VurderingsperiodeInstitusjonsopphold.tryCreate(
                                id = UUID.randomUUID(),
                                opprettet = Tidspunkt.now(clock),
                                vurdering = when (erVilkårOppfylt()) {
                                    true -> Vurdering.Innvilget
                                    false -> Vurdering.Avslag
                                },
                                grunnlag = InstitusjonsoppholdGrunnlag.tryCreate(
                                    id = UUID.randomUUID(),
                                    opprettet = Tidspunkt.now(clock),
                                    periode = stønadsperiode.periode,
                                ).getOrHandle {
                                    throw IllegalArgumentException("Kunne ikke instansiere ${InstitusjonsoppholdGrunnlag::class.simpleName}. Melding: $it")
                                },
                                vurderingsperiode = stønadsperiode.periode,
                            ).getOrHandle {
                                throw IllegalArgumentException("Kunne ikke instansiere ${VurderingsperiodeInstitusjonsopphold::class.simpleName}. Melding: $it")
                            },
                        ),
                    ).getOrHandle {
                        throw IllegalArgumentException("Kunne ikke instansiere ${InstitusjonsoppholdVilkår.Vurdert::class.simpleName}. Melding: $it")
                    }
                }
                Status.Uavklart -> InstitusjonsoppholdVilkår.IkkeVurdert
            }
        }
    }

    companion object {
        fun lagTomBehandlingsinformasjon() = Behandlingsinformasjon(
            flyktning = null,
            fastOppholdINorge = null,
            institusjonsopphold = null,
        )
    }
}
