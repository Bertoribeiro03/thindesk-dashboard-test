"""Rotas de leitura, contrato de erros e dashboard na mesma origem."""

import logging
import sqlite3
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, FastAPI, Query, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from starlette.exceptions import HTTPException as StarletteHTTPException

from . import repository
from .config import PROJECT_ROOT, data_source
from .errors import ApiError
from .filters import Filters, get_filters

logger = logging.getLogger(__name__)
app = FastAPI(title="Thindesk Dashboard API", version="1.0.0", description="Leitura de dados de EXEMPLO em SQLite local.")
router = APIRouter(prefix="/api/v1")
FilterDependency = Annotated[Filters, Depends(get_filters)]


@app.exception_handler(ApiError)
async def api_error_handler(request: Request, exc: ApiError):
    return JSONResponse(status_code=exc.status, content=exc.body())


@app.exception_handler(RequestValidationError)
async def validation_handler(request: Request, exc: RequestValidationError):
    details = []
    for error in exc.errors():
        param = ".".join(str(part) for part in error["loc"] if part != "query")
        msg = {
            "start": "Use uma data válida no formato AAAA-MM-DD.",
            "end": "Use uma data válida no formato AAAA-MM-DD.",
            "granularity": "Escolha day, week ou month.",
            "limit": "Informe um inteiro entre 1 e 50.",
            "page": "Informe um inteiro maior ou igual a 1.",
            "page_size": "Informe um inteiro entre 1 e 100.",
        }.get(param, "Parâmetro inválido.")
        details.append({"param": param, "msg": msg})
    return JSONResponse(status_code=400, content=ApiError(400, "VALIDATION_ERROR", "Parâmetros inválidos.", details).body())


@app.exception_handler(StarletteHTTPException)
async def http_handler(request: Request, exc: StarletteHTTPException):
    code, message = ("NOT_FOUND", "Recurso não encontrado.") if exc.status_code == 404 else ("HTTP_ERROR", "Não foi possível atender à solicitação HTTP.")
    return JSONResponse(status_code=exc.status_code, content=ApiError(exc.status_code, code, message).body(), headers=exc.headers)


@app.exception_handler(sqlite3.Error)
async def database_handler(request: Request, exc: sqlite3.Error):
    logger.exception("Falha de acesso ao banco de dados.")
    return JSONResponse(status_code=503, content=ApiError(503, "DATABASE_UNAVAILABLE", "Banco de dados indisponível.").body())


@app.exception_handler(Exception)
async def unexpected_handler(request: Request, exc: Exception):
    logger.exception("Erro inesperado ao processar a solicitação.")
    return JSONResponse(status_code=500, content=ApiError(500, "INTERNAL_ERROR", "Erro interno do servidor.").body())


@router.get("/health", summary="Verificar disponibilidade do banco")
def health():
    repository.health()
    return {"status": "ok", "database": "ok", "data_source": data_source()}


@router.get("/meta/filters", summary="Consultar opções de filtros")
def metadata():
    return {"data": repository.metadata()}


@router.get("/dashboard/summary", summary="Consultar indicadores")
def summary(filters: FilterDependency):
    return {"filters": filters.as_dict(), "data": repository.summary(filters)}


@router.get("/dashboard/timeseries", summary="Consultar série de abertos e atendidos")
def timeseries(filters: FilterDependency, granularity: Literal["day", "week", "month"] = "day"):
    return {"filters": filters.as_dict(), "granularity": granularity, "data": repository.timeseries(filters, granularity)}


@router.get("/dashboard/status-distribution", summary="Consultar distribuição por status")
def distribution(filters: FilterDependency):
    return {"filters": filters.as_dict(), "data": repository.status_distribution(filters)}


@router.get("/dashboard/top-types", summary="Consultar ranking de tipos")
def top_types(filters: FilterDependency, limit: int = Query(5, ge=1, le=50)):
    return {"filters": filters.as_dict(), "data": repository.top_types(filters, limit)}


@router.get("/tickets", summary="Listar chamados paginados")
def tickets(filters: FilterDependency, page: int = Query(1, ge=1), page_size: int = Query(10, ge=1, le=100)):
    return {"filters": filters.as_dict(), **repository.tickets(filters, page, page_size)}


app.include_router(router)


@app.api_route("/api/{path:path}", methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"], include_in_schema=False)
def api_not_found(path: str):
    raise StarletteHTTPException(status_code=404)


# O router e o catch-all da API precedem o mount; nenhuma URL da API vira HTML.
app.mount("/", StaticFiles(directory=PROJECT_ROOT / "dashboard", html=True), name="dashboard")
