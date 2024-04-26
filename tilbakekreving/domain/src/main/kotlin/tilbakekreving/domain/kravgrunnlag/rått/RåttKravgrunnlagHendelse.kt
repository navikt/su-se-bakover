package tilbakekreving.domain.kravgrunnlag.rått

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.Hendelse
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.UUID

/**
 * En rå kravgrunnlagshendelse fra oppdrag, som vi ikke har knyttet til sak enda.
 *
 * @see [økonomi.domain.kvittering.RåUtbetalingskvitteringhendelse] for en tilsvarende hendelse som ikke er knyttet til sak.
 */
data class RåttKravgrunnlagHendelse(
    override val hendelseId: HendelseId,
    override val hendelsestidspunkt: Tidspunkt,
    val råttKravgrunnlag: RåttKravgrunnlag,
) : Hendelse<RåttKravgrunnlagHendelse> {
    // Vi tar ikke stilling til sammenhengen mellom kravgrunnlagshendelsene her, men lar heller den logikken ligge der vi knytter hendelsen til saken.
    override val tidligereHendelseId: HendelseId? = null
    override val entitetId: UUID = hendelseId.value
    override val versjon: Hendelsesversjon = Hendelsesversjon(1L)

    /**
     * Gir ikke så mye mening i dette tilfelle, da den krever at disse hendelsene er like.
     */
    override fun compareTo(other: RåttKravgrunnlagHendelse): Int {
        require(this.entitetId == other.entitetId)
        return this.versjon.compareTo(other.versjon)
    }

    companion object {
        fun fraPersistert(
            hendelseId: HendelseId,
            hendelsestidspunkt: Tidspunkt,
            forrigeVersjon: Hendelsesversjon,
            entitetId: UUID,
            råttKravgrunnlag: RåttKravgrunnlag,
        ): RåttKravgrunnlagHendelse {
            return RåttKravgrunnlagHendelse(
                hendelseId = hendelseId,
                hendelsestidspunkt = hendelsestidspunkt,
                råttKravgrunnlag = råttKravgrunnlag,
            ).also {
                require(it.entitetId == entitetId) {
                    "Den persistert entitetId var ulik den utleda fra domenet:${it.entitetId} vs. $entitetId. "
                }
                require(forrigeVersjon == Hendelsesversjon(1L)) {
                    "Den persistert versjon var ulik den utleda fra domenet:$forrigeVersjon vs. ${Hendelsesversjon(1L)}. "
                }
            }
        }
    }
}
