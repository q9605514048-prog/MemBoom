param([string]$Device='emulator-5554',[string]$Adb="$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe",[string]$QaMasterPassword='QaPhone!123',[string]$Package='local.accountvault.android.qa')
$ErrorActionPreference='Stop'
$qaRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaOutput=Join-Path $qaRoot 'artifacts/android/qa'
New-Item -ItemType Directory -Force -Path $qaOutput | Out-Null
$qaTitle='QA-Note-'+(Get-Date -Format HHmmss)
function Invoke-QaAdb { & $Adb -s $Device @args; if($LASTEXITCODE -ne 0){throw "adb failed: $($args[0])"} }
function Read-QaUi {
    for($attempt=0;$attempt -lt 8;$attempt++){
        $dump=(& $Adb -s $Device shell uiautomator dump /sdcard/memboom-notes-qa.xml 2>$null)-join "`n"
        if($dump -notmatch 'dumped to:'){continue}
        $raw=(& $Adb -s $Device shell cat /sdcard/memboom-notes-qa.xml 2>$null)-join "`n"
        if($LASTEXITCODE -eq 0 -and $raw.Contains('<hierarchy')){return [xml]$raw}
    }
    throw 'Emulator UI hierarchy unavailable'
}
function Tap-QaElement($node){$xy=[regex]::Matches($node.bounds,'\d+')|ForEach-Object{[int]$_.Value};Invoke-QaAdb shell input tap ([int](($xy[0]+$xy[2])/2)) ([int](($xy[1]+$xy[3])/2))}
function Tap-QaNode($text,$class='android.widget.Button'){
    for($attempt=0;$attempt -lt 6;$attempt++){
        $ui=Read-QaUi;$nodes=@($ui.SelectNodes('//node')|Where-Object{$_.class -eq $class -and ($_.text -eq $text -or $_.hint -eq $text)})
        if($nodes.Count -eq 1){Tap-QaElement $nodes[0];return}
    }
    throw "Control not found: $text"
}
function Fill-QaField($hint,$value){
    Tap-QaNode $hint 'android.widget.EditText'
    $null=Read-QaUi
    for($attempt=0;$attempt -lt 4;$attempt++){
        Invoke-QaAdb shell input keycombination 113 29
        Invoke-QaAdb shell input text $value
        $ui=Read-QaUi;$field=@($ui.SelectNodes('//node')|Where-Object{$_.class -eq 'android.widget.EditText' -and $_.hint -eq $hint})
        if($field.Count -eq 1 -and $field[0].text -eq $value){Invoke-QaAdb shell input keyevent 4;return}
    }
    throw "Synthetic text was not entered completely: $hint"
}
function Assert-QaText($text){for($attempt=0;$attempt -lt 5;$attempt++){if(@((Read-QaUi).SelectNodes('//node')|Where-Object{$_.text -eq $text}).Count){return}};throw "Missing text: $text"}
function Unlock-Qa {
    $ui=Read-QaUi;$setup=@($ui.SelectNodes('//node')|Where-Object{$_.hint -eq 'Повторите мастер-пароль'}).Count -gt 0
    Fill-QaField 'Мастер-пароль' $QaMasterPassword
    if($setup){Fill-QaField 'Повторите мастер-пароль' $QaMasterPassword;Tap-QaNode 'Создать и открыть'}else{Tap-QaNode 'Открыть хранилище'}
    Assert-QaText 'Ваши аккаунты'
}
Invoke-QaAdb shell am force-stop $Package
Write-Output 'Notes QA: unlock test profile'
Invoke-QaAdb shell am start -n "$Package/local.accountvault.android.features.security.UnlockActivity" | Out-Null
Unlock-Qa
Tap-QaNode 'Заметки'
Tap-QaNode 'Новая заметка'
Write-Output 'Notes QA: create and pin synthetic note'
Fill-QaField 'Название заметки' $qaTitle
Fill-QaField 'Текст заметки' 'QA-note-content-123'
Tap-QaNode 'Закрепить заметку' 'android.widget.CheckBox'
Tap-QaNode 'Сохранить заметку'
Assert-QaText ('★  '+$qaTitle)
Write-Output 'Notes QA: note saved; restarting process'
Invoke-QaAdb shell am force-stop $Package
Invoke-QaAdb shell am start -n "$Package/local.accountvault.android.features.security.UnlockActivity" | Out-Null
Unlock-Qa
Tap-QaNode 'Заметки'
Fill-QaField 'Поиск заметок' 'QA-note-content-123'
Write-Output 'Notes QA: verifying persisted note and search'
Assert-QaText ('★  '+$qaTitle)
Tap-QaNode 'Открепить'
Assert-QaText $qaTitle
Tap-QaNode 'Изменить'
Tap-QaNode 'Название заметки' 'android.widget.EditText'
Invoke-QaAdb shell input keyevent 123
Invoke-QaAdb shell input text '-edited'
Invoke-QaAdb shell input keyevent 4
Tap-QaNode 'Сохранить заметку'
Assert-QaText ($qaTitle+'-edited')
Write-Output 'Notes QA: edit and unpin verified'
Invoke-QaAdb shell screencap -p /sdcard/memboom-notes-qa.png
Invoke-QaAdb pull /sdcard/memboom-notes-qa.png (Join-Path $qaOutput 'notes-preview.png') | Out-Null
# Search isolates the uniquely named QA note. Existing notes are never removed.
$ui=Read-QaUi;$title=@($ui.SelectNodes('//node')|Where-Object{$_.text -eq ($qaTitle+'-edited')})
if($title.Count -ne 1){throw 'Cannot identify the synthetic note'}
$card=$title[0].ParentNode
$delete=@($card.SelectNodes('.//node')|Where-Object{$_.class -eq 'android.widget.Button' -and $_.text -eq 'Удалить'})
if($delete.Count -ne 1){throw 'Cannot identify QA note delete button'}
Tap-QaElement $delete[0]
$ui=Read-QaUi;$confirm=@($ui.SelectNodes('//node')|Where-Object{$_.'resource-id' -eq 'android:id/button1' -and $_.text -eq 'Удалить'})
if($confirm.Count -ne 1){throw 'QA deletion confirmation missing'};Tap-QaElement $confirm[0]
$ui=Read-QaUi
if(@($ui.SelectNodes('//node')|Where-Object{$_.text -eq ($qaTitle+'-edited')}).Count){throw 'QA note was not removed'}
Write-Output 'NOTES QA PASSED: create, pin, restart, search, edit, unpin, delete; only synthetic note removed'
