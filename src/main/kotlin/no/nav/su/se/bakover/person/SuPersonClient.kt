package no.nav.su.se.bakover.person

import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.Environment.Companion.SU_PERSON_URL

class SuPersonClient {
    fun person(): String {
        val url = "$SU_PERSON_URL/isalive"
        val(_,_,result) = url.httpGet().responseString()
        return result.get()
    }
}