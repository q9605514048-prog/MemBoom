param([string]$Device='emulator-5554',[string]$Adb="$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe",[switch]$Prepare)
$ErrorActionPreference='Stop'
$qaRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
function Adb-Qa { & $Adb -s $Device @args;if($LASTEXITCODE -ne 0){throw "adb failed: $($args[0])"} }
function Read-Qa {
    for($attempt=0;$attempt -lt 6;$attempt++){
        $null=& $Adb -s $Device shell uiautomator dump /sdcard/memboom-visual-qa.xml 2>$null
        $raw=(& $Adb -s $Device shell cat /sdcard/memboom-visual-qa.xml 2>$null)-join "`n"
        if($raw.Contains('<hierarchy')){return [xml]$raw}
    };throw 'UI unavailable'
}
function Tap-Node($node){$bounds=[regex]::Matches($node.bounds,'\d+')|ForEach-Object{[int]$_.Value};Adb-Qa shell input tap ([int](($bounds[0]+$bounds[2])/2)) ([int](($bounds[1]+$bounds[3])/2))}
function Tap-Qa($text,$class='android.widget.Button'){
    for($attempt=0;$attempt -lt 4;$attempt++){
        $nodes=@((Read-Qa).SelectNodes('//node')|Where-Object{$_.class -eq $class -and ($_.text -eq $text -or $_.hint -eq $text)})
        if($nodes.Count -eq 1){Tap-Node $nodes[0];$null=Read-Qa;return}
    };throw "Missing control: $text"
}
function Capture-Qa($name){
    $null=Read-Qa
    Adb-Qa shell screencap -p /sdcard/memboom-visual-qa.png
    Adb-Qa pull /sdcard/memboom-visual-qa.png (Join-Path $qaRoot "artifacts/android/qa/ui-0.8.0-$name.png") | Out-Null
    Write-Output "Captured: $name"
}
if($Prepare){
    Adb-Qa install -r (Join-Path $qaRoot 'clients/android/app/build/outputs/apk/qa/app-qa.apk')
    Adb-Qa shell am force-stop local.accountvault.android.qa
    Adb-Qa shell am start -S -n local.accountvault.android.qa/local.accountvault.android.features.security.UnlockActivity -f 0x10008000 --ez force_unlock true
    Tap-Qa 'Мастер-пароль' 'android.widget.EditText'
    Adb-Qa shell input text 'QaPhone!123';$null=Read-Qa;Adb-Qa shell input keyevent 4;$null=Read-Qa
    Tap-Qa 'Открыть хранилище'
}
$ui=Read-Qa
if(-not $ui.OuterXml.Contains('local.accountvault.android.qa')){throw 'Expected the isolated QA app'}
Tap-Qa 'Аккаунты';Capture-Qa 'accounts'
Tap-Qa 'Добавить';Capture-Qa 'add'
Tap-Qa 'Название аккаунта' 'android.widget.EditText';Capture-Qa 'keyboard'
Adb-Qa shell input keyevent 4;$null=Read-Qa
Tap-Qa 'Настройки';Capture-Qa 'settings'
for($attempt=0;$attempt -lt 5;$attempt++){
    $ui=Read-Qa
    $toggle=@($ui.SelectNodes('//node')|Where-Object{$_.class -eq 'android.widget.Button' -and $_.text -like 'Дополнительные настройки*'})
    if($toggle.Count -eq 1){Tap-Node $toggle[0];$null=Read-Qa;Capture-Qa 'advanced';break}
    Adb-Qa shell input swipe 540 1700 540 650 400
}
Tap-Qa 'Заметки';Capture-Qa 'notes'
Write-Output 'UI CAPTURE COMPLETE: navigation, forms, keyboard, settings and notes.'
