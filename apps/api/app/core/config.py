import json
from functools import lru_cache
from typing import Annotated, Any

from pydantic import AnyHttpUrl, Field
from pydantic import field_validator
from pydantic_settings import NoDecode
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_env: str = "local"
    supabase_url: AnyHttpUrl
    supabase_jwt_audience: str = "authenticated"
    database_url: str = Field(min_length=1)
    cors_origins: Annotated[list[str], NoDecode] = [
        "http://localhost:3000",
        "https://abhay-accounting-os-new-shubhambhatupatil-5720s-projects.vercel.app",
        "https://abhay-accounting-os-new.vercel.app",
        "https://abhay.anvritai.com",
        "https://anvritai.com",
        "https://www.anvritai.com",
    ]
    cors_origin_regex: str = r"https://.*\.vercel\.app"
    openai_api_key: str | None = None
    alpha_demo_mode: bool = False
    client_demo_mode: bool = True

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    @property
    def supabase_jwks_url(self) -> str:
        return f"{str(self.supabase_url).rstrip('/')}/auth/v1/.well-known/jwks.json"

    @field_validator("cors_origins", mode="before")
    @classmethod
    def parse_cors_origins(cls, value: Any) -> list[str]:
        if isinstance(value, str):
            raw_value = value.strip()
            if not raw_value:
                return []
            if raw_value.startswith("["):
                parsed = json.loads(raw_value)
                if not isinstance(parsed, list):
                    raise ValueError("CORS_ORIGINS JSON must be an array")
                return [str(origin).strip() for origin in parsed if str(origin).strip()]
            return [origin.strip() for origin in raw_value.split(",") if origin.strip()]
        return value


@lru_cache
def get_settings() -> Settings:
    return Settings()
