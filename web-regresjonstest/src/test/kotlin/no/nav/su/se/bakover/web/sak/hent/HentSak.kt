package no.nav.su.se.bakover.web.sak.hent

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.whenever
import no.nav.su.se.bakover.test.application.defaultRequest
import org.json.JSONArray
import org.json.JSONObject

/**
 * TODO jah: Autentisering/Autorisering (Gjelder generelt for integrasjonsendepunktene).
 *  Denne skal f.eks. kun være tilgjengelig for saksbehandler/attestant.
 *  I tillegg er visse personer/saker beskyttet. Kode 6/7/Egen ansatt.
 */
internal fun hentSak(sakId: String, client: HttpClient): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Get,
            "/saker/$sakId",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ).apply {
            status shouldBe HttpStatusCode.OK
        }.bodyAsText()
    }
}

internal fun hentSakForFnr(fnr: String, sakstype: String = "uføre", client: HttpClient): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/søk",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) {
            //language=json
            setBody("""{"fnr":"$fnr", "type": "$sakstype", "saksnummer":null}""")
        }.apply {
            status shouldBe HttpStatusCode.OK
        }.bodyAsText()
    }
}

/**
 * Henter sakens id fra SakJson
 */
internal fun hentSakId(sakJson: String): String {
    return JSONObject(sakJson).get("id").toString()
}

internal fun hentSaksnummer(sakJson: String): String {
    return JSONObject(sakJson).getLong("saksnummer").toString()
}

/**
 * Henter sakens fnr fra SakJson
 */
internal fun hentFnr(sakJson: String): String {
    return JSONObject(sakJson).get("fnr").toString()
}

internal fun finnesSøknadId(sakJson: String, søknadId: String): Boolean {
    return hentSøknad(sakJson, søknadId) != null
}

/**
 * Henter første søknads id fra SakJson
 * @return null dersom lista er tom eller søknadId ikke finnes
 */
internal fun hentSøknad(sakJson: String, søknadId: String): String? {
    return JSONObject(sakJson).getJSONArray("søknader").let {
        if (it.isEmpty) {
            null
        } else {
            it.firstOrNull {
                (it as JSONObject).get("id").toString() == søknadId
            }?.toString()
        }
    }
}

internal fun hentReguleringer(sakJson: String): String {
    return JSONObject(sakJson).getJSONArray("reguleringer").toString()
}

internal fun String.hentReguleringMedId(id: String): String = hentReguleringer(this).let {
    JSONArray(it).filter {
        JSONObject(it.toString()).get("id").toString() == id
    }.let {
        it.whenever(
            isEmpty = {
                throw IllegalArgumentException("Fant ikke regulering med id $id")
            },
            isNotEmpty = {
                it.first().toString()
            },
        )
    }
}
