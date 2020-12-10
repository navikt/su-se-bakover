package no.nav.su.se.bakover.service

import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.domain.Fnr

/**
 * Inneholder sjekk på fødselsnumre i preprod som har historikk i Oppdrag/Økonomi som vi har mistet referansen til og ikke lenger vil virke.
 * Typisk pgr. sletting av databasen i preprod eller manuelle utbetalingskall mot oppdrag.
 */
class BrenteFnrIOppdragPreprodValidator(
    val config: Config = Config
) {

    // Sortert
    private val brenteFødselsnumreIOppdragPreprod = listOf(
        "02057512841",
        "02067324016",
        "07028820547", // NYDELIG KRONJUVEL (fra før første wipe)
        "03096123948",
        "03097822947",
        "05086819408",
        "07068619925",
        "08116821614",
        "10029020545",
        "10067121878",
        "11047120871",
        "12017822606",
        "12107322408", // STOR BÆREPOSE (fra før første wipe)
        "12116919239",
        "16057000124",
        "16118120187",
        "17026522439",
        "17027021417",
        "17049221738",
        "18098019304",
        "18125923336",
        "19017524103",
        "19038421800",
        "19058420916",
        "19118423103",
        "20128127969", // LUR MYGG (fra før første wipe)
        "21036812556",
        "22017219425",
        "22046514029",
        "23036721510",
        "23088800133",
        "23106326401",
        "25117822632",
        "26128920004",
        "28038517823",
        "31017622172",
    )

    /**
     * Kaster BrentFnrIOppdragPreprod dersom vi er i preprod og fødselsnummeret er brent der.
     */
    fun assertUbrentFødselsnummerIOppdragPreprod(fnr: Fnr) {
        if (config.isPreprod && brenteFødselsnumreIOppdragPreprod.contains(fnr.toString())) {
            throw BrentFnrIOppdragPreprod(fnr)
        }
    }
}

class BrentFnrIOppdragPreprod(val fnr: Fnr) : RuntimeException()
