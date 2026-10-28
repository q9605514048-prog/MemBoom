namespace MemBoom.Core.Sync;

public sealed record SyncConnection(string Endpoint,string Token,string DeviceId="",string DeviceName="")
{
    public void Validate(){using var client=new VaultSyncClient(Endpoint,Token);}
}
