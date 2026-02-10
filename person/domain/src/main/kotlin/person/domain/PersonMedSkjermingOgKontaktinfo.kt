package person.domain

data class PersonMedSkjermingOgKontaktinfo(
    val person: Person,
    val skjermet: Boolean,
    val kontaktinfo: Kontaktinfo?,
)
