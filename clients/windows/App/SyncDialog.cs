using MemBoom.Core;
using MemBoom.Core.Sync;
using System.Security.Cryptography;

namespace MemBoom.Windows;

internal sealed class SyncDialog:Form
{
    private readonly VaultSession session;
    private readonly TextBox endpoint=Theme.Input("Адрес сервера");
    private readonly TextBox token=Theme.Input("Токен устройства",true);
    private readonly TextBox password=Theme.Input("Пароль синхронизации",true);
    private readonly Label summary=new(){AutoSize=true,MaximumSize=new Size(640,0),ForeColor=Theme.Muted,Margin=new Padding(4,12,4,12)};
    private readonly Button previewButton,download,upload,merge,register,save,forget,manage;
    private string deviceId="",deviceName="";
    private readonly CancellationTokenSource lifetime=new();
    private VaultSyncClient? client;
    private VaultSyncService? service;
    private SyncPreview? preview;
    private bool busy;
    public SyncDialog(VaultSession vault)
    {
        session=vault;Theme.Form(this);Text="Синхронизация";Size=new Size(740,790);MinimumSize=new Size(650,650);MaximizeBox=false;MinimizeBox=false;
        var root=new TableLayoutPanel{Dock=DockStyle.Fill,ColumnCount=1,Padding=new Padding(20),AutoScroll=true};
        root.ColumnStyles.Add(new(SizeType.Percent,100));
        root.Controls.Add(Theme.Label("Синхронизация устройств",true));
        root.Controls.Add(new Label{Text="Адрес и токен можно сохранить под локальным мастер-паролем. Общий пароль шифрования вводится заново. Локальный мастер-пароль может отличаться.",AutoSize=true,MaximumSize=new Size(650,0),ForeColor=Theme.Muted,Margin=new Padding(4,8,4,12)});
        endpoint.Text=session.Connection?.Endpoint??"http://127.0.0.1:8788";token.Text=session.Connection?.Token??"";
        deviceId=session.Connection?.DeviceId??"";deviceName=session.Connection?.DeviceName??"";
        foreach(var field in new[]{endpoint,token,password}){root.Controls.Add(Theme.Label(field.AccessibleName!));root.Controls.Add(field);field.TextChanged+=(_,_)=>InvalidatePreview();}
        token.TextChanged+=(_,_)=>{deviceId="";deviceName="";};
        var connectionActions=new FlowLayoutPanel{AutoSize=true,Dock=DockStyle.Top,WrapContents=true};
        register=Theme.Button("Зарегистрировать ПК",RegisterDevice);
        save=Theme.Button("Сохранить подключение",()=>
        {
            try{session.SaveConnection(new(endpoint.Text.Trim(),token.Text.Trim(),deviceId,deviceName));InvalidatePreview();summary.Text="Подключение сохранено в зашифрованном хранилище. Пароль синхронизации не сохраняется.";}
            catch(Exception error){summary.Text=ErrorText(error);}
        });
        forget=Theme.Button("Забыть подключение",()=>
        {
            try{session.SaveConnection(null);token.Clear();deviceId=deviceName="";InvalidatePreview();summary.Text="Подключение удалено из текущего хранилища. Серверный токен не отозван; предыдущая зашифрованная версия может содержать его.";}
            catch(Exception error){summary.Text=ErrorText(error);}
        });
        connectionActions.Controls.AddRange([register,save,forget]);root.Controls.Add(connectionActions);
        manage=Theme.Button("Подключённые устройства",()=>{if(!busy&&session.IsUnlocked){using var dialog=new DevicesDialog(session,endpoint.Text.Trim());dialog.ShowDialog(this);}});root.Controls.Add(manage);
        root.Controls.Add(new Label{Text="Для удалённого сервера нужен HTTPS. Пароль шифрования должен совпадать с паролем синхронизации на телефоне.",AutoSize=true,MaximumSize=new Size(650,0),ForeColor=Theme.Muted,Margin=new Padding(4,8,4,8)});
        previewButton=Theme.Button("Проверить сервер",()=>_ = LoadPreview(),true);root.Controls.Add(previewButton);
        summary.Text="Сначала проверьте сервер. Данные не отправляются автоматически.";root.Controls.Add(summary);
        var actions=new FlowLayoutPanel{AutoSize=true,Dock=DockStyle.Top,WrapContents=true};
        download=Theme.Button("Загрузить с сервера",()=>_ = Apply(SyncChoice.Download));
        upload=Theme.Button("Отправить локальные",()=>_ = Apply(SyncChoice.Upload));
        merge=Theme.Button("Объединить и отправить",()=>_ = Apply(SyncChoice.Merge));
        actions.Controls.AddRange([download,upload,merge]);root.Controls.Add(actions);
        var close=Theme.Button("Закрыть",Close);root.Controls.Add(close);CancelButton=close;AcceptButton=previewButton;
        Controls.Add(root);SetBusy(false);
        FormClosing+=(_,_)=>lifetime.Cancel();
        FormClosed+=(_,_)=>{token.Clear();password.Clear();preview=null;client?.Dispose();client=null;service=null;lifetime.Dispose();};
    }
    private void InvalidatePreview(){preview=null;if(download!=null)SetBusy(busy);}
    private void SetBusy(bool value)
    {
        busy=value;endpoint.Enabled=token.Enabled=password.Enabled=previewButton.Enabled=!value;
        register.Enabled=save.Enabled=forget.Enabled=manage.Enabled=!value;
        download.Enabled=!value&&preview?.Remote!=null;upload.Enabled=merge.Enabled=!value&&preview!=null;
    }
    private void RegisterDevice()
    {
        if(busy||!session.IsUnlocked)return;
        using var dialog=new RegisterDeviceDialog(session,endpoint.Text.Trim());
        if(dialog.ShowDialog(this)!=DialogResult.OK||dialog.Connection==null||IsDisposed||!session.IsUnlocked)return;
        token.Text=dialog.Connection.Token;deviceId=dialog.Connection.DeviceId;deviceName=dialog.Connection.DeviceName;
        InvalidatePreview();summary.Text="Устройство зарегистрировано. Нажмите «Сохранить подключение», чтобы запомнить его токен на этом ПК.";
    }
    private async Task LoadPreview()
    {
        if(busy)return;SetBusy(true);preview=null;summary.Text="Загружаем и проверяем серверную копию…";
        try
        {
            client?.Dispose();client=new VaultSyncClient(endpoint.Text.Trim(),token.Text.Trim());service=new VaultSyncService(client);
            preview=await service.PreviewAsync(session,password.Text,lifetime.Token);
            if(IsDisposed)return;
            summary.Text=$"Сервер: ревизия {preview.Revision}, аккаунтов {preview.Remote?.Accounts.Count??0}. На этом ПК: {preview.Local.Accounts.Count}.\n"+
                "Загрузка заменит локальные записи. Объединение сохранит обе отличающиеся версии. Отправка заменит серверную копию.";
        }
        catch(Exception error){if(!IsDisposed)summary.Text=ErrorText(error);}
        finally{if(!IsDisposed)SetBusy(false);}
    }
    private async Task Apply(SyncChoice choice)
    {
        if(busy||preview==null||service==null)return;
        var message=choice switch
        {
            SyncChoice.Download=>"Заменить локальные записи загруженной серверной копией? Текущая зашифрованная версия останется в файле .previous.",
            SyncChoice.Upload=>"Заменить серверную копию локальными записями? Это действие влияет на данные, которые загрузят другие устройства.",
            _=>"Объединить записи с серверной копией и отправить результат? Отличающиеся записи сохранятся отдельно."
        };
        if(MessageBox.Show(this,message,"Подтвердите действие",MessageBoxButtons.YesNo,MessageBoxIcon.Question)!=DialogResult.Yes)return;
        if(IsDisposed||lifetime.IsCancellationRequested||!session.IsUnlocked)return;
        SetBusy(true);summary.Text="Выполняем синхронизацию…";
        try
        {
            var revision=await service.ApplyAsync(session,preview,choice,password.Text,lifetime.Token);
            if(IsDisposed)return;
            preview=null;summary.Text=$"Готово. Ревизия: {revision}. Для следующего действия снова проверьте сервер.";
        }
        catch(Exception error){if(!IsDisposed){preview=null;summary.Text=ErrorText(error)+" Обновите предпросмотр перед повтором.";}}
        finally{if(!IsDisposed)SetBusy(false);}
    }
    private static string ErrorText(Exception error)=>error switch
    {
        SyncConflictException=>error.Message,
        CryptographicException=>"Не удалось расшифровать серверную копию. Проверьте общий пароль синхронизации.",
        OperationCanceledException=>"Операция отменена или сервер не ответил вовремя. Проверьте сервер перед повторной отправкой.",
        ArgumentException or InvalidDataException or InvalidOperationException=>error.Message,
        _=>"Не удалось выполнить обмен. Проверьте адрес, подключение и формат ответа. Локальные данные сохранены."
    };
    internal void SmokeCheck(string directory)
    {
        if(!token.UseSystemPasswordChar||!password.UseSystemPasswordChar||download.Enabled||upload.Enabled||merge.Enabled||!previewButton.Enabled)
            throw new Exception("Sync dialog initial state is invalid");
        if(!register.Enabled||!save.Enabled||!forget.Enabled)throw new Exception("Connection actions unavailable");
        using(var registration=new RegisterDeviceDialog(session,endpoint.Text)){registration.Show(this);Application.DoEvents();registration.SmokeCheck();registration.Close();}
        using(var management=new DevicesDialog(session,endpoint.Text)){management.Show(this);Application.DoEvents();management.SmokeCheck(directory);management.Close();}
        PerformLayout();Update();Application.DoEvents();
        using var image=new Bitmap(Width,Height);DrawToBitmap(image,new Rectangle(0,0,Width,Height));image.Save(Path.Combine(directory,"sync-preview.png"));
    }
}
