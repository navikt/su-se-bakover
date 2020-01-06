package no.nav.su.se.bakover.person

import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.Environment

class SuPersonClient(private val environment: Environment) {
    fun person(): String {
        val url = "${environment.suPersonUrl}/isalive"
        val (_, _, result) = url.httpGet().responseString()
        return result.get()
    }
}