@echo off
setlocal
where adb >nul 2>nul
if errorlevel 1 (
  echo ADB was not found in PATH. Add platform-tools to PATH or run this file from a configured terminal.
  pause
  exit /b 1
)
where py >nul 2>nul
if errorlevel 1 (
  echo Python launcher "py" was not found. Install Python 3 for Windows.
  pause
  exit /b 1
)
echo Creating ADB USB port forward on TCP 8765...
adb forward tcp:8765 tcp:8765
if errorlevel 1 (
  echo ADB port forwarding failed. Check that the PD500C is connected and authorized.
  pause
  exit /b 1
)
echo Starting Freelander MIDI bridge...
py "%~dp0bridge.py" --port-name "Freelander Control"
pause
