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
 * @param revurderingId Dersom vi har behandlet tilbakekrevingen i en revurdering, så vil denne peke på revurderingen. Dersom denne er tilstede, ønsker vi ikke å starte en separat tilbakekrevingsbehandling.
 */
data class KravgrunnlagDetaljerPåSakHendelse(
    override val hendelseId: HendelseId,
    override val versjon: Hendelsesversjon,
    override val sakId: UUID,
    override val hendelsestidspunkt: Tidspunkt,
    override val meta: DefaultHendelseMetadata,
    override val tidligereHendelseId: HendelseId,
    val kravgrunnlag: Kravgrunnlag,
    val revurderingId: UUID?,
) : KravgrunnlagPåSakHendelse {
    init {
        require(versjon.value > 1L) {
            "Bare en liten guard mot å opprette en hendelse med versjon 1, siden den er reservert for SakOpprettetHendelse"
        }
    }
    override val entitetId: UUID = sakId
    override val saksnummer = kravgrunnlag.saksnummer
    override val eksternVedtakId = kravgrunnlag.eksternVedtakId
    override val status = kravgrunnlag.status
    override val eksternTidspunkt = kravgrunnlag.eksternTidspunkt

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
        ): KravgrunnlagDetaljerPåSakHendelse {
            require(kravgrunnlag.hendelseId == hendelseId) {
                "Den persisterte hendelseId $hendelseId var ulik den på kravgrunnlaget ${kravgrunnlag.hendelseId}."
            }
            return KravgrunnlagDetaljerPåSakHendelse(
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
