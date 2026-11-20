package person.domain

enum class Adressebeskyttelse {
    UGRADERT,
    FORTROLIG,
    STRENGT_FORTROLIG,
    STRENGT_FORTROLIG_UTLAND,
    ;

    fun erKode6(): Boolean = when (this) {
        STRENGT_FORTROLIG, STRENGT_FORTROLIG_UTLAND -> true
        UGRADERT, FORTROLIG -> false
    }

    companion object {
        fun fraPdl(gradering: String?): Adressebeskyttelse =
            gradering?.let { valueOf(it) } ?: UGRADERT
    }
}
