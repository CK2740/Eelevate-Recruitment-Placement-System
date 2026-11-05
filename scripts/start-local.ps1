[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
    [Console]::Error.WriteLine('LOCALAPPDATA is not set; cannot locate the encrypted MySQL password.')
    exit 1
}

$secretPath = Join-Path (Join-Path $env:LOCALAPPDATA 'Eelevate') 'mysql-password.dpapi'
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\')
$fullSecretPath = [System.IO.Path]::GetFullPath($secretPath)

if ($fullSecretPath.Equals($repositoryRoot, [System.StringComparison]::OrdinalIgnoreCase) -or
    $fullSecretPath.StartsWith($repositoryRoot + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
    [Console]::Error.WriteLine('The encrypted password path resolves inside the repository; refusing to read it.')
    exit 1
}

if (-not (Test-Path -LiteralPath $fullSecretPath -PathType Leaf)) {
    [Console]::Error.WriteLine("Encrypted MySQL password not found at $fullSecretPath. Run .\scripts\setup-local-db-secret.ps1 first.")
    exit 1
}

$encryptedPassword = $null
try {
    $encryptedPassword = [System.IO.File]::ReadAllText($fullSecretPath).Trim()
    if ([string]::IsNullOrWhiteSpace($encryptedPassword)) {
        throw 'The encrypted password file is empty.'
    }
}
catch {
    [Console]::Error.WriteLine("Unable to read the encrypted MySQL password at $fullSecretPath.")
    exit 1
}

$securePassword = $null
$passwordPointer = [IntPtr]::Zero
$password = $null
try {
    $securePassword = ConvertTo-SecureString -String $encryptedPassword
    $passwordPointer = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    $password = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    if ([string]::IsNullOrEmpty($password)) {
        throw 'The decrypted password is empty.'
    }
}
catch {
    [Console]::Error.WriteLine('Unable to decrypt the MySQL password. Run the setup script again as this Windows user.')
    exit 1
}
finally {
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    if ($null -ne $securePassword) {
        $securePassword.Dispose()
    }
}

$maven = Get-Command mvn.cmd -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
if ($null -eq $maven) {
    $maven = Get-Command mvn -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
}
if ($null -eq $maven -or [string]::IsNullOrWhiteSpace($maven.Path)) {
    [Console]::Error.WriteLine('Maven (mvn) was not found on PATH. Install Maven or add it to PATH, then try again.')
    exit 1
}
$mavenPath = $maven.Path
$mavenIsCommandScript = [System.IO.Path]::GetExtension($mavenPath).Equals('.cmd', [System.StringComparison]::OrdinalIgnoreCase)

$hadPreviousUrl = Test-Path Env:DB_URL
$previousUrl = $env:DB_URL
$hadPreviousUsername = Test-Path Env:DB_USERNAME
$previousUsername = $env:DB_USERNAME
$hadPreviousPassword = Test-Path Env:DB_PASSWORD
$previousPassword = $env:DB_PASSWORD
$previousNativePreference = Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue
$hadPreviousNativePreference = $null -ne $previousNativePreference
$previousNativePreferenceValue = if ($hadPreviousNativePreference) { $previousNativePreference.Value } else { $null }
$exitCode = 0

try {
    $env:DB_URL = 'jdbc:mysql://localhost:3306/recruitment_db?serverTimezone=UTC'
    $env:DB_USERNAME = 'root'
    $env:DB_PASSWORD = $password
    $password = $null

    Push-Location $repositoryRoot
    try {
        $PSNativeCommandUseErrorActionPreference = $false
        try {
            if ($mavenIsCommandScript) {
                $mavenCommandLine = '""{0}" spring-boot:run"' -f $mavenPath
                & $env:ComSpec /d /s /c $mavenCommandLine
            }
            else {
                & $mavenPath spring-boot:run
            }
            $exitCode = $LASTEXITCODE
            if ($exitCode -ne 0) {
                [Console]::Error.WriteLine("mvn spring-boot:run returned exit code $exitCode.")
            }
        }
        catch {
            [Console]::Error.WriteLine("Unable to launch Maven: $($_.Exception.Message)")
            $exitCode = 1
        }
        finally {
            if ($hadPreviousNativePreference) {
                Set-Variable -Name PSNativeCommandUseErrorActionPreference -Value $previousNativePreferenceValue
            }
            else {
                Remove-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue
            }
        }
    }
    finally {
        Pop-Location
    }
}
catch {
    [Console]::Error.WriteLine("Unable to prepare Maven startup: $($_.Exception.Message)")
    $exitCode = 1
}
finally {
    if ($hadPreviousUrl) {
        $env:DB_URL = $previousUrl
    }
    else {
        Remove-Item Env:DB_URL -ErrorAction SilentlyContinue
    }
    if ($hadPreviousUsername) {
        $env:DB_USERNAME = $previousUsername
    }
    else {
        Remove-Item Env:DB_USERNAME -ErrorAction SilentlyContinue
    }
    if ($hadPreviousPassword) {
        $env:DB_PASSWORD = $previousPassword
    }
    else {
        Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    }
}

if ($exitCode -ne 0) {
    exit $exitCode
}
