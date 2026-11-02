from .settings import ROOT, ALLOWED_ORIGINS
from typing import List, Optional, Dict, Any
from fastapi import FastAPI, File, UploadFile, HTTPException, Request, Query
from fastapi.middleware.cors import CORSMiddleware
from .pydantic_models import (
    DocumentInfo, DeleteFileRequest, MatchInput, MatchResponse, JobDetails, MatchedJob,
    CVInsightsResponse, CVImproveResponse, ImprovementSuggestion,
    JobSearchInput, JobSearchResponse, JobSearchResult,
    ApplyJobInput, ApplicationResponse, ApplicationItem, ApplicationsResponse,
    DocumentPreviewResponse, SuggestQuestionsInput, SuggestQuestionsResponse, QuestionSuggestion
)
from .providers import (
    match_cv, preload_jobs_to_chroma, index_cv_extracts, delete_cv_from_chroma,
    analyze_cv_insights, generate_cv_improvements, generate_why_match,
    generate_question_suggestions, ai_configured, ensure_ai,
    extract_cv_info as provider_extract_cv_info
)
from .workspace_security import WorkspaceMiddleware, open_workspace
from .session_context import require_workspace
from .upload_safety import read_pdf, write_temporary_pdf, remove_temporary_pdf
from fastapi.responses import Response, RedirectResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field
import sqlite3
from .db_utils import (
    get_db_connection, insert_cv_record, insert_match_log, get_match_history, get_cached_matches,
    get_all_cvs, delete_cv_record, get_filtered_jobs,
    insert_application, get_applications_by_cv, check_application_exists,
    save_cv_insights, get_cv_insights, save_document_preview, get_document_preview
)


import re
import uuid
import logging
import os
import json
from datetime import datetime
import time
from dateutil.parser import parse

# ==== CONFIG ====
# Logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

app = FastAPI(title="YangCareer", description="A derivative of TalentBridge with private CV workspaces.")

app.add_middleware(WorkspaceMiddleware)
app.add_middleware(CORSMiddleware, allow_origins=list(ALLOWED_ORIGINS),
                   allow_credentials=True, allow_methods=["GET", "POST", "OPTIONS"],
                   allow_headers=["Content-Type", "X-CSRF-Token"])

# === PATH ===
base_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.dirname(base_dir)
data_path = os.path.join(project_root, "data", "demo_jobs.jsonl")
db_path = os.path.join(project_root, "db")
os.makedirs(db_path, exist_ok=True)

@app.on_event("startup")
async def startup_event():
    from .db_utils import create_tables
    from .demo_data import seed_demo_jobs
    create_tables()
    seed_demo_jobs()
    if ai_configured() and os.environ.get("INDEX_ON_STARTUP", "0") == "1":
        preload_jobs_to_chroma(data_path, batch_size=20)

def extract_text_from_pdf(pdf_path: str) -> str:
    try:
        import pdfplumber
    except ImportError:
        raise HTTPException(503, "Cài requirements-ai.txt để đọc CV PDF.")
    try:
        with pdfplumber.open(pdf_path) as pdf:
            text = "\n".join(page.extract_text() or "" for page in pdf.pages)
        if not text.strip():
            raise HTTPException(400, "PDF chưa có văn bản đọc được. Bản này chưa hỗ trợ OCR.")
        return text
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(400, "Không thể đọc PDF. Hãy kiểm tra file có bị hỏng hoặc khóa mật khẩu.")

def extract_cv_info(cv_text: str) -> dict:
    return provider_extract_cv_info(cv_text)

def parse_cv_input_string(cv_input: str) -> dict:
    """Parse chuỗi cv_input thành dictionary."""
    try:
        # Nếu cv_input là JSON string, parse trực tiếp
        if cv_input.strip().startswith('{'):
            return json.loads(cv_input)

        # Nếu cv_input là chuỗi text, parse thủ công
        cv_info = {
            "skills": [],
            "career_objective": "",
            "experience": [],
            "education": [],
            "name": "",
            "email": "",
            "phone": ""
        }

        # Giả định format: "Skills: ...; Aspirations: ...; Experience: ...; Education: ..."
        sections = re.split(r'(Skills|Aspirations|Experience|Education|Name|Email|Phone):', cv_input, flags=re.IGNORECASE)
        for i in range(1, len(sections), 2):
            key = sections[i].lower()
            value = sections[i + 1].strip()
            if key == "skills":
                cv_info["skills"] = [s.strip() for s in value.split(',') if s.strip()]
            elif key == "aspirations":
                cv_info["career_objective"] = value
            elif key == "name":
                cv_info["name"] = value
            elif key == "email":
                cv_info["email"] = value
            elif key == "phone":
                cv_info["phone"] = value
            elif key == "experience":
                exp_entries = value.split('\n')
                for entry in exp_entries:
                    if entry.strip():
                        exp = {"company": "Unknown", "title": "Unknown", "start_date": "", "end_date": "", "description": ""}
                        fields = re.split(r';|,', entry)
                        for field in fields:
                            if ':' in field:
                                k, v = field.split(':', 1)
                                k = k.strip().lower()
                                v = v.strip()
                                if k in ["company", "title", "description"]:
                                    exp[k] = v
                                elif k in ["start_date", "end_date"]:
                                    exp[k] = normalize_date(v)
                        cv_info["experience"].append(exp)
            elif key == "education":
                edu_entries = value.split('\n')
                for entry in edu_entries:
                    if entry.strip():
                        edu = {"school": "Unknown", "degree": "Unknown", "major": "Unknown", "start_date": "", "end_date": ""}
                        fields = re.split(r';|,', entry)
                        for field in fields:
                            if ':' in field:
                                k, v = field.split(':', 1)
                                k = k.strip().lower()
                                v = v.strip()
                                if k in ["school", "degree", "major"]:
                                    edu[k] = v
                                elif k in ["start_date", "end_date"]:
                                    edu[k] = normalize_date(v)
                        cv_info["education"].append(edu)
        return cv_info
    except json.JSONDecodeError:
        logging.error(f"Invalid JSON in cv_input string: {cv_input[:100]}...")
        raise HTTPException(status_code=400, detail="Invalid JSON format in cv_input")
    except Exception as e:
        logging.error(f"Error parsing cv_input string: {str(e)}")
        raise HTTPException(status_code=400, detail=f"Failed to parse cv_input string: {str(e)}")


def normalize_deadline(val: str) -> str:
    """Đưa deadline bất kỳ (vd 09/10/2025, 2025/10/09, 09-10-2025, '04/10/2025', ...) về YYYY-MM-DD.
    Không parse được thì trả về chuỗi rỗng."""
    if not val:
        return ""
    s = str(val).strip()
    if not s or s.lower() in {"n/a", "none", "null", "không xác định"}:
        return ""
    # Thử parse "dayfirst" để nhận dạng định dạng Việt Nam dd/mm/yyyy
    try:
        dt = _dt_parse(s, dayfirst=True, fuzzy=True)
        return dt.strftime("%Y-%m-%d")
    except Exception:
        pass
    # Thử vài regex phổ biến nếu cần
    import re
    m = re.match(r"^(\d{2})[/-](\d{2})[/-](\d{4})$", s)
    if m:
        d, mth, y = m.groups()
        try:
            dt = datetime(int(y), int(mth), int(d))
            return dt.strftime("%Y-%m-%d")
        except Exception:
            return ""
    return ""

def get_job_details(job_ids: List[int]) -> List[JobDetails]:
    """Lấy chi tiết công việc từ job_store dựa trên job_id (chuẩn hoá deadline -> YYYY-MM-DD)."""
    if not job_ids or not all(isinstance(job_id, int) for job_id in job_ids):
        raise HTTPException(status_code=400, detail="job_ids must be a non-empty list of integers")
    try:
        with get_db_connection() as conn:
            cursor = conn.cursor()
            batch_size = 100
            jobs: List[JobDetails] = []
            for i in range(0, len(job_ids), batch_size):
                batch_ids = job_ids[i:i + batch_size]
                placeholders = ",".join(["?"] * len(batch_ids))
                cursor.execute(f"SELECT * FROM job_store WHERE id IN ({placeholders})", batch_ids)
                rows = cursor.fetchall()
                for row in rows:
                    rd = dict(row)

                    # 🔧 Chuẩn hoá deadline về YYYY-MM-DD để hợp schema Pydantic
                    if "deadline" in rd:
                        rd["deadline"] = normalize_deadline(rd["deadline"])

                    # (Tùy chọn an toàn) Nếu skills lưu dạng JSON string, có thể parse:
                    # if isinstance(rd.get("skills"), str) and rd["skills"].strip().startswith("["):
                    #     try:
                    #         rd["skills"] = json.loads(rd["skills"])
                    #     except Exception:
                    #         pass

                    jobs.append(JobDetails(**rd))
            return jobs
    except Exception as e:
        logging.error(f"Error fetching job details for IDs {job_ids}: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Failed to fetch job details: {str(e)}")


def normalize_date(date_str: str) -> str:
    """Chuẩn hóa định dạng ngày thành YYYY-MM-DD hoặc giữ 'Present'."""
    if not date_str or date_str.lower() == "present":
        return "Present"
    try:
        parsed = parse(date_str, fuzzy=True)
        return parsed.strftime("%Y-%m-%d")
    except (ValueError, TypeError):
        logging.warning(f"Invalid date format: {date_str}, defaulting to empty")
        return ""

@app.get("/")
async def root():
    return RedirectResponse("/app/")

@app.post("/upload-cv")
async def upload_cv(file: UploadFile = File(...)):
    filename, file_data = await read_pdf(file)
    temp_path = None
    try:
        ensure_ai()
        temp_path = write_temporary_pdf(file_data)
        cv_info = extract_cv_info(extract_text_from_pdf(temp_path))
        if not cv_info.get("skills") and not cv_info.get("career_objective"):
            raise HTTPException(400, "CV chưa có kỹ năng hoặc mục tiêu nghề nghiệp để phân tích.")
        cv_id = insert_cv_record(filename, cv_info, file_data)
        return {"message": "CV đã được lưu trong không gian riêng.", "cv_id": cv_id, "cv_info": cv_info}
    except HTTPException:
        raise
    except Exception:
        logging.exception("CV upload processing failed")
        raise HTTPException(502, "Không thể xử lý CV. Vui lòng kiểm tra PDF và thử lại.")
    finally:
        remove_temporary_pdf(temp_path)

@app.post("/match", response_model=MatchResponse)
async def match_cv_endpoint(input: MatchInput, request: Request):
    """Khớp CV với công việc, sử dụng lọc trước và post-processing."""
    start_time = time.time()

    valid_keys = {"job_type", "work_location", "experience", "education", "skills", "deadline_after"}
    cleaned_filters = {k: v for k, v in input.filters.items() if k in valid_keys}
    if cleaned_filters != input.filters:
        logging.warning(f"Bộ lọc không hợp lệ được bỏ qua: {input.filters}. Sử dụng: {cleaned_filters}")

    session_id = input.session_id or str(uuid.uuid4())
    model_name = input.model.value
    cv_id = None

    try:
        # Lấy CV
        cv_start = time.time()
        if input.cv_id:
            cv_id = input.cv_id
            if not isinstance(cv_id, int):
                raise HTTPException(status_code=400, detail="cv_id must be an integer")
            with get_db_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("SELECT id, filename, cv_info_json FROM cv_store WHERE id = ?", (input.cv_id,))
                cv = cursor.fetchone()
                if not cv:
                    raise HTTPException(status_code=404, detail=f"CV với ID {input.cv_id} không tìm thấy")
                cv_info = json.loads(cv["cv_info_json"])
        elif input.cv_input:
            cv_id = input.cv_id or str(uuid.uuid4())
            cv_info = parse_cv_input_string(input.cv_input)
            cv_id = insert_cv_record("manual_input", cv_info)
            await index_cv_extracts(cv_info["skills"], cv_info["career_objective"], cv_info["education"], cv_id)
        else:
            cvs = get_all_cvs()
            if not cvs:
                raise HTTPException(status_code=404, detail="Không tìm thấy CV nào")
            cv = cvs[0]
            cv_id = cv["id"]
            cv_info = json.loads(cv["cv_info_json"])
        logging.info(f"✅ Lấy CV {cv_id} thành công ({time.time() - cv_start:.2f}s)")

        # Chuẩn hóa education và experience
        input_start = time.time()
        skills = cv_info.get("skills", [])
        aspirations = cv_info.get("career_objective", "")
        education = cv_info.get("education", [])
        experience = cv_info.get("experience", [])

        for edu in education:
            try:
                edu["start_date"] = normalize_date(edu.get("start_date", ""))
                edu["end_date"] = normalize_date(edu.get("end_date", ""))
                edu["school"] = edu.get("school") or "Unknown"
                edu["degree"] = edu.get("degree") or "Unknown"
                edu["major"] = edu.get("major") or "Unknown"
            except Exception as e:
                logging.warning(f"Error normalizing education: {str(e)}")
                edu["start_date"] = ""
                edu["end_date"] = ""
                edu["school"] = "Unknown"
                edu["degree"] = "Unknown"
                edu["major"] = "Unknown"

        for exp in experience:
            try:
                exp["start_date"] = normalize_date(exp.get("start_date", ""))
                exp["end_date"] = normalize_date(exp.get("end_date", ""))
                exp["company"] = exp.get("company") or "Unknown"
                exp["title"] = exp.get("title") or "Unknown"
                exp["description"] = exp.get("description") or "No description provided"
            except Exception as e:
                logging.warning(f"Error normalizing experience: {str(e)}")
                exp["start_date"] = ""
                exp["end_date"] = ""
                exp["company"] = "Unknown"
                exp["title"] = "Unknown"
                exp["description"] = "No description provided"



        experience_summary = "\n".join([
            f"Project: {exp['title']} - {exp['description'][:200] + '...' if len(exp['description']) > 200 else exp['description']}"
            for exp in experience
        ]) if experience else "No experience provided"
        education_summary = "\n".join([
            f"Degree: {edu['degree']} at {edu['school']} ({edu['start_date']}-{edu['end_date']})"
            for edu in education
        ]) if education else "No education provided"
        aspirations_summary = aspirations if aspirations else "No career objective provided"

        cv_input = {
            "skills": skills,
            "aspirations": aspirations_summary,
            "experience": experience_summary,
            "education": education_summary,
            "cv_id": cv_id
        }

        # Lấy filtered_job_ids
        filtered_job_ids = get_filtered_jobs(cleaned_filters)
        suggestions = []
        if filtered_job_ids is None:
            suggestions = [{"skill_or_experience": "N/A", "suggestion": "No filters applied or no jobs matched, showing best matches from all jobs."}]
        else:
            logging.info(f"✅ Lọc được {len(filtered_job_ids)} jobs")

        # Kiểm tra cache trước
        cached_jobs = get_cached_matches(cv_id)

        if cached_jobs and not cleaned_filters:
            # Có cache và không có filters → Dùng cache
            logging.info(f"🚀 Sử dụng cached matches cho CV {cv_id} (skip RAG)")
            matched_jobs_all = cached_jobs
            suggestions = []
        else:
            # Chạy RAG với match_cv
            try:
                invoke_start = time.time()
                result = await match_cv(cv_input, filtered_job_ids, session_id)
                logging.info(f"✅ Match CV hoàn tất ({time.time() - invoke_start:.2f}s)")

                if not result or not isinstance(result, dict):
                    logging.error("match_cv trả về kết quả rỗng")
                    return MatchResponse(
                        name=cv_info.get("name", ""),
                        email=cv_info.get("email", ""),
                        phone=cv_info.get("phone", ""),
                        cv_skills=skills,
                        career_objective=aspirations,
                        education=education,
                        experience=experience,
                        matched_jobs=[],
                        suggestions=[{"skill_or_experience": "N/A", "suggestion": "Failed to match jobs"}],
                        session_id=session_id,
                        model=input.model
                    )

                answer = result

                # Lấy TẤT CẢ matched_jobs (có thể lên đến 20)
                matched_jobs_all = answer.get("matched_jobs", [])
                suggestions = answer.get("suggestions", []) or suggestions

                logging.info(f"📊 Nhận được {len(matched_jobs_all)} jobs từ RAG")
            except Exception as e:
                logging.error(f"❌ Lỗi match_cv: {e}")
                raise

        # Validate matched_jobs_all
        if not isinstance(matched_jobs_all, list):
            logging.error(f"matched_jobs không phải danh sách: {matched_jobs_all}")
            matched_jobs_all = []

        for job in matched_jobs_all[:]:
            if not isinstance(job, dict) or "job_id" not in job or "match_score" not in job:
                logging.warning(f"Job thiếu job_id hoặc match_score: {job}. Skipping.")
                matched_jobs_all.remove(job)
                continue
            for field in ["matched_skills", "matched_aspirations", "matched_experience", "matched_education", "skills"]:
                if not isinstance(job.get(field, []), list):
                    logging.error(f"Trường {field} không phải danh sách trong job: {job}")
                    job[field] = []

        if not isinstance(suggestions, list):
            logging.error(f"suggestions không phải danh sách: {suggestions}")
            suggestions = []

        # Post-processing: chuẩn hóa job_id -> int, enrich từ DB
        import re
        def _to_int_job_id(x):
            """Nhận int, '716', 'job_716'... -> int; invalid -> None"""
            if isinstance(x, int):
                return x
            if isinstance(x, str):
                m = re.search(r"\d+", x.strip())
                if m:
                    try:
                        return int(m.group())
                    except Exception:
                        return None
            return None

        # 1) Chuẩn hóa danh sách job_id cho TẤT CẢ jobs
        job_ids: List[int] = []
        for job in matched_jobs_all:
            jid_int = _to_int_job_id(job.get("job_id"))
            if jid_int is None:
                logging.warning(f"Invalid job_id skipped: {job.get('job_id')}")
                continue
            job_ids.append(jid_int)

        # 2) Lấy chi tiết từ DB
        if not job_ids:
            job_details = []
        else:
            job_details = get_job_details(job_ids)

        # 3) Map chi tiết theo INT key (quan trọng)
        job_details_dict = {int(job.job_id): job for job in job_details if getattr(job, "job_id", None) is not None}

        # 4) Enrich TẤT CẢ jobs (lên đến 20 jobs)
        enriched_all_jobs = []
        for job in matched_jobs_all:
            jid_int = _to_int_job_id(job.get("job_id"))
            if jid_int is None:
                continue

            detail = job_details_dict.get(jid_int)
            if not detail:
                logging.warning(f"Job ID {job.get('job_id')} not found in job_details")
                continue

            # match_score có thể là 0..1 hoặc phần trăm >1 -> đưa về 0..1
            ms = job.get("match_score", 0.0)
            try:
                ms = float(ms)
                if ms > 1.0:          # ví dụ 62 -> 0.62
                    ms = ms / 100.0
                ms = max(0.0, min(1.0, ms))
            except Exception:
                ms = 0.0

            enriched_all_jobs.append(
                MatchedJob(
                    job_id=str(jid_int),
                    job_title=job.get("job_title") or getattr(detail, "job_title", ""),
                    job_url=(job.get("job_url") or getattr(detail, "job_url", "")),
                    match_score=ms,
                    matched_skills=job.get("matched_skills") or [],
                    matched_aspirations=job.get("matched_aspirations") or [],
                    matched_experience=job.get("matched_experience") or [],
                    matched_education=job.get("matched_education") or [],
                    work_location=getattr(detail, "work_location", None),
                    salary=getattr(detail, "salary", None),
                    deadline=getattr(detail, "deadline", None),
                    benefits=getattr(detail, "benefits", None),
                    job_type=getattr(detail, "work_type", None),
                    experience_required=getattr(detail, "experience", None),
                    education_required=getattr(detail, "education", None),
                    company_name=getattr(detail, "name", None),
                    skills=getattr(detail, "skills", None),
                    why_match=job.get("why_match", None),  # AI-generated reason
                    job_description=getattr(detail, "job_description", None),
                )
            )

        # 5) Lưu TẤT CẢ jobs vào cache (20 jobs)
        safe_all_jobs = [
            {
                "job_id": job.job_id,
                "job_title": job.job_title,
                "job_url": job.job_url,
                "match_score": job.match_score,
                "matched_skills": job.matched_skills,
                "matched_aspirations": job.matched_aspirations,
                "matched_experience": job.matched_experience,
                "matched_education": job.matched_education,
                "work_location": job.work_location,
                "salary": job.salary,
                "deadline": job.deadline,
                "benefits": job.benefits,
                "job_type": job.job_type,
                "experience_required": job.experience_required,
                "education_required": job.education_required,
                "company_name": job.company_name,
                "skills": job.skills,
                "why_match": job.why_match,
                "job_description": job.job_description
            }
            for job in enriched_all_jobs
        ]

        # Lưu cache (20 jobs)
        if not cached_jobs:  # Chỉ lưu nếu không dùng cache
            insert_match_log(session_id, cv_id, safe_all_jobs)
            logging.info(f"💾 Đã cache {len(safe_all_jobs)} jobs cho CV {cv_id}")

        # 6) Trả về TOP 5 jobs
        top_5_jobs = enriched_all_jobs[:5]

        logging.info(f"✅ Hoàn tất! CV {cv_id} matched {len(enriched_all_jobs)} jobs, trả về top {len(top_5_jobs)} | Tổng thời gian: {time.time() - start_time:.2f}s")

        return MatchResponse(
            name=cv_info.get("name", ""),
            email=cv_info.get("email", ""),
            phone=cv_info.get("phone", ""),
            cv_skills=skills,
            career_objective=aspirations,
            education=education,
            experience=experience,
            matched_jobs=top_5_jobs,  # Chỉ trả về top 5
            suggestions=suggestions,
            session_id=session_id,
            model=input.model
        )
    except Exception as e:
        logging.error(f"Lỗi khi khớp CV {cv_id}: {str(e)}")
        return MatchResponse(
                name=cv_info.get("name", ""),
                email=cv_info.get("email", ""),
                phone=cv_info.get("phone", ""),
                cv_skills=skills,
                career_objective=aspirations,
                education=education,
                experience=experience,
                matched_jobs=[],
                suggestions=[{"skill_or_experience": "N/A", "suggestion": f"Failed to match jobs: {str(e)}"}],
                session_id=session_id,
                model=input.model
            )
    except json.JSONDecodeError as e:
        logging.error(f"JSON không hợp lệ trong CV {cv_id if cv_id else 'chưa xác định'}: {str(e)}")
        raise HTTPException(status_code=500, detail="Dữ liệu JSON CV không hợp lệ")
    except Exception as e:
        logging.error(f"Lỗi khi truy cập CV {cv_id if cv_id else 'chưa xác định'}: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Không thể truy cập dữ liệu CV: {str(e)}")
    
@app.get("/list-cvs", response_model=List[DocumentInfo])
async def list_cvs(page: int = Query(1, ge=1), page_size: int = Query(10, ge=1, le=100)):
    with get_db_connection() as conn:
        rows = conn.execute(
            "SELECT id,filename,cv_info_json,upload_timestamp FROM cv_store WHERE owner_id=? ORDER BY id DESC LIMIT ? OFFSET ?",
            (require_workspace(), page_size, (page - 1) * page_size)
        ).fetchall()
    return [DocumentInfo(**dict(row)) for row in rows]

@app.post("/delete-cv")
async def delete_cv(request: DeleteFileRequest):
    if not delete_cv_record(request.file_id):
        raise HTTPException(404, "CV not found.")
    return {"message": "Đã xóa CV và dữ liệu liên quan."}

@app.get("/cv/{cv_id}/insights", response_model=CVInsightsResponse)
async def get_cv_insights_endpoint(cv_id: int):
    """
    Phân tích CV chuyên sâu - Đánh giá chất lượng, điểm mạnh/yếu

    Khác với /upload-cv (chỉ parse thông tin cơ bản),
    endpoint này phân tích và đánh giá CV bằng AI.
    """
    try:
        # Kiểm tra CV tồn tại
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT cv_info_json FROM cv_store WHERE id = ?", (cv_id,))
            row = cursor.fetchone()
            if not row:
                raise HTTPException(status_code=404, detail=f"CV {cv_id} không tìm thấy")
            cv_info = json.loads(row["cv_info_json"])

        # Kiểm tra cache
        cached_insights = get_cv_insights(cv_id)
        if cached_insights:
            logging.info(f"✅ Lấy insights từ cache cho CV {cv_id}")
            return CVInsightsResponse(
                cv_id=cv_id,
                quality_score=cached_insights['quality_score'],
                completeness={
                    "has_portfolio": False,
                    "has_certifications": False,
                    "has_projects": False,
                    "missing_sections": cached_insights['missing_sections']
                },
                market_fit={
                    "skill_match_rate": cached_insights['market_fit_score'],
                    "experience_level": "Junior",
                    "salary_range": "8-12 triệu",
                    "competitive_score": cached_insights['completeness_score'] * 10
                },
                strengths=cached_insights['strengths'],
                weaknesses=cached_insights['weaknesses'],
                last_analyzed=cached_insights['last_analyzed']
            )

        # Phân tích mới bằng AI
        logging.info(f"🔍 Bắt đầu phân tích CV {cv_id}...")
        insights = await analyze_cv_insights(cv_info)

        # Lưu vào cache
        save_cv_insights(cv_id, insights)

        logging.info(f"✅ Phân tích CV {cv_id} hoàn tất")
        return CVInsightsResponse(
            cv_id=cv_id,
            quality_score=insights.get('quality_score', 5.0),
            completeness={
                "has_portfolio": insights.get('has_portfolio', False),
                "has_certifications": insights.get('has_certifications', False),
                "has_projects": insights.get('has_projects', False),
                "missing_sections": insights.get('missing_sections', [])
            },
            market_fit={
                "skill_match_rate": insights.get('market_fit_score', 0.5),
                "experience_level": insights.get('experience_level', 'Unknown'),
                "salary_range": insights.get('salary_range', 'N/A'),
                "competitive_score": insights.get('competitive_score', 5.0)
            },
            strengths=insights.get('strengths', []),
            weaknesses=insights.get('weaknesses', []),
            last_analyzed=datetime.now().isoformat()
        )

    except HTTPException:
        raise
    except Exception as e:
        logging.error(f"❌ Lỗi phân tích CV {cv_id}: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Lỗi phân tích CV: {str(e)}")


@app.post("/cv/improve", response_model=CVImproveResponse)
async def improve_cv_endpoint(cv_id: int = Query(..., description="ID của CV cần cải thiện")):
    """
    Gợi ý cải thiện CV cụ thể

    Dựa trên kết quả phân tích từ /cv/{cv_id}/insights,
    endpoint này đưa ra các gợi ý hành động cụ thể.
    """
    try:
        # Lấy CV info
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT cv_info_json FROM cv_store WHERE id = ?", (cv_id,))
            row = cursor.fetchone()
            if not row:
                raise HTTPException(status_code=404, detail=f"CV {cv_id} không tìm thấy")
            cv_info = json.loads(row["cv_info_json"])

        # Lấy insights (hoặc phân tích mới)
        insights = get_cv_insights(cv_id)
        if not insights:
            logging.info(f"Chưa có insights, phân tích CV {cv_id} trước...")
            insights_data = await analyze_cv_insights(cv_info)
            save_cv_insights(cv_id, insights_data)
            insights = insights_data

        # Tạo gợi ý cải thiện
        logging.info(f"💡 Tạo gợi ý cải thiện cho CV {cv_id}...")
        improvements = await generate_cv_improvements(cv_info, insights)

        improvement_suggestions = [
            ImprovementSuggestion(**imp) for imp in improvements
        ]

        logging.info(f"✅ Tạo {len(improvement_suggestions)} gợi ý cải thiện")
        return CVImproveResponse(
            cv_id=cv_id,
            improvements=improvement_suggestions
        )

    except HTTPException:
        raise
    except Exception as e:
        logging.error(f"❌ Lỗi tạo gợi ý cải thiện CV {cv_id}: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Lỗi tạo gợi ý: {str(e)}")


@app.post("/jobs/search", response_model=JobSearchResponse)
async def search_jobs_endpoint(input: JobSearchInput):
    """
    Tìm kiếm jobs thông minh với AI ranking

    Khác với /jobs (chỉ list tất cả),
    endpoint này cho phép search với query, filters và AI ranking theo CV.
    """
    try:
        query = input.query
        filters = input.filters
        cv_id = input.cv_id
        limit = input.limit
        offset = input.offset

        logging.info(f"🔍 Tìm kiếm jobs: query='{query}', filters={filters}, cv_id={cv_id}")

        # Lấy CV info nếu có cv_id (để AI ranking)
        cv_info = None
        if cv_id:
            with get_db_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("SELECT cv_info_json FROM cv_store WHERE id = ?", (cv_id,))
                row = cursor.fetchone()
                if row:
                    cv_info = json.loads(row["cv_info_json"])

        # Build SQL query
        sql = "SELECT * FROM job_store WHERE 1=1"
        params = []

        # Text search
        if query:
            sql += " AND (job_title LIKE ? OR job_description LIKE ? OR skills LIKE ?)"
            search_term = f"%{query}%"
            params.extend([search_term, search_term, search_term])

        # Filters
        if filters.get('work_location'):
            locations = filters['work_location']
            placeholders = ','.join(['?' for _ in locations])
            sql += f" AND work_location IN ({placeholders})"
            params.extend(locations)

        if filters.get('work_type'):
            work_types = filters['work_type']
            placeholders = ','.join(['?' for _ in work_types])
            sql += f" AND work_type IN ({placeholders})"
            params.extend(work_types)

        if filters.get('experience'):
            sql += " AND experience = ?"
            params.append(filters['experience'])

        if filters.get('salary_min'):
            # Simple salary filter (can be improved)
            pass

        # Pagination
        sql += " LIMIT ? OFFSET ?"
        params.extend([limit, offset])

        # Execute query
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(sql, params)
            jobs = [dict(row) for row in cursor.fetchall()]

        # Count total
        count_sql = sql.split("LIMIT")[0]
        count_params = params[:-2]
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute(f"SELECT COUNT(*) as total FROM ({count_sql})", count_params)
            total = cursor.fetchone()['total']

        # AI ranking nếu có cv_id - SIMPLE MATCHING (không dùng semantic search)
        results = []

        for job in jobs:
            job_skills = [s.strip() for s in job.get('skills', '').split(';') if s.strip()]

            match_score = None
            why_match = None

            if cv_info:
                cv_skills = cv_info.get('skills', [])
                matched_skills = {s.casefold() for s in cv_skills} & {s.casefold() for s in job_skills}
                match_score = len(matched_skills) / max(len(job_skills), 1) if job_skills else 0.0

                # Generate simple why_match
                if matched_skills:
                    why_match = f"Khớp {len(matched_skills)} kỹ năng: {', '.join(list(matched_skills)[:3])}"
                else:
                    why_match = "Chưa có kỹ năng trùng khớp trong dữ liệu đã nhập."

            # Get company name from various possible fields
            company_name = job.get('name') or job.get('company_name') or job.get('company') or 'Unknown Company'

            results.append(JobSearchResult(
                job_id=job['id'],
                job_title=job['job_title'],
                company_name=company_name,
                match_score=match_score,
                salary=job.get('salary', 'N/A'),
                work_location=job.get('work_location', 'N/A'),
                work_type=job.get('work_type', 'N/A'),
                deadline=job.get('deadline', 'N/A'),
                why_match=why_match
            ))

        # Sort by match_score if available
        if cv_info:
            results.sort(key=lambda x: x.match_score or 0, reverse=True)

        logging.info(f"✅ Tìm được {total} jobs, trả về {len(results)} jobs")
        return JobSearchResponse(
            total=total,
            jobs=results,
            limit=limit,
            offset=offset
        )

    except Exception as e:
        logging.error(f"❌ Lỗi tìm kiếm jobs: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Lỗi tìm kiếm: {str(e)}")


@app.post("/apply", response_model=ApplicationResponse)
async def apply_job_endpoint(input: ApplyJobInput):
    """
    Ứng tuyển job - Lưu lại hành động ứng tuyển

    Endpoint mới để tracking việc ứng tuyển của user.
    """
    try:
        cv_id = input.cv_id
        job_id = input.job_id
        cover_letter = input.cover_letter
        status = input.status

        # Kiểm tra CV tồn tại
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT id FROM cv_store WHERE id = ?", (cv_id,))
            if not cursor.fetchone():
                raise HTTPException(status_code=404, detail=f"CV {cv_id} không tìm thấy")

        # Kiểm tra job tồn tại
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT id FROM job_store WHERE id = ?", (job_id,))
            if not cursor.fetchone():
                raise HTTPException(status_code=404, detail=f"Job {job_id} không tìm thấy")

        # Kiểm tra đã apply chưa
        if check_application_exists(cv_id, job_id):
            raise HTTPException(status_code=409, detail=f"Đã ứng tuyển job {job_id} rồi")

        # Lưu application
        app_id = insert_application(cv_id, job_id, cover_letter, status)

        logging.info(f"✅ CV {cv_id} đã ứng tuyển job {job_id}")
        return ApplicationResponse(
            application_id=app_id,
            cv_id=cv_id,
            job_id=job_id,
            status=status,
            applied_at=datetime.now().isoformat(),
            message=f"Đã ứng tuyển job {job_id} thành công"
        )

    except HTTPException:
        raise
    except sqlite3.IntegrityError:
        raise HTTPException(409, "Hồ sơ đã được ghi nhận cho công việc này.")
    except Exception as e:
        logging.error(f"❌ Lỗi ứng tuyển: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Lỗi ứng tuyển: {str(e)}")


@app.get("/applications/{cv_id}", response_model=ApplicationsResponse)
async def get_applications_endpoint(
    cv_id: int,
    status: Optional[str] = Query(None, description="Lọc theo status (applied, pending, accepted, rejected)")
):
    """
    Lấy lịch sử ứng tuyển của CV

    Endpoint mới để xem các job đã apply.
    """
    try:
        # Kiểm tra CV tồn tại
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT id FROM cv_store WHERE id = ?", (cv_id,))
            if not cursor.fetchone():
                raise HTTPException(status_code=404, detail=f"CV {cv_id} không tìm thấy")

        # Lấy applications
        applications = get_applications_by_cv(cv_id, status)

        app_items = [
            ApplicationItem(
                id=app['id'],
                cv_id=app['cv_id'],
                job_id=app['job_id'],
                job_title=app['job_title'],
                company_url=app['company_url'],
                salary=app['salary'],
                work_location=app['work_location'],
                status=app['status'],
                applied_at=app['applied_at']
            )
            for app in applications
        ]

        logging.info(f"✅ Lấy {len(app_items)} applications cho CV {cv_id}")
        return ApplicationsResponse(
            cv_id=cv_id,
            total=len(app_items),
            applications=app_items
        )

    except HTTPException:
        raise
    except Exception as e:
        logging.error(f"❌ Lỗi lấy applications: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Lỗi lấy applications: {str(e)}")


@app.get("/preview-doc/{file_id}")
async def preview_document_pdf(file_id: int):
    with get_db_connection() as conn:
        row = conn.execute(
            "SELECT file_data FROM cv_store WHERE id=? AND owner_id=?", (file_id, require_workspace())
        ).fetchone()
    if not row or not row["file_data"]:
        raise HTTPException(404, "CV không có PDF để xem trước.")
    return Response(bytes(row["file_data"]), media_type="application/pdf",
                    headers={"Content-Disposition": 'inline; filename="cv.pdf"', "Cache-Control": "no-store"})

@app.get("/preview-doc-info/{file_id}", response_model=DocumentPreviewResponse)
async def preview_document_info(file_id: int):
    """
    Xem trước tài liệu (CV) - Hiển thị summary, thumbnail

    Endpoint mới để preview CV trước khi submit hoặc xem lại.
    """
    try:
        # Kiểm tra cache
        cached_preview = get_document_preview(file_id)
        if cached_preview:
            logging.info(f"✅ Lấy preview từ cache cho file {file_id}")

            # Lấy thông tin CV
            with get_db_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("SELECT filename, cv_info_json FROM cv_store WHERE id = ?", (file_id,))
                row = cursor.fetchone()
                if not row:
                    raise HTTPException(status_code=404, detail=f"File {file_id} không tìm thấy")

                cv_info = json.loads(row["cv_info_json"])

                return DocumentPreviewResponse(
                    file_id=file_id,
                    type=cached_preview['type'],
                    filename=row['filename'],
                    preview={
                        "title": f"CV - {cv_info.get('name', 'Unknown')}",
                        "summary": cached_preview['summary'],
                        "page_count": cached_preview['page_count'],
                        "file_size": f"{cached_preview['file_size'] / 1024:.1f} KB" if cached_preview['file_size'] else "N/A"
                    },
                    quick_info={
                        "name": cv_info.get('name', 'N/A'),
                        "email": cv_info.get('email', 'N/A'),
                        "phone": cv_info.get('phone', 'N/A'),
                        "top_skills": cv_info.get('skills', [])[:5]
                    }
                )

        # Tạo preview mới
        with get_db_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT filename, cv_info_json FROM cv_store WHERE id = ?", (file_id,))
            row = cursor.fetchone()
            if not row:
                raise HTTPException(status_code=404, detail=f"File {file_id} không tìm thấy")

            cv_info = json.loads(row["cv_info_json"])

            # Tạo summary
            skills_str = ", ".join(cv_info.get('skills', [])[:5])
            summary = f"{cv_info.get('name', 'Unknown')} - {skills_str}"

            # Lưu preview
            preview_data = {
                "type": "cv",
                "summary": summary,
                "page_count": 1,  # Placeholder
                "file_size": 0  # Placeholder
            }
            save_document_preview(file_id, preview_data)

            logging.info(f"✅ Tạo preview mới cho file {file_id}")
            return DocumentPreviewResponse(
                file_id=file_id,
                type="cv",
                filename=row['filename'],
                preview={
                    "title": f"CV - {cv_info.get('name', 'Unknown')}",
                    "summary": summary,
                    "page_count": 1,
                    "file_size": "N/A"
                },
                quick_info={
                    "name": cv_info.get('name', 'N/A'),
                    "email": cv_info.get('email', 'N/A'),
                    "phone": cv_info.get('phone', 'N/A'),
                    "top_skills": cv_info.get('skills', [])[:5]
                }
            )

    except HTTPException:
        raise
    except Exception as e:
        logging.error(f"❌ Lỗi preview document: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Lỗi preview: {str(e)}")


@app.post("/suggest-questions", response_model=SuggestQuestionsResponse)
async def suggest_questions_endpoint(input: SuggestQuestionsInput):
    """
    Gợi ý câu hỏi dựa trên context

    Endpoint mới để hướng dẫn user hỏi AI những câu hỏi phù hợp.
    """
    try:
        context = input.context
        cv_id = input.cv_id
        job_id = input.job_id

        # Lấy CV info nếu có
        cv_info = None
        if cv_id:
            with get_db_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("SELECT cv_info_json FROM cv_store WHERE id = ?", (cv_id,))
                row = cursor.fetchone()
                if row:
                    cv_info = json.loads(row["cv_info_json"])

        # Lấy job info nếu có
        job_info = None
        if job_id:
            with get_db_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("SELECT job_title FROM job_store WHERE id = ?", (job_id,))
                row = cursor.fetchone()
                if row:
                    job_info = {"job_title": row["job_title"]}

        # Tạo gợi ý câu hỏi
        suggestions = generate_question_suggestions(context, cv_info, job_info)

        question_items = [
            QuestionSuggestion(**q) for q in suggestions
        ]

        logging.info(f"✅ Tạo {len(question_items)} gợi ý câu hỏi cho context '{context}'")
        return SuggestQuestionsResponse(suggestions=question_items)

    except Exception as e:
        logging.error(f"❌ Lỗi tạo gợi ý câu hỏi: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Lỗi tạo gợi ý: {str(e)}")


# ===== FRONTEND ENDPOINTS =====

@app.get("/cvs")
async def get_all_cvs_simple():
    return [{"id": row["id"], "filename": row["filename"], "cv_info": row["cv_info_json"],
             "upload_timestamp": row["upload_timestamp"]} for row in get_all_cvs()]

@app.get("/jobs")
async def get_all_jobs_simple(limit: int = Query(100, ge=1, le=1000), offset: int = Query(0, ge=0)):
    """
    Lấy tất cả jobs (cho frontend dashboard và jobs listing)

    Khác với /jobs/search (cần cv_id và AI ranking),
    endpoint này chỉ đơn giản list jobs với pagination

    Parameters:
    - limit: Số lượng jobs tối đa (default: 100)
    - offset: Vị trí bắt đầu (default: 0)

    Returns:
    - jobs: List of job objects
    - total: Tổng số jobs trong database
    - limit: Limit được sử dụng
    - offset: Offset được sử dụng
    """
    try:
        with get_db_connection() as conn:
            cursor = conn.cursor()

            # Get total count
            cursor.execute("SELECT COUNT(*) as total FROM job_store")
            total = cursor.fetchone()["total"]

            # Get jobs with pagination
            cursor.execute(f"SELECT * FROM job_store LIMIT ? OFFSET ?", (limit, offset))
            jobs = [dict(row) for row in cursor.fetchall()]

            logging.info(f"✅ Lấy {len(jobs)} jobs (total: {total}, limit: {limit}, offset: {offset})")

            return {
                "jobs": jobs,
                "total": total,
                "limit": limit,
                "offset": offset
            }

    except Exception as e:
        logging.error(f"❌ Lỗi lấy jobs: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Lỗi lấy jobs: {str(e)}")


@app.get("/jobs/analytics")
async def get_jobs_analytics():
    """
    Phân tích xu hướng việc làm - Dashboard Analytics

    Returns:
    - top_job_titles: Top 10 vị trí tuyển dụng nhiều nhất
    - top_companies: Top 10 công ty tuyển dụng nhiều nhất
    - salary_distribution: Phân bố mức lương
    - location_distribution: Phân bố địa điểm làm việc
    - job_type_distribution: Phân bố loại hình công việc
    - experience_distribution: Phân bố yêu cầu kinh nghiệm
    - top_skills: Top 20 kỹ năng được yêu cầu nhiều nhất
    - deadline_stats: Thống kê deadline (sắp hết hạn, còn lâu)
    """
    try:
        with get_db_connection() as conn:
            cursor = conn.cursor()

            # 1. Top 10 Job Titles
            cursor.execute("""
                SELECT job_title, COUNT(*) as count
                FROM job_store
                WHERE job_title IS NOT NULL AND job_title != ''
                GROUP BY job_title
                ORDER BY count DESC
                LIMIT 10
            """)
            top_job_titles = [{"title": row["job_title"], "count": row["count"]} for row in cursor.fetchall()]

            # 2. Top 10 Companies
            cursor.execute("""
                SELECT name, COUNT(*) as count
                FROM job_store
                WHERE name IS NOT NULL AND name != ''
                GROUP BY name
                ORDER BY count DESC
                LIMIT 10
            """)
            top_companies = [{"company": row["name"], "count": row["count"]} for row in cursor.fetchall()]

            # 3. Salary Distribution (phân loại theo range)
            cursor.execute("""
                SELECT salary, COUNT(*) as count
                FROM job_store
                WHERE salary IS NOT NULL AND salary != '' AND salary != 'Thỏa thuận'
                GROUP BY salary
                ORDER BY count DESC
                LIMIT 15
            """)
            salary_distribution = [{"salary": row["salary"], "count": row["count"]} for row in cursor.fetchall()]

            # 4. Location Distribution
            cursor.execute("""
                SELECT work_location, COUNT(*) as count
                FROM job_store
                WHERE work_location IS NOT NULL AND work_location != ''
                GROUP BY work_location
                ORDER BY count DESC
                LIMIT 10
            """)
            location_distribution = [{"location": row["work_location"], "count": row["count"]} for row in cursor.fetchall()]

            # 5. Job Type Distribution (work_type column)
            cursor.execute("""
                SELECT work_type, COUNT(*) as count
                FROM job_store
                WHERE work_type IS NOT NULL AND work_type != ''
                GROUP BY work_type
                ORDER BY count DESC
            """)
            job_type_distribution = [{"type": row["work_type"], "count": row["count"]} for row in cursor.fetchall()]

            # 6. Experience Distribution
            cursor.execute("""
                SELECT experience, COUNT(*) as count
                FROM job_store
                WHERE experience IS NOT NULL AND experience != ''
                GROUP BY experience
                ORDER BY count DESC
            """)
            experience_distribution = [{"experience": row["experience"], "count": row["count"]} for row in cursor.fetchall()]

            # 7. Top Skills (parse từ skills JSON array)
            cursor.execute("SELECT skills FROM job_store WHERE skills IS NOT NULL AND skills != ''")
            skills_counter = {}
            for row in cursor.fetchall():
                try:
                    skills_list = json.loads(row["skills"]) if isinstance(row["skills"], str) else row["skills"]
                    if isinstance(skills_list, list):
                        for skill in skills_list:
                            if skill and isinstance(skill, str):
                                skill = skill.strip()
                                skills_counter[skill] = skills_counter.get(skill, 0) + 1
                except:
                    pass

            top_skills = [{"skill": skill, "count": count} for skill, count in sorted(skills_counter.items(), key=lambda x: x[1], reverse=True)[:20]]

            # 8. Deadline Stats (sắp hết hạn trong 7 ngày, 30 ngày)
            from datetime import datetime, timedelta
            today = datetime.now().date()
            deadline_7_days = (today + timedelta(days=7)).isoformat()
            deadline_30_days = (today + timedelta(days=30)).isoformat()

            cursor.execute("""
                SELECT
                    COUNT(CASE WHEN deadline <= ? THEN 1 END) as expiring_7_days,
                    COUNT(CASE WHEN deadline <= ? THEN 1 END) as expiring_30_days,
                    COUNT(*) as total
                FROM job_store
                WHERE deadline IS NOT NULL AND deadline != ''
            """, (deadline_7_days, deadline_30_days))
            deadline_row = cursor.fetchone()
            deadline_stats = {
                "expiring_7_days": deadline_row["expiring_7_days"] or 0,
                "expiring_30_days": deadline_row["expiring_30_days"] or 0,
                "total_with_deadline": deadline_row["total"] or 0
            }

            # 9. Total Stats
            cursor.execute("SELECT COUNT(*) as total FROM job_store")
            total_jobs = cursor.fetchone()["total"]

            logging.info(f"✅ Phân tích {total_jobs} jobs thành công")

            return {
                "total_jobs": total_jobs,
                "top_job_titles": top_job_titles,
                "top_companies": top_companies,
                "salary_distribution": salary_distribution,
                "location_distribution": location_distribution,
                "job_type_distribution": job_type_distribution,
                "experience_distribution": experience_distribution,
                "top_skills": top_skills,
                "deadline_stats": deadline_stats
            }

    except Exception as e:
        logging.error(f"❌ Lỗi phân tích jobs: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Lỗi phân tích: {str(e)}")

@app.post("/jobs/analytics/insights")
async def generate_chart_insights(request: Dict[str, Any]):
    """
    Generate AI insights for dashboard charts using LLM

    Request body:
    {
        "chart_type": "top_jobs" | "top_companies" | "location" | "job_type" | "experience" | "salary",
        "data": [...chart data...]
    }

    Returns:
    {
        "analysis": "AI-generated analysis text in Vietnamese"
    }
    """
    try:
        chart_type = request.get("chart_type")
        data = request.get("data", [])

        if not chart_type or not data:
            return {"analysis": "Thiếu thông tin biểu đồ để phân tích."}

        # Import LLM from ai_analysis
        from .ai_analysis import llm

        # Create prompt based on chart type
        if chart_type == "top_jobs":
            data_str = "\n".join([f"- {item.get('title', 'N/A')}: {item.get('count', 0)} việc làm" for item in data[:10]])
            prompt = f"""Bạn là chuyên gia phân tích thị trường việc làm. Phân tích biểu đồ "Top 10 Vị Trí Tuyển Dụng Nhiều Nhất" dựa trên dữ liệu sau:

{data_str}

Cung cấp phân tích ngắn gọn dưới dạng văn bản liên tục (3-4 câu), không sử dụng tiêu đề chào hỏi, không đánh số hoặc bullet points, chỉ tập trung vào nội dung chính:
- Xu hướng tuyển dụng chính
- Ngành nghề hot nhất
- Cơ hội cho ứng viên
- Lời khuyên

Trả lời bằng tiếng Việt, chuyên nghiệp, súc tích."""

        elif chart_type == "top_companies":
            data_str = "\n".join([f"- {item.get('company', 'N/A')}: {item.get('count', 0)} việc làm" for item in data[:10]])
            prompt = f"""Bạn là chuyên gia phân tích thị trường việc làm. Phân tích biểu đồ "Top 10 Công Ty Tuyển Dụng Nhiều Nhất" dựa trên dữ liệu sau:

{data_str}

Cung cấp phân tích ngắn gọn dưới dạng văn bản liên tục (3-4 câu), không sử dụng tiêu đề chào hỏi, không đánh số hoặc bullet points, chỉ tập trung vào nội dung chính:
- Công ty đang mở rộng
- Lĩnh vực kinh doanh
- Cơ hội phát triển
- Lời khuyên cho ứng viên

Trả lời bằng tiếng Việt, chuyên nghiệp, súc tích."""

        elif chart_type == "location":
            data_str = "\n".join([f"- {item.get('location', 'N/A')}: {item.get('count', 0)} việc làm" for item in data[:10]])
            prompt = f"""Bạn là chuyên gia phân tích thị trường việc làm. Phân tích biểu đồ "Phân Bố Địa Điểm Làm Việc" dựa trên dữ liệu sau:

{data_str}

Cung cấp phân tích ngắn gọn dưới dạng văn bản liên tục (3-4 câu), không sử dụng tiêu đề chào hỏi, không đánh số hoặc bullet points, chỉ tập trung vào nội dung chính:
- Thành phố có nhiều cơ hội nhất
- Xu hướng phân bố địa lý
- So sánh các thành phố
- Lời khuyên theo địa điểm

Trả lời bằng tiếng Việt, chuyên nghiệp, súc tích."""

        elif chart_type == "job_type":
            data_str = "\n".join([f"- {item.get('type', 'N/A')}: {item.get('count', 0)} việc làm" for item in data])
            prompt = f"""Bạn là chuyên gia phân tích thị trường việc làm. Phân tích biểu đồ "Phân Bố Loại Hình Công Việc" dựa trên dữ liệu sau:

{data_str}

Cung cấp phân tích ngắn gọn dưới dạng văn bản liên tục (3-4 câu), không sử dụng tiêu đề chào hỏi, không đánh số hoặc bullet points, chỉ tập trung vào nội dung chính:
- Loại hình phổ biến nhất
- Xu hướng remote/hybrid/onsite
- Cơ hội freelance/part-time
- Lời khuyên theo loại hình

Trả lời bằng tiếng Việt, chuyên nghiệp, súc tích."""

        elif chart_type == "experience":
            data_str = "\n".join([f"- {item.get('experience', 'N/A')}: {item.get('count', 0)} việc làm" for item in data[:10]])
            prompt = f"""Bạn là chuyên gia phân tích thị trường việc làm. Phân tích biểu đồ "Phân Bố Yêu Cầu Kinh Nghiệm" dựa trên dữ liệu sau:

{data_str}

Cung cấp phân tích ngắn gọn dưới dạng văn bản liên tục (3-4 câu), không sử dụng tiêu đề chào hỏi, không đánh số hoặc bullet points, chỉ tập trung vào nội dung chính:
- Mức kinh nghiệm được yêu cầu nhiều
- Cơ hội cho fresher vs experienced
- Xu hướng tuyển dụng
- Lời khuyên cho từng nhóm

Trả lời bằng tiếng Việt, chuyên nghiệp, súc tích."""

        elif chart_type == "salary":
            data_str = "\n".join([f"- {item.get('salary', 'N/A')}: {item.get('count', 0)} việc làm" for item in data[:10]])
            prompt = f"""Bạn là chuyên gia phân tích thị trường việc làm. Phân tích biểu đồ "Phân Bố Mức Lương" dựa trên dữ liệu sau:

{data_str}

Cung cấp phân tích ngắn gọn dưới dạng văn bản liên tục (3-4 câu), không sử dụng tiêu đề chào hỏi, không đánh số hoặc bullet points, chỉ tập trung vào nội dung chính:
- Mức lương phổ biến
- Xu hướng lương theo ngành
- So sánh thị trường
- Lời khuyên thương lượng lương

Trả lời bằng tiếng Việt, chuyên nghiệp, súc tích."""

        else:
            return {"analysis": "Loại biểu đồ không hợp lệ."}

        # Call LLM with API key rotation
        logging.info(f"Generating analysis for chart type: {chart_type}")
        from .providers import get_llm
        llm_instance = get_llm()
        response = await llm_instance.ainvoke(prompt)
        analysis = response.content.strip()

        logging.info(f"Generated analysis: {analysis[:100]}...")
        return {"analysis": analysis}

    except Exception as e:
        logging.error(f"Lỗi generate chart insights: {e}")
        return {"analysis": "Không thể tạo phân tích. Vui lòng thử lại sau."}

@app.get("/workspace")
async def workspace(request: Request):
    return await open_workspace(request)

@app.get("/health")
async def health():
    with get_db_connection() as conn:
        conn.execute("SELECT 1")
    return {"status": "ok", "product": "YangCareer", "ai_configured": ai_configured()}

class ManualCV(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    skills: List[str] = Field(min_length=1, max_length=40)
    career_objective: str = Field(default="", max_length=1000)

@app.post("/cvs/manual", status_code=201)
async def create_manual_cv(input: ManualCV):
    skills = list(dict.fromkeys(value.strip()[:80] for value in input.skills if value.strip()))
    if not skills or not input.name.strip():
        raise HTTPException(422, "Hãy nhập tên và ít nhất một kỹ năng.")
    info = {"name": input.name.strip(), "skills": skills, "career_objective": input.career_objective.strip(),
            "email": "", "phone": "", "education": [], "experience": []}
    cv_id = insert_cv_record("manual-profile", info)
    return {"cv_id": cv_id, "cv_info": info}

app.mount("/app", StaticFiles(directory=str(ROOT / "frontend"), html=True), name="frontend")
