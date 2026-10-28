using System.Text;
using System.Text.Json;

namespace MemBoom.Core;

public sealed record VaultField(string Label,string Value);
public sealed class VaultAccount
{
    public string Id{get;set;}=Guid.NewGuid().ToString();
    public string Category{get;set;}="";
    public string Name{get;set;}="";
    public List<VaultField> Fields{get;set;}=[];
    public bool Favorite{get;set;}
    public VaultAccount Copy()=>new(){Id=Id,Category=Category,Name=Name,Fields=[..Fields],Favorite=Favorite};
}
public sealed class VaultDocument
{
    public List<VaultAccount> Accounts{get;set;}=[];
    public List<VaultNote> Notes{get;set;}=[];
    public void Validate()
    {
        if(Accounts.Any(a=>string.IsNullOrWhiteSpace(a.Id)||string.IsNullOrWhiteSpace(a.Category)||string.IsNullOrWhiteSpace(a.Name)||a.Fields.Any(f=>string.IsNullOrWhiteSpace(f.Label)))||Accounts.Select(a=>a.Id).Distinct().Count()!=Accounts.Count)
            throw new InvalidDataException("Проверьте названия категорий, аккаунтов и полей.");
        if(Notes.Any(n=>n==null||string.IsNullOrWhiteSpace(n.Id)||string.IsNullOrWhiteSpace(n.Title)||n.Title.Length>200||n.Content==null||n.Content.Length>500000)||Notes.Select(n=>n.Id).Distinct().Count()!=Notes.Count)
            throw new InvalidDataException("Проверьте заголовки заметок: до 200 символов. Текст — до 500 000 символов.");
    }
    public VaultDocument Copy()=>new(){Accounts=Accounts.Select(a=>a.Copy()).ToList(),Notes=Notes.Select(n=>n.Copy()).ToList()};
    public string ToBackupJson()
    {
        using var categories=JsonDocument.Parse(ToPortableJson());
        return JsonSerializer.Serialize(new{categories=categories.RootElement,notes=Notes});
    }
    public string ToPortableJson()
    {
        Validate();
        return JsonSerializer.Serialize(Accounts.GroupBy(a=>a.Category).Select(group=>new{
            id="category-"+Convert.ToBase64String(Encoding.UTF8.GetBytes(group.Key)),name=group.Key,icon="▤",
            accounts=group.Select(a=>new{id=a.Id,name=a.Name,favorite=a.Favorite,fields=a.Fields.Select((f,i)=>new{id=$"{a.Id}-field-{i}",label=f.Label,value=f.Value})})
        }));
    }
    public static VaultDocument FromPortableJson(string json)
    {
        using var parsed=JsonDocument.Parse(json);
        var root=parsed.RootElement;
        var categories=root.ValueKind==JsonValueKind.Array?root:root.GetProperty("categories");
        if(categories.ValueKind!=JsonValueKind.Array)throw new InvalidDataException("Не найден список категорий.");
        var result=new VaultDocument();
        static string Read(JsonElement e,string name)=>e.GetProperty(name).GetString()??throw new InvalidDataException("Отсутствует значение.");
        foreach(var category in categories.EnumerateArray())
        {
            var categoryName=Read(category,"name");
            foreach(var item in category.GetProperty("accounts").EnumerateArray())
            {
                var account=new VaultAccount{Id=Read(item,"id"),Name=Read(item,"name"),Category=categoryName};
                if(item.TryGetProperty("favorite",out var favorite))account.Favorite=favorite.ValueKind==JsonValueKind.True;
                if(item.TryGetProperty("fields",out var fields))foreach(var field in fields.EnumerateArray())account.Fields.Add(new(Read(field,"label"),field.TryGetProperty("value",out var value)?value.GetString()??"":""));
                result.Accounts.Add(account);
            }
        }
        if(root.ValueKind==JsonValueKind.Object&&root.TryGetProperty("notes",out var notes))
            result.Notes=notes.Deserialize<List<VaultNote>>()??throw new InvalidDataException("Некорректный список заметок.");
        result.Validate();return result;
    }
    public int Merge(VaultDocument incoming)
    {
        incoming.Validate();int count=0;
        foreach(var account in incoming.Accounts)
        {
            if(Accounts.Any(a=>a.Category==account.Category&&a.Name==account.Name&&a.Fields.SequenceEqual(account.Fields)))continue;
            var copy=account.Copy();if(Accounts.Any(a=>a.Id==copy.Id))copy.Id=Guid.NewGuid().ToString();
            Accounts.Add(copy);count++;
        }
        foreach(var note in incoming.Notes)
        {
            if(Notes.Any(n=>n.Title==note.Title&&n.Content==note.Content&&n.Pinned==note.Pinned))continue;
            var copy=note.Copy();if(Notes.Any(n=>n.Id==copy.Id))copy.Id=Guid.NewGuid().ToString();
            Notes.Add(copy);count++;
        }
        return count;
    }
    public IEnumerable<VaultNote> SearchNotes(string query)
    {
        query=query.Trim();return Notes.Where(n=>query.Length==0||n.Title.Contains(query,StringComparison.OrdinalIgnoreCase)||n.Content.Contains(query,StringComparison.OrdinalIgnoreCase)).OrderByDescending(n=>n.Pinned).ThenByDescending(n=>n.UpdatedAt);
    }
    public IEnumerable<VaultAccount> Search(string query,string category="",bool favoritesOnly=false)
    {
        query=query.Trim();
        return Accounts.Where(a=>(category.Length==0||a.Category==category)&&(!favoritesOnly||a.Favorite)&&
            (query.Length==0||new[]{a.Category,a.Name}.Concat(a.Fields.SelectMany(f=>new[]{f.Label,f.Value})).Any(s=>s.Contains(query,StringComparison.OrdinalIgnoreCase))));
    }
}
