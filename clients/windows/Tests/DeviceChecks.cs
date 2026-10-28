using MemBoom.Core.Sync;
using System.Net;
using System.Text;

internal static class DeviceChecks
{
    private const string Id="aaaabbbb-1234-4321-8888-123456789abc";
    public static void Run(Action<string,Action> check)
    {
        check("device list parses active and revoked state",()=>
        {
            using var client=new VaultSyncClient("http://localhost:8788","qa-admin-token-123456",new Handler(request=>
            {
                Require(request.Method==HttpMethod.Get&&request.RequestUri!.AbsolutePath=="/api/devices");
                return Reply("[{\"deviceId\":\""+Id+"\",\"name\":\"QA Phone\",\"createdAt\":\"2026-10-26T00:00:00Z\",\"revokedAt\":\"2026-10-26T01:00:00Z\",\"revoked\":false}]");
            }));
            var device=client.ListDevicesAsync(CancellationToken.None).GetAwaiter().GetResult().Single();
            Require(device.Name=="QA Phone"&&device.IsRevoked);
        });
        check("device revoke validates id, route and confirmation reply",()=>
        {
            using var client=new VaultSyncClient("http://localhost:8788","qa-admin-token-123456",new Handler(request=>
            {
                Require(request.Method==HttpMethod.Post&&request.RequestUri!.AbsolutePath==$"/api/devices/{Id}/revoke");
                return Reply("{\"deviceId\":\""+Id+"\",\"revoked\":true}");
            }));
            client.RevokeDeviceAsync(Id,CancellationToken.None).GetAwaiter().GetResult();
            Reject(()=>client.RevokeDeviceAsync("../vault",CancellationToken.None).GetAwaiter().GetResult());
            using var malformed=new VaultSyncClient("http://localhost:8788","qa-admin-token-123456",new Handler(_=>Reply("{\"deviceId\":\"other\",\"revoked\":true}")));
            Reject(()=>malformed.RevokeDeviceAsync(Id,CancellationToken.None).GetAwaiter().GetResult());
        });
        check("malformed device lists are rejected",()=>
        {
            foreach(var json in new[]{"{}","[null]","[{\"deviceId\":\"../vault\",\"name\":\"QA\"}]","[{\"deviceId\":\""+Id+"\",\"name\":\"\"}]"})
            {
                using var client=new VaultSyncClient("http://localhost:8788","qa-admin-token-123456",new Handler(_=>Reply(json)));
                Reject(()=>client.ListDevicesAsync(CancellationToken.None).GetAwaiter().GetResult());
            }
        });
    }
    public static void Integration(string endpoint,string token,Action<string,Action> check)
    {
        check("real device revoke blocks access without changing vault",()=>
        {
            using var admin=new VaultSyncClient(endpoint,token);
            var created=admin.RegisterDeviceAsync("QA Revoke",CancellationToken.None).GetAwaiter().GetResult();
            using var device=new VaultSyncClient(endpoint,created.DeviceToken);
            var revision=device.FetchAsync(CancellationToken.None).GetAwaiter().GetResult().Revision;
            var listed=admin.ListDevicesAsync(CancellationToken.None).GetAwaiter().GetResult();Require(!listed.Single(d=>d.DeviceId==created.DeviceId).IsRevoked);
            Reject(()=>device.ListDevicesAsync(CancellationToken.None).GetAwaiter().GetResult());
            Reject(()=>device.RevokeDeviceAsync(created.DeviceId,CancellationToken.None).GetAwaiter().GetResult());
            admin.RevokeDeviceAsync(created.DeviceId,CancellationToken.None).GetAwaiter().GetResult();
            Require(admin.ListDevicesAsync(CancellationToken.None).GetAwaiter().GetResult().Single(d=>d.DeviceId==created.DeviceId).IsRevoked);
            Reject(()=>device.FetchAsync(CancellationToken.None).GetAwaiter().GetResult());
            Require(admin.FetchAsync(CancellationToken.None).GetAwaiter().GetResult().Revision==revision);
        });
    }
    private static void Require(bool value){if(!value)throw new Exception("Assertion failed");}
    private static void Reject(Action action){try{action();}catch{return;}throw new Exception("Expected rejection");}
    private static HttpResponseMessage Reply(string json)=>new(HttpStatusCode.OK){Content=new StringContent(json,Encoding.UTF8,"application/json")};
    private sealed class Handler(Func<HttpRequestMessage,HttpResponseMessage> reply):HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,CancellationToken cancellationToken)=>Task.FromResult(reply(request));
    }
}
