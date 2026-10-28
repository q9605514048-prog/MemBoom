using System.Drawing.Drawing2D;
using System.Runtime.InteropServices;

namespace MemBoom.Windows;

internal static class Theme
{
    public static readonly Color Background=Color.FromArgb(16,17,24),Panel=Color.FromArgb(26,27,38),Sidebar=Color.FromArgb(21,21,30),Border=Color.FromArgb(48,47,62),Red=Color.FromArgb(233,77,94),Gold=Color.FromArgb(239,190,105),Text=Color.FromArgb(246,241,239),Muted=Color.FromArgb(157,153,169);
    public static void Form(Form form)
    {
        form.BackColor=Background;form.ForeColor=Text;form.Font=new Font("Segoe UI",10);form.StartPosition=FormStartPosition.CenterParent;
        typeof(Control).GetProperty("DoubleBuffered",System.Reflection.BindingFlags.NonPublic|System.Reflection.BindingFlags.Instance)?.SetValue(form,true);
        form.HandleCreated+=(_,_)=>{try{var dark=1;DwmSetWindowAttribute(form.Handle,20,ref dark,4);}catch{}};
    }
    [DllImport("dwmapi.dll")]private static extern int DwmSetWindowAttribute(IntPtr window,int attribute,ref int value,int size);
    [DllImport("user32.dll")][return:MarshalAs(UnmanagedType.Bool)]private static extern bool SystemParametersInfo(uint action,uint parameter,out int value,uint flags);
    public static bool AnimationsEnabled{get{try{return !SystemInformation.HighContrast&&SystemParametersInfo(0x1042,0,out var enabled,0)&&enabled!=0;}catch{return false;}}}
    public static Button Button(string text,Action click,bool primary=false)
    {
        var button=new MotionButton(primary){Text=text,AutoSize=true,MinimumSize=new Size(104,42),Margin=new Padding(4),Padding=new Padding(14,7,14,7)};
        button.Click+=(_,_)=>click();return button;
    }
    public static Label Label(string text,bool heading=false)=>new(){Text=text,AutoSize=true,ForeColor=heading?Text:Muted,Font=new Font("Segoe UI",heading?23:10,heading?FontStyle.Bold:FontStyle.Regular),Margin=new Padding(4,8,4,8)};
    public static TextBox Input(string name,bool secret=false)=>new(){AccessibleName=name,BackColor=Panel,ForeColor=Text,BorderStyle=BorderStyle.FixedSingle,UseSystemPasswordChar=secret,Dock=DockStyle.Top,Margin=new Padding(4,6,4,12)};
    public static GraphicsPath Rounded(RectangleF r,float radius)
    {
        var p=new GraphicsPath();var d=Math.Min(radius*2,Math.Min(r.Width,r.Height));
        p.AddArc(r.Left,r.Top,d,d,180,90);p.AddArc(r.Right-d,r.Top,d,d,270,90);p.AddArc(r.Right-d,r.Bottom-d,d,d,0,90);p.AddArc(r.Left,r.Bottom-d,d,d,90,90);p.CloseFigure();return p;
    }
}

internal sealed class MotionButton:Button
{
    private readonly System.Windows.Forms.Timer animation=new(){Interval=16};
    private readonly bool primary;
    private bool hovered;
    private float progress;
    [System.ComponentModel.DesignerSerializationVisibility(System.ComponentModel.DesignerSerializationVisibility.Hidden)]
    internal bool Active{get;set;}
    public MotionButton(bool accent)
    {
        primary=accent;FlatStyle=FlatStyle.Flat;FlatAppearance.BorderSize=0;ForeColor=Theme.Text;BackColor=Theme.Panel;
        SetStyle(ControlStyles.UserPaint|ControlStyles.AllPaintingInWmPaint|ControlStyles.OptimizedDoubleBuffer|ControlStyles.ResizeRedraw,true);
        animation.Tick+=(_,_)=>{var target=hovered?1f:0f;progress+=(target-progress)*.24f;if(Math.Abs(target-progress)<.02f){progress=target;animation.Stop();}Invalidate();};
    }
    protected override void OnMouseEnter(EventArgs e){base.OnMouseEnter(e);hovered=true;Animate();}
    protected override void OnMouseLeave(EventArgs e){base.OnMouseLeave(e);hovered=false;Animate();}
    private void Animate(){if(Theme.AnimationsEnabled)animation.Start();else{progress=hovered?1:0;Invalidate();}}
    protected override void OnPaint(PaintEventArgs e)
    {
        if(Width<2||Height<2)return;
        e.Graphics.SmoothingMode=SmoothingMode.AntiAlias;e.Graphics.Clear(Parent?.BackColor??Theme.Background);
        var normal=primary?Theme.Red:Active?Color.FromArgb(62,34,45):Theme.Panel;
        var hover=primary?Color.FromArgb(251,100,113):Color.FromArgb(49,43,58);
        Color Blend(Color a,Color b)=>Color.FromArgb((int)(a.R+(b.R-a.R)*progress),(int)(a.G+(b.G-a.G)*progress),(int)(a.B+(b.B-a.B)*progress));
        using var path=Theme.Rounded(new RectangleF(.5f,.5f,Width-1,Height-1),11);
        using var fill=new SolidBrush(Enabled?Blend(normal,hover):Theme.Sidebar);e.Graphics.FillPath(fill,path);
        using var pen=new Pen(Active?Theme.Red:primary?normal:Theme.Border);e.Graphics.DrawPath(pen,path);
        var area=ClientRectangle;area.Inflate(-12,-4);
        var alignment=TextAlign==ContentAlignment.MiddleLeft?TextFormatFlags.Left:TextFormatFlags.HorizontalCenter;
        TextRenderer.DrawText(e.Graphics,Text,Font,area,Enabled?Theme.Text:Theme.Muted,alignment|TextFormatFlags.VerticalCenter|TextFormatFlags.EndEllipsis);
        if(Focused&&ShowFocusCues){var focus=ClientRectangle;focus.Inflate(-5,-5);ControlPaint.DrawFocusRectangle(e.Graphics,focus,Theme.Gold,normal);}
    }
    protected override void Dispose(bool disposing){if(disposing)animation.Dispose();base.Dispose(disposing);}
}

internal class SmoothPanel:Panel
{
    public SmoothPanel(){DoubleBuffered=true;ResizeRedraw=true;}
}
