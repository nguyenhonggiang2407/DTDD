import json
import logging
import os
from langchain_community.document_loaders import PyPDFLoader
from langchain_google_genai import GoogleGenerativeAIEmbeddings
from langchain_chroma import Chroma
from langchain_core.documents import Document
from .db_utils import get_db_connection, create_tables

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

_vectorstore = None

def get_vectorstore():
    """
    Khởi tạo Chroma vectorstore với Google Gemini Embedding API
    Model: text-embedding-004 (miễn phí, hỗ trợ multilingual)
    """
    global _vectorstore
    if _vectorstore is None:
        try:
            # Lấy API key từ environment
            google_api_key = os.getenv("GOOGLE_API_KEY")
            if not google_api_key:
                raise ValueError("GOOGLE_API_KEY not found in environment variables")

            base_dir = os.path.dirname(os.path.abspath(__file__))
            project_root = os.path.dirname(base_dir)
            chroma_path = os.path.join(project_root, "db/chroma_db")
            os.makedirs(chroma_path, exist_ok=True)

            # Sử dụng Google Gemini Embedding API
            embedding_function = GoogleGenerativeAIEmbeddings(
                model=os.environ.get("EMBEDDING_MODEL", "models/gemini-embedding-001"),
                google_api_key=google_api_key,
                task_type="retrieval_document"  # Tối ưu cho retrieval
            )

            _vectorstore = Chroma(
                persist_directory=chroma_path,
                collection_name="yangcareer_jobs",
                embedding_function=embedding_function
            )
            logging.info("✅ Initialized Chroma vectorstore with Google Gemini Embedding API (text-embedding-004)")
        except Exception as e:
            logging.error(f"❌ Error initializing Chroma vectorstore: {e}")
            raise
    return _vectorstore

def preload_jobs(jsonl_path: str, batch_size: int = 20) -> bool:
    create_tables()
    vectorstore = get_vectorstore()
    existing = set(vectorstore.get(include=["metadatas"]).get("ids", []))
    with get_db_connection() as conn:
        rows = [dict(row) for row in conn.execute("SELECT * FROM job_store ORDER BY id")]
    documents, ids = [], []
    for row in rows:
        document_id = "job_" + str(row["id"])
        if document_id in existing:
            continue
        metadata = {key: value if value is not None else "" for key, value in row.items()}
        metadata["job_id"] = row["id"]
        content = "\n".join(str(row.get(key, "")) for key in (
            "job_title", "name", "skills", "job_description", "candidate_requirements", "work_location"
        ))
        documents.append(Document(page_content=content, metadata=metadata))
        ids.append(document_id)
        if len(documents) >= batch_size:
            vectorstore.add_documents(documents, ids=ids)
            documents, ids = [], []
    if documents:
        vectorstore.add_documents(documents, ids=ids)
    return True

async def index_cv_extracts(skills: list, aspirations: str, experience: str, education: str, cv_id: int) -> bool:
    # CVs are intentionally excluded from the shared jobs vector collection.
    return True

def delete_cv_from_chroma(cv_id: int) -> bool:
    if not isinstance(cv_id, int):
        raise ValueError("cv_id must be an integer")
    try:
        vectorstore = get_vectorstore()
        docs = vectorstore.get(where={"cv_id": cv_id})
        if not docs['ids']:
            logging.info(f"No documents found for CV {cv_id}")
            return False
        vectorstore._collection.delete(where={"cv_id": cv_id})
        logging.info(f"Deleted CV {cv_id} from Chroma")
        return True
    except Exception as e:
        logging.error(f"Error deleting CV {cv_id} from Chroma: {e}")
        return False