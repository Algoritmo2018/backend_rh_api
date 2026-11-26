<#
.SYNOPSIS
  Local staging (whole system in Docker). Shortcuts for docker compose; see deploy/README.md.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File deploy\staging.ps1 init
  powershell -ExecutionPolicy Bypass -File deploy\staging.ps1 up
  powershell -ExecutionPolicy Bypass -File deploy\staging.ps1 sms
#>
param(
    [Parameter(Position = 0)]
    [ValidateSet('init', 'up', 'status', 'logs', 'sms', 'rebuild', 'down', 'reset', 'help')]
    [string]$Command = 'help',

    [Parameter(Position = 1)]
    [string]$Service = '',

    # logs / sms: keep following new lines
    [switch]$Follow,

    # reset: do not ask for confirmation
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$deployDir = $PSScriptRoot
$composeFile = Join-Path $deployDir 'docker-compose.staging.yml'
$envFile = Join-Path $deployDir '.env.staging'
$exampleFile = Join-Path $deployDir '.env.staging.example'

function Invoke-Compose {
    param([string[]]$Arguments)
    & docker compose -f $composeFile --env-file $envFile @Arguments
    if ($LASTEXITCODE -ne 0) { throw "docker compose $($Arguments -join ' ') terminou com erro ($LASTEXITCODE)." }
}

function New-RandomBase64([int]$Bytes) {
    $buffer = New-Object byte[] $Bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($buffer)
    $rng.Dispose()
    return [Convert]::ToBase64String($buffer)
}

function New-RandomPassword {
    # Letters and digits only: safe in URLs, JDBC and compose files
    return ((New-RandomBase64 36) -replace '[^A-Za-z0-9]', '').Substring(0, 32)
}

function New-SecretFor([string]$Name) {
    switch ($Name) {
        'MINIO_KMS_SECRET_KEY' { return 'recrutamento-key:' + (New-RandomBase64 32) }
        'APP_SECRETS_KEY' { return New-RandomBase64 32 }
        'APP_JWT_SECRET' { return New-RandomBase64 48 }
        'APP_AUTH_TOKEN_PEPPER' { return New-RandomBase64 48 }
        default { return New-RandomPassword }
    }
}

function Initialize-EnvFile {
    if (Test-Path $envFile) {
        Write-Host "deploy\.env.staging ja existe: nada alterado (apague-o para gerar novos segredos; os dados antigos deixam de abrir)."
        return
    }
    $lines = Get-Content $exampleFile | ForEach-Object {
        if ($_ -match '^([A-Z0-9_]+)=CHANGE_ME$') { "$($Matches[1])=$(New-SecretFor $Matches[1])" } else { $_ }
    }
    # UTF-8 without BOM: docker compose would read a BOM as part of the first variable name
    [System.IO.File]::WriteAllLines($envFile, [string[]]$lines, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "Criado deploy\.env.staging com segredos aleatorios (fica so neste PC, nunca no git)."
}

function Assert-EnvFile {
    if (-not (Test-Path $envFile)) { Initialize-EnvFile }
}

function Get-AppPort {
    $line = Get-Content $envFile | Where-Object { $_ -match '^STAGING_HTTP_PORT=' } | Select-Object -First 1
    if ($line) { return $line.Split('=')[1].Trim() }
    return '8088'
}

function Get-MailpitPort {
    $line = Get-Content $envFile | Where-Object { $_ -match '^STAGING_MAILPIT_PORT=' } | Select-Object -First 1
    if ($line) { return $line.Split('=')[1].Trim() }
    return '8026'
}

switch ($Command) {
    'init' {
        Initialize-EnvFile
    }
    'up' {
        Assert-EnvFile
        Write-Host 'A construir e a arrancar (a primeira vez demora varios minutos)...'
        Invoke-Compose @('up', '-d', '--build', '--wait', '--wait-timeout', '600')
        Write-Host ''
        Write-Host "Aplicacao: http://localhost:$(Get-AppPort)/software_rh/"
        Write-Host "Mailpit:   http://localhost:$(Get-MailpitPort)"
    }
    'status' {
        Assert-EnvFile
        Invoke-Compose @('ps')
    }
    'logs' {
        Assert-EnvFile
        $arguments = @('logs', '--tail', '200')
        if ($Follow) { $arguments += '-f' }
        if ($Service) { $arguments += $Service }
        Invoke-Compose $arguments
    }
    'sms' {
        # Simulated SMS (2FA, SMS login, phone change) are written to the backend log
        Assert-EnvFile
        if ($Follow) {
            & docker compose -f $composeFile --env-file $envFile logs -f --no-log-prefix backend | Select-String -SimpleMatch '[simulated SMS]' | ForEach-Object { $_.Line }
        } else {
            $found = & docker compose -f $composeFile --env-file $envFile logs --no-log-prefix backend | Select-String -SimpleMatch '[simulated SMS]' | ForEach-Object { $_.Line }
            if ($found) { $found | Select-Object -Last 10 } else { Write-Host 'Ainda nao foi enviado nenhum SMS.' }
        }
    }
    'rebuild' {
        Assert-EnvFile
        $arguments = @('up', '-d', '--build', '--wait', '--wait-timeout', '600')
        if ($Service) { $arguments += $Service }
        Invoke-Compose $arguments
    }
    'down' {
        Assert-EnvFile
        Invoke-Compose @('down')
        Write-Host 'Parado. Os dados (base de dados e ficheiros) ficam guardados.'
    }
    'reset' {
        Assert-EnvFile
        if (-not $Force) {
            $answer = Read-Host 'Apagar a base de dados, os ficheiros e os emails do staging e recomecar? Escreva SIM'
            if ($answer -ne 'SIM') { Write-Host 'Cancelado.'; return }
        }
        Invoke-Compose @('down', '-v')
        Invoke-Compose @('up', '-d', '--build', '--wait', '--wait-timeout', '600')
        Write-Host "Recomecado do zero: http://localhost:$(Get-AppPort)/software_rh/"
    }
    default {
        Write-Host 'Uso: powershell -ExecutionPolicy Bypass -File deploy\staging.ps1 <comando> [servico] [-Follow] [-Force]'
        Write-Host '  init                 cria deploy\.env.staging com segredos aleatorios'
        Write-Host '  up                   constroi e arranca tudo (espera que fique saudavel)'
        Write-Host '  status               estado dos contentores'
        Write-Host '  logs [servico]       ultimos logs (backend, web, postgres, minio, mailpit); -Follow para seguir'
        Write-Host '  sms                  codigos SMS simulados (do log do backend); -Follow para seguir'
        Write-Host '  rebuild [servico]    reconstroi depois de alteracoes ao codigo (backend ou web)'
        Write-Host '  down                 para tudo (mantem os dados)'
        Write-Host '  reset                apaga todos os dados e recomeca (-Force sem perguntar)'
    }
}
