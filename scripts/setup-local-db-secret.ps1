[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
    [Console]::Error.WriteLine('LOCALAPPDATA is not set; cannot choose a secure local password location.')
    exit 1
}

$secretDirectory = Join-Path $env:LOCALAPPDATA 'Eelevate'
$secretPath = Join-Path $secretDirectory 'mysql-password.dpapi'
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\')
$fullSecretPath = [System.IO.Path]::GetFullPath($secretPath)

if ($fullSecretPath.Equals($repositoryRoot, [System.StringComparison]::OrdinalIgnoreCase) -or
    $fullSecretPath.StartsWith($repositoryRoot + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
    [Console]::Error.WriteLine('The encrypted password path resolves inside the repository; refusing to save it.')
    exit 1
}

$securePassword = $null
try {
    $securePassword = Read-Host 'Enter the MySQL root password' -AsSecureString
    if ($securePassword.Length -eq 0) {
        [Console]::Error.WriteLine('The password cannot be empty.')
        exit 1
    }

    $encryptedPassword = ConvertFrom-SecureString -SecureString $securePassword
    New-Item -ItemType Directory -Path $secretDirectory -Force | Out-Null
    [System.IO.File]::WriteAllText($fullSecretPath, $encryptedPassword, [System.Text.Encoding]::ASCII)
    Write-Host "Encrypted MySQL password saved to $fullSecretPath"
}
catch {
    [Console]::Error.WriteLine('Unable to encrypt or save the MySQL password.')
    exit 1
}
finally {
    if ($null -ne $securePassword) {
        $securePassword.Dispose()
    }
}
