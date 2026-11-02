import sqlite3
from datetime import datetime
import json
import logging
import os
from typing import Dict, List, Optional

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

base_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.dirname(base_dir)
from .settings import DB_PATH
from .session_context import require_workspace
DB_NAME = str(DB_PATH)

from contextlib import contextmanager
@contextmanager
def get_db_connection():
    conn = sqlite3.connect(DB_NAME, timeout=10)
    conn.execute("PRAGMA foreign_keys = ON")
    conn.row_factory = sqlite3.Row
    try:
        yield conn
    finally:
        conn.close()

def create_tables():
    with get_db_connection() as conn:
        # Create cv_store table with file_data column
        conn.execute('''CREATE TABLE IF NOT EXISTS cv_store
                        (id INTEGER PRIMARY KEY AUTOINCREMENT,
                         filename TEXT,
                         cv_info_json TEXT,
                         file_data BLOB,
                         upload_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP)''')

        # Migrate existing table if needed (add file_data column if missing)
        cursor = conn.cursor()
        cursor.execute("PRAGMA table_info(cv_store)")
        columns = [row[1] for row in cursor.fetchall()]
        if 'file_data' not in columns:
            logging.info("⚙️ Migrating cv_store: Adding file_data column...")
            conn.execute("ALTER TABLE cv_store ADD COLUMN file_data BLOB")
            logging.info("✅ Migration completed: file_data column added")

        conn.execute('''CREATE TABLE IF NOT EXISTS match_logs
                        (id INTEGER PRIMARY KEY AUTOINCREMENT,
                         session_id TEXT,
                         cv_id INTEGER,
                         matched_jobs_json TEXT,
                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)''')
        conn.execute('''CREATE TABLE IF NOT EXISTS job_store
                        (id INTEGER PRIMARY KEY AUTOINCREMENT,
                         name TEXT,
                         job_title TEXT,
                         job_url TEXT UNIQUE,
                         job_description TEXT,
                         candidate_requirements TEXT,
                         benefits TEXT,
                         work_location TEXT,
                         work_time TEXT,
                         job_tags TEXT,
                         skills TEXT,
                         related_categories TEXT,
                         salary TEXT,
                         experience TEXT,
                         deadline TEXT,
                         company_logo TEXT,
                         company_scale TEXT,
                         company_field TEXT,
                         company_address TEXT,
                         level TEXT,
                         education TEXT,
                         number_of_hires INTEGER,
                         work_type TEXT,
                         company_url TEXT,
                         timestamp TEXT)''')
        conn.execute('''CREATE INDEX IF NOT EXISTS idx_job_store_filters ON job_store
                        (work_type, work_location, experience, education, skills)''')

        # Bảng applications - Lưu lịch sử ứng tuyển
        conn.execute('''CREATE TABLE IF NOT EXISTS applications
                        (id INTEGER PRIMARY KEY AUTOINCREMENT,
                         cv_id INTEGER NOT NULL,
                         job_id INTEGER NOT NULL,
                         status TEXT DEFAULT 'applied',
                         cover_letter TEXT,
                         applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         FOREIGN KEY (cv_id) REFERENCES cv_store(id),
                         FOREIGN KEY (job_id) REFERENCES job_store(id))''')
        conn.execute('''CREATE INDEX IF NOT EXISTS idx_applications_cv ON applications(cv_id)''')
        conn.execute('''CREATE INDEX IF NOT EXISTS idx_applications_job ON applications(job_id)''')

        # Bảng cv_insights - Cache kết quả phân tích CV
        conn.execute('''CREATE TABLE IF NOT EXISTS cv_insights
                        (cv_id INTEGER PRIMARY KEY,
                         quality_score REAL,
                         completeness_score REAL,
                         market_fit_score REAL,
                         strengths TEXT,
                         weaknesses TEXT,
                         missing_sections TEXT,
                         last_analyzed TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         FOREIGN KEY (cv_id) REFERENCES cv_store(id))''')

        # Bảng document_previews - Cache preview tài liệu
        conn.execute('''CREATE TABLE IF NOT EXISTS document_previews
                        (file_id INTEGER PRIMARY KEY,
                         type TEXT DEFAULT 'cv',
                         thumbnail BLOB,
                         summary TEXT,
                         page_count INTEGER,
                         file_size INTEGER,
                         generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         FOREIGN KEY (file_id) REFERENCES cv_store(id))''')

        columns = {row[1] for row in conn.execute("PRAGMA table_info(cv_store)")}
        if "owner_id" not in columns:
            conn.execute("ALTER TABLE cv_store ADD COLUMN owner_id TEXT")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_cv_owner ON cv_store(owner_id)")
        conn.execute("""CREATE TABLE IF NOT EXISTS workspace_sessions(
            token_hash TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, expires_at INTEGER NOT NULL
        )""")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_workspace_expiry ON workspace_sessions(expires_at)")
        conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_application_cv_job ON applications(cv_id,job_id)")
        conn.commit()

def assert_cv_access(cv_id):
    with get_db_connection() as conn:
        found = conn.execute(
            "SELECT 1 FROM cv_store WHERE id=? AND owner_id=?", (cv_id, require_workspace())
        ).fetchone()
    if not found:
        raise LookupError("CV not found.")

def insert_cv_record(filename: str, cv_info: Dict, file_data: bytes = None) -> int:
    if not isinstance(cv_info, dict):
        raise ValueError("cv_info must be a dictionary")
    with get_db_connection() as conn:
        cursor = conn.execute(
            "INSERT INTO cv_store(filename, cv_info_json, file_data, owner_id) VALUES(?,?,?,?)",
            (filename, json.dumps(cv_info, ensure_ascii=False), file_data, require_workspace())
        )
        conn.commit()
        return cursor.lastrowid

def insert_match_log(session_id: str, cv_id: int, matched_jobs: Dict) -> None:
    assert_cv_access(cv_id)
    if not isinstance(session_id, str) or not session_id:
        raise ValueError("session_id must be a non-empty string")
    if not isinstance(cv_id, int):
        raise ValueError("cv_id must be an integer")
    if not isinstance(matched_jobs, (list, dict)):
        raise ValueError("matched_jobs must be a list or dict")
    with get_db_connection() as conn:
        conn.execute('INSERT INTO match_logs (session_id, cv_id, matched_jobs_json) VALUES (?, ?, ?)',
                     (session_id, cv_id, json.dumps(matched_jobs, ensure_ascii=False)))
        conn.commit()

def get_cached_matches(cv_id: int) -> Optional[List[Dict]]:
    assert_cv_access(cv_id)
    """
    Lấy cached matches cho CV (20 jobs đã match trước đó)

    Args:
        cv_id: ID của CV

    Returns:
        List 20 jobs đã match, hoặc None nếu chưa có cache
    """
    with get_db_connection() as conn:
        cursor = conn.cursor()
        cursor.execute(
            'SELECT matched_jobs_json, created_at FROM match_logs WHERE cv_id = ? ORDER BY created_at DESC LIMIT 1',
            (cv_id,)
        )
        row = cursor.fetchone()
        if row:
            try:
                jobs = json.loads(row['matched_jobs_json'])
                # Kiểm tra cache có cũ hơn 1 giờ không
                from datetime import datetime, timedelta
                created_at = datetime.fromisoformat(row['created_at'])
                if datetime.now() - created_at < timedelta(hours=1):
                    logging.info(f"✅ Lấy {len(jobs)} cached jobs cho CV {cv_id}")
                    return jobs
                else:
                    logging.info(f"⚠️ Cache cho CV {cv_id} đã cũ hơn 1 giờ, cần refresh")
                    return None
            except Exception as e:
                logging.error(f"❌ Lỗi parse cached jobs: {e}")
                return None
        return None

def get_match_history(session_id: str) -> List[Dict]:
    if not isinstance(session_id, str) or not session_id:
        raise ValueError("session_id must be a non-empty string")
    with get_db_connection() as conn:
        cursor = conn.cursor()
        cursor.execute('SELECT m.cv_id, m.matched_jobs_json FROM match_logs m JOIN cv_store c ON c.id=m.cv_id WHERE m.session_id=? AND c.owner_id=? ORDER BY m.created_at', (session_id, require_workspace()))
        history = []
        for row in cursor.fetchall():
            try:
                history.append({"cv_id": row["cv_id"], "matched_jobs": json.loads(row["matched_jobs_json"])})
            except json.JSONDecodeError as e:
                logging.warning(f"Failed to parse matched_jobs_json for cv_id {row['cv_id']}: {e}")
                continue
        return history

def get_all_cvs() -> List[Dict]:
    with get_db_connection() as conn:
        cursor = conn.cursor()
        cursor.execute('SELECT id, filename, cv_info_json, upload_timestamp FROM cv_store WHERE owner_id=? ORDER BY upload_timestamp DESC', (require_workspace(),))
        cvs = []
        for row in cursor.fetchall():
            try:
                cvs.append({
                    "id": row["id"],
                    "filename": row["filename"],
                    "cv_info_json": json.loads(row["cv_info_json"]),
                    "upload_timestamp": row["upload_timestamp"]
                })
            except json.JSONDecodeError as e:
                logging.warning(f"Failed to parse cv_info_json for cv_id {row['id']}: {e}")
                continue
        return cvs

def delete_cv_record(cv_id: int) -> bool:
    assert_cv_access(cv_id)
    with get_db_connection() as conn:
        for table, column in (("applications", "cv_id"), ("cv_insights", "cv_id"),
                              ("document_previews", "file_id"), ("match_logs", "cv_id")):
            conn.execute(f"DELETE FROM {table} WHERE {column}=?", (cv_id,))
        deleted = conn.execute("DELETE FROM cv_store WHERE id=? AND owner_id=?", (cv_id, require_workspace()))
        conn.commit()
        return deleted.rowcount == 1

def insert_application(cv_id: int, job_id: int, cover_letter: str = "", status: str = "applied") -> int:
    assert_cv_access(cv_id)
    """Lưu application vào database."""
    with get_db_connection() as conn:
        cursor = conn.cursor()
        cursor.execute('''INSERT INTO applications (cv_id, job_id, cover_letter, status)
                         VALUES (?, ?, ?, ?)''',
                      (cv_id, job_id, cover_letter, status))
        conn.commit()
        return cursor.lastrowid

def get_applications_by_cv(cv_id: int, status: str = None) -> List[Dict]:
    assert_cv_access(cv_id)
    """Lấy danh sách applications của CV."""
    with get_db_connection() as conn:
        cursor = conn.cursor()
        if status:
            cursor.execute('''SELECT a.*, j.job_title, j.company_url, j.salary, j.work_location
                             FROM applications a
                             JOIN job_store j ON a.job_id = j.id
                             WHERE a.cv_id = ? AND a.status = ?
                             ORDER BY a.applied_at DESC''', (cv_id, status))
        else:
            cursor.execute('''SELECT a.*, j.job_title, j.company_url, j.salary, j.work_location
                             FROM applications a
                             JOIN job_store j ON a.job_id = j.id
                             WHERE a.cv_id = ?
                             ORDER BY a.applied_at DESC''', (cv_id,))
        return [dict(row) for row in cursor.fetchall()]

def check_application_exists(cv_id: int, job_id: int) -> bool:
    assert_cv_access(cv_id)
    """Kiểm tra xem đã apply job này chưa."""
    with get_db_connection() as conn:
        cursor = conn.cursor()
        cursor.execute('SELECT id FROM applications WHERE cv_id = ? AND job_id = ?', (cv_id, job_id))
        return cursor.fetchone() is not None

# ===== CV INSIGHTS FUNCTIONS =====

def save_cv_insights(cv_id: int, insights: Dict) -> None:
    assert_cv_access(cv_id)
    """Lưu kết quả phân tích CV."""
    with get_db_connection() as conn:
        conn.execute('''INSERT OR REPLACE INTO cv_insights
                       (cv_id, quality_score, completeness_score, market_fit_score,
                        strengths, weaknesses, missing_sections, last_analyzed)
                       VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)''',
                    (cv_id,
                     insights.get('quality_score'),
                     insights.get('completeness_score'),
                     insights.get('market_fit_score'),
                     json.dumps(insights.get('strengths', []), ensure_ascii=False),
                     json.dumps(insights.get('weaknesses', []), ensure_ascii=False),
                     json.dumps(insights.get('missing_sections', []), ensure_ascii=False)))
        conn.commit()

def get_cv_insights(cv_id: int) -> Optional[Dict]:
    assert_cv_access(cv_id)
    """Lấy kết quả phân tích CV từ cache."""
    with get_db_connection() as conn:
        cursor = conn.cursor()
        cursor.execute('SELECT * FROM cv_insights WHERE cv_id = ?', (cv_id,))
        row = cursor.fetchone()
        if row:
            return {
                'cv_id': row['cv_id'],
                'quality_score': row['quality_score'],
                'completeness_score': row['completeness_score'],
                'market_fit_score': row['market_fit_score'],
                'strengths': json.loads(row['strengths']) if row['strengths'] else [],
                'weaknesses': json.loads(row['weaknesses']) if row['weaknesses'] else [],
                'missing_sections': json.loads(row['missing_sections']) if row['missing_sections'] else [],
                'last_analyzed': row['last_analyzed']
            }
        return None

# ===== DOCUMENT PREVIEW FUNCTIONS =====

def save_document_preview(file_id: int, preview_data: Dict) -> None:
    assert_cv_access(file_id)
    """Lưu preview tài liệu."""
    with get_db_connection() as conn:
        conn.execute('''INSERT OR REPLACE INTO document_previews
                       (file_id, type, summary, page_count, file_size, generated_at)
                       VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)''',
                    (file_id,
                     preview_data.get('type', 'cv'),
                     preview_data.get('summary'),
                     preview_data.get('page_count'),
                     preview_data.get('file_size')))
        conn.commit()

def get_document_preview(file_id: int) -> Optional[Dict]:
    assert_cv_access(file_id)
    """Lấy preview tài liệu từ cache."""
    with get_db_connection() as conn:
        cursor = conn.cursor()
        cursor.execute('SELECT * FROM document_previews WHERE file_id = ?', (file_id,))
        row = cursor.fetchone()
        if row:
            return dict(row)
        return None

# ===== EXISTING FUNCTIONS =====

def get_filtered_jobs(filters: Dict) -> Optional[List[int]]:
    if not isinstance(filters, dict):
        raise ValueError("filters must be a dictionary")
    try:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT COUNT(*) FROM job_store")
            total_jobs = cursor.fetchone()[0]
            if not filters:
                logging.info(f"No filters provided, returning None to query all {total_jobs} jobs")
                return None  # Fallback to query all jobs in Chroma

            query = "SELECT id FROM job_store WHERE 1=1"
            params = []
            if 'job_type' in filters and filters['job_type']:
                if isinstance(filters['job_type'], list):
                    query += f" AND work_type IN ({','.join(['?' for _ in filters['job_type']])})"
                    params.extend(filters['job_type'])
                else:
                    query += " AND work_type = ?"
                    params.append(filters['job_type'])
            if 'work_location' in filters and filters['work_location']:
                if isinstance(filters['work_location'], list):
                    query += f" AND work_location IN ({','.join(['?' for _ in filters['work_location']])})"
                    params.extend(filters['work_location'])
                else:
                    query += " AND work_location LIKE ?"
                    params.append(f"%{filters['work_location']}%")
            if 'deadline_after' in filters and filters['deadline_after']:
                query += " AND deadline > ?"
                params.append(filters['deadline_after'])
            if 'experience' in filters and filters['experience']:
                if isinstance(filters['experience'], list):
                    query += f" AND experience IN ({','.join(['?' for _ in filters['experience']])})"
                    params.extend(filters['experience'])
                else:
                    query += " AND experience = ?"
                    params.append(filters['experience'])
            if 'education' in filters and filters['education']:
                if isinstance(filters['education'], list):
                    query += f" AND education IN ({','.join(['?' for _ in filters['education']])})"
                    params.extend(filters['education'])
                else:
                    query += " AND education = ?"
                    params.append(filters['education'])
            if 'skills' in filters and filters['skills']:
                if isinstance(filters['skills'], list):
                    query += " AND (" + " OR ".join(["LOWER(skills) LIKE LOWER(?)" for _ in filters['skills']]) + ")"
                    params.extend([f"%{skill.lower()}%" for skill in filters['skills']])
                else:
                    query += " AND LOWER(skills) LIKE LOWER(?)"
                    params.append(f"%{filters['skills'].lower()}%")
            try:
                cursor.execute(query, params)
                job_ids = [row['id'] for row in cursor.fetchall()]
                logging.info(f"Filtered {len(job_ids)} jobs with filters: {filters}")
                if not job_ids:
                    logging.warning(f"No jobs matched filters: {filters}, returning None to query all jobs")
                    return None  # Fallback to query all jobs in Chroma
                return job_ids
            except sqlite3.Error as e:
                logging.error(f"SQL query failed: {query}, params: {params}, error: {e}")
                raise
    except Exception as e:
        logging.error(f"Error filtering jobs: {e}")
        raise

def get_total_jobs() -> int:
    with get_db_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM job_store")
        return cursor.fetchone()[0]