package vilkår.vurderinger.domain

import vilkår.skatt.domain.Skattegrunnlag

sealed interface EksterneGrunnlag {
    fun leggTilSkatt(skatt: EksterneGrunnlagSkatt): EksterneGrunnlag
    fun fjernEps(): EksterneGrunnlag

    val skatt: EksterneGrunnlagSkatt
}

data class StøtterHentingAvEksternGrunnlag(
    override val skatt: EksterneGrunnlagSkatt,
) : EksterneGrunnlag {

    override fun leggTilSkatt(skatt: EksterneGrunnlagSkatt): EksterneGrunnlag = this.copy(skatt = skatt)
    override fun fjernEps(): EksterneGrunnlag = this.copy(skatt = skatt.fjernEps())

    companion object {
        fun ikkeHentet(): EksterneGrunnlag = StøtterHentingAvEksternGrunnlag(
            skatt = EksterneGrunnlagSkatt.IkkeHentet,
        )
    }
}

sealed interface EksterneGrunnlagSkatt {
    fun fjernEps(): EksterneGrunnlagSkatt

    data object IkkeHentet : EksterneGrunnlagSkatt {
        override fun fjernEps(): EksterneGrunnlagSkatt = this
    }

    data class Hentet(
        val søkers: Skattegrunnlag,
        val eps: Skattegrunnlag?,
    ) : EksterneGrunnlagSkatt {

        override fun fjernEps(): EksterneGrunnlagSkatt = this.copy(eps = null)
    }
}

data object StøtterIkkeHentingAvEksternGrunnlag : EksterneGrunnlag {
    override fun leggTilSkatt(skatt: EksterneGrunnlagSkatt): EksterneGrunnlag {
        throw UnsupportedOperationException("Støtter ikke henting av eksterne grunnlagsdata")
    }

    override fun fjernEps(): EksterneGrunnlag = this

    override val skatt: EksterneGrunnlagSkatt get() = EksterneGrunnlagSkatt.IkkeHentet
}
