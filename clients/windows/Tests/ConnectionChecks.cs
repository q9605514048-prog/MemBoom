using MemBoom.Core;
using MemBoom.Core.Sync;

internal static class ConnectionChecks
{
    public static void Run(Action<string,Action> check)
    {
        check("legacy local vault migrates without exporting connection",()=>
        {
            var directory=Path.Combine(Path.GetTempPath(),"MemBoom-legacy-"+Guid.NewGuid().ToString("N"));Directory.CreateDirectory(directory);
            try
            {
                var path=Path.Combine(directory,"vault.json");
                File.WriteAllText(path,PortableCrypto.Encrypt("[{\"name\":\"QA\",\"accounts\":[{\"id\":\"old\",\"name\":\"Legacy\",\"fields\":[]}]}]","QaLocal!123"));
                var session=new VaultSession(path);session.Unlock("QaLocal!123");session.SaveConnection(new("http://localhost:8788","qa-device-token-123456"));session.Lock();session.Unlock("QaLocal!123");
                if(session.Document.Accounts.Single().Name!="Legacy"||session.Connection==null)throw new Exception("Legacy migration lost data");
            }
            finally{Directory.Delete(directory,true);}
        });
        check("connection is encrypted, survives restart and master password change",()=>
        {
            var directory=Path.Combine(Path.GetTempPath(),"MemBoom-connection-"+Guid.NewGuid().ToString("N"));
            try
            {
                var path=Path.Combine(directory,"vault.json");var session=new VaultSession(path);session.Unlock("QaLocal!123");
                session.SaveConnection(new("http://localhost:8788","qa-device-token-123456","qa-id","QA Windows"));
                if(File.ReadAllText(path).Contains("qa-device-token"))throw new Exception("Token written as plaintext");
                if(session.Export("QaExport!123").Contains("qa-device-token"))throw new Exception("Token exported");
                if(PortableCrypto.Decrypt(session.Export("QaExport!123"),"QaExport!123").Contains("qa-device-token"))throw new Exception("Connection leaked into portable backup");
                session.Lock();if(session.Connection!=null)throw new Exception("Connection retained after lock");session.Unlock("QaLocal!123");
                if(session.Connection?.DeviceId!="qa-id")throw new Exception("Connection not restored");
                session.ChangePassword("QaLocal!123","QaNew!123");session.Lock();session.Unlock("QaNew!123");
                if(session.Connection?.Token!="qa-device-token-123456")throw new Exception("Connection lost after password change");
                session.SaveConnection(null);session.Lock();session.Unlock("QaNew!123");if(session.Connection!=null)throw new Exception("Connection not forgotten");
            }
            finally{if(Directory.Exists(directory))Directory.Delete(directory,true);}
        });
        check("connection updates reject bad addresses and preserve accounts",()=>
        {
            var directory=Path.Combine(Path.GetTempPath(),"MemBoom-connection-"+Guid.NewGuid().ToString("N"));
            try
            {
                var session=new VaultSession(Path.Combine(directory,"vault.json"));session.Unlock("QaLocal!123");
                session.Apply(d=>d.Accounts.Add(new(){Category="QA",Name="Account"}));
                try{session.SaveConnection(new("http://example.test","qa-device-token-123456"));throw new Exception("Insecure connection accepted");}catch(ArgumentException){}
                if(session.Connection!=null||session.Document.Accounts.Count!=1)throw new Exception("Invalid connection changed data");
                session.SaveConnection(new("http://localhost:8788","qa-device-token-123456"));
                session.Apply(d=>d.Accounts[0].Name="Changed");session.Lock();session.Unlock("QaLocal!123");
                if(session.Connection==null||session.Document.Accounts[0].Name!="Changed")throw new Exception("Saving account lost connection");
            }
            finally{if(Directory.Exists(directory))Directory.Delete(directory,true);}
        });
    }
}
