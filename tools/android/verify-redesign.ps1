param([string]$Device='emulator-5556',[string]$Adb="$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe")
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$qaOutput=Join-Path $taskRoot 'artifacts/redesign'
$steps=[Collections.Generic.List[string]]::new()
function Run-Adb { & $Adb -s $Device @args;if($LASTEXITCODE -ne 0){throw "adb failed at $($args[0])"} }
function Read-Qa {
 for($attempt=0;$attempt -lt 5;$attempt++){
  $dumpOutput=(& $Adb -s $Device shell uiautomator dump /sdcard/memboom-redesign.xml 2>$null)-join ' '
  if($LASTEXITCODE -ne 0 -or !$dumpOutput.Contains('dumped')){continue}
  $raw=(& $Adb -s $Device shell cat /sdcard/memboom-redesign.xml 2>$null)-join "`n"
  if($raw.Contains('<hierarchy')){
   $ui=[xml]$raw
   $os=@($ui.SelectNodes('//node')|Where-Object{$_.text -in @("System UI isn't responding","Pixel Launcher isn't responding")})
   $wait=@($ui.SelectNodes('//node')|Where-Object{$_.text -eq 'Wait'})
   if($os.Count -eq 1 -and $wait.Count -eq 1){Tap-Node $wait[0];continue}
   return $ui
  }
 };throw 'Android UI unavailable'
}
function Tap-Node($node){$b=[regex]::Matches($node.bounds,'\d+')|ForEach-Object{[int]$_.Value};Run-Adb shell input tap ([int](($b[0]+$b[2])/2)) ([int](($b[1]+$b[3])/2))}
function Find-Qa($label,$class=$null){
 for($attempt=0;$attempt -lt 8;$attempt++){
  $ui=Read-Qa
  $nodes=@($ui.SelectNodes('//node')|Where-Object{(!$class -or $_.class -eq $class)-and($_.text -eq $label -or $_.hint -eq $label -or $_.'content-desc' -eq $label)})
  if($nodes.Count -eq 1){return $nodes[0]}
  $scroll=@($ui.SelectNodes('//node')|Where-Object{$_.scrollable -eq 'true' -and $_.class -eq 'android.widget.ScrollView'})
  if($scroll.Count){$b=[regex]::Matches($scroll[0].bounds,'\d+')|ForEach-Object{[int]$_.Value};$x=[int](($b[0]+$b[2])/2);Run-Adb shell input swipe $x ([int]($b[3]-120)) $x ([int]($b[1]+120)) 400}
 };throw "Missing Android control: $label"
}
function Tap-Qa($label,$class=$null){Tap-Node (Find-Qa $label $class);$null=Read-Qa}
function Fill-Qa($label,$value){Tap-Qa $label 'android.widget.EditText';Run-Adb shell input text ($value.Replace(' ','%s'));$null=Read-Qa;Run-Adb shell input keyevent 4;$null=Read-Qa}
function Assert-Qa($label){$null=Find-Qa $label;$steps.Add($label);Write-Output "Verified: $label"}
function Capture-Qa($name){$null=Read-Qa;Run-Adb shell screencap -p /sdcard/memboom-redesign.png;Run-Adb pull /sdcard/memboom-redesign.png (Join-Path $qaOutput "android-$name.png")}
Run-Adb shell am start -S -n local.accountvault.android.qa/local.accountvault.android.features.security.UnlockActivity -f 0x10008000 --ez force_unlock true
$ui=Read-Qa
if(@($ui.SelectNodes('//node')|Where-Object{$_.text -eq 'Try without a server' -and $_.class -eq 'android.widget.Button'}).Count -eq 1){Capture-Qa 'login';Tap-Qa 'Try without a server' 'android.widget.Button'}
$setup=(Read-Qa).OuterXml.Contains('Confirm master password')
Capture-Qa 'unlock'
Fill-Qa 'Master password' 'QaPhone!123'
if($setup){Fill-Qa 'Confirm master password' 'QaPhone!123';Tap-Qa 'Create and open'}else{Tap-Qa 'Open vault'}
Assert-Qa 'Your accounts';Capture-Qa 'accounts-empty'
Tap-Qa 'Add' 'android.widget.Button'
Fill-Qa 'Category' 'QA Work';Fill-Qa 'Account name' 'Explosion Demo';Fill-Qa 'Email / username' 'demo@example.test';Fill-Qa 'Password' 'Synthetic!123'
Capture-Qa 'add-edit';Tap-Qa 'Add account' 'android.widget.Button'
Tap-Qa 'Accounts' 'android.widget.Button';Assert-Qa 'Explosion Demo';Capture-Qa 'accounts'
Tap-Qa 'Explosion Demo';Assert-Qa 'Account';Capture-Qa 'account-details'
Tap-Qa 'Edit account';Assert-Qa 'Edit account';Capture-Qa 'edit-account';Tap-Qa 'Back' 'android.widget.Button'
Tap-Qa 'Notes' 'android.widget.Button';Assert-Qa 'New note';Capture-Qa 'notes-empty';Tap-Qa 'New note' 'android.widget.Button'
Fill-Qa 'Note title' 'Weekend ideas';Fill-Qa 'Note text' 'Synthetic note for the redesign test. No private data.';Capture-Qa 'note-editor';Tap-Qa 'Save note' 'android.widget.Button'
Assert-Qa 'Weekend ideas';Capture-Qa 'notes';Tap-Qa 'Open note: Weekend ideas';Assert-Qa 'Note';Capture-Qa 'note-details';Tap-Qa 'Back' 'android.widget.Button'
Tap-Qa 'Settings' 'android.widget.Button';Assert-Qa 'Create backup';Capture-Qa 'settings-backup';Tap-Qa 'API connection' 'android.widget.Button';Assert-Qa 'Device token';Capture-Qa 'settings-api'
Tap-Qa 'Language / Язык: English';Tap-Qa 'Русский';Assert-Qa 'Резервные копии';Capture-Qa 'ru-settings'
Tap-Qa 'Аккаунты' 'android.widget.Button';Assert-Qa 'Explosion Demo';Capture-Qa 'ru-accounts'
$steps | ConvertTo-Json | Set-Content (Join-Path $qaOutput 'android-qa-results.json') -Encoding utf8
Write-Output 'ANDROID REDESIGN QA PASSED'
