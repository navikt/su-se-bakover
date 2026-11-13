package dokument.domain.generer.bygger.maler.skattegrunnlag

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

data class SkattegrunnlagsPdfInnhold(
    val saksnummer: String?,
    val behandlingstype: BehandlingstypeForSkattemelding,
    override val sakstype: Sakstype,
    val behandlingsId: UUID?,
    val vedtaksId: UUID?,
    val hentet: Tidspunkt,
    val opprettet: Tidspunkt,
    val søkers: SkattPdfDataJson?,
    val eps: SkattPdfDataJson?,
    val begrunnelse: String?,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.Skattegrunnlag(
        nyTittel = if (søkers == null && eps != null) "Skattegrunnlag - EPS" else null,
    )
}

val skattegrunnlag = { tittel: String, pdfInnhold: SkattegrunnlagsPdfInnhold ->
    TODO()
}
