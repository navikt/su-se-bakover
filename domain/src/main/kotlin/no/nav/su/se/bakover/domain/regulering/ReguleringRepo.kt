package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Saksnummer
import java.time.LocalDate
import java.util.UUID

data class VedtakSomKanReguleres(
    val sakId: UUID,
    val saksnummer: Saksnummer,
    val opprettet: Tidspunkt,
    val behandlingId: UUID,
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val vedtakType: VedtakType,
    val reguleringType: ReguleringType,
)

enum class ReguleringType {
    AUTOMATISK,
    MANUELL,
}

enum class VedtakType {
    SØKNAD,
    AVSLAG,
    ENDRING,
    REGULERING,
    INGEN_ENDRING,
    OPPHØR,
    STANS_AV_YTELSE,
    GJENOPPTAK_AV_YTELSE,
    AVVIST_KLAGE,
}

interface ReguleringRepo {
    fun hent(id: UUID): Regulering?
    fun hent(saksnummer: Saksnummer, jobbnavn: String): Regulering?
    fun hent(jobbnavn: Reguleringsjobb): List<Regulering>
    fun hentForSakId(sakId: UUID, sessionContext: TransactionContext = defaultTransactionContext()): List<Regulering>
    fun lagre(regulering: Regulering, sessionContext: TransactionContext = defaultTransactionContext())
    fun hentVedtakSomKanReguleres(fraOgMed: LocalDate): List<VedtakSomKanReguleres>
    fun defaultTransactionContext(): TransactionContext
}
