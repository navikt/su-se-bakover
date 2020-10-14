package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.domain.VedtakInnhold.Avslagsvedtak.Companion.lagAvslagsvedtak
import no.nav.su.se.bakover.domain.VedtakInnhold.Innvilgelsesvedtak.Companion.lagInnvilgelsesvedtak
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.beregning.Fradragstype
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed class VedtakInnhold {
    abstract val dato: String
    abstract val fødselsnummer: Fnr
    abstract val fornavn: String
    abstract val etternavn: String
    abstract val adresse: String?
    abstract val husnummer: String?
    abstract val bruksenhet: String?
    abstract val postnummer: String?
    abstract val poststed: String
    abstract val satsbeløp: Int
    abstract val fradragSum: Int

    data class Innvilgelsesvedtak(
        override val dato: String,
        override val fødselsnummer: Fnr,
        override val fornavn: String,
        override val etternavn: String,
        override val adresse: String?,
        override val husnummer: String?,
        override val bruksenhet: String?,
        override val postnummer: String?,
        override val poststed: String,
        override val satsbeløp: Int,
        override val fradragSum: Int,
        val månedsbeløp: Int,
        val fradato: String,
        val tildato: String,
        val sats: String,
        val satsGrunn: Satsgrunn,
        val redusertStønadStatus: Boolean,
        val harEktefelle: Boolean,
        val fradrag: List<FradragPerMåned>,
    ) : VedtakInnhold() {
        companion object {
            fun lagInnvilgelsesvedtak(person: Person, behandling: Behandling): Innvilgelsesvedtak {
                val førsteMånedsberegning =
                    behandling.beregning()!!.månedsberegninger.firstOrNull()!! // Støtte för variende beløp i framtiden?

                return Innvilgelsesvedtak(
                    dato = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    fødselsnummer = behandling.søknad.søknadInnhold.personopplysninger.fnr,
                    fornavn = person.navn.fornavn,
                    etternavn = person.navn.etternavn,
                    adresse = person.adresse?.adressenavn,
                    bruksenhet = person.adresse?.bruksenhet,
                    husnummer = person.adresse?.husnummer,
                    postnummer = person.adresse?.poststed?.postnummer,
                    poststed = person.adresse?.poststed?.poststed!!,
                    månedsbeløp = førsteMånedsberegning.beløp,
                    fradato = behandling.beregning()!!.fraOgMed.formatMonthYear(),
                    tildato = behandling.beregning()!!.tilOgMed.formatMonthYear(),
                    sats = behandling.beregning()?.sats.toString().toLowerCase(),
                    satsbeløp = førsteMånedsberegning.satsBeløp,
                    satsGrunn = satsgrunnForBehandling(behandling)!!,
                    redusertStønadStatus = behandling.beregning()?.fradrag?.isNotEmpty() ?: false,
                    harEktefelle = behandling.behandlingsinformasjon().bosituasjon?.delerBoligMed == Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER,
                    fradrag = behandling.beregning()!!.fradrag.toFradragPerMåned(),
                    fradragSum = behandling.beregning()!!.fradrag.toFradragPerMåned().sumBy { fradrag -> fradrag.beløp },
                )
            }
        }
    }

    data class Avslagsvedtak(
        override val dato: String,
        override val fødselsnummer: Fnr,
        override val fornavn: String,
        override val etternavn: String,
        override val adresse: String?,
        override val husnummer: String?,
        override val bruksenhet: String?,
        override val postnummer: String?,
        override val poststed: String,
        override val satsbeløp: Int,
        override val fradragSum: Int,
        val avslagsgrunn: Avslagsgrunn,
        val halvGrunnbeløp: Int,
    ) : VedtakInnhold() {
        companion object {
            fun lagAvslagsvedtak(person: Person, behandling: Behandling) =
                Avslagsvedtak(
                    dato = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    fødselsnummer = behandling.søknad.søknadInnhold.personopplysninger.fnr,
                    fornavn = person.navn.fornavn,
                    etternavn = person.navn.etternavn,
                    adresse = person.adresse?.adressenavn,
                    bruksenhet = person.adresse?.bruksenhet,
                    husnummer = person.adresse?.husnummer,
                    postnummer = person.adresse?.poststed?.postnummer,
                    poststed = person.adresse?.poststed?.poststed!!,
                    satsbeløp = behandling.beregning()?.månedsberegninger?.firstOrNull()?.satsBeløp ?: 0,
                    fradragSum = behandling.beregning()?.fradrag?.toFradragPerMåned()?.sumBy { fradrag -> fradrag.beløp } ?: 0,
                    avslagsgrunn = avslagsgrunnForBehandling(behandling)!!,
                    halvGrunnbeløp = Grunnbeløp.`0,5G`.fraDato(LocalDate.now()).toInt()
                )
        }
    }

    companion object {
        fun lagVedtaksinnhold(person: Person, behandling: Behandling): VedtakInnhold =
            when {
                erAvslått(behandling) -> lagAvslagsvedtak(person, behandling)
                erInnvilget(behandling) -> lagInnvilgelsesvedtak(person, behandling)
                else -> throw java.lang.RuntimeException("Kan ikke lage vedtaksinnhold for behandling som ikke er avslått/innvilget")
            }

        private fun erInnvilget(behandling: Behandling): Boolean {
            val innvilget = listOf(
                Behandling.BehandlingsStatus.SIMULERT,
                Behandling.BehandlingsStatus.BEREGNET_INNVILGET,
                Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET,
                Behandling.BehandlingsStatus.IVERKSATT_INNVILGET
            )
            return innvilget.contains(behandling.status())
        }

        private fun erAvslått(behandling: Behandling): Boolean {
            val avslått = listOf(
                Behandling.BehandlingsStatus.BEREGNET_AVSLAG,
                Behandling.BehandlingsStatus.VILKÅRSVURDERT_AVSLAG,
                Behandling.BehandlingsStatus.TIL_ATTESTERING_AVSLAG,
                Behandling.BehandlingsStatus.IVERKSATT_AVSLAG
            )
            return avslått.contains(behandling.status())
        }
    }
}

enum class Avslagsgrunn {
    UFØRHET,
    FLYKTNING,
    OPPHOLDSTILLATELSE,
    PERSONLIG_OPPMØTE,
    FORMUE,
    BOR_OG_OPPHOLDER_SEG_I_NORGE,
    FOR_HØY_INNTEKT,
    SU_UNDER_MINSTEGRENSE,
    UTENLANDSOPPHOLD_OVER_90_DAGER,
    INNLAGT_PÅ_INSTITUSJON
}

enum class Satsgrunn {
    ENSLIG,
    DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN,
    DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67,
    DELER_BOLIG_MED_EKTEMAKE_SAMBOER_OVER_67,
    DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING
}

data class FradragPerMåned(val type: Fradragstype, val beløp: Int)

// TODO Hente Locale fra brukerens målform
fun LocalDate.formatMonthYear(): String =
    this.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("nb-NO")))

internal fun List<Fradrag>.toFradragPerMåned(): List<FradragPerMåned> =
    this.map {
        FradragPerMåned(it.type, it.perMåned())
    }

internal fun avslagsgrunnForBehandling(behandling: Behandling): Avslagsgrunn? {
    if (behandling.beregning()?.beløpErNull() == true) {
        return Avslagsgrunn.FOR_HØY_INNTEKT
    }
    if (behandling.beregning()?.beløpErOverNullMenUnderMinstebeløp() == true) {
        return Avslagsgrunn.SU_UNDER_MINSTEGRENSE
    }

    return behandling.behandlingsinformasjon().let {
        when {
            it.uførhet?.status == Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt -> Avslagsgrunn.UFØRHET
            it.flyktning?.status == Behandlingsinformasjon.Flyktning.Status.VilkårIkkeOppfylt -> Avslagsgrunn.FLYKTNING
            it.lovligOpphold?.status == Behandlingsinformasjon.LovligOpphold.Status.VilkårIkkeOppfylt -> Avslagsgrunn.OPPHOLDSTILLATELSE
            it.fastOppholdINorge?.status == Behandlingsinformasjon.FastOppholdINorge.Status.VilkårIkkeOppfylt -> Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE
            it.oppholdIUtlandet?.status == Behandlingsinformasjon.OppholdIUtlandet.Status.SkalVæreMerEnn90DagerIUtlandet -> Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER
            it.formue?.status == Behandlingsinformasjon.Formue.Status.VilkårIkkeOppfylt -> Avslagsgrunn.FORMUE
            it.personligOppmøte?.status.let { s ->
                s == Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttOpp ||
                    s == Behandlingsinformasjon.PersonligOppmøte.Status.FullmektigUtenLegeattest
            } -> Avslagsgrunn.PERSONLIG_OPPMØTE
            else -> null
        }
    }
}

internal fun satsgrunnForBehandling(behandling: Behandling): Satsgrunn? {
    return behandling.behandlingsinformasjon().bosituasjon?.let {
        when {
            !it.delerBolig -> Satsgrunn.ENSLIG
            it.delerBoligMed == Boforhold.DelerBoligMed.VOKSNE_BARN -> Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
            it.delerBoligMed == Boforhold.DelerBoligMed.ANNEN_VOKSEN -> Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
            it.delerBoligMed == Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER
                && it.ektemakeEllerSamboerUnder67År == false -> Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_OVER_67
            it.delerBoligMed == Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER &&
                it.ektemakeEllerSamboerUnder67År == true && it.ektemakeEllerSamboerUførFlyktning == false -> Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67
            it.delerBoligMed == Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER
                && it.ektemakeEllerSamboerUførFlyktning == true -> Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING
            else -> null
        }
    }
}
