using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace MemBoom.Core;

public sealed record Envelope(
    [property:JsonPropertyName("version")] int Version,
    [property:JsonPropertyName("salt")] string Salt,
    [property:JsonPropertyName("nonce")] string Nonce,
    [property:JsonPropertyName("ciphertext")] string Ciphertext);

public static class PortableCrypto
{
    public const int MaxBackupBytes=8*1024*1024;
    public static bool ValidPassword(string password)=>password.Length>=4&&password.All(c=>c>=33&&c<=126);
    public static string Encrypt(string plaintext,string password)
    {
        if(!ValidPassword(password))throw new ArgumentException("Пароль: от 4 символов, только английские буквы, цифры и спецсимволы без пробелов.");
        var salt=RandomNumberGenerator.GetBytes(16);var nonce=RandomNumberGenerator.GetBytes(12);
        var key=Rfc2898DeriveBytes.Pbkdf2(password,salt,310000,HashAlgorithmName.SHA256,32);
        var bytes=Encoding.UTF8.GetBytes(plaintext);
        try
        {
            if(bytes.Length>MaxBackupBytes)throw new InvalidDataException("Слишком большой объём данных.");
            var ciphertext=new byte[bytes.Length];var tag=new byte[16];
            using var aes=new AesGcm(key,16);aes.Encrypt(nonce,bytes,ciphertext,tag);
            var result=JsonSerializer.Serialize(new Envelope(1,Convert.ToBase64String(salt),Convert.ToBase64String(nonce),Convert.ToBase64String([..ciphertext,..tag])));
            if(Encoding.UTF8.GetByteCount(result)>MaxBackupBytes)throw new InvalidDataException("Копия превышает 8 МБ.");
            return result;
        }
        finally{CryptographicOperations.ZeroMemory(key);CryptographicOperations.ZeroMemory(bytes);}
    }
    public static string Decrypt(string json,string password)
    {
        if(!ValidPassword(password)||Encoding.UTF8.GetByteCount(json)>MaxBackupBytes)throw new CryptographicException("Недопустимый пароль или файл.");
        var envelope=JsonSerializer.Deserialize<Envelope>(json)??throw new InvalidDataException("Некорректная копия.");
        if(envelope.Version!=1)throw new InvalidDataException("Неподдерживаемая версия копии.");
        var salt=Convert.FromBase64String(envelope.Salt);var nonce=Convert.FromBase64String(envelope.Nonce);var ciphertext=Convert.FromBase64String(envelope.Ciphertext);
        if(salt.Length!=16||nonce.Length!=12||ciphertext.Length<16)throw new CryptographicException("Некорректный зашифрованный файл.");
        var key=Rfc2898DeriveBytes.Pbkdf2(password,salt,310000,HashAlgorithmName.SHA256,32);var plaintext=new byte[ciphertext.Length-16];
        try{using var aes=new AesGcm(key,16);aes.Decrypt(nonce,ciphertext.AsSpan(0,plaintext.Length),ciphertext.AsSpan(plaintext.Length),plaintext);return Encoding.UTF8.GetString(plaintext);}
        finally{CryptographicOperations.ZeroMemory(key);CryptographicOperations.ZeroMemory(plaintext);}
    }
    public static string GeneratePassword(int length=20)
    {
        if(length<4||length>128)throw new ArgumentOutOfRangeException(nameof(length));
        string[] groups=["ABCDEFGHJKLMNPQRSTUVWXYZ","abcdefghijkmnopqrstuvwxyz","23456789","!@#$%^&*()-_=+"];
        var all=string.Concat(groups);var chars=new List<char>();
        foreach(var group in groups)chars.Add(group[RandomNumberGenerator.GetInt32(group.Length)]);
        while(chars.Count<length)chars.Add(all[RandomNumberGenerator.GetInt32(all.Length)]);
        for(int i=chars.Count-1;i>0;i--){int j=RandomNumberGenerator.GetInt32(i+1);(chars[i],chars[j])=(chars[j],chars[i]);}
        return new string(chars.ToArray());
    }
    public static bool IsSecret(string label)=>new[]{"парол","password","passcode","secret","token","ключ","key"}.Any(s=>label.Contains(s,StringComparison.OrdinalIgnoreCase));
}
