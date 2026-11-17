FROM python:3.13-slim
WORKDIR /app
COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt && useradd -m -u 10001 courtmate
COPY app.py /app/app.py
COPY static /app/static
RUN mkdir /data && chown 10001:10001 /data
USER 10001
ENV COURTMATE_DB=/data/courtmate_cloud.db
EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
