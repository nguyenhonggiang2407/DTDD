from pydantic import BaseModel, Field, field_validator, ConfigDict
from enum import Enum
from typing import List, Dict, Any, Optional
from datetime import datetime
import re

class ModelName(str, Enum):
    gemini_2_5_flash = "gemini-2.5-flash"
    gemini_2_0_flash_exp = "gemini-2.0-flash-exp"

class Education(BaseModel):
    school: str = Field(..., min_length=1, description="Tên trường học")
    degree: str = Field(..., min_length=1, description="Bằng cấp")
    major: str = Field(..., min_length=1, description="Chuyên ngành")
    start_date: str = Field(..., description="Ngày bắt đầu (YYYY-MM-DD)")
    end_date: str = Field(..., description="Ngày kết thúc (YYYY-MM-DD hoặc 'Present')")

    @field_validator("start_date")
    @classmethod
    def validate_start_date(cls, value):
        """Kiểm tra định dạng ngày YYYY-MM-DD."""
        if not re.match(r"\d{4}-\d{2}-\d{2}", value):
            raise ValueError("Date must be in YYYY-MM-DD format")
        try:
            datetime.strptime(value, "%Y-%m-%d")
        except ValueError:
            raise ValueError("Invalid date")
        return value

    @field_validator("end_date")
    @classmethod
    def validate_end_date(cls, value):
        """Kiểm tra định dạng ngày YYYY-MM-DD hoặc 'Present'."""
        if value != "Present" and not re.match(r"\d{4}-\d{2}-\d{2}", value):
            raise ValueError("Date must be in YYYY-MM-DD format or 'Present'")
        if value != "Present":
            try:
                datetime.strptime(value, "%Y-%m-%d")
            except ValueError:
                raise ValueError("Invalid date")
        return value
    
class Experience(BaseModel):
    company: str = Field(..., min_length=1, description="Tên công ty")
    title: str = Field(..., min_length=1, description="Chức danh")
    start_date: str = Field(..., description="Ngày bắt đầu (YYYY-MM-DD)")
    end_date: str = Field(..., description="Ngày kết thúc (YYYY-MM-DD hoặc 'Present')")
    description: str = Field(..., min_length=1, description="Mô tả công việc")

    @field_validator("company", mode="before")
    @classmethod
    def ensure_non_empty_company(cls, value):
        """Đảm bảo trường company không rỗng, gán 'Unknown' nếu rỗng hoặc None."""
        return value or "Unknown"

    @field_validator("start_date", "end_date")
    @classmethod
    def validate_date_format(cls, value):
        """Kiểm tra định dạng ngày YYYY-MM-DD hoặc 'Present'."""
        if value != "Present" and not re.match(r"\d{4}-\d{2}-\d{2}", value):
            raise ValueError("Date must be in YYYY-MM-DD format or 'Present'")
        if value != "Present":
            try:
                datetime.strptime(value, "%Y-%m-%d")
            except ValueError:
                raise ValueError("Invalid date")
        return value

class MatchedJob(BaseModel):
    job_id: int = Field(..., ge=1, description="ID công việc từ job_store")
    job_title: str = Field(..., min_length=1, description="Tiêu đề công việc")
    job_url: str = Field(..., description="URL công việc")
    match_score: float = Field(..., ge=0.0, le=1.0, description="Điểm khớp (0-1)")
    matched_skills: List[str] = Field(default_factory=list, description="Kỹ năng khớp với CV")
    matched_aspirations: List[str] = Field(default_factory=list, description="Mục tiêu nghề nghiệp khớp")
    matched_experience: List[str] = Field(default_factory=list, description="Kinh nghiệm khớp")
    matched_education: List[str] = Field(default_factory=list, description="Học vấn khớp")
    work_location: str = Field(..., min_length=1, description="Địa điểm làm việc")
    salary: str = Field(..., description="Mức lương")
    deadline: str = Field(..., description="Hạn nộp hồ sơ")
    benefits: str = Field(..., description="Phúc lợi")
    job_type: str = Field(..., min_length=1, description="Loại hình công việc")
    experience_required: str = Field(..., description="Kinh nghiệm yêu cầu")
    education_required: str = Field(..., description="Học vấn yêu cầu")
    company_name: str = Field(..., min_length=1, description="Tên công ty")
    skills: List[str] = Field(default_factory=list, description="Danh sách kỹ năng yêu cầu")
    why_match: Optional[str] = Field(None, description="Lý do tại sao phù hợp (AI-generated)")
    job_description: Optional[str] = Field(None, description="Mô tả công việc")

    @field_validator("deadline")
    @classmethod
    def validate_deadline(cls, value):
        """Kiểm tra định dạng ngày YYYY-MM-DD cho deadline."""
        if not re.match(r"\d{4}-\d{2}-\d{2}", value):
            raise ValueError("Deadline must be in YYYY-MM-DD format")
        try:
            datetime.strptime(value, "%Y-%m-%d")
        except ValueError:
            raise ValueError("Invalid deadline date")
        return value

class Suggestion(BaseModel):
    skill_or_experience: str = Field(..., min_length=1, description="Kỹ năng hoặc kinh nghiệm cần cải thiện")
    suggestion: str = Field(..., min_length=1, description="Gợi ý cải thiện")

class DocumentInfo(BaseModel):
    id: int = Field(..., ge=1, description="ID của CV trong cv_store")
    filename: str = Field(..., min_length=1, description="Tên file CV")
    cv_info_json: str = Field(..., min_length=1, description="JSON thông tin CV")
    upload_timestamp: Optional[str] = Field(None, description="Thời gian tạo (timestamp)")

class DocumentListResponse(BaseModel):
    documents: List[DocumentInfo] = Field(..., description="Danh sách thông tin CV")

class DeleteFileRequest(BaseModel):
    file_id: int = Field(..., ge=1, description="ID của CV cần xóa")

class MatchInput(BaseModel):
    model_config = ConfigDict(
        schema_extra={
            "example": {
                "session_id": "test-session-123",
                "model": "gemini-2.5-flash",
                "cv_input": "Skills: Python, SQL\nAspirations: Senior Data Scientist\nExperience: Company: Tech Corp; Title: Data Analyst; Start_date: 2020-01-01; End_date: Present; Description: Analyzed data\nEducation: School: Hanoi University; Degree: Bachelor; Major: Computer Science; Start_date: 2016-09-01; End_date: 2020-06-30\nName: John Doe\nEmail: john.doe@example.com\nPhone: +84-123-456-789",
                "cv_id": 1,
                "filters": {
                    "job_type": ["Full-time"],
                    "work_location": ["Hà Nội"],
                    "skills": ["Python", "SQL"]
                }
            }
        }
    )

    session_id: Optional[str] = Field(None, description="ID phiên khớp, nếu có")
    model: ModelName = Field(ModelName.gemini_2_5_flash, description="Mô hình AI sử dụng")
    cv_input: Optional[str] = Field(None, description="Input CV dạng chuỗi")
    cv_id: Optional[int] = Field(None, ge=1, description="ID của CV trong cv_store")
    filters: Dict[str, Any] = Field(
        default_factory=dict,
        description="Bộ lọc công việc. Allowed keys: 'job_type', 'work_location', 'experience', 'education', 'skills', 'deadline_after'. Example: {'job_type': ['Full-time'], 'work_location': ['Hà Nội'], 'skills': ['Python']}"
    )

    @field_validator("filters")
    @classmethod
    def validate_filters(cls, value):
        """Kiểm tra các khóa và giá trị hợp lệ trong filters."""
        valid_keys = {"job_type", "work_location", "experience", "education", "skills", "deadline_after"}
        for key in value:
            if key not in valid_keys:
                raise ValueError(
                    f"Invalid filter key: '{key}'. Allowed keys are: {', '.join(valid_keys)}."
                )
            if key in {"job_type", "skills"} and not isinstance(value[key], list):
                raise ValueError(f"Filter '{key}' must be a list")
            if key == "deadline_after" and not re.match(r"\d{4}-\d{2}-\d{2}", value[key]):
                raise ValueError("deadline_after must be in YYYY-MM-DD format")
        return value

    @field_validator("cv_input", "cv_id", mode="before")
    @classmethod
    def ensure_cv_input_or_id(cls, value, info):
        """Đảm bảo ít nhất một trong cv_input hoặc cv_id được cung cấp."""
        values = info.data
        if info.field_name == "cv_id" and value is None and values.get("cv_input") is None:
            raise ValueError("At least one of cv_input or cv_id must be provided")
        return value

class MatchResponse(BaseModel):
    name: Optional[str] = Field(None, description="Tên ứng viên")
    email: Optional[str] = Field(None, description="Email ứng viên")
    phone: Optional[str] = Field(None, description="Số điện thoại ứng viên")
    cv_skills: List[str] = Field(default_factory=list, description="Kỹ năng từ CV")
    career_objective: Optional[str] = Field(None, description="Mục tiêu nghề nghiệp")
    education: List[Education] = Field(default_factory=list, description="Học vấn")
    experience: List[Experience] = Field(default_factory=list, description="Kinh nghiệm")
    matched_jobs: List[MatchedJob] = Field(default_factory=list, description="Danh sách công việc khớp")
    suggestions: List[Suggestion] = Field(default_factory=list, description="Gợi ý cải thiện")
    session_id: Optional[str] = Field(None, description="ID phiên khớp")
    model: ModelName = Field(..., description="Mô hình AI sử dụng")

class JobDetails(BaseModel):
    job_id: int = Field(..., ge=1, description="ID công việc", alias="id")
    name: str = Field(..., min_length=1, description="Tên công việc")
    job_title: str = Field(..., min_length=1, description="Tiêu đề công việc")
    job_url: str = Field(..., description="URL công việc")
    job_description: str = Field(..., description="Mô tả công việc")
    candidate_requirements: str = Field(..., description="Yêu cầu ứng viên")
    benefits: str = Field(..., description="Phúc lợi")
    work_location: str = Field(..., min_length=1, description="Địa điểm làm việc")
    work_time: str = Field(..., description="Thời gian làm việc")
    job_tags: str = Field(..., description="Tag công việc")
    skills: List[str] = Field(default_factory=list, description="Kỹ năng yêu cầu")
    related_categories: str = Field(..., description="Danh mục liên quan")
    salary: str = Field(..., description="Mức lương")
    experience: str = Field(..., description="Kinh nghiệm yêu cầu")
    deadline: str = Field(..., description="Hạn nộp hồ sơ")
    company_logo: str = Field(..., description="Logo công ty")
    company_scale: str = Field(..., description="Quy mô công ty")
    company_field: str = Field(..., description="Lĩnh vực công ty")
    company_address: str = Field(..., description="Địa chỉ công ty")
    level: str = Field(..., description="Cấp bậc")
    education: str = Field(..., description="Học vấn yêu cầu")
    number_of_hires: int = Field(..., ge=0, description="Số lượng tuyển")
    work_type: str = Field(..., min_length=1, description="Loại hình công việc")
    company_url: str = Field(..., description="URL công ty")
    timestamp: str = Field(..., description="Thời gian tạo")

    @field_validator("skills", mode="before")
    @classmethod
    def parse_skills(cls, value):
        """Chuyển đổi chuỗi skills thành danh sách."""
        if isinstance(value, str):
            return [skill.strip() for skill in value.split(";") if skill.strip()]
        return value

    @field_validator("deadline")
    @classmethod
    def validate_deadline(cls, value):
        """Kiểm tra định dạng ngày YYYY-MM-DD cho deadline."""
        if not re.match(r"\d{4}-\d{2}-\d{2}", value):
            raise ValueError("Deadline must be in YYYY-MM-DD format")
        try:
            datetime.strptime(value, "%Y-%m-%d")
        except ValueError:
            raise ValueError("Invalid deadline date")
        return value

# ===== NEW MODELS FOR NEW ENDPOINTS =====

class CVInsightsResponse(BaseModel):
    """Response cho endpoint /cv/{cv_id}/insights"""
    cv_id: int = Field(..., description="ID của CV")
    quality_score: float = Field(..., ge=0.0, le=10.0, description="Điểm chất lượng CV (0-10)")
    completeness: Dict[str, Any] = Field(..., description="Độ đầy đủ của CV")
    market_fit: Dict[str, Any] = Field(..., description="Mức độ phù hợp với thị trường")
    strengths: List[str] = Field(default_factory=list, description="Điểm mạnh")
    weaknesses: List[str] = Field(default_factory=list, description="Điểm yếu")
    last_analyzed: Optional[str] = Field(None, description="Thời gian phân tích gần nhất")

class ImprovementSuggestion(BaseModel):
    """Gợi ý cải thiện cho từng section"""
    section: str = Field(..., description="Section cần cải thiện (skills, experience, education...)")
    current: Optional[List[str]] = Field(None, description="Giá trị hiện tại")
    suggested_add: Optional[List[str]] = Field(None, description="Gợi ý thêm vào")
    suggestion: str = Field(..., description="Mô tả gợi ý")
    reason: str = Field(..., description="Lý do")
    priority: str = Field(..., description="Mức độ ưu tiên (high, medium, low)")
    impact: str = Field(..., description="Tác động dự kiến")

class CVImproveResponse(BaseModel):
    """Response cho endpoint /cv/improve"""
    cv_id: int = Field(..., description="ID của CV")
    improvements: List[ImprovementSuggestion] = Field(..., description="Danh sách gợi ý cải thiện")

class JobSearchInput(BaseModel):
    """Input cho endpoint /jobs/search"""
    query: Optional[str] = Field(None, description="Từ khóa tìm kiếm")
    filters: Dict[str, Any] = Field(default_factory=dict, description="Bộ lọc")
    cv_id: Optional[int] = Field(None, description="ID CV để AI ranking")
    limit: int = Field(20, ge=1, le=100, description="Số lượng kết quả")
    offset: int = Field(0, ge=0, description="Offset cho pagination")

class JobSearchResult(BaseModel):
    """Kết quả tìm kiếm job"""
    job_id: int = Field(..., description="ID job")
    job_title: str = Field(..., description="Tiêu đề job")
    company_name: str = Field(..., description="Tên công ty")
    match_score: Optional[float] = Field(None, description="Điểm match (nếu có cv_id)")
    salary: str = Field(..., description="Mức lương")
    work_location: str = Field(..., description="Địa điểm")
    work_type: str = Field(..., description="Loại hình")
    deadline: str = Field(..., description="Hạn nộp")
    why_match: Optional[str] = Field(None, description="Lý do phù hợp")

    class Config:
        populate_by_name = True  # Allow both field name and alias

class JobSearchResponse(BaseModel):
    """Response cho endpoint /jobs/search"""
    total: int = Field(..., description="Tổng số jobs tìm được")
    jobs: List[JobSearchResult] = Field(..., description="Danh sách jobs")
    limit: int = Field(..., description="Limit")
    offset: int = Field(..., description="Offset")

class ApplyJobInput(BaseModel):
    """Input cho endpoint /apply"""
    cv_id: int = Field(..., ge=1, description="ID của CV")
    job_id: int = Field(..., ge=1, description="ID của job")
    cover_letter: str = Field("", description="Thư xin việc")
    status: str = Field("applied", pattern="^(applied|pending|accepted|rejected)$", description="Trạng thái hồ sơ")

class ApplicationResponse(BaseModel):
    """Response cho endpoint /apply"""
    application_id: int = Field(..., description="ID của application")
    cv_id: int = Field(..., description="ID CV")
    job_id: int = Field(..., description="ID job")
    status: str = Field(..., description="Trạng thái")
    applied_at: str = Field(..., description="Thời gian apply")
    message: str = Field(..., description="Thông báo")

class ApplicationItem(BaseModel):
    """Item trong danh sách applications"""
    id: int = Field(..., description="ID application")
    cv_id: int = Field(..., description="ID CV")
    job_id: int = Field(..., description="ID job")
    job_title: str = Field(..., description="Tiêu đề job")
    company_url: str = Field(..., description="URL công ty")
    salary: str = Field(..., description="Mức lương")
    work_location: str = Field(..., description="Địa điểm")
    status: str = Field(..., description="Trạng thái")
    applied_at: str = Field(..., description="Thời gian apply")

class ApplicationsResponse(BaseModel):
    """Response cho endpoint /applications/{cv_id}"""
    cv_id: int = Field(..., description="ID CV")
    total: int = Field(..., description="Tổng số applications")
    applications: List[ApplicationItem] = Field(..., description="Danh sách applications")

class DocumentPreviewResponse(BaseModel):
    """Response cho endpoint /preview-doc/{file_id}"""
    file_id: int = Field(..., description="ID file")
    type: str = Field(..., description="Loại file (cv, cover_letter...)")
    filename: str = Field(..., description="Tên file")
    preview: Dict[str, Any] = Field(..., description="Thông tin preview")
    quick_info: Dict[str, Any] = Field(..., description="Thông tin nhanh")

class SuggestQuestionsInput(BaseModel):
    """Input cho endpoint /suggest-questions"""
    context: str = Field(..., description="Context (cv_uploaded, viewing_job, chatting...)")
    cv_id: Optional[int] = Field(None, description="ID CV")
    job_id: Optional[int] = Field(None, description="ID job")

class QuestionSuggestion(BaseModel):
    """Gợi ý câu hỏi"""
    question: str = Field(..., description="Câu hỏi")
    category: str = Field(..., description="Danh mục (cv_analysis, improvement, salary...)")
    icon: str = Field(..., description="Icon emoji")

class SuggestQuestionsResponse(BaseModel):
    """Response cho endpoint /suggest-questions"""
    suggestions: List[QuestionSuggestion] = Field(..., description="Danh sách câu hỏi gợi ý")