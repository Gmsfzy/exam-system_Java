@echo off
chcp 65001 >nul
title Exam System - 一键启动

cd /d "%~dp0"

echo ============================================
echo    考试系统 - 一键启动脚本
echo ============================================
echo.

echo [1/3] 正在检查 Java 代码是否需要重新编译...
if not exist target (
    echo       首次运行，正在编译 Java 代码...
    mkdir target
    javac -cp .;sqlite-jdbc.jar -d target src\main\java\com\exam\ai\*.java src\main\java\com\exam\api\*.java src\main\java\com\exam\dao\*.java src\main\java\com\exam\database\*.java src\main\java\com\exam\enums\*.java src\main\java\com\exam\models\*.java src\main\java\com\exam\ExamSystem.java src\main\java\com\exam\Main.java
    if errorlevel 1 (
        echo [错误] Java 编译失败，请检查上面的错误信息
        pause
        exit /b 1
    )
    echo       编译完成！
) else (
    echo       target 目录已存在，跳过编译（若修改过 Java 代码，请先运行 recompile.bat）
)
echo.

echo [2/3] 正在启动后端服务（端口 8080）...
cd /d "%~dp0"
start "后端服务 - Exam System API" cmd /k cd /d "%~dp0" ^& java -cp target;sqlite-jdbc.jar com.exam.api.ApiServer

timeout /t 2 /nobreak >nul

echo [3/3] 正在启动前端服务（端口 5173）...
cd /d "%~dp0frontend"
start "前端服务 - Vue3 + Vite" cmd /k npm run dev

echo.
echo ============================================
echo    启动完成！请在浏览器打开：
echo    http://localhost:5173/
echo ============================================
echo.
echo 提示：
echo   - 两个新弹出的窗口请勿关闭，它们分别是后端和前端服务
echo   - 若要完全停止，请运行 stop.bat
echo   - 如果访问 5173 没反应，请等待几秒让前端编译完成
echo.
pause