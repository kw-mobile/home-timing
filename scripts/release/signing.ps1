[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('CreateKey', 'Build', 'UploadSecrets')]
    [string]$Action,
    [string]$SigningDirectory = '',
    [string]$VersionName = '0.1.0',
    [ValidateRange(1, 2100000000)][int]$VersionCode = 1,
    [string]$GhPath = 'gh'
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if (!$SigningDirectory) { $SigningDirectory = Join-Path $projectRoot '.signing' }
$SigningDirectory = [IO.Path]::GetFullPath($SigningDirectory)
$storePath = Join-Path $SigningDirectory 'home-timing-release.jks'
$credentialPath = Join-Path $SigningDirectory 'password.dpapi.xml'
$alias = 'hometiming'
$names = @('RELEASE_STORE_FILE', 'RELEASE_STORE_PASSWORD', 'RELEASE_KEY_ALIAS', 'RELEASE_KEY_PASSWORD')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    if ($Action -eq 'CreateKey') {
        if ((Test-Path $storePath) -or (Test-Path $credentialPath)) {
            throw 'Existing signing files found. They will not be replaced.'
        }
        if (!$env:JAVA_HOME -or !(Test-Path (Join-Path $env:JAVA_HOME 'bin/keytool.exe'))) {
            throw 'Set JAVA_HOME to JDK 17 before creating the key.'
        }
        [IO.Directory]::CreateDirectory($SigningDirectory) | Out-Null
        # Restrict generated material before writing it. Password is encrypted by Windows DPAPI.
        $acl = New-Object Security.AccessControl.DirectorySecurity
        $acl.SetAccessRuleProtection($true, $false)
        $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
        $rule = New-Object Security.AccessControl.FileSystemAccessRule(
            $sid, 'FullControl', 'ContainerInherit, ObjectInherit', 'None', 'Allow')
        $acl.AddAccessRule($rule)
        Set-Acl -LiteralPath $SigningDirectory -AclObject $acl
        $random = New-Object byte[] 32
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($random) } finally { $rng.Dispose() }
        $password = [Convert]::ToBase64String($random)
        $secure = ConvertTo-SecureString $password -AsPlainText -Force
        $credential = New-Object Management.Automation.PSCredential($alias, $secure)
        $credential | Export-Clixml -LiteralPath $credentialPath
        $env:RELEASE_STORE_PASSWORD = $password
        & (Join-Path $env:JAVA_HOME 'bin/keytool.exe') -genkeypair -noprompt -storetype JKS -keystore $storePath -alias $alias -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=HomeTiming Release,O=HomeTiming,C=KR' -storepass:env RELEASE_STORE_PASSWORD -keypass:env RELEASE_STORE_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Key generation failed. Preserve files for diagnosis; do not overwrite them.' }
        Write-Output "Created release key: $storePath"
        Write-Output "Encrypted password: $credentialPath (readable only by this Windows user on this PC)"
    } else {
        if (!(Test-Path $storePath) -or !(Test-Path $credentialPath)) { throw 'Signing files missing. Run CreateKey first.' }
        $credential = Import-Clixml -LiteralPath $credentialPath
        $password = $credential.GetNetworkCredential().Password
        if ($Action -eq 'Build') {
            $env:RELEASE_STORE_FILE = $storePath
            $env:RELEASE_STORE_PASSWORD = $password
            $env:RELEASE_KEY_ALIAS = $credential.UserName
            $env:RELEASE_KEY_PASSWORD = $password
            Push-Location $projectRoot
            try {
                & './gradlew.bat' --no-daemon --no-configuration-cache --no-build-cache --console=plain "-PreleaseVersionName=$VersionName" "-PreleaseVersionCode=$VersionCode" :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease
                if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
            } finally { Pop-Location }
        } else {
            # Inspect names first. Never silently replace another release key in GitHub.
            $existing = & $GhPath secret list --repo kw-mobile/home-timing --json name --jq '.[].name'
            if ($LASTEXITCODE -ne 0) { throw 'Cannot access GitHub secrets. Grant repository Secrets read/write to your PAT.' }
            $values = [ordered]@{
                ANDROID_KEYSTORE_BASE64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($storePath))
                ANDROID_STORE_PASSWORD = $password
                ANDROID_KEY_ALIAS = $credential.UserName
                ANDROID_KEY_PASSWORD = $password
            }
            foreach ($name in $values.Keys) {
                if ($existing -contains $name) { throw "Secret $name already exists. Verify the original key before changing signing secrets." }
            }
            foreach ($name in $values.Keys) {
                # Secret travels through stdin, never through the command line or console.
                $values[$name] | & $GhPath secret set $name --repo kw-mobile/home-timing
                if ($LASTEXITCODE -ne 0) { throw "Could not upload $name. Some earlier secrets may already be registered." }
                Write-Output "Registered secret: $name"
            }
        }
    }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    $password = $null
    $credential = $null
    $values = $null
}
