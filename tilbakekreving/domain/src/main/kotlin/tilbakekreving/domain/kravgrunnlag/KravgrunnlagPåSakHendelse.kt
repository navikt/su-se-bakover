package tilbakekreving.domain.kravgrunnlag

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import java.util.UUID

/**
 * Vi knytter et kravgrunnlag mot en sak, utbetaling og potensielt en revurdering (dersom vi behandlet tilbakekrevingen i revurderingen)
 *
 * @param tidligereHendelseId Hendelsen vi mottok på køen fra Oppdrag. Her vil den originale meldingen ligge. Kan leses som at denne hendelsen erstatter den forrige, selvom entitetId/sakId og versjon ikke vil henge sammen i dette tilfellet. Dette vil gjøre det enklere og debugge sammenhengen mellom disse hendelsene..
 * @param eksternKravgrunnlagId Dette er en ekstern id som genereres og eies av Oppdrag. Den er transient i vårt system.
 * @param eksternVedtakId Dette er en ekstern id som genereres og eies av Oppdrag. Den er transient i vårt system.
 * @param eksternKontrollfelt Denne er generert av Oppdrag og er vedlagt i kravgrunnlaget, den er transient i vårt system. Kan være på formatet: 2023-09-19-10.01.03.842916.
 * @param behandler Transient felt. Saksbehandleren/Attestanten knyttet til vedtaket/utbetalinga. Utbetalinga vår kaller dette behandler, så vi gjenbruker det her. Oppdrag har ikke skillet mellom saksbehandler/attestant (men bruker ofte ordet saksbehandler).
 * @param utbetalingId Mappes fra referansefeltet i kravgrunnlaget. En referanse til utbetalingId (vår) som førte til opprettelse/endring av dette kravgrunnlaget. Usikker på om denne kan være null dersom det var en manuell endring som førte til opprettelse av kravgrunnlaget.
 * @param grunnlagsmåneder En eller flere måneder kravgrunnlaget knyttes mot. Antar at det finnes minst ett element i lista.
 * @param revurderingId Dersom vi har behandlet tilbakekrevingen i en revurdering, så vil denne peke på revurderingen. Dersom denne er tilstede, ønsker vi ikke å starte en separat tilbakekrevingsbehandling.
 */
data class KravgrunnlagPåSakHendelse(
    override val hendelseId: HendelseId,
    override val versjon: Hendelsesversjon,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val meta: DefaultHendelseMetadata,
    override val tidligereHendelseId: HendelseId,
    val kravgrunnlag: Kravgrunnlag,
    val revurderingId: UUID?,
) : Sakshendelse {
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
            hendelseMetadata: DefaultHendelseMetadata,
            forrigeVersjon: Hendelsesversjon,
            entitetId: UUID,
            sakId: UUID,
            tidligereHendelseId: HendelseId,
            kravgrunnlag: Kravgrunnlag,
            revurderingId: UUID?,
        ): KravgrunnlagPåSakHendelse {
            return KravgrunnlagPåSakHendelse(
                hendelseId = hendelseId,
                hendelsestidspunkt = hendelsestidspunkt,
                meta = hendelseMetadata,
                sakId = sakId,
                versjon = forrigeVersjon,
                tidligereHendelseId = tidligereHendelseId,
                kravgrunnlag = kravgrunnlag,
                revurderingId = revurderingId,
            ).also {
                require(it.entitetId == entitetId) {
                    "Den persistert entitetId var ulik den utleda fra domenet:${it.entitetId} vs. $entitetId."
                }
            }
        }
    }
}
