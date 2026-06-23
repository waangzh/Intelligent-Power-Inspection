from fastapi import HTTPException


def model_error(message: str, status_code: int = 500, code: str = "MODEL_RUNTIME_ERROR") -> HTTPException:
    return HTTPException(
        status_code=status_code,
        detail={"status": "FAILED", "code": code, "message": message, "details": {}},
    )
