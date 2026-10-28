using System.Text;
using System.Text.Json;
using MemBoom.Core.Sync;

namespace MemBoom.Core;

public sealed class VaultSession
{
    private readonly string path;
    private string? password;
    public VaultDocument Document{get;private set;}=new();
    public SyncConnection? Connection{get;private set;}
    public bool IsUnlocked=>password!=null;
    public long Version{get;private set;}
    public bool IsFirstRun=>!File.Exists(path);
    public string FilePath=>path;
    public void WithUnlockSecret(Action<string> action){if(password==null)throw new InvalidOperationException("Хранилище заблокировано.");action(password);}
    public VaultSession(string file){path=Path.GetFullPath(file);}
    public void Unlock(string value)
    {
        Lock();
        if(!PortableCrypto.ValidPassword(value))throw new ArgumentException("Пароль: от 4 символов без пробелов и кириллицы.");
        VaultDocument restored=new();
        SyncConnection? connection=null;
        if(File.Exists(path))
        {
            if(new FileInfo(path).Length>PortableCrypto.MaxBackupBytes)throw new InvalidDataException("Файл хранилища слишком большой.");
            var plaintext=PortableCrypto.Decrypt(File.ReadAllText(path),value);
            restored=VaultDocument.FromPortableJson(plaintext);
            using var local=JsonDocument.Parse(plaintext);
            if(local.RootElement.ValueKind==JsonValueKind.Object&&local.RootElement.TryGetProperty("connection",out var settings)&&settings.ValueKind!=JsonValueKind.Null)
            {connection=settings.Deserialize<SyncConnection>()??throw new InvalidDataException("Некорректное подключение.");connection.Validate();}
        }
        else SaveAtomic(restored,value,null);
        Document=restored;Connection=connection;password=value;
    }
    public void Lock(){Version++;password=null;Document=new();Connection=null;}
    public void Apply(Action<VaultDocument> action)
    {
        if(password==null)throw new InvalidOperationException("Хранилище заблокировано.");
        var before=Document.Copy();
        try{action(Document);Document.Validate();SaveAtomic(Document,password,Connection);Version++;}
        catch{Document=before;throw;}
    }
    public void ChangePassword(string oldPassword,string newPassword)
    {
        if(password==null)throw new InvalidOperationException("Хранилище заблокировано.");
        PortableCrypto.Decrypt(File.ReadAllText(path),oldPassword);
        if(!PortableCrypto.ValidPassword(newPassword))throw new ArgumentException("Недопустимый новый пароль.");
        SaveAtomic(Document,newPassword,Connection);password=newPassword;Version++;
    }
    public string Export(string backupPassword,bool includeNotes=true)
    {
        if(password==null)throw new InvalidOperationException("Хранилище заблокировано.");
        return PortableCrypto.Encrypt(includeNotes&&Document.Notes.Count>0?Document.ToBackupJson():Document.ToPortableJson(),backupPassword);
    }
    public void SaveConnection(SyncConnection? connection)
    {
        if(password==null)throw new InvalidOperationException("Хранилище заблокировано.");
        connection?.Validate();SaveAtomic(Document,password,connection);Connection=connection;Version++;
    }
    private void SaveAtomic(VaultDocument document,string value,SyncConnection? connection)
    {
        using var categories=JsonDocument.Parse(document.ToPortableJson());
        var local=JsonSerializer.Serialize(new{format=3,categories=categories.RootElement,notes=document.Notes,connection});
        var encrypted=PortableCrypto.Encrypt(local,value);
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var temporary=path+"."+Guid.NewGuid().ToString("N")+".tmp";
        try
        {
            using(var stream=new FileStream(temporary,FileMode.CreateNew,FileAccess.Write,FileShare.None))
            {var bytes=Encoding.UTF8.GetBytes(encrypted);stream.Write(bytes);stream.Flush(true);}
            if(File.Exists(path))File.Replace(temporary,path,path+".previous",true);else File.Move(temporary,path);
        }
        finally{if(File.Exists(temporary))File.Delete(temporary);}
    }
}
