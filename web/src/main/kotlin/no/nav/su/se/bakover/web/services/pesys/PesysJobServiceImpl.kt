package no.nav.su.se.bakover.web.services.pesys

import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.common.person.Fnr
import org.slf4j.LoggerFactory
import java.time.LocalDate

interface PesysJobService {
    fun hentDatafraPesys()
}

class PesysJobServiceImpl(
    private val client: PesysClient,
) : PesysJobService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentDatafraPesys() {
        log.info("Henter data fra pesys for hardkodet services")
        val hardkodetFnrs = listOf(
            "22503904369",
            "01416304056",
            "10435046563",
            "01445407670",
            "14445014177",
            "24415045545",
        ).map { Fnr(it) }

        client.hentVedtakForPersonPaaDatoAlder(hardkodetFnrs, LocalDate.now())
        log.info("Hentet data fra Pesys klient på dato ${LocalDate.now()}")
    }
}
