package tilbakekreving.domain.kravgrunnlag.påsak

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlagstatus

/**
 * Kravgrunnlagshendelser er delt opp i 2 varianter:
 * - [KravgrunnlagDetaljerPåSakHendelse] inneholder hele kravgrunnlaget og statusen.
 * - [KravgrunnlagStatusendringPåSakHendelse] inneholder kun statusendringen.
 */
sealed interface KravgrunnlagPåSakHendelse : Sakshendelse {
    val saksnummer: Saksnummer

    /** Dette er en ekstern id som genereres og eies av Oppdrag. Den er transient i vårt system. Endringer/statusendringer på et kravgrunnlag vil ha samme id. */
    val eksternVedtakId: String

    val status: Kravgrunnlagstatus

    /** For kravgrunnlagdetaljer får vi med et kontrollfelt som mappes om til [eksternTidspunkt], men for statusendringer bruker vi JMStimestamp. */
    val eksternTidspunkt: Tidspunkt
}
