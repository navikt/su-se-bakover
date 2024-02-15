package vilkår.vurderinger.domain

sealed interface EksterneGrunnlag {
    fun leggTilSkatt(skatt: EksterneGrunnlagSkatt): EksterneGrunnlag
    fun fjernEps(): EksterneGrunnlag
    fun copyWithNewId(): EksterneGrunnlag

    val skatt: EksterneGrunnlagSkatt
}

data class StøtterHentingAvEksternGrunnlag(
    override val skatt: EksterneGrunnlagSkatt,
) : EksterneGrunnlag {

    override fun leggTilSkatt(skatt: EksterneGrunnlagSkatt): EksterneGrunnlag = this.copy(skatt = skatt)
    override fun fjernEps(): EksterneGrunnlag = this.copy(skatt = skatt.fjernEps())
    override fun copyWithNewId(): EksterneGrunnlag = this.copy(skatt = skatt.copyWithNewId())

    companion object {
        fun ikkeHentet(): EksterneGrunnlag = StøtterHentingAvEksternGrunnlag(skatt = EksterneGrunnlagSkatt.IkkeHentet)
    }
}

data object StøtterIkkeHentingAvEksternGrunnlag : EksterneGrunnlag {
    override fun leggTilSkatt(skatt: EksterneGrunnlagSkatt): EksterneGrunnlag {
        throw UnsupportedOperationException("Støtter ikke henting av eksterne grunnlagsdata")
    }

    override fun fjernEps(): EksterneGrunnlag = this
    override fun copyWithNewId(): EksterneGrunnlag = this

    override val skatt: EksterneGrunnlagSkatt get() = EksterneGrunnlagSkatt.IkkeHentet
}
