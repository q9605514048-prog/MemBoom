param([string]$Device='emulator-5554',[string]$Adb="$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe",[string]$ScreenshotName='import-0.7.2.png')
$ErrorActionPreference='Stop'
$qaRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
function Adb-Qa { & $Adb -s $Device @args; if($LASTEXITCODE -ne 0){throw "adb failed: $($args[0])"} }
function Read-Qa {
    for($attempt=0;$attempt -lt 8;$attempt++){
        $null=& $Adb -s $Device shell uiautomator dump /sdcard/memboom-import-qa.xml 2>$null
        $raw=(& $Adb -s $Device shell cat /sdcard/memboom-import-qa.xml 2>$null)-join "`n"
        if($raw.Contains('<hierarchy')){return [xml]$raw}
    };throw 'UI unavailable'
}
function Tap-Node($node){$bounds=[regex]::Matches($node.bounds,'\d+')|ForEach-Object{[int]$_.Value};Adb-Qa shell input tap ([int](($bounds[0]+$bounds[2])/2)) ([int](($bounds[1]+$bounds[3])/2))}
function Tap-Qa($text){
    for($attempt=0;$attempt -lt 6;$attempt++){
        $nodes=@((Read-Qa).SelectNodes('//node')|Where-Object{$_.text -eq $text -or $_.hint -eq $text})
        if($nodes.Count -eq 1){Tap-Node $nodes[0];return}
    };throw "Missing control: $text"
}
function Assert-Qa($pattern){for($attempt=0;$attempt -lt 6;$attempt++){if((Read-Qa).OuterXml -match $pattern){return}};throw "Expected UI: $pattern"}
function Fill-Qa($hint,$value){Tap-Qa $hint;Adb-Qa shell input text $value;$null=Read-Qa;Adb-Qa shell input keyevent 4;$null=Read-Qa}
Adb-Qa install -r (Join-Path $qaRoot 'clients/android/app/build/outputs/apk/qa/app-qa.apk')
Adb-Qa push (Join-Path $qaRoot 'clients/android/app/src/test/resources/windows-notes-v1.json') /sdcard/Download/MemBoom-QA-import.json
Adb-Qa shell am force-stop local.accountvault.android.qa
Adb-Qa shell am start -n local.accountvault.android.qa/local.accountvault.android.features.security.UnlockActivity --ez force_unlock true
$ui=Read-Qa
$setup=@($ui.SelectNodes('//node')|Where-Object{$_.hint -eq 'Повторите мастер-пароль'}).Count -gt 0
Fill-Qa 'Мастер-пароль' 'QaPhone!123'
if($setup){Fill-Qa 'Повторите мастер-пароль' 'QaPhone!123';Tap-Qa 'Создать и открыть'}else{Tap-Qa 'Открыть хранилище'}
Tap-Qa 'Настройки';Tap-Qa 'Импорт резервной копии'
Assert-Qa 'com.google.android.documentsui|com.android.documentsui'
Write-Output 'QA: Android document picker opened before password prompt.'
# Open the drawer and select Downloads in the system picker.
$ui=Read-Qa
$drawer=@($ui.SelectNodes('//node')|Where-Object{$_.'content-desc' -match 'Show roots|Показать корневые|Open navigation|Открыть панель'})
if($drawer.Count -eq 1){Tap-Node $drawer[0];$ui=Read-Qa}
$downloads=@($ui.SelectNodes('//node')|Where-Object{$_.text -in @('Downloads','Загрузки')})
if($downloads.Count){Tap-Node $downloads[-1]}
Tap-Qa 'MemBoom-QA-import.json'
Tap-Qa 'Пароль, заданный при экспорте на сайте'
Adb-Qa shell input text 'QaBackup!123';$null=Read-Qa;Adb-Qa shell input keyevent 4;$null=Read-Qa
Tap-Qa 'Открыть'
Assert-Qa 'В копии 1 аккаунтов и 1 заметок'
Tap-Qa 'Добавить записи'
Assert-Qa 'Всего:.*аккаунтов,.*заметок'
Tap-Qa 'Заметки';Assert-Qa 'Общая заметка'
Adb-Qa shell screencap -p /sdcard/memboom-import-qa.png
Adb-Qa pull /sdcard/memboom-import-qa.png (Join-Path $qaRoot "artifacts/android/qa/$ScreenshotName")
Write-Output 'IMPORT QA PASSED: document picker, password after file, account+note preview, import and visible note.'
