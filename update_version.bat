@echo off
chcp 65001 >nul
echo.
echo 🔧 버전 업데이트 스크립트
echo ========================

set GRADLE_FILE=app\build.gradle.kts

if not exist "%GRADLE_FILE%" (
    echo ❌ %GRADLE_FILE% 파일을 찾을 수 없습니다.
    pause
    exit /b 1
)

:: 현재 versionCode 읽기
for /f "tokens=3" %%i in ('findstr "versionCode = " "%GRADLE_FILE%"') do set CURRENT_VERSION=%%i

if "%CURRENT_VERSION%"=="" (
    echo ❌ 현재 버전 코드를 찾을 수 없습니다.
    pause
    exit /b 1
)

:: 새 버전 계산
set /a NEW_VERSION=%CURRENT_VERSION%+1

:: 임시 파일 생성
set TEMP_FILE=%GRADLE_FILE%.tmp

:: versionCode 업데이트
(for /f "delims=" %%i in (%GRADLE_FILE%) do (
    set "line=%%i"
    setlocal enabledelayedexpansion
    if "!line:versionCode = %CURRENT_VERSION%=!" neq "!line!" (
        echo         versionCode = %NEW_VERSION%
    ) else if "!line:versionName = =!" neq "!line!" (
        echo         versionName = "1.%NEW_VERSION%"
    ) else (
        echo !line!
    )
    endlocal
)) > "%TEMP_FILE%"

:: 원본 파일 교체
move "%TEMP_FILE%" "%GRADLE_FILE%" >nul

echo ✅ 버전 업데이트 완료!
echo    이전: v1.%CURRENT_VERSION% (빌드 #%CURRENT_VERSION%)
echo    현재: v1.%NEW_VERSION% (빌드 #%NEW_VERSION%)
echo.
echo 🔧 변경 사항:
echo    - versionCode: %CURRENT_VERSION% → %NEW_VERSION%
echo    - versionName: → 1.%NEW_VERSION%
echo.
echo 📱 이제 앱을 빌드하면 상단에 새 버전 번호가 표시됩니다!
echo.
pause 