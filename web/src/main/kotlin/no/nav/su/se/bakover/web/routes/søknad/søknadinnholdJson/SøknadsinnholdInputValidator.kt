package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

internal data class UgyldigSøknadsinnholdInput(
    val felt: String,
    val begrunnelse: String,
)

internal object SøknadsinnholdInputValidator {
    private const val STANDARD_MAKS_LENGDE = 500

    private val tillatteSkilletegn = setOf(
        ' ',
        '.',
        ',',
        '?',
        '!',
        '(',
        ')',
        '%',
        '*',
        ':',
        ';',
        '-',
        '\'',
        '"',
        '/',
        '+',
        '&',
        '_',
    )

    // Aksenttegn
    private val tillatteSpesialTegn = setOf(
        'ô',
        'è',
        'ò',
        'ê',
    )

    private val mistenkeligeMønstre = listOf(
        Regex("<\\s*/?\\s*script", RegexOption.IGNORE_CASE),
        Regex("javascript\\s*:", RegexOption.IGNORE_CASE),
        Regex("<\\s*iframe", RegexOption.IGNORE_CASE),
        Regex("<\\s*object", RegexOption.IGNORE_CASE),
        Regex("<\\s*embed", RegexOption.IGNORE_CASE),
        Regex("onerror\\s*=", RegexOption.IGNORE_CASE),
        Regex("onload\\s*=", RegexOption.IGNORE_CASE),
    )

    fun valider(søknadsinnhold: SøknadsinnholdJson): List<UgyldigSøknadsinnholdInput> {
        val feil = mutableListOf<UgyldigSøknadsinnholdInput>()

        with(feil) {
            validerFellesTekstfelter(søknadsinnhold)
            søknadsinnhold.ektefelle?.let {
                validerFormue("ektefelle.formue", it.formue)
                validerInntektOgPensjon("ektefelle.inntektOgPensjon", it.inntektOgPensjon)
            }
        }

        return feil
    }

    private fun MutableList<UgyldigSøknadsinnholdInput>.validerFellesTekstfelter(søknadsinnhold: SøknadsinnholdJson) {
        validerTekst(
            felt = "oppholdstillatelse.statsborgerskapAndreLandFritekst",
            verdi = søknadsinnhold.oppholdstillatelse.statsborgerskapAndreLandFritekst,
        )
        // TODO validere mot PDL da veileder/sb ikke kan velge selv MEN mulig å sette i redux så må ha validering på adressen mtp sikkerhet
        søknadsinnhold.boforhold.borPåAdresse?.let { adresse ->
            validerTekst("boforhold.borPåAdresse.adresselinje", adresse.adresselinje, maksLengde = 200)
            validerTekst("boforhold.borPåAdresse.postnummer", adresse.postnummer, maksLengde = 4)
            validerTekst("boforhold.borPåAdresse.poststed", adresse.poststed, maksLengde = 100)
            validerTekst("boforhold.borPåAdresse.bruksenhet", adresse.bruksenhet, maksLengde = 20)
        }

        when (val forNav = søknadsinnhold.forNav) {
            is ForNavJson.DigitalSøknad -> Unit
            is ForNavJson.Papirsøknad -> {
                validerTekst(
                    felt = "forNav.annenGrunn",
                    verdi = forNav.annenGrunn,
                    maksLengde = 500,
                )
            }
        }

        validerFormue("formue", søknadsinnhold.formue)
        validerInntektOgPensjon("inntektOgPensjon", søknadsinnhold.inntektOgPensjon)
    }

    private fun MutableList<UgyldigSøknadsinnholdInput>.validerFormue(
        sti: String,
        formue: FormueJson,
    ) {
        validerTekst("$sti.boligBrukesTil", formue.boligBrukesTil, maksLengde = 1000)
        validerTekst("$sti.eiendomBrukesTil", formue.eiendomBrukesTil, maksLengde = 1000)

        formue.kjøretøy?.forEachIndexed { index, kjøretøy ->
            validerTekst("$sti.kjøretøy.$index.kjøretøyDeEier", kjøretøy.kjøretøyDeEier, maksLengde = 100)
        }
    }

    private fun MutableList<UgyldigSøknadsinnholdInput>.validerInntektOgPensjon(
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

    private fun MutableList<UgyldigSøknadsinnholdInput>.validerTekst(
        felt: String,
        verdi: String?,
        maksLengde: Int = STANDARD_MAKS_LENGDE,
    ) {
        if (verdi == null) return

        val begrunnelse = when {
            verdi.length > maksLengde -> "for lang verdi"
            verdi.inneholderForbudteKontrolltegn() -> "inneholder kontrolltegn"
            verdi.inneholderTegnUtenforTillattTegnsett() -> "inneholder tegn utenfor tillatt tegnsett"
            verdi.harMistenkeligInnhold() -> "inneholder mistenkelig innhold"
            else -> null
        }

        if (begrunnelse != null) {
            add(UgyldigSøknadsinnholdInput(felt, begrunnelse))
        }
    }

    private fun String.harMistenkeligInnhold(): Boolean {
        return mistenkeligeMønstre.any { it.containsMatchIn(this) }
    }

    // ASCII kontrolltegn
    private fun String.inneholderForbudteKontrolltegn(): Boolean {
        return this.any {
            (it.code in 0..31 && it != '\n' && it != '\r' && it != '\t') || it.code == 127
        }
    }

    private fun String.inneholderTegnUtenforTillattTegnsett(): Boolean {
        return this.any { !it.erTillattTegn() }
    }

    private fun Char.erTillattTegn(): Boolean {
        if (this == '\n' || this == '\r' || this == '\t') return true
        if (this in '0'..'9') return true
        if (this in 'a'..'z' || this in 'A'..'Z') return true
        if (this in setOf('æ', 'ø', 'å', 'Æ', 'Ø', 'Å')) return true
        if (this in tillatteSkilletegn) return true
        if (this in tillatteSpesialTegn) return true

        return false
    }
}
