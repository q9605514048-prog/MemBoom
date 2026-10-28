using MemBoom.Core;

namespace MemBoom.Windows;

internal sealed class NoteEditor:Form
{
    private readonly TextBox title=Theme.Input("Название заметки");
    private readonly TextBox content=Theme.Input("Текст заметки");
    private readonly CheckBox pin=new(){Text="Закрепить заметку",AutoSize=true,ForeColor=Theme.Muted};
    private readonly VaultNote original;
    public VaultNote? Result{get;private set;}
    public NoteEditor(VaultNote? note=null)
    {
        original=note?.Copy()??new();Theme.Form(this);Text=note==null?"Новая заметка":"Изменить заметку";Size=new Size(760,670);MinimumSize=new Size(540,460);MinimizeBox=false;
        var root=new TableLayoutPanel{Dock=DockStyle.Fill,Padding=new Padding(24),ColumnCount=1,RowCount=7};root.ColumnStyles.Add(new(SizeType.Percent,100));
        for(var i=0;i<7;i++)root.RowStyles.Add(new(i==3?SizeType.Percent:SizeType.AutoSize,i==3?100:0));
        root.Controls.Add(Theme.Label(Text,true));root.Controls.Add(Theme.Label("Название"));title.Text=original.Title;title.MaxLength=200;root.Controls.Add(title);
        content.Multiline=true;content.AcceptsReturn=true;content.AcceptsTab=true;content.ScrollBars=ScrollBars.Vertical;content.Dock=DockStyle.Fill;content.MaxLength=500000;content.Text=original.Content;content.Font=new Font("Segoe UI",12);root.Controls.Add(content);
        pin.Checked=original.Pinned;root.Controls.Add(pin);var error=new Label{AutoSize=true,ForeColor=Theme.Red};root.Controls.Add(error);
        var actions=new FlowLayoutPanel{AutoSize=true,Dock=DockStyle.Top};
        actions.Controls.Add(Theme.Button("Сохранить заметку",()=>{if(!TryBuild()){error.Text="Введите название заметки.";return;}DialogResult=DialogResult.OK;},true));
        var cancel=Theme.Button("Отмена",()=>DialogResult=DialogResult.Cancel);actions.Controls.Add(cancel);root.Controls.Add(actions);CancelButton=cancel;Controls.Add(root);
        Shown+=(_,_)=>title.Focus();FormClosing+=(_,e)=>
        {
            if(DialogResult==DialogResult.OK||Owner is{IsDisposed:true}||Owner?.Tag as string=="locking")return;
            if(title.Text==original.Title&&content.Text==original.Content&&pin.Checked==original.Pinned)return;
            if(MessageBox.Show(this,"Закрыть заметку без сохранения изменений?","Несохранённая заметка",MessageBoxButtons.YesNo,MessageBoxIcon.Question)!=DialogResult.Yes)e.Cancel=true;
        };
    }
    private bool TryBuild()
    {
        if(string.IsNullOrWhiteSpace(title.Text))return false;
        Result=original.Copy();Result.Title=title.Text.Trim();Result.Content=content.Text;Result.Pinned=pin.Checked;Result.UpdatedAt=DateTimeOffset.UtcNow;return true;
    }
    internal void SmokeCheck(){title.Text="QA note";content.Text="Две строки\r\nТестовый текст";pin.Checked=true;if(!TryBuild()||Result!.Content!=content.Text||!Result.Pinned)throw new Exception("Note editor failed");DialogResult=DialogResult.OK;}
}
