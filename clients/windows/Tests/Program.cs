using MemBoom.Core;

var passed=0;
var failed=0;
void Check(string name,Action action){try{action();Console.WriteLine("PASS "+name);passed++;}catch(Exception e){Console.WriteLine("FAIL "+name+": "+e.GetType().Name);failed++;}}
void Assert(bool condition){if(!condition)throw new Exception("Assertion failed");}
void Reject(Action action){try{action();}catch{return;}throw new Exception("Expected rejection");}
if(args.Contains("--auth-integration")){AccountAuthChecks.Integration(Console.In.ReadToEnd(),Check);return failed==0?0:1;}
VaultDocument Sample()=>new(){Accounts=[new(){Id="qa-account",Category="Игры",Name="Desktop QA",Fields=[new("Логин","qa@example.test"),new("Пароль","test-only-value")]}]};
const string password="QaBackup!123";
Check("password policy",()=>{Assert(PortableCrypto.ValidPassword("a1!b"));Assert(!PortableCrypto.ValidPassword("abc"));Assert(!PortableCrypto.ValidPassword("a b!"));Assert(!PortableCrypto.ValidPassword("пароль"));});
Check("generator groups",()=>{var p=PortableCrypto.GeneratePassword();Assert(p.Length==20&&p.Any(char.IsUpper)&&p.Any(char.IsLower)&&p.Any(char.IsDigit)&&p.Any(c=>!char.IsLetterOrDigit(c)));});
Check("encrypted backup roundtrip",()=>{var json=Sample().ToPortableJson();var encrypted=PortableCrypto.Encrypt(json,password);Assert(!encrypted.Contains("test-only-value"));Assert(VaultDocument.FromPortableJson(PortableCrypto.Decrypt(encrypted,password)).Accounts.Single().Fields.Last().Value=="test-only-value");});
Check("wrong password and tampering rejected",()=>{var e=PortableCrypto.Encrypt(Sample().ToPortableJson(),password);Reject(()=>PortableCrypto.Decrypt(e,"wrong!"));Reject(()=>PortableCrypto.Decrypt(e.Replace("ciphertext","broken"),password));});
Check("legacy category root",()=>{var array=Sample().ToPortableJson();Assert(VaultDocument.FromPortableJson("{\"categories\":"+array+"}").Accounts.Count==1);});
Check("import preserves both versions and is idempotent",()=>{var local=Sample();var incoming=Sample();incoming.Accounts[0].Fields=[new("Пароль","different")];Assert(local.Merge(incoming)==1);Assert(local.Accounts.Count==2);Assert(local.Merge(incoming)==0);Assert(local.Accounts.Select(a=>a.Id).Distinct().Count()==2);});
Check("invalid model rejected",()=>{Reject(()=>VaultDocument.FromPortableJson("{}"));Reject(()=>VaultDocument.FromPortableJson("[{\"name\":\"Игры\",\"accounts\":[{\"id\":\"x\",\"name\":\"\",\"fields\":[]}]}]"));});
var dir=Path.Combine(Path.GetTempPath(),"MemBoom-qa-"+Guid.NewGuid().ToString("N"));
Directory.CreateDirectory(dir);
try{
    var file=Path.Combine(dir,"vault.json");
    Check("local save and locked session",()=>{var s=new VaultSession(file);s.Unlock(password);s.Apply(d=>d.Accounts.Add(Sample().Accounts[0]));s.Lock();Assert(!s.IsUnlocked&&s.Document.Accounts.Count==0);var reopened=new VaultSession(file);Reject(()=>reopened.Unlock("wrong!"));reopened.Unlock(password);Assert(reopened.Document.Accounts.Count==1);Assert(!File.ReadAllText(file).Contains("test-only-value"));});
    Check("failed mutations roll back",()=>{var s=new VaultSession(file);s.Unlock(password);Reject(()=>s.Apply(d=>{d.Accounts.Clear();throw new Exception();}));Assert(s.Document.Accounts.Count==1);});
    Check("master password change",()=>{var s=new VaultSession(file);s.Unlock(password);s.ChangePassword(password,"NewPassword!1");s.Lock();Reject(()=>s.Unlock(password));s.Unlock("NewPassword!1");Assert(s.Document.Accounts.Count==1);});
    Check("encrypted previous version retained",()=>{Assert(File.Exists(file+".previous"));Assert(!File.ReadAllText(file+".previous").Contains("test-only-value"));});
}finally{Directory.Delete(dir,true);}
var androidIndex=Array.IndexOf(args,"--android-backup");
if(androidIndex>=0)Check("actual Android export decrypts on Windows",()=>{var json=File.ReadAllText(args[androidIndex+1]);Assert(VaultDocument.FromPortableJson(PortableCrypto.Decrypt(json,password)).Accounts.Count>=0);});
var outputIndex=Array.IndexOf(args,"--output");
SyncChecks.Run(Check);
ConnectionChecks.Run(Check);
DeviceChecks.Run(Check);
NoteChecks.Run(Check);
AccountAuthChecks.Run(Check);
var syncIndex=Array.IndexOf(args,"--sync-server");
if(syncIndex>=0)SyncChecks.Integration(args[syncIndex+1],"qa-integration-token-123456",Check);
if(syncIndex>=0)DeviceChecks.Integration(args[syncIndex+1],"qa-integration-token-123456",Check);
if(outputIndex>=0){
    Directory.CreateDirectory(args[outputIndex+1]);File.WriteAllText(Path.Combine(args[outputIndex+1],"desktop-backup.json"),PortableCrypto.Encrypt(Sample().ToPortableJson(),password));
    var notesSample=Sample();notesSample.Notes.Add(new(){Id="qa-note-interop",Title="Общая заметка",Content="Windows → Android → сайт\nВторая строка",Pinned=true});
    File.WriteAllText(Path.Combine(args[outputIndex+1],"desktop-notes-backup.json"),PortableCrypto.Encrypt(notesSample.ToBackupJson(),password));
}
Console.WriteLine($"Results: {passed} passed, {failed} failed");
return failed==0?0:1;
