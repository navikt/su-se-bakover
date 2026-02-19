package dokument.domain

enum class Brevtype {
    VEDTAK,
    FORHANDSVARSEL,
    INNKALLING_KONTROLLSAMTALE,
    PÅMINNELSE_NY_STØNADSPERIODE,
    KLAGE,
    REVURDERING_AVSLUTTET,
    SØKNAD_TRUKKET,
    AVVIST_SØKNAD,
    FRITEKST_VEDTAK,
    FRITEKST_VIKTIG,
    FRITEKST_ANNET,
    OPPLASTET_PDF_VEDTAK,
    OPPLASTET_PDF_VIKTIG,
    OPPLASTET_PDF_ANNET,

    // Legacy-verdi for historiske rader/gradvis migrering.
    ANNET,
    ;

    companion object {
        private val byName = entries.associateBy { it.name }

        fun fraString(verdi: String): Brevtype? {
            return byName[verdi.trim().uppercase()]
        }
    }
}
