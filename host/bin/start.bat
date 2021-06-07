@if (@CodeSection == @Batch) @then

@echo off
setlocal

rem TODO: Check if java is installed and if not install!

rem // Get repo version from config.properties
rem this is for when the code works properly on windows with updateRepo, watchout the 10 characters could be wrong!
rem for /F "delims=" %%a in ('findstr /I "updateRepo" %HOMEPATH%/3dPrinters/config.properties') do set "updateRepo=%%a"

for /F "delims=" %%a in ('findstr /I "printerProfileRepo" config.properties') do set "updateRepo=%%a"

IF NOT "%1"=="" (
    set updateRepo=%1
) ELSE (
  IF "%updateRepo%"=="" (
	set updateRepo=area515/Photonic3D
  ) ELSE (
	rem set updateRepo=%updateRepo:~10% when changing to updateREpo
	set updateRepo=%updateRepo:~19%
  )
)

echo %updateRepo%

rem // download dependencies (unzip and curl) **WARNING** bitsadmin.exe is deprecated but seems available from xp to win10 for now! 
rem // Might break in future but windows has no proper alternative
rem // also it's extremely slow to start connecting! But hey it works(ish)
if NOT exist %CD%\unzip.exe (
echo installing unzip.exe
bitsadmin.exe /transfer "Unzip.exe" https://github.com/%updateRepo%/raw/master/host/bin/unzip.exe "%cd%\unzip.exe"
)

if NOT exist %CD%\curl\bin\curl.exe (
	echo installing curl
	IF NOT exist curl.zip (
		bitsadmin.exe /transfer "Curl" https://github.com/%updateRepo%/raw/master/host/bin/curl.zip "%cd%\curl.zip"
		unzip -o curl.zip
	) ELSE (
		unzip -o curl.zip
	)
	del -Q curl.zip
)

rem // Get latest release file from github
%CD%\curl\bin\curl -outf https://api.github.com/repos/%updateRepo%/releases/latest

rem // Set location of downloaded release file (json)
set "latestRelease=utf"

rem // Find current repoversion from build.number
for /F "delims=" %%a in ('findstr /I "repo.version" build.number') do set "repoVersion=%%a"

rem // Cut off repo.version !WARNING! when this changes it will break the code!
set repoVersion=%repoVersion:~13%

rem // Jscript  call to parse JSON
for /f "delims=" %%I in ('cscript /nologo /e:JScript "%~f0" "%latestRelease%"') do set "%%~I"


echo Network TAG: %tag_name%
echo Local TAG : %repoVersion%

del -Q utf

rem // Determing if updating is needed
IF "%tag_name%"=="~13" (
	echo Download URL = %browser_download_url%
	%CD%\curl\bin\curl -L %browser_download_url% > Update.zip
	unzip -o Update
	java -Dlog4j.configurationFile=log4j2.properties -cp lib/*;. org.area515.resinprinter.server.Main > log.out 2> log.err
) ELSE (
	IF "%tag_name%"=="%repoVersion%" (
		echo No update needed
		java -Dlog4j.configurationFile=log4j2.properties -cp lib/*;. org.area515.resinprinter.server.Main > log.out 2> log.err
	) ELSE (
		echo Download URL = %browser_download_url%
		%CD%\curl\bin\curl -L %browser_download_url% > Update.zip
		unzip -o Update
		del -Q Update.zip
		java -Dlog4j.configurationFile=log4j2.properties -cp lib/*;. org.area515.resinprinter.server.Main > log.out 2> log.err
	)
)

goto :EOF

@end // end batch / begin JScript chimera

var fso = WSH.CreateObject('scripting.filesystemobject'),
    latestRelease = fso.OpenTextFile(WSH.Arguments(0), 1);

eval('obj = ' + latestRelease.ReadAll());
latestRelease.Close();

function walk(tree) {
    for (var i in tree) {
        if (typeof tree[i] === 'object') walk(tree[i]);
        else WSH.Echo(i + '=' + tree[i]);
    }
}

walk(obj);
