UPDATE
    behandling
SET
    beregning = beregning || '{ "fradragStrategyName": "Enslig" }'
WHERE
      beregning IS NOT NULL
