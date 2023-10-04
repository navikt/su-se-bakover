package tilbakekreving.infrastructure

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlagHendelse

data class RåttKravgrunnlagDbJson(
    val råttKravgrunnlag: String,
) {
    companion object {
        fun RåttKravgrunnlagHendelse.toJson(): String {
            return RåttKravgrunnlagDbJson(
                råttKravgrunnlag = this.råttKravgrunnlag.melding,
            ).let {
                serialize(it)
            }
        }

        fun PersistertHendelse.toRåttKravgrunnlagHendelse(): RåttKravgrunnlagHendelse {
            require(this.sakId == null) {
                "Uprosessert kravgrunnlag skal ikke ha sakId, men var $sakId"
            }
            require(this.tidligereHendelseId == null) {
                "Uprosessert kravgrunnlag skal ikke ha tidligereHendelseId, men var $tidligereHendelseId"
            }
            return deserialize<RåttKravgrunnlagDbJson>(this.data).let { json ->
                RåttKravgrunnlagHendelse.fraPersistert(
                    hendelseId = this.hendelseId,
                    hendelsestidspunkt = this.hendelsestidspunkt,
                    jmsHendelseMetadata = this.jmsHendelseMetadata(),
                    forrigeVersjon = this.versjon,
                    entitetId = this.entitetId,
                    råttKravgrunnlag = RåttKravgrunnlag(json.råttKravgrunnlag),
                )
            }
        }
    }
}
