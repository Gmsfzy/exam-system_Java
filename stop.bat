@echo off
chcp 65001 >nul
title Exam System - 停止服务

echo 正在停止后端和前端服务...
taskkill /F /IM java.exe 2>nul
taskkill /F /FI "WINDOWTITLE eq 前端服务*" 2>nul
taskkill /F /FI "WINDOWTITLE eq 后端服务*" 2>nul

echo.
echo 所有服务已停止。
pause