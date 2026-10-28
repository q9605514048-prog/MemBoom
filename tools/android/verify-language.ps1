param([string]$Device='emulator-5554',[string]$Adb="$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe")
$ErrorActionPreference='Stop'
$qaRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
function Adb-Qa { & $Adb -s $Device @args;if($LASTEXITCODE -ne 0){throw "adb failed: $($args[0])"} }
function Read-Qa {
 for($attempt=0;$attempt -lt 8;$attempt++){
  $null=& $Adb -s $Device shell uiautomator dump /sdcard/memboom-language-qa.xml 2>$null
  $raw=(& $Adb -s $Device shell cat /sdcard/memboom-language-qa.xml 2>$null)-join "`n"
  if($raw.Contains('<hierarchy')){
   $parsed=[xml]$raw
   $os=@($parsed.SelectNodes('//node')|Where-Object{$_.text -in @("System UI isn't responding","Pixel Launcher isn't responding")})
   $wait=@($parsed.SelectNodes('//node')|Where-Object{$_.text -eq 'Wait'})
   if($os.Count -eq 1 -and $wait.Count -eq 1){Tap-Node $wait[0];continue}
   return $parsed
  }
 };throw 'UI unavailable'
}
function Tap-Node($node){$bounds=[regex]::Matches($node.bounds,'\d+')|ForEach-Object{[int]$_.Value};Adb-Qa shell input tap ([int](($bounds[0]+$bounds[2])/2)) ([int](($bounds[1]+$bounds[3])/2))}
function Tap-Qa($text,$class=$null){
 for($attempt=0;$attempt -lt 6;$attempt++){
  $nodes=@((Read-Qa).SelectNodes('//node')|Where-Object{(!$class -or $_.class -eq $class) -and ($_.text -eq $text -or $_.hint -eq $text)})
  if($nodes.Count -eq 1){Tap-Node $nodes[0];$null=Read-Qa;return}
 };throw "Missing control: $text"
}
function Assert-Qa($text){for($attempt=0;$attempt -lt 5;$attempt++){if((Read-Qa).OuterXml.Contains($text)){return}};throw "Expected UI: $text"}
function Fill-Qa($hint,$text){Tap-Qa $hint 'android.widget.EditText';Adb-Qa shell input text $text;$null=Read-Qa;Adb-Qa shell input keyevent 4;$null=Read-Qa}
Adb-Qa install -r (Join-Path $qaRoot 'clients/android/app/build/outputs/apk/qa/app-qa.apk')
Adb-Qa shell am start -S -n local.accountvault.android.qa/local.accountvault.android.features.security.UnlockActivity -f 0x10008000 --ez force_unlock true
$ui=Read-Qa
if(!$ui.OuterXml.Contains('Language / Язык: English')){Tap-Qa 'Language / Язык: Русский';Tap-Qa 'English'}
if((Read-Qa).OuterXml.Contains('Try without a server')){Tap-Qa 'Try without a server'}
$setup=(Read-Qa).OuterXml.Contains('Confirm master password')
Fill-Qa 'Master password' 'QaPhone!123'
if($setup){Fill-Qa 'Confirm master password' 'QaPhone!123';Tap-Qa 'Create and open'}else{Tap-Qa 'Open vault'}
Assert-Qa 'Your accounts'
Tap-Qa 'Settings' 'android.widget.Button';Tap-Qa 'Language / Язык: English';Tap-Qa 'Русский';Assert-Qa 'Импорт резервной копии'
Tap-Qa 'Language / Язык: Русский';Tap-Qa 'English';Assert-Qa 'Import backup'
Adb-Qa shell am start -S -n local.accountvault.android.qa/local.accountvault.android.features.security.UnlockActivity -f 0x10008000 --ez force_unlock true
Assert-Qa 'Language / Язык: English';Fill-Qa 'Master password' 'QaPhone!123';Tap-Qa 'Open vault';Assert-Qa 'Your accounts'
Tap-Qa 'Notes' 'android.widget.Button';Assert-Qa 'New note'
Adb-Qa shell screencap -p /sdcard/memboom-language-qa.png
Adb-Qa pull /sdcard/memboom-language-qa.png (Join-Path $qaRoot 'artifacts/android/qa/english-ui.png')
Write-Output 'LANGUAGE QA PASSED: English UI, Russian switch, English switch and persisted choice after process restart.'
