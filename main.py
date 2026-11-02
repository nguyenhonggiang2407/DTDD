"""Run YangCareer locally, with frontend and API on the same origin."""
import os
import uvicorn
from api.settings import ROOT

if __name__ == "__main__":
    uvicorn.run(
        "api.main:app",
        host=os.environ.get("HOST", "127.0.0.1"),
        port=int(os.environ.get("PORT", "9990")),
        reload=os.environ.get("RELOAD", "0") == "1",
    )
