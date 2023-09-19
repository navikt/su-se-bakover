package økonomi.domain

enum class KlasseType {
    YTEL,
    SKAT,
    FEIL,
    MOTP,
    ;

    companion object {
        fun skalIkkeFiltreres(): List<String> {
            return setOf(YTEL, FEIL, MOTP).map { it.name }
        }

        fun contains(value: String): Boolean {
            return entries.map { it.name }.contains(value)
        }
    }
}
