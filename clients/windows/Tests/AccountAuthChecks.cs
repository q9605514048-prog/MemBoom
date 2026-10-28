using MemBoom.Core.Auth;
using System.Net;
using System.Text;
using System.Text.Json;

internal static class AccountAuthChecks
{
    public static void Run(Action<string,Action> check)
    {
        check("native authentication requests remembered login without email",()=>
        {
            using var client=new AccountAuthClient("http://localhost:8788",new Handler(request=>{
                if(request.RequestUri!.AbsolutePath!="/api/auth/login"||request.Method!=HttpMethod.Post)throw new Exception("Wrong auth route");
                var body=request.Content!.ReadAsStringAsync().GetAwaiter().GetResult();if(!body.Contains("qa_user")||!body.Contains("\"remember\":true"))throw new Exception("Missing remembered login");
                return Reply(HttpStatusCode.OK,"{\"token\":\"mbs_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG\",\"expiresAt\":\"2099-01-01T00:00:00Z\",\"user\":{\"id\":\"qa\",\"email\":\"qa@gmail.com\",\"login\":\"qa_user\",\"emailVerified\":true}}");
            }));
            if(client.LoginAsync("qa_user","a1!b",CancellationToken.None).GetAwaiter().GetResult().User.Login!="qa_user")throw new Exception("Invalid session");
        });
    }
    public static void Integration(string input,Action<string,Action> check)
    {
        using var parsed=JsonDocument.Parse(input);var root=parsed.RootElement;string Read(string key)=>root.GetProperty(key).GetString()!;
        check("Windows registers and signs in by login against real API",()=>
        {
            using var client=new AccountAuthClient(Read("endpoint"));
            var created=client.RegisterAsync(Read("login"),Read("password"),CancellationToken.None).GetAwaiter().GetResult();
            if(DateTimeOffset.Parse(created.ExpiresAt)<DateTimeOffset.UtcNow.AddDays(300))throw new Exception("Native registration was not remembered");
            client.LogoutAsync(created.Token,CancellationToken.None).GetAwaiter().GetResult();
            var session=client.LoginAsync(Read("login"),Read("password"),CancellationToken.None).GetAwaiter().GetResult();if(session.User.Login!=Read("login"))throw new Exception("Wrong account");client.LogoutAsync(session.Token,CancellationToken.None).GetAwaiter().GetResult();
        });
    }
    private static HttpResponseMessage Reply(HttpStatusCode status,string body)=>new(status){Content=new StringContent(body,Encoding.UTF8,"application/json")};
    private sealed class Handler(Func<HttpRequestMessage,HttpResponseMessage> respond):HttpMessageHandler{protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,CancellationToken token)=>Task.FromResult(respond(request));}
}
