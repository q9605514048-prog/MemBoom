using MemBoom.Core;
using MemBoom.Core.Sync;
using System.Net;
using System.Text;
using System.Text.Json;

internal static class SyncChecks
{
    public static void Run(Action<string,Action> check)
    {
        const string password="QaSync!123";
        VaultDocument Document(string value)=>new(){Accounts=[new(){Id="shared",Category="QA",Name="Example",Fields=[new("Пароль",value)]}]};
        check("sync rejects insecure destinations and malformed tokens",()=>
        {
            Reject(()=>new VaultSyncClient("http://example.test","test-token-123456"));
            Reject(()=>new VaultSyncClient("https://user:pass@example.test","test-token-123456"));
            Reject(()=>new VaultSyncClient("file:///tmp","test-token-123456"));
            Reject(()=>new VaultSyncClient("http://localhost:8788","bad\r\ntoken"));
        });
        check("sync sends ciphertext and respects CAS conflict",()=>
        {
            var envelope=JsonSerializer.Deserialize<Envelope>(PortableCrypto.Encrypt(Document("private-value").ToPortableJson(),password))!;
            var handler=new Handler(request=>
            {
                Require(request.Headers.Authorization?.ToString()=="Bearer test-token-123456");
                var body=request.Content!.ReadAsStringAsync().GetAwaiter().GetResult();
                Require(!body.Contains("private-value")&&!body.Contains(password));
                using var json=JsonDocument.Parse(body);Require(json.RootElement.GetProperty("revision").GetInt64()==3);
                return Reply(HttpStatusCode.Conflict,"{\"revision\":4}");
            });
            using var client=new VaultSyncClient("http://localhost:8788","test-token-123456",handler);
            try{client.UploadAsync(3,envelope,CancellationToken.None).GetAwaiter().GetResult();throw new Exception("Conflict was ignored");}
            catch(SyncConflictException){}
        });
        check("sync preview decrypts without changing local data",()=>
        {
            WithSession(session=>
            {
                session.Apply(d=>d.Accounts.Add(Document("local").Accounts[0]));
                var envelope=PortableCrypto.Encrypt(Document("remote").ToPortableJson(),password);
                using var client=new VaultSyncClient("http://localhost:8788","test-token-123456",new Handler(_=>Reply(HttpStatusCode.OK,"{\"revision\":2,\"envelope\":"+envelope+"}")));
                var preview=new VaultSyncService(client).PreviewAsync(session,password,CancellationToken.None).GetAwaiter().GetResult();
                Require(preview.Remote!.Accounts[0].Fields[0].Value=="remote");
                Require(session.Document.Accounts[0].Fields[0].Value=="local");
                Reject(()=>new VaultSyncService(client).PreviewAsync(session,"Wrong!123",CancellationToken.None).GetAwaiter().GetResult());
                Require(session.Document.Accounts[0].Fields[0].Value=="local");
            });
        });
        check("sync merge preserves both versions",()=>
        {
            WithSession(session=>
            {
                session.Apply(d=>d.Accounts.Add(Document("local").Accounts[0]));
                var envelope=PortableCrypto.Encrypt(Document("remote").ToPortableJson(),password);
                using var client=new VaultSyncClient("http://localhost:8788","test-token-123456",new Handler(request=>request.Method==HttpMethod.Get?Reply(HttpStatusCode.OK,"{\"revision\":2,\"envelope\":"+envelope+"}"):Reply(HttpStatusCode.OK,"{\"revision\":3}")));
                var service=new VaultSyncService(client);var preview=service.PreviewAsync(session,password,CancellationToken.None).GetAwaiter().GetResult();
                service.ApplyAsync(session,preview,SyncChoice.Merge,password,CancellationToken.None).GetAwaiter().GetResult();
                Require(session.Document.Accounts.Count==2);
            });
        });
        check("sync refuses stale local data and sessions reopened after lock",()=>
        {
            WithSession(session=>
            {
                using var client=new VaultSyncClient("http://localhost:8788","test-token-123456",new Handler(_=>Reply(HttpStatusCode.OK,"{\"revision\":0,\"envelope\":null}")));
                var service=new VaultSyncService(client);var preview=service.PreviewAsync(session,password,CancellationToken.None).GetAwaiter().GetResult();
                session.Apply(d=>d.Accounts.Add(Document("new").Accounts[0]));
                Reject(()=>service.ApplyAsync(session,preview,SyncChoice.Upload,password,CancellationToken.None).GetAwaiter().GetResult());
                preview=service.PreviewAsync(session,password,CancellationToken.None).GetAwaiter().GetResult();
                session.Lock();session.Unlock("QaLocal!123");
                Reject(()=>service.ApplyAsync(session,preview,SyncChoice.Upload,password,CancellationToken.None).GetAwaiter().GetResult());
                Require(session.Document.Accounts.Count==1);
            });
        });
        check("sync rejects oversized replies and unauthorized access",()=>
        {
            using var huge=new VaultSyncClient("http://localhost:8788","test-token-123456",new Handler(_=>Reply(HttpStatusCode.OK,new string('x',VaultSyncClient.MaxResponseBytes+1))));
            Reject(()=>huge.FetchAsync(CancellationToken.None).GetAwaiter().GetResult());
            using var denied=new VaultSyncClient("http://localhost:8788","test-token-123456",new Handler(_=>Reply(HttpStatusCode.Unauthorized,"{}")));
            Reject(()=>denied.FetchAsync(CancellationToken.None).GetAwaiter().GetResult());
        });
        check("sync ignores replies arriving after lock and unlock",()=>
        {
            WithSession(session=>
            {
                var response=new TaskCompletionSource<HttpResponseMessage>(TaskCreationOptions.RunContinuationsAsynchronously);
                using var client=new VaultSyncClient("http://localhost:8788","test-token-123456",new AsyncHandler(response.Task));
                var pending=new VaultSyncService(client).PreviewAsync(session,password,CancellationToken.None);
                session.Lock();session.Unlock("QaLocal!123");
                response.SetResult(Reply(HttpStatusCode.OK,"{\"revision\":0,\"envelope\":null}"));
                Reject(()=>pending.GetAwaiter().GetResult());Require(session.Document.Accounts.Count==0);
            });
        });
        check("sync rejects redirects and malformed snapshots",()=>
        {
            foreach(var reply in new[]{Reply(HttpStatusCode.Redirect,"{}"),Reply(HttpStatusCode.OK,"{}"),Reply(HttpStatusCode.OK,"{\"revision\":-1,\"envelope\":null}")})
            {
                using var client=new VaultSyncClient("http://localhost:8788","test-token-123456",new Handler(_=>reply));
                Reject(()=>client.FetchAsync(CancellationToken.None).GetAwaiter().GetResult());
            }
        });
        check("device registration validates reply and sends only device name",()=>
        {
            using var client=new VaultSyncClient("http://localhost:8788","qa-admin-token-123456",new Handler(request=>
            {
                Require(request.Method==HttpMethod.Post&&request.RequestUri!.AbsolutePath=="/api/devices");
                var body=request.Content!.ReadAsStringAsync().GetAwaiter().GetResult();
                using var json=JsonDocument.Parse(body);Require(json.RootElement.GetProperty("name").GetString()=="QA Windows");
                Require(!body.Contains("qa-admin-token"));
                return Reply(HttpStatusCode.Created,"{\"deviceId\":\"qa-id\",\"deviceToken\":\"qa-device-token-123456\"}");
            }));
            Require(client.RegisterDeviceAsync(" QA Windows ",CancellationToken.None).GetAwaiter().GetResult().DeviceId=="qa-id");
            Reject(()=>client.RegisterDeviceAsync(" ",CancellationToken.None).GetAwaiter().GetResult());
            using var malformed=new VaultSyncClient("http://localhost:8788","qa-admin-token-123456",new Handler(_=>Reply(HttpStatusCode.Created,"{}")));
            Reject(()=>malformed.RegisterDeviceAsync("QA",CancellationToken.None).GetAwaiter().GetResult());
        });
    }
    private static void WithSession(Action<VaultSession> action)
    {
        var directory=Path.Combine(Path.GetTempPath(),"MemBoom-sync-qa-"+Guid.NewGuid().ToString("N"));
        try{var session=new VaultSession(Path.Combine(directory,"vault.json"));session.Unlock("QaLocal!123");action(session);session.Lock();}
        finally{if(Directory.Exists(directory))Directory.Delete(directory,true);}
    }
    public static void Integration(string endpoint,string token,Action<string,Action> check)
    {
        check("real server roundtrip, concurrent conflict and merge",()=>
        {
            WithSession(first=>WithSession(second=>
            {
                const string password="QaSync!123";
                first.Apply(d=>d.Accounts.Add(new(){Id="integration-account",Category="QA",Name="Integration",Fields=[new("Пароль","qa-network-secret")]}));
                using var a=new VaultSyncClient(endpoint,token);using var b=new VaultSyncClient(endpoint,token);
                var firstService=new VaultSyncService(a);var secondService=new VaultSyncService(b);
                second.Apply(d=>d.Notes.Add(new(){Title="Local-only QA note",Content="This note must survive account sync"}));
                SyncPreview Read(VaultSyncService service,VaultSession s)=>service.PreviewAsync(s,password,CancellationToken.None).GetAwaiter().GetResult();
                long Apply(VaultSyncService service,VaultSession s,SyncPreview preview,SyncChoice choice)=>service.ApplyAsync(s,preview,choice,password,CancellationToken.None).GetAwaiter().GetResult();
                var empty=Read(firstService,first);Require(empty.Remote==null&&empty.Revision==0);
                Require(Apply(firstService,first,empty,SyncChoice.Upload)==1);
                Apply(secondService,second,Read(secondService,second),SyncChoice.Download);
                Require(second.Document.Accounts.Single().Fields.Single().Value=="qa-network-secret");
                Require(second.Document.Notes.Single().Title=="Local-only QA note");
                first.Apply(d=>d.Accounts[0].Fields=[new("Пароль","qa-network-first")]);
                second.Apply(d=>d.Accounts[0].Fields=[new("Пароль","qa-network-second")]);
                var stale=Read(secondService,second);
                Require(Apply(firstService,first,Read(firstService,first),SyncChoice.Upload)==2);
                try{Apply(secondService,second,stale,SyncChoice.Merge);throw new Exception("Concurrent change was overwritten");}catch(SyncConflictException){}
                Require(second.Document.Accounts.Single().Fields.Single().Value=="qa-network-second");
                Require(Apply(secondService,second,Read(secondService,second),SyncChoice.Merge)==3);
                Require(second.Document.Accounts.Count==2);
                Require(second.Document.Notes.Count==1);
                var final=Read(firstService,first);Require(final.Remote!.Accounts.Count==2);
                Require(final.Remote.Accounts.Select(a=>a.Fields.Single().Value).Order().SequenceEqual(new[]{"qa-network-first","qa-network-second"}));
                Reject(()=>firstService.PreviewAsync(first,"Wrong!123",CancellationToken.None).GetAwaiter().GetResult());
                Require(first.Document.Accounts.Count==1);
            }));
        });
        check("real server rejects invalid device token",()=>
        {
            using var denied=new VaultSyncClient(endpoint,"qa-invalid-token-123456");
            Reject(()=>denied.FetchAsync(CancellationToken.None).GetAwaiter().GetResult());
        });
        check("real server registers device with restricted token",()=>
        {
            using var admin=new VaultSyncClient(endpoint,token);
            var device=admin.RegisterDeviceAsync("QA Windows",CancellationToken.None).GetAwaiter().GetResult();
            Require(!string.IsNullOrWhiteSpace(device.DeviceId)&&device.DeviceToken!=token);
            using var client=new VaultSyncClient(endpoint,device.DeviceToken);
            Require(client.FetchAsync(CancellationToken.None).GetAwaiter().GetResult().Revision==3);
            Reject(()=>client.RegisterDeviceAsync("Unauthorized child",CancellationToken.None).GetAwaiter().GetResult());
        });
    }
    private static void Require(bool condition){if(!condition)throw new Exception("Assertion failed");}
    private static void Reject(Action action){try{action();}catch{return;}throw new Exception("Expected rejection");}
    private static HttpResponseMessage Reply(HttpStatusCode status,string json)=>new(status){Content=new StringContent(json,Encoding.UTF8,"application/json")};
    private sealed class Handler(Func<HttpRequestMessage,HttpResponseMessage> respond):HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,CancellationToken cancellationToken)=>Task.FromResult(respond(request));
    }
    private sealed class AsyncHandler(Task<HttpResponseMessage> response):HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,CancellationToken cancellationToken)=>response;
    }
}
