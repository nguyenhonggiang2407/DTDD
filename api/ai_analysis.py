"""
AI Analysis Functions - Phân tích CV và Jobs bằng Gemini AI
"""
import logging
import json
from typing import Dict, List, Optional, Any
from langchain_google_genai import ChatGoogleGenerativeAI
import os
from .api_key_manager import get_next_api_key

# Initialize Gemini model with API key rotation
def get_llm():
    """
    Get LLM instance with rotated API key
    Uses gemini-2.5-flash (stable, higher quota than 2.0-flash-exp)
    """
    return ChatGoogleGenerativeAI(
        model=os.environ.get("GOOGLE_MODEL", "gemini-2.5-flash"),
        google_api_key=get_next_api_key(),
        temperature=0.3
    )

# Legacy global instance (for backward compatibility)
llm = None

async def analyze_cv_insights(cv_info: Dict) -> Dict[str, Any]:
    """
    Phân tích CV chuyên sâu - Đánh giá chất lượng, điểm mạnh/yếu
    
    Args:
        cv_info: Thông tin CV đã parse
        
    Returns:
        Dict chứa quality_score, strengths, weaknesses, completeness, market_fit
    """
    prompt = f"""
Bạn là chuyên gia phân tích CV. Hãy phân tích CV sau và đưa ra đánh giá chi tiết:

**THÔNG TIN CV:**
- Tên: {cv_info.get('name', 'N/A')}
- Email: {cv_info.get('email', 'N/A')}
- Điện thoại: {cv_info.get('phone', 'N/A')}
- Kỹ năng: {', '.join(cv_info.get('skills', []))}
- Mục tiêu nghề nghiệp: {cv_info.get('career_objective', 'N/A')}
- Kinh nghiệm: {len(cv_info.get('experience', []))} công việc
- Học vấn: {len(cv_info.get('education', []))} bằng cấp

**YÊU CẦU PHÂN TÍCH:**

1. **Quality Score (0-10):** Đánh giá tổng thể chất lượng CV
2. **Completeness Score (0-1):** Độ đầy đủ của CV
   - Tính theo công thức: (Số phần có / Tổng số phần cần thiết)
   - Các phần cần thiết: Name, Email, Phone, Skills, Career Objective, Experience, Education
   - Các phần bổ sung: Portfolio, Certifications, Projects, Awards
   - **LƯU Ý:** Điểm này KHÔNG BAO GIỜ ÂM, tối thiểu là 0.0
   - Ví dụ: Có 7/10 phần → completeness_score = 0.7
3. **Điểm mạnh:** 3-5 điểm dựa trên CV hiện tại
4. **Điểm yếu:** 3-5 điểm dựa trên những gì CV thiếu hoặc cần cải thiện
5. **Market Fit Score (0-1):** Mức độ phù hợp với thị trường việc làm

**ĐỊNH DẠNG JSON RESPONSE:**
{{
  "quality_score": 7.5,
  "completeness_score": 0.7,
  "has_portfolio": false,
  "has_certifications": false,
  "has_projects": false,
  "missing_sections": ["Portfolio", "Certifications", "Projects"],
  "market_fit_score": 0.65,
  "experience_level": "Junior",
  "salary_range": "8-12 triệu",
  "competitive_score": 6.8,
  "strengths": [
    "Có kỹ năng Adobe Photoshop tốt",
    "Có kinh nghiệm thực tế với Social Media",
    "Mục tiêu nghề nghiệp rõ ràng"
  ],
  "weaknesses": [
    "Thiếu kỹ năng Illustrator (cần cho 80% jobs Marketing Designer)",
    "Chưa có portfolio online để showcase sản phẩm",
    "Kinh nghiệm chưa đủ 2 năm cho vị trí Senior"
  ]
}}

**LƯU Ý QUAN TRỌNG:**
- completeness_score PHẢI từ 0.0 đến 1.0, KHÔNG BAO GIỜ ÂM
- strengths và weaknesses phải cụ thể, dựa trên CV hiện tại
- missing_sections chỉ liệt kê những phần thực sự thiếu

Chỉ trả về JSON, không giải thích thêm.
"""

    try:
        # Use fresh LLM instance with rotated API key
        llm_instance = get_llm()
        response = await llm_instance.ainvoke(prompt)
        content = response.content.strip()
        
        # Remove markdown code blocks if present
        if content.startswith("```json"):
            content = content[7:]
        if content.startswith("```"):
            content = content[3:]
        if content.endswith("```"):
            content = content[:-3]
        content = content.strip()
        
        result = json.loads(content)

        # Validate và fix completeness_score (KHÔNG BAO GIỜ ÂM)
        if 'completeness_score' in result:
            result['completeness_score'] = max(0.0, min(1.0, float(result['completeness_score'])))

        # Validate quality_score
        if 'quality_score' in result:
            result['quality_score'] = max(0.0, min(10.0, float(result['quality_score'])))

        # Validate market_fit_score
        if 'market_fit_score' in result:
            result['market_fit_score'] = max(0.0, min(1.0, float(result['market_fit_score'])))

        logging.info(f"✅ Phân tích CV thành công: quality_score={result.get('quality_score')}, completeness={result.get('completeness_score')}")
        return result
        
    except json.JSONDecodeError as e:
        logging.error(f"❌ Lỗi parse JSON từ Gemini: {e}")
        logging.error(f"Response content: {content}")
        # Return default values
        return {
            "quality_score": 5.0,
            "completeness_score": 0.5,
            "has_portfolio": False,
            "has_certifications": False,
            "has_projects": False,
            "missing_sections": ["Unknown"],
            "market_fit_score": 0.5,
            "experience_level": "Unknown",
            "salary_range": "N/A",
            "competitive_score": 5.0,
            "strengths": ["Cần phân tích thêm"],
            "weaknesses": ["Cần phân tích thêm"]
        }
    except Exception as e:
        logging.error(f"❌ Lỗi phân tích CV: {e}")
        raise


async def generate_cv_improvements(cv_info: Dict, insights: Dict) -> List[Dict[str, Any]]:
    """
    Tạo gợi ý cải thiện CV cụ thể
    
    Args:
        cv_info: Thông tin CV
        insights: Kết quả phân tích từ analyze_cv_insights
        
    Returns:
        List các gợi ý cải thiện
    """
    # Chuẩn bị thông tin chi tiết
    skills_str = ', '.join(cv_info.get('skills', [])) if cv_info.get('skills') else 'Chưa có'
    experience_count = len(cv_info.get('experience', []))
    education_count = len(cv_info.get('education', []))
    career_objective = cv_info.get('career_objective', 'Chưa có')

    prompt = f"""
Bạn là chuyên gia tư vấn CV. Dựa trên phân tích CV, hãy đưa ra gợi ý cải thiện CỤ THỂ DỰA TRÊN CV HIỆN TẠI:

**THÔNG TIN CV HIỆN TẠI:**
- Kỹ năng: {skills_str}
- Số lượng kinh nghiệm: {experience_count} công việc
- Số lượng học vấn: {education_count} bằng cấp
- Mục tiêu nghề nghiệp: {career_objective[:200]}...
- Điểm yếu đã phát hiện: {', '.join(insights.get('weaknesses', []))}
- Phần thiếu: {', '.join(insights.get('missing_sections', []))}
- Có Portfolio: {insights.get('has_portfolio', False)}
- Có Certifications: {insights.get('has_certifications', False)}
- Có Projects: {insights.get('has_projects', False)}

**YÊU CẦU GỢI Ý:**
Đưa ra 5 gợi ý cải thiện CỤ THỂ, DỰA TRÊN CV HIỆN TẠI:

1. **Nếu thiếu kỹ năng:** Gợi ý thêm kỹ năng cụ thể (dựa trên ngành nghề)
2. **Nếu thiếu dự án:** Gợi ý thêm mục Projects với ví dụ cụ thể
3. **Nếu mô tả kinh nghiệm chung chung:** Gợi ý thêm metrics, con số cụ thể
4. **Nếu mục tiêu nghề nghiệp mơ hồ:** Gợi ý làm rõ hơn
5. **Nếu thiếu portfolio/certifications:** Gợi ý thêm

**ĐỊNH DẠNG JSON:**
[
  {{
    "section": "skills",
    "current": ["Python", "JavaScript"],
    "suggested_add": ["React", "Node.js"],
    "suggestion": "Thêm kỹ năng React và Node.js để tăng cơ hội với vị trí Full-stack Developer",
    "reason": "70% jobs Full-stack yêu cầu React, Node.js đang rất hot trong thị trường",
    "priority": "high",
    "impact": "+40% match rate với jobs Full-stack"
  }},
  {{
    "section": "projects",
    "current": null,
    "suggested_add": null,
    "suggestion": "Thêm mục 'Dự án cá nhân' với 2-3 projects showcase kỹ năng Python, JavaScript",
    "reason": "Projects giúp nhà tuyển dụng thấy được năng lực thực tế, tăng độ tin cậy",
    "priority": "high",
    "impact": "Tăng 50% cơ hội được phỏng vấn"
  }},
  {{
    "section": "experience",
    "current": null,
    "suggested_add": null,
    "suggestion": "Thêm metrics vào mô tả công việc (VD: 'Tối ưu API giảm 30% response time', 'Xây dựng feature tăng 20% user engagement')",
    "reason": "Metrics cụ thể giúp CV nổi bật hơn, chứng minh impact thực tế",
    "priority": "medium",
    "impact": "Tăng độ tin cậy và chuyên nghiệp"
  }},
  {{
    "section": "career_objective",
    "current": null,
    "suggested_add": null,
    "suggestion": "Làm rõ mục tiêu nghề nghiệp: Thay vì 'Tìm cơ hội phát triển', hãy viết 'Mong muốn trở thành Full-stack Developer tại công ty công nghệ, đóng góp vào các dự án web app quy mô lớn'",
    "reason": "Mục tiêu cụ thể giúp nhà tuyển dụng hiểu rõ định hướng của bạn",
    "priority": "medium",
    "impact": "Tăng sự phù hợp với job description"
  }},
  {{
    "section": "certifications",
    "current": null,
    "suggested_add": null,
    "suggestion": "Thêm chứng chỉ liên quan (VD: AWS Certified Developer, Google Cloud Associate)",
    "reason": "Certifications chứng minh năng lực và sự đầu tư vào nghề nghiệp",
    "priority": "low",
    "impact": "+15% match rate với jobs yêu cầu cloud"
  }}
]

**LƯU Ý:**
- Gợi ý phải CỤ THỂ, DỰA TRÊN CV HIỆN TẠI
- Không gợi ý chung chung như "Cải thiện CV"
- Phải có lý do và impact rõ ràng

Chỉ trả về JSON array, không giải thích thêm.
"""

    try:
        # Use fresh LLM instance with rotated API key
        llm_instance = get_llm()
        response = await llm_instance.ainvoke(prompt)
        content = response.content.strip()

        # Remove markdown code blocks
        if content.startswith("```json"):
            content = content[7:]
        if content.startswith("```"):
            content = content[3:]
        if content.endswith("```"):
            content = content[:-3]
        content = content.strip()
        
        improvements = json.loads(content)

        # Validate và fix data types
        for imp in improvements:
            # Convert current to list if it's a string
            if isinstance(imp.get('current'), str):
                imp['current'] = [imp['current']] if imp['current'] else None

            # Convert suggested_add to list if it's a string
            if isinstance(imp.get('suggested_add'), str):
                imp['suggested_add'] = [imp['suggested_add']] if imp['suggested_add'] else None

        logging.info(f"✅ Tạo {len(improvements)} gợi ý cải thiện")
        return improvements
        
    except json.JSONDecodeError as e:
        logging.error(f"❌ Lỗi parse JSON: {e}")
        logging.error(f"Response: {content}")
        return [
            {
                "section": "general",
                "current": None,
                "suggested_add": None,
                "suggestion": "Cần phân tích thêm để đưa ra gợi ý cụ thể",
                "reason": "Lỗi phân tích",
                "priority": "medium",
                "impact": "N/A"
            }
        ]
    except Exception as e:
        logging.error(f"❌ Lỗi tạo gợi ý: {e}")
        raise


async def generate_why_match(cv_skills: List[str], job_skills: List[str], job_title: str) -> str:
    """
    Tạo lý do tại sao CV phù hợp với job
    
    Args:
        cv_skills: Kỹ năng từ CV
        job_skills: Kỹ năng yêu cầu của job
        job_title: Tiêu đề job
        
    Returns:
        Chuỗi mô tả lý do phù hợp
    """
    matched_skills = set(cv_skills) & set(job_skills)
    
    if not matched_skills:
        return f"Có thể phù hợp với vị trí {job_title}"
    
    skills_str = ", ".join(list(matched_skills)[:3])
    return f"Phù hợp với kỹ năng: {skills_str}"


def generate_question_suggestions(context: str, cv_info: Optional[Dict] = None, job_info: Optional[Dict] = None) -> List[Dict[str, str]]:
    """
    Tạo gợi ý câu hỏi dựa trên context
    
    Args:
        context: Context hiện tại (cv_uploaded, viewing_job, chatting)
        cv_info: Thông tin CV (optional)
        job_info: Thông tin job (optional)
        
    Returns:
        List các câu hỏi gợi ý
    """
    suggestions = []
    
    if context == "cv_uploaded":
        suggestions = [
            {
                "question": "CV của tôi có điểm mạnh gì?",
                "category": "cv_analysis",
                "icon": "💪"
            },
            {
                "question": "Tôi nên cải thiện kỹ năng gì để tăng cơ hội?",
                "category": "improvement",
                "icon": "📈"
            },
            {
                "question": "Mức lương tôi có thể mong đợi là bao nhiêu?",
                "category": "salary",
                "icon": "💰"
            },
            {
                "question": "Có job nào phù hợp với tôi không?",
                "category": "job_match",
                "icon": "🎯"
            },
            {
                "question": "CV của tôi thiếu gì so với thị trường?",
                "category": "gap_analysis",
                "icon": "🔍"
            }
        ]
    
    elif context == "viewing_job":
        job_title = job_info.get('job_title', 'công việc này') if job_info else 'công việc này'
        suggestions = [
            {
                "question": f"Tôi có phù hợp với vị trí {job_title} không?",
                "category": "job_fit",
                "icon": "🎯"
            },
            {
                "question": "Tôi cần chuẩn bị gì để ứng tuyển?",
                "category": "preparation",
                "icon": "📝"
            },
            {
                "question": "Mức lương của vị trí này có hợp lý không?",
                "category": "salary",
                "icon": "💰"
            },
            {
                "question": "Công ty này có uy tín không?",
                "category": "company",
                "icon": "🏢"
            }
        ]
    
    elif context == "chatting":
        suggestions = [
            {
                "question": "Tôi nên học skill gì tiếp theo?",
                "category": "learning",
                "icon": "📚"
            },
            {
                "question": "Làm thế nào để tăng cơ hội được tuyển?",
                "category": "tips",
                "icon": "💡"
            },
            {
                "question": "Có khóa học nào phù hợp với tôi?",
                "category": "courses",
                "icon": "🎓"
            }
        ]
    
    else:
        # Default suggestions
        suggestions = [
            {
                "question": "Tôi có thể hỏi gì?",
                "category": "general",
                "icon": "❓"
            }
        ]
    
    return suggestions

