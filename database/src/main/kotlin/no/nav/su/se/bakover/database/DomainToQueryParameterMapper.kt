package no.nav.su.se.bakover.database

import arrow.core.Either
import arrow.core.left
import behandling.domain.beregning.fradrag.Fradragstype
import dokument.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.infrastructure.persistence.QueryParameterMapper
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.database.beregning.serialiser
import no.nav.su.se.bakover.database.revurdering.RevurderingsType
import no.nav.su.se.bakover.database.vedtak.VedtakType
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import java.sql.PreparedStatement

data object DomainToQueryParameterMapper : QueryParameterMapper {

    override fun tryMap(
        idx: Int,
        v: Any?,
    ): Either<QueryParameterMapper.TypeStøttesIkke, (preparedStatement: PreparedStatement) -> Unit> {
        return when (v) {
            is OppgaveId -> Either.Right {
                it.setString(idx, v.toString())
            }

            is BrevbestillingId -> Either.Right {
                it.setString(idx, v.toString())
            }

            is JournalpostId -> Either.Right {
                it.setString(idx, v.toString())
            }

            is VedtakType -> Either.Right {
                it.setString(idx, v.toString())
            }

            is Utbetalingslinje.Endring.LinjeStatus -> Either.Right {
                it.setString(idx, v.toString())
            }

            is RevurderingsType -> Either.Right {
                it.setString(idx, v.toString())
            }

            is Fradragstype.Kategori -> Either.Right {
                it.setString(idx, v.toString())
            }

            is FradragTilhører -> Either.Right {
                it.setString(idx, v.toString())
            }

            is Beregning -> Either.Right {
                it.setString(idx, v.serialiser())
            }

            else -> QueryParameterMapper.TypeStøttesIkke.left()
        }
    }
}
