package økonomi.domain

enum class KlasseType {
    YTEL,
    SKAT,
    FEIL,
    MOTP,
    TREK,
    JUST, // Justeringskonto
    ;

    companion object {
        fun skalIkkeFiltreres(): List<String> {
            return setOf(YTEL, FEIL, MOTP, TREK).map { it.name }
        }

        fun contains(value: String): Boolean {
            return entries.map { it.name }.contains(value)
        }
    }
}
