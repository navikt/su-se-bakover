package no.nav.su.se.bakover.web.services.fradragssjekken

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal data class SjekkPlan(
    val sak: SakInfo,
    val sjekkpunkter: List<Sjekkpunkt>,
)

internal data class Sjekkpunkt(
    val fnr: Fnr,
    val tilhører: FradragTilhører,
    val fradragstype: Fradragstype,
    val ytelse: EksternYtelse,
    val lokaltBeløp: Double?,
)

internal data class LøpendeSakForMåned(
    val sak: SakInfo,
    val gjeldendeMånedsutbetaling: Int,
)

internal data class SjekkgrunnlagForSak(
    val sjekkplan: SjekkPlan,
    val gjeldendeVedtaksdata: GjeldendeVedtaksdata,
    val gjeldendeMånedsutbetaling: Int,
)

internal enum class EpsKategori {
    UNDER_SEKSTISYV,
    SEKSTISYV_ELLER_ELDRE,
}

internal enum class EksternYtelse(val ytelseNavn: String) {
    AAP("AAP"),
    PESYS_ALDER("Pesys alder"),
    PESYS_UFORE("Pesys uføre"),
}

internal sealed interface EksterntOppslag {
    data class Funnet(val beløp: Double) : EksterntOppslag
    data object IngenTreff : EksterntOppslag
    data class Feil(val grunn: String) : EksterntOppslag
}

internal data class EksterneOppslagsresultater(
    val aap: Map<Fnr, EksterntOppslag>,
    val pesysAlder: Map<Fnr, EksterntOppslag>,
    val pesysUføre: Map<Fnr, EksterntOppslag>,
) {
    fun finnYtelseForPerson(sjekkpunkt: Sjekkpunkt): EksterntOppslag {
        return when (sjekkpunkt.ytelse) {
            EksternYtelse.AAP -> aap[sjekkpunkt.fnr]
            EksternYtelse.PESYS_ALDER -> pesysAlder[sjekkpunkt.fnr]
            EksternYtelse.PESYS_UFORE -> pesysUføre[sjekkpunkt.fnr]
        } ?: throw ManglerLagretOppslagsresultatException(sjekkpunkt)
    }
}

internal class ManglerLagretOppslagsresultatException(
    sjekkpunkt: Sjekkpunkt,
) : IllegalStateException(
    "Mangler lagret oppslagsresultat for ytelse=${sjekkpunkt.ytelse}, fnr=${sjekkpunkt.fnr}",
)

internal data class FradragssjekkResultat(
    val saksresultater: List<FradragssjekkSakResultat> = emptyList(),
)

internal data class FradragssjekkKjøring(
    val id: UUID,
    val dato: LocalDate,
    val dryRun: Boolean,
    val status: FradragssjekkKjøringStatus,
    val opprettet: Instant,
    val ferdigstilt: Instant,
    val resultat: FradragssjekkResultat,
    val feilmelding: String? = null,
)

internal enum class FradragssjekkKjøringStatus {
    FULLFØRT,
    FEILET,
}

internal data class FradragssjekkSakResultat(
    val sakId: UUID,
    val status: FradragssjekkSakStatus,
    val sjekkplan: SjekkPlanData,
    val oppgaveAvvik: List<Fradragsfunn.Oppgaveavvik> = emptyList(),
    val observasjoner: List<Fradragsfunn.Observasjon> = emptyList(),
    val opprettetOppgave: OppgaveopprettelseResultat.Opprettet? = null,
    val mislykketOppgaveopprettelse: MislykketOppgaveopprettelse? = null,
    val eksterneFeil: List<EksternFeilPåSjekkpunkt> = emptyList(),
    val feilmelding: String? = null,
)

internal enum class FradragssjekkSakStatus {
    INGEN_AVVIK,
    KUN_OBSERVASJON,
    EKSTERN_FEIL,
    OPPGAVE_IKKE_OPPRETTET_DRY_RUN,
    OPPGAVE_OPPRETTET,
    OPPGAVEOPPRETTELSE_FEILET,
    INVARIANTBRUDD,
}

internal data class EksternFeilPåSjekkpunkt(
    val sjekkpunkt: SjekkpunktData,
    val grunn: String,
)

internal data class SjekkPlanData(
    val sak: SakInfo,
    val sjekkpunkter: List<SjekkpunktData>,
) {
    fun tilDomain(): SjekkPlan {
        return SjekkPlan(
            sak = sak,
            sjekkpunkter = sjekkpunkter.map { it.tilDomain() },
        )
    }

    companion object {
        fun fraDomain(
            sjekkplan: SjekkPlan,
        ): SjekkPlanData {
            return SjekkPlanData(
                sak = sjekkplan.sak,
                sjekkpunkter = sjekkplan.sjekkpunkter.map { SjekkpunktData.fraDomain(it) },
            )
        }
    }
}

internal data class SjekkpunktData(
    val fnr: Fnr,
    val tilhører: FradragTilhører,
    val fradragstype: FradragstypeData,
    val ytelse: EksternYtelse,
    val lokaltBeløp: Double?,
) {
    fun tilDomain(): Sjekkpunkt {
        return Sjekkpunkt(
            fnr = fnr,
            tilhører = tilhører,
            fradragstype = fradragstype.tilDomain(),
            ytelse = ytelse,
            lokaltBeløp = lokaltBeløp,
        )
    }

    companion object {
        fun fraDomain(
            sjekkpunkt: Sjekkpunkt,
        ): SjekkpunktData {
            return SjekkpunktData(
                fnr = sjekkpunkt.fnr,
                tilhører = sjekkpunkt.tilhører,
                fradragstype = FradragstypeData.fraDomain(sjekkpunkt.fradragstype),
                ytelse = sjekkpunkt.ytelse,
                lokaltBeløp = sjekkpunkt.lokaltBeløp,
            )
        }
    }
}

internal data class FradragstypeData(
    val kategori: Fradragstype.Kategori,
    val beskrivelse: String? = null,
) {
    fun tilDomain(): Fradragstype {
        return Fradragstype.from(kategori, beskrivelse)
    }

    companion object {
        fun fraDomain(
            fradragstype: Fradragstype,
        ): FradragstypeData {
            return FradragstypeData(
                kategori = fradragstype.kategori,
                beskrivelse = (fradragstype as? Fradragstype.Annet)?.beskrivelse,
            )
        }
    }
}

internal sealed interface Avviksvurdering {
    data object IngenDiff : Avviksvurdering
    data class Diff(val avvik: List<Fradragsfunn>) : Avviksvurdering
}

internal sealed interface Fradragsfunn {
    data class Oppgaveavvik(
        val kode: OppgaveConfig.Fradragssjekk.AvvikKode,
        val oppgavetekst: String,
        val fradragstype: FradragstypeData? = null,
        val tilhører: FradragTilhører? = null,
    ) : Fradragsfunn

    data class Observasjon(
        val kode: Observasjonskode,
        val loggtekst: String,
    ) : Fradragsfunn
}

internal enum class Observasjonskode {
    INSIGNIFIKANT_BELOEPSDIFFERANSE,
}

internal data class MislykketOppgaveopprettelse(
    val sakId: UUID,
    val avvikskoder: List<OppgaveConfig.Fradragssjekk.AvvikKode>,
)

internal sealed interface OppgaveopprettelseResultat {
    data class Opprettet(val oppgaveId: OppgaveId, val sakId: UUID) : OppgaveopprettelseResultat
    data class Feilet(val feil: MislykketOppgaveopprettelse) : OppgaveopprettelseResultat
}
