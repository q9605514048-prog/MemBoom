using MemBoom.Core;
using Microsoft.Win32;

namespace MemBoom.Windows;

internal sealed class MainForm:Form,IMessageFilter
{
    private readonly VaultSession session;
    private readonly ListView accounts=new(){Dock=DockStyle.Fill,View=View.Details,FullRowSelect=true,HideSelection=false,MultiSelect=false,AccessibleName="Аккаунты"};
    private readonly FlowLayoutPanel details=new(){Dock=DockStyle.Fill,FlowDirection=FlowDirection.TopDown,WrapContents=false,AutoScroll=true,Padding=new Padding(12)};
    private readonly TextBox search=Theme.Input("Поиск");
    private readonly CategoryPicker categories=new(){Width=240,AccessibleName="Категория"};
    private readonly CheckBox favorites=new(){Text="Только избранное",AutoSize=true,ForeColor=Theme.Muted,Margin=new Padding(12,10,8,8)};
    private readonly Label status=new(){AutoSize=true,ForeColor=Theme.Muted,Margin=new Padding(4,8,4,8)};
    private readonly System.Windows.Forms.Timer idle=new(){Interval=1000};
    private readonly System.Windows.Forms.Timer clipboardTimer=new(){Interval=30000};
    private DateTime lastInput=DateTime.UtcNow;
    private string? clipboardValue;
    private bool updating,nativeDialog;
    private readonly SmoothPanel pageHost=new(){Dock=DockStyle.Fill,BackColor=Theme.Background};
    private readonly SmoothPanel accountPage=new(){BackColor=Theme.Background};
    private readonly NotesView notesView;
    private readonly System.Windows.Forms.Timer transition=new(){Interval=16};
    private readonly System.Diagnostics.Stopwatch transitionClock=new();
    private Control? currentPage;
    private readonly Button accountsNav,notesNav;
    private sealed record CategoryOption(string Label,string Value){public override string ToString()=>Label;}
    private VaultAccount? Selected=>accounts.SelectedItems.Count>0?accounts.SelectedItems[0].Tag as VaultAccount:null;

    public MainForm(VaultSession vault,bool smoke=false)
    {
        session=vault;notesView=new NotesView(session);Theme.Form(this);Text="MemBoom · Личное пространство";Size=new Size(1380,880);MinimumSize=new Size(1100,720);StartPosition=FormStartPosition.CenterScreen;
        var shell=new TableLayoutPanel{Dock=DockStyle.Fill,ColumnCount=2,RowCount=1,Margin=Padding.Empty};shell.ColumnStyles.Add(new(SizeType.Absolute,224));shell.ColumnStyles.Add(new(SizeType.Percent,100));shell.RowStyles.Add(new(SizeType.Percent,100));
        var sidebar=new FlowLayoutPanel{Dock=DockStyle.Fill,FlowDirection=FlowDirection.TopDown,WrapContents=false,BackColor=Theme.Sidebar,Padding=new Padding(14,22,14,18),Margin=Padding.Empty};
        var brand=Theme.Label("MemBoom",true);brand.ForeColor=Theme.Gold;sidebar.Controls.Add(brand);sidebar.Controls.Add(Theme.Label("Ваше личное пространство"));
        sidebar.Controls.Add(new Label{Height=18,Width=180});
        accountsNav=Theme.Button("▤   Аккаунты",()=>SwitchPage(false));notesNav=Theme.Button("✎   Заметки",()=>SwitchPage(true));
        void Nav(Button button){button.AutoSize=false;button.Width=190;button.Height=46;button.TextAlign=ContentAlignment.MiddleLeft;sidebar.Controls.Add(button);}
        Nav(accountsNav);Nav(notesNav);sidebar.Controls.Add(Theme.Label("ХРАНИЛИЩЕ"));
        Nav(Theme.Button("Аккаунт MemBoom",()=>Guard(()=>{using var dialog=new AccountDialog(session);dialog.ShowDialog(this);if(session.IsUnlocked)RefreshVault();else{ClearDetails();accounts.Items.Clear();notesView.ClearSensitive();StartAccount();}})));
        Nav(Theme.Button("Импорт копии",()=>Guard(Import)));Nav(Theme.Button("Экспорт копии",()=>Guard(Export)));
        Nav(Theme.Button("Синхронизация",()=>Guard(()=>{using var dialog=new SyncDialog(session);dialog.ShowDialog(this);if(session.IsUnlocked)RefreshVault();})));
        sidebar.Controls.Add(Theme.Label("БЕЗОПАСНОСТЬ"));Nav(Theme.Button("Мастер-пароль",()=>Guard(ChangePassword)));Nav(Theme.Button("Заблокировать",LockVault));
        var foot=Theme.Label("● Данные зашифрованы\n\nКрасная магия.\nСпокойный порядок.");foot.MaximumSize=new Size(185,0);foot.Margin=new Padding(4,24,4,4);sidebar.Controls.Add(foot);
        shell.Controls.Add(sidebar,0,0);shell.Controls.Add(pageHost,1,0);Controls.Add(shell);
        accountPage.Controls.Clear();pageHost.Controls.Add(accountPage);notesView.Dock=DockStyle.None;pageHost.Controls.Add(notesView);
        var root=new TableLayoutPanel{Dock=DockStyle.Fill,Padding=new Padding(0),ColumnCount=1,RowCount=5};root.ColumnStyles.Add(new(SizeType.Percent,100));
        root.RowStyles.Add(new(SizeType.AutoSize));root.RowStyles.Add(new(SizeType.AutoSize));root.RowStyles.Add(new(SizeType.AutoSize));root.RowStyles.Add(new(SizeType.Percent,100));root.RowStyles.Add(new(SizeType.AutoSize));
        var heading=new FlowLayoutPanel{Dock=DockStyle.Top,AutoSize=true,FlowDirection=FlowDirection.TopDown};heading.Controls.Add(Theme.Label("Ваши аккаунты",true));heading.Controls.Add(Theme.Label("Всё важное под рукой. И под защитой."));root.Controls.Add(heading);
        var toolbar=new FlowLayoutPanel{AutoSize=true,Dock=DockStyle.Fill,WrapContents=true};
        toolbar.Controls.Add(Theme.Button("＋ Добавить",()=>Guard(()=>EditAccount(null)),true));
        toolbar.Controls.Add(Theme.Button("Изменить",()=>Guard(()=>{if(Selected is{} a)EditAccount(a);} )));
        toolbar.Controls.Add(Theme.Button("Удалить",()=>Guard(DeleteSelected)));
        toolbar.Controls.Add(Theme.Button("★ Избранное",()=>Guard(ToggleFavorite)));
        toolbar.Padding=new Padding(0,12,0,8);root.Controls.Add(toolbar);
        var filters=new FlowLayoutPanel{AutoSize=true,Dock=DockStyle.Fill,WrapContents=true,Padding=new Padding(0,8,0,12)};
        search.Dock=DockStyle.None;search.Width=240;search.PlaceholderText="Поиск по аккаунтам…";categories.Width=180;
        categories.BackColor=Theme.Panel;categories.ForeColor=Theme.Text;categories.Margin=new Padding(4,2,4,8);
        filters.Controls.Add(Theme.Label("Поиск"));filters.Controls.Add(search);filters.Controls.Add(Theme.Label("Категория"));filters.Controls.Add(categories);filters.Controls.Add(favorites);filters.Controls.Add(Theme.Button("Сбросить",()=>{search.Clear();favorites.Checked=false;categories.SelectedIndex=0;}));root.Controls.Add(filters);
        accounts.BackColor=Theme.Panel;accounts.ForeColor=Theme.Text;accounts.BorderStyle=BorderStyle.None;accounts.Columns.Add("★",40);accounts.Columns.Add("Аккаунт",230);accounts.Columns.Add("Категория",150);
        accounts.OwnerDraw=true;
        accounts.Resize+=(_,_)=>accounts.Columns[2].Width=Math.Max(80,accounts.ClientSize.Width-accounts.Columns[0].Width-accounts.Columns[1].Width);
        accounts.DrawColumnHeader+=(_,e)=>{using var brush=new SolidBrush(Theme.Background);e.Graphics.FillRectangle(brush,e.Bounds);TextRenderer.DrawText(e.Graphics,e.Header!.Text,Font,e.Bounds,Theme.Gold,TextFormatFlags.Left|TextFormatFlags.VerticalCenter);};
        accounts.DrawItem+=(_,_)=>{};
        accounts.DrawSubItem+=(_,e)=>{using var brush=new SolidBrush(e.Item!.Selected?Color.FromArgb(70,30,54):Theme.Panel);e.Graphics.FillRectangle(brush,e.Bounds);var area=e.Bounds;area.Inflate(-4,0);TextRenderer.DrawText(e.Graphics,e.SubItem!.Text,Font,area,e.ColumnIndex==0?Theme.Gold:Theme.Text,TextFormatFlags.Left|TextFormatFlags.VerticalCenter|TextFormatFlags.EndEllipsis);};
        var split=new SplitContainer{Size=new Size(1100,540),Dock=DockStyle.Fill,SplitterDistance=350,BackColor=Theme.Background,Panel1MinSize=260,Panel2MinSize=350,SplitterWidth=16};
        accounts.Columns[1].Width=165;accounts.Columns[0].Width=30;
        split.Panel1.Controls.Add(accounts);details.BackColor=Theme.Panel;split.Panel2.Controls.Add(details);root.Controls.Add(split);root.Controls.Add(status);accountPage.Controls.Add(root);
        pageHost.Resize+=(_,_)=>LayoutPage(0);transition.Tick+=(_,_)=>{var t=Math.Min(1,transitionClock.Elapsed.TotalMilliseconds/180);LayoutPage((int)(14*Math.Pow(1-t,3)));if(t>=1)transition.Stop();};
        SwitchPage(false,false);
        accounts.SelectedIndexChanged+=(_,_)=>ShowSelected();accounts.DoubleClick+=(_,_)=>Guard(()=>{if(Selected is{} a)EditAccount(a);});
        search.TextChanged+=(_,_)=>Populate();categories.SelectedIndexChanged+=(_,_)=>{if(!updating)Populate();};favorites.CheckedChanged+=(_,_)=>Populate();
        details.SizeChanged+=(_,_)=>{foreach(Control control in details.Controls)control.Width=Math.Max(250,details.ClientSize.Width-38);};
        idle.Tick+=(_,_)=>{if(session.IsUnlocked&&!nativeDialog&&DateTime.UtcNow-lastInput>TimeSpan.FromMinutes(5))LockVault();};idle.Start();
        clipboardTimer.Tick+=(_,_)=>ClearClipboard();Application.AddMessageFilter(this);SystemEvents.SessionSwitch+=SessionChanged;
        Resize+=(_,_)=>{if(WindowState==FormWindowState.Minimized&&session.IsUnlocked)LockVault();};
        FormClosed+=(_,_)=>{transition.Dispose();idle.Dispose();clipboardTimer.Dispose();Application.RemoveMessageFilter(this);SystemEvents.SessionSwitch-=SessionChanged;ClearClipboard();session.Lock();};
        Shown+=(_,_)=>{if(smoke||session.IsUnlocked)RefreshVault();else if(DeviceSignIn.TryOpen(session))RefreshVault();else StartAccount();};
    }
    private void LayoutPage(int offset){if(currentPage!=null)currentPage.Bounds=new Rectangle(24,18+offset,Math.Max(1,pageHost.ClientSize.Width-48),Math.Max(1,pageHost.ClientSize.Height-36));}
    private void SwitchPage(bool notes,bool animate=true)
    {
        if(animate&&!session.IsUnlocked)return;
        transition.Stop();accountPage.Visible=!notes;notesView.Visible=notes;currentPage=notes?notesView:accountPage;currentPage.BringToFront();
        ((MotionButton)accountsNav).Active=!notes;((MotionButton)notesNav).Active=notes;accountsNav.Invalidate();notesNav.Invalidate();
        if(notes&&session.IsUnlocked)notesView.RefreshNotes();
        if(animate&&Theme.AnimationsEnabled){LayoutPage(14);transitionClock.Restart();transition.Start();}else LayoutPage(0);
    }
    public bool PreFilterMessage(ref Message message){if(message.Msg is 0x100 or 0x101 or 0x200 or 0x201 or 0x204)lastInput=DateTime.UtcNow;return false;}
    private void SessionChanged(object sender,SessionSwitchEventArgs e){if(e.Reason==SessionSwitchReason.SessionLock&&!IsDisposed)BeginInvoke(LockVault);}
    private void Guard(Action action)
    {
        if(!session.IsUnlocked)return;
        try{action();}catch(Exception error){RefreshVault();MessageBox.Show(this,error is ArgumentException or InvalidDataException?error.Message:"Операция не выполнена. Проверьте пароль, файл и доступ к папке. Сохранённые записи не удалены.","MemBoom",MessageBoxButtons.OK,MessageBoxIcon.Warning);}
    }
    private void StartAccount()
    {
        using var dialog=new AccountDialog(session);
        if(dialog.ShowDialog(this)!=DialogResult.OK||!session.IsUnlocked){Close();return;}
        lastInput=DateTime.UtcNow;RefreshVault();
    }
    private void Unlock()
    {
        using var dialog=new PasswordDialog(session.IsFirstRun?"Создайте мастер-пароль":"Открыть хранилище",session.IsFirstRun,unlockSession:session);
        if(dialog.ShowDialog(this)!=DialogResult.OK){Close();return;}lastInput=DateTime.UtcNow;RefreshVault();
    }
    private void LockVault()
    {
        if(!session.IsUnlocked||IsDisposed)return;
        Tag="locking";transition.Stop();
        foreach(var owned in OwnedForms.ToArray())owned.Close();
        session.Lock();notesView.ClearSensitive();Tag=null;ClearClipboard();accounts.Items.Clear();categories.Items.Clear();favorites.Checked=false;ClearDetails();search.Clear();status.Text="Хранилище заблокировано";
        BeginInvoke(()=>{if(!IsDisposed)Unlock();});
    }
    private void RefreshVault(string? selectedId=null)
    {
        if(!session.IsUnlocked)return;
        var category=(categories.SelectedItem as CategoryOption)?.Value??"";
        updating=true;categories.Items.Clear();categories.Items.Add(new CategoryOption("Все категории",""));
        foreach(var value in session.Document.Accounts.Select(a=>a.Category).Distinct().Order())categories.Items.Add(new CategoryOption(value,value));
        categories.SelectedItem=categories.Items.Cast<CategoryOption>().FirstOrDefault(c=>c.Value==category)??categories.Items[0];updating=false;Populate(selectedId);notesView.RefreshNotes();
    }
    private void Populate(string? selectedId=null)
    {
        if(updating||!session.IsUnlocked)return;
        selectedId??=Selected?.Id;
        accounts.BeginUpdate();accounts.Items.Clear();
        foreach(var account in session.Document.Search(search.Text,(categories.SelectedItem as CategoryOption)?.Value??"",favorites.Checked).OrderBy(a=>a.Name,StringComparer.CurrentCultureIgnoreCase))
        {
            var row=new ListViewItem([account.Favorite?"★":"",account.Name,account.Category]){Tag=account};accounts.Items.Add(row);if(account.Id==selectedId)row.Selected=true;
        }
        if(accounts.SelectedItems.Count==0&&accounts.Items.Count>0)accounts.Items[0].Selected=true;
        accounts.EndUpdate();ShowSelected();status.Text=$"Показано аккаунтов: {accounts.Items.Count} из {session.Document.Accounts.Count} · Изменения сохраняются автоматически · Автоблокировка через 5 минут";
    }
    private void ClearDetails(){foreach(Control control in details.Controls.Cast<Control>().ToArray())control.Dispose();details.Controls.Clear();}
    private void ShowSelected()
    {
        ClearDetails();var account=Selected;
        if(account==null){details.Controls.Add(Theme.Label(session.Document.Accounts.Count==0?"Добавьте первый аккаунт":"Выберите аккаунт или измените поиск",true));return;}
        details.Controls.Add(Theme.Label(account.Name,true));details.Controls.Add(Theme.Label(account.Category));
        foreach(var field in account.Fields)
        {
            var card=new TableLayoutPanel{ColumnCount=1,RowCount=3,AutoSize=false,Height=160,Width=Math.Max(250,details.ClientSize.Width-38),Padding=new Padding(10),BackColor=Theme.Background,Margin=new Padding(4,6,4,6)};
            card.ColumnStyles.Add(new(SizeType.Percent,100));
            card.RowStyles.Add(new(SizeType.AutoSize));card.RowStyles.Add(new(SizeType.AutoSize));card.RowStyles.Add(new(SizeType.AutoSize));
            card.Controls.Add(Theme.Label(field.Label));
            var value=new TextBox{Text=field.Value,UseSystemPasswordChar=PortableCrypto.IsSecret(field.Label),ReadOnly=true,BackColor=Theme.Panel,ForeColor=Theme.Text,BorderStyle=BorderStyle.FixedSingle,Dock=DockStyle.Top,AccessibleName=field.Label};card.Controls.Add(value);
            var actions=new FlowLayoutPanel{AutoSize=true,Dock=DockStyle.Top};
            var copy=Theme.Button("Копировать",()=>{try{Clipboard.SetText(field.Value);clipboardValue=field.Value;clipboardTimer.Stop();clipboardTimer.Start();status.Text="Поле скопировано. Буфер будет очищен через 30 секунд.";}catch{status.Text="Буфер обмена занят. Попробуйте ещё раз.";}});copy.Enabled=field.Value.Length>0;actions.Controls.Add(copy);
            if(PortableCrypto.IsSecret(field.Label)){Button? reveal=null;reveal=Theme.Button("Показать",()=>{value.UseSystemPasswordChar=!value.UseSystemPasswordChar;reveal!.Text=value.UseSystemPasswordChar?"Показать":"Скрыть";});actions.Controls.Add(reveal);}
            card.Controls.Add(actions);details.Controls.Add(card);
        }
    }
    private void EditAccount(VaultAccount? account)
    {
        using var dialog=new AccountEditor(session.Document.Accounts.Select(a=>a.Category).Distinct(),account);
        if(dialog.ShowDialog(this)!=DialogResult.OK||dialog.Result==null)return;
        var result=dialog.Result;
        session.Apply(d=>{var index=d.Accounts.FindIndex(a=>a.Id==result.Id);if(index<0)d.Accounts.Add(result);else d.Accounts[index]=result;});RefreshVault(result.Id);status.Text="Аккаунт сохранён.";
    }
    private void DeleteSelected()
    {
        if(Selected is not{} account)return;
        if(MessageBox.Show(this,$"Удалить «{account.Name}» из категории «{account.Category}»?","Удалить аккаунт",MessageBoxButtons.YesNo,MessageBoxIcon.Question)!=DialogResult.Yes)return;
        session.Apply(d=>d.Accounts.RemoveAll(a=>a.Id==account.Id));RefreshVault();status.Text="Аккаунт удалён.";
    }
    private void ToggleFavorite()
    {
        if(Selected is not{} account)return;
        session.Apply(d=>{var target=d.Accounts.Single(a=>a.Id==account.Id);target.Favorite=!target.Favorite;});RefreshVault(account.Id);
    }
    private DialogResult FileDialog(CommonDialog dialog)
    {
        nativeDialog=true;try{return dialog.ShowDialog(this);}finally{nativeDialog=false;lastInput=DateTime.UtcNow;}
    }
    private void Export()
    {
        var includeNotes=true;
        if(session.Document.Notes.Count>0)
        {
            var choice=MessageBox.Show(this,"Включить заметки?\n\nДа — полная копия для Windows 0.6 и новее.\nНет — только аккаунты для переноса на телефон или сайт.","Содержимое резервной копии",MessageBoxButtons.YesNoCancel,MessageBoxIcon.Question);
            if(choice==DialogResult.Cancel)return;includeNotes=choice==DialogResult.Yes;
        }
        using var password=new PasswordDialog("Пароль резервной копии",true);
        if(password.ShowDialog(this)!=DialogResult.OK)return;
        using var file=new SaveFileDialog{Filter="Зашифрованная копия (*.json)|*.json",FileName=$"MemBoom-{DateTime.Today:yyyy-MM-dd}.json",AddExtension=true,DefaultExt="json",OverwritePrompt=true};
        if(FileDialog(file)!=DialogResult.OK)return;
        File.WriteAllText(file.FileName,session.Export(password.Password,includeNotes));status.Text="Зашифрованная резервная копия сохранена.";
    }
    private void Import()
    {
        using var file=new OpenFileDialog{Filter="Зашифрованная копия (*.json)|*.json|Все файлы|*.*",CheckFileExists=true};if(FileDialog(file)!=DialogResult.OK)return;
        if(new FileInfo(file.FileName).Length>PortableCrypto.MaxBackupBytes)throw new InvalidDataException("Максимальный размер копии — 8 МБ.");
        using var password=new PasswordDialog("Пароль резервной копии");if(password.ShowDialog(this)!=DialogResult.OK)return;
        var incoming=VaultDocument.FromPortableJson(PortableCrypto.Decrypt(File.ReadAllText(file.FileName),password.Password));
        if(MessageBox.Show(this,$"Добавить из копии: аккаунтов {incoming.Accounts.Count}, заметок {incoming.Notes.Count}? Существующие записи останутся, совпадающие будут пропущены.","Импорт",MessageBoxButtons.YesNo,MessageBoxIcon.Question)!=DialogResult.Yes)return;
        int added=0;session.Apply(d=>added=d.Merge(incoming));RefreshVault();status.Text=$"Импортировано записей: {added}.";
    }
    private void ChangePassword()
    {
        using var dialog=new PasswordDialog("Изменить мастер-пароль",true,true);if(dialog.ShowDialog(this)!=DialogResult.OK)return;
        session.ChangePassword(dialog.OldPassword,dialog.Password);DeviceSignIn.RefreshUnlockSecret(session);status.Text="Мастер-пароль изменён.";
    }
    private void ClearClipboard()
    {
        clipboardTimer.Stop();try{if(clipboardValue!=null&&Clipboard.ContainsText()&&Clipboard.GetText()==clipboardValue)Clipboard.Clear();}catch{}finally{clipboardValue=null;}
    }
    public void SmokeCheck(string directory)
    {
        RefreshVault();if(accounts.Items.Count!=2)throw new Exception("Account list did not render");
        if(categories.SelectedItem?.ToString()!="Все категории")throw new Exception("Category selection failed");
        categories.SelectedIndex=1;if(accounts.Items.Count!=1)throw new Exception("Category filter failed");categories.SelectedIndex=0;
        search.Text="no-such-record";if(accounts.Items.Count!=0)throw new Exception("Search failed");search.Clear();
        accounts.Items[0].Selected=true;ToggleFavorite();favorites.Checked=true;if(accounts.Items.Count!=1)throw new Exception("Favorites failed");favorites.Checked=false;
        using(var editor=new AccountEditor(session.Document.Accounts.Select(a=>a.Category),session.Document.Accounts[0])){editor.Show(this);Application.DoEvents();editor.Close();}
        using(var sync=new SyncDialog(session)){sync.Show(this);Application.DoEvents();sync.SmokeCheck(directory);sync.Close();}
        using(var account=new AccountDialog(session)){account.Show(this);Application.DoEvents();account.SmokeCheck(directory);account.Close();}
        SwitchPage(true,false);notesView.SmokeCheck(directory);SwitchPage(false,false);
        RefreshVault();PerformLayout();Update();Application.DoEvents();
        using var image=new Bitmap(Width,Height);DrawToBitmap(image,new Rectangle(0,0,Width,Height));image.Save(Path.Combine(directory,"windows-preview.png"));
        File.WriteAllText(Path.Combine(directory,"ui-check.json"),"{\"accounts\":true,\"categories\":true,\"search\":true,\"favorites\":true,\"editor\":true,\"syncDialog\":true,\"notes\":true,\"mascot\":false}");
    }
}
