"""Configuração consultada a cada chamada; não carrega arquivos .env."""

import os
from pathlib import Path

API_ROOT = Path(__file__).resolve().parents[1]
PROJECT_ROOT = API_ROOT.parent


def db_path() -> Path:
    path = Path(os.environ.get("THINDESK_DB_PATH", "data/thindesk.db")).expanduser()
    return (path if path.is_absolute() else API_ROOT / path).resolve()


def data_source() -> str:
    return os.environ.get("THINDESK_DATA_SOURCE", "local_sqlite")
