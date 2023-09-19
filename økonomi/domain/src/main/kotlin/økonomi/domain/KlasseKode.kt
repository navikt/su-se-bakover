package økonomi.domain

enum class KlasseKode {
    SUUFORE,
    KL_KODE_FEIL_INNT,
    TBMOTOBS,
    FSKTSKAT,

    /** Filtreres vekk av klient, bakoverkompatabilitet */
    UFOREUT,

    SUALDER,
    KL_KODE_FEIL,
    ;

    companion object {
        fun skalIkkeFiltreres(): List<String> {
            return setOf(SUUFORE, KL_KODE_FEIL_INNT, SUALDER, KL_KODE_FEIL, TBMOTOBS).map { it.name }
        }

        fun contains(value: String): Boolean {
            return entries.map { it.name }.contains(value)
        }
    }
}
