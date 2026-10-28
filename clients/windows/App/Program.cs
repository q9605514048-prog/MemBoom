using MemBoom.Core;

namespace MemBoom.Windows;

internal static class Program
{
    [STAThread]
    private static int Main(string[] args)
    {
        ApplicationConfiguration.Initialize();
        try
        {
            if(args.Length==2&&args[0]=="--ui-smoke")
            {
                var output=Path.GetFullPath(args[1]);Directory.CreateDirectory(output);
                var session=new VaultSession(Path.Combine(output,"smoke-vault.json"));session.Unlock("QaDesktop!123");
                session.Apply(d=>d.Notes.Clear());
                session.Apply(d=>{d.Accounts.Clear();d.Accounts.Add(new(){Category="Игры",Name="Steam · пример",Fields=[new("Почта / логин","player@example.test"),new("Пароль","test-only-password"),new("Заметка","Тестовая запись для проверки интерфейса")]});d.Accounts.Add(new(){Category="Работа",Name="Почта · пример",Fields=[new("Логин","qa@example.test"),new("Пароль","test-only-password")]});});
                using var form=new MainForm(session,true);form.Show();Application.DoEvents();form.SmokeCheck(output);form.Close();return 0;
            }
            var profile=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"MemBoom","vault-v1.json");
            using var mutex=new Mutex(true,"Local\\MemBoom.Windows.Vault",out var acquired);
            if(!acquired){MessageBox.Show("MemBoom уже запущен.","MemBoom");return 0;}
            Application.Run(new MainForm(new VaultSession(profile)));return 0;
        }
        catch(Exception error)
        {
            if(args.Length==2&&args[0]=="--ui-smoke"){File.WriteAllText(Path.Combine(args[1],"ui-error.txt"),error.ToString());return 1;}
            MessageBox.Show("Не удалось открыть MemBoom. Данные не удалены. Проверьте доступ к папке приложения.","MemBoom",MessageBoxButtons.OK,MessageBoxIcon.Error);return 1;
        }
    }
}
