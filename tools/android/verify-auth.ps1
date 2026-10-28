param([string]$Device='emulator-5554',[string]$Adb="$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe",[string]$Package='local.accountvault.android.qa',[string]$Endpoint,[string]$Login='qa_browser',[string]$Password='QaBrowser!123',[string]$QaMasterPassword='QaPhone1234')
$ErrorActionPreference='Stop'
$qaRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if(-not $Endpoint){$qaServer=Get-Content (Join-Path $qaRoot 'artifacts/auth/qa/server.json') -Raw | ConvertFrom-Json;$Endpoint=$qaServer.endpoint.Replace('127.0.0.1','10.0.2.2')}
function Adb-Qa { & $Adb -s $Device @args;if($LASTEXITCODE -ne 0){throw "adb failed: $($args[0])"} }
function Read-Qa {
    for($attempt=0;$attempt -lt 8;$attempt++){
        $dump=(& $Adb -s $Device shell uiautomator dump /sdcard/memboom-auth-qa.xml 2>$null)-join "`n";if($dump -notmatch 'dumped to:'){continue}
        $raw=(& $Adb -s $Device shell cat /sdcard/memboom-auth-qa.xml 2>$null)-join "`n"
        if($raw.Contains('<hierarchy')){
            $parsed=[xml]$raw
            # API 37 can show an OS/launcher ANR during boot; never suppress an app ANR.
            $osAlert=@($parsed.SelectNodes('//node')|Where-Object{$_.text -in @("System UI isn't responding","Pixel Launcher isn't responding")})
            $wait=@($parsed.SelectNodes('//node')|Where-Object{$_.'resource-id' -eq 'android:id/aerr_wait'})
            if($osAlert.Count -eq 1 -and $wait.Count -eq 1){
                $xy=[regex]::Matches($wait[0].bounds,'\d+')|ForEach-Object{[int]$_.Value}
                Adb-Qa shell input tap ([int](($xy[0]+$xy[2])/2)) ([int](($xy[1]+$xy[3])/2));continue
            }
            return $parsed
        }
    };throw 'Emulator UI unavailable'
}
function Tap-Qa($text,$class='android.widget.Button'){
    for($attempt=0;$attempt -lt 8;$attempt++){
        $ui=Read-Qa;$nodes=@($ui.SelectNodes('//node')|Where-Object{$_.class -eq $class -and ($_.text -eq $text -or $_.hint -eq $text)})
        if($nodes.Count -eq 1){$xy=[regex]::Matches($nodes[0].bounds,'\d+')|ForEach-Object{[int]$_.Value};Adb-Qa shell input tap ([int](($xy[0]+$xy[2])/2)) ([int](($xy[1]+$xy[3])/2));return}
        if($attempt -ge 2){Adb-Qa shell input swipe 540 1850 540 850 350}
    };throw "Control missing: $text"
}
function Fill-Qa($hint,$value){
    Tap-Qa $hint 'android.widget.EditText';$null=Read-Qa
    Adb-Qa shell input keycombination 113 29;Adb-Qa shell input text $value
    for($attempt=0;$attempt -lt 4;$attempt++){
        $ui=Read-Qa;$node=@($ui.SelectNodes('//node')|Where-Object{$_.class -eq 'android.widget.EditText' -and $_.hint -eq $hint})
        if($node.Count -eq 1 -and $node[0].text -eq $value){Adb-Qa shell input keyevent 4;return}
        if($node.Count -eq 1 -and $value.StartsWith([string]$node[0].text) -and $node[0].text.Length -gt 0){Adb-Qa shell input text $value.Substring($node[0].text.Length)}
        else{Adb-Qa shell input keycombination 113 29;Adb-Qa shell input text $value}
    };throw "QA text incomplete: $hint"
}
function Assert-Qa($text){for($attempt=0;$attempt -lt 8;$attempt++){if(@((Read-Qa).SelectNodes('//node')|Where-Object{$_.text -eq $text}).Count){return}};throw "Missing text: $text"}
if($Package -ne 'local.accountvault.android.qa'){throw 'This startup test only accepts the isolated QA package'}
Write-Output 'Auth QA: first launch shows account choice'
Adb-Qa shell am force-stop $Package;Adb-Qa shell am start -n "$Package/local.accountvault.android.features.security.UnlockActivity" | Out-Null
Assert-Qa 'Добро пожаловать в MemBoom';Tap-Qa 'Создать аккаунт'
Write-Output 'Auth QA: register a synthetic account'
Fill-Qa 'Адрес сервера MemBoom' $Endpoint
Fill-Qa 'Логин' $Login
Fill-Qa 'Пароль' $Password
Fill-Qa 'Повторите пароль' $Password
Tap-Qa 'Создать аккаунт';Assert-Qa 'Ваши аккаунты'
Write-Output 'Auth QA: process restart must open the vault directly'
Adb-Qa shell am force-stop $Package;Adb-Qa shell am start -n "$Package/local.accountvault.android.features.security.UnlockActivity" | Out-Null
Assert-Qa 'Ваши аккаунты';Tap-Qa 'Настройки';Tap-Qa 'Вход / Регистрация';Assert-Qa ('Подключён аккаунт: '+$Login)
$qaOutput=Join-Path $qaRoot 'artifacts/android/qa';New-Item -ItemType Directory -Force -Path $qaOutput | Out-Null
Adb-Qa shell screencap -p /sdcard/memboom-auth-qa.png;Adb-Qa pull /sdcard/memboom-auth-qa.png (Join-Path $qaOutput 'auth-preview.png') | Out-Null
Tap-Qa 'Выйти из аккаунта';Assert-Qa 'Добро пожаловать в MemBoom'
Adb-Qa shell am force-stop $Package;Adb-Qa shell am start -n "$Package/local.accountvault.android.features.security.UnlockActivity" | Out-Null
Assert-Qa 'Добро пожаловать в MemBoom'
Write-Output 'AUTH QA PASSED: first-run choice, registration, automatic process restart, logout and choice after restart'
