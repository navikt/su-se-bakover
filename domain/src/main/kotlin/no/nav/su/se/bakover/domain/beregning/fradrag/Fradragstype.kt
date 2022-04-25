package no.nav.su.se.bakover.domain.beregning.fradrag

import arrow.core.Either
import arrow.core.left
import arrow.core.right

sealed class Fradragstype {

    abstract val kategori: Kategori

    override fun toString(): String {
        return kategori.toString()
    }

    enum class Kategori {
        NAVytelserTilLivsopphold,
        Arbeidsinntekt,
        OffentligPensjon,
        PrivatPensjon,
        Sosialstønad,
        Kontantstøtte,
        Introduksjonsstønad,
        Kvalifiseringsstønad,
        BidragEtterEkteskapsloven,
        Kapitalinntekt,
        ForventetInntekt,
        AvkortingUtenlandsopphold,

        /**
         *  Resulting type of the operation that calculates EPS fradrag to be included in brukers beregning.
         *  Represents a "mixed bag" of fradrag that in total exceeds the respecive limits given by §5 and §6.
         *  Not to be used for input-operations (i.e. from frontend).
         */
        BeregnetFradragEPS,
        UnderMinstenivå;
    }

    object NAVytelserTilLivsopphold : Fradragstype() {
        override val kategori: Kategori = Kategori.NAVytelserTilLivsopphold
    }

    object Arbeidsinntekt : Fradragstype() {
        override val kategori: Kategori = Kategori.Arbeidsinntekt
    }

    object OffentligPensjon : Fradragstype() {
        override val kategori: Kategori = Kategori.OffentligPensjon
    }

    object PrivatPensjon : Fradragstype() {
        override val kategori: Kategori = Kategori.PrivatPensjon
    }

    object Sosialstønad : Fradragstype() {
        override val kategori: Kategori = Kategori.Sosialstønad
    }

    object Kontantstøtte : Fradragstype() {
        override val kategori: Kategori = Kategori.Kontantstøtte
    }

    object Introduksjonsstønad : Fradragstype() {
        override val kategori: Kategori = Kategori.Introduksjonsstønad
    }

    object Kvalifiseringsstønad : Fradragstype() {
        override val kategori: Kategori = Kategori.Kvalifiseringsstønad
    }

    object BidragEtterEkteskapsloven : Fradragstype() {
        override val kategori: Kategori = Kategori.BidragEtterEkteskapsloven
    }

    object Kapitalinntekt : Fradragstype() {
        override val kategori: Kategori = Kategori.Kapitalinntekt
    }

    object ForventetInntekt : Fradragstype() {
        override val kategori: Kategori = Kategori.ForventetInntekt
    }

    object AvkortingUtenlandsopphold : Fradragstype() {
        override val kategori: Kategori = Kategori.AvkortingUtenlandsopphold
    }

    object BeregnetFradragEPS : Fradragstype() {
        override val kategori: Kategori = Kategori.BeregnetFradragEPS
    }

    object UnderMinstenivå : Fradragstype() {
        override val kategori: Kategori = Kategori.UnderMinstenivå
    }

    companion object {
        fun tryParse(value: String): Either<UgyldigFradragstype, Fradragstype> {
            return Kategori.values().firstOrNull { it.name == value }
                ?.let { from(it).right() }
                ?: UgyldigFradragstype.left()
        }

        fun from(kategori: Kategori): Fradragstype {
            return Fradragstype::class.sealedSubclasses.first { kategori == it.objectInstance?.kategori }.objectInstance!!
        }

        object UgyldigFradragstype
    }
}
