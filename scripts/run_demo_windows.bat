@echo off
setlocal EnableExtensions
cd /d "%~dp0.."
where py >nul 2>&1
if errorlevel 1 (
  echo Chua co Python Launcher. Cai Python 3.11+ roi chay lai.
  pause
  exit /b 1
)
if not exist ".venv\Scripts\python.exe" py -3 -m venv .venv
if errorlevel 1 goto error
".venv\Scripts\python.exe" -m pip install -r backend\requirements.txt
if errorlevel 1 goto error
for /f "delims=" %%t in ('".venv\Scripts\python.exe" -c "import secrets; print(secrets.token_urlsafe(48))"') do set "COURTMATE_ADMIN_TOKEN=%%t"
set "COURTMATE_DB=%CD%\backend\courtmate_demo.db"
set "COURTMATE_PUBLIC_BOOKING=true"
".venv\Scripts\python.exe" scripts\seed_demo.py
if errorlevel 1 goto error
start "" /MIN cmd /c "timeout /t 4 /nobreak ^>nul ^& start http://127.0.0.1:8000/"
echo.
echo CourtMate demo dang chay tai http://127.0.0.1:8000/
echo Nhan Ctrl+C de dung. Chi dung cho demo tren may ca nhan.
echo.
cd backend
"..\.venv\Scripts\python.exe" -m uvicorn app:app --host 127.0.0.1 --port 8000
exit /b 0
:error
echo Khong the khoi dong demo. Kiem tra Python va mang internet de cai dependencies.
pause
exit /b 1
