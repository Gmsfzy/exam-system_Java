@echo off
chcp 65001 >nul
title Exam System - 重新编译 Java

cd /d "%~dp0"

echo 正在清理旧的编译文件...
if exist target (rmdir /s /q target)
mkdir target

echo 正在编译所有 Java 代码...
javac -cp ".;sqlite-jdbc.jar" -d target src\main\java\com\exam\ExamSystem.java src\main\java\com\exam\Main.java src\main\java\com\exam\ai\AIClient.java src\main\java\com\exam\ai\AIService.java src\main\java\com\exam\api\ApiServer.java src\main\java\com\exam\dao\AnswerDao.java src\main\java\com\exam\dao\ExamDao.java src\main\java\com\exam\dao\ExamQuestionDao.java src\main\java\com\exam\dao\ExamSessionDao.java src\main\java\com\exam\dao\ExamStudentDao.java src\main\java\com\exam\dao\MajorDao.java src\main\java\com\exam\dao\QuestionDao.java src\main\java\com\exam\dao\ResultDao.java src\main\java\com\exam\dao\UserDao.java src\main\java\com\exam\database\DatabaseManager.java src\main\java\com\exam\enums\DifficultyEnum.java src\main\java\com\exam\enums\ExamStatusEnum.java src\main\java\com\exam\enums\QuestionTypeEnum.java src\main\java\com\exam\enums\RoleEnum.java src\main\java\com\exam\enums\SessionStatusEnum.java src\main\java\com\exam\models\Answer.java src\main\java\com\exam\models\Exam.java src\main\java\com\exam\models\ExamQuestion.java src\main\java\com\exam\models\ExamSession.java src\main\java\com\exam\models\ExamStudent.java src\main\java\com\exam\models\Major.java src\main\java\com\exam\models\Question.java src\main\java\com\exam\models\Result.java src\main\java\com\exam\models\User.java

if errorlevel 1 (
    echo.
    echo [错误] 编译失败，请检查上面的错误信息
    pause
    exit /b 1
)

echo.
echo 重新编译完成！现在可以运行 start.bat 启动系统。
pause