@echo off
setlocal

set "DASHBOARD_BUILD_DIR=%~dp0"

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ErrorActionPreference = 'Stop';" ^
    "$sourceDirectory = [System.IO.Path]::GetFullPath($env:DASHBOARD_BUILD_DIR);" ^
    "$htmlPath = Join-Path $sourceDirectory 'dashboard-console-source.html';" ^
    "$scriptPath = Join-Path $sourceDirectory 'dashboard-console-source.js';" ^
    "$outputPath = Join-Path $sourceDirectory 'dashboard-console.html';" ^
    "if (-not (Test-Path -LiteralPath $htmlPath -PathType Leaf)) { throw ('Missing input file: ' + $htmlPath) };" ^
    "if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) { throw ('Missing input file: ' + $scriptPath) };" ^
    "$utf8 = New-Object System.Text.UTF8Encoding($false);" ^
    "$html = [System.IO.File]::ReadAllText($htmlPath);" ^
    "$javascript = [System.IO.File]::ReadAllText($scriptPath);" ^
    "$quote = [char]34;" ^
    "$marker = '<script src=' + $quote + 'dashboard-console-source.js' + $quote + '></script>';" ^
    "$markerCount = ([regex]::Matches($html, [regex]::Escape($marker))).Count;" ^
    "if ($markerCount -ne 1) { throw ('Expected exactly one dashboard script import, found ' + $markerCount) };" ^
    "if ($javascript -match '(?i)</script\s*>') { throw 'dashboard-console-source.js contains a closing script tag and cannot be safely inlined' };" ^
    "$newline = if ($html.Contains([Environment]::NewLine)) { [Environment]::NewLine } else { [char]10 };" ^
    "$replacement = '<script>' + $newline + $javascript.TrimEnd([char]13, [char]10) + $newline + '    </script>';" ^
    "$compiled = $html.Replace($marker, $replacement);" ^
    "[System.IO.File]::WriteAllText($outputPath, $compiled, $utf8);" ^
    "Write-Host ('Created ' + $outputPath)"

if errorlevel 1 (
    echo Dashboard compilation failed.
    exit /b 1
)

exit /b 0
