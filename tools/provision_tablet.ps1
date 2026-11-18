# Настройка планшета стенда AEROCELL: установка APK и включение режима киоска.
# Запускать с компьютера, к которому планшет подключён по USB.
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$ApkPath
)

$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Root = Split-Path -Parent $ScriptDir

function Fail([string]$Message) {
    Write-Host "ОШИБКА: $Message"
    exit 1
}

function Get-AdbPath {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    $candidates = @()
    if ($env:ANDROID_HOME) {
        $candidates += (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe')
    }
    if ($env:ANDROID_SDK_ROOT) {
        $candidates += (Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe')
    }
    $localProps = Join-Path $Root 'local.properties'
    if (Test-Path -LiteralPath $localProps) {
        $line = Get-Content -LiteralPath $localProps | Where-Object { $_ -match '^\s*sdk\.dir=' } | Select-Object -First 1
        if ($line) {
            $sdk = $line -replace '^\s*sdk\.dir=', ''
            $sdk = $sdk.Trim().Trim('"').Trim("'")
            $sdk = $sdk -replace '\\:', ':'
            $sdk = $sdk -replace '\\\\', '\'
            $candidates += (Join-Path $sdk 'platform-tools\adb.exe')
        }
    }
    $candidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
    $candidates += (Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk\platform-tools\adb.exe')
    foreach ($item in $candidates) {
        if ($item -and (Test-Path -LiteralPath $item)) { return $item }
    }
    return $null
}

function Get-ApkPath([string]$Explicit) {
    if ($Explicit) {
        if (-not (Test-Path -LiteralPath $Explicit)) {
            Fail "APK не найден: $Explicit"
        }
        return (Resolve-Path -LiteralPath $Explicit).Path
    }
    $all = @(Get-ChildItem -LiteralPath $Root -Filter 'AEROCELL-*.apk' -File -ErrorAction SilentlyContinue)
    $release = @($all | Where-Object { $_.Name -notmatch 'debug' })
    $pool = if ($release.Count -gt 0) { $release } else { $all }
    if ($pool.Count -eq 0) {
        Fail "В корне проекта нет файла AEROCELL-*.apk. Соберите release-сборку или укажите путь: provision_tablet.bat путь\к\файлу.apk"
    }
    return ($pool | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}

function Test-HasAccounts([string]$Dump) {
    $counts = [regex]::Matches($Dump, '(?m)^\s*Accounts:\s*(\d+)')
    foreach ($match in $counts) {
        if ([int]$match.Groups[1].Value -gt 0) { return $true }
    }
    if ([regex]::IsMatch($Dump, 'Account \{[^}]+\}')) { return $true }
    return $false
}

$adb = Get-AdbPath
if (-not $adb) {
    Fail "Не найден adb. Установите Android platform-tools, добавьте их в PATH или задайте переменную ANDROID_HOME."
}

$devicesRaw = & $adb devices
$devices = @()
foreach ($line in $devicesRaw) {
    if ($line -match '^(\S+)\s+(device|unauthorized|offline|no permissions)\s*$') {
        $devices += [pscustomobject]@{ Serial = $Matches[1]; State = $Matches[2] }
    }
}

if ($devices.Count -eq 0) {
    Fail "Планшет не виден. Подключите его по USB, включите «Отладка по USB» и на планшете подтвердите разрешение на отладку."
}
if ($devices.Count -gt 1) {
    Fail "Подключено больше одного устройства. Оставьте только один планшет и запустите скрипт снова."
}

$device = $devices[0]
if ($device.State -eq 'unauthorized') {
    Fail "Планшет виден, но отладка не разрешена. На экране планшета подтвердите запрос «Разрешить отладку по USB» и запустите скрипт снова."
}
if ($device.State -ne 'device') {
    Fail "Планшет в состоянии $($device.State). Проверьте кабель, «Отладка по USB» и на Xiaomi ещё «Установка через USB»."
}

$accountDump = & $adb shell dumpsys account 2>&1 | Out-String
if (Test-HasAccounts $accountDump) {
    Fail @"
На планшете есть аккаунт (Google, Xiaomi или другой). Режим киоска из-за этого не включится.

Выйдите из всех аккаунтов в настройках или сделайте сброс до заводских. В мастере настройки не входите ни в Google, ни в Mi-аккаунт. Затем запустите скрипт снова.

На HyperOS, чтобы включить «Установка через USB», иногда сначала нужно войти в Mi-аккаунт, включить переключатели и сразу выйти. К моменту запуска скрипта аккаунтов уже быть не должно.
"@
}

$apk = Get-ApkPath $ApkPath
Write-Host "Планшет: $($device.Serial)"
Write-Host "APK: $apk"
Write-Host "Ставлю приложение..."

$installOut = & $adb install -r $apk 2>&1 | Out-String
if ($LASTEXITCODE -ne 0 -or $installOut -match 'Failure|INSTALL_FAILED') {
    $hint = ''
    if ($installOut -match 'INSTALL_FAILED_USER_RESTRICTED') {
        $hint = "`nНа Xiaomi откройте «Для разработчиков» и включите «Установка через USB» и «Отладка по USB (параметры безопасности)». Если переключатели просят Mi-аккаунт: войдите, включите их, выйдите из аккаунта и запустите скрипт снова."
    }
    Fail "Не удалось установить APK.`n$installOut$hint"
}

$ownerComponent = 'com.aerocell.stand/.kiosk.StandAdminReceiver'
Write-Host "Включаю режим киоска..."
$dpmOut = & $adb shell dpm set-device-owner $ownerComponent 2>&1 | Out-String
$dpmOk = ($LASTEXITCODE -eq 0) -or ($dpmOut -match 'Success')

if (-not $dpmOk) {
    $ownerDump = & $adb shell dumpsys device_policy 2>&1 | Out-String
    if ($ownerDump -match 'com\.aerocell\.stand') {
        Write-Host "Режим киоска уже был включён для этого приложения."
        $dpmOk = $true
    }
}

if (-not $dpmOk) {
    $hint = ''
    if ($dpmOut -match 'account') {
        $hint = "`nПохоже, на планшете остался аккаунт. Выйдите из всех аккаунтов или сделайте сброс до заводских."
    }
    Fail "Не удалось включить режим киоска.`n$dpmOut$hint"
}

Write-Host "Запускаю приложение..."
& $adb shell am start -n com.aerocell.stand/.MainActivity | Out-Null

Write-Host ""
Write-Host "Готово."
Write-Host "Планшет: $($device.Serial)"
Write-Host "Приложение установлено, режим киоска включён, стенд запущен."
Write-Host "Обновления будут ставиться сами, когда стенд в режиме простоя."
