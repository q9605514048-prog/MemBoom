using System.Text.Json.Serialization;

namespace MemBoom.Core.Sync;

public sealed record DeviceInfo(
    [property:JsonPropertyName("deviceId")] string DeviceId,
    [property:JsonPropertyName("name")] string Name,
    [property:JsonPropertyName("createdAt")] DateTimeOffset CreatedAt,
    [property:JsonPropertyName("revokedAt")] DateTimeOffset? RevokedAt,
    [property:JsonPropertyName("revoked")] bool Revoked)
{
    public bool IsRevoked=>Revoked||RevokedAt.HasValue;
    internal static bool ValidId(string? id)=>!string.IsNullOrEmpty(id)&&id.Length<=128&&id.All(c=>char.IsAsciiLetterOrDigit(c)||c is '-' or '_');
}
