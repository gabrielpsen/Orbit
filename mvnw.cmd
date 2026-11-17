@echo off
setlocal EnableDelayedExpansion
set "MVN_VERSION=3.10.0"
set "MVN_BIN="
where mvn >nul 2>nul
if %ERRORLEVEL%==0 (
  for /f "tokens=3" %%v in ('mvn -version ^| findstr /b "Apache Maven"') do set "LOCAL_MVN=%%v"
  if "!LOCAL_MVN!"=="%MVN_VERSION%" set "MVN_BIN=mvn"
)
if not defined MVN_BIN (
  set "CACHE=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MVN_VERSION%"
  set "MVN_BIN=!CACHE!\apache-maven-%MVN_VERSION%\bin\mvn.cmd"
  if not exist "!MVN_BIN!" (
    if not exist "!CACHE!" mkdir "!CACHE!"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $u='https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MVN_VERSION%/apache-maven-%MVN_VERSION%-bin.zip'; $a='!CACHE!\apache-maven-%MVN_VERSION%-bin.zip'; Invoke-WebRequest $u -OutFile $a; Invoke-WebRequest ($u+'.sha512') -OutFile ($a+'.sha512'); $expected=((Get-Content ($a+'.sha512') -Raw).Trim() -split '\s+')[0]; $actual=(Get-FileHash $a -Algorithm SHA512).Hash.ToLowerInvariant(); if ($actual -ne $expected.ToLowerInvariant()) { throw 'Maven SHA-512 mismatch' }; Expand-Archive -Force $a '!CACHE!'; Remove-Item $a,($a+'.sha512')"
    if errorlevel 1 exit /b 1
  )
)
setlocal DisableDelayedExpansion
call "%MVN_BIN%" -f "%~dp0pom.xml" %*
exit /b %ERRORLEVEL%
