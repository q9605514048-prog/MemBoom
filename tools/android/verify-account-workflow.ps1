param(
    [string]$Device = 'emulator-5554',
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$QaMasterPassword = 'QaPhone!123'
)

$ErrorActionPreference = 'Stop'
$qaRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaOutput = Join-Path $qaRoot 'artifacts/android/qa'
New-Item -ItemType Directory -Force -Path $qaOutput | Out-Null
$qaCategory = 'QA-Games'
$qaAccount = 'QA-Offline-' + (Get-Date -Format HHmmss)

function Invoke-QaAdb {
    & $Adb -s $Device @args
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($args[0])" }
}

function Read-QaUi {
    Invoke-QaAdb shell uiautomator dump /sdcard/memboom-workflow.xml | Out-Null
    [xml]((Invoke-QaAdb shell cat /sdcard/memboom-workflow.xml) -join "`n")
}

function Tap-QaElement($qaNode) {
    $coordinates = [regex]::Matches($qaNode.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    Invoke-QaAdb shell input tap ([int](($coordinates[0] + $coordinates[2]) / 2)) ([int](($coordinates[1] + $coordinates[3]) / 2))
}

function Tap-QaNode($qaText, $qaClass = 'android.widget.Button') {
    $nodes=@()
    for($attempt=0;$attempt -lt 6;$attempt++){
        $ui = Read-QaUi
        $nodes = @($ui.SelectNodes('//node') | Where-Object { $_.class -eq $qaClass -and ($_.text -eq $qaText -or $_.hint -eq $qaText) })
        if($nodes.Count -eq 1){break}
        Invoke-QaAdb shell input swipe 540 1900 540 800 350
    }
    if ($nodes.Count -ne 1) { throw "Expected one control '$qaText', found $($nodes.Count)" }
    Tap-QaElement $nodes[0]
}

function Fill-QaField($qaHint, $qaValue) {
    Tap-QaNode $qaHint 'android.widget.EditText'
    Invoke-QaAdb shell input text $qaValue
    Invoke-QaAdb shell input keyevent 4
}

function Assert-QaText($qaText) {
    for($attempt=0;$attempt -lt 5;$attempt++){
        $ui = Read-QaUi
        if (@($ui.SelectNodes('//node') | Where-Object { $_.text -eq $qaText }).Count) { return }
    }
    throw "Missing UI text: $qaText"
}

function Unlock-QaVault {
    $ui=Read-QaUi
    $setup=@($ui.SelectNodes('//node') | Where-Object { $_.hint -eq 'Повторите мастер-пароль' }).Count -gt 0
    Fill-QaField 'Мастер-пароль' $QaMasterPassword
    if($setup){Fill-QaField 'Повторите мастер-пароль' $QaMasterPassword;Tap-QaNode 'Создать и открыть'}else{Tap-QaNode 'Открыть хранилище'}
    Assert-QaText 'Ваши аккаунты'
}

Write-Output 'QA: adding an account without a sync master password'
Invoke-QaAdb shell am force-stop local.accountvault.android
Invoke-QaAdb shell am start -n local.accountvault.android/.features.security.UnlockActivity | Out-Null
Unlock-QaVault
Tap-QaNode 'Добавить'
Fill-QaField 'Категория' $qaCategory
Fill-QaField 'Название аккаунта' $qaAccount
Fill-QaField 'Почта / логин' 'qa@example.test'
Tap-QaNode 'Сгенерировать пароль'
$ui = Read-QaUi
$generated = @($ui.SelectNodes('//node') | Where-Object { $_.class -eq 'android.widget.EditText' -and $_.hint -eq 'Пароль' })
if ($generated.Count -ne 1 -or $generated[0].text.Length -ne 20 -or $generated[0].password -ne 'true') { throw 'Generated password must have 20 characters and be masked' }
Write-Output 'QA: generated password is 20 characters and masked'

Tap-QaNode 'Показать пароль' 'android.widget.CheckBox'
$ui = Read-QaUi
if (-not @($ui.SelectNodes('//node') | Where-Object { $_.class -eq 'android.widget.CheckBox' -and $_.checked -eq 'true' }).Count) { throw 'Show password checkbox did not switch on' }
Tap-QaNode 'Показать пароль' 'android.widget.CheckBox'
Tap-QaNode 'Добавить аккаунт'
Assert-QaText "$qaCategory / $qaAccount"
Write-Output 'QA: account saved offline and shown immediately'

Invoke-QaAdb shell am force-stop local.accountvault.android
Invoke-QaAdb shell am start -n local.accountvault.android/.features.security.UnlockActivity | Out-Null
Unlock-QaVault
Assert-QaText "$qaCategory / $qaAccount"
Write-Output 'QA: encrypted account survived process restart'

Fill-QaField 'Поиск по категориям, аккаунтам и полям' 'no-such-qa-record'
Assert-QaText 'Ничего не найдено'
Tap-QaNode 'Сбросить поиск'
Assert-QaText "$qaCategory / $qaAccount"
Write-Output 'QA: search and reset work'

Tap-QaNode 'Настройки'
Assert-QaText 'Синхронизация'
$ui = Read-QaUi
$token = @($ui.SelectNodes('//node') | Where-Object { $_.class -eq 'android.widget.EditText' -and $_.hint -eq 'Токен устройства' })
for($attempt=0;$token.Count -eq 0 -and $attempt -lt 5;$attempt++){
    Invoke-QaAdb shell input swipe 540 1900 540 800 350
    $ui=Read-QaUi
    $token=@($ui.SelectNodes('//node') | Where-Object { $_.class -eq 'android.widget.EditText' -and $_.hint -eq 'Токен устройства' })
}
if ($token.Count -ne 1 -or $token[0].password -ne 'true') { throw 'Device token must be masked' }
Tap-QaNode 'Аккаунты'
Assert-QaText "$qaCategory / $qaAccount"
Invoke-QaAdb shell screencap -p /sdcard/memboom-workflow.png
Invoke-QaAdb pull /sdcard/memboom-workflow.png (Join-Path $qaOutput 'account-workflow.png') | Out-Null
Write-Output 'QA: all three sections work; screenshot saved'

# Only remove the uniquely named synthetic record created by this run.
$ui = Read-QaUi
$title = @($ui.SelectNodes('//node') | Where-Object { $_.text -eq "$qaCategory / $qaAccount" })
if ($title.Count -ne 1) { throw 'Cannot identify the synthetic record for cleanup' }
$delete = @($title[0].ParentNode.SelectNodes('node') | Where-Object { $_.class -eq 'android.widget.Button' -and $_.text -eq 'Удалить' })
if ($delete.Count -ne 1) { throw 'Cannot identify the synthetic record delete control' }
Tap-QaElement $delete[0]
$ui = Read-QaUi
$confirm = @($ui.SelectNodes('//node') | Where-Object { $_.'resource-id' -eq 'android:id/button1' -and $_.text -eq 'Удалить' })
if ($confirm.Count -ne 1) { throw 'Synthetic record deletion confirmation was not found' }
Tap-QaElement $confirm[0]
Write-Output 'QA PASSED: synthetic test record removed; existing records preserved'
