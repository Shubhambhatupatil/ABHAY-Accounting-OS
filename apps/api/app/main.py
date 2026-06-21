from collections.abc import AsyncIterator
from collections import defaultdict, deque
from contextlib import asynccontextmanager
import time

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.routes.accounting import router as accounting_router
from app.api.routes.ai_accountant import router as ai_accountant_router
from app.api.routes.ai_command import router as ai_command_router
from app.api.routes.ai_entry import router as ai_entry_router
from app.api.routes.auth import router as auth_router
from app.api.routes.bank_reconciliation import router as bank_reconciliation_router
from app.api.routes.document_intelligence import router as document_intelligence_router
from app.api.routes.financial_intelligence import router as financial_intelligence_router
from app.api.routes.automation import router as automation_router
from app.core.config import get_settings
from app.core.database import create_alpha_schema_if_needed

SYSTEM_STATUS = {"status": "ok", "service": "abhay-api", "ai_engine": "ready"}
RATE_LIMIT_BUCKETS: dict[str, deque[float]] = defaultdict(deque)
RATE_LIMIT_WINDOW_SECONDS = 60
RATE_LIMIT_MAX_REQUESTS = 120
RATE_LIMITED_PREFIXES = (
    "/auth",
    "/ai/command",
    "/ai-entry",
    "/companies",
)


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

    @app.middleware("http")
    async def add_security_headers(request: Request, call_next):
        response = await call_next(request)
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
        response.headers["Permissions-Policy"] = "camera=(), microphone=(), geolocation=()"
        return response

    @app.middleware("http")
    async def rate_limit_sensitive_routes(request: Request, call_next):
        if request.method == "OPTIONS" or not request.url.path.startswith(RATE_LIMITED_PREFIXES):
            return await call_next(request)

        client_ip = get_client_ip(request)
        bucket_key = f"{client_ip}:{request.url.path}"
        now = time.monotonic()
        bucket = RATE_LIMIT_BUCKETS[bucket_key]
        while bucket and now - bucket[0] > RATE_LIMIT_WINDOW_SECONDS:
            bucket.popleft()

        if len(bucket) >= RATE_LIMIT_MAX_REQUESTS:
            return JSONResponse(
                status_code=429,
                content={"detail": "Too many requests. Please wait a moment and try again."},
            )

        bucket.append(now)
        return await call_next(request)
    app.include_router(auth_router, prefix="/auth", tags=["auth"])
    app.include_router(accounting_router, tags=["accounting"])
    app.include_router(ai_accountant_router)
    app.include_router(ai_command_router)
    app.include_router(ai_entry_router)
    app.include_router(automation_router)
    app.include_router(bank_reconciliation_router)
    app.include_router(document_intelligence_router)
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


def get_client_ip(request: Request) -> str:
    forwarded_for = request.headers.get("x-forwarded-for")
    if forwarded_for:
        return forwarded_for.split(",")[0].strip()
    return request.client.host if request.client else "unknown"
