package no.nav.su.se.bakover.web.services.pesys

import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.common.person.Fnr
import java.time.LocalDate

interface PesysJobService {
    fun hentDatafraPesys()
}

class PesysJobServiceImpl(
    private val client: PesysClient,
) : PesysJobService {
    override fun hentDatafraPesys() {
        val hardkodetFnrs = listOf(
            "22503904369",
            "01416304056",
            "10435046563",
            "01445407670",
            "14445014177",
            "24415045545",
        ).map { Fnr(it) }
        client.hentVedtakForPersonPaaDatoAlder(hardkodetFnrs, LocalDate.now())
    }
}
