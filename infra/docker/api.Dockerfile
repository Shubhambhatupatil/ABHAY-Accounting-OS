FROM python:3.12-slim

WORKDIR /app
COPY apps/api/pyproject.toml /app/apps/api/pyproject.toml
COPY apps/api /app/apps/api
RUN pip install --no-cache-dir -e /app/apps/api
WORKDIR /app/apps/api
CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000}"]
