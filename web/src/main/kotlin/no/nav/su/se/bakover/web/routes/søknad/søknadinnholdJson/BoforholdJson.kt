package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import no.nav.su.se.bakover.domain.Boforhold
import no.nav.su.se.bakover.domain.InnlagtPåInstitusjon

data class BoforholdJson(
    val borOgOppholderSegINorge: Boolean,
    val delerBoligMedVoksne: Boolean,
    val delerBoligMed: String? = null,
    val ektefellePartnerSamboer: Boforhold.EktefellePartnerSamboer?,
    val innlagtPåInstitusjon: InnlagtPåInstitusjon?,
    val borPåAdresse: AdresseFraSøknad?,
    val ingenAdresseGrunn: Boforhold.OppgittAdresse.IngenAdresse.IngenAdresseGrunn?,
) {
    data class AdresseFraSøknad(
        val adresselinje: String,
        val postnummer: String,
        val poststed: String?,
        val bruksenhet: String?,
    )

    fun toBoforhold(): Boforhold {
        if (borPåAdresse != null && ingenAdresseGrunn != null) {
            throw IllegalArgumentException("Ogyldig adresseinformasjon sendt!")
        }

        val oppgittAdresse = when {
            borPåAdresse != null -> Boforhold.OppgittAdresse.BorPåAdresse(
                adresselinje = borPåAdresse.adresselinje,
                postnummer = borPåAdresse.postnummer,
                poststed = borPåAdresse.poststed,
                bruksenhet = borPåAdresse.bruksenhet,
            )
            ingenAdresseGrunn != null -> Boforhold.OppgittAdresse.IngenAdresse(ingenAdresseGrunn)
            else -> null
        }

        return Boforhold(
            borOgOppholderSegINorge = borOgOppholderSegINorge,
            delerBolig = delerBoligMedVoksne,
            delerBoligMed = delerBoligMed?.let {
                toBoforholdType(it)
            },
            ektefellePartnerSamboer = ektefellePartnerSamboer,
            innlagtPåInstitusjon = innlagtPåInstitusjon,
            oppgittAdresse = oppgittAdresse,
        )
    }

    private fun toBoforholdType(str: String): Boforhold.DelerBoligMed {
        return when (str) {
            "EKTEMAKE_SAMBOER" -> Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER
            "VOKSNE_BARN" -> Boforhold.DelerBoligMed.VOKSNE_BARN
            "ANNEN_VOKSEN" -> Boforhold.DelerBoligMed.ANNEN_VOKSEN
            else -> throw IllegalArgumentException("delerBoligMed feltet er ugyldig")
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
                    is Boforhold.OppgittAdresse.BorPåAdresse -> AdresseFraSøknad(
                        adresselinje = o.adresselinje,
                        postnummer = o.postnummer,
                        poststed = o.poststed,
                        bruksenhet = o.bruksenhet,
                    )
                    is Boforhold.OppgittAdresse.IngenAdresse -> null
                    null -> null
                },
                ingenAdresseGrunn = when (val o = this.oppgittAdresse) {
                    is Boforhold.OppgittAdresse.BorPåAdresse -> null
                    is Boforhold.OppgittAdresse.IngenAdresse -> o.grunn
                    null -> null
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
