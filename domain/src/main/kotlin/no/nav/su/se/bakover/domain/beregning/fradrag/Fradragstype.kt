package no.nav.su.se.bakover.domain.beregning.fradrag

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right

sealed class Fradragstype {

    abstract val kategori: Kategori
    abstract val måJusteresManueltVedGEndring: Boolean

    override fun toString(): String {
        return kategori.toString()
    }

    enum class Kategori {
        Alderspensjon,
        Annet,
        Arbeidsavklaringspenger,
        Arbeidsinntekt,

        // AFP
        AvtalefestetPensjon,
        AvtalefestetPensjonPrivat,
        BidragEtterEkteskapsloven,
        Dagpenger,
        Fosterhjemsgodtgjørelse,
        Gjenlevendepensjon,
        Introduksjonsstønad,
        Kapitalinntekt,
        Kontantstøtte,
        Kvalifiseringsstønad,
        NAVytelserTilLivsopphold,
        OffentligPensjon,
        PrivatPensjon,
        Sosialstønad,
        SupplerendeStønad,
        Sykepenger,
        Tiltakspenger,
        Ventestønad,
        Uføretrygd,

        /**
         *  Resulting type of the operation that calculates EPS fradrag to be included in brukers beregning.
         *  Represents a "mixed bag" of fradrag that in total exceeds the respecive limits given by §5 and §6.
         *  Not to be used for input-operations (i.e. from frontend).
         */
        ForventetInntekt,
        AvkortingUtenlandsopphold,
        BeregnetFradragEPS,
        UnderMinstenivå,
    }

    object Alderspensjon : Fradragstype() {
        override val kategori: Kategori = Kategori.Alderspensjon
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    data class Annet(val beskrivelse: String) : Fradragstype() {
        override val kategori: Kategori = Kategori.Annet
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    object Arbeidsavklaringspenger : Fradragstype() {
        override val kategori: Kategori = Kategori.Arbeidsavklaringspenger
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    object Arbeidsinntekt : Fradragstype() {
        override val kategori: Kategori = Kategori.Arbeidsinntekt
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    object AvtalefestetPensjon : Fradragstype() {
        override val kategori: Kategori = Kategori.AvtalefestetPensjon
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    object AvtalefestetPensjonPrivat : Fradragstype() {
        override val kategori: Kategori = Kategori.AvtalefestetPensjonPrivat
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    object BidragEtterEkteskapsloven : Fradragstype() {
        override val kategori: Kategori = Kategori.BidragEtterEkteskapsloven
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    object Dagpenger : Fradragstype() {
        override val kategori: Kategori = Kategori.Dagpenger
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    object Fosterhjemsgodtgjørelse : Fradragstype() {
        override val kategori: Kategori = Kategori.Fosterhjemsgodtgjørelse
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    object Gjenlevendepensjon : Fradragstype() {
        override val kategori: Kategori = Kategori.Gjenlevendepensjon
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    object Introduksjonsstønad : Fradragstype() {
        override val kategori: Kategori = Kategori.Introduksjonsstønad
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    object Kapitalinntekt : Fradragstype() {
        override val kategori: Kategori = Kategori.Kapitalinntekt
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    object Kontantstøtte : Fradragstype() {
        override val kategori: Kategori = Kategori.Kontantstøtte
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    object Kvalifiseringsstønad : Fradragstype() {
        override val kategori: Kategori = Kategori.Kvalifiseringsstønad
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    object NAVytelserTilLivsopphold : Fradragstype() {
        override val kategori: Kategori = Kategori.NAVytelserTilLivsopphold
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    object OffentligPensjon : Fradragstype() {
        override val kategori: Kategori = Kategori.OffentligPensjon
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    object PrivatPensjon : Fradragstype() {
        override val kategori: Kategori = Kategori.PrivatPensjon
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    object Sosialstønad : Fradragstype() {
        override val kategori: Kategori = Kategori.Sosialstønad
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    object SupplerendeStønad : Fradragstype() {
        override val kategori: Kategori = Kategori.SupplerendeStønad
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    object Sykepenger : Fradragstype() {
        override val kategori: Kategori = Kategori.Sykepenger
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    object Tiltakspenger : Fradragstype() {
        override val kategori: Kategori = Kategori.Tiltakspenger
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    object Ventestønad : Fradragstype() {
        override val kategori: Kategori = Kategori.Ventestønad
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    object Uføretrygd : Fradragstype() {
        override val kategori: Kategori = Kategori.Uføretrygd
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    object ForventetInntekt : Fradragstype() {
        override val kategori: Kategori = Kategori.ForventetInntekt
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    object AvkortingUtenlandsopphold : Fradragstype() {
        override val kategori: Kategori = Kategori.AvkortingUtenlandsopphold
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    object BeregnetFradragEPS : Fradragstype() {
        override val kategori: Kategori = Kategori.BeregnetFradragEPS
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    object UnderMinstenivå : Fradragstype() {
        override val kategori: Kategori = Kategori.UnderMinstenivå
        override val måJusteresManueltVedGEndring: Boolean = false
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
