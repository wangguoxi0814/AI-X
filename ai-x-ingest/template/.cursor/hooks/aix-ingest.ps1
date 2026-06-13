# AI-X Cursor Hook — PowerShell wrapper (fail-open)
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$pythonScript = Join-Path $scriptDir "aix_ingest.py"

try {
    if (Get-Command python -ErrorAction SilentlyContinue) {
        $inputJson = [Console]::In.ReadToEnd()
        $inputJson | python $pythonScript $args[0] 2>&1 | ForEach-Object { Write-Error $_ -ErrorAction Continue }
        exit $LASTEXITCODE
    }

    if (-not (Get-Command python3 -ErrorAction SilentlyContinue)) {
        Write-Error "Python not found; install Python 3 or use aix_ingest.py directly."
        exit 0
    }

    $inputJson = [Console]::In.ReadToEnd()
    $inputJson | python3 $pythonScript $args[0] 2>&1 | ForEach-Object { Write-Error $_ -ErrorAction Continue }
    exit 0
}
catch {
    Write-Error "aix-ingest fail-open: $_"
    exit 0
}
