using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace MemBoom.Core.Sync;

public enum SyncChoice{Download,Upload,Merge}
public sealed record SyncPreview(long Revision,VaultDocument Local,VaultDocument? Remote,long SessionVersion,string LocalHash);

public sealed class VaultSyncService(VaultSyncClient client)
{
    public async Task<SyncPreview> PreviewAsync(VaultSession session,string syncPassword,CancellationToken cancellationToken)
    {
        if(!session.IsUnlocked)throw new InvalidOperationException("Хранилище заблокировано.");
        if(!PortableCrypto.ValidPassword(syncPassword))throw new ArgumentException("Проверьте пароль синхронизации.");
        var local=session.Document.Copy();var version=session.Version;var hash=Hash(local);
        var snapshot=await client.FetchAsync(cancellationToken);
        var remote=snapshot.Envelope==null?null:VaultDocument.FromPortableJson(PortableCrypto.Decrypt(JsonSerializer.Serialize(snapshot.Envelope),syncPassword));
        cancellationToken.ThrowIfCancellationRequested();
        var preview=new SyncPreview(snapshot.Revision,local,remote,version,hash);
        EnsureCurrent(session,preview);return preview;
    }
    public async Task<long> ApplyAsync(VaultSession session,SyncPreview preview,SyncChoice choice,string syncPassword,CancellationToken cancellationToken)
    {
        EnsureCurrent(session,preview);cancellationToken.ThrowIfCancellationRequested();
        if(choice==SyncChoice.Download)
        {
            if(preview.Remote==null)throw new InvalidOperationException("Сервер пока пуст. Загрузка недоступна.");
            Replace(session,preview.Remote);return preview.Revision;
        }
        if(choice is not SyncChoice.Upload and not SyncChoice.Merge)throw new ArgumentOutOfRangeException(nameof(choice));
        var outgoing=preview.Local.Copy();
        if(choice==SyncChoice.Merge&&preview.Remote!=null)outgoing.Merge(preview.Remote);
        var envelope=JsonSerializer.Deserialize<Envelope>(PortableCrypto.Encrypt(outgoing.ToPortableJson(),syncPassword))!;
        var revision=await client.UploadAsync(preview.Revision,envelope,cancellationToken);
        cancellationToken.ThrowIfCancellationRequested();EnsureCurrent(session,preview);
        if(choice==SyncChoice.Merge)Replace(session,outgoing);
        return revision;
    }
    private static void Replace(VaultSession session,VaultDocument document)=>session.Apply(local=>{local.Accounts.Clear();local.Accounts.AddRange(document.Copy().Accounts);});
    private static string Hash(VaultDocument document)=>Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(document.ToPortableJson())));
    private static void EnsureCurrent(VaultSession session,SyncPreview preview)
    {
        if(!session.IsUnlocked||session.Version!=preview.SessionVersion||Hash(session.Document)!=preview.LocalHash)
            throw new InvalidOperationException("Локальное хранилище изменилось или было заблокировано. Обновите предпросмотр.");
    }
}
