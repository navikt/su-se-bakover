package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.InputValidator.validerTekst

internal object SøknadsinnholdInputValidator {
    private const val STANDARD_MAKS_LENGDE = 500

    fun valider(søknadsinnhold: SøknadsinnholdJson): List<UgyldigInput> {
        val feil = mutableListOf<UgyldigInput>()

        with(feil) {
            validerFellesTekstfelter(søknadsinnhold)
            søknadsinnhold.ektefelle?.let {
                validerFormue("ektefelle.formue", it.formue)
                validerInntektOgPensjon("ektefelle.inntektOgPensjon", it.inntektOgPensjon)
            }
        }

        return feil
    }

    private fun MutableList<UgyldigInput>.validerFellesTekstfelter(søknadsinnhold: SøknadsinnholdJson) {
        validerTekst(
            felt = "oppholdstillatelse.statsborgerskapAndreLandFritekst",
            verdi = søknadsinnhold.oppholdstillatelse!!.statsborgerskapAndreLandFritekst,
            maksLengde = STANDARD_MAKS_LENGDE,
        )
        // TODO validere mot PDL da veileder/sb ikke kan velge selv MEN mulig å sette i redux så må ha validering på adressen mtp sikkerhet
        /*søknadsinnhold.boforhold.borPåAdresse?.let { adresse ->
            validerTekst("boforhold.borPåAdresse.adresselinje", adresse.adresselinje, maksLengde = 200)
            validerTekst("boforhold.borPåAdresse.postnummer", adresse.postnummer, maksLengde = 4)
            validerTekst("boforhold.borPåAdresse.poststed", adresse.poststed, maksLengde = 100)
            validerTekst("boforhold.borPåAdresse.bruksenhet", adresse.bruksenhet, maksLengde = 20)
        }*/

        when (val forNav = søknadsinnhold.forNav) {
            is ForNavJson.DigitalSøknad -> Unit
            is ForNavJson.Papirsøknad -> {
                validerTekst(
                    felt = "forNav.annenGrunn",
                    verdi = forNav.annenGrunn,
                    maksLengde = 500,
                )
            }

            null -> TODO()
        }

        validerFormue("formue", søknadsinnhold.formue!!)
        validerInntektOgPensjon("inntektOgPensjon", søknadsinnhold.inntektOgPensjon!!)
    }

    private fun MutableList<UgyldigInput>.validerFormue(
        sti: String,
        formue: FormueJson,
    ) {
        validerTekst("$sti.boligBrukesTil", formue.boligBrukesTil, maksLengde = 1000)
        validerTekst("$sti.eiendomBrukesTil", formue.eiendomBrukesTil, maksLengde = 1000)

        formue.kjøretøy?.forEachIndexed { index, kjøretøy ->
            validerTekst("$sti.kjøretøy.$index.kjøretøyDeEier", kjøretøy.kjøretøyDeEier, maksLengde = 100)
        }
    }

    private fun MutableList<UgyldigInput>.validerInntektOgPensjon(
        sti: String,
        inntektOgPensjon: InntektOgPensjonJson,
    ) {
        validerTekst("$sti.andreYtelserINav", inntektOgPensjon.andreYtelserINav, maksLengde = 500)
        validerTekst("$sti.søktAndreYtelserIkkeBehandletBegrunnelse", inntektOgPensjon.søktAndreYtelserIkkeBehandletBegrunnelse, maksLengde = 1000)

        inntektOgPensjon.trygdeytelserIUtlandet?.forEachIndexed { index, ytelse ->
            validerTekst("$sti.trygdeytelserIUtlandet.$index.type", ytelse.type, maksLengde = 200)
            validerTekst("$sti.trygdeytelserIUtlandet.$index.valuta", ytelse.valuta, maksLengde = 50)
        }

        inntektOgPensjon.pensjon?.forEachIndexed { index, pensjon ->
            validerTekst("$sti.pensjon.$index.ordning", pensjon.ordning, maksLengde = 200)
        }
    }
}
