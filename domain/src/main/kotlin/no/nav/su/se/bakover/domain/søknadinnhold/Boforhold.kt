package no.nav.su.se.bakover.domain.søknadinnhold

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.InnlagtPåInstitusjon

data class Boforhold private constructor(
    val borOgOppholderSegINorge: Boolean,
    val delerBolig: Boolean,
    val delerBoligMed: DelerBoligMed? = null,
    val ektefellePartnerSamboer: EktefellePartnerSamboer? = null,
    val innlagtPåInstitusjon: InnlagtPåInstitusjon?,
    val oppgittAdresse: OppgittAdresse,
) {
    enum class DelerBoligMed() {
        EKTEMAKE_SAMBOER, // TODO AI: Skal endres till ektefelle (14/10/2020 LOL)
        VOKSNE_BARN,
        ANNEN_VOKSEN;
    }

    companion object {
        fun tryCreate(
            borOgOppholderSegINorge: Boolean,
            delerBolig: Boolean,
            delerBoligMed: DelerBoligMed?,
            ektefellePartnerSamboer: EktefellePartnerSamboer?,
            innlagtPåInstitusjon: InnlagtPåInstitusjon?,
            oppgittAdresse: OppgittAdresse,
        ): Either<FeilVedOpprettelseAvBoforhold, Boforhold> {
            validerDelerBoligMed(delerBolig, delerBoligMed).mapLeft { return it.left() }
            validerEPS(delerBolig, delerBoligMed, ektefellePartnerSamboer).mapLeft { return it.left() }

            return Boforhold(
                borOgOppholderSegINorge = borOgOppholderSegINorge,
                delerBolig = delerBolig,
                delerBoligMed = delerBoligMed,
                ektefellePartnerSamboer = ektefellePartnerSamboer,
                innlagtPåInstitusjon = innlagtPåInstitusjon,
                oppgittAdresse = oppgittAdresse,
            ).right()
        }

        private fun validerDelerBoligMed(
            delerBolig: Boolean,
            delerBoligMed: DelerBoligMed?,
        ) =
            if (delerBolig && delerBoligMed == null) FeilVedOpprettelseAvBoforhold.DelerBoligMedErIkkeUtfylt.left() else Unit.right()

        private fun validerEPS(
            delerBolig: Boolean,
            delerBoligMed: DelerBoligMed?,
            ektefellePartnerSamboer: EktefellePartnerSamboer?,
        ) =
            if (delerBolig && delerBoligMed == DelerBoligMed.EKTEMAKE_SAMBOER && ektefellePartnerSamboer == null) FeilVedOpprettelseAvBoforhold.EktefellePartnerSamboerMåVæreUtfylt.left() else Unit.right()
    }
}

data class EktefellePartnerSamboer(
    val erUførFlyktning: Boolean?,
    val fnr: Fnr,
)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = OppgittAdresse.BorPåAdresse::class, name = "BorPåAdresse"),
    JsonSubTypes.Type(value = OppgittAdresse.IngenAdresse::class, name = "IngenAdresse"),
)
sealed class OppgittAdresse {

    data class BorPåAdresse(
        val adresselinje: String,
        val postnummer: String,
        val poststed: String?,
        val bruksenhet: String?,
    ) : OppgittAdresse() {
        override fun toString() = "${hentGateAdresse()}, ${hentPostAdresse()}"

        private fun hentGateAdresse() = if (bruksenhet != null) "$adresselinje $bruksenhet" else adresselinje
        private fun hentPostAdresse() = if (poststed != null) "$postnummer $poststed" else postnummer
    }

    data class IngenAdresse(
        val grunn: IngenAdresseGrunn,
    ) : OppgittAdresse() {

        enum class IngenAdresseGrunn {
            BOR_PÅ_ANNEN_ADRESSE, HAR_IKKE_FAST_BOSTED
        }
    }
}

sealed interface FeilVedOpprettelseAvBoforhold {
    object DelerBoligMedErIkkeUtfylt : FeilVedOpprettelseAvBoforhold
    object EktefellePartnerSamboerMåVæreUtfylt : FeilVedOpprettelseAvBoforhold
    object BeggeAdressegrunnerErUtfylt : FeilVedOpprettelseAvBoforhold
}
