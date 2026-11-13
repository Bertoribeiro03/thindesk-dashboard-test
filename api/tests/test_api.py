"""Contrato público com base isolada; não depende do seed nem de serviços externos."""

import sqlite3
from datetime import date, datetime

import pytest
from fastapi.testclient import TestClient
from starlette.exceptions import HTTPException

from app import config, repository
from app.db import connect, init_schema
from app.filters import Filters
from app.main import app
from app.seed import seed

PREFIX = "/api/v1"
ROWS = [
    (1, "A", 1, "concluido", "alta", "Ana", "Troca", "2026-01-05 08:00:00", "2026-01-05 12:00:00"),
    (2, "B", 1, "concluido", "media", "Ana", "Driver", "2026-01-06 08:00:00", "2026-01-07 08:00:00"),
    (3, "C", 2, "aberto", "baixa", None, None, "2026-01-06 09:00:00", None),
    (4, "D", 2, "em_andamento", "alta", "Bruno", "Analisando", "2026-01-10 10:00:00", None),
    (5, "E", 1, "cancelado", "baixa", None, None, "2026-02-01 10:00:00", "2026-02-01 11:00:00"),
]


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("THINDESK_DB_PATH", str(tmp_path / "exemplo.db"))
    monkeypatch.setenv("THINDESK_DATA_SOURCE", "local_sqlite")
    with connect(readonly=False) as db:
        init_schema(db)
        db.executemany("INSERT INTO ticket_types VALUES (?, ?)", [(1, "Hardware"), (2, "Rede"), (3, "Software")])
        db.executemany("INSERT INTO tickets VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", ROWS)
    with TestClient(app, raise_server_exceptions=False) as session:
        yield session


def get(client, path, **params):
    response = client.get(PREFIX + path, params=params)
    assert response.status_code == 200, response.text
    return response.json()


def error(client, path, code, expected_status=400, **params):
    response = client.get(PREFIX + path, params=params)
    assert response.status_code == expected_status, response.text
    body = response.json()
    assert set(body) == {"error"}
    assert set(body["error"]) == {"code", "message", "details"}
    assert body["error"]["code"] == code
    return body["error"]


def test_T01_health(client):
    assert get(client, "/health") == {"status": "ok", "database": "ok", "data_source": "local_sqlite"}


def test_T02_summary(client):
    assert get(client, "/dashboard/summary")["data"] == {
        "total": 5, "abertos": 1, "em_andamento": 1, "concluidos": 2, "cancelados": 1,
        "backlog": 2, "taxa_resolucao_pct": 40.0, "tempo_medio_resolucao_h": 14.0,
    }


def test_T03_status_csv(client):
    assert get(client, "/dashboard/summary", status="aberto,em_andamento")["data"]["total"] == 2


def test_T04_tipo_periodo(client):
    assert get(client, "/dashboard/summary", type_id=1, start="2026-01-01", end="2026-01-31")["data"]["total"] == 2


def test_T05_vazio(client):
    data = get(client, "/dashboard/summary", start="2020-01-01", end="2020-01-31")["data"]
    assert data["total"] == 0 and data["tempo_medio_resolucao_h"] is None
    assert data["taxa_resolucao_pct"] == 0.0


@pytest.mark.parametrize("value", ["2026-13-40", "ontem", "20260105", "1700000000", "2026-01-05T00:00:00", ""])
def test_T06_data_invalida(client, value):
    detail = error(client, "/dashboard/summary", "VALIDATION_ERROR", start=value)["details"]
    assert detail == [{"param": "start", "msg": "Use uma data válida no formato AAAA-MM-DD."}]


def test_T07_periodo_invertido(client):
    error(client, "/dashboard/summary", "INVALID_PERIOD", start="2026-02-01", end="2026-01-01")


def test_T08_status_invalido(client):
    details = error(client, "/dashboard/summary", "INVALID_STATUS", status="xpto")["details"]
    assert details == {"invalid": ["xpto"], "allowed": ["aberto", "em_andamento", "concluido", "cancelado"]}


@pytest.mark.parametrize("value", ["abc", "0", "-1", "1.5", "1,,2", "", "1 OR 1=1"])
def test_T09_tipo_invalido(client, value):
    error(client, "/dashboard/summary", "INVALID_TYPE_ID", type_id=value)


def test_T10_serie_dia(client):
    assert get(client, "/dashboard/timeseries", start="2026-01-05", end="2026-01-07")["data"] == [
        {"period": "2026-01-05", "abertos": 1, "atendidos": 1},
        {"period": "2026-01-06", "abertos": 2, "atendidos": 0},
        {"period": "2026-01-07", "abertos": 0, "atendidos": 1},
    ]
    error(client, "/dashboard/timeseries", "VALIDATION_ERROR", granularity="year")


def test_T11_distribuicao(client):
    rows = get(client, "/dashboard/status-distribution")["data"]
    assert [row["status"] for row in rows] == ["aberto", "em_andamento", "concluido", "cancelado"]
    assert sum(row["count"] for row in rows) == 5
    assert sum(row["pct"] for row in rows) == pytest.approx(100)


def test_T12_ranking(client):
    rows = get(client, "/dashboard/top-types")["data"]
    assert [row["type"] for row in rows] == ["Hardware", "Rede"]
    assert rows[0] == {"rank": 1, "type_id": 1, "type": "Hardware", "count": 3, "pct": 60.0}
    error(client, "/dashboard/top-types", "VALIDATION_ERROR", limit=0)


def test_T13_paginacao(client):
    data = get(client, "/tickets", page_size=2)
    assert len(data["items"]) == 2 and data["pages"] == 3
    assert data["items"][0]["id"] == 5
    assert set(data["items"][0]) == {"id", "title", "type", "status", "priority", "technician", "technical_notes", "opened_at", "closed_at"}


@pytest.mark.parametrize("path", ["/health", "/meta/filters", "/dashboard/summary", "/dashboard/timeseries", "/dashboard/status-distribution", "/dashboard/top-types", "/tickets"])
def test_T14_banco_inexistente(client, monkeypatch, tmp_path, path):
    missing = tmp_path / "segredo-interno" / "nao-existe.db"
    monkeypatch.setenv("THINDESK_DB_PATH", str(missing))
    body = error(client, path, "DATABASE_UNAVAILABLE", expected_status=503)
    assert body == {"code": "DATABASE_UNAVAILABLE", "message": "Banco de dados indisponível.", "details": None}
    assert str(missing) not in str(body) and "unable to open" not in str(body)
    assert not missing.exists() and not missing.parent.exists()


def test_T15_rota_inexistente(client):
    error(client, "/nao-existe", "NOT_FOUND", expected_status=404)


def test_T16_periodo_longo(client):
    error(client, "/dashboard/summary", "PERIOD_TOO_LONG", start="2020-01-01", end="2023-01-01")
    get(client, "/dashboard/summary", start="2024-01-01", end="2026-01-01")  # exatamente 731 dias


def test_T17_serie_mes(client):
    assert get(client, "/dashboard/timeseries", granularity="month")["data"] == [
        {"period": "2026-01", "abertos": 4, "atendidos": 2}, {"period": "2026-02", "abertos": 1, "atendidos": 0}]
    rows = get(client, "/dashboard/timeseries", granularity="month", start="2025-12-01", end="2026-03-31")["data"]
    assert rows[0] == {"period": "2025-12", "abertos": 0, "atendidos": 0}
    assert rows[-1] == {"period": "2026-03", "abertos": 0, "atendidos": 0}


def test_T18_serie_semana(client):
    assert get(client, "/dashboard/timeseries", granularity="week")["data"] == [
        {"period": "2026-W01", "abertos": 4, "atendidos": 2}, {"period": "2026-W04", "abertos": 1, "atendidos": 0}]
    rows = get(client, "/dashboard/timeseries", granularity="week", start="2025-12-29", end="2026-01-07")["data"]
    assert rows == [{"period": "2025-W52", "abertos": 0, "atendidos": 0},
                    {"period": "2026-W00", "abertos": 0, "atendidos": 0},
                    {"period": "2026-W01", "abertos": 3, "atendidos": 2}]


@pytest.mark.parametrize("page", [4, 999999999999999999999])
def test_T19_pagina_fora(client, page):
    assert get(client, "/tickets", page=page, page_size=2)["items"] == []


@pytest.mark.parametrize("type_id", [999, 999999999999999999999])
def test_T20_tipo_inexistente(client, type_id):
    assert get(client, "/dashboard/summary", type_id=type_id)["data"]["total"] == 0


def test_T21_dashboard(client):
    response = client.get("/")
    assert response.status_code == 200 and "text/html" in response.headers["content-type"]
    assert "Thindesk Dashboard Administrativo" in response.text
    assert "chart.js@4.4.1" in response.text
    assert client.get("/app.js").status_code == client.get("/styles.css").status_code == 200


def test_T22_meta(client):
    data = get(client, "/meta/filters")["data"]
    assert data["date_range"] == {"min": "2026-01-05", "max": "2026-02-01"}
    assert [row["name"] for row in data["types"]] == ["Hardware", "Rede", "Software"]
    assert len(data["statuses"]) == 4


def test_T23_normalizacao(client):
    filters = get(client, "/dashboard/summary", status="em_andamento,aberto,aberto", type_id="2,1,2")["filters"]
    assert filters == {"start": None, "end": None, "status": ["aberto", "em_andamento"], "type_id": [1, 2]}


def test_T24_ordem_validacoes(client):
    error(client, "/dashboard/summary", "VALIDATION_ERROR", start="data", status="x", type_id="abc")
    error(client, "/dashboard/summary", "INVALID_PERIOD", start="2026-02-01", end="2026-01-01", status="x", type_id="abc")
    error(client, "/dashboard/summary", "PERIOD_TOO_LONG", start="2020-01-01", end="2026-01-01", status="x", type_id="abc")
    error(client, "/dashboard/summary", "INVALID_STATUS", status="x", type_id="abc")


def test_T25_encerramento_e_fim_inclusivo(client):
    with connect(readonly=False) as db:
        db.execute("INSERT INTO tickets VALUES (6, 'F', 1, 'concluido', 'media', 'Ana', 'Nota', '2025-12-30 08:00:00', '2026-01-06 23:59:59')")
    params = {"start": "2026-01-06", "end": "2026-01-06"}
    assert get(client, "/dashboard/summary", **params)["data"]["total"] == 2
    assert get(client, "/dashboard/timeseries", **params)["data"] == [{"period": "2026-01-06", "abertos": 2, "atendidos": 1}]


def test_T26_consistencia(client):
    for params in ({}, {"status": "aberto,em_andamento"}, {"type_id": "999"}):
        summary = get(client, "/dashboard/summary", **params)["data"]
        total = summary["total"]
        assert sum(summary[key] for key in ("abertos", "em_andamento", "concluidos", "cancelados")) == total
        assert sum(row["count"] for row in get(client, "/dashboard/status-distribution", **params)["data"]) == total
        assert get(client, "/tickets", **params)["total"] == total


def test_T27_somente_leitura_e_indices(client):
    with connect() as db:
        assert isinstance(db.execute("SELECT * FROM tickets LIMIT 1").fetchone(), sqlite3.Row)
        assert db.execute("PRAGMA foreign_keys").fetchone()[0] == 1
        assert {row["name"] for row in db.execute("PRAGMA index_list(tickets)")} >= {"ix_tickets_opened", "ix_tickets_closed", "ix_tickets_status"}
        with pytest.raises(sqlite3.Error):
            db.execute("DELETE FROM tickets")
    with pytest.raises(sqlite3.ProgrammingError):
        db.execute("SELECT 1")


def test_T28_erro_interno(client, monkeypatch):
    def fail():
        raise RuntimeError("SEGREDO_INTERNO")
    monkeypatch.setattr(repository, "health", fail)
    body = error(client, "/health", "INTERNAL_ERROR", expected_status=500)
    assert "SEGREDO" not in str(body) and body["details"] is None


def test_T29_http_e_arquivo_ausente(client, monkeypatch):
    error(client, "/../arquivo-inexistente.css", "NOT_FOUND", expected_status=404)
    def fail():
        raise HTTPException(403, "Detalhe privado")
    monkeypatch.setattr(repository, "health", fail)
    error(client, "/health", "HTTP_ERROR", expected_status=403)


@pytest.mark.parametrize("path,params", [("/tickets", {"page": 0}), ("/tickets", {"page_size": 101}), ("/tickets", {"page_size": 0}), ("/tickets", {"page": "abc"}), ("/dashboard/top-types", {"limit": 51})])
def test_T30_limites(client, path, params):
    error(client, path, "VALIDATION_ERROR", **params)


def test_T31_seed_deterministico(client):
    now = datetime(2026, 11, 13, 12)
    seed(n=50, days=1, now=now)
    with connect() as db:
        first = [tuple(row) for row in db.execute("SELECT * FROM tickets ORDER BY id")]
    seed(n=50, days=1, now=now)
    with connect() as db:
        assert first == [tuple(row) for row in db.execute("SELECT * FROM tickets ORDER BY id")]
    assert len(first) == 50
    for row in first:
        opened = datetime.fromisoformat(row[7])
        if (now - opened).total_seconds() < 7200:
            assert row[3] == "aberto"
        if row[3] == "aberto":
            assert row[5] is None and row[6] is None
        assert (row[8] is not None) == (row[3] in ("concluido", "cancelado"))
        if row[8]:
            assert opened <= datetime.fromisoformat(row[8]) <= now


def test_T32_base_vazia(client):
    with connect(readonly=False) as db:
        db.execute("DELETE FROM tickets")
    assert get(client, "/health")["database"] == "ok"
    assert get(client, "/meta/filters")["data"]["date_range"] == {"min": None, "max": None}
    assert get(client, "/tickets")["pages"] == 0
    assert len(get(client, "/dashboard/status-distribution")["data"]) == 4
    assert all(row["pct"] == 0 for row in get(client, "/dashboard/status-distribution")["data"])


def test_T33_config_dinamica(client, monkeypatch):
    monkeypatch.setenv("THINDESK_DATA_SOURCE", "fonte_teste")
    assert config.data_source() == get(client, "/health")["data_source"] == "fonte_teste"
    monkeypatch.setenv("THINDESK_DB_PATH", "outro.db")
    assert config.db_path() == config.API_ROOT / "outro.db"


def test_T34_sql_parametrizado():
    where, params = Filters(date(2026, 1, 1), date(2026, 1, 31), ("aberto",), (1,)).where()
    assert "2026" not in where and "aberto" not in where and params == ["2026-01-01 00:00:00", "2026-02-01 00:00:00", "aberto", "1"]
    with pytest.raises(ValueError):
        Filters().where("t.opened_at; DROP TABLE tickets")


def test_T35_openapi(client):
    assert client.get("/docs").status_code == 200
    spec = client.get("/openapi.json").json()
    assert spec["info"]["title"] == "Thindesk Dashboard API" and spec["info"]["version"] == "1.0.0"
