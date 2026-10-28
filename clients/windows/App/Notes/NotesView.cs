using MemBoom.Core;

namespace MemBoom.Windows;

internal sealed class NotesView:UserControl
{
    private readonly VaultSession session;
    private readonly TextBox search=Theme.Input("Поиск заметок");
    private readonly ListBox list=new(){Dock=DockStyle.Fill,DrawMode=DrawMode.OwnerDrawFixed,ItemHeight=90,BorderStyle=BorderStyle.None,IntegralHeight=false,AccessibleName="Заметки"};
    private readonly Label title=Theme.Label("Ваши мысли — в одном месте",true);
    private readonly Label meta=Theme.Label("");
    private readonly RichTextBox body=new(){Dock=DockStyle.Fill,ReadOnly=true,BorderStyle=BorderStyle.None,DetectUrls=false,Font=new Font("Segoe UI",12),BackColor=Theme.Panel,ForeColor=Theme.Text,AccessibleName="Текст заметки"};
    private readonly Label count=Theme.Label("");
    private VaultNote? Selected=>list.SelectedItem as VaultNote;
    public NotesView(VaultSession vault)
    {
        session=vault;Dock=DockStyle.Fill;BackColor=Theme.Background;DoubleBuffered=true;
        var root=new TableLayoutPanel{Dock=DockStyle.Fill,ColumnCount=1,RowCount=5};root.ColumnStyles.Add(new(SizeType.Percent,100));
        root.RowStyles.Add(new(SizeType.AutoSize));root.RowStyles.Add(new(SizeType.AutoSize));root.RowStyles.Add(new(SizeType.AutoSize));root.RowStyles.Add(new(SizeType.Percent,100));root.RowStyles.Add(new(SizeType.AutoSize));
        var heading=new FlowLayoutPanel{AutoSize=true,Dock=DockStyle.Top,FlowDirection=FlowDirection.TopDown};heading.Controls.Add(Theme.Label("Заметки",true));heading.Controls.Add(Theme.Label("Идеи, планы и важные детали. Только для вас."));root.Controls.Add(heading);
        var actions=new FlowLayoutPanel{AutoSize=true,Dock=DockStyle.Top,Padding=new Padding(0,10,0,8)};
        actions.Controls.Add(Theme.Button("＋ Новая заметка",()=>Guard(()=>Edit(null)),true));
        actions.Controls.Add(Theme.Button("Изменить",()=>Guard(()=>{if(Selected is{} n)Edit(n);})));
        actions.Controls.Add(Theme.Button("Закрепить / открепить",()=>Guard(TogglePin)));actions.Controls.Add(Theme.Button("Удалить",()=>Guard(Delete)));root.Controls.Add(actions);
        search.Dock=DockStyle.Top;search.PlaceholderText="Найти по заголовку или тексту…";root.Controls.Add(search);
        var split=new SplitContainer{Dock=DockStyle.Fill,Size=new Size(1000,400),SplitterDistance=320,Panel1MinSize=220,Panel2MinSize=280,BackColor=Theme.Background,SplitterWidth=14};
        list.BackColor=Theme.Background;list.ForeColor=Theme.Text;split.Panel1.Controls.Add(list);
        var detail=new TableLayoutPanel{Dock=DockStyle.Fill,Padding=new Padding(24),ColumnCount=1,RowCount=3,BackColor=Theme.Panel};
        detail.ColumnStyles.Add(new(SizeType.Percent,100));detail.RowStyles.Add(new(SizeType.AutoSize));detail.RowStyles.Add(new(SizeType.AutoSize));detail.RowStyles.Add(new(SizeType.Percent,100));
        title.MaximumSize=new Size(650,0);detail.Controls.Add(title);detail.Controls.Add(meta);detail.Controls.Add(body);split.Panel2.Controls.Add(detail);root.Controls.Add(split);root.Controls.Add(count);Controls.Add(root);
        search.TextChanged+=(_,_)=>RefreshNotes();list.SelectedIndexChanged+=(_,_)=>ShowNote();list.DoubleClick+=(_,_)=>Guard(()=>{if(Selected is{} n)Edit(n);});
        list.DrawItem+=(_,e)=>
        {
            if(e.Index<0)return;var note=(VaultNote)list.Items[e.Index];var selected=(e.State&DrawItemState.Selected)!=0;
            using var bg=new SolidBrush(Theme.Background);e.Graphics.FillRectangle(bg,e.Bounds);
            var area=e.Bounds;area.Inflate(-3,-4);using var path=Theme.Rounded(area,12);using var fill=new SolidBrush(selected?Color.FromArgb(63,33,44):Theme.Panel);e.Graphics.FillPath(fill,path);
            var text=new Rectangle(area.X+16,area.Y+12,area.Width-28,26);
            TextRenderer.DrawText(e.Graphics,(note.Pinned?"★  ":"")+note.Title,Font,text,note.Pinned?Theme.Gold:Theme.Text,TextFormatFlags.EndEllipsis|TextFormatFlags.SingleLine);
            text.Y+=29;TextRenderer.DrawText(e.Graphics,note.Content.Replace('\r',' ').Replace('\n',' '),Font,text,Theme.Muted,TextFormatFlags.EndEllipsis|TextFormatFlags.SingleLine);
        };
    }
    public void RefreshNotes(string? selectedId=null)
    {
        if(!session.IsUnlocked){ClearSensitive();return;}
        selectedId??=Selected?.Id;list.BeginUpdate();list.Items.Clear();foreach(var note in session.Document.SearchNotes(search.Text))list.Items.Add(note);list.EndUpdate();
        for(var i=0;i<list.Items.Count;i++)if(((VaultNote)list.Items[i]).Id==selectedId){list.SelectedIndex=i;break;}
        if(list.SelectedIndex<0&&list.Items.Count>0)list.SelectedIndex=0;ShowNote();
        count.Text=$"Заметок: {list.Items.Count} из {session.Document.Notes.Count}  ·  Зашифрованы на этом ПК  ·  Включены в резервную копию";
    }
    private void ShowNote()
    {
        var note=Selected;title.Text=note?.Title??"Место для новой идеи";meta.Text=note==null?"Создайте заметку или измените поиск":(note.Pinned?"★ Закреплено   ·   ":"")+"Изменено "+note.UpdatedAt.ToLocalTime().ToString("dd.MM.yyyy, HH:mm");
        body.Text=note?.Content??"Здесь удобно хранить планы, списки и мысли.\n\nНачните с кнопки «Новая заметка».";
    }
    private void Guard(Action action)
    {
        if(!session.IsUnlocked)return;try{action();}catch{MessageBox.Show(FindForm(),"Не удалось сохранить заметку. Предыдущая версия осталась без изменений.","Заметки",MessageBoxButtons.OK,MessageBoxIcon.Warning);RefreshNotes();}
    }
    private void Edit(VaultNote? note)
    {
        using var editor=new NoteEditor(note);if(editor.ShowDialog(FindForm())!=DialogResult.OK||editor.Result==null||!session.IsUnlocked)return;
        Save(editor.Result);
    }
    private void Save(VaultNote note)
    {
        session.Apply(d=>{var index=d.Notes.FindIndex(n=>n.Id==note.Id);if(index<0)d.Notes.Add(note.Copy());else d.Notes[index]=note.Copy();});RefreshNotes(note.Id);
    }
    private void TogglePin(){if(Selected is not{} note)return;var copy=note.Copy();copy.Pinned=!copy.Pinned;copy.UpdatedAt=DateTimeOffset.UtcNow;Save(copy);}
    private void Delete()
    {
        if(Selected is not{} note)return;if(MessageBox.Show(FindForm(),$"Удалить заметку «{note.Title}»?","Удаление заметки",MessageBoxButtons.YesNo,MessageBoxIcon.Question)!=DialogResult.Yes||!session.IsUnlocked)return;
        session.Apply(d=>d.Notes.RemoveAll(n=>n.Id==note.Id));RefreshNotes();
    }
    public void ClearSensitive(){list.Items.Clear();body.Clear();title.Text="Заметки";meta.Text="";search.Clear();count.Text="";}
    internal void SmokeCheck(string directory)
    {
        var id=Guid.NewGuid().ToString();Save(new(){Id=id,Title="Идеи на выходные",Content="Прогулка по городу\nНовый плейлист\nВремя для любимой книги",Pinned=true});
        Save(new(){Title="Планы на неделю",Content="Собрать все важные мысли в одном месте."});
        search.Text="плейлист";if(list.Items.Count!=1||Selected?.Id!=id)throw new Exception("Notes UI search failed");search.Clear();RefreshNotes(id);
        TogglePin();if(session.Document.Notes.Single(n=>n.Id==id).Pinned)throw new Exception("Note pin failed");TogglePin();
        using(var editor=new NoteEditor()){editor.Show(FindForm());Application.DoEvents();editor.SmokeCheck();editor.Close();}
        PerformLayout();Update();Application.DoEvents();using var image=new Bitmap(Width,Height);DrawToBitmap(image,new Rectangle(0,0,Width,Height));image.Save(Path.Combine(directory,"notes-preview.png"));
    }
}
