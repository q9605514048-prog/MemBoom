using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using MemBoom.Core;
using MemBoom.Core.Auth;

namespace MemBoom.Windows;

internal static class DeviceSignIn
{
    private sealed record Remembered(string UnlockSecret,string Token,string ExpiresAt,string Login);
    private static string PathFor(VaultSession vault)=>vault.FilePath+".device";
    public static void Save(VaultSession vault,AccountSession account)
    {
        vault.WithUnlockSecret(secret=>{
            var plaintext=Encoding.UTF8.GetBytes(JsonSerializer.Serialize(new Remembered(secret,account.Token,account.ExpiresAt,account.User.Login)));
            try{
                var encrypted=ProtectedData.Protect(plaintext,null,DataProtectionScope.CurrentUser);
                var path=PathFor(vault);File.WriteAllBytes(path+".tmp",encrypted);File.Move(path+".tmp",path,true);
            }finally{CryptographicOperations.ZeroMemory(plaintext);}
        });
    }
    public static bool TryOpen(VaultSession vault)
    {
        var path=PathFor(vault);if(!File.Exists(path)||vault.IsFirstRun)return false;
        try{
            var bytes=ProtectedData.Unprotect(File.ReadAllBytes(path),null,DataProtectionScope.CurrentUser);
            Remembered? saved;try{saved=JsonSerializer.Deserialize<Remembered>(bytes);}finally{CryptographicOperations.ZeroMemory(bytes);}
            if(saved==null||!DateTimeOffset.TryParse(saved.ExpiresAt,out var expiry)||expiry<=DateTimeOffset.UtcNow)return false;
            vault.Unlock(saved.UnlockSecret);
            if(vault.Connection?.Token==saved.Token)return true;
            vault.Lock();return false;
        }catch{vault.Lock();return false;}
    }
    public static void Forget(VaultSession vault){var path=PathFor(vault);if(File.Exists(path))File.Delete(path);}
    public static void RefreshUnlockSecret(VaultSession vault)
    {
        var path=PathFor(vault);if(!File.Exists(path))return;
        var bytes=ProtectedData.Unprotect(File.ReadAllBytes(path),null,DataProtectionScope.CurrentUser);
        Remembered? saved;try{saved=JsonSerializer.Deserialize<Remembered>(bytes);}finally{CryptographicOperations.ZeroMemory(bytes);}
        if(saved!=null)Save(vault,new AccountSession(saved.Token,saved.ExpiresAt,new("remembered",saved.Login)));
    }
    public static void Check(string directory)
    {
        var vault=new VaultSession(System.IO.Path.Combine(directory,"auth",Guid.NewGuid().ToString("N"),"remembered-qa.json"));vault.Unlock("QaRemember!123");
        var account=new AccountSession("mbs_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG","2099-01-01T00:00:00Z",new("qa","qa_user"));
        vault.SaveConnection(new("http://127.0.0.1:8788",account.Token,"","qa_user"));Save(vault,account);vault.Lock();
        if(!TryOpen(vault))throw new Exception("Remembered native sign-in was lost on restart");
        if(Encoding.UTF8.GetString(File.ReadAllBytes(PathFor(vault))).Contains("QaRemember!123"))throw new Exception("Unprotected device secret");
        vault.ChangePassword("QaRemember!123","QaChanged!123");RefreshUnlockSecret(vault);vault.Lock();
        if(!TryOpen(vault))throw new Exception("Changing master password broke remembered sign-in");
        Save(vault,account with{ExpiresAt="2020-01-01T00:00:00Z"});vault.Lock();
        if(TryOpen(vault))throw new Exception("Expired session opened the vault");
        vault.Unlock("QaChanged!123");Save(vault,account);
        Forget(vault);vault.Lock();if(TryOpen(vault))throw new Exception("Logout did not disable remembered sign-in");
    }
}
