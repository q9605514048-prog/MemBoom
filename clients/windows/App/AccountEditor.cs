using MemBoom.Core;

namespace MemBoom.Windows;

internal sealed class AccountEditor:Form
{
    public VaultAccount? Result{get;private set;}
    public AccountEditor(IEnumerable<string> categories,VaultAccount? existing=null)
    {
        Theme.Form(this);Text=existing==null?"Новый аккаунт":"Изменить аккаунт";Size=new Size(850,650);MinimumSize=new Size(720,540);
        var root=new TableLayoutPanel{Dock=DockStyle.Fill,Padding=new Padding(18),ColumnCount=1,RowCount=6};
        root.RowStyles.Add(new(SizeType.AutoSize));root.RowStyles.Add(new(SizeType.AutoSize));root.RowStyles.Add(new(SizeType.AutoSize));root.RowStyles.Add(new(SizeType.Percent,100));root.RowStyles.Add(new(SizeType.AutoSize));root.RowStyles.Add(new(SizeType.AutoSize));
        root.Controls.Add(Theme.Label(Text,true));
        var identity=new TableLayoutPanel{Dock=DockStyle.Top,AutoSize=true,ColumnCount=2};identity.ColumnStyles.Add(new(SizeType.Percent,38));identity.ColumnStyles.Add(new(SizeType.Percent,62));
        var category=new ComboBox{Dock=DockStyle.Fill,BackColor=Theme.Panel,ForeColor=Theme.Text,AccessibleName="Категория"};category.Items.AddRange(categories.Cast<object>().ToArray());category.Text=existing?.Category??"";
        var name=Theme.Input("Название аккаунта");name.Text=existing?.Name??"";
        identity.Controls.Add(Theme.Label("Категория"),0,0);identity.Controls.Add(Theme.Label("Название аккаунта"),1,0);identity.Controls.Add(category,0,1);identity.Controls.Add(name,1,1);root.Controls.Add(identity);
        root.Controls.Add(Theme.Label("Именованные поля — добавляйте логины, пароли, коды и заметки."));
        var fields=new DataGridView{Dock=DockStyle.Fill,BackgroundColor=Theme.Panel,BorderStyle=BorderStyle.None,AutoSizeColumnsMode=DataGridViewAutoSizeColumnsMode.Fill,AllowUserToAddRows=true,AllowUserToDeleteRows=true,RowHeadersVisible=false,SelectionMode=DataGridViewSelectionMode.CellSelect,AccessibleName="Поля аккаунта",EnableHeadersVisualStyles=false};
        fields.DefaultCellStyle.BackColor=Theme.Panel;fields.DefaultCellStyle.ForeColor=Theme.Text;fields.DefaultCellStyle.SelectionBackColor=Color.FromArgb(80,38,68);fields.DefaultCellStyle.SelectionForeColor=Theme.Text;
        fields.ColumnHeadersDefaultCellStyle.BackColor=Theme.Background;fields.ColumnHeadersDefaultCellStyle.ForeColor=Theme.Gold;
        fields.Columns.Add("label","Название поля");fields.Columns.Add("value","Значение");fields.Columns[0].FillWeight=35;fields.Columns[1].FillWeight=65;fields.RowTemplate.Height=34;
        foreach(var field in existing?.Fields??[new("Почта / логин",""),new("Пароль","")])fields.Rows.Add(field.Label,field.Value);
        var reveal=new CheckBox{Text="Показать секретные значения",AutoSize=true,ForeColor=Theme.Muted,Margin=new Padding(8,12,4,4)};
        fields.CellFormatting+=(_,e)=>{if(e.ColumnIndex==1&&e.RowIndex>=0&&!reveal.Checked&&PortableCrypto.IsSecret(fields.Rows[e.RowIndex].Cells[0].Value?.ToString()??"")&&!string.IsNullOrEmpty(e.Value?.ToString())){e.Value="••••••";e.FormattingApplied=true;}};
        fields.EditingControlShowing+=(_,e)=>{if(e.Control is TextBox input)input.UseSystemPasswordChar=fields.CurrentCell?.ColumnIndex==1&&!reveal.Checked&&PortableCrypto.IsSecret(fields.CurrentRow?.Cells[0].Value?.ToString()??"");};
        reveal.CheckedChanged+=(_,_)=>{fields.EndEdit();fields.Invalidate();};root.Controls.Add(fields);
        var tools=new FlowLayoutPanel{AutoSize=true,Dock=DockStyle.Top,WrapContents=true};
        tools.Controls.Add(Theme.Button("Добавить поле",()=>{int row=fields.Rows.Add("","");fields.CurrentCell=fields.Rows[row].Cells[0];fields.BeginEdit(true);}));
        tools.Controls.Add(Theme.Button("Удалить поле",()=>{if(fields.CurrentRow is{IsNewRow:false} row)fields.Rows.Remove(row);}));
        tools.Controls.Add(Theme.Button("Сгенерировать пароль",()=>{
            fields.EndEdit();var row=fields.Rows.Cast<DataGridViewRow>().FirstOrDefault(r=>!r.IsNewRow&&PortableCrypto.IsSecret(r.Cells[0].Value?.ToString()??""));
            if(row==null)row=fields.Rows[fields.Rows.Add("Пароль","")];row.Cells[1].Value=PortableCrypto.GeneratePassword();fields.Invalidate();
        }));tools.Controls.Add(reveal);root.Controls.Add(tools);
        var footer=new FlowLayoutPanel{AutoSize=true,Dock=DockStyle.Top,WrapContents=true};var error=new Label{AutoSize=true,MaximumSize=new Size(450,0),ForeColor=Theme.Red};
        var save=Theme.Button("Сохранить",()=>{
            fields.EndEdit();
            if(string.IsNullOrWhiteSpace(category.Text)||string.IsNullOrWhiteSpace(name.Text)){error.Text="Укажите категорию и название аккаунта.";return;}
            var values=new List<VaultField>();
            foreach(DataGridViewRow row in fields.Rows){if(row.IsNewRow)continue;var label=row.Cells[0].Value?.ToString()?.Trim()??"";var value=row.Cells[1].Value?.ToString()??"";if(label.Length==0&&value.Length==0)continue;if(label.Length==0){error.Text="Укажите названия всех заполненных полей.";return;}values.Add(new(label,value));}
            Result=new(){Id=existing?.Id??Guid.NewGuid().ToString(),Category=category.Text.Trim(),Name=name.Text.Trim(),Fields=values,Favorite=existing?.Favorite??false};DialogResult=DialogResult.OK;
        },true);
        var cancel=Theme.Button("Отмена",()=>DialogResult=DialogResult.Cancel);footer.Controls.Add(save);footer.Controls.Add(cancel);footer.Controls.Add(error);root.Controls.Add(footer);
        Controls.Add(root);AcceptButton=save;CancelButton=cancel;
    }
}
