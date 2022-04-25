package no.nav.su.se.bakover.domain.beregning.fradrag

import arrow.core.Either
import arrow.core.getOrHandle
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
        UnderMinstenivå,
        Annet;
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

    data class Annet(val beskrivelse: String) : Fradragstype() {
        override val kategori: Kategori = Kategori.Annet
    }

    companion object {
        fun tryParse(value: String, beskrivelse: String?): Either<UgyldigFradragstype, Fradragstype> {
            return Kategori.values().firstOrNull { value == it.name }?.let { kategori ->
                when {
                    kategori == Kategori.Annet && beskrivelse == null -> {
                        UgyldigFradragstype.UspesifisertKategoriUtenBeskrivelse.left()
                    }
                    kategori != Kategori.Annet && beskrivelse != null -> {
                        UgyldigFradragstype.SpesifisertKategoriMedBeskrivelse.left()
                    }
                    else -> {
                        if (kategori == Kategori.Annet) {
                            Annet(beskrivelse!!)
                        } else {
                            Fradragstype::class.sealedSubclasses.first { kategori == it.objectInstance?.kategori }.objectInstance!!
                        }.right()
                    }
                }
            } ?: UgyldigFradragstype.UkjentFradragstype.left()
        }

        fun from(kategori: Kategori, beskrivelse: String?): Fradragstype {
            return tryParse(kategori.name, beskrivelse).getOrHandle { throw IllegalArgumentException("$it") }
        }

        sealed interface UgyldigFradragstype {
            object UkjentFradragstype : UgyldigFradragstype
            object UspesifisertKategoriUtenBeskrivelse : UgyldigFradragstype
            object SpesifisertKategoriMedBeskrivelse : UgyldigFradragstype
        }
    }
}
