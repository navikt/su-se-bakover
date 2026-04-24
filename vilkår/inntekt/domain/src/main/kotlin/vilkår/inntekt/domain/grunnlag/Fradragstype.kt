package vilkår.inntekt.domain.grunnlag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right

// TODO jah: Vurder bytt til sealed interface. Merk felles toString()
sealed class Fradragstype {

    abstract val kategori: Kategori
    abstract val måJusteresVedGEndring: Boolean

    // vil være tilfelle hvis reguelert fradrag kan hentes fra kilde
    abstract val kanJusteresAutomatisk: Boolean

    override fun toString(): String {
        return kategori.toString()
    }

    /**
     * Merk at disse serialisere/deserialiseres direkte til basen, så anbefaler ikke rename av disse.
     *
     * TODO - disse lagres direkte inn i basen
     */
    enum class Kategori {
        Alderspensjon,
        Annet, // Kommer kun fra saksbehandler
        Arbeidsavklaringspenger,
        Arbeidsinntekt,
        Omstillingsstønad,
        Overgangsstønad,

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
        override val måJusteresVedGEndring: Boolean = true
        override val kanJusteresAutomatisk: Boolean = true
    }

    data object Omstillingsstønad : Fradragstype() {
        override val kategori: Kategori = Kategori.Omstillingsstønad
        override val måJusteresVedGEndring: Boolean = true
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object Overgangsstønad : Fradragstype() {
        override val kategori: Kategori = Kategori.Overgangsstønad
        override val måJusteresVedGEndring: Boolean = true
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data class Annet(val beskrivelse: String) : Fradragstype() {
        override val kategori: Kategori = Kategori.Annet
        override val måJusteresVedGEndring: Boolean = false
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object Arbeidsavklaringspenger : Fradragstype() {
        override val kategori: Kategori = Kategori.Arbeidsavklaringspenger
        override val måJusteresVedGEndring: Boolean = true
        override val kanJusteresAutomatisk: Boolean = true
    }

    data object Arbeidsinntekt : Fradragstype() {
        override val kategori: Kategori = Kategori.Arbeidsinntekt
        override val måJusteresVedGEndring: Boolean = false
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object AvtalefestetPensjon : Fradragstype() {
        override val kategori: Kategori = Kategori.AvtalefestetPensjon
        override val måJusteresVedGEndring: Boolean = true
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object AvtalefestetPensjonPrivat : Fradragstype() {
        override val kategori: Kategori = Kategori.AvtalefestetPensjonPrivat
        override val måJusteresVedGEndring: Boolean = true
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object BidragEtterEkteskapsloven : Fradragstype() {
        override val kategori: Kategori = Kategori.BidragEtterEkteskapsloven
        override val måJusteresVedGEndring: Boolean = false
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object Dagpenger : Fradragstype() {
        override val kategori: Kategori = Kategori.Dagpenger
        override val måJusteresVedGEndring: Boolean = true
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object Fosterhjemsgodtgjørelse : Fradragstype() {
        override val kategori: Kategori = Kategori.Fosterhjemsgodtgjørelse
        override val måJusteresVedGEndring: Boolean = true
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object Gjenlevendepensjon : Fradragstype() {
        override val kategori: Kategori = Kategori.Gjenlevendepensjon
        override val måJusteresVedGEndring: Boolean = true
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object Introduksjonsstønad : Fradragstype() {
        override val kategori: Kategori = Kategori.Introduksjonsstønad
        override val måJusteresVedGEndring: Boolean = true
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object Kapitalinntekt : Fradragstype() {
        override val kategori: Kategori = Kategori.Kapitalinntekt
        override val måJusteresVedGEndring: Boolean = false
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object Kontantstøtte : Fradragstype() {
        override val kategori: Kategori = Kategori.Kontantstøtte
        override val måJusteresVedGEndring: Boolean = false
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object Kvalifiseringsstønad : Fradragstype() {
        override val kategori: Kategori = Kategori.Kvalifiseringsstønad
        override val måJusteresVedGEndring: Boolean = true
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object NAVytelserTilLivsopphold : Fradragstype() {
        override val kategori: Kategori = Kategori.NAVytelserTilLivsopphold
        override val måJusteresVedGEndring: Boolean = true
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object OffentligPensjon : Fradragstype() {
        override val kategori: Kategori = Kategori.OffentligPensjon
        override val måJusteresVedGEndring: Boolean = true
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object PrivatPensjon : Fradragstype() {
        override val kategori: Kategori = Kategori.PrivatPensjon
        override val måJusteresVedGEndring: Boolean = false
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object Sosialstønad : Fradragstype() {
        override val kategori: Kategori = Kategori.Sosialstønad
        override val måJusteresVedGEndring: Boolean = false
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object StatensLånekasse : Fradragstype() {
        override val kategori: Kategori = Kategori.StatensLånekasse
        override val måJusteresVedGEndring: Boolean = false
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object SupplerendeStønad : Fradragstype() {
        override val kategori: Kategori = Kategori.SupplerendeStønad
        override val måJusteresVedGEndring: Boolean = true
        override val kanJusteresAutomatisk: Boolean = true
    }

    data object Sykepenger : Fradragstype() {
        override val kategori: Kategori = Kategori.Sykepenger
        override val måJusteresVedGEndring: Boolean = false
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object Tiltakspenger : Fradragstype() {
        override val kategori: Kategori = Kategori.Tiltakspenger
        override val måJusteresVedGEndring: Boolean = true
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object Ventestønad : Fradragstype() {
        override val kategori: Kategori = Kategori.Ventestønad
        override val måJusteresVedGEndring: Boolean = true
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object Uføretrygd : Fradragstype() {
        override val kategori: Kategori = Kategori.Uføretrygd
        override val måJusteresVedGEndring: Boolean = true
        override val kanJusteresAutomatisk: Boolean = true
    }

    data object ForventetInntekt : Fradragstype() {
        override val kategori: Kategori = Kategori.ForventetInntekt
        override val måJusteresVedGEndring: Boolean = true
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    /**
     * ADVARSEL: Historisk fradragstype. Ikke bruk i nye beregninger.
     */
    data object AvkortingUtenlandsopphold : Fradragstype() {
        override val kategori: Kategori = Kategori.AvkortingUtenlandsopphold
        override val måJusteresVedGEndring: Boolean = false
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object BeregnetFradragEPS : Fradragstype() {
        override val kategori: Kategori = Kategori.BeregnetFradragEPS
        override val måJusteresVedGEndring: Boolean = false
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
    }

    data object UnderMinstenivå : Fradragstype() {
        override val kategori: Kategori = Kategori.UnderMinstenivå
        override val måJusteresVedGEndring: Boolean = false
        override val kanJusteresAutomatisk: Boolean = !måJusteresVedGEndring
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

fun Fradragstype.harGrunnbeløpSomKanReguleresAutomatisk(): Boolean =
    this.måJusteresVedGEndring && this.kanJusteresAutomatisk
