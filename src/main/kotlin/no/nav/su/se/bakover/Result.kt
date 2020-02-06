package no.nav.su.se.bakover

internal sealed class Result
internal class Ok(val json: String) : Result() {
    override fun equals(other: Any?): Boolean = other is Ok && other.json == this.json
    override fun hashCode(): Int = json.hashCode()
}

internal class Feil(val httpCode: Int, val message: String) : Result() {
    fun toJson() = """{"message":"$message"}"""
    override fun toString() = "Feil: ${toJson()}"
    override fun equals(other: Any?) =
        other is Feil && other.httpCode == this.httpCode && other.message == this.message

    override fun hashCode(): Int = 31 * httpCode + message.hashCode()
}
