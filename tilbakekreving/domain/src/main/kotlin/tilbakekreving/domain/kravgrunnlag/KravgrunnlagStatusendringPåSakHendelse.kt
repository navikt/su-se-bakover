package tilbakekreving.domain.kravgrunnlag

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import java.util.UUID

/**
 * I noen tilfeller endrer ikke selve kravgrunnlaget seg, men kun statusen.
 * I disse tilfellene vil vi få en [KravgrunnlagStatusendringPåSakHendelse].
 * Denne enkle meldingen har ikke et kontrollfelt som endringsmeldingene, så vi må bruke jmsTidspunktet for å sortere hendelsene.
 *
 * ### Eksempel 1 - Endring av feilutbetalte måneder:
 * Når det utføres endringer på feilutbetalte måneder, og Oppdrag ikke umiddelbart beregner resultatet, vil Oppdrag initielt sende oss statusen [Kravgrunnlagstatus.Sperret].
 * Dette indikerer at en beregning pågår.
 * Etter at oppdrag har fullført beregningen, som ofte skjer innen samme dag, oppdateres statusen basert på resultatet av beregningen:
 *   - Hvis kravgrunnlaget ikke har endret seg som følge av beregningen, vil statusen oppdateres til ENDR, som reflekterer ingen endring i kravgrunnlaget.
 *   - Hvis det derimot er en endring i kravgrunnlaget etter beregningen, vil Oppdrag sende et oppdatert [Kravgrunnlag] med statusen ENDR, som indikerer at det har skjedd en endring.
 * Det er viktig å merke seg at systemet ikke vil generere en ekstra statusendring; det er en enten-eller situasjon basert på resultatet av beregningen.
 * ### Eksempel 2 - Kansellering av feilutbetalte måneder:
 * Dersom endringen av kravgrunnlaget fører til at det ikke er noen feilutbetaling igjen, vil vi få en status AVSL.
 *
 * TODO jah: Utvid denne beskrivelsen med flere eksempler etter hvert som de dukker opp i preprod/produksjon.
 */
data class KravgrunnlagStatusendringPåSakHendelse(
    override val hendelseId: HendelseId,
    override val versjon: Hendelsesversjon,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val tidligereHendelseId: HendelseId,
    override val saksnummer: Saksnummer,

    /** Dette er en ekstern id som genereres og eies av Oppdrag. Den er transient i vårt system. Endringer/statusendringer på et kravgrunnlag vil ha samme id. */
    override val eksternVedtakId: String,

    /** TODO jah: Antar at ikke alle disse statusene kan oppstå for statusendringer. Noen brukes kun av kravgrunnlaget. */
    override val status: Kravgrunnlagstatus,
    override val eksternTidspunkt: Tidspunkt,
) : KravgrunnlagPåSakHendelse {
    init {
        require(versjon.value > 1L) {
            "Bare en liten guard mot å opprette en hendelse med versjon 1, siden den er reservert for SakOpprettetHendelse"
        }
    }

    override val entitetId: UUID = sakId
    override fun compareTo(other: Sakshendelse): Int {
        require(this.entitetId == other.entitetId)
        return this.versjon.compareTo(other.versjon)
    }

    companion object {
        fun fraPersistert(
            hendelseId: HendelseId,
            hendelsestidspunkt: Tidspunkt,
            forrigeVersjon: Hendelsesversjon,
            entitetId: UUID,
            sakId: UUID,
            tidligereHendelseId: HendelseId,
            status: Kravgrunnlagstatus,
            eksternVedtakId: String,
            saksnummer: Saksnummer,
            eksternTidspunkt: Tidspunkt,
        ): KravgrunnlagStatusendringPåSakHendelse {
            return KravgrunnlagStatusendringPåSakHendelse(
                hendelseId = hendelseId,
                hendelsestidspunkt = hendelsestidspunkt,
                sakId = sakId,
                versjon = forrigeVersjon,
                tidligereHendelseId = tidligereHendelseId,
                status = status,
                eksternVedtakId = eksternVedtakId,
                saksnummer = saksnummer,
                eksternTidspunkt = eksternTidspunkt,
            ).also {
                require(it.entitetId == entitetId) {
                    "Den persistert entitetId var ulik den utleda fra domenet:${it.entitetId} vs. $entitetId."
                }
            }
        }
    }
}
