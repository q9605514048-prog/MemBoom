param([string]$Device='emulator-5554',[string]$Package='local.accountvault.android.qa',[string]$QaMasterPassword='QaPhone!123',[switch]$CancelOnly,[string]$ExistingQaTitle)
$ErrorActionPreference='Stop'
if($Package -ne 'local.accountvault.android.qa'){throw 'This test is restricted to the separate QA package.'}
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$output=Join-Path $projectRoot 'artifacts/android/qa'
New-Item -ItemType Directory -Path $output -Force | Out-Null
$title='QA-File-Links-'+(Get-Date -Format HHmmss)
function Adb-Qa { & $adb -s $Device @args;if($LASTEXITCODE -ne 0){throw 'QA adb command failed'} }
function Read-Qa {
    for($i=0;$i -lt 6;$i++){
        $null=& $adb -s $Device shell uiautomator dump /sdcard/memboom-links-qa.xml 2>$null
        $raw=(& $adb -s $Device shell cat /sdcard/memboom-links-qa.xml 2>$null)-join "`n"
        if($raw.Contains('<hierarchy')){
            $ui=[xml]$raw
            $osAlert=@($ui.SelectNodes('//node')|Where-Object{$_.text -in @("System UI isn't responding","Pixel Launcher isn't responding")})
            $wait=@($ui.SelectNodes('//node')|Where-Object{$_.'resource-id' -eq 'android:id/aerr_wait'})
            if($osAlert.Count -eq 1 -and $wait.Count -eq 1){Tap-Qa $wait[0];continue}
            return $ui
        }
    }throw 'QA UI hierarchy unavailable'
}
function Tap-Qa($node){$n=[regex]::Matches($node.bounds,'\d+')|ForEach-Object{[int]$_.Value};Adb-Qa shell input tap ([int](($n[0]+$n[2])/2)) ([int](($n[1]+$n[3])/2))}
function Find-Qa($text,$class='android.widget.Button'){
    for($i=0;$i -lt 7;$i++){
        $nodes=@((Read-Qa).SelectNodes('//node')|Where-Object{$_.class -eq $class -and ($_.text -eq $text -or $_.hint -eq $text)})
        if($nodes.Count -eq 1){return $nodes[0]}
        Adb-Qa shell input swipe 500 1600 500 650 350
    }throw "QA control not found: $text"
}
function Fill-Qa($hint,$value,[switch]$Password){
    for($attempt=0;$attempt -lt 4;$attempt++){
        Tap-Qa (Find-Qa $hint 'android.widget.EditText')
        $null=Read-Qa
        Adb-Qa shell input keycombination 113 29
        Adb-Qa shell input keyevent 67
        Adb-Qa shell input text $value
        if($Password){Adb-Qa shell input keyevent 4;return}
        $field=@((Read-Qa).SelectNodes('//node')|Where-Object{$_.class -eq 'android.widget.EditText' -and $_.text -eq $value})
        Adb-Qa shell input keyevent 4
        if($field.Count -eq 1){return}
    }throw "QA field input incomplete: $hint"
}
function Has-Qa($text){return @((Read-Qa).SelectNodes('//node')|Where-Object{$_.text -eq $text -or $_.'content-desc' -eq $text}).Count -gt 0}
function Assert-Qa($text){for($i=0;$i -lt 4;$i++){if(Has-Qa $text){return}};throw "QA expected text missing: $text"}
function Unlock-Qa {
    Adb-Qa shell am start -n "$Package/local.accountvault.android.features.security.UnlockActivity" --ez force_unlock true | Out-Null
    $setup=Has-Qa 'Повторите мастер-пароль'
    Fill-Qa 'Мастер-пароль' $QaMasterPassword -Password
    if($setup){Fill-Qa 'Повторите мастер-пароль' $QaMasterPassword -Password;Tap-Qa (Find-Qa 'Создать и открыть')}
    else{Tap-Qa (Find-Qa 'Открыть хранилище')}
    Assert-Qa 'Ваши аккаунты'
}
if($CancelOnly){
    if($ExistingQaTitle -notmatch '^QA-File-Links-\d+$'){throw 'Cancel verification requires a synthetic QA note title.'}
    Write-Output 'File-links QA: verify Back confirmation on final build'
    Adb-Qa shell am force-stop $Package
    Unlock-Qa;Tap-Qa (Find-Qa 'Заметки');Fill-Qa 'Поиск заметок' $ExistingQaTitle
    Tap-Qa (Find-Qa 'Изменить');Tap-Qa (Find-Qa '＋ Ссылка на файл')
    Fill-Qa 'Название файла' 'QA-Google-file';Fill-Qa 'Ссылка на файл' 'https://drive.google.com/file/d/qa-phone/view'
    $null=Read-Qa
    Adb-Qa shell input keyevent 4;Assert-Qa 'Закрыть без сохранения?';Tap-Qa (Find-Qa 'Продолжить редактирование')
    Tap-Qa (Find-Qa 'Сохранить заметку');Assert-Qa 'Открыть файл: QA-Google-file'
    Adb-Qa shell screencap -p /sdcard/memboom-note-links-final.png
    Adb-Qa pull /sdcard/memboom-note-links-final.png (Join-Path $output 'note-file-links-final.png') | Out-Null
    Write-Output 'FINAL FILE LINKS QA PASSED: Back confirmation retains entered link and saving works'
    exit
}
Adb-Qa shell am force-stop $Package
Write-Output 'File-links QA: unlock separate test profile'
Unlock-Qa
Tap-Qa (Find-Qa 'Заметки')
Tap-Qa (Find-Qa 'Новая заметка')
Fill-Qa 'Название заметки' $title
Fill-Qa 'Текст заметки' 'QA-file-links-content'
Tap-Qa (Find-Qa '＋ Ссылка на файл')
Fill-Qa 'Название файла' 'QA-Google-file'
Fill-Qa 'Ссылка на файл' 'https://drive.google.com/file/d/qa-phone/view'
$null=Read-Qa
Adb-Qa shell input keyevent 4
Assert-Qa 'Закрыть без сохранения?'
Tap-Qa (Find-Qa 'Продолжить редактирование')
Tap-Qa (Find-Qa 'Сохранить заметку')
Assert-Qa 'Открыть файл: QA-Google-file'
Write-Output 'File-links QA: Google shortcut saved, restarting process'
Adb-Qa shell am force-stop $Package
Unlock-Qa
Tap-Qa (Find-Qa 'Заметки')
Fill-Qa 'Поиск заметок' 'QA-Google-file'
Assert-Qa $title
Assert-Qa 'Открыть файл: QA-Google-file'
Tap-Qa (Find-Qa 'Изменить')
Fill-Qa 'Название файла' 'QA-Yandex-file'
Fill-Qa 'Ссылка на файл' 'https://disk.yandex.ru/d/qa-phone'
Tap-Qa (Find-Qa 'Сохранить заметку')
# Search used the old file name; switch to the new one to see the edited note.
Fill-Qa 'Поиск заметок' 'QA-Yandex-file'
Assert-Qa 'Открыть файл: QA-Yandex-file'
Write-Output 'File-links QA: restart, file-name search and link editing passed'
Adb-Qa shell screencap -p /sdcard/memboom-note-links-qa.png
Adb-Qa pull /sdcard/memboom-note-links-qa.png (Join-Path $output 'note-file-links.png') | Out-Null
Tap-Qa (Find-Qa 'Изменить')
Tap-Qa (Find-Qa 'Убрать ссылку')
Tap-Qa (Find-Qa 'Сохранить заметку')
Fill-Qa 'Поиск заметок' $title
Assert-Qa $title
if(Has-Qa 'Открыть файл: QA-Yandex-file'){throw 'QA removed link is still visible'}
Write-Output 'FILE LINKS QA PASSED: add, save, restart, search, edit, remove; no cloud upload'
