package tilbakekreving.domain.kravgrunnlag

enum class Kravgrunnlagstatus {
    Annulert,

    /** Kommentar jah: Gjetter på omg står for omgjøring. */
    AnnulertVedOmg,
    Avsluttet,
    Ferdigbehandlet,
    Endret,
    Feil,
    Manuell,
    Nytt,

    /** Skal tolkes som at det er sannsynlighet for at kravgrunnlaget kommer til å bli endret. Vedtak skal/bør ikke sendes til tilbakekrevingskomponenten i denne tilstanden. */
    Sperret,
    ;

    fun erÅpen(): Boolean {
        return this in listOf(Nytt, Endret)
    }
}
