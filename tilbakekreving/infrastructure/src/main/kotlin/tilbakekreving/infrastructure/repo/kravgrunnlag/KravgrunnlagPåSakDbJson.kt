package tilbakekreving.infrastructure.repo.kravgrunnlag

import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagDetaljerPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagStatusendringPåSakHendelse

fun KravgrunnlagPåSakHendelse.toDbJson(): String {
    return when (this) {
        is KravgrunnlagDetaljerPåSakHendelse -> this.toJson()
        is KravgrunnlagStatusendringPåSakHendelse -> this.toJson()
    }
}

fun PersistertHendelse.toKravgrunnlagPåSakHendelse(): KravgrunnlagPåSakHendelse {
    return if (this.data.contains("kravgrunnlag")) {
        this.toKravgrunnlagDetaljerPåSakHendelse()
    } else {
        this.toKravgrunnlagStatusendringPåSakHendelse()
    }
}
