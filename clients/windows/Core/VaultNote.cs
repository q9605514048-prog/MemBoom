namespace MemBoom.Core;

public sealed class VaultNote
{
    public string Id{get;set;}=Guid.NewGuid().ToString();
    public string Title{get;set;}="";
    public string Content{get;set;}="";
    public bool Pinned{get;set;}
    public DateTimeOffset UpdatedAt{get;set;}=DateTimeOffset.UtcNow;
    public VaultNote Copy()=>new(){Id=Id,Title=Title,Content=Content,Pinned=Pinned,UpdatedAt=UpdatedAt};
}
