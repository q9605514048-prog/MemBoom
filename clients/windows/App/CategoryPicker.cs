using System.ComponentModel;

namespace MemBoom.Windows;

internal sealed class CategoryPicker:Button
{
    internal readonly List<object> Items=[];
    private object? selected;
    public event EventHandler? SelectedIndexChanged;
    [DesignerSerializationVisibility(DesignerSerializationVisibility.Hidden)]
    internal object? SelectedItem
    {
        get=>selected!=null&&Items.Contains(selected)?selected:null;
        set{if(ReferenceEquals(selected,value))return;selected=value;Invalidate();SelectedIndexChanged?.Invoke(this,EventArgs.Empty);}
    }
    [DesignerSerializationVisibility(DesignerSerializationVisibility.Hidden)]
    internal int SelectedIndex{get=>SelectedItem==null?-1:Items.IndexOf(SelectedItem);set=>SelectedItem=value>=0&&value<Items.Count?Items[value]:null;}
    public CategoryPicker()
    {
        FlatStyle=FlatStyle.Flat;FlatAppearance.BorderSize=0;Height=34;AccessibleRole=AccessibleRole.ComboBox;
        SetStyle(ControlStyles.UserPaint|ControlStyles.OptimizedDoubleBuffer|ControlStyles.AllPaintingInWmPaint|ControlStyles.ResizeRedraw,true);
    }
    protected override void OnClick(EventArgs e)
    {
        base.OnClick(e);if(Items.Count==0)return;
        var menu=new ContextMenuStrip{BackColor=Theme.Panel,ForeColor=Theme.Text,ShowImageMargin=false};
        foreach(var option in Items)
        {
            var item=new ToolStripMenuItem(option.ToString()){Checked=ReferenceEquals(option,SelectedItem),BackColor=Theme.Panel,ForeColor=Theme.Text};
            item.Click+=(_,_)=>SelectedItem=option;menu.Items.Add(item);
        }
        menu.Closed+=(_,_)=>menu.Dispose();menu.Show(this,new Point(0,Height));
    }
    protected override void OnPaint(PaintEventArgs e)
    {
        if(Width<2||Height<2)return;e.Graphics.SmoothingMode=System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
        e.Graphics.Clear(Parent?.BackColor??Theme.Background);using var path=Theme.Rounded(new RectangleF(.5f,.5f,Width-1,Height-1),8);
        using var fill=new SolidBrush(Theme.Panel);using var border=new Pen(Focused?Theme.Gold:Theme.Border);e.Graphics.FillPath(fill,path);e.Graphics.DrawPath(border,path);
        TextRenderer.DrawText(e.Graphics,SelectedItem?.ToString()??"Все категории",Font,new Rectangle(12,0,Width-36,Height),Theme.Text,TextFormatFlags.VerticalCenter|TextFormatFlags.Left|TextFormatFlags.EndEllipsis);
        using var arrow=new Pen(Theme.Muted,1.5f);e.Graphics.DrawLines(arrow,[new(Width-22,Height/2-2),new(Width-17,Height/2+3),new(Width-12,Height/2-2)]);
    }
}
