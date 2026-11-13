"""Execute com o Python da .venv; preserva stdout/stderr reais e JUnit."""

import argparse
import json
import os
import platform
import sqlite3
import subprocess
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
API = ROOT / "api"
EVIDENCE = ROOT / "docs" / "evidencias"


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="Executar pytest e salvar evidências reais")
    parser.add_argument("--release", action="store_true")
    args = parser.parse_args()
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().astimezone().strftime("%Y-%m-%d") + ("_release" if args.release else "")
    command = [sys.executable, "-m", "pytest", "-v", f"--junitxml={EVIDENCE / ('pytest_' + stamp + '.xml')}"]
    result = subprocess.run(command, cwd=API, env={**os.environ, "PYTHONUTF8": "1"}, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, encoding="utf-8")
    header = f"Data: {datetime.now().astimezone().isoformat()}\nDiretório: {API}\nComando: {subprocess.list2cmdline(command)}\n\n"
    (EVIDENCE / f"pytest_{stamp}.txt").write_text(header + result.stdout + f"\nCódigo de saída: {result.returncode}\n", encoding="utf-8")
    print(result.stdout)
    if not args.release:
        freeze = subprocess.run([sys.executable, "-m", "pip", "freeze"], capture_output=True, text=True, check=True)
        environment = {"data": datetime.now().astimezone().isoformat(), "python": sys.version,
                       "sistema": platform.platform(), "sqlite": sqlite3.sqlite_version,
                       "dependencias_instaladas": freeze.stdout.splitlines()}
        (EVIDENCE / f"ambiente_{stamp}.json").write_text(json.dumps(environment, ensure_ascii=False, indent=2), encoding="utf-8")
    return result.returncode


if __name__ == "__main__":
    sys.exit(main())
