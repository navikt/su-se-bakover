package no.nav.su.se.bakover

//Random string resembling a FNR - totally not a valid FNR
object FødselsnummerGenerator {
    private val numbers: CharRange = '0'..'9'
    private val LENGHT = 11
    fun random() = (1..LENGHT)
            .map { numbers.random() }
            .joinToString("")
}