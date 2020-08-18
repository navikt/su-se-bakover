package no.nav.su.se.bakover.domain.beregning

import arrow.core.Either
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import java.util.UUID
import kotlinx.coroutines.runBlocking

enum class Fradragstype {
    Uføretrygd,
    Barnetillegg,
    Arbeidsinntekt,
    Pensjon,
    Kapitalinntekt,
    AndreYtelser;

    companion object {
        fun isValid(s: String) =
            runBlocking {
                Either.catch { valueOf(s) }
                    .isRight()
            }
    }
}

class Fradrag(
    private val id: UUID = UUID.randomUUID(),
    private val type: Fradragstype,
    private val beløp: Int,
    private val beskrivelse: String? = null
) : DtoConvertable<FradragDto> {
    fun perMåned(): Int = beløp / 12

    override fun toDto(): FradragDto = FradragDto(id, type, beløp, beskrivelse)
}

data class FradragDto(
    val id: UUID,
    val type: Fradragstype,
    var beløp: Int,
    val beskrivelse: String?
)
