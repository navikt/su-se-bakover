package tilbakekreving.domain.forhåndsvarsel

import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

data class ForhåndsvarselMetaInfo(
    val id: UUID,
    val hendelsestidspunkt: Tidspunkt,
)
