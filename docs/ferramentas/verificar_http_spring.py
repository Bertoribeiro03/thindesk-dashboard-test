"""Verificação HTTP real do JAR Spring Boot; usa bancos e processos isolados."""

import json
import hashlib
import os
import shutil
import socket
import statistics
import subprocess
import sys
import time
from contextlib import contextmanager
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RUNTIME = ROOT / ".runtime"
EVIDENCE = ROOT / "docs/evidencias"
STAMP = datetime.now().astimezone().strftime("%Y-%m-%d")
JAVA = str(Path(os.environ["JAVA_HOME"]) / "bin/java.exe") if os.name == "nt" and os.environ.get("JAVA_HOME") else "java"
JAR = ROOT / "target/thindesk-1.1.0-SNAPSHOT.jar"
CURL = "curl.exe" if os.name == "nt" else "curl"
TRANSCRIPT = []


def record(value):
    TRANSCRIPT.append(value)


@contextmanager
def server(port, database, initialize=True, seed=False):
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", port))
    env = {**os.environ, "THINDESK_DB_PATH": str(database), "THINDESK_DATA_SOURCE": "local_sqlite",
           "THINDESK_INITIALIZE": str(initialize).lower(), "THINDESK_SEED_EXAMPLE": str(seed).lower(), "THINDESK_SECURITY_ENABLED": "false"}
    command = [JAVA, "-jar", str(JAR), f"--server.port={port}", "--server.servlet.context-path="]
    with (RUNTIME / f"http-spring-{port}.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        record("$ " + subprocess.list2cmdline(command) + f"\nPID próprio: {process.pid}; inicializar={initialize}; seed={seed}")
        try:
            for _ in range(400):
                if process.poll() is not None:
                    raise RuntimeError("JAR encerrou antes de disponibilizar HTTP.")
                try:
                    with socket.create_connection(("127.0.0.1", port), timeout=0.1):
                        break
                except OSError:
                    time.sleep(0.1)
            else:
                raise RuntimeError("JAR não iniciou em 40 segundos.")
            yield
        finally:
            process.terminate()
            process.wait(timeout=15)
            record(f"Processo próprio da porta {port} encerrado.")


def curl(route, expected=200, port=8082, method="GET"):
    command = [CURL, "--noproxy", "*", "--silent", "--show-error", "--max-time", "15", "-X", method,
               "-w", "\n__METRICS__%{http_code}|%{time_total}", f"http://127.0.0.1:{port}{route}"]
    result = subprocess.run(command, check=True, capture_output=True, encoding="utf-8")
    record("$ " + subprocess.list2cmdline(command) + "\n" + result.stdout)
    body, metrics = result.stdout.rsplit("\n__METRICS__", 1)
    status, elapsed = metrics.split("|")
    assert int(status) == expected, result.stdout
    parsed = json.loads(body) if route.startswith("/api/") or route == "/openapi.json" else body
    return parsed, float(elapsed)


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    RUNTIME.mkdir(exist_ok=True)
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    assert JAR.exists(), "Execute mvnw verify antes da verificação HTTP."
    unique = str(time.time_ns())
    database = RUNTIME / f"http-spring-{unique}.db"
    # Usa uma cópia do banco existente, quando presente, sem alterá-lo.
    existing = ROOT / "api/data/thindesk.db"
    if existing.exists():
        shutil.copy2(existing, database)
    report = {"data": datetime.now().astimezone().isoformat(), "backend": "Spring Boot 3.5.16", "jar_sha256": hashlib.sha256(JAR.read_bytes()).hexdigest(), "respostas": {}}
    try:
        with server(8082, database, seed=True):
            for route in ["/api/v1/health", "/api/v1/meta/filters", "/api/v1/dashboard/summary", "/api/v1/dashboard/timeseries?granularity=month",
                          "/api/v1/dashboard/status-distribution", "/api/v1/dashboard/top-types?limit=8", "/api/v1/tickets?page_size=2"]:
                body, elapsed = curl(route)
                report["respostas"][route] = {"http": 200, "tempo_s": elapsed, "corpo": body}
            for route, code in [
                ("/api/v1/dashboard/summary?start=2026-13-40", "VALIDATION_ERROR"),
                ("/api/v1/dashboard/summary?start=2026-02-01&end=2026-01-01", "INVALID_PERIOD"),
                ("/api/v1/dashboard/summary?start=2020-01-01&end=2026-01-01", "PERIOD_TOO_LONG"),
                ("/api/v1/dashboard/summary?status=xpto", "INVALID_STATUS"),
                ("/api/v1/dashboard/summary?type_id=abc", "INVALID_TYPE_ID"),
            ]:
                body, elapsed = curl(route, 400)
                assert body["error"]["code"] == code
                report["respostas"][route] = {"http": 400, "tempo_s": elapsed, "corpo": body}
            body, _ = curl("/api/v1/nao-existe", 404)
            assert body["error"]["code"] == "NOT_FOUND"
            report["respostas"]["/api/v1/nao-existe"] = {"http": 404, "corpo": body}
            body, _ = curl("/api/clientes", 403, method="POST")
            assert body["error"]["code"] == "HTTP_ERROR"
            report["respostas"]["POST /api/clientes sem CSRF"] = {"http": 403, "corpo": body}
            for route, text in [("/", "Thindesk Dashboard Administrativo"), ("/clientes", "Clientes"), ("/ajustes-horarios", "Horários de atendimento"), ("/chamados", "Chamados técnicos"), ("/docs", "API do Thindesk")]:
                body, _ = curl(route)
                assert text in body and "th:replace=" not in body
            spec, _ = curl("/openapi.json")
            assert "400" in spec["paths"]["/api/v1/dashboard/summary"]["get"]["responses"]
            assert "422" not in spec["paths"]["/api/v1/dashboard/summary"]["get"]["responses"]
            summary = report["respostas"]["/api/v1/dashboard/summary"]["corpo"]["data"]
            distribution = report["respostas"]["/api/v1/dashboard/status-distribution"]["corpo"]["data"]
            consistency = {"total": summary["total"], "soma_cards_status": sum(summary[key] for key in ("abertos", "em_andamento", "concluidos", "cancelados")),
                           "soma_rosca": sum(row["count"] for row in distribution), "total_lista": report["respostas"]["/api/v1/tickets?page_size=2"]["corpo"]["total"]}
            assert len(set(consistency.values())) == 1
            report["consistencia"] = consistency

        with server(8083, RUNTIME / f"ausente-spring-{unique}.db", initialize=False):
            body, elapsed = curl("/api/v1/health", 503, port=8083)
            assert body["error"] == {"code": "DATABASE_UNAVAILABLE", "message": "Banco de dados indisponível.", "details": None}
            report["respostas"]["503 banco ausente"] = {"http": 503, "tempo_s": elapsed, "corpo": body}

        performance_db = RUNTIME / f"performance-spring-{unique}.db"
        seed_env = {**os.environ, "PYTHONUTF8": "1", "THINDESK_DB_PATH": str(performance_db), "THINDESK_DATA_SOURCE": "local_sqlite"}
        seed_command = [sys.executable, "-m", "app.seed", "--n", "10000"]
        seed = subprocess.run(seed_command, cwd=ROOT / "api", env=seed_env, check=True, capture_output=True, encoding="utf-8")
        record("$ " + subprocess.list2cmdline(seed_command) + "\n" + seed.stdout)
        performance = {"registros": 10000, "amostras_por_endpoint": 5, "metodo": "curl time_total; loopback, 1 aquecimento e 5 amostras por rota, JAR Spring Boot; banco separado", "endpoints": {}}
        with server(8082, performance_db):
            for route in ["/dashboard/summary", "/dashboard/timeseries", "/dashboard/status-distribution", "/dashboard/top-types?limit=8", "/tickets?page_size=100"]:
                body, _ = curl("/api/v1" + route)
                if route == "/dashboard/summary":
                    assert body["data"]["total"] == 10000
                samples = [curl("/api/v1" + route)[1] * 1000 for _ in range(5)]
                performance["endpoints"][route] = {"tempos_ms": samples, "mediana_ms": statistics.median(samples), "max_ms": max(samples), "meta_500ms": max(samples) < 500}
        report["desempenho"] = performance
        report["resultado"] = "APROVADO"
        report["servidores_encerrados"] = True
        (EVIDENCE / f"http_spring_{STAMP}.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps({"resultado": report["resultado"], "consistencia": report["consistencia"], "desempenho": performance}, ensure_ascii=False, indent=2))
    finally:
        (EVIDENCE / f"curl_spring_{STAMP}.txt").write_text("\n\n".join(TRANSCRIPT) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
