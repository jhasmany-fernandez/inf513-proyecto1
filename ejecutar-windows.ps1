param(
    [ValidateSet('--init','--execute','--once','--listen')]
    [string]$Modo = '--listen',
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Argumentos
)

$ErrorActionPreference = 'Stop'
$Proyecto = Split-Path -Parent $MyInvocation.MyCommand.Path
$EnvFile = Join-Path $Proyecto '.env.remoto'

if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "Falta la configuracion: $EnvFile"
}

Get-Content -LiteralPath $EnvFile -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $Nombre = $Matches[1]
        $Valor = $Matches[2].Trim()
        if ($Valor.Length -ge 2 -and (($Valor.StartsWith('"') -and $Valor.EndsWith('"')) -or ($Valor.StartsWith("'") -and $Valor.EndsWith("'")))) {
            $Valor = $Valor.Substring(1, $Valor.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($Nombre, $Valor, 'Process')
    }
}

Push-Location $Proyecto
try {
    $Maven = Get-Command mvn -ErrorAction SilentlyContinue
    if ($Maven) { $Maven = $Maven.Source }
    else {
        $Maven = Join-Path $Proyecto '.tools\apache-maven-3.9.9\bin\mvn.cmd'
        if (-not (Test-Path -LiteralPath $Maven)) { throw 'Instale Maven y agreguelo al PATH.' }
    }
    & $Maven -q compile dependency:copy-dependencies -DincludeScope=runtime
    if ($LASTEXITCODE -ne 0) { throw 'La compilacion fallo.' }

    $Classpath = 'target/classes;target/dependency/*'
    & java -cp $Classpath org.academia.correo.ServicioEmail $Modo @Argumentos
    if ($LASTEXITCODE -ne 0) { throw "El servicio termino con codigo $LASTEXITCODE." }
}
finally {
    Pop-Location
}
