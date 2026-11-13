<# : batch portion
@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@Echo Off
@setlocal

set "MAVEN_PROJECTBASEDIR=%~dp0"
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"

set "WRAPPER_DIR=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.9"
set "MVN_CMD=%WRAPPER_DIR%\apache-maven-3.9.9\bin\mvn.cmd"

if not exist "%MVN_CMD%" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
      "$ErrorActionPreference='Stop'; New-Item -ItemType Directory -Force -Path '%WRAPPER_DIR%' | Out-Null; $zip='%WRAPPER_DIR%\maven.zip'; Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip' -OutFile $zip; Expand-Archive -Force -Path $zip -DestinationPath '%WRAPPER_DIR%'; Remove-Item $zip"
)

call "%MVN_CMD%" %*
exit /B %ERRORLEVEL%
#>
