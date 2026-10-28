using MemBoom.Core;
using MemBoom.Core.Sync;

namespace MemBoom.Windows;

internal sealed class RegisterDeviceDialog:Form
{
    public SyncConnection? Connection{get;private set;}
    private readonly CancellationTokenSource lifetime=new();
    private readonly TextBox admin=Theme.Input("Административный токен",true);
    public RegisterDeviceDialog(VaultSession session,string endpoint)
    {
        Theme.Form(this);Text="Регистрация этого ПК";Size=new Size(650,500);MinimumSize=new Size(600,460);MaximizeBox=false;MinimizeBox=false;
        var root=new TableLayoutPanel{Dock=DockStyle.Fill,ColumnCount=1,Padding=new Padding(20),AutoScroll=true};root.ColumnStyles.Add(new(SizeType.Percent,100));
        root.Controls.Add(Theme.Label("Новое устройство",true));
        root.Controls.Add(new Label{Text="На указанном сервере будет создан отдельный токен для этого ПК. Нужен административный токен вашего MemBoom API. Он используется только для регистрации и не сохраняется.",AutoSize=true,MaximumSize=new Size(560,0),ForeColor=Theme.Muted});
        root.Controls.Add(Theme.Label("Название устройства"));var name=Theme.Input("Название устройства");name.Text="Мой Windows ПК";root.Controls.Add(name);
        root.Controls.Add(Theme.Label(admin.AccessibleName!));root.Controls.Add(admin);
        var message=new Label{AutoSize=true,MaximumSize=new Size(560,0),ForeColor=Theme.Muted,Margin=new Padding(4,12,4,12)};root.Controls.Add(message);
        var register=Theme.Button("Зарегистрировать",()=>{},true);
        register.Click+=async(_,_)=>
        {
            if(!session.IsUnlocked)return;
            register.Enabled=false;admin.Enabled=name.Enabled=false;var version=session.Version;message.Text="Регистрируем устройство…";
            try
            {
                using var client=new VaultSyncClient(endpoint,admin.Text.Trim());
                var device=await client.RegisterDeviceAsync(name.Text,lifetime.Token);
                if(IsDisposed||!session.IsUnlocked||session.Version!=version)return;
                Connection=new(endpoint,device.DeviceToken,device.DeviceId,name.Text.Trim());DialogResult=DialogResult.OK;
            }
            catch(Exception error)
            {
                if(!IsDisposed)message.Text=error is ArgumentException?error.Message:"Регистрация не подтверждена. Проверьте адрес и административный токен. Если ответ потерян, устройство могло быть создано на сервере.";
            }
            finally{if(!IsDisposed){register.Enabled=true;admin.Enabled=name.Enabled=true;}}
        };
        root.Controls.Add(register);var cancel=Theme.Button("Отмена",Close);root.Controls.Add(cancel);CancelButton=cancel;Controls.Add(root);
        FormClosing+=(_,_)=>lifetime.Cancel();FormClosed+=(_,_)=>{admin.Clear();lifetime.Dispose();};
    }
    internal void SmokeCheck(){if(!admin.UseSystemPasswordChar)throw new Exception("Administrative token is visible");}
}
