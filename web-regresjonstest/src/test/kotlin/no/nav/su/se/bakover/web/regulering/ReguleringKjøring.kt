package no.nav.su.se.bakover.web.regulering

import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.domain.regulering.Reguleringsvariant
import no.nav.su.se.bakover.test.application.defaultRequest

internal fun hentReguleringKjøringRequest(client: HttpClient, variant: Reguleringsvariant = Reguleringsvariant.GRUNNBELØP): List<ReguleringKjøring> {
    return runBlocking {
        defaultRequest(
            HttpMethod.Get,
            "/reguleringer/test/kjøringer",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ).let {
            if (it.status != HttpStatusCode.OK) {
                throw IllegalStateException("Fant ikke reguleringskjøring: ${it.bodyAsText()}")
            }
            when (variant) {
                Reguleringsvariant.GRUNNBELØP -> deserialize<List<ReguleringKjøring.Grunnbeløp>>(it.bodyAsText())
                Reguleringsvariant.ALDERSFRADRAG -> deserialize<List<ReguleringKjøring.Aldersfradrag>>(it.bodyAsText())
            }
        }
    }
}

// NB hard casting her men må stemme med return i metode over
internal fun hentReguleringKjøringRequestGrunnbeløp(client: HttpClient): List<ReguleringKjøring.Grunnbeløp> {
    val kjøring = hentReguleringKjøringRequest(client, Reguleringsvariant.GRUNNBELØP)
    return kjøring.map {
        it as? ReguleringKjøring.Grunnbeløp
            ?: throw IllegalStateException("hentReguleringKjøringRequestGrunnbeløp henter feil type: ${it::class.simpleName}")
    }
}
internal fun hentReguleringKjøringRequestAlderfradrag(client: HttpClient): List<ReguleringKjøring.Aldersfradrag> {
    val kjøring = hentReguleringKjøringRequest(client, Reguleringsvariant.ALDERSFRADRAG)
    return kjøring.map {
        it as? ReguleringKjøring.Aldersfradrag
            ?: throw IllegalStateException("hentReguleringKjøringRequestAlderfradrag henter feil type: ${it::class.simpleName}")
    }
}
