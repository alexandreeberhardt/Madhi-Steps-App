FROM python:3.12.11-slim-bookworm AS base

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /srv/madhi

RUN addgroup --system madhi && adduser --system --ingroup madhi madhi

COPY requirements.txt requirements-dev.txt ./
RUN pip install --no-cache-dir --requirement requirements.txt

COPY app ./app
COPY migrations ./migrations

FROM base AS test

USER root
RUN pip install --no-cache-dir --requirement requirements-dev.txt
COPY pyproject.toml .
COPY tests ./tests
USER madhi

CMD ["pytest"]

FROM base AS runtime

USER madhi

EXPOSE 8080

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080", "--proxy-headers", "--forwarded-allow-ips", "*"]
