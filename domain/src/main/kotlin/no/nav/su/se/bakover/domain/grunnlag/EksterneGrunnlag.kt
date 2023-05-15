package no.nav.su.se.bakover.domain.grunnlag

import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import java.util.UUID

sealed interface EksterneGrunnlag {
    fun leggTilSkatt(skatt: EksterneGrunnlagSkatt.Hentet): EksterneGrunnlag
    fun fjernEps(): EksterneGrunnlag

    val skatt: EksterneGrunnlagSkatt
}

data class StøtterHentingAvEksternGrunnlag(
    override val skatt: EksterneGrunnlagSkatt,
) : EksterneGrunnlag {

    override fun leggTilSkatt(skatt: EksterneGrunnlagSkatt.Hentet): EksterneGrunnlag = this.copy(skatt = skatt)
    override fun fjernEps(): EksterneGrunnlag = this.copy(skatt = skatt.fjernEps())

    companion object {
        fun ikkeHentet(): EksterneGrunnlag = StøtterHentingAvEksternGrunnlag(
            skatt = EksterneGrunnlagSkatt.IkkeHentet,
        )
    }
}

sealed interface EksterneGrunnlagSkatt {
    fun fjernEps(): EksterneGrunnlagSkatt

    object IkkeHentet : EksterneGrunnlagSkatt {
        override fun fjernEps(): EksterneGrunnlagSkatt = this
    }

    data class Hentet(
        val søkers: SkattegrunnlagMedId,
        val eps: SkattegrunnlagMedId?,
    ) : EksterneGrunnlagSkatt {

        override fun fjernEps(): EksterneGrunnlagSkatt = this.copy(eps = null)

        companion object {
            data class EksternGrunnlagSkattRequest(
                val søkers: Skattegrunnlag,
                val eps: Skattegrunnlag?,
            ) {
                fun tilHentet(): Hentet {
                    return Hentet(
                        søkers = SkattegrunnlagMedId(
                            id = UUID.randomUUID(),
                            skattegrunnlag = søkers,
                        ),
                        eps = if (eps == null) {
                            null
                        } else {
                            SkattegrunnlagMedId(
                                id = UUID.randomUUID(),
                                skattegrunnlag = eps,
                            )
                        },
                    )
                }
            }
        }
    }
}

data class SkattegrunnlagMedId(
    val id: UUID,
    val skattegrunnlag: Skattegrunnlag,
)

object StøtterIkkeHentingAvEksternGrunnlag : EksterneGrunnlag {
    override fun leggTilSkatt(skatt: EksterneGrunnlagSkatt.Hentet): EksterneGrunnlag {
        throw UnsupportedOperationException("Støtter ikke henting av eksterne grunnlagsdata")
    }

    override fun fjernEps(): EksterneGrunnlag = this

    override val skatt: EksterneGrunnlagSkatt get() = EksterneGrunnlagSkatt.IkkeHentet
}
