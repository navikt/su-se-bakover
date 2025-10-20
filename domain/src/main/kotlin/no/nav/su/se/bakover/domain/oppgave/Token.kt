package no.nav.su.se.bakover.domain.oppgave

interface Token {
    val value: String
}

data class SystembrukerToken(override val value: String) : Token {
    override fun toString(): String = this.value
}

data class OboToken(override val value: String) : Token {
    override fun toString(): String = this.value
}
