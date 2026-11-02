FROM python:3.12-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 YANGDOCS_DB=/app/data/yangdocs.sqlite3
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt && useradd --create-home yangdocs
COPY app ./app
COPY examples ./examples
RUN mkdir -p /app/data && chown -R yangdocs:yangdocs /app
USER yangdocs
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=3s CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=2)" || exit 1
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
