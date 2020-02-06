package no.nav.su.se.bakover

import io.ktor.application.ApplicationCall
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond

internal sealed class Resultat {
    abstract suspend fun svar(call: ApplicationCall): Unit
}

internal class Ok(private val json: String) : Resultat() {
    override fun equals(other: Any?): Boolean = other is Ok && other.json == this.json
    override fun hashCode(): Int = json.hashCode()
    override suspend fun svar(call: ApplicationCall) = call.respond(HttpStatusCode.OK, json)
}

internal class Feil(private val httpCode: Int, private val message: String) : Resultat() {
    private fun toJson() = """{"message":"$message"}"""
    override fun equals(other: Any?) = other is Feil && other.httpCode == this.httpCode && other.message == this.message
    override fun hashCode(): Int = 31 * httpCode + message.hashCode()
    override suspend fun svar(call: ApplicationCall) = call.respond(HttpStatusCode.fromValue(httpCode), toJson())
}

internal suspend fun ApplicationCall.svar(resultat: Resultat) = resultat.svar(this)