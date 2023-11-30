package økonomi.domain

enum class Fagområde {
    SUALDER,
    SUUFORE,
    ;

    companion object {
        fun valuesAsStrings(): List<String> {
            return entries.map { it.name }
        }
    }
}
