@echo off
chcp 65001 >nul
setlocal
set "DEST=%LOCALAPPDATA%\TabGuard"

echo.
echo  Страж вкладок — установка расширения
echo  ====================================
echo.

if not exist "%~dp0TabGuard\manifest.json" (
  echo  Не найдена папка TabGuard рядом с install.bat. Распакуйте архив целиком и запустите снова.
  pause
  exit /b 1
)

if exist "%DEST%" rmdir /s /q "%DEST%"
xcopy "%~dp0TabGuard" "%DEST%\" /e /i /q /y >nul
if errorlevel 1 (
  echo  Не удалось скопировать файлы в %DEST%
  pause
  exit /b 1
)
echo  Расширение скопировано в: %DEST%

powershell -NoProfile -Command "Set-Clipboard -Value ($env:LOCALAPPDATA + '\TabGuard')" >nul 2>&1
echo  Путь к папке скопирован в буфер обмена.
echo.

reg query "HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\chrome.exe" >nul 2>&1 && set "HAS_CHROME=1"
reg query "HKCU\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\chrome.exe" >nul 2>&1 && set "HAS_CHROME=1"
if defined HAS_CHROME start "" chrome "chrome://extensions"

echo  ПЕРВАЯ УСТАНОВКА (один раз):
echo    1. Откройте страницу расширений:
echo         Chrome:          chrome://extensions
echo         Яндекс.Браузер:  browser://extensions
echo    2. Включите «Режим разработчика» (переключатель справа вверху).
echo    3. Нажмите «Загрузить распакованное» и вставьте путь (Ctrl+V) в адресную строку окна выбора папки.
echo    4. В карточке «Страж вкладок» нажмите «Подробнее» и включите «Разрешить в режиме инкогнито».
echo.
echo  ОБНОВЛЕНИЕ:
echo    На странице расширений нажмите кнопку обновления (круглая стрелка) у «Стража вкладок».
echo    Категории и статистика сохранятся.
echo.
pause
