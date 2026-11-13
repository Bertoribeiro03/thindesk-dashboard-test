"""Validação comum e construção de predicados parametrizados."""

import re
from dataclasses import dataclass
from datetime import date, timedelta

from fastapi import Query, Request
from fastapi.exceptions import RequestValidationError

from .errors import ApiError

STATUSES = {
    "aberto": "Aberto",
    "em_andamento": "Em andamento",
    "concluido": "Concluído",
    "cancelado": "Cancelado",
}


@dataclass(frozen=True)
class Filters:
    start: date | None = None
    end: date | None = None
    statuses: tuple[str, ...] = ()
    type_ids: tuple[int, ...] = ()

    def where(self, date_col: str = "t.opened_at") -> tuple[str, list]:
        # Nomes de coluna vêm exclusivamente desta lista interna, nunca da URL.
        if date_col not in ("t.opened_at", "t.closed_at"):
            raise ValueError("Coluna de data não permitida.")
        clauses, params = [], []
        if self.start:
            clauses.append(f"{date_col} >= ?")
            params.append(self.start.isoformat() + " 00:00:00")
        if self.end:
            if self.end == date.max:
                clauses.append(f"{date_col} <= ?")
                params.append("9999-12-31 23:59:59")
            else:
                clauses.append(f"{date_col} < ?")
                params.append((self.end + timedelta(days=1)).isoformat() + " 00:00:00")
        if self.statuses:
            clauses.append("t.status IN (" + ",".join("?" for _ in self.statuses) + ")")
            params.extend(self.statuses)
        if self.type_ids:
            clauses.append("t.type_id IN (" + ",".join("?" for _ in self.type_ids) + ")")
            # Strings numéricas preservam a comparação com INTEGER e evitam
            # OverflowError para um ID positivo maior que o limite do SQLite.
            params.extend(str(item) for item in self.type_ids)
        return (" WHERE " + " AND ".join(clauses) if clauses else ""), params

    def as_dict(self) -> dict:
        return {
            "start": self.start.isoformat() if self.start else None,
            "end": self.end.isoformat() if self.end else None,
            "status": list(self.statuses),
            "type_id": list(self.type_ids),
        }


def get_filters(
    request: Request,
    start: date | None = Query(None, description="Data inicial: AAAA-MM-DD"),
    end: date | None = Query(None, description="Data final inclusiva: AAAA-MM-DD"),
    status: str | None = Query(None, description="Status separados por vírgula"),
    type_id: str | None = Query(None, description="IDs positivos separados por vírgula"),
) -> Filters:
    # Pydantic também aceita timestamps numéricos; o contrato aceita só datas ISO.
    malformed = [
        {"loc": ("query", name), "msg": "Use uma data válida no formato AAAA-MM-DD.", "type": "date_parsing"}
        for name in ("start", "end")
        if name in request.query_params
        and not re.fullmatch(r"\d{4}-\d{2}-\d{2}", request.query_params[name])
    ]
    if malformed:
        raise RequestValidationError(malformed)
    if start and end:
        if start > end:
            raise ApiError(400, "INVALID_PERIOD", "A data inicial deve ser anterior à final.")
        if (end - start).days > 731:
            raise ApiError(400, "PERIOD_TOO_LONG", "O período não pode exceder 731 dias.")
    statuses = tuple(sorted(set(item.strip() for item in status.split(",")))) if status is not None else ()
    invalid = sorted(set(statuses) - STATUSES.keys())
    if invalid:
        raise ApiError(400, "INVALID_STATUS", "Status inválido.", {"invalid": invalid, "allowed": list(STATUSES)})
    type_ids = ()
    if type_id is not None:
        parts = [item.strip() for item in type_id.split(",")]
        try:
            if any(not re.fullmatch(r"[0-9]+", part) for part in parts):
                raise ValueError
            type_ids = tuple(sorted(set(int(part) for part in parts)))
            if any(item <= 0 for item in type_ids):
                raise ValueError
        except ValueError:
            raise ApiError(400, "INVALID_TYPE_ID", "Informe IDs de tipo inteiros positivos válidos.") from None
    return Filters(start, end, statuses, type_ids)
