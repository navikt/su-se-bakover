package no.nav.su.se.bakover.domain

import arrow.core.NonEmptyList

data class InstitusjonsoppholdHendelserPåSak(
    private val hendelser: NonEmptyList<InstitusjonsoppholdHendelse>,
) : List<InstitusjonsoppholdHendelse> by hendelser {
    init {
        require(hendelser.sorted() == hendelser) { "krever at hendelsene er i sortert rekkefølge" }
        // disse blir også fanget på compareTo i sorted() over enn så lenge
        require(hendelser.distinctBy { it.sakId }.size == 1) { "Krever at alle hendelser har samme sakId" }
        require(hendelser.distinctBy { it.entitetId }.size == 1) { "Krever at alle hendelser har samme entitetId" }
        require(hendelser.distinctBy { it.entitetId } == hendelser.distinctBy { it.sakId }) { " Krever at sakId og entitetId er det samme" }
        require(hendelser.distinctBy { it.versjon.value }.size == hendelser.size) { "Krever at alle hendelser har ulik versjon" }
        require(hendelser.distinctBy { it.hendelseId.value }.size == hendelser.size) { "Krever at alle hendelser har ulik id" }
        hendelser.mapNotNull { it.tidligereHendelseId }.let {
            require(it.distinct() == it) { "Krever at hendelser ikke kan peke til samme tidligere hendelse" }
        }
    }

    fun sisteHendelse(): InstitusjonsoppholdHendelse = this.last()
}
