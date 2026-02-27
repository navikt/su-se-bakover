package no.nav.su.se.bakover.web.services.aap

import no.nav.su.se.bakover.client.aap.AapApiInternClient
import no.nav.su.se.bakover.common.person.Fnr
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate

interface AapJobService {
    fun hentMaksimum()
}

class AapJobServiceImpl(
    private val client: AapApiInternClient,
    private val clock: Clock,
) : AapJobService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentMaksimum() {
        log.info("AAP: Henter maksimum fra AAP-api-intern for hardkodede fnr")
        val hardkodetFnrs = listOf(
            "22503904369",
            "01416304056",
            "10435046563",
            "01445407670",
            "14445014177",
            "24415045545",
            "29518429009", // Begge skal ha løpende ytelse og har levert meldekort
            "27479517784", // Begge skal ha løpende ytelse og har levert meldekort
        ).map { Fnr(it) }

        val tilOgMedDato = LocalDate.now(clock)
        val fraOgMedDato = tilOgMedDato.withDayOfMonth(1)

        hardkodetFnrs.forEach { fnr ->
            client.hentMaksimum(
                fnr = fnr,
                fraOgMedDato = fraOgMedDato,
                tilOgMedDato = tilOgMedDato,
            ).fold(
                { err ->
                    log.warn("AAP: Feil ved henting av maksimum for fnr {}. {} - {}", fnr, err.httpStatus, err.message)
                },
                { response ->
                    log.info(
                        "AAP: Hentet maksimum for fnr {} fra {} til {}. Antall vedtak: {}",
                        fnr,
                        fraOgMedDato,
                        tilOgMedDato,
                        response.vedtak.size,
                    )
                },
            )
        }
    }
}
