package økonomi.domain

enum class KlasseKode {
    SUUFORE,
    KL_KODE_FEIL_INNT, // Sakstype.UFORE
    TBMOTOBS,
    FSKTSKAT,

    /** Filtreres vekk av klient, bakoverkompatabilitet */
    UFOREUT,

    SUALDER,
    KL_KODE_FEIL, // Sakstype.ALDER

    KL_KODE_JUST_PEN,
    KREDKRED,
    TBTREKK,

    ;

    companion object {
        fun skalIkkeFiltreres(): List<String> {
            return setOf(
                SUUFORE,
                KL_KODE_FEIL_INNT,
                SUALDER,
                KL_KODE_FEIL,
                TBMOTOBS,
                KREDKRED,
                TBTREKK,
            ).map { it.name }
        }

        fun contains(value: String): Boolean {
            return entries.map { it.name }.contains(value)
        }
    }
}
