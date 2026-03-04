package dokument.domain

enum class Brevtype {
    VEDTAK,
    FORHANDSVARSEL,
    OVERSENDELSE_KA,
    INNKALLING_KONTROLLSAMTALE,
    PÅMINNELSE_NY_STØNADSPERIODE,

    // Legacy-verdi for historiske rader/gradvis migrering.
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

    ANNET,
    ;

    companion object {
        private val byName = entries.associateBy { it.name }

        fun fraString(verdi: String): Brevtype? {
            return byName[verdi.trim().uppercase()]
        }
    }
}
