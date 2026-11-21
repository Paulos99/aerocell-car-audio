# Настройка планшета стенда AEROCELL: установка APK и включение режима киоска.
# Запускать с компьютера, к которому планшет подключён по USB.
# Без параметров: ставит самую свежую release-сборку из корня проекта и включает киоск.
# -Diagnose: только показывает состояние планшета, ничего не меняет.
# Все шаги пишутся в лог tools\provision-*.log.
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$ApkPath,
    [switch]$Diagnose,
    [int]$WaitSeconds = 90
)

$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Root = Split-Path -Parent $ScriptDir
$LogPath = Join-Path $ScriptDir ("provision-{0}.log" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
$StageDir = Join-Path ([IO.Path]::GetTempPath()) 'aerocell-provision'
$script:Adb = $null

function Write-Log {
    param([string]$Text = '')
    Write-Host $Text
    try { Add-Content -LiteralPath $LogPath -Value $Text -Encoding UTF8 } catch { }
}

function Fail {
    param([string]$Message, [string]$Hint = '')
    Write-Log ''
    Write-Log "ОШИБКА: $Message"
    if ($Hint) {
        Write-Log ''
        Write-Log $Hint
    }
    Write-Log ''
    Write-Log "Подробный лог: $LogPath"
    exit 1
}

function Invoke-Adb {
    param([string[]]$Arguments)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $lines = @(& $script:Adb @Arguments 2>&1 | ForEach-Object { "$_" })
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    [pscustomobject]@{
        Lines = $lines
        ExitCode = $code
        Text = ($lines -join [Environment]::NewLine)
    }
}

function Start-AdbServer {
    # Сервер adb запускаем отдельно и без ожидания: команда start-server создаёт
    # фоновый процесс, который не завершается, поэтому -Wait здесь повесит скрипт.
    try {
        Start-Process -FilePath $script:Adb -ArgumentList 'start-server' -WindowStyle Hidden -ErrorAction Stop | Out-Null
        Start-Sleep -Seconds 1
    } catch {
        Write-Log ('adb start-server: ' + $_.Exception.Message)
    }
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

function Get-DeviceList {
    $result = Invoke-Adb -Arguments @('devices')
    $list = @()
    foreach ($line in $result.Lines) {
        if ($line -match '^(\S+)\s+(device|unauthorized|offline|no permissions)\s*$') {
            $list += [pscustomobject]@{ Serial = $Matches[1]; State = $Matches[2] }
        }
    }
    return $list
}

function Wait-ForDevice {
    param([int]$Seconds)

    $deadline = (Get-Date).AddSeconds([Math]::Max(0, $Seconds))
    $stateNotice = $false
    $lastNote = Get-Date
    $list = @()

    while ($true) {
        $list = @(Get-DeviceList)
        if ($list.Count -gt 1) {
            $serials = ($list | ForEach-Object { $_.Serial }) -join ', '
            Fail "Подключено больше одного устройства: $serials" "Оставьте только планшет стенда на кабеле и запустите скрипт снова."
        }
        if ($list.Count -eq 1) {
            $device = $list[0]
            switch ($device.State) {
                'device' { return $device }
                'unauthorized' {
                    if (-not $stateNotice) {
                        Write-Log "Планшет $($device.Serial) виден, но отладка не разрешена."
                        Write-Log 'На экране планшета нажмите «Разрешить отладку с этого компьютера» и поставьте галочку «Всегда разрешать с этого компьютера».'
                        $stateNotice = $true
                    }
                }
                default {
                    if (-not $stateNotice) {
                        Write-Log "Планшет в состоянии $($device.State). Проверьте кабель и «Отладку по USB»."
                        $stateNotice = $true
                    }
                }
            }
        } elseif (((Get-Date) - $lastNote).TotalSeconds -ge 15) {
            $left = [Math]::Max(0, [int](($deadline - (Get-Date)).TotalSeconds))
            Write-Log "Жду планшет по USB... осталось $left с"
            $lastNote = Get-Date
        }

        if ((Get-Date) -ge $deadline) { break }
        Start-Sleep -Seconds 2
    }

    if ($list.Count -eq 0) {
        Fail 'Планшет не виден по USB.' 'Подключите кабель, включите в планшете «Для разработчиков» → «Отладка по USB», подтвердите разрешение на экране планшета. На HyperOS может понадобиться «Отладка по USB (параметры безопасности)». Проверьте кабель и порт.'
    }
    if ($list[0].State -eq 'unauthorized') {
        Fail 'Планшет виден, но отладка не разрешена.' 'На экране планшета подтвердите запрос «Разрешить отладку по USB» и запустите скрипт снова.'
    }
    Fail "Планшет в состоянии $($list[0].State)." 'Проверьте кабель, «Отладку по USB» и на Xiaomi ещё «Установку через USB».'
}

function Get-Prop {
    param([string]$Name)
    $result = Invoke-Adb -Arguments @('shell', 'getprop', $Name)
    return (($result.Text) -replace "`r", '').Trim()
}

function Show-Diagnostics {
    param($Device)

    Write-Log ''
    Write-Log '--- Диагностика ---'
    Write-Log "Устройство: $($Device.Serial)"
    $props = @(
        @{ Name = 'ro.product.manufacturer'; Label = 'Производитель' },
        @{ Name = 'ro.product.model'; Label = 'Модель' },
        @{ Name = 'ro.build.version.release'; Label = 'Android' },
        @{ Name = 'ro.build.version.sdk'; Label = 'SDK' },
        @{ Name = 'ro.miui.ui.version.name'; Label = 'HyperOS/MIUI' },
        @{ Name = 'ro.build.version.incremental'; Label = 'Сборка' }
    )
    foreach ($item in $props) {
        $value = Get-Prop $item.Name
        if (-not $value) { $value = '—' }
        Write-Log ("  {0}: {1}" -f $item.Label, $value)
    }

    $pm = Invoke-Adb -Arguments @('shell', 'pm', 'path', 'com.aerocell.stand')
    $installed = $pm.Text -match 'package:'
    Write-Log ("  Приложение: " + $(if ($installed) { 'установлено' } else { 'не установлено' }))
    if ($installed) {
        $package = (Invoke-Adb -Arguments @('shell', 'dumpsys', 'package', 'com.aerocell.stand')).Text
        $versionCode = '?'
        $versionName = '?'
        if ($package -match 'versionCode=(\d+)') { $versionCode = $Matches[1] }
        if ($package -match 'versionName=(\S+)') { $versionName = $Matches[1] }
        Write-Log "  Версия: $versionName ($versionCode)"
    }

    $accountDump = (Invoke-Adb -Arguments @('shell', 'dumpsys', 'account')).Text
    $accountLines = @($accountDump -split "`n" | Where-Object { $_ -match 'Accounts:' })
    if ($accountLines.Count -eq 0) {
        Write-Log '  Аккаунты: не удалось прочитать'
    } else {
        Write-Log ('  Аккаунты: ' + (($accountLines | ForEach-Object { $_.Trim() }) -join '; '))
    }

    $policyDump = (Invoke-Adb -Arguments @('shell', 'dumpsys', 'device_policy')).Text
    $ownerLines = @($policyDump -split "`n" | Where-Object { $_ -match 'Device Owner' })
    if ($ownerLines.Count -eq 0) {
        Write-Log '  Владелец устройства: не назначен'
    } else {
        Write-Log ('  Владелец устройства: ' + (($ownerLines | ForEach-Object { $_.Trim() }) -join '; '))
    }
    Write-Log '--- Конец диагностики ---'
}

function Get-ApkPath {
    param([string]$Explicit)

    if ($Explicit) {
        if (-not (Test-Path -LiteralPath $Explicit)) {
            Fail "APK не найден: $Explicit" 'Укажите полный путь к файлу или соберите сборку: gradlew.bat assembleRelease'
        }
        return (Resolve-Path -LiteralPath $Explicit).Path
    }
    $all = @(Get-ChildItem -LiteralPath $Root -Filter 'AEROCELL-*.apk' -File -ErrorAction SilentlyContinue)
    $release = @($all | Where-Object { $_.Name -notmatch 'debug' })
    $pool = if ($release.Count -gt 0) { $release } else { $all }
    if ($pool.Count -eq 0) {
        Fail 'В корне проекта нет файла AEROCELL-*.apk.' 'Соберите release-сборку (gradlew.bat assembleRelease) или укажите путь: provision_tablet.bat путь\к\файлу.apk'
    }
    return ($pool | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}

function Test-HasAccounts {
    param([string]$Dump)

    $counts = [regex]::Matches($Dump, '(?m)^\s*Accounts:\s*(\d+)')
    foreach ($match in $counts) {
        if ([int]$match.Groups[1].Value -gt 0) { return $true }
    }
    if ([regex]::IsMatch($Dump, 'Account \{[^}]+\}')) { return $true }
    return $false
}

function Get-InstallHint {
    param([string]$Output)

    if ($Output -match 'INSTALL_FAILED_USER_RESTRICTED') {
        return 'На Xiaomi/HyperOS откройте «Для разработчиков» и включите «Установка через USB» и «Отладка по USB (параметры безопасности)». Если переключатели просят Mi-аккаунт: войдите в него, включите их, сразу выйдите из аккаунта и запустите скрипт снова.'
    }
    if ($Output -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match') {
        return 'На планшете уже стоит версия приложения с другой подписью. Удалите её командой: adb uninstall com.aerocell.stand — и запустите скрипт снова. Все настройки приложения при этом сбросятся.'
    }
    if ($Output -match 'INSTALL_FAILED_VERSION_DOWNGRADE|downgrade') {
        return 'APK старее уже установленной версии. Соберите свежую: gradlew.bat assembleRelease — и запустите скрипт снова.'
    }
    if ($Output -match 'INSTALL_PARSE_FAILED|failed to stat|No such file') {
        return 'APK не читается или повреждён. Проверьте, что файл собран целиком и путь к нему верный.'
    }
    return 'Полный вывод adb сохранён в логе рядом со скриптом.'
}

# --- Основной сценарий ---

Write-Log 'AEROCELL: настройка планшета стенда'
Write-Log ("Время: " + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
Write-Log ("Компьютер: " + $env:COMPUTERNAME)

$adbPath = Get-AdbPath
if (-not $adbPath) {
    Fail 'Не найден adb.' 'Установите Android platform-tools, добавьте их в PATH или задайте переменную ANDROID_HOME.'
}
$script:Adb = $adbPath
Write-Log "adb: $adbPath"

$adbVersion = Invoke-Adb -Arguments @('version')
Write-Log ('adb: ' + (($adbVersion.Lines | Select-Object -First 1) -join ''))
Start-AdbServer

if ($Diagnose -and -not $PSBoundParameters.ContainsKey('WaitSeconds')) {
    $WaitSeconds = 15
}

$device = Wait-ForDevice -Seconds $WaitSeconds
Write-Log "Планшет подключён: $($device.Serial)"
Write-Log ("Модель: " + (Get-Prop 'ro.product.manufacturer') + ' ' + (Get-Prop 'ro.product.model'))
Write-Log ""

if ($Diagnose) {
    Show-Diagnostics -Device $device
    Write-Log ''
    Write-Log "Готово. Лог: $LogPath"
    exit 0
}

$accountDump = (Invoke-Adb -Arguments @('shell', 'dumpsys', 'account')).Text
if (Test-HasAccounts $accountDump) {
    $accountLines = @($accountDump -split "`n" | Where-Object { $_ -match 'Accounts:' })
    if ($accountLines.Count -gt 0) {
        Write-Log ('Аккаунты на планшете: ' + (($accountLines | ForEach-Object { $_.Trim() }) -join '; '))
    }
    Fail 'На планшете есть аккаунт (Google, Xiaomi или другой). С ним режим киоска не включится.' @'
Выйдите из всех аккаунтов в настройках планшета или сделайте сброс до заводских и не входите ни в Google, ни в Mi-аккаунт.

На HyperOS, чтобы включить «Установка через USB», иногда сначала нужно войти в Mi-аккаунт, включить переключатели и сразу выйти. К моменту запуска скрипта аккаунтов быть не должно.
'@
}

$apk = Get-ApkPath $ApkPath
Write-Log "APK: $apk"

$apkHash = try { (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLower() } catch { '?' }
Write-Log "SHA256: $apkHash"

# Копируем APK во временную папку с простым путём: adb надёжнее читает файлы без
# кириллицы и пробелов в пути.
$stageDir = $StageDir
New-Item -ItemType Directory -Force -Path $stageDir | Out-Null
$stagedApk = Join-Path $stageDir 'AEROCELL.apk'
Copy-Item -LiteralPath $apk -Destination $stagedApk -Force
Write-Log "Ставлю приложение: $stagedApk"

$install = Invoke-Adb -Arguments @('install', '-r', $stagedApk)
foreach ($line in $install.Lines) { Write-Log "  $line" }
if ($install.ExitCode -ne 0 -or $install.Text -match 'Failure|INSTALL_FAILED|INSTALL_PARSE_FAILED') {
    Fail 'Не удалось установить APK.' (Get-InstallHint $install.Text)
}

$pmPath = Invoke-Adb -Arguments @('shell', 'pm', 'path', 'com.aerocell.stand')
if ($pmPath.Text -notmatch 'package:') {
    Fail 'APK установился, но пакет com.aerocell.stand на планшете не найден.' 'Проверьте, что ставите release-сборку (пакет com.aerocell.stand), а не debug (com.aerocell.stand.debug).'
}
$packageDump = (Invoke-Adb -Arguments @('shell', 'dumpsys', 'package', 'com.aerocell.stand')).Text
if ($packageDump -match 'versionName=(\S+)') { $installedVersion = $Matches[1] } else { $installedVersion = '?' }
Write-Log "Установлено: $installedVersion"
Write-Log ''

$ownerComponent = 'com.aerocell.stand/.kiosk.StandAdminReceiver'
$ownerSet = $false
$policyDump = (Invoke-Adb -Arguments @('shell', 'dumpsys', 'device_policy')).Text
if ($policyDump -match 'Device Owner:[^\r\n]*com\.aerocell\.stand') { $ownerSet = $true }

if ($ownerSet) {
    Write-Log 'Режим киоска уже включён для этого приложения.'
} else {
    Write-Log 'Включаю режим киоска...'
    $dpm = Invoke-Adb -Arguments @('shell', 'dpm', 'set-device-owner', $ownerComponent)
    foreach ($line in $dpm.Lines) { Write-Log "  $line" }

    if ($dpm.Text -match 'Success') {
        $ownerSet = $true
    } else {
        $policyDump = (Invoke-Adb -Arguments @('shell', 'dumpsys', 'device_policy')).Text
        if ($policyDump -match 'Device Owner:[^\r\n]*com\.aerocell\.stand') { $ownerSet = $true }
    }

    if (-not $ownerSet) {
        $hint = 'Полный вывод dpm сохранён в логе рядом со скриптом.'
        if ($dpm.Text -match 'account') {
            $hint = 'Похоже, на планшете остался аккаунт. Выйдите из всех аккаунтов или сделайте сброс до заводских и повторите шаги настройки.'
        } elseif ($dpm.Text -match 'provision|not allowed|already') {
            $hint = 'Планшет уже прошёл первичную настройку или на нём остались пользователи. Нужен сброс до заводских: «Настройки» → «О планшете» → «Сброс» → «Стереть все данные», затем повторить настройку.'
        }
        Fail 'Не удалось включить режим киоска.' $hint
    }
}

Write-Log ''
Write-Log 'Запускаю приложение...'
$start = Invoke-Adb -Arguments @('shell', 'am', 'start', '-W', '-n', 'com.aerocell.stand/.MainActivity')
foreach ($line in $start.Lines) { Write-Log "  $line" }
if ($start.Text -match 'Error|Exception') {
    Fail 'Приложение установлено, но не запустилось.' 'Откройте приложение на планшете вручную и проверьте, что оно не падает.'
}

Write-Log ''
Write-Log 'Готово.'
Write-Log "Планшет: $($device.Serial)"
Write-Log "Версия приложения: $installedVersion"
Write-Log 'Режим киоска включён, стенд запущен, обновления будут ставиться сами в простое.'
Write-Log "Лог: $LogPath"
