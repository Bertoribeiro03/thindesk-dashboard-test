"""Geração explícita de EXEMPLOS. Apaga apenas as tabelas do banco configurado."""

import argparse
import random
from datetime import datetime, timedelta

from .config import data_source, db_path
from .db import connect, init_schema

TYPES = ["Hardware", "Software", "Rede", "Acesso/Senha", "Impressora", "E-mail", "Telefonia", "ERP"]
TYPE_WEIGHTS = [18, 22, 14, 20, 8, 9, 4, 5]
TECHNICIANS = ["Ana Souza", "Bruno Lima", "Carla Dias", "Diego Rocha"]
NOTES = ["Equipamento substituído.", "Reinstalação do driver.", "Permissão ajustada no AD.",
         "Aguardando peça do fornecedor.", "Cabo de rede reparado.", "Atualização aplicada."]


def seed(n: int = 500, days: int = 180, now: datetime | None = None) -> int:
    if data_source() != "local_sqlite":
        raise ValueError("O seed exige THINDESK_DATA_SOURCE=local_sqlite.")
    if n < 0 or days < 1:
        raise ValueError("Informe n não negativo e days maior ou igual a 1.")
    rng = random.Random(42)
    # O mesmo instante-base gera exatamente o mesmo conjunto de registros.
    now = (now or datetime.now()).replace(microsecond=0)
    rows = []
    for i in range(1, n + 1):
        type_id = rng.choices(range(1, 9), weights=TYPE_WEIGHTS)[0]
        age_h = rng.uniform(0, days * 24)
        opened = now - timedelta(hours=age_h)
        status = rng.choices(["concluido", "em_andamento", "aberto", "cancelado"], weights=[70, 12, 12, 6])[0]
        if age_h < 2:
            status = "aberto"
        closed = min(opened + timedelta(hours=rng.uniform(0.5, min(age_h, 120))), now) if status in ("concluido", "cancelado") else None
        technician = rng.choice(TECHNICIANS) if status != "aberto" else None
        note = rng.choice(NOTES) if status != "aberto" else None
        rows.append((i, f"Chamado {i} - {TYPES[type_id - 1]}", type_id, status,
                     rng.choice(["baixa", "media", "alta", "critica"]), technician, note,
                     opened.strftime("%Y-%m-%d %H:%M:%S"), closed.strftime("%Y-%m-%d %H:%M:%S") if closed else None))
    with connect(readonly=False) as db:
        db.executescript("DROP TABLE IF EXISTS tickets; DROP TABLE IF EXISTS ticket_types;")
        init_schema(db)
        db.executemany("INSERT INTO ticket_types(id, name) VALUES (?, ?)", enumerate(TYPES, 1))
        db.executemany("INSERT INTO tickets VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", rows)
    return n


def main():
    parser = argparse.ArgumentParser(description="Recriar banco local com dados de EXEMPLO (substitui os dados existentes).")
    parser.add_argument("--n", type=int, default=500, help="Quantidade de chamados (padrão: 500)")
    parser.add_argument("--days", type=int, default=180, help="Janela em dias (padrão: 180)")
    args = parser.parse_args()
    total = seed(args.n, args.days)
    print(f"Dados de EXEMPLO: {total} chamados em {db_path()} (fonte: {data_source()}).")


if __name__ == "__main__":
    main()
