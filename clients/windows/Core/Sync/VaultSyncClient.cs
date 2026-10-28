using System.Net;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace MemBoom.Core.Sync;

public sealed record RemoteSnapshot(
    [property:JsonPropertyName("revision")] long Revision,
    [property:JsonPropertyName("envelope")] Envelope? Envelope);
public sealed record RegisteredDevice(
    [property:JsonPropertyName("deviceId")] string DeviceId,
    [property:JsonPropertyName("deviceToken")] string DeviceToken);

public sealed class SyncConflictException():Exception("Сервер изменился. Обновите предпросмотр перед повторной отправкой.");

public sealed class VaultSyncClient:IDisposable
{
    // The existing server accepts at most 1,000,000 request bytes.
    public const int MaxResponseBytes=4*1024*1024;
    private readonly HttpClient http;
    public VaultSyncClient(string endpoint,string token,HttpMessageHandler? handler=null)
    {
        if(!Uri.TryCreate(endpoint,UriKind.Absolute,out var uri)||
           uri.UserInfo.Length!=0||uri.Query.Length!=0||uri.Fragment.Length!=0||uri.AbsolutePath!="/"||
           !(uri.Scheme=="https"||(uri.Scheme=="http"&&uri.IsLoopback)))
            throw new ArgumentException("Укажите адрес HTTPS без пути. HTTP разрешён только для localhost.");
        if(token.Length<16||token.Length>512||token.Any(c=>c<33||c>126))throw new ArgumentException("Проверьте токен устройства: от 16 символов без пробелов.");
        http=new HttpClient(handler??new HttpClientHandler{AllowAutoRedirect=false}){BaseAddress=uri,Timeout=TimeSpan.FromSeconds(20)};
        http.DefaultRequestHeaders.Authorization=new AuthenticationHeaderValue("Bearer",token);
    }
    public async Task<RemoteSnapshot> FetchAsync(CancellationToken cancellationToken)
    {
        using var request=new HttpRequestMessage(HttpMethod.Get,"api/vault");
        using var result=await SendAsync(request,cancellationToken);
        var snapshot=JsonSerializer.Deserialize<RemoteSnapshot>(result.RootElement.GetRawText())??throw new InvalidDataException("Некорректный ответ сервера.");
        if(snapshot.Revision<0||!result.RootElement.TryGetProperty("revision",out _)||!result.RootElement.TryGetProperty("envelope",out _))throw new InvalidDataException("Некорректная ревизия сервера.");
        return snapshot;
    }
    public async Task<RegisteredDevice> RegisterDeviceAsync(string name,CancellationToken cancellationToken)
    {
        if(string.IsNullOrWhiteSpace(name)||name.Trim().Length>80)throw new ArgumentException("Название устройства: от 1 до 80 символов.");
        using var request=new HttpRequestMessage(HttpMethod.Post,"api/devices"){Content=new StringContent(JsonSerializer.Serialize(new{name=name.Trim()}),Encoding.UTF8,"application/json")};
        using var result=await SendAsync(request,cancellationToken);
        var device=result.RootElement.Deserialize<RegisteredDevice>()??throw new InvalidDataException("Некорректный ответ регистрации.");
        if(string.IsNullOrWhiteSpace(device.DeviceId)||device.DeviceId.Length>128||string.IsNullOrWhiteSpace(device.DeviceToken))throw new InvalidDataException("В ответе отсутствует токен устройства.");
        using var validate=new VaultSyncClient(http.BaseAddress!.AbsoluteUri,device.DeviceToken);
        return device;
    }
    public async Task<long> UploadAsync(long revision,Envelope envelope,CancellationToken cancellationToken)
    {
        if(revision<0)throw new ArgumentOutOfRangeException(nameof(revision));
        var body=JsonSerializer.Serialize(new{revision,envelope});
        if(Encoding.UTF8.GetByteCount(body)>1_000_000)throw new InvalidDataException("Хранилище превышает лимит сервера (1 МБ). Используйте резервную копию.");
        using var request=new HttpRequestMessage(HttpMethod.Put,"api/vault"){Content=new StringContent(body,Encoding.UTF8,"application/json")};
        using var result=await SendAsync(request,cancellationToken);
        var next=result.RootElement.GetProperty("revision").GetInt64();
        if(next!=checked(revision+1))throw new InvalidDataException("Неожиданная ревизия сервера. Обновите предпросмотр.");
        return next;
    }
    public async Task<IReadOnlyList<DeviceInfo>> ListDevicesAsync(CancellationToken cancellationToken)
    {
        using var request=new HttpRequestMessage(HttpMethod.Get,"api/devices");
        using var result=await SendAsync(request,cancellationToken);
        if(result.RootElement.ValueKind!=JsonValueKind.Array||result.RootElement.GetArrayLength()>5000)throw new InvalidDataException("Некорректный список устройств.");
        var devices=result.RootElement.Deserialize<List<DeviceInfo>>()??throw new InvalidDataException("Список устройств отсутствует.");
        if(devices.Any(d=>d==null||!DeviceInfo.ValidId(d.DeviceId)||string.IsNullOrWhiteSpace(d.Name)||d.Name.Length>2048||d.CreatedAt==default)||devices.Select(d=>d.DeviceId).Distinct().Count()!=devices.Count)
            throw new InvalidDataException("Некорректные данные устройства.");
        return devices;
    }
    public async Task RevokeDeviceAsync(string deviceId,CancellationToken cancellationToken)
    {
        if(!DeviceInfo.ValidId(deviceId))throw new ArgumentException("Некорректный идентификатор устройства.");
        using var request=new HttpRequestMessage(HttpMethod.Post,$"api/devices/{deviceId}/revoke");
        using var result=await SendAsync(request,cancellationToken);
        if(!result.RootElement.TryGetProperty("deviceId",out var id)||id.GetString()!=deviceId||!result.RootElement.TryGetProperty("revoked",out var revoked)||revoked.ValueKind!=JsonValueKind.True)
            throw new InvalidDataException("Сервер не подтвердил отзыв устройства. Обновите список.");
    }
    private async Task<JsonDocument> SendAsync(HttpRequestMessage request,CancellationToken cancellationToken)
    {
        using var response=await http.SendAsync(request,HttpCompletionOption.ResponseHeadersRead,cancellationToken).ConfigureAwait(false);
        if(response.StatusCode==HttpStatusCode.Conflict)throw new SyncConflictException();
        if(response.StatusCode==HttpStatusCode.Unauthorized)throw new InvalidOperationException("Токен недействителен или устройство отозвано.");
        if((int)response.StatusCode==429)throw new InvalidOperationException("Слишком много попыток. Подождите перед повтором.");
        if(!response.IsSuccessStatusCode)throw new HttpRequestException($"Сервер вернул HTTP {(int)response.StatusCode}. Перенаправления не выполняются.");
        if(response.Content.Headers.ContentLength>MaxResponseBytes)throw new InvalidDataException("Ответ сервера слишком большой.");
        await using var stream=await response.Content.ReadAsStreamAsync(cancellationToken).ConfigureAwait(false);
        using var buffer=new MemoryStream();var chunk=new byte[16384];int count;
        while((count=await stream.ReadAsync(chunk,cancellationToken).ConfigureAwait(false))>0)
        {
            if(buffer.Length+count>MaxResponseBytes)throw new InvalidDataException("Ответ сервера слишком большой.");
            buffer.Write(chunk,0,count);
        }
        return JsonDocument.Parse(buffer.ToArray());
    }
    public void Dispose()=>http.Dispose();
}
