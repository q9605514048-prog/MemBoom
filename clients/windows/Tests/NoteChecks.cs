using MemBoom.Core;

internal static class NoteChecks
{
    public static void Run(Action<string,Action> check)
    {
        check("notes persist encrypted and survive account synchronization changes",()=>
        {
            var directory=Path.Combine(Path.GetTempPath(),"MemBoom-notes-"+Guid.NewGuid().ToString("N"));
            try
            {
                var path=Path.Combine(directory,"vault.json");var session=new VaultSession(path);session.Unlock("QaNotes!123");
                session.Apply(d=>d.Notes.Add(new(){Title="Планы",Content="qa-private-note",Pinned=true}));
                session.Apply(d=>d.Accounts.Clear());session.Lock();session.Unlock("QaNotes!123");
                if(session.Document.Notes.Single().Content!="qa-private-note"||!session.Document.Notes[0].Pinned||File.ReadAllText(path).Contains("qa-private-note"))throw new Exception("Note persistence failed");
                session.ChangePassword("QaNotes!123","QaNew!123");session.Lock();session.Unlock("QaNew!123");
                if(session.Document.Notes.Count!=1)throw new Exception("Password change lost notes");
                var backup=VaultDocument.FromPortableJson(PortableCrypto.Decrypt(session.Export("QaBackup!123"),"QaBackup!123"));
                if(backup.Notes.Single().Content!="qa-private-note")throw new Exception("Backup lost notes");
                var accountsOnly=PortableCrypto.Decrypt(session.Export("QaBackup!123",false),"QaBackup!123");
                if(!accountsOnly.StartsWith('[')||accountsOnly.Contains("qa-private-note"))throw new Exception("Account-only export contains notes");
                try{session.Apply(d=>{d.Notes[0].Content="changed";d.Notes[0].Title="";});throw new Exception("Invalid mutation accepted");}catch(InvalidDataException){}
                if(session.Document.Notes[0].Content!="qa-private-note")throw new Exception("Failed change did not roll back");
                session.Lock();if(session.Document.Notes.Count!=0)throw new Exception("Lock retained notes");
            }finally{if(Directory.Exists(directory))Directory.Delete(directory,true);}
        });
        check("note merge preserves conflicting versions and is idempotent",()=>
        {
            var local=new VaultDocument();local.Notes.Add(new(){Id="same",Title="Заметка",Content="local"});
            var incoming=local.Copy();incoming.Notes[0].Content="remote";
            local.Merge(incoming);if(local.Notes.Count!=2||local.Notes.Select(n=>n.Id).Distinct().Count()!=2)throw new Exception("Conflict lost note");
            local.Merge(incoming);if(local.Notes.Count!=2)throw new Exception("Duplicate note imported");
        });
        check("note search and validation",()=>
        {
            var doc=new VaultDocument();doc.Notes.Add(new(){Title="Идея",Content="Текст для ПОИСКА"});
            if(doc.SearchNotes("поиска").Count()!=1||doc.SearchNotes("нет совпадений").Any())throw new Exception("Note search failed");
            doc.Notes.Add(doc.Notes[0].Copy());try{doc.Validate();throw new Exception("Duplicate note accepted");}catch(InvalidDataException){}
        });
    }
}
