package no.nav.su.se.bakover.web.services.pesys

import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.common.person.Fnr
import org.slf4j.LoggerFactory
import java.time.LocalDate

interface PesysJobService {
    fun hentDataFraAlder()
    fun hentDataFraUføre()
}

class PesysJobServiceImpl(
    private val client: PesysClient,
) : PesysJobService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentDataFraAlder() {
        log.info("ALDER: Henter data fra pesys for hardkodet fnrer alder")
        // TODO: Dette er testdata fra Dolly
        val hardkodetFnrs = listOf(
            "22503904369",
            "01416304056",
            "10435046563",
            "01445407670",
            "14445014177",
            "24415045545",
        ).map { Fnr(it) }

        val result = client.hentVedtakForPersonPaaDatoAlder(hardkodetFnrs, LocalDate.now())
        result.map { result ->
            log.info("ALDER: Hentet data fra Pesys klient på dato ${LocalDate.now()} antall vedtak ${result.resultat.size}")
        }
    }

    override fun hentDataFraUføre() {
        log.info("UFØRE: Henter data fra pesys for hardkodet fnrer")
        val result = client.hentVedtakForPersonPaaDatoUføre(emptyList(), LocalDate.now())
        result.map { result ->
            log.info("UFØRE: Hentet data fra Pesys klient på dato ${LocalDate.now()} antall vedtak ${result.resultat.size}")
        }
    }
}
