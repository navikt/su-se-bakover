package vilkår.inntekt.domain.grunnlag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right

// TODO jah: Vurder bytt til sealed interface. Merk felles toString()
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
        StatensLånekasse,
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

        /**
         * ADVARSEL: Historisk fradragstype. Ikke bruk i nye beregninger.
         */
        AvkortingUtenlandsopphold,
        BeregnetFradragEPS,
        UnderMinstenivå,
    }

    data object Alderspensjon : Fradragstype() {
        override val kategori: Kategori = Kategori.Alderspensjon
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    data class Annet(val beskrivelse: String) : Fradragstype() {
        override val kategori: Kategori = Kategori.Annet
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    data object Arbeidsavklaringspenger : Fradragstype() {
        override val kategori: Kategori = Kategori.Arbeidsavklaringspenger
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    data object Arbeidsinntekt : Fradragstype() {
        override val kategori: Kategori = Kategori.Arbeidsinntekt
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    data object AvtalefestetPensjon : Fradragstype() {
        override val kategori: Kategori = Kategori.AvtalefestetPensjon
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    data object AvtalefestetPensjonPrivat : Fradragstype() {
        override val kategori: Kategori = Kategori.AvtalefestetPensjonPrivat
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    data object BidragEtterEkteskapsloven : Fradragstype() {
        override val kategori: Kategori = Kategori.BidragEtterEkteskapsloven
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    data object Dagpenger : Fradragstype() {
        override val kategori: Kategori = Kategori.Dagpenger
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    data object Fosterhjemsgodtgjørelse : Fradragstype() {
        override val kategori: Kategori = Kategori.Fosterhjemsgodtgjørelse
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    data object Gjenlevendepensjon : Fradragstype() {
        override val kategori: Kategori = Kategori.Gjenlevendepensjon
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    data object Introduksjonsstønad : Fradragstype() {
        override val kategori: Kategori = Kategori.Introduksjonsstønad
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    data object Kapitalinntekt : Fradragstype() {
        override val kategori: Kategori = Kategori.Kapitalinntekt
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    data object Kontantstøtte : Fradragstype() {
        override val kategori: Kategori = Kategori.Kontantstøtte
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    data object Kvalifiseringsstønad : Fradragstype() {
        override val kategori: Kategori = Kategori.Kvalifiseringsstønad
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    data object NAVytelserTilLivsopphold : Fradragstype() {
        override val kategori: Kategori = Kategori.NAVytelserTilLivsopphold
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    data object OffentligPensjon : Fradragstype() {
        override val kategori: Kategori = Kategori.OffentligPensjon
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    data object PrivatPensjon : Fradragstype() {
        override val kategori: Kategori = Kategori.PrivatPensjon
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    data object Sosialstønad : Fradragstype() {
        override val kategori: Kategori = Kategori.Sosialstønad
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    data object StatensLånekasse : Fradragstype() {
        override val kategori: Kategori = Kategori.StatensLånekasse
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    data object SupplerendeStønad : Fradragstype() {
        override val kategori: Kategori = Kategori.SupplerendeStønad
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    data object Sykepenger : Fradragstype() {
        override val kategori: Kategori = Kategori.Sykepenger
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    data object Tiltakspenger : Fradragstype() {
        override val kategori: Kategori = Kategori.Tiltakspenger
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    data object Ventestønad : Fradragstype() {
        override val kategori: Kategori = Kategori.Ventestønad
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    data object Uføretrygd : Fradragstype() {
        override val kategori: Kategori = Kategori.Uføretrygd
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    data object ForventetInntekt : Fradragstype() {
        override val kategori: Kategori = Kategori.ForventetInntekt
        override val måJusteresManueltVedGEndring: Boolean = true
    }

    /**
     * ADVARSEL: Historisk fradragstype. Ikke bruk i nye beregninger.
     */
    data object AvkortingUtenlandsopphold : Fradragstype() {
        override val kategori: Kategori = Kategori.AvkortingUtenlandsopphold
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    data object BeregnetFradragEPS : Fradragstype() {
        override val kategori: Kategori = Kategori.BeregnetFradragEPS
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    data object UnderMinstenivå : Fradragstype() {
        override val kategori: Kategori = Kategori.UnderMinstenivå
        override val måJusteresManueltVedGEndring: Boolean = false
    }

    companion object {
        fun tryParse(value: String, beskrivelse: String?): Either<UgyldigFradragstype, Fradragstype> {
            return Kategori.entries.firstOrNull { value == it.name }?.let { kategori ->
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
            return tryParse(kategori.name, beskrivelse).getOrElse { throw IllegalArgumentException("$it") }
        }

        sealed interface UgyldigFradragstype {
            data object UkjentFradragstype : UgyldigFradragstype
            data object UspesifisertKategoriUtenBeskrivelse : UgyldigFradragstype
            data object SpesifisertKategoriMedBeskrivelse : UgyldigFradragstype
        }
    }
}
