package tilbakekreving.presentation.api.common

import no.nav.su.se.bakover.common.tid.Tidspunkt
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarselMetaInfo
import java.util.UUID

data class ForhåndsvarselMetaInfoJson(
    val id: UUID,
    val hendelsestidspunkt: Tidspunkt,
) {
    companion object {
        fun ForhåndsvarselMetaInfo.toJson(): ForhåndsvarselMetaInfoJson =
            ForhåndsvarselMetaInfoJson(id = this.id, hendelsestidspunkt = this.hendelsestidspunkt)

        fun List<ForhåndsvarselMetaInfo>.toJson(): List<ForhåndsvarselMetaInfoJson> = this.map { it.toJson() }
    }
}
