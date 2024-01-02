package tilbakekreving.domain.kravgrunnlag

enum class Kravgrunnlagstatus {
    Annullert,

    /** Kommentar jah: Gjetter på omg står for omgjøring. */
    AnnullertVedOmg,
    Avsluttet,
    Ferdigbehandlet,
    Endret,
    Feil,
    Manuell,
    Nytt,

    /** Skal tolkes som at det er sannsynlighet for at kravgrunnlaget kommer til å bli endret. Vedtak skal/bør ikke sendes til tilbakekrevingskomponenten i denne tilstanden. */
    Sperret,
    ;

    /**
     * Dersom kravgrunnlaget er nytt eller endret.
     *
     * Sperret inngår verken i åpen eller avsluttet.
     */
    fun erÅpen(): Boolean {
        return this in listOf(Nytt, Endret)
    }

    /**
     * Dersom kravgrunnlaget er avsluttet, ferdigbehandlet, annullert, feil eller manuell.
     *
     * Sperret inngår verken i åpen eller avsluttet.
     */
    fun erAvsluttet(): Boolean {
        return this in listOf(Avsluttet, Ferdigbehandlet, Annullert, AnnullertVedOmg, Feil, Manuell)
    }
}
