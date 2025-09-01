package statistikk.domain

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.common.domain.JaNei
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import statistikk.domain.StønadstatistikkDto.Stønadstype
import statistikk.domain.StønadstatistikkDto.Vedtaksresultat
import statistikk.domain.StønadstatistikkDto.Vedtakstype
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class StønadstatistikkMåned(
    val id: UUID,
    val måned: YearMonth,
    val funksjonellTid: Tidspunkt,
    val tekniskTid: Tidspunkt,
    val sakId: UUID,
    val stonadstype: Stønadstype,
    val personnummer: Fnr,
    val personNummerEps: Fnr? = null,
    val vedtaksdato: LocalDate,
    val vedtakstype: Vedtakstype,
    val vedtaksresultat: Vedtaksresultat,
    val vedtakFraOgMed: LocalDate,
    val vedtakTilOgMed: LocalDate,
    val opphorsgrunn: String? = null,
    val opphorsdato: LocalDate? = null,

    val harUtenlandsOpphold: JaNei?,
    val harFamiliegjenforening: JaNei?,
    val flyktningsstatus: JaNei?,

    val årsakStans: String? = null,

    val behandlendeEnhetKode: String,
    val månedsbeløp: StønadstatistikkDto.Månedsbeløp,
)
