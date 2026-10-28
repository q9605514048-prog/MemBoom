using MemBoom.Core;

namespace MemBoom.Windows;

internal sealed class PasswordDialog:Form
{
    public string Password{get;private set;}="";
    public string OldPassword{get;private set;}="";
    public PasswordDialog(string title,bool confirm=false,bool old=false,VaultSession? unlockSession=null)
    {
        Theme.Form(this);Text=title;Size=new Size(510,430);MinimumSize=new Size(470,380);MaximizeBox=false;MinimizeBox=false;
        var form=new TableLayoutPanel{Dock=DockStyle.Fill,Padding=new Padding(22),ColumnCount=1,AutoScroll=true};
        form.Controls.Add(Theme.Label(title,true));
        form.Controls.Add(new Label{AutoSize=true,MaximumSize=new Size(430,0),Text="От 4 символов: английские буквы, цифры и спецсимволы, без пробелов.",ForeColor=Theme.Muted,Margin=new Padding(4,6,4,12)});
        var previous=Theme.Input("Текущий мастер-пароль",true);
        if(old){form.Controls.Add(Theme.Label("Текущий мастер-пароль"));form.Controls.Add(previous);}
        var password=Theme.Input("Пароль",true);form.Controls.Add(Theme.Label(confirm?"Новый пароль":"Пароль"));form.Controls.Add(password);
        var repeat=Theme.Input("Повторите пароль",true);
        if(confirm){form.Controls.Add(Theme.Label("Повторите пароль"));form.Controls.Add(repeat);}
        var show=new CheckBox{Text="Показать пароль",AutoSize=true,ForeColor=Theme.Muted};show.CheckedChanged+=(_,_)=>{password.UseSystemPasswordChar=!show.Checked;repeat.UseSystemPasswordChar=!show.Checked;};form.Controls.Add(show);
        var error=new Label{AutoSize=true,MaximumSize=new Size(430,0),ForeColor=Theme.Red};form.Controls.Add(error);
        var buttons=new FlowLayoutPanel{AutoSize=true,Dock=DockStyle.Top};
        var accept=Theme.Button(unlockSession!=null?"Открыть хранилище":"Продолжить",()=>{
            if(!PortableCrypto.ValidPassword(password.Text)){error.Text="Проверьте допустимые символы и длину пароля.";return;}
            if(confirm&&password.Text!=repeat.Text){error.Text="Пароли не совпадают.";return;}
            if(old&&!PortableCrypto.ValidPassword(previous.Text)){error.Text="Введите текущий пароль.";return;}
            try{if(unlockSession!=null)unlockSession.Unlock(password.Text);}
            catch{error.Text="Не удалось открыть хранилище. Проверьте пароль и файл.";password.Clear();return;}
            Password=password.Text;OldPassword=previous.Text;DialogResult=DialogResult.OK;
        },true);
        var cancel=Theme.Button("Отмена",()=>{DialogResult=DialogResult.Cancel;});buttons.Controls.Add(accept);buttons.Controls.Add(cancel);form.Controls.Add(buttons);
        AcceptButton=accept;CancelButton=cancel;Controls.Add(form);Shown+=(_,_)=>password.Focus();
        if(old)Height=560;else if(confirm)Height=480;
    }
}
