package vilkår.vurderinger.domain

import vilkår.skatt.domain.Skattegrunnlag
import java.util.UUID

sealed interface EksterneGrunnlagSkatt {
    fun fjernEps(): EksterneGrunnlagSkatt
    fun copyWithNewId(): EksterneGrunnlagSkatt

    data object IkkeHentet : EksterneGrunnlagSkatt {
        override fun fjernEps(): EksterneGrunnlagSkatt = this
        override fun copyWithNewId(): IkkeHentet = this
    }

    data class Hentet(val søkers: Skattegrunnlag, val eps: Skattegrunnlag?) : EksterneGrunnlagSkatt {
        override fun fjernEps(): EksterneGrunnlagSkatt = this.copy(eps = null)
        override fun copyWithNewId(): Hentet =
            Hentet(søkers = søkers.copy(id = UUID.randomUUID()), eps = eps?.copy(id = UUID.randomUUID()))
    }
}
