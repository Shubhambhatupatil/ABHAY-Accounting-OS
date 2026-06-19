from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes.accounting import router as accounting_router
from app.api.routes.ai_accountant import router as ai_accountant_router
from app.api.routes.ai_command import router as ai_command_router
from app.api.routes.ai_entry import router as ai_entry_router
from app.api.routes.auth import router as auth_router
from app.api.routes.bank_reconciliation import router as bank_reconciliation_router
from app.api.routes.financial_intelligence import router as financial_intelligence_router
from app.api.routes.automation import router as automation_router
from app.core.config import get_settings
from app.core.database import create_alpha_schema_if_needed

SYSTEM_STATUS = {"status": "ok", "service": "abhay-api", "ai_engine": "ready"}


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    create_alpha_schema_if_needed()
    yield


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(
        title="ABHAY Accounting OS API",
        version="0.1.0",
        docs_url="/docs" if settings.app_env != "production" else None,
        redoc_url=None,
        lifespan=lifespan,
    )
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_origin_regex=settings.cors_origin_regex,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    app.include_router(auth_router, prefix="/auth", tags=["auth"])
    app.include_router(accounting_router, tags=["accounting"])
    app.include_router(ai_accountant_router)
    app.include_router(ai_command_router)
    app.include_router(ai_entry_router)
    app.include_router(automation_router)
    app.include_router(bank_reconciliation_router)
    app.include_router(financial_intelligence_router)

    @app.get("/health", tags=["system"])
    def health() -> dict[str, str]:
        return SYSTEM_STATUS

    @app.get("/", tags=["system"])
    def root() -> dict[str, str]:
        return SYSTEM_STATUS

    @app.get("/routes", tags=["system"])
    def routes() -> list[dict[str, object]]:
        paths = app.openapi().get("paths", {})
        return [
            {
                "path": path,
                "methods": sorted(method.upper() for method in operations.keys()),
            }
            for path, operations in paths.items()
        ]

    return app


app = create_app()
