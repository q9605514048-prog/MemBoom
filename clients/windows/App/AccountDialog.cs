using MemBoom.Core;
using MemBoom.Core.Auth;
using MemBoom.Core.Sync;
using System.Security.Cryptography;

namespace MemBoom.Windows;

internal sealed class AccountDialog:Form
{
    private readonly VaultSession vault;
    private readonly TableLayoutPanel root=new(){Dock=DockStyle.Fill,ColumnCount=1,Padding=new Padding(24),AutoScroll=true};
    private readonly CancellationTokenSource lifetime=new();
    private string endpoint,mode="welcome";
    private bool busy,disposed;
    public AccountDialog(VaultSession session)
    {
        vault=session;endpoint=session.Connection?.Endpoint??"http://127.0.0.1:8788";
        Theme.Form(this);Text="Аккаунт MemBoom";Size=new Size(680,670);MinimumSize=new Size(600,560);
        root.ColumnStyles.Add(new(SizeType.Percent,100));Controls.Add(root);Draw();FormClosing+=(_,_)=>lifetime.Cancel();
    }
    private void Draw()
    {
        foreach(Control c in root.Controls.Cast<Control>().ToArray())c.Dispose();root.Controls.Clear();
        root.Controls.Add(Theme.Label(mode=="welcome"?"Добро пожаловать в MemBoom":mode=="register"?"Создать аккаунт":"Войти в MemBoom",true));
        if(mode=="welcome"){
            root.Controls.Add(Theme.Label("Создайте аккаунт или войдите. Приложение запомнит вход на этом ПК."));
            root.Controls.Add(Theme.Button("Создать аккаунт",()=>{mode="register";Draw();},true));
            root.Controls.Add(Theme.Button("Войти",()=>{mode="login";Draw();}));
            if(vault.Connection?.Token.StartsWith("mbs_")==true)root.Controls.Add(Theme.Button("Выйти из аккаунта",Logout));
            root.Controls.Add(Theme.Button("Закрыть",Close));return;
        }
        TextBox Field(string title,bool secret=false){root.Controls.Add(Theme.Label(title));var box=Theme.Input(title,secret);root.Controls.Add(box);return box;}
        var address=Field("Адрес сервера MemBoom");address.Text=endpoint;
        var login=Field("Логин");login.Text=vault.Connection?.DeviceName??"";
        var password=Field("Пароль",true);var repeat=mode=="register"?Field("Повторите пароль",true):null;
        var status=Theme.Label("");status.MaximumSize=new Size(580,0);root.Controls.Add(status);
        var accept=Theme.Button(mode=="register"?"Создать аккаунт":"Войти",()=>{},true);root.Controls.Add(accept);
        var back=Theme.Button("Назад",()=>{if(!busy){endpoint=address.Text.Trim();mode="welcome";Draw();}});root.Controls.Add(back);
        accept.Click+=async(_,_)=>{
            if(busy)return;if(repeat!=null&&password.Text!=repeat.Text){status.Text="Пароли не совпадают.";return;}
            busy=true;accept.Enabled=back.Enabled=false;var version=vault.Version;var action=mode;
            try{
                endpoint=address.Text.Trim();using var client=new AccountAuthClient(endpoint);
                var account=action=="register"?await client.RegisterAsync(login.Text,password.Text,lifetime.Token):await client.LoginAsync(login.Text,password.Text,lifetime.Token);
                if(IsDisposed||lifetime.IsCancellationRequested||vault.Version!=version)return;
                if(!vault.IsUnlocked){
                    if(vault.IsFirstRun)vault.Unlock(password.Text);
                    else{
                        using var unlock=new PasswordDialog("Откройте прежние локальные записи",unlockSession:vault);
                        if(unlock.ShowDialog(this)!=DialogResult.OK)return;
                    }
                }
                vault.SaveConnection(new SyncConnection(endpoint,account.Token,"",account.User.Login));
                DeviceSignIn.Save(vault,account);DialogResult=DialogResult.OK;
            }catch(Exception error){if(!IsDisposed)status.Text=error is ArgumentException or InvalidOperationException?error.Message:"Не удалось подключиться. Проверьте сервер.";}
            finally{if(!IsDisposed){busy=false;accept.Enabled=back.Enabled=true;password.Clear();repeat?.Clear();}}
        };
    }
    private void Logout()
    {
        if(busy)return;busy=true;var connection=vault.Connection;
        try{
            DeviceSignIn.Forget(vault);vault.SaveConnection(null);vault.Lock();DialogResult=DialogResult.OK;
            if(connection!=null)_=Revoke(connection);
        }catch{if(!IsDisposed)MessageBox.Show(this,"Не удалось удалить сохранённый вход. Проверьте доступ к файлам приложения.","MemBoom");}
        finally{busy=false;}
    }
    private static async Task Revoke(SyncConnection connection){try{using var client=new AccountAuthClient(connection.Endpoint);await client.LogoutAsync(connection.Token,CancellationToken.None);}catch{/* Local sign-out already removed device access. */}}
    internal void SmokeCheck(string directory){mode="register";Draw();PerformLayout();Application.DoEvents();using var image=new Bitmap(Width,Height);DrawToBitmap(image,new Rectangle(0,0,Width,Height));image.Save(Path.Combine(directory,"account-preview.png"));DeviceSignIn.Check(directory);}
    protected override void Dispose(bool disposing){if(disposing&&!disposed){disposed=true;lifetime.Cancel();lifetime.Dispose();}base.Dispose(disposing);}
}
