from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes.accounting import router as accounting_router
from app.api.routes.ai_accountant import router as ai_accountant_router
from app.api.routes.ai_entry import router as ai_entry_router
from app.api.routes.auth import router as auth_router
from app.api.routes.bank_reconciliation import router as bank_reconciliation_router
from app.api.routes.financial_intelligence import router as financial_intelligence_router
from app.api.routes.automation import router as automation_router
from app.core.config import get_settings


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(
        title="ABHAY Accounting OS API",
        version="0.1.0",
        docs_url="/docs" if settings.app_env != "production" else None,
        redoc_url=None,
    )
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=True,
        allow_methods=["GET", "POST", "PATCH", "DELETE", "OPTIONS"],
        allow_headers=["*"],
    )
    app.include_router(auth_router, prefix="/auth", tags=["auth"])
    app.include_router(accounting_router, tags=["accounting"])
    app.include_router(ai_accountant_router)
    app.include_router(ai_entry_router)
    app.include_router(automation_router)
    app.include_router(bank_reconciliation_router)
    app.include_router(financial_intelligence_router)

    @app.get("/health", tags=["system"])
    def health() -> dict[str, str]:
        return {"status": "ok", "service": "abhay-api"}

    return app


app = create_app()
