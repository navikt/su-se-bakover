package dokument.domain

enum class Brevtype {
    VEDTAK,
    FORHANDSVARSEL,
    ANNET,
    ;

    companion object {
        private val byName = entries.associateBy { it.name }

        fun fraString(verdi: String): Brevtype? {
            return byName[verdi.trim().uppercase()]
        }
    }
}
