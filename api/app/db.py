"""Conexões curtas; somente seed e preparação de testes usam escrita."""

import sqlite3
from contextlib import contextmanager

from .config import db_path

SCHEMA = """
CREATE TABLE IF NOT EXISTS ticket_types(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE);
CREATE TABLE IF NOT EXISTS tickets(
  id INTEGER PRIMARY KEY,
  title TEXT NOT NULL,
  type_id INTEGER NOT NULL REFERENCES ticket_types(id),
  status TEXT NOT NULL CHECK(status IN ('aberto','em_andamento','concluido','cancelado')),
  priority TEXT NOT NULL CHECK(priority IN ('baixa','media','alta','critica')),
  technician TEXT,
  technical_notes TEXT,
  opened_at TEXT NOT NULL,
  closed_at TEXT,
  CHECK (closed_at IS NULL OR closed_at >= opened_at));
CREATE INDEX IF NOT EXISTS ix_tickets_opened ON tickets(opened_at);
CREATE INDEX IF NOT EXISTS ix_tickets_closed ON tickets(closed_at);
CREATE INDEX IF NOT EXISTS ix_tickets_status ON tickets(status);
"""


@contextmanager
def connect(readonly: bool = True):
    path = db_path()
    connection = None
    try:
        if readonly:
            connection = sqlite3.connect(path.as_uri() + "?mode=ro", uri=True)
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            connection = sqlite3.connect(path)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys=ON")
        yield connection
        if not readonly:
            connection.commit()
    except Exception:
        if connection is not None and not readonly:
            connection.rollback()
        raise
    finally:
        if connection is not None:
            connection.close()


def init_schema(connection: sqlite3.Connection) -> None:
    connection.executescript(SCHEMA)
