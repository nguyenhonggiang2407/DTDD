from contextvars import ContextVar

workspace_id = ContextVar("workspace_id", default=None)

def require_workspace():
    value = workspace_id.get()
    if not value:
        raise PermissionError("A workspace session is required.")
    return value
