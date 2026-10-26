# Equivalente do docker-publish.sh para PowerShell.
#   docker login
#   $env:DOCKERHUB_USER="meuuser"; $env:TAG="1.0.0"; .\scripts\docker-publish.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$user = if ($env:DOCKERHUB_USER) { $env:DOCKERHUB_USER } else { 'nexonucleo5' }
$tag  = if ($env:TAG) { $env:TAG } else { 'latest' }
$ctx  = [ordered]@{ backend = './backend'; frontend = './nexo'; database = './database' }
foreach ($nome in $ctx.Keys) {
    $img = "$user/nexo-$nome"
    Write-Host ">> ${img}:$tag"
    docker build -t "${img}:$tag" -t "${img}:latest" $ctx[$nome]
    if ($LASTEXITCODE) { exit 1 }
    docker push "${img}:$tag"
    if ($LASTEXITCODE) { exit 1 }
    if ($tag -ne 'latest') { docker push "${img}:latest" }
    if ($env:REGISTRY_LOCAL -eq '1') {
        docker tag "${img}:$tag" "localhost:5000/nexo-${nome}:$tag"
        docker push "localhost:5000/nexo-${nome}:$tag"
    }
}
