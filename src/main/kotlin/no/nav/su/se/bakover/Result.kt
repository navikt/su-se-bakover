package no.nav.su.se.bakover

internal sealed class Result
internal class Ok(val json: String) : Result()
internal class Feil(val httpCode: Int, val message: String) : Result()
