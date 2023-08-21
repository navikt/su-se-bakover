package økonomi.domain.kvittering

interface UtbetalingKvitteringRepo {
    fun lagre(hendelse: NyKvitteringHendelse)
}
