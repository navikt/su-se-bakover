package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Boforhold
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.EktefellePartnerSamboer
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FeilVedOpprettelseAvBoforhold
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.InnlagtPåInstitusjon
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.OppgittAdresse

data class BoforholdJson(
    val borOgOppholderSegINorge: Boolean,
    val delerBoligMedVoksne: Boolean,
    val delerBoligMed: String? = null,
    val ektefellePartnerSamboer: EktefellePartnerSamboer?,
    val innlagtPåInstitusjon: InnlagtPåInstitusjon?,
    val borPåAdresse: AdresseFraSøknad?,
    val ingenAdresseGrunn: OppgittAdresse.IngenAdresse.IngenAdresseGrunn?,
) {
    data class AdresseFraSøknad(
        val adresselinje: String,
        val postnummer: String,
        val poststed: String?,
        val bruksenhet: String?,
    )

    fun toBoforhold(): Either<FeilVedOpprettelseAvBoforholdJson, Boforhold> {
        if ((borPåAdresse != null && ingenAdresseGrunn != null)) {
            return FeilVedOpprettelseAvBoforholdJson.DomeneFeil(FeilVedOpprettelseAvBoforhold.BeggeAdressegrunnerErUtfylt).left()
        }

        val oppgittAdresse = when {
            borPåAdresse != null -> OppgittAdresse.BorPåAdresse(
                adresselinje = borPåAdresse.adresselinje,
                postnummer = borPåAdresse.postnummer,
                poststed = borPåAdresse.poststed,
                bruksenhet = borPåAdresse.bruksenhet,
            )
            ingenAdresseGrunn != null -> OppgittAdresse.IngenAdresse(ingenAdresseGrunn)
            else -> return FeilVedOpprettelseAvBoforholdJson.UgyldigInput(
                UgyldigSøknadsinnholdInputFraJson(
                    felt = "boforhold.borPåAdresse",
                    begrunnelse = "borPåAdresse eller ingenAdresseGrunn må være satt",
                ),
            ).left()
        }

        val delerBoligMed = delerBoligMed?.let {
            toBoforholdType(it).getOrElse { return FeilVedOpprettelseAvBoforholdJson.UgyldigInput(it).left() }
        }

        return Boforhold.tryCreate(
            borOgOppholderSegINorge = borOgOppholderSegINorge,
            delerBolig = delerBoligMedVoksne,
            delerBoligMed = delerBoligMed,
            ektefellePartnerSamboer = ektefellePartnerSamboer,
            innlagtPåInstitusjon = innlagtPåInstitusjon,
            oppgittAdresse = oppgittAdresse,
        ).fold(
            { FeilVedOpprettelseAvBoforholdJson.DomeneFeil(it).left() },
            { it.right() },
        )
    }

    private fun toBoforholdType(str: String): Either<UgyldigSøknadsinnholdInputFraJson, Boforhold.DelerBoligMed> {
        return when (str) {
            "EKTEMAKE_SAMBOER" -> Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER.right()
            "VOKSNE_BARN" -> Boforhold.DelerBoligMed.VOKSNE_BARN.right()
            "ANNEN_VOKSEN" -> Boforhold.DelerBoligMed.ANNEN_VOKSEN.right()
            else -> UgyldigSøknadsinnholdInputFraJson(
                felt = "boforhold.delerBoligMed",
                begrunnelse = "Ukjent verdi: $str",
            ).left()
        }
    }

    companion object {
        fun Boforhold.toBoforholdJson(): BoforholdJson {
            return BoforholdJson(
                borOgOppholderSegINorge = this.borOgOppholderSegINorge,
                delerBoligMedVoksne = this.delerBolig,
                delerBoligMed = this.delerBoligMed?.toJson(),
                ektefellePartnerSamboer = this.ektefellePartnerSamboer,
                innlagtPåInstitusjon = this.innlagtPåInstitusjon,
                borPåAdresse = when (val o = this.oppgittAdresse) {
                    is OppgittAdresse.BorPåAdresse -> AdresseFraSøknad(
                        adresselinje = o.adresselinje,
                        postnummer = o.postnummer,
                        poststed = o.poststed,
                        bruksenhet = o.bruksenhet,
                    )
                    is OppgittAdresse.IngenAdresse -> null
                },
                ingenAdresseGrunn = when (val o = this.oppgittAdresse) {
                    is OppgittAdresse.BorPåAdresse -> null
                    is OppgittAdresse.IngenAdresse -> o.grunn
                },
            )
        }
    }
}

private fun Boforhold.DelerBoligMed.toJson(): String {
    return when (this) {
        Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER -> "EKTEMAKE_SAMBOER"
        Boforhold.DelerBoligMed.VOKSNE_BARN -> "VOKSNE_BARN"
        Boforhold.DelerBoligMed.ANNEN_VOKSEN -> "ANNEN_VOKSEN"
    }
}

sealed interface FeilVedOpprettelseAvBoforholdJson {
    data class DomeneFeil(val underliggendeFeil: FeilVedOpprettelseAvBoforhold) : FeilVedOpprettelseAvBoforholdJson
    data class UgyldigInput(val underliggendeFeil: UgyldigSøknadsinnholdInputFraJson) : FeilVedOpprettelseAvBoforholdJson
}
