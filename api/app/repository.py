"""Agregações executadas em SQL; nenhuma consulta recebe SQL da URL."""

from datetime import timedelta
from math import ceil

from .db import connect
from .filters import Filters, STATUSES

PERIOD_FORMATS = {"day": "%Y-%m-%d", "week": "%Y-W%W", "month": "%Y-%m"}


def health() -> None:
    with connect() as db:
        db.execute("SELECT 1 FROM tickets LIMIT 1").fetchone()


def metadata() -> dict:
    with connect() as db:
        types = [dict(row) for row in db.execute("SELECT id, name FROM ticket_types ORDER BY name")]
        dates = db.execute("SELECT date(MIN(opened_at)) AS min, date(MAX(opened_at)) AS max FROM tickets").fetchone()
    return {
        "statuses": [{"value": value, "label": label} for value, label in STATUSES.items()],
        "types": types,
        "date_range": dict(dates),
    }


def summary(filters: Filters) -> dict:
    where, params = filters.where()
    with connect() as db:
        row = db.execute("""
            SELECT COUNT(*) AS total,
              COALESCE(SUM(status='aberto'), 0) AS abertos,
              COALESCE(SUM(status='em_andamento'), 0) AS em_andamento,
              COALESCE(SUM(status='concluido'), 0) AS concluidos,
              COALESCE(SUM(status='cancelado'), 0) AS cancelados,
              ROUND(AVG(CASE WHEN status='concluido'
                THEN (julianday(closed_at)-julianday(opened_at))*24 END), 1) AS tempo_medio_resolucao_h
            FROM tickets t
        """ + where, params).fetchone()
    data = dict(row)
    data["backlog"] = data["abertos"] + data["em_andamento"]
    data["taxa_resolucao_pct"] = round(data["concluidos"] / data["total"] * 100, 1) if data["total"] else 0.0
    return data


def timeseries(filters: Filters, granularity: str) -> list[dict]:
    fmt = PERIOD_FORMATS[granularity]
    series = []
    with connect() as db:
        for column, completed in (("t.opened_at", False), ("t.closed_at", True)):
            where, params = filters.where(column)
            if completed:
                where += (" AND " if where else " WHERE ") + "t.status = ? AND t.closed_at IS NOT NULL"
                params.append("concluido")
            rows = db.execute(
                f"SELECT strftime(?, {column}) AS period, COUNT(*) AS count FROM tickets t"
                + where + " GROUP BY period ORDER BY period", [fmt, *params],
            ).fetchall()
            series.append({row["period"]: row["count"] for row in rows})
    if filters.start and filters.end:
        periods = set()
        for offset in range((filters.end - filters.start).days + 1):
            periods.add((filters.start + timedelta(days=offset)).strftime(fmt))
        keys = sorted(periods)
    else:
        keys = sorted(series[0].keys() | series[1].keys())
    return [{"period": key, "abertos": series[0].get(key, 0), "atendidos": series[1].get(key, 0)} for key in keys]


def status_distribution(filters: Filters) -> list[dict]:
    where, params = filters.where()
    with connect() as db:
        counts = {row["status"]: row["count"] for row in db.execute(
            "SELECT status, COUNT(*) AS count FROM tickets t" + where + " GROUP BY status", params
        )}
    total = sum(counts.values())
    return [{"status": status, "label": label, "count": counts.get(status, 0),
             "pct": round(counts.get(status, 0) / total * 100, 1) if total else 0.0}
            for status, label in STATUSES.items()]


def top_types(filters: Filters, limit: int) -> list[dict]:
    where, params = filters.where()
    with connect() as db:
        total = db.execute("SELECT COUNT(*) FROM tickets t" + where, params).fetchone()[0]
        rows = db.execute(
            "SELECT tt.id AS type_id, tt.name AS type, COUNT(*) AS count FROM tickets t "
            "JOIN ticket_types tt ON tt.id=t.type_id" + where
            + " GROUP BY tt.id, tt.name ORDER BY count DESC, tt.name ASC LIMIT ?", [*params, limit]
        ).fetchall()
    return [{"rank": rank, **dict(row), "pct": round(row["count"] / total * 100, 1) if total else 0.0}
            for rank, row in enumerate(rows, 1)]


def tickets(filters: Filters, page: int, page_size: int) -> dict:
    where, params = filters.where()
    with connect() as db:
        # A contagem e a página compartilham o mesmo snapshot de leitura.
        db.execute("BEGIN")
        total = db.execute("SELECT COUNT(*) FROM tickets t" + where, params).fetchone()[0]
        offset = (page - 1) * page_size
        rows = [] if offset > 9223372036854775807 else db.execute(
            "SELECT t.id, t.title, tt.name AS type, t.status, t.priority, t.technician, "
            "t.technical_notes, t.opened_at, t.closed_at FROM tickets t "
            "JOIN ticket_types tt ON tt.id=t.type_id" + where
            + " ORDER BY t.opened_at DESC, t.id DESC LIMIT ? OFFSET ?", [*params, page_size, offset]
        ).fetchall()
    return {"items": [dict(row) for row in rows], "page": page, "page_size": page_size,
            "total": total, "pages": ceil(total / page_size)}
