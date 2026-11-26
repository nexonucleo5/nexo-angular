<#
.SYNOPSIS
  Sobe e gerencia frontend + backend + banco do Nexo com um comando.

.EXAMPLE
  .\docker\nexo up             sobe tudo (cria o .env se faltar) e abre o navegador
  .\docker\nexo down           para os containers (mantém o banco)
  .\docker\nexo restart backend  rebuilda e sobe só um serviço
  .\docker\nexo logs backend   acompanha os logs (sem serviço = todos)
  .\docker\nexo status         mostra o estado de cada container
  .\docker\nexo reset          para e APAGA o banco
  .\docker\nexo push <usuario> [tag]   builda e publica no Docker Hub
  .\docker\nexo registry       sobe o registry local e publica nele
#>
param(
    [string]$Comando = 'up',
    [string]$Arg1,
    [string]$Arg2
)

$ErrorActionPreference = 'Stop'
$Raiz = Split-Path $PSScriptRoot -Parent
Set-Location $Raiz

function Falhar($msg) { Write-Host "x $msg" -ForegroundColor Red; exit 1 }
function Passo($msg)  { Write-Host "> $msg" -ForegroundColor Cyan }

function Compose {
    docker compose @args
    if ($LASTEXITCODE -ne 0) { Falhar "docker compose $($args -join ' ') falhou" }
}

function Segredo($bytes) {
    $b = New-Object byte[] $bytes
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
    [Convert]::ToBase64String($b)
}

# .env sem BOM: com BOM a primeira chave é lida com lixo na frente.
function PrepararEnv {
    $arquivo = Join-Path $Raiz '.env'
    $atual = if (Test-Path $arquivo) { Get-Content $arquivo -Raw } else { '' }
    $novo = ''
    if ($atual -notmatch '(?m)^NEXO_JWT_SECRET=\S') {
        $novo += "NEXO_JWT_SECRET=$(Segredo 48)`n"
    }
    if ($atual -notmatch '(?m)^POSTGRES_PASSWORD=\S') {
        $novo += "POSTGRES_PASSWORD=$((Segredo 18) -replace '[^A-Za-z0-9]', 'x')`n"
    }
    if ($novo) {
        if ($atual -and -not $atual.EndsWith("`n")) { $atual += "`n" }
        [IO.File]::WriteAllText($arquivo, $atual + $novo, (New-Object Text.UTF8Encoding $false))
        Passo '.env atualizado com segredos gerados'
    }
}

function PrepararDocker {
    docker info *> $null
    if ($LASTEXITCODE -ne 0) { Falhar 'Docker não está rodando. Abra o Docker Desktop e tente de novo.' }
}

function Porta {
    $m = Select-String -Path (Join-Path $Raiz '.env') -Pattern '^HOST_PORT=(\d+)' -ErrorAction SilentlyContinue
    if ($m) { $m.Matches[0].Groups[1].Value } else { '8080' }
}

PrepararDocker

switch ($Comando) {
    'up' {
        PrepararEnv
        Passo 'Subindo banco, backend e frontend...'
        Compose up -d --build --wait
        $url = "http://localhost:$(Porta)"
        Write-Host "`nNexo no ar: $url" -ForegroundColor Green
        Write-Host 'Logins de exemplo: diretor, professor, aluno, admin (senha 123456)'
        Start-Process $url
    }
    'down'    { Compose down }
    'restart' {
        PrepararEnv
        if (-not $Arg1) { Falhar 'Informe o serviço: db, backend ou frontend' }
        Compose up -d --build --wait $Arg1
    }
    'logs' {
        if ($Arg1) { Compose logs -f --tail 100 $Arg1 } else { Compose logs -f --tail 100 }
    }
    'status'  { Compose ps }
    'reset' {
        $r = Read-Host 'Isso APAGA o banco. Digite "sim" para continuar'
        if ($r -ne 'sim') { Falhar 'Cancelado.' }
        Compose down -v
    }
    'push' {
        if (-not $Arg1) { Falhar 'Uso: .\docker\nexo push <usuario-dockerhub> [tag]' }
        PrepararEnv
        $env:REGISTRY_NAMESPACE = $Arg1
        $env:TAG = if ($Arg2) { $Arg2 } else { 'latest' }
        Passo "Publicando $($env:REGISTRY_NAMESPACE)/nexo-*:$($env:TAG) (precisa de docker login)"
        Compose build
        Compose push
    }
    'registry' {
        PrepararEnv
        Compose --profile registry up -d registry
        $env:REGISTRY_NAMESPACE = 'localhost:5000/nexo'
        $env:TAG = if ($Arg1) { $Arg1 } else { 'latest' }
        Compose build
        Compose push
        Write-Host "`nImagens em http://localhost:5000/v2/_catalog" -ForegroundColor Green
    }
    default { Falhar "Comando desconhecido: $Comando. Veja: Get-Help .\docker\nexo.ps1" }
}
