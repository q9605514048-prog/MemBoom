using MemBoom.Core;
using MemBoom.Core.Sync;

namespace MemBoom.Windows;

internal sealed class DevicesDialog:Form
{
    private readonly VaultSession session;
    private readonly string endpoint;
    private readonly TextBox admin=Theme.Input("Административный токен",true);
    private readonly ListView devices=new(){Dock=DockStyle.Fill,View=View.Details,FullRowSelect=true,MultiSelect=false,HideSelection=false,AccessibleName="Подключённые устройства",MinimumSize=new Size(560,220)};
    private readonly Label message=new(){AutoSize=true,MaximumSize=new Size(730,0),ForeColor=Theme.Muted,Margin=new Padding(4,8,4,8)};
    private readonly Button refresh,revoke;
    private readonly CancellationTokenSource lifetime=new();
    private string? loadedToken;
    private bool busy;
    private DeviceInfo? Selected=>devices.SelectedItems.Count==1?devices.SelectedItems[0].Tag as DeviceInfo:null;
    public DevicesDialog(VaultSession vault,string server)
    {
        session=vault;endpoint=server;Theme.Form(this);Text="Подключённые устройства";Size=new Size(820,700);MinimumSize=new Size(680,600);MaximizeBox=false;MinimizeBox=false;
        var root=new TableLayoutPanel{Dock=DockStyle.Fill,ColumnCount=1,RowCount=7,Padding=new Padding(20)};
        root.ColumnStyles.Add(new(SizeType.Percent,100));
        for(var i=0;i<7;i++)root.RowStyles.Add(new(i==4?SizeType.Percent:SizeType.AutoSize,i==4?100:0));
        root.Controls.Add(Theme.Label("Устройства и доступ",true));
        root.Controls.Add(new Label{Text="Введите административный токен этого сервера. Он не сохраняется. Отзыв блокирует будущие запросы устройства к API; уже загруженные локальные данные остаются на нём.",AutoSize=true,MaximumSize=new Size(730,0),ForeColor=Theme.Muted,Margin=new Padding(4,8,4,12)});
        root.Controls.Add(Theme.Label(admin.AccessibleName!));root.Controls.Add(admin);
        devices.BackColor=Theme.Panel;devices.ForeColor=Theme.Text;devices.Columns.Add("Устройство",240);devices.Columns.Add("Доступ",140);devices.Columns.Add("Создано",170);devices.Columns.Add("ID",260);root.Controls.Add(devices);
        root.Controls.Add(message);var actions=new FlowLayoutPanel{AutoSize=true,Dock=DockStyle.Top,WrapContents=true};
        refresh=Theme.Button("Обновить список",()=>_ = LoadDevices(),true);
        revoke=Theme.Button("Отозвать доступ",()=>_ = Revoke());revoke.Enabled=false;
        actions.Controls.AddRange([refresh,revoke,Theme.Button("Закрыть",Close)]);root.Controls.Add(actions);Controls.Add(root);
        admin.TextChanged+=(_,_)=>{loadedToken=null;devices.Items.Clear();SetBusy(busy);message.Text="Обновите список с новым токеном.";};
        devices.SelectedIndexChanged+=(_,_)=>SetBusy(busy);message.Text="Список ещё не загружен.";
        FormClosing+=(_,_)=>lifetime.Cancel();FormClosed+=(_,_)=>{admin.Clear();loadedToken=null;devices.Items.Clear();lifetime.Dispose();};
    }
    private void SetBusy(bool value){busy=value;admin.Enabled=refresh.Enabled=devices.Enabled=!value;revoke.Enabled=!value&&loadedToken!=null&&Selected is{IsRevoked:false};}
    private bool Current(long version)=>!IsDisposed&&!lifetime.IsCancellationRequested&&session.IsUnlocked&&session.Version==version;
    private async Task LoadDevices()
    {
        if(busy||!session.IsUnlocked)return;SetBusy(true);loadedToken=null;devices.Items.Clear();var version=session.Version;
        message.Text="Загружаем устройства…";
        try
        {
            using var client=new VaultSyncClient(endpoint,admin.Text.Trim());var result=await client.ListDevicesAsync(lifetime.Token);
            if(!Current(version))return;
            Populate(result);loadedToken=admin.Text.Trim();message.Text=$"Устройств: {result.Count}. Выберите активное устройство для отзыва доступа.";
        }
        catch(Exception error){if(!IsDisposed)message.Text=Error(error);}
        finally{if(!IsDisposed)SetBusy(false);}
    }
    private void Populate(IReadOnlyList<DeviceInfo> items)
    {
        devices.BeginUpdate();try
        {
            devices.Items.Clear();foreach(var item in items.OrderBy(d=>d.IsRevoked).ThenBy(d=>d.Name))
            {
                var own=item.DeviceId==session.Connection?.DeviceId?" (этот ПК)":"";
                devices.Items.Add(new ListViewItem([item.Name+own,item.IsRevoked?"Отозван":"Активен",item.CreatedAt.ToLocalTime().ToString("dd.MM.yyyy HH:mm"),item.DeviceId]){Tag=item});
            }
        }finally{devices.EndUpdate();}
    }
    private async Task Revoke()
    {
        if(busy||Selected is not{IsRevoked:false} selected||loadedToken==null||!session.IsUnlocked)return;
        var own=selected.DeviceId==session.Connection?.DeviceId?" Это подключение данного ПК.":"";
        if(MessageBox.Show(this,$"Отозвать доступ у «{selected.Name}»?{own} Устройство перестанет обращаться к серверу. Локальные записи на нём не удалятся.","Отзыв доступа",MessageBoxButtons.YesNo,MessageBoxIcon.Warning)!=DialogResult.Yes)return;
        if(IsDisposed||!session.IsUnlocked||lifetime.IsCancellationRequested)return;
        SetBusy(true);var version=session.Version;var credential=loadedToken;devices.Items.Clear();loadedToken=null;message.Text="Отзываем доступ…";
        try
        {
            using var client=new VaultSyncClient(endpoint,credential);await client.RevokeDeviceAsync(selected.DeviceId,lifetime.Token);
            if(!Current(version))return;
            message.Text="Доступ отозван. Обновите список для проверки состояния. Сохранённое подключение на этом ПК не удалено.";
        }
        catch(Exception error){if(!IsDisposed)message.Text=Error(error)+" Запрос мог быть принят сервером; обновите список перед повтором.";}
        finally{if(!IsDisposed)SetBusy(false);}
    }
    private static string Error(Exception error)=>error is ArgumentException or InvalidDataException?error.Message:"Не удалось подтвердить операцию. Проверьте административный токен, адрес и подключение.";
    internal void SmokeCheck(string directory)
    {
        if(!admin.UseSystemPasswordChar||revoke.Enabled)throw new Exception("Device management initial state invalid");
        Populate([new("qa-active","QA Phone",DateTimeOffset.UtcNow,null,false),new("qa-old","Old PC",DateTimeOffset.UtcNow,DateTimeOffset.UtcNow,true)]);
        loadedToken="qa-smoke-token-123456";devices.Items[0].Selected=true;SetBusy(false);if(!revoke.Enabled)throw new Exception("Active device cannot be selected");
        devices.Items[0].Selected=false;devices.Items[1].Selected=true;SetBusy(false);if(revoke.Enabled)throw new Exception("Revoked device action enabled");
        PerformLayout();Update();Application.DoEvents();using var image=new Bitmap(Width,Height);DrawToBitmap(image,new Rectangle(0,0,Width,Height));image.Save(Path.Combine(directory,"devices-preview.png"));
    }
}
