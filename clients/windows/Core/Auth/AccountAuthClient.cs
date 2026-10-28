using System.Text;using System.Text.Json;using MemBoom.Core.Sync;
namespace MemBoom.Core.Auth;
public sealed record AccountUser(string Id,string Login);
public sealed record AccountSession(string Token,string ExpiresAt,AccountUser User);
public sealed class AccountAuthClient:IDisposable{
 readonly HttpClient http;static readonly JsonSerializerOptions Json=new(){PropertyNameCaseInsensitive=true};
 public AccountAuthClient(string endpoint,HttpMessageHandler? handler=null){using var _=new VaultSyncClient(endpoint,"endpoint-validation-123456");http=new(handler??new HttpClientHandler{AllowAutoRedirect=false}){BaseAddress=new Uri(endpoint),Timeout=TimeSpan.FromSeconds(20)};}
 public Task<AccountSession> LoginAsync(string login,string password,CancellationToken ct)=>Session("login",new{login,password,remember=true},ct);
 public Task<AccountSession> RegisterAsync(string login,string password,CancellationToken ct)=>Session("register",new{login,password,remember=true},ct);
 async Task<AccountSession> Session(string path,object body,CancellationToken ct){using var r=await Send(path,body,ct);var s=r.RootElement.Deserialize<AccountSession>(Json)??throw new InvalidDataException("Некорректный ответ входа.");if(string.IsNullOrWhiteSpace(s.User?.Id)||string.IsNullOrWhiteSpace(s.User.Login)||!DateTimeOffset.TryParse(s.ExpiresAt,out _))throw new InvalidDataException("Некорректный профиль.");return s;}
 public async Task LogoutAsync(string token,CancellationToken ct){using var _=await Send("logout",new{},ct,token);}
 async Task<JsonDocument> Send(string path,object body,CancellationToken ct,string? bearer=null){using var req=new HttpRequestMessage(HttpMethod.Post,"api/auth/"+path){Content=new StringContent(JsonSerializer.Serialize(body),Encoding.UTF8,"application/json")};if(bearer!=null)req.Headers.Authorization=new("Bearer",bearer);using var r=await http.SendAsync(req,HttpCompletionOption.ResponseHeadersRead,ct);if(r.StatusCode==System.Net.HttpStatusCode.NoContent)return JsonDocument.Parse("{}");var text=await r.Content.ReadAsStringAsync(ct);if(text.Length>32768)throw new InvalidDataException("Ответ сервера слишком большой.");var j=JsonDocument.Parse(text);if(!r.IsSuccessStatusCode){var e=j.RootElement.TryGetProperty("error",out var x)?x.GetString():null;j.Dispose();throw new InvalidOperationException(Message(e));}return j;}
 public static string Message(string? e)=>e switch{"invalid_credentials"=>"Неверный логин или пароль.","login_unavailable"=>"Этот логин уже используется.","too_many_attempts"=>"Слишком много попыток. Подождите перед повтором.","invalid_registration"=>"Проверьте логин и пароль.",_=>"Не удалось выполнить вход. Проверьте сервер и подключение."};public void Dispose()=>http.Dispose();
}
