"""Fictional demo jobs; no scraped recruitment dataset is redistributed."""
import json
from .db_utils import get_db_connection
from .settings import ROOT

def seed_demo_jobs():
    with get_db_connection() as conn:
        if conn.execute("SELECT COUNT(*) FROM job_store").fetchone()[0]:
            return
        for line in (ROOT / "data" / "demo_jobs.jsonl").read_text(encoding="utf-8").splitlines():
            job = json.loads(line)
            columns = list(job)
            placeholders = ",".join("?" for _ in columns)
            conn.execute(
                "INSERT INTO job_store (" + ",".join(columns) + ") VALUES (" + placeholders + ")",
                [job[column] for column in columns]
            )
        conn.commit()
