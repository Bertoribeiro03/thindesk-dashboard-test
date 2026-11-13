"""Seed, curl real, consistência e medições locais. Sem dependências extras."""

import json
import os
import socket
import statistics
import subprocess
import sys
import time
from contextlib import contextmanager
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
API = ROOT / "api"
EVIDENCE = ROOT / "docs" / "evidencias"
RUNTIME = API / ".runtime"
STAMP = datetime.now().astimezone().strftime("%Y-%m-%d")
ENV = {**os.environ, "PYTHONUTF8": "1", "THINDESK_DATA_SOURCE": "local_sqlite", "THINDESK_DB_PATH": "data/thindesk.db"}
CURL = "curl.exe" if os.name == "nt" else "curl"
TRANSCRIPT = []


def record(text):
    TRANSCRIPT.append(text)
    print(text, flush=True)


def run_seed(env, n=None):
    command = [sys.executable, "-m", "app.seed"] + (["--n", str(n)] if n is not None else [])
    result = subprocess.run(command, cwd=API, env=env, capture_output=True, encoding="utf-8", check=True)
    record("$ " + subprocess.list2cmdline(command) + "\n" + result.stdout)


@contextmanager
def server(port, env, name):
    # Não reutiliza nem interrompe processos alheios.
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", port))
    command = [sys.executable, "-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", str(port)]
    log = (RUNTIME / f"{name}.log").open("w", encoding="utf-8")
    process = subprocess.Popen(command, cwd=API, env=env, stdout=log, stderr=subprocess.STDOUT,
                               creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
    record("$ " + subprocess.list2cmdline(command) + f"\nPID próprio: {process.pid}; banco: {env['THINDESK_DB_PATH']}")
    try:
        for _ in range(100):
            if process.poll() is not None:
                raise RuntimeError("Uvicorn encerrou antes de ficar disponível.")
            try:
                with socket.create_connection(("127.0.0.1", port), timeout=0.1):
                    break
            except OSError:
                time.sleep(0.1)
        else:
            raise RuntimeError("Uvicorn não iniciou em 10 segundos.")
        yield
    finally:
        process.terminate()
        process.wait(timeout=10)
        log.close()
        record(f"Servidor próprio na porta {port} encerrado (código {process.returncode}).")


def curl(path, expected=200, port=8000):
    command = [CURL, "--noproxy", "*", "--silent", "--show-error", "--max-time", "10", "-w", "\n__METRICS__%{http_code}|%{time_total}", f"http://127.0.0.1:{port}{path}"]
    result = subprocess.run(command, capture_output=True, encoding="utf-8", check=True)
    record("$ " + subprocess.list2cmdline(command) + "\n" + result.stdout + ("\n" + result.stderr if result.stderr else ""))
    body, metrics = result.stdout.rsplit("\n__METRICS__", 1)
    status, elapsed = metrics.split("|")
    assert int(status) == expected, result.stdout
    parsed = json.loads(body) if path.startswith("/api/") or path == "/openapi.json" else body
    if expected >= 400:
        assert set(parsed["error"]) == {"code", "message", "details"}
    return parsed, float(elapsed)


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    RUNTIME.mkdir(parents=True, exist_ok=True)
    record(f"Execução real em {datetime.now().astimezone().isoformat()}\nFonte: local_sqlite; dados de EXEMPLO\n")
    observations = {"data": datetime.now().astimezone().isoformat(), "fonte": "local_sqlite", "respostas": {}}
    try:
        run_seed(ENV)
        with server(8000, ENV, "http"):
            routes = ["/health", "/meta/filters", "/dashboard/summary", "/dashboard/timeseries?granularity=month",
                      "/dashboard/status-distribution", "/dashboard/top-types?limit=8", "/tickets?page_size=2"]
            for route in routes:
                body, elapsed = curl("/api/v1" + route)
                observations["respostas"][route] = {"http": 200, "tempo_s": elapsed, "corpo": body}
            for route in ["/dashboard/summary?status=aberto,em_andamento", "/dashboard/summary?type_id=3&start=2026-01-01&end=2026-12-31",
                          "/dashboard/summary?start=2020-01-01&end=2020-01-31", "/dashboard/timeseries?granularity=week&type_id=3",
                          "/dashboard/timeseries?granularity=day&start=2026-01-05&end=2026-01-07"]:
                body, elapsed = curl("/api/v1" + route)
                observations["respostas"][route] = {"http": 200, "tempo_s": elapsed, "corpo": body}
            for route, code in [
                ("/dashboard/summary?start=2026-13-40", "VALIDATION_ERROR"),
                ("/dashboard/summary?start=2026-02-01&end=2026-01-01", "INVALID_PERIOD"),
                ("/dashboard/summary?start=2020-01-01&end=2026-01-01", "PERIOD_TOO_LONG"),
                ("/dashboard/summary?status=xpto", "INVALID_STATUS"),
                ("/dashboard/summary?type_id=abc", "INVALID_TYPE_ID"),
                ("/dashboard/summary?type_id=0", "INVALID_TYPE_ID"),
                ("/dashboard/summary?type_id=-1", "INVALID_TYPE_ID"),
                ("/dashboard/timeseries?granularity=year", "VALIDATION_ERROR"),
                ("/dashboard/top-types?limit=0", "VALIDATION_ERROR"),
                ("/tickets?page=0", "VALIDATION_ERROR"),
            ]:
                body, elapsed = curl("/api/v1" + route, expected=400)
                assert body["error"]["code"] == code
                observations["respostas"][route] = {"http": 400, "tempo_s": elapsed, "corpo": body}
            body, elapsed = curl("/api/v1/nao-existe", expected=404)
            observations["respostas"]["/nao-existe"] = {"http": 404, "tempo_s": elapsed, "corpo": body}
            html, _ = curl("/")
            assert "Thindesk Dashboard Administrativo" in html
            curl("/docs")
            spec, _ = curl("/openapi.json")
            assert spec["info"]["version"] == "1.0.0"
            summary = observations["respostas"]["/dashboard/summary"]["corpo"]["data"]
            distribution = observations["respostas"]["/dashboard/status-distribution"]["corpo"]["data"]
            listing = observations["respostas"]["/tickets?page_size=2"]["corpo"]
            consistency = {"total": summary["total"], "soma_cards_status": sum(summary[key] for key in ("abertos", "em_andamento", "concluidos", "cancelados")),
                           "soma_rosca": sum(row["count"] for row in distribution), "total_lista": listing["total"]}
            assert len(set(consistency.values())) == 1
            observations["consistencia"] = consistency
            record("Consistência verificada: " + json.dumps(consistency, ensure_ascii=False))

        unavailable_env = {**ENV, "THINDESK_DB_PATH": str(RUNTIME / "banco-inexistente.db")}
        assert not Path(unavailable_env["THINDESK_DB_PATH"]).exists()
        with server(8001, unavailable_env, "indisponivel"):
            body, elapsed = curl("/api/v1/health", expected=503, port=8001)
            assert body["error"]["code"] == "DATABASE_UNAVAILABLE"
            assert "banco-inexistente" not in json.dumps(body)
            observations["respostas"]["503 /health porta 8001"] = {"http": 503, "tempo_s": elapsed, "corpo": body}

        performance_env = {**ENV, "THINDESK_DB_PATH": str(RUNTIME / "performance.db")}
        run_seed(performance_env, 10000)
        performance = {"data": datetime.now().astimezone().isoformat(), "registros": 10000, "amostras_por_endpoint": 5,
                       "metodo": "curl -w %{time_total}; HTTP em loopback; 1 aquecimento + 5 amostras sequenciais; sem concorrência; inclui transporte e serialização; não mede CDN",
                       "endpoints": {}}
        with server(8002, performance_env, "performance"):
            body, _ = curl("/api/v1/dashboard/summary", port=8002)
            assert body["data"]["total"] == 10000
            for route in ["/dashboard/summary", "/dashboard/timeseries", "/dashboard/status-distribution", "/dashboard/top-types?limit=8", "/tickets?page_size=100"]:
                curl("/api/v1" + route, port=8002)
                samples = [curl("/api/v1" + route, port=8002)[1] * 1000 for _ in range(5)]
                performance["endpoints"][route] = {"tempos_ms": samples, "min_ms": min(samples), "mediana_ms": statistics.median(samples),
                                                       "max_ms": max(samples), "meta_500ms_atingida": max(samples) < 500}
            assert all(row["meta_500ms_atingida"] for row in performance["endpoints"].values())
        observations["resultado"] = "APROVADO"
        observations["servidores_encerrados"] = True
        (EVIDENCE / f"http_{STAMP}.json").write_text(json.dumps(observations, ensure_ascii=False, indent=2), encoding="utf-8")
        (EVIDENCE / f"desempenho_{STAMP}.json").write_text(json.dumps(performance, ensure_ascii=False, indent=2), encoding="utf-8")
        record("Medições reais (ms): " + json.dumps(performance["endpoints"], ensure_ascii=False))
    finally:
        (EVIDENCE / f"curl_{STAMP}.txt").write_text("\n\n".join(TRANSCRIPT) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
