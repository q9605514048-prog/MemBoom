param([string]$Device='emulator-5554',[string]$Adb="$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe")
$ErrorActionPreference='Stop'
$qaRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
function Adb-Qa { & $Adb -s $Device @args;if($LASTEXITCODE -ne 0){throw "adb failed: $($args[0])"} }
function Read-Qa {
 for($attempt=0;$attempt -lt 8;$attempt++){
  $null=& $Adb -s $Device shell uiautomator dump /sdcard/memboom-local-qa.xml 2>$null
  $raw=(& $Adb -s $Device shell cat /sdcard/memboom-local-qa.xml 2>$null)-join "`n"
  if($raw.Contains('<hierarchy')){return [xml]$raw}
 };throw 'UI unavailable'
}
function Tap-Qa($text,$class='android.widget.Button'){
 for($attempt=0;$attempt -lt 6;$attempt++){
  $nodes=@((Read-Qa).SelectNodes('//node')|Where-Object{$_.class -eq $class -and ($_.text -eq $text -or $_.hint -eq $text)})
  if($nodes.Count -eq 1){$bounds=[regex]::Matches($nodes[0].bounds,'\d+')|ForEach-Object{[int]$_.Value};Adb-Qa shell input tap ([int](($bounds[0]+$bounds[2])/2)) ([int](($bounds[1]+$bounds[3])/2));$null=Read-Qa;return}
 };throw "Missing control: $text"
}
function Assert-Qa($text){for($attempt=0;$attempt -lt 5;$attempt++){if((Read-Qa).OuterXml.Contains($text)){return}};throw "Expected UI: $text"}
function Fill-Qa($hint){Tap-Qa $hint 'android.widget.EditText';Adb-Qa shell input text 'QaPhone!123';$null=Read-Qa;Adb-Qa shell input keyevent 4;$null=Read-Qa}
Adb-Qa install -r (Join-Path $qaRoot 'clients/android/app/build/outputs/apk/qa/app-qa.apk')
Adb-Qa shell svc wifi disable
Adb-Qa shell svc data disable
Adb-Qa shell am start -S -n local.accountvault.android.qa/local.accountvault.android.features.security.UnlockActivity -f 0x10008000
Tap-Qa 'Попробовать без сервера'
$setup=(Read-Qa).OuterXml.Contains('Повторите мастер-пароль')
Fill-Qa 'Мастер-пароль'
if($setup){Fill-Qa 'Повторите мастер-пароль';Tap-Qa 'Создать и открыть'}else{Tap-Qa 'Открыть хранилище'}
Assert-Qa 'Ваши аккаунты'
Adb-Qa shell am start -S -n local.accountvault.android.qa/local.accountvault.android.features.security.UnlockActivity -f 0x10008000
Assert-Qa 'Мастер-пароль'
if((Read-Qa).OuterXml.Contains('Попробовать без сервера')){throw 'Local preference was not restored'}
Fill-Qa 'Мастер-пароль';Tap-Qa 'Открыть хранилище';Assert-Qa 'Ваши аккаунты'
Adb-Qa shell screencap -p /sdcard/memboom-public-qa.png
Adb-Qa pull /sdcard/memboom-public-qa.png (Join-Path $qaRoot 'artifacts/android/qa/public-local-mode.png')
Write-Output 'LOCAL MODE QA PASSED: no network, local unlock, process restart, local password and vault restored.'
